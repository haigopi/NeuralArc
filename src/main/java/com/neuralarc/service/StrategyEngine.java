package com.neuralarc.service;

import com.neuralarc.api.AlpacaClient;
import com.neuralarc.api.AlpacaOrderData;
import com.neuralarc.api.AlpacaPositionData;
import com.neuralarc.model.*;
import com.neuralarc.util.BrokerOrderStatusUtil;
import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public class StrategyEngine {
    private static final Logger LOGGER = Logger.getLogger(StrategyEngine.class.getName());

    private final StrategyRepository strategyRepository;
    private final StrategyOrderRepository orderRepository;
    private final StrategyStateMachine stateMachine;
    private final AlpacaClient alpacaClient;
    private final AppSettingsService appSettingsService;
    private final MarketHoursService marketHoursService;

    public StrategyEngine(
            StrategyRepository strategyRepository,
            StrategyOrderRepository orderRepository,
            StrategyStateMachine stateMachine,
            AlpacaClient alpacaClient
    ) {
        this(strategyRepository, orderRepository, stateMachine, alpacaClient, new AppSettingsService(), new MarketHoursService());
    }

    public StrategyEngine(
            StrategyRepository strategyRepository,
            StrategyOrderRepository orderRepository,
            StrategyStateMachine stateMachine,
            AlpacaClient alpacaClient,
            AppSettingsService appSettingsService,
            MarketHoursService marketHoursService
    ) {
        this.strategyRepository = strategyRepository;
        this.orderRepository = orderRepository;
        this.stateMachine = stateMachine;
        this.alpacaClient = alpacaClient;
        this.appSettingsService = appSettingsService;
        this.marketHoursService = marketHoursService;
    }

    /**
     * Immutable record describing the evaluation result of a single strategy rule during
     * a poll cycle. Used to surface rule analysis summaries to the UI log.
     */
    public record RuleOutcome(String ruleName, String outcome, String details) {
        @Override
        public String toString() {
            return ruleName + "=" + outcome + " (" + details + ")";
        }
    }

    /**
     * Legacy reconcile entry-point (void). Streaming and resume paths use this.
     * Delegates to the tracked overload with an empty price cache; outcomes are discarded.
     */
    public void reconcile(Strategy strategy) {
        reconcileTracked(strategy, Map.of());
    }

    /**
     * Reconcile with a pre-fetched price cache supplied by the polling service.
     * Returns the list of rule outcomes so the caller can surface them to the UI log.
     * Package-private: only intended for use by {@link StrategyPollingService}.
     */
    List<RuleOutcome> reconcileTracked(Strategy strategy, Map<String, BigDecimal> priceCache) {
        List<RuleOutcome> outcomes = new ArrayList<>();
        if (!isAutoExecutionAllowed(strategy.id())) {
            logPoll(strategy, "POLL", "SKIPPED", "Strategy is no longer active (likely manually canceled/paused)");
            return outcomes;
        }
        AppSettingsService.AppSettings settings = appSettingsService.load();
        boolean sessionOpen = marketHoursService.isTradingSessionOpen(settings.extendedHoursTradingEnabled());
        refreshOrderStatuses(strategy);
        Optional<AlpacaPositionData> position = alpacaClient.getPosition(strategy.symbol());

        // Use pre-fetched price when available; fall back to position market price or individual call.
        String symbolKey = strategy.symbol() != null ? strategy.symbol().toUpperCase() : "";
        BigDecimal cachedPrice = priceCache.get(symbolKey);
        BigDecimal latestPrice;
        if (position.isPresent() && position.get().marketPrice() != null
                && position.get().marketPrice().compareTo(BigDecimal.ZERO) > 0) {
            latestPrice = position.get().marketPrice();
        } else if (cachedPrice != null && cachedPrice.compareTo(BigDecimal.ZERO) > 0) {
            latestPrice = cachedPrice;
        } else {
            latestPrice = alpacaClient.getLatestPrice(strategy.symbol());
        }

        List<StrategyOrder> orders = orderRepository.findByStrategyId(strategy.id());
        List<AlpacaOrderData> remoteOpenOrders = alpacaClient.getOpenOrders(strategy.symbol());
        logPoll(strategy, "POLL", "STARTED",
                "state=" + strategy.currentState().name()
                        + ", latestPrice=" + latestPrice.toPlainString()
                        + ", hasPosition=" + (position.isPresent() && position.get().exists())
                        + ", openOrders=" + remoteOpenOrders.size()
                        + ", priceSource=" + (cachedPrice != null && cachedPrice.compareTo(BigDecimal.ZERO) > 0
                                && !(position.isPresent() && position.get().marketPrice() != null
                                        && position.get().marketPrice().compareTo(BigDecimal.ZERO) > 0)
                                ? "batchCache" : "direct"));

        if (ensureRemoteOrderPresence(strategy, orders, remoteOpenOrders, position)) {
            orders = orderRepository.findByStrategyId(strategy.id());
        }

        if (strategy.currentState() == StrategyLifecycleState.QUEUED_FOR_OPEN
                && sessionOpen
                && (position.isEmpty() || !position.get().exists())
                && !isStageFilled(orders, StrategyStage.BASE_BUY)
                && !hasPendingStage(orders, StrategyStage.BASE_BUY)) {
            submitBaseBuy(strategy, false);
            orders = orderRepository.findByStrategyId(strategy.id());
        }

        if (position.isPresent() && position.get().exists()) {
            if (shouldActivateStopLossMonitoring(strategy, orders)) {
                stateMachine.transition(strategy, StrategyLifecycleState.STOP_LOSS_ACTIVE,
                        StrategyEventType.STOP_LOSS_ACTIVATED,
                        "Stop loss monitoring active",
                        "{\"symbol\":\"" + strategy.symbol() + "\"}");
                strategyRepository.save(strategy);
            }
            evaluateManagedStopLoss(strategy, position.get(), latestPrice, orders, outcomes);
            evaluateTargetSellAndProfitHold(strategy, position.get(), latestPrice, orders, outcomes);
        } else {
            logRule(strategy, "STOP_LOSS", "SKIPPED", "No open position", outcomes);
            logRule(strategy, "TARGET_SELL", "SKIPPED", "No open position", outcomes);
            logRule(strategy, "PROFIT_HOLD", "SKIPPED", "No open position", outcomes);
            maybeRestartStrategy(strategy, orders);
        }

        maybeSubmitBuyLimit1(strategy, latestPrice, orders, outcomes);
        maybeSubmitBuyLimit2(strategy, latestPrice, orders, outcomes);
        if (!isAutoExecutionAllowed(strategy.id())) {
            logPoll(strategy, "POLL", "COMPLETED", "Skipped final state save because strategy was paused during poll");
            return outcomes;
        }
        strategy.setLastPolledAt(Instant.now());
        strategyRepository.save(strategy);
        logPoll(strategy, "POLL", "COMPLETED", "lastPolledAt=" + strategy.lastPolledAt());
        return outcomes;
    }

    public StrategyOrder submitBaseBuy(Strategy strategy) {
        return submitBaseBuy(strategy, true);
    }

    public StrategyOrder submitBaseBuy(Strategy strategy, boolean enforceTradableSession) {
        return submitBuyOrder(strategy, StrategyStage.BASE_BUY, strategy.baseBuyQuantity(), strategy.baseBuyLimitPrice(),
                StrategyLifecycleState.BASE_BUY_PLACED, "Base buy order submitted", enforceTradableSession);
    }

    public void resumeStrategy(Strategy strategy) {
        List<StrategyOrder> orders = orderRepository.findByStrategyId(strategy.id());
        List<AlpacaOrderData> remoteOpenOrders = alpacaClient.getOpenOrders(strategy.symbol());
        Optional<AlpacaPositionData> position = alpacaClient.getPosition(strategy.symbol());

        if (!remoteOpenOrders.isEmpty()) {
            reconcile(strategy);
            return;
        }

        if (position.isEmpty() || !position.get().exists()) {
            if (!isStageFilled(orders, StrategyStage.BASE_BUY)) {
                submitBaseBuy(strategy, false);
                return;
            }
        }

        reconcile(strategy);
    }

    public boolean canAutoRetryFailed(Strategy strategy) {
        List<AlpacaOrderData> remoteOpenOrders = alpacaClient.getOpenOrders(strategy.symbol());
        Optional<AlpacaPositionData> position = alpacaClient.getPosition(strategy.symbol());
        return remoteOpenOrders.isEmpty() && (position.isEmpty() || !position.get().exists());
    }

    private boolean ensureRemoteOrderPresence(
            Strategy strategy,
            List<StrategyOrder> orders,
            List<AlpacaOrderData> remoteOpenOrders,
            Optional<AlpacaPositionData> position
    ) {
        if (!remoteOpenOrders.isEmpty() || (position.isPresent() && position.get().exists())) {
            return false;
        }

        // After a full exit, old staged buy orders from the previous cycle must not be recreated.
        // Restart/completion handling later in reconcile() owns the next action for that case.
        if (latestFilledExitOrder(orders).isPresent()) {
            return false;
        }

        boolean updatedLocalOrderState = false;
        for (StrategyOrder order : orders) {
            if (!order.isPending()) {
                continue;
            }
            order.setStatus(StrategyOrderStatus.CANCELED);
            orderRepository.save(order);
            updatedLocalOrderState = true;
        }

        if (!isStageFilled(orders, StrategyStage.BASE_BUY)) {
            submitBaseBuy(strategy);
            return true;
        }
        if (isStageFilled(orders, StrategyStage.BASE_BUY)
                && strategy.lossBuyLevelsEnabled()
                && strategy.buyLimit1Quantity() > 0
                && !isStageFilled(orders, StrategyStage.BUY_LIMIT_1)) {
            submitBuyOrder(strategy, StrategyStage.BUY_LIMIT_1, strategy.buyLimit1Quantity(), strategy.buyLimit1Price(),
                    StrategyLifecycleState.BUY_LIMIT_1_PLACED, "Buy Limit 1 recreated after missing Alpaca order", true);
            return true;
        }
        if (isStageFilled(orders, StrategyStage.BUY_LIMIT_1)
                && strategy.lossBuyLevelsEnabled()
                && strategy.buyLimit2Quantity() > 0
                && !isStageFilled(orders, StrategyStage.BUY_LIMIT_2)) {
            submitBuyOrder(strategy, StrategyStage.BUY_LIMIT_2, strategy.buyLimit2Quantity(), strategy.buyLimit2Price(),
                    StrategyLifecycleState.BUY_LIMIT_2_PLACED, "Buy Limit 2 recreated after missing Alpaca order", true);
            return true;
        }
        return updatedLocalOrderState;
    }

    private void maybeSubmitBuyLimit1(Strategy strategy, BigDecimal latestPrice, List<StrategyOrder> orders, List<RuleOutcome> outcomes) {
        if (!strategy.lossBuyLevelsEnabled()) {
            logRule(strategy, "BUY_LIMIT_1", "SKIPPED", "Loss buy levels disabled", outcomes);
            return;
        }
        if (strategy.buyLimit1Quantity() <= 0 || strategy.buyLimit1Price().compareTo(BigDecimal.ZERO) <= 0) {
            logRule(strategy, "BUY_LIMIT_1", "SKIPPED", "Not configured", outcomes);
            return;
        }
        if (latestPrice.compareTo(strategy.buyLimit1Price()) > 0) {
            logRule(strategy, "BUY_LIMIT_1", "NOT_SATISFIED",
                    "latestPrice=" + latestPrice.toPlainString()
                            + " > triggerPrice=" + strategy.buyLimit1Price().toPlainString(), outcomes);
            return;
        }
        if (!isStageFilled(orders, StrategyStage.BASE_BUY)) {
            logRule(strategy, "BUY_LIMIT_1", "SKIPPED", "Base buy not fully filled", outcomes);
            return;
        }
        if (hasPendingOrFilledStage(orders, StrategyStage.BUY_LIMIT_1)) {
            logRule(strategy, "BUY_LIMIT_1", "SKIPPED", "Existing pending or filled order already present", outcomes);
            return;
        }
        logRule(strategy, "BUY_LIMIT_1", "SATISFIED",
                "latestPrice=" + latestPrice.toPlainString()
                        + " <= triggerPrice=" + strategy.buyLimit1Price().toPlainString()
                        + ", quantity=" + strategy.buyLimit1Quantity(), outcomes);
        submitBuyOrder(strategy, StrategyStage.BUY_LIMIT_1, strategy.buyLimit1Quantity(), strategy.buyLimit1Price(),
                StrategyLifecycleState.BUY_LIMIT_1_PLACED, "Buy Limit 1 submitted", true);
    }

    private void maybeSubmitBuyLimit2(Strategy strategy, BigDecimal latestPrice, List<StrategyOrder> orders, List<RuleOutcome> outcomes) {
        if (!strategy.lossBuyLevelsEnabled()) {
            logRule(strategy, "BUY_LIMIT_2", "SKIPPED", "Loss buy levels disabled", outcomes);
            return;
        }
        if (strategy.buyLimit2Quantity() <= 0 || strategy.buyLimit2Price().compareTo(BigDecimal.ZERO) <= 0) {
            logRule(strategy, "BUY_LIMIT_2", "SKIPPED", "Not configured", outcomes);
            return;
        }
        if (latestPrice.compareTo(strategy.buyLimit2Price()) > 0) {
            logRule(strategy, "BUY_LIMIT_2", "NOT_SATISFIED",
                    "latestPrice=" + latestPrice.toPlainString()
                            + " > triggerPrice=" + strategy.buyLimit2Price().toPlainString(), outcomes);
            return;
        }
        if (!isStageFilled(orders, StrategyStage.BUY_LIMIT_1)) {
            logRule(strategy, "BUY_LIMIT_2", "SKIPPED", "Buy Limit 1 not fully filled", outcomes);
            return;
        }
        if (hasPendingOrFilledStage(orders, StrategyStage.BUY_LIMIT_2)) {
            logRule(strategy, "BUY_LIMIT_2", "SKIPPED", "Existing pending or filled order already present", outcomes);
            return;
        }
        logRule(strategy, "BUY_LIMIT_2", "SATISFIED",
                "latestPrice=" + latestPrice.toPlainString()
                        + " <= triggerPrice=" + strategy.buyLimit2Price().toPlainString()
                        + ", quantity=" + strategy.buyLimit2Quantity(), outcomes);
        submitBuyOrder(strategy, StrategyStage.BUY_LIMIT_2, strategy.buyLimit2Quantity(), strategy.buyLimit2Price(),
                StrategyLifecycleState.BUY_LIMIT_2_PLACED, "Buy Limit 2 submitted", true);
    }

    private void evaluateManagedStopLoss(Strategy strategy, AlpacaPositionData position, BigDecimal latestPrice, List<StrategyOrder> orders, List<RuleOutcome> outcomes) {
        if (!strategy.automatedStopLossEnabled()) {
            logRule(strategy, "STOP_LOSS", "SKIPPED", "Disabled", outcomes);
            return;
        }
        if (hasPendingOrFilledStage(orders, StrategyStage.STOP_LOSS)) {
            logRule(strategy, "STOP_LOSS", "SKIPPED", "Existing pending or filled stop loss order already present", outcomes);
            return;
        }
        BigDecimal stopThreshold = strategy.stopLossType() == StopLossType.PERCENT_BELOW_AVERAGE_COST
                ? Monetary.round(position.avgEntryPrice().multiply(BigDecimal.ONE.subtract(strategy.stopLossPercent().divide(new BigDecimal("100")))))
                : strategy.stopLossPrice();
        if (stopThreshold.compareTo(BigDecimal.ZERO) <= 0) {
            logRule(strategy, "STOP_LOSS", "SKIPPED", "Computed threshold is not positive", outcomes);
            return;
        }
        if (latestPrice.compareTo(stopThreshold) > 0) {
            logRule(strategy, "STOP_LOSS", "NOT_SATISFIED",
                    "latestPrice=" + latestPrice.toPlainString()
                            + " > threshold=" + stopThreshold.toPlainString(), outcomes);
            return;
        }
        logRule(strategy, "STOP_LOSS", "SATISFIED",
                "latestPrice=" + latestPrice.toPlainString()
                        + " <= threshold=" + stopThreshold.toPlainString()
                        + ", quantity=" + position.quantity().toPlainString(), outcomes);
        submitSellOrder(strategy, StrategyStage.STOP_LOSS, position.quantity(), latestPrice,
                StrategyLifecycleState.SELL_PLACED, "Stop loss sell submitted", StrategyEventType.STOP_LOSS_TRIGGERED);
    }


    private void evaluateTargetSellAndProfitHold(Strategy strategy, AlpacaPositionData position, BigDecimal latestPrice, List<StrategyOrder> orders, List<RuleOutcome> outcomes) {
        if (!strategy.targetSellEnabled() || strategy.targetSellPrice().compareTo(BigDecimal.ZERO) <= 0) {
            logRule(strategy, "TARGET_SELL", "SKIPPED", "Disabled or invalid target sell price", outcomes);
            logRule(strategy, "PROFIT_HOLD", "SKIPPED", "Target sell is not active", outcomes);
            return;
        }
        if (hasPendingOrFilledExitOrder(orders, StrategyStage.TARGET_SELL) || hasPendingOrFilledExitOrder(orders, StrategyStage.PROFIT_EXIT)) {
            logRule(strategy, "TARGET_SELL", "SKIPPED", "Existing pending or filled exit order already present", outcomes);
            logRule(strategy, "PROFIT_HOLD", "SKIPPED", "Existing pending or filled exit order already present", outcomes);
            return;
        }
        boolean profitHoldActive = strategy.currentState() == StrategyLifecycleState.PROFIT_HOLD_ACTIVE
                || strategy.highestObservedPriceAfterTarget().compareTo(BigDecimal.ZERO) > 0;
        if (!profitHoldActive && latestPrice.compareTo(strategy.targetSellPrice()) < 0) {
            logRule(strategy, "TARGET_SELL", "NOT_SATISFIED",
                    "latestPrice=" + latestPrice.toPlainString()
                            + " < targetPrice=" + strategy.targetSellPrice().toPlainString(), outcomes);
            logRule(strategy, "PROFIT_HOLD", "SKIPPED", "Target sell has not triggered yet", outcomes);
            return;
        }

        if (!strategy.profitHoldEnabled()) {
            if (strategy.alpacaTrailingStopEnabled()) {
                logRule(strategy, "TARGET_SELL", "SATISFIED",
                        "latestPrice=" + latestPrice.toPlainString()
                                + " >= targetPrice=" + strategy.targetSellPrice().toPlainString()
                                + ", quantity=" + strategy.targetSellQuantity(position.quantity()).toPlainString(), outcomes);
                logRule(strategy, "ALPACA_TRAILING_STOP", "SUBMITTED",
                        "Broker trailing stop requested after trigger", outcomes);
                submitTrailingStopSellOrder(strategy, strategy.targetSellQuantity(position.quantity()),
                        StrategyLifecycleState.SELL_PLACED,
                        "Broker trailing stop sell submitted after trigger",
                        StrategyEventType.ORDER_SUBMITTED);
                return;
            }
            logRule(strategy, "TARGET_SELL", "SATISFIED",
                    "latestPrice=" + latestPrice.toPlainString()
                            + " >= targetPrice=" + strategy.targetSellPrice().toPlainString()
                            + ", quantity=" + strategy.targetSellQuantity(position.quantity()).toPlainString(), outcomes);
            logRule(strategy, "PROFIT_HOLD", "SKIPPED", "Disabled", outcomes);
            submitSellOrder(strategy, StrategyStage.TARGET_SELL, strategy.targetSellQuantity(position.quantity()), latestPrice,
                    StrategyLifecycleState.SELL_PLACED, "Target sell submitted", StrategyEventType.TARGET_TRIGGERED);
            return;
        }

        if (strategy.alpacaTrailingStopEnabled()) {
            logRule(strategy, "TARGET_SELL", "SATISFIED",
                    "latestPrice=" + latestPrice.toPlainString()
                            + " >= targetPrice=" + strategy.targetSellPrice().toPlainString()
                            + ", quantity=" + strategy.targetSellQuantity(position.quantity()).toPlainString(), outcomes);
            logRule(strategy, "ALPACA_TRAILING_STOP", "SUBMITTED",
                    "Broker trailing stop requested after trigger", outcomes);
            submitTrailingStopSellOrder(strategy, strategy.targetSellQuantity(position.quantity()),
                    StrategyLifecycleState.SELL_PLACED,
                    "Broker trailing stop sell submitted after trigger",
                    StrategyEventType.ORDER_SUBMITTED);
            return;
        }

        if (!profitHoldActive) {
            strategy.setCurrentState(StrategyLifecycleState.PROFIT_HOLD_ACTIVE);
            strategy.updateHighestObservedPriceAfterTarget(latestPrice);
            stateMachine.transition(strategy, StrategyLifecycleState.PROFIT_HOLD_ACTIVE,
                    StrategyEventType.PROFIT_HOLD_ARMED,
                    "Profit hold armed",
                    "{\"highest\":\"" + strategy.highestObservedPriceAfterTarget().toPlainString() + "\"}");
        } else {
            strategy.updateHighestObservedPriceAfterTarget(latestPrice);
        }
        BigDecimal threshold = trailingThreshold(strategy);
        if (latestPrice.compareTo(threshold) > 0) {
            logRule(strategy, "TARGET_SELL", "SATISFIED",
                    "latestPrice=" + latestPrice.toPlainString()
                            + " >= targetPrice=" + strategy.targetSellPrice().toPlainString(), outcomes);
            logRule(strategy, "PROFIT_HOLD", "NOT_SATISFIED",
                    "latestPrice=" + latestPrice.toPlainString()
                            + " > trailingThreshold=" + threshold.toPlainString()
                            + ", highest=" + strategy.highestObservedPriceAfterTarget().toPlainString(), outcomes);
            strategyRepository.save(strategy);
            return;
        }
        logRule(strategy, "TARGET_SELL", "SATISFIED",
                "latestPrice=" + latestPrice.toPlainString()
                        + " >= targetPrice=" + strategy.targetSellPrice().toPlainString(), outcomes);
        logRule(strategy, "PROFIT_HOLD", "SATISFIED",
                "latestPrice=" + latestPrice.toPlainString()
                        + " <= trailingThreshold=" + threshold.toPlainString()
                        + ", highest=" + strategy.highestObservedPriceAfterTarget().toPlainString(), outcomes);
        submitSellOrder(strategy, StrategyStage.PROFIT_EXIT, strategy.targetSellQuantity(position.quantity()), latestPrice,
                StrategyLifecycleState.SELL_PLACED, "Profit hold exit submitted", StrategyEventType.ORDER_SUBMITTED);
    }

    private void maybeRestartStrategy(Strategy strategy, List<StrategyOrder> orders) {
        // Called only when no open position exists, so restart is inherently full-exit only.
        Optional<StrategyOrder> latestFilledExitOrder = latestFilledExitOrder(orders);
        if (latestFilledExitOrder.isEmpty()) {
            return;
        }

        StrategyOrder filledExitOrder = latestFilledExitOrder.get();
        // Defensive exits (e.g., CLOSE_POSITION, STOP_LOSS) always complete the cycle.
        // Manual exits may restart if the strategy is configured to repeat after exit.
        if (!strategy.restartAfterExitEnabled() || !isProfitableExitStage(filledExitOrder.stage())) {
            stateMachine.transition(strategy, StrategyLifecycleState.COMPLETED,
                    StrategyEventType.STRATEGY_COMPLETED,
                    "Strategy cycle completed",
                    "{}");
            strategyRepository.save(strategy);
            return;
        }

        if (hasPendingStage(orders, StrategyStage.BASE_BUY)) {
            return;
        }
        strategy.clearProfitHoldTracking();
        strategy.setCurrentState(StrategyLifecycleState.CREATED);
        strategy.setStatus(StrategyStatus.ACTIVE);
        strategy.clearLastError();
        strategyRepository.save(strategy);
        submitBaseBuy(strategy);
    }

    private void refreshOrderStatuses(Strategy strategy) {
        List<StrategyOrder> orders = orderRepository.findByStrategyId(strategy.id());
        for (StrategyOrder order : orders) {
            if (order.alpacaOrderId() == null || order.alpacaOrderId().isBlank() || order.isTerminal()) {
                continue;
            }
            Optional<AlpacaOrderData> latest = alpacaClient.getOrder(order.alpacaOrderId());
            if (latest.isEmpty()) {
                continue;
            }
            applyOrderUpdate(strategy, order, latest.get());
        }
    }

    public boolean applyStreamingOrderUpdate(AlpacaOrderData orderData) {
        if (orderData == null) {
            return false;
        }

        Optional<StrategyOrder> matchingOrder = orderRepository.findByAlpacaOrderId(orderData.orderId());
        if (matchingOrder.isEmpty()) {
            matchingOrder = orderRepository.findByClientOrderId(orderData.clientOrderId());
        }
        if (matchingOrder.isEmpty()) {
            return false;
        }

        StrategyOrder order = matchingOrder.get();
        Optional<Strategy> maybeStrategy = strategyRepository.findById(order.strategyId());
        if (maybeStrategy.isEmpty()) {
            return false;
        }

        Strategy strategy = maybeStrategy.get();
        StrategyOrderStatus status = applyOrderUpdate(strategy, order, orderData);
        if (status == StrategyOrderStatus.FILLED || status == StrategyOrderStatus.PARTIALLY_FILLED) {
            reconcile(strategy);
        }
        return true;
    }

    private StrategyOrderStatus applyOrderUpdate(Strategy strategy, StrategyOrder order, AlpacaOrderData data) {
        StrategyOrderStatus status = StrategyService.mapOrderStatus(data.status());
        if (data.orderId() != null && !data.orderId().isBlank() && (order.alpacaOrderId() == null || order.alpacaOrderId().isBlank())) {
            order.setAlpacaOrderId(data.orderId());
        }
        order.setStatus(status);
        order.setFilledQuantity(data.filledQuantity());
        order.setFilledAveragePrice(data.filledAveragePrice());
        order.setRawResponseJson(data.rawJson());
        if (status == StrategyOrderStatus.FILLED && order.filledAt() == null) {
            order.setFilledAt(Instant.now());
        }
        orderRepository.save(order);
        strategy.setLatestOrderStatus(BrokerOrderStatusUtil.normalize(data.status()));
        strategy.setLatestAlpacaOrderId(order.alpacaOrderId() == null ? "" : order.alpacaOrderId());
        transitionForOrderUpdate(strategy, order, status);
        strategyRepository.save(strategy);
        return status;
    }

    private void transitionForOrderUpdate(Strategy strategy, StrategyOrder order, StrategyOrderStatus status) {
        StrategyLifecycleState lifecycleState = switch (order.stage()) {
            case BASE_BUY -> status == StrategyOrderStatus.FILLED
                    ? StrategyLifecycleState.BASE_BUY_FILLED
                    : status == StrategyOrderStatus.PARTIALLY_FILLED
                    ? StrategyLifecycleState.BASE_BUY_PARTIALLY_FILLED
                    : StrategyLifecycleState.BASE_BUY_PLACED;
            case BUY_LIMIT_1 -> status == StrategyOrderStatus.FILLED
                    ? StrategyLifecycleState.BUY_LIMIT_1_FILLED
                    : status == StrategyOrderStatus.PARTIALLY_FILLED
                    ? StrategyLifecycleState.BUY_LIMIT_1_PARTIALLY_FILLED
                    : StrategyLifecycleState.BUY_LIMIT_1_PLACED;
            case BUY_LIMIT_2 -> status == StrategyOrderStatus.FILLED
                    ? StrategyLifecycleState.BUY_LIMIT_2_FILLED
                    : status == StrategyOrderStatus.PARTIALLY_FILLED
                    ? StrategyLifecycleState.BUY_LIMIT_2_PARTIALLY_FILLED
                    : StrategyLifecycleState.BUY_LIMIT_2_PLACED;
            case TARGET_SELL, PROFIT_EXIT, STOP_LOSS, LOSS_EXIT, MANUAL_EXIT, CLOSE_POSITION -> status == StrategyOrderStatus.PARTIALLY_FILLED
                    ? StrategyLifecycleState.SELL_PARTIALLY_FILLED
                    : status == StrategyOrderStatus.FILLED
                    ? StrategyLifecycleState.COMPLETED
                    : StrategyLifecycleState.SELL_PLACED;
        };
        StrategyEventType type = status == StrategyOrderStatus.FILLED || status == StrategyOrderStatus.PARTIALLY_FILLED
                ? StrategyEventType.ORDER_STATUS_UPDATED
                : StrategyEventType.ORDER_SUBMITTED;
        stateMachine.transition(strategy, lifecycleState, type,
                "Order " + order.stage() + " is " + status.name(),
                order.rawResponseJson());
    }

    private StrategyOrder submitBuyOrder(
            Strategy strategy,
            StrategyStage stage,
            int quantity,
            BigDecimal limitPrice,
            StrategyLifecycleState lifecycleState,
            String message,
            boolean enforceTradableSession
    ) {
        if (!isAutoExecutionAllowed(strategy.id())) {
            return null;
        }
        RiskProjection projection = projectedRisk(strategy, BigDecimal.valueOf(quantity), limitPrice);
        if (!projection.allowed()) {
            strategy.setLastError(projection.reason());
            stateMachine.transition(strategy, StrategyLifecycleState.FAILED,
                    StrategyEventType.STRATEGY_FAILED,
                    projection.reason(),
                    "{}");
            strategyRepository.save(strategy);
            return null;
        }
        // Broker should be the source of truth for whether off-hours orders are accepted.
        String clientOrderId = StrategyService.buildClientOrderId(strategy.id(), stage);
        AlpacaOrderData submitted = alpacaClient.submitLimitBuyOrder(strategy.symbol(), quantity, limitPrice, clientOrderId);
        Instant submittedAt = submitted.submittedAt() == null ? Instant.now() : submitted.submittedAt();
        StrategyOrder order = new StrategyOrder(
                UUID.randomUUID().toString(),
                strategy.id(),
                stage,
                submitted.orderId(),
                clientOrderId,
                strategy.symbol(),
                StrategyOrderSide.BUY,
                StrategyOrderType.LIMIT,
                limitPrice,
                BigDecimal.ZERO,
                BigDecimal.valueOf(quantity),
                submitted.filledQuantity(),
                submitted.filledAveragePrice(),
                StrategyService.mapOrderStatus(submitted.status()),
                submittedAt,
                Instant.now(),
                null,
                submitted.rawJson()
        );
        if (order.status() == StrategyOrderStatus.REJECTED || order.status() == StrategyOrderStatus.FAILED) {
            orderRepository.save(order);
            String failureMessage = failureMessage(submitted.rawJson(), stage);
            if (isQueueableSessionRejection(submitted.rawJson())) {
                strategy.clearLastError();
                strategy.setLatestOrderStatus("QUEUED_FOR_OPEN");
                strategy.setLatestAlpacaOrderId("");
                strategy.setLastTriggeredRuleType(mapStageToRuleName(stage));
                stateMachine.transition(strategy, StrategyLifecycleState.QUEUED_FOR_OPEN,
                        StrategyEventType.ORDER_STATUS_UPDATED,
                        "Order queued for next market open after broker rejection",
                        submitted.rawJson());
                strategyRepository.save(strategy);
                return order;
            }
            strategy.setLastError(failureMessage);
            strategy.setLatestOrderStatus(BrokerOrderStatusUtil.normalize(submitted.status()));
            strategy.setLatestAlpacaOrderId("");
            stateMachine.transition(strategy, StrategyLifecycleState.FAILED,
                    StrategyEventType.STRATEGY_FAILED,
                    failureMessage,
                    submitted.rawJson());
            strategyRepository.save(strategy);
            return order;
        }
        orderRepository.save(order);
        strategy.setLatestOrderStatus(BrokerOrderStatusUtil.normalize(submitted.status()));
        strategy.setLatestAlpacaOrderId(submitted.orderId());
        strategy.setLastTriggeredRuleType(mapStageToRuleName(stage));
        stateMachine.transition(strategy, lifecycleState, StrategyEventType.ORDER_SUBMITTED, message, submitted.rawJson());
        strategyRepository.save(strategy);
        return order;
    }

    private StrategyOrder submitSellOrder(
            Strategy strategy,
            StrategyStage stage,
            BigDecimal quantity,
            BigDecimal limitPrice,
            StrategyLifecycleState lifecycleState,
            String message,
            StrategyEventType eventType
    ) {
        if (!isAutoExecutionAllowed(strategy.id())) {
            return null;
        }
        int requestedQuantity = quantity.setScale(0, java.math.RoundingMode.DOWN).intValue();
        if (requestedQuantity <= 0) {
            return null;
        }
        String clientOrderId = StrategyService.buildClientOrderId(strategy.id(), stage);
        AlpacaOrderData submitted = alpacaClient.submitLimitSellOrder(strategy.symbol(), requestedQuantity, limitPrice, clientOrderId);
        Instant submittedAt = submitted.submittedAt() == null ? Instant.now() : submitted.submittedAt();
        StrategyOrder order = new StrategyOrder(
                UUID.randomUUID().toString(),
                strategy.id(),
                stage,
                submitted.orderId(),
                clientOrderId,
                strategy.symbol(),
                StrategyOrderSide.SELL,
                StrategyOrderType.LIMIT,
                limitPrice,
                BigDecimal.ZERO,
                BigDecimal.valueOf(requestedQuantity),
                submitted.filledQuantity(),
                submitted.filledAveragePrice(),
                StrategyService.mapOrderStatus(submitted.status()),
                submittedAt,
                Instant.now(),
                null,
                submitted.rawJson()
        );
        if (order.status() == StrategyOrderStatus.REJECTED || order.status() == StrategyOrderStatus.FAILED) {
            orderRepository.save(order);
            String failureMessage = failureMessage(submitted.rawJson(), stage);
            if (isQueueableSessionRejection(submitted.rawJson())) {
                strategy.clearLastError();
                strategy.setLatestOrderStatus("QUEUED_FOR_OPEN");
                strategy.setLatestAlpacaOrderId("");
                strategy.setLastTriggeredRuleType(mapStageToRuleName(stage));
                stateMachine.transition(strategy, StrategyLifecycleState.QUEUED_FOR_OPEN,
                        StrategyEventType.ORDER_STATUS_UPDATED,
                        "Order queued for next market open after broker rejection",
                        submitted.rawJson());
                strategyRepository.save(strategy);
                return order;
            }
            strategy.setLastError(failureMessage);
            strategy.setLatestOrderStatus(BrokerOrderStatusUtil.normalize(submitted.status()));
            strategy.setLatestAlpacaOrderId("");
            stateMachine.transition(strategy, StrategyLifecycleState.FAILED,
                    StrategyEventType.STRATEGY_FAILED,
                    failureMessage,
                    submitted.rawJson());
            strategyRepository.save(strategy);
            return order;
        }
        orderRepository.save(order);
        strategy.setLatestOrderStatus(BrokerOrderStatusUtil.normalize(submitted.status()));
        strategy.setLatestAlpacaOrderId(submitted.orderId());
        strategy.setLastTriggeredRuleType(mapStageToRuleName(stage));
        stateMachine.transition(strategy, lifecycleState, eventType, message, submitted.rawJson());
        strategyRepository.save(strategy);
        return order;
    }

    private StrategyOrder submitTrailingStopSellOrder(
            Strategy strategy,
            BigDecimal quantity,
            StrategyLifecycleState lifecycleState,
            String message,
            StrategyEventType eventType
    ) {
        if (!isAutoExecutionAllowed(strategy.id())) {
            return null;
        }
        int requestedQuantity = quantity.setScale(0, java.math.RoundingMode.DOWN).intValue();
        if (requestedQuantity <= 0) {
            return null;
        }
        String clientOrderId = StrategyService.buildClientOrderId(strategy.id(), StrategyStage.PROFIT_EXIT);
        BigDecimal trailPercent = strategy.profitHoldType() == ProfitHoldType.PERCENT_TRAILING
                ? strategy.profitHoldPercent()
                : BigDecimal.ZERO;
        BigDecimal trailPrice = strategy.profitHoldType() == ProfitHoldType.FIXED_AMOUNT_TRAILING
                ? strategy.profitHoldAmount()
                : BigDecimal.ZERO;
        AlpacaOrderData submitted = alpacaClient.submitTrailingStopSellOrder(
                strategy.symbol(),
                requestedQuantity,
                trailPercent,
                trailPrice,
                clientOrderId
        );
        Instant submittedAt = submitted.submittedAt() == null ? Instant.now() : submitted.submittedAt();
        StrategyOrder order = new StrategyOrder(
                UUID.randomUUID().toString(),
                strategy.id(),
                StrategyStage.PROFIT_EXIT,
                submitted.orderId(),
                clientOrderId,
                strategy.symbol(),
                StrategyOrderSide.SELL,
                StrategyOrderType.TRAILING_STOP,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.valueOf(requestedQuantity),
                submitted.filledQuantity(),
                submitted.filledAveragePrice(),
                StrategyService.mapOrderStatus(submitted.status()),
                submittedAt,
                Instant.now(),
                null,
                submitted.rawJson()
        );
        if (order.status() == StrategyOrderStatus.REJECTED || order.status() == StrategyOrderStatus.FAILED) {
            orderRepository.save(order);
            String failureMessage = failureMessage(submitted.rawJson(), StrategyStage.PROFIT_EXIT);
            if (isQueueableSessionRejection(submitted.rawJson())) {
                strategy.clearLastError();
                strategy.setLatestOrderStatus("QUEUED_FOR_OPEN");
                strategy.setLatestAlpacaOrderId("");
                strategy.setLastTriggeredRuleType("ALPACA_TRAILING_STOP");
                stateMachine.transition(strategy, StrategyLifecycleState.QUEUED_FOR_OPEN,
                        StrategyEventType.ORDER_STATUS_UPDATED,
                        "Order queued for next market open after broker rejection",
                        submitted.rawJson());
                strategyRepository.save(strategy);
                return order;
            }
            strategy.setLastError(failureMessage);
            strategy.setLatestOrderStatus(BrokerOrderStatusUtil.normalize(submitted.status()));
            strategy.setLatestAlpacaOrderId("");
            stateMachine.transition(strategy, StrategyLifecycleState.FAILED,
                    StrategyEventType.STRATEGY_FAILED,
                    failureMessage,
                    submitted.rawJson());
            strategyRepository.save(strategy);
            return order;
        }
        orderRepository.save(order);
        strategy.setLatestOrderStatus(BrokerOrderStatusUtil.normalize(submitted.status()));
        strategy.setLatestAlpacaOrderId(submitted.orderId());
        strategy.setLastTriggeredRuleType("ALPACA_TRAILING_STOP");
        stateMachine.transition(strategy, lifecycleState, eventType, message, submitted.rawJson());
        strategyRepository.save(strategy);
        return order;
    }

    private BigDecimal trailingThreshold(Strategy strategy) {
        BigDecimal high = strategy.highestObservedPriceAfterTarget();
        if (strategy.profitHoldType() == ProfitHoldType.FIXED_AMOUNT_TRAILING) {
            return Monetary.round(high.subtract(strategy.profitHoldAmount()));
        }
        return Monetary.round(high.multiply(BigDecimal.ONE.subtract(strategy.profitHoldPercent().divide(new BigDecimal("100")))));
    }

    private boolean hasPendingOrFilledStage(List<StrategyOrder> orders, StrategyStage stage) {
        return orders.stream().anyMatch(order -> order.stage() == stage && (order.isPending() || order.status() == StrategyOrderStatus.FILLED));
    }

    private boolean hasPendingStage(List<StrategyOrder> orders, StrategyStage stage) {
        return orders.stream().anyMatch(order -> order.stage() == stage && order.isPending());
    }

    private boolean hasPendingOrFilledExitOrder(List<StrategyOrder> orders, StrategyStage stage) {
        return orders.stream().anyMatch(order -> order.stage() == stage && (order.isPending() || order.status() == StrategyOrderStatus.FILLED));
    }

    private boolean shouldActivateStopLossMonitoring(Strategy strategy, List<StrategyOrder> orders) {
        if (!strategy.automatedStopLossEnabled()) {
            return false;
        }
        StrategyLifecycleState state = strategy.currentState();
        if (state == StrategyLifecycleState.SELL_PLACED
                || state == StrategyLifecycleState.SELL_PARTIALLY_FILLED
                || state == StrategyLifecycleState.PROFIT_HOLD_ACTIVE) {
            return false;
        }
        return orders.stream().noneMatch(order -> isExitStage(order.stage()) && (order.isPending() || order.status() == StrategyOrderStatus.FILLED));
    }

    private Optional<StrategyOrder> latestFilledExitOrder(List<StrategyOrder> orders) {
        return orders.stream()
                .filter(order -> isExitStage(order.stage()) && order.status() == StrategyOrderStatus.FILLED)
                .max(Comparator.comparing(StrategyOrder::filledAt,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(StrategyOrder::submittedAt, Comparator.nullsLast(Comparator.naturalOrder())));
    }

    private boolean isExitStage(StrategyStage stage) {
        return stage == StrategyStage.TARGET_SELL
                || stage == StrategyStage.PROFIT_EXIT
                || stage == StrategyStage.STOP_LOSS
                || stage == StrategyStage.LOSS_EXIT
                || stage == StrategyStage.MANUAL_EXIT
                || stage == StrategyStage.CLOSE_POSITION;
    }

    private boolean isProfitableExitStage(StrategyStage stage) {
        return stage == StrategyStage.TARGET_SELL
                || stage == StrategyStage.PROFIT_EXIT
                || stage == StrategyStage.MANUAL_EXIT;
    }

    private boolean isStageFilled(List<StrategyOrder> orders, StrategyStage stage) {
        return orders.stream().anyMatch(order -> order.stage() == stage && order.status() == StrategyOrderStatus.FILLED);
    }

    private RiskProjection projectedRisk(Strategy strategy, BigDecimal newOrderQty, BigDecimal newOrderPrice) {
        List<StrategyOrder> orders = orderRepository.findByStrategyId(strategy.id());
        BigDecimal projectedQty = newOrderQty;
        BigDecimal projectedCapital = Monetary.round(newOrderPrice.multiply(newOrderQty));

        for (StrategyOrder order : orders) {
            if (order.side() != StrategyOrderSide.BUY) {
                continue;
            }
            if (order.status() == StrategyOrderStatus.CANCELED
                    || order.status() == StrategyOrderStatus.REJECTED
                    || order.status() == StrategyOrderStatus.FAILED) {
                continue;
            }
            projectedQty = projectedQty.add(order.requestedQuantity());
            projectedCapital = Monetary.round(projectedCapital.add(order.limitPrice().multiply(order.requestedQuantity())));
        }

        if (projectedQty.compareTo(BigDecimal.valueOf(strategy.maxTotalQuantity())) > 0) {
            return new RiskProjection(false, "Projected quantity exceeds maxTotalQuantity");
        }
        if (projectedCapital.compareTo(strategy.maxCapitalAllowed()) > 0) {
            return new RiskProjection(false, "Projected capital exceeds maxCapitalAllowed");
        }
        return new RiskProjection(true, "");
    }

    private record RiskProjection(boolean allowed, String reason) {}

    private boolean isQueueableSessionRejection(String rawJson) {
        String normalized = rawJson == null ? "" : rawJson.toLowerCase();
        return normalized.contains("market is closed")
                || normalized.contains("outside market hours")
                || normalized.contains("extended_hours")
                || normalized.contains("time_in_force")
                || normalized.contains("session");
    }

    private String failureMessage(String rawJson, StrategyStage stage) {
        if (rawJson == null || rawJson.isBlank()) {
            return "Broker rejected " + stage.name() + " order";
        }
        String compact = rawJson.replace('\n', ' ').replace('\r', ' ').trim();
        if (compact.length() > 220) {
            compact = compact.substring(0, 220) + "...";
        }
        return "Broker rejected " + stage.name() + " order: " + compact;
    }

    private void logPoll(Strategy strategy, String scope, String status, String details) {
        if ("STARTED".equals(status)) {
            LOGGER.info("");
            LOGGER.info(() -> "========== [" + strategy.symbol() + "] Poll Evaluation ==========");
        }
        LOGGER.info(() -> "[POLL][" + strategy.symbol() + "][" + scope + "][" + status + "] " + details);
        if ("COMPLETED".equals(status)) {
            LOGGER.info(() -> "========== [" + strategy.symbol() + "] End Poll ==========");
            LOGGER.info("");
        }
    }

    private void logRule(Strategy strategy, String ruleName, String status, String details, List<RuleOutcome> outcomes) {
        LOGGER.info(() -> "[POLL][" + strategy.symbol() + "][" + ruleName + "][" + status + "] " + details);
        if (outcomes != null) {
            outcomes.add(new RuleOutcome(ruleName, status, details));
        }
    }

    private String mapStageToRuleName(StrategyStage stage) {
        return switch (stage) {
            case BASE_BUY -> "BUY_RULE";
            case BUY_LIMIT_1 -> "LOSS_BUY_RULE";
            case BUY_LIMIT_2 -> "LOSS_INVESTMENT_BUY_RULE";
            case TARGET_SELL -> "SELL_RULE";
            case STOP_LOSS -> "STOP_LOSS_RULE";
            case LOSS_EXIT, PROFIT_EXIT, MANUAL_EXIT, CLOSE_POSITION -> stage.name();
        };
    }

    private boolean isAutoExecutionAllowed(String strategyId) {
        Optional<Strategy> latest = strategyRepository.findById(strategyId);
        return latest.isPresent() && latest.get().status() == StrategyStatus.ACTIVE;
    }
}
