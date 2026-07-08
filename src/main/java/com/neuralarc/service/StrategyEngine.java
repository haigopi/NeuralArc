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

import static com.neuralarc.service.BrokerRejectionMessageFormatter.failureMessage;
import static com.neuralarc.service.BrokerRejectionMessageFormatter.isQueueableSessionRejection;
import static com.neuralarc.service.StrategyBuyRiskProjector.projectedRisk;

public class StrategyEngine {
    private static final Logger LOGGER = Logger.getLogger(StrategyEngine.class.getName());
    private static final Logger TRADE_LOGGER = Logger.getLogger("com.neuralarc.trade");

    private final StrategyRepository strategyRepository;
    private final StrategyOrderRepository orderRepository;
    private final StrategyStateMachine stateMachine;
    private final AlpacaClient alpacaClient;
    private final AppSettingsService appSettingsService;
    private final MarketHoursService marketHoursService;
    private final StrategyProfitControlEvaluator profitControlEvaluator;
    private final TradeEmailNotificationService emailNotificationService;
    private final ExpiredEntryOrderResubmitter expiredEntryOrderResubmitter;
    private final BaseBuyPriceGuard baseBuyPriceGuard = new BaseBuyPriceGuard();
    private WorkspaceCodeResolver workspaceCodeResolver = WorkspaceCodeResolver.unassigned();

    void setWorkspaceCodeResolver(WorkspaceCodeResolver resolver) {
        this.workspaceCodeResolver = resolver == null ? WorkspaceCodeResolver.unassigned() : resolver;
    }

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
        this(
                strategyRepository,
                orderRepository,
                stateMachine,
                alpacaClient,
                appSettingsService,
                marketHoursService,
                new TradeEmailNotificationService(appSettingsService)
        );
    }

    StrategyEngine(
            StrategyRepository strategyRepository,
            StrategyOrderRepository orderRepository,
            StrategyStateMachine stateMachine,
            AlpacaClient alpacaClient,
            AppSettingsService appSettingsService,
            MarketHoursService marketHoursService,
            TradeEmailNotificationService emailNotificationService
    ) {
        this.strategyRepository = strategyRepository;
        this.orderRepository = orderRepository;
        this.stateMachine = stateMachine;
        this.alpacaClient = alpacaClient;
        this.appSettingsService = appSettingsService;
        this.marketHoursService = marketHoursService;
        this.emailNotificationService = emailNotificationService;
        this.expiredEntryOrderResubmitter = new ExpiredEntryOrderResubmitter(orderRepository, alpacaClient);
        this.profitControlEvaluator = new StrategyProfitControlEvaluator(
                strategyRepository,
                stateMachine,
                alpacaClient,
                this::submitSellOrder,
                this::submitTrailingStopSellOrder
        );
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

    public void setEmailNotificationListener(TradeEmailNotificationService.EmailNotificationListener listener) {
        emailNotificationService.setNotificationListener(listener);
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
                && !StrategyStageSupport.isStageFilled(orders, StrategyStage.BASE_BUY)
                && !hasPendingStage(orders, StrategyStage.BASE_BUY)) {
            submitBaseBuy(strategy, false, latestPrice);
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
            profitControlEvaluator.evaluate(strategy, position.get(), latestPrice, orders, outcomes);
        } else {
            logRule(strategy, "STOP_LOSS", "SKIPPED", "No open position", outcomes);
            logRule(strategy, "PROFIT_CONTROLS", "SKIPPED", "No open position", outcomes);
            maybeRestartStrategy(strategy, orders, latestPrice);
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
        return submitBaseBuy(strategy, enforceTradableSession, null);
    }

    private StrategyOrder submitBaseBuy(Strategy strategy, boolean enforceTradableSession, BigDecimal currentPrice) {
        BaseBuyPriceGuard.GuardedPrice guardedPrice = baseBuyPriceGuard.guardedBaseBuyPrice(
                alpacaClient,
                strategy.symbol(),
                strategy.baseBuyLimitPrice(),
                currentPrice,
                strategy.baseBuyRepostReductionPercent()
        );
        if (!guardedPrice.reason().isBlank()) {
            logPoll(strategy, "BASE_BUY_GUARD", "APPLIED", guardedPrice.reason());
        }
        return submitBuyOrder(strategy, StrategyStage.BASE_BUY, strategy.baseBuyQuantity(), guardedPrice.price(),
                StrategyLifecycleState.BASE_BUY_PLACED, "Base buy order submitted", enforceTradableSession);
    }

    public void resumeStrategy(Strategy strategy) {
        if (!isAutoExecutionAllowed(strategy.id())) {
            logPoll(strategy, "RESUME", "SKIPPED", "Polling not resumed because strategy is not ACTIVE");
            return;
        }
        List<StrategyOrder> orders = orderRepository.findByStrategyId(strategy.id());
        List<AlpacaOrderData> remoteOpenOrders = alpacaClient.getOpenOrders(strategy.symbol());
        Optional<AlpacaPositionData> position = alpacaClient.getPosition(strategy.symbol());

        if (!remoteOpenOrders.isEmpty()) {
            reconcile(strategy);
            return;
        }

        if (position.isEmpty() || !position.get().exists()) {
            if (!StrategyStageSupport.isStageFilled(orders, StrategyStage.BASE_BUY)) {
                submitBaseBuy(strategy, false);
                return;
            }
        }

        reconcile(strategy);
    }

    public boolean canAutoRetryFailed(Strategy strategy) {
        return expiredEntryOrderResubmitter.canAutoRetryFailed(strategy);
    }

    public boolean canAutoResubmitExpiredEntryOrder(Strategy strategy) {
        return expiredEntryOrderResubmitter.canAutoResubmit(strategy);
    }

    public StrategyOrder resubmitExpiredEntryOrder(Strategy strategy) {
        Optional<StrategyOrder> expiredOrder = expiredEntryOrderResubmitter.resolveOrder(strategy);
        Optional<StrategyStage> stage = expiredEntryOrderResubmitter.resolveStage(strategy);
        if (stage.isEmpty()) {
            return null;
        }
        return switch (stage.get()) {
            case BASE_BUY -> submitBaseBuy(strategy, false);
            case BUY_LIMIT_1 -> submitBuyOrder(strategy, StrategyStage.BUY_LIMIT_1,
                    strategy.buyLimit1Quantity(), strategy.buyLimit1Price(),
                    StrategyLifecycleState.BUY_LIMIT_1_PLACED,
                    "Buy Limit 1 order resubmitted after expiry", false);
            case BUY_LIMIT_2 -> submitBuyOrder(strategy, StrategyStage.BUY_LIMIT_2,
                    strategy.buyLimit2Quantity(), strategy.buyLimit2Price(),
                    StrategyLifecycleState.BUY_LIMIT_2_PLACED,
                    "Buy Limit 2 order resubmitted after expiry", false);
            case MANUAL_BUY -> expiredOrder
                    .map(order -> submitBuyOrder(strategy, StrategyStage.MANUAL_BUY,
                            order.requestedQuantityInt(), order.limitPrice(),
                            StrategyLifecycleState.BASE_BUY_FILLED,
                            "Manual limit buy order resubmitted after expiry", false))
                    .orElse(null);
            default -> null;
        };
    }

    private boolean ensureRemoteOrderPresence(
            Strategy strategy,
            List<StrategyOrder> orders,
            List<AlpacaOrderData> remoteOpenOrders,
            Optional<AlpacaPositionData> position
    ) {
        if (!isAutoExecutionAllowed(strategy.id())) {
            if (isManuallyCanceled(strategy.id())) {
                logPoll(strategy, "ORDER_RECON", "SKIPPED",
                        "Skipping auto placement because strategy is manually cancelled");
            }
            return false;
        }
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

        if (!StrategyStageSupport.isStageFilled(orders, StrategyStage.BASE_BUY)) {
            submitBaseBuy(strategy);
            return true;
        }
        if (StrategyStageSupport.isStageFilled(orders, StrategyStage.BASE_BUY)
                && strategy.lossBuyLevelsEnabled()
                && strategy.buyLimit1Quantity() > 0
                && !StrategyStageSupport.isStageFilled(orders, StrategyStage.BUY_LIMIT_1)) {
            submitBuyOrder(strategy, StrategyStage.BUY_LIMIT_1, strategy.buyLimit1Quantity(), strategy.buyLimit1Price(),
                    StrategyLifecycleState.BUY_LIMIT_1_PLACED, "Buy Limit 1 recreated after missing Alpaca order", true);
            return true;
        }
        if (StrategyStageSupport.isStageFilled(orders, StrategyStage.BUY_LIMIT_1)
                && strategy.lossBuyLevelsEnabled()
                && strategy.buyLimit2Quantity() > 0
                && !StrategyStageSupport.isStageFilled(orders, StrategyStage.BUY_LIMIT_2)) {
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
        if (!StrategyStageSupport.isStageFilled(orders, StrategyStage.BASE_BUY)) {
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
        if (!StrategyStageSupport.isStageFilled(orders, StrategyStage.BUY_LIMIT_1)) {
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


    private void maybeRestartStrategy(Strategy strategy, List<StrategyOrder> orders, BigDecimal latestPrice) {
        if (!isAutoExecutionAllowed(strategy.id())) {
            if (isManuallyCanceled(strategy.id())) {
                logPoll(strategy, "RESTART", "SKIPPED",
                        "Manual cancel detected; waiting for user to click Place Limit Buy Again");
            }
            return;
        }
        // Called only when no open position exists, so restart is inherently full-exit only.
        Optional<StrategyOrder> latestFilledExitOrder = latestFilledExitOrder(orders);
        if (latestFilledExitOrder.isEmpty()) {
            return;
        }

        StrategyOrder filledExitOrder = latestFilledExitOrder.get();
        // Defensive exits (e.g., CLOSE_POSITION, STOP_LOSS) always complete the cycle.
        // Manual exits may restart if the strategy is configured to repeat after exit.
        if (!strategy.restartAfterExitEnabled() || !StrategyStageSupport.isProfitableExitStage(filledExitOrder.stage())) {
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
        submitBaseBuy(strategy, true, latestPrice);
    }

    void refreshOrderStatuses(Strategy strategy) {
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

    public Optional<String> applyStreamingOrderUpdate(AlpacaOrderData orderData) {
        if (orderData == null) {
            return Optional.empty();
        }

        Optional<StrategyOrder> matchingOrder = orderRepository.findByAlpacaOrderId(orderData.orderId());
        if (matchingOrder.isEmpty()) {
            matchingOrder = orderRepository.findByClientOrderId(orderData.clientOrderId());
        }
        if (matchingOrder.isEmpty()) {
            return Optional.empty();
        }

        StrategyOrder order = matchingOrder.get();
        Optional<Strategy> maybeStrategy = strategyRepository.findById(order.strategyId());
        if (maybeStrategy.isEmpty()) {
            return Optional.empty();
        }

        Strategy strategy = maybeStrategy.get();
        StrategyOrderStatus status = applyOrderUpdate(strategy, order, orderData);
        if (status == StrategyOrderStatus.FILLED || status == StrategyOrderStatus.PARTIALLY_FILLED) {
            reconcile(strategy);
        }
        return Optional.of(strategy.id());
    }

    private StrategyOrderStatus applyOrderUpdate(Strategy strategy, StrategyOrder order, AlpacaOrderData data) {
        StrategyOrderStatus previousStatus = order.status();
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
        Optional<Strategy> latest = strategyRepository.findById(strategy.id());
        Strategy target = latest.orElse(strategy);
        if (order.side() == StrategyOrderSide.SELL
                && status == StrategyOrderStatus.FILLED
                && previousStatus != StrategyOrderStatus.FILLED) {
            logSellCompleted(target, order);
            emailNotificationService.notifySellExecuted(target, order);
        }
        target.setLatestOrderStatus(BrokerOrderStatusUtil.normalize(data.status()));
        target.setLatestAlpacaOrderId(order.alpacaOrderId() == null ? "" : order.alpacaOrderId());
        if ("expired".equals(BrokerOrderStatusUtil.normalize(data.status()))) {
            target.setStatus(StrategyStatus.FAILED);
            target.setCurrentState(StrategyLifecycleState.FAILED);
            target.setLastError("Alpaca order expired");
            stateMachine.transition(target, StrategyLifecycleState.FAILED, StrategyEventType.ORDER_STATUS_UPDATED,
                    "Alpaca order expired", data.rawJson());
            strategyRepository.save(target);
            return status;
        }
        if (target.status() != StrategyStatus.ACTIVE) {
            strategyRepository.save(target);
            if (target.pauseReason() == PauseReason.MANUAL_LIMIT_BUY_CANCELED) {
                logPoll(target, "ORDER_RECON", "SKIPPED",
                        "Skipping lifecycle transition because strategy is manually cancelled");
            }
            return status;
        }
        transitionForOrderUpdate(target, order, status);
        strategyRepository.save(target);
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
            case MANUAL_BUY -> strategy.currentState() == null ? StrategyLifecycleState.BASE_BUY_FILLED : strategy.currentState();
            case TARGET_SELL, PROFIT_EXIT, STOP_LOSS, LOSS_EXIT, MANUAL_EXIT, CLOSE_POSITION -> status == StrategyOrderStatus.PARTIALLY_FILLED
                    ? StrategyLifecycleState.SELL_PARTIALLY_FILLED
                    : status == StrategyOrderStatus.FILLED
                    ? StrategyLifecycleState.COMPLETED
                    : StrategyLifecycleState.SELL_PLACED;
            default -> strategy.currentState() == null ? StrategyLifecycleState.VALIDATED : strategy.currentState();
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
        StrategyBuyRiskProjector.RiskProjection projection = projectedRisk(
                strategy,
                orderRepository.findByStrategyId(strategy.id()),
                BigDecimal.valueOf(quantity),
                limitPrice
        );
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
        String clientOrderId = StrategyService.buildClientOrderId(strategy, stage, workspaceCodeResolver);
        TimeInForce timeInForce = strategy.timeInForce() == null ? TimeInForce.DAY : strategy.timeInForce();
        AlpacaOrderData submitted = alpacaClient.submitLimitBuyOrder(
                strategy.symbol(),
                quantity,
                limitPrice,
                clientOrderId,
                timeInForce
        );
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
                submitted.rawJson(),
                timeInForce
        );
        if (order.status() == StrategyOrderStatus.REJECTED || order.status() == StrategyOrderStatus.FAILED) {
            orderRepository.save(order);
            String normalizedSubmittedStatus = BrokerOrderStatusUtil.normalize(submitted.status());
            if (isRetryableBrokerConnectivityFailure(normalizedSubmittedStatus)) {
                strategy.setLastError("Unable to reach broker. Retrying position.");
                strategy.setLatestOrderStatus(normalizedSubmittedStatus);
                strategy.setLatestAlpacaOrderId("");
                stateMachine.transition(strategy, strategy.currentState(),
                        StrategyEventType.ORDER_STATUS_UPDATED,
                        "Unable to reach broker; retrying order submission",
                        submitted.rawJson());
                strategyRepository.save(strategy);
                return order;
            }
            String failureMessage = failureMessage(submitted.rawJson(), stage);
            if (isQueueableSessionRejection(submitted.rawJson())) {
                strategy.clearLastError();
                strategy.setLatestOrderStatus("QUEUED_FOR_OPEN");
                strategy.setLatestAlpacaOrderId("");
                strategy.setLastTriggeredRuleType(StrategyStageSupport.ruleNameForStage(stage));
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
        emailNotificationService.notifyBuyExpected(strategy, order);
        strategy.setLatestOrderStatus(BrokerOrderStatusUtil.normalize(submitted.status()));
        strategy.setLatestAlpacaOrderId(submitted.orderId());
        strategy.setLastTriggeredRuleType(StrategyStageSupport.ruleNameForStage(stage));
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
        String clientOrderId = StrategyService.buildClientOrderId(strategy, stage, workspaceCodeResolver);
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
            String normalizedSubmittedStatus = BrokerOrderStatusUtil.normalize(submitted.status());
            if (isRetryableBrokerConnectivityFailure(normalizedSubmittedStatus)) {
                strategy.setLastError("Unable to reach broker. Retrying position.");
                strategy.setLatestOrderStatus(normalizedSubmittedStatus);
                strategy.setLatestAlpacaOrderId("");
                stateMachine.transition(strategy, strategy.currentState(),
                        StrategyEventType.ORDER_STATUS_UPDATED,
                        "Unable to reach broker; retrying order submission",
                        submitted.rawJson());
                strategyRepository.save(strategy);
                return order;
            }
            String failureMessage = failureMessage(submitted.rawJson(), stage);
            if (isQueueableSessionRejection(submitted.rawJson())) {
                strategy.clearLastError();
                strategy.setLatestOrderStatus("QUEUED_FOR_OPEN");
                strategy.setLatestAlpacaOrderId("");
                strategy.setLastTriggeredRuleType(StrategyStageSupport.ruleNameForStage(stage));
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
        notifyImmediateFilledSell(strategy, order);
        strategy.setLatestOrderStatus(BrokerOrderStatusUtil.normalize(submitted.status()));
        strategy.setLatestAlpacaOrderId(submitted.orderId());
        strategy.setLastTriggeredRuleType(StrategyStageSupport.ruleNameForStage(stage));
        stateMachine.transition(strategy, lifecycleStateForSubmittedSell(order, lifecycleState), eventType, message, submitted.rawJson());
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
        String clientOrderId = StrategyService.buildClientOrderId(strategy, StrategyStage.PROFIT_EXIT, workspaceCodeResolver);
        boolean automaticStopSell = strategy.profitControlMode() == ProfitControlMode.AUTOMATIC_STOP_SELL;
        BigDecimal trailPercent = automaticStopSell
                ? (strategy.automaticStopSellTrailingType() == TrailingType.PERCENTAGE
                        ? strategy.automaticStopSellTrailingValue()
                        : BigDecimal.ZERO)
                : (strategy.profitHoldType() == ProfitHoldType.PERCENT_TRAILING
                        ? strategy.profitHoldPercent()
                        : BigDecimal.ZERO);
        BigDecimal trailPrice = automaticStopSell
                ? (strategy.automaticStopSellTrailingType() == TrailingType.FIXED_AMOUNT
                        ? strategy.automaticStopSellTrailingValue()
                        : BigDecimal.ZERO)
                : (strategy.profitHoldType() == ProfitHoldType.FIXED_AMOUNT_TRAILING
                        ? strategy.profitHoldAmount()
                        : BigDecimal.ZERO);
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
            String normalizedSubmittedStatus = BrokerOrderStatusUtil.normalize(submitted.status());
            if (isRetryableBrokerConnectivityFailure(normalizedSubmittedStatus)) {
                strategy.setLastError("Unable to reach broker. Retrying position.");
                strategy.setLatestOrderStatus(normalizedSubmittedStatus);
                strategy.setLatestAlpacaOrderId("");
                stateMachine.transition(strategy, strategy.currentState(),
                        StrategyEventType.ORDER_STATUS_UPDATED,
                        "Unable to reach broker; retrying order submission",
                        submitted.rawJson());
                strategyRepository.save(strategy);
                return order;
            }
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
        notifyImmediateFilledSell(strategy, order);
        strategy.setLatestOrderStatus(BrokerOrderStatusUtil.normalize(submitted.status()));
        strategy.setLatestAlpacaOrderId(submitted.orderId());
        strategy.setLastTriggeredRuleType("ALPACA_TRAILING_STOP");
        stateMachine.transition(strategy, lifecycleStateForSubmittedSell(order, lifecycleState), eventType, message, submitted.rawJson());
        strategyRepository.save(strategy);
        return order;
    }

    private void notifyImmediateFilledSell(Strategy strategy, StrategyOrder order) {
        if (order.side() != StrategyOrderSide.SELL || order.status() != StrategyOrderStatus.FILLED) {
            return;
        }
        if (order.filledAt() == null) {
            order.setFilledAt(Instant.now());
            orderRepository.save(order);
        }
        logSellCompleted(strategy, order);
        emailNotificationService.notifySellExecuted(strategy, order);
    }

    private void logSellCompleted(Strategy strategy, StrategyOrder order) {
        TRADE_LOGGER.info(() -> "SELL_COMPLETED symbol=" + strategy.symbol()
                + " stage=" + order.stage()
                + " quantity=" + order.filledQuantity().toPlainString()
                + " averagePrice=" + order.filledAveragePrice().toPlainString()
                + " alpacaOrderId=" + safeLogValue(order.alpacaOrderId())
                + " strategyId=" + strategy.id());
    }

    private String safeLogValue(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
    }

    private StrategyLifecycleState lifecycleStateForSubmittedSell(StrategyOrder order, StrategyLifecycleState fallback) {
        if (order.side() != StrategyOrderSide.SELL) {
            return fallback;
        }
        if (order.status() == StrategyOrderStatus.FILLED) {
            return StrategyLifecycleState.COMPLETED;
        }
        if (order.status() == StrategyOrderStatus.PARTIALLY_FILLED) {
            return StrategyLifecycleState.SELL_PARTIALLY_FILLED;
        }
        return fallback;
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

    private boolean isRetryableBrokerConnectivityFailure(String normalizedStatus) {
        return "failed_transport".equals(normalizedStatus);
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
        return orders.stream().noneMatch(order -> StrategyStageSupport.isExitStage(order.stage())
                && (order.isPending() || order.status() == StrategyOrderStatus.FILLED));
    }

    private Optional<StrategyOrder> latestFilledExitOrder(List<StrategyOrder> orders) {
        return orders.stream()
                .filter(order -> StrategyStageSupport.isExitStage(order.stage()) && order.status() == StrategyOrderStatus.FILLED)
                .max(Comparator.comparing(StrategyOrder::filledAt,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(StrategyOrder::submittedAt, Comparator.nullsLast(Comparator.naturalOrder())));
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

    private boolean isAutoExecutionAllowed(String strategyId) {
        Optional<Strategy> latest = strategyRepository.findById(strategyId);
        return latest.isPresent() && latest.get().status() == StrategyStatus.ACTIVE;
    }

    private boolean isManuallyCanceled(String strategyId) {
        Optional<Strategy> latest = strategyRepository.findById(strategyId);
        return latest.isPresent()
                && latest.get().status() == StrategyStatus.PAUSED
                && latest.get().pauseReason() == PauseReason.MANUAL_LIMIT_BUY_CANCELED;
    }
}
