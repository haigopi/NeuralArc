package com.neuralarc.service;

import com.neuralarc.api.AlpacaClient;
import com.neuralarc.api.AlpacaOrderData;
import com.neuralarc.api.AlpacaPositionData;
import com.neuralarc.model.StrategyOrderStatus;
import com.neuralarc.util.BrokerOrderStatusUtil;
import com.neuralarc.model.ProfitControlMode;
import com.neuralarc.model.ProfitHoldType;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyEventType;
import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyOrder;
import com.neuralarc.model.StrategyStage;
import com.neuralarc.model.ThresholdType;
import com.neuralarc.model.TimeInForce;
import com.neuralarc.model.TrailingType;
import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

final class StrategyProfitControlEvaluator {
    private static final Logger LOGGER = Logger.getLogger(StrategyProfitControlEvaluator.class.getName());
    private static final java.time.ZoneId US_EASTERN = java.time.ZoneId.of("America/New_York");

    private final StrategyRepository strategyRepository;
    private final StrategyOrderRepository orderRepository;
    private final StrategyStateMachine stateMachine;
    private final AlpacaClient alpacaClient;
    private final SellOrderSubmitter sellOrderSubmitter;
    private final TrailingStopSubmitter trailingStopSubmitter;
    private final ExitOrderCanceller exitOrderCanceller;
    private final SessionHighCache sessionHighCache;

    StrategyProfitControlEvaluator(
            StrategyRepository strategyRepository,
            StrategyOrderRepository orderRepository,
            StrategyStateMachine stateMachine,
            AlpacaClient alpacaClient,
            SellOrderSubmitter sellOrderSubmitter,
            TrailingStopSubmitter trailingStopSubmitter
    ) {
        this(strategyRepository, orderRepository, stateMachine, alpacaClient, sellOrderSubmitter,
                trailingStopSubmitter, SessionHighCache.shared());
    }

    StrategyProfitControlEvaluator(
            StrategyRepository strategyRepository,
            StrategyOrderRepository orderRepository,
            StrategyStateMachine stateMachine,
            AlpacaClient alpacaClient,
            SellOrderSubmitter sellOrderSubmitter,
            TrailingStopSubmitter trailingStopSubmitter,
            SessionHighCache sessionHighCache
    ) {
        this.sessionHighCache = sessionHighCache == null ? SessionHighCache.shared() : sessionHighCache;
        this.strategyRepository = strategyRepository;
        this.orderRepository = orderRepository;
        this.stateMachine = stateMachine;
        this.alpacaClient = alpacaClient;
        this.sellOrderSubmitter = sellOrderSubmitter;
        this.trailingStopSubmitter = trailingStopSubmitter;
        this.exitOrderCanceller = new ExitOrderCanceller(alpacaClient, orderRepository);
    }

    void evaluate(Strategy strategy, AlpacaPositionData position, BigDecimal latestPrice, List<StrategyOrder> orders,
                  List<StrategyEngine.RuleOutcome> outcomes) {
        ProfitControlMode mode = strategy.profitControlMode();
        if (mode == null || mode == ProfitControlMode.NONE) {
            if (strategy.targetSellEnabled()) {
                evaluateProfitHold(strategy, position, latestPrice, orders, outcomes);
            } else {
                logRule(strategy, "PROFIT_CONTROLS", "SKIPPED", "Target sell disabled; no profit control active", outcomes);
            }
            return;
        }

        switch (mode) {
            case SELL_TRIGGER -> evaluateSellTrigger(strategy, position, latestPrice, orders, outcomes);
            case AUTOMATIC_STOP_SELL -> evaluateAutomaticStopSell(strategy, position, latestPrice, orders, outcomes);
            case PROFIT_HOLD -> evaluateProfitHold(strategy, position, latestPrice, orders, outcomes);
            default -> logRule(strategy, "PROFIT_CONTROLS", "SKIPPED", "Unknown profit control mode: " + mode, outcomes);
        }
    }

    private void evaluateSellTrigger(Strategy strategy, AlpacaPositionData position, BigDecimal latestPrice,
                                     List<StrategyOrder> orders, List<StrategyEngine.RuleOutcome> outcomes) {
        if (!strategy.targetSellEnabled() || strategy.targetSellPrice().compareTo(BigDecimal.ZERO) <= 0) {
            logRule(strategy, "SELL_TRIGGER", "SKIPPED", "Disabled or invalid trigger price", outcomes);
            return;
        }
        BigDecimal expectedQuantity = strategy.targetSellQuantity(position.quantity());
        BrokerManagedExitOrderDecision decision = reconcileBrokerManagedExitOrder(strategy, orders, expectedQuantity,
                "SELL_TRIGGER", outcomes);
        if (decision == BrokerManagedExitOrderDecision.PRESENT) {
            return;
        }
        if (decision == BrokerManagedExitOrderDecision.REPLACE_AND_SUBMIT_NOW) {
            submitReplacementTargetSell(strategy, position, orders, expectedQuantity,
                    "Sell trigger order replaced after position update", outcomes);
            return;
        }
        if (hasPendingOrFilledExitOrder(orders, StrategyStage.TARGET_SELL)
                || hasPendingOrFilledExitOrder(orders, StrategyStage.PROFIT_EXIT)) {
            logRule(strategy, "SELL_TRIGGER", "SKIPPED", "Existing pending or filled exit order already present", outcomes);
            return;
        }
        if (shouldRecoverMissingBrokerManagedExitOrder(strategy)) {
            logRule(strategy, "SELL_TRIGGER", "RECOVERED", "Missing broker sell order detected; placing replacement", outcomes);
            submitReplacementTargetSell(strategy, position, orders, expectedQuantity,
                    "Sell trigger order restored after missing broker order", outcomes);
            return;
        }
        if (latestPrice.compareTo(strategy.targetSellPrice()) < 0) {
            logRule(strategy, "SELL_TRIGGER", "NOT_SATISFIED",
                    "latestPrice=" + latestPrice.toPlainString()
                            + " < triggerPrice=" + strategy.targetSellPrice().toPlainString(), outcomes);
            return;
        }

        logRule(strategy, "SELL_TRIGGER", "SATISFIED",
                "latestPrice=" + latestPrice.toPlainString()
                        + " >= triggerPrice=" + strategy.targetSellPrice().toPlainString()
                        + ", quantity=" + strategy.targetSellQuantity(position.quantity()).toPlainString(), outcomes);
        if (!hasNoDuplicateSellOrder(strategy, orders)) {
            logRule(strategy, "SELL_TRIGGER", "BLOCKED", "Duplicate sell order detected; skipping", outcomes);
            return;
        }
        sellOrderSubmitter.submit(strategy, StrategyStage.TARGET_SELL, expectedQuantity, latestPrice,
                StrategyLifecycleState.SELL_PLACED, "Sell trigger order submitted", StrategyEventType.TARGET_TRIGGERED);
    }

    private void evaluateAutomaticStopSell(Strategy strategy, AlpacaPositionData position, BigDecimal latestPrice,
                                           List<StrategyOrder> orders, List<StrategyEngine.RuleOutcome> outcomes) {
        BigDecimal expectedQuantity = strategy.targetSellQuantity(position.quantity());
        BrokerManagedExitOrderDecision decision = reconcileBrokerManagedExitOrder(strategy, orders, expectedQuantity,
                "AUTOMATIC_STOP_SELL", outcomes);
        if (decision == BrokerManagedExitOrderDecision.PRESENT) {
            return;
        }
        if (decision == BrokerManagedExitOrderDecision.REPLACE_AND_SUBMIT_NOW) {
            trailingStopSubmitter.submit(strategy, expectedQuantity,
                    StrategyLifecycleState.SELL_PLACED,
                    "Automatic stop sell replaced after position update",
                    StrategyEventType.ORDER_SUBMITTED);
            return;
        }
        BigDecimal threshold = calculateProfitActivationThreshold(strategy, position);
        if (threshold.compareTo(BigDecimal.ZERO) <= 0) {
            logRule(strategy, "AUTOMATIC_STOP_SELL", "SKIPPED", "Profit activation threshold not configured", outcomes);
            return;
        }
        if (hasPendingOrFilledExitOrder(orders, StrategyStage.TARGET_SELL)
                || hasPendingOrFilledExitOrder(orders, StrategyStage.PROFIT_EXIT)) {
            logRule(strategy, "AUTOMATIC_STOP_SELL", "SKIPPED", "Existing pending or filled exit order already present", outcomes);
            return;
        }
        if (shouldRecoverMissingBrokerManagedExitOrder(strategy)) {
            logRule(strategy, "AUTOMATIC_STOP_SELL", "RECOVERED",
                    "Missing broker trailing stop detected; placing replacement", outcomes);
            trailingStopSubmitter.submit(strategy, expectedQuantity,
                    StrategyLifecycleState.SELL_PLACED,
                    "Automatic stop sell restored after missing broker order",
                    StrategyEventType.ORDER_SUBMITTED);
            return;
        }
        if (latestPrice.compareTo(threshold) < 0) {
            logRule(strategy, "AUTOMATIC_STOP_SELL", "NOT_SATISFIED",
                    "latestPrice=" + latestPrice.toPlainString()
                            + " < profitThreshold=" + threshold.toPlainString(), outcomes);
            return;
        }

        logRule(strategy, "AUTOMATIC_STOP_SELL", "SATISFIED",
                "latestPrice=" + latestPrice.toPlainString()
                        + " >= profitThreshold=" + threshold.toPlainString()
                        + ", will place broker trailing stop", outcomes);
        if (!hasNoDuplicateSellOrder(strategy, orders)) {
            logRule(strategy, "AUTOMATIC_STOP_SELL", "BLOCKED", "Duplicate sell order detected; skipping", outcomes);
            return;
        }
        trailingStopSubmitter.submit(strategy, expectedQuantity,
                StrategyLifecycleState.SELL_PLACED,
                "Automatic stop sell submitted after profit threshold",
                StrategyEventType.ORDER_SUBMITTED);
    }

    private void evaluateProfitHold(Strategy strategy, AlpacaPositionData position, BigDecimal latestPrice,
                                    List<StrategyOrder> orders, List<StrategyEngine.RuleOutcome> outcomes) {
        boolean explicitProfitHoldMode = strategy.profitControlMode() == ProfitControlMode.PROFIT_HOLD;
        BigDecimal activationThreshold = explicitProfitHoldMode
                ? calculateProfitActivationThreshold(strategy, position)
                : strategy.targetSellPrice();
        String activationLabel = explicitProfitHoldMode ? "profitActivationThreshold" : "targetPrice";

        if ((!explicitProfitHoldMode && !strategy.targetSellEnabled()) || activationThreshold.compareTo(BigDecimal.ZERO) <= 0) {
            logRule(strategy, "TARGET_SELL", "SKIPPED", "Disabled or invalid target sell price", outcomes);
            logRule(strategy, "PROFIT_HOLD", "SKIPPED",
                    explicitProfitHoldMode
                            ? "Profit activation threshold is not configured"
                            : "Target sell is not active", outcomes);
            return;
        }
        boolean localOnlyProfitHold = strategy.profitHoldEnabled() && !strategy.alpacaTrailingStopEnabled();
        boolean profitHoldModeDisabled = explicitProfitHoldMode && !strategy.profitHoldEnabled();
        if (!localOnlyProfitHold && !profitHoldModeDisabled) {
            BigDecimal expectedQuantity = strategy.targetSellQuantity(position.quantity());
            String ruleName = strategy.alpacaTrailingStopEnabled() ? "ALPACA_TRAILING_STOP" : "TARGET_SELL";
            BrokerManagedExitOrderDecision decision = reconcileBrokerManagedExitOrder(strategy, orders, expectedQuantity,
                    ruleName, outcomes);
            if (decision == BrokerManagedExitOrderDecision.PRESENT) {
                logRule(strategy, "PROFIT_HOLD", "SKIPPED", "Existing pending or filled exit order already present", outcomes);
                return;
            }
            if (decision == BrokerManagedExitOrderDecision.REPLACE_AND_SUBMIT_NOW) {
                if (strategy.alpacaTrailingStopEnabled()) {
                    trailingStopSubmitter.submit(strategy, expectedQuantity,
                            StrategyLifecycleState.SELL_PLACED,
                            "Broker trailing stop order replaced after position update",
                            StrategyEventType.ORDER_SUBMITTED);
                } else {
                    submitReplacementTargetSell(strategy, position, orders, expectedQuantity,
                            "Target sell order replaced after position update", outcomes);
                }
                return;
            }
            if (shouldRecoverMissingBrokerManagedExitOrder(strategy)) {
                logRule(strategy, ruleName, "RECOVERED",
                        "Missing broker-managed sell order detected; placing replacement", outcomes);
                if (strategy.alpacaTrailingStopEnabled()) {
                    trailingStopSubmitter.submit(strategy, expectedQuantity,
                            StrategyLifecycleState.SELL_PLACED,
                            "Broker trailing stop restored after missing broker order",
                            StrategyEventType.ORDER_SUBMITTED);
                } else {
                    submitReplacementTargetSell(strategy, position, orders, expectedQuantity,
                            "Target sell restored after missing broker order", outcomes);
                }
                return;
            }
        }
        // A resting profit-hold exit is this rule's own order, and it has to stay adjustable: bailing
        // out here because "an exit order is present" is what would freeze the trail at the first
        // price it armed on, so only a filled one — or somebody else's exit — stops the evaluation.
        boolean restingTrailToManage = localOnlyProfitHold
                && hasPendingExitOrder(orders, StrategyStage.PROFIT_EXIT)
                && !hasFilledExitOrder(orders, StrategyStage.PROFIT_EXIT);
        if (hasPendingOrFilledExitOrder(orders, StrategyStage.TARGET_SELL)
                || (hasPendingOrFilledExitOrder(orders, StrategyStage.PROFIT_EXIT) && !restingTrailToManage)) {
            logRule(strategy, "TARGET_SELL", "SKIPPED", "Existing pending or filled exit order already present", outcomes);
            logRule(strategy, "PROFIT_HOLD", "SKIPPED", "Existing pending or filled exit order already present", outcomes);
            return;
        }

        boolean profitHoldActive = strategy.currentState() == StrategyLifecycleState.PROFIT_HOLD_ACTIVE
                || strategy.highestObservedPriceAfterTarget().compareTo(BigDecimal.ZERO) > 0;
        // The engine only sees the price at the instant it polls. A stock that touches the threshold
        // between two polls used to go unnoticed for good, so the session high arms the hold too.
        BigDecimal sessionHigh = sessionHighCache.highFor(strategy.symbol(), java.time.LocalDate.now(US_EASTERN));
        BigDecimal observedHigh = sessionHigh.compareTo(latestPrice) > 0 ? sessionHigh : latestPrice;
        if (!profitHoldActive && observedHigh.compareTo(activationThreshold) < 0) {
            logRule(strategy, "TARGET_SELL", "NOT_SATISFIED",
                    "latestPrice=" + latestPrice.toPlainString()
                            + ", sessionHigh=" + observedHigh.toPlainString()
                            + " < " + activationLabel + "=" + activationThreshold.toPlainString(), outcomes);
            logRule(strategy, "PROFIT_HOLD", "SKIPPED", "Profit activation threshold has not been reached", outcomes);
            return;
        }

        if (!strategy.profitHoldEnabled()) {
            if (explicitProfitHoldMode) {
                logRule(strategy, "PROFIT_HOLD", "SKIPPED", "Profit Hold mode selected but Profit Hold is disabled", outcomes);
                return;
            }
            submitLegacyProfitExit(strategy, position, latestPrice, outcomes);
            return;
        }
        if (strategy.alpacaTrailingStopEnabled()) {
            submitLegacyBrokerTrailingStop(strategy, position, latestPrice, outcomes);
            return;
        }

        logRule(strategy, "TARGET_SELL", "SATISFIED",
                "latestPrice=" + latestPrice.toPlainString()
                        + ", sessionHigh=" + observedHigh.toPlainString()
                        + " >= " + activationLabel + "=" + activationThreshold.toPlainString(), outcomes);
        if (!profitHoldActive) {
            strategy.updateHighestObservedPriceAfterTarget(observedHigh);
            String armedAt = observedHigh.compareTo(latestPrice) > 0
                    ? observedHigh.toPlainString() + " (today's high; the price came back to $" + latestPrice.toPlainString() + ")"
                    : observedHigh.toPlainString();
            logRule(strategy, "PROFIT_HOLD", "ARMED",
                    "Threshold $" + activationThreshold.toPlainString() + " reached at $" + armedAt, outcomes);
            stateMachine.transition(strategy, StrategyLifecycleState.PROFIT_HOLD_ACTIVE,
                    StrategyEventType.PROFIT_HOLD_ARMED,
                    "Profit hold armed at $" + armedAt,
                    "{\"highest\":\"" + strategy.highestObservedPriceAfterTarget().toPlainString() + "\"}");
        } else {
            strategy.updateHighestObservedPriceAfterTarget(observedHigh);
        }
        restProfitHoldExit(strategy, position, orders, latestPrice, outcomes);
    }

    /**
     * Keeps a resting limit sell at the broker for an armed profit hold, trailing it up as the stock
     * makes new highs.
     *
     * <p>This is the whole point of the rework: a resting order fills between polls, so an exit no
     * longer depends on the app watching at the right second. The order is raised when the trail
     * moves up by at least a cent, and never lowered.
     */
    private void restProfitHoldExit(Strategy strategy, AlpacaPositionData position, List<StrategyOrder> orders,
                                    BigDecimal latestPrice, List<StrategyEngine.RuleOutcome> outcomes) {
        BigDecimal peak = strategy.highestObservedPriceAfterTarget();
        BigDecimal averageCost = position != null && position.avgEntryPrice() != null
                && position.avgEntryPrice().signum() > 0
                ? position.avgEntryPrice()
                : strategy.baseBuyLimitPrice();
        ProfitHoldExitPricing.Plan plan = ProfitHoldExitPricing.plan(peak, strategy.profitHoldType(),
                strategy.profitHoldAmount(), strategy.profitHoldPercent(), averageCost);
        if (plan.limitPrice().signum() <= 0) {
            logRule(strategy, "PROFIT_HOLD", "SKIPPED", "No usable trailing exit price yet", outcomes);
            strategyRepository.save(strategy);
            return;
        }
        BigDecimal quantity = strategy.targetSellQuantity(position.quantity());
        StrategyOrder resting = workingProfitExit(orders);
        if (resting == null) {
            logRule(strategy, "PROFIT_HOLD", "PLACED",
                    "Resting limit sell " + quantity.toPlainString() + " @ $" + plan.limitPrice().toPlainString()
                            + " — " + plan.describe(peak) + "; it fills at the broker even between polls", outcomes);
            sellOrderSubmitter.submit(strategy, StrategyStage.PROFIT_EXIT, quantity, plan.limitPrice(),
                    strategy.timeInForce(), StrategyLifecycleState.SELL_PLACED,
                    "Profit hold resting exit at $" + plan.limitPrice().toPlainString()
                            + " (" + plan.describe(peak) + ")",
                    StrategyEventType.ORDER_SUBMITTED);
            strategyRepository.save(strategy);
            return;
        }
        if (!ProfitHoldExitPricing.shouldRaise(resting.limitPrice(), plan.limitPrice())) {
            logRule(strategy, "PROFIT_HOLD", "NOT_SATISFIED",
                    "Resting limit sell already at $" + Monetary.round(resting.limitPrice()).toPlainString()
                            + "; " + plan.describe(peak) + ", latestPrice=" + latestPrice.toPlainString(), outcomes);
            strategyRepository.save(strategy);
            return;
        }
        ExitOrderCanceller.Outcome canceled = exitOrderCanceller.cancelAndConfirm(resting);
        if (canceled == ExitOrderCanceller.Outcome.FILLED) {
            logRule(strategy, "PROFIT_HOLD", "SATISFIED",
                    "The resting exit filled at $" + Monetary.round(resting.limitPrice()).toPlainString()
                            + " while it was being raised", outcomes);
            return;
        }
        if (canceled == ExitOrderCanceller.Outcome.STILL_WORKING) {
            logRule(strategy, "PROFIT_HOLD", "SKIPPED",
                    "Waiting for the broker to confirm the cancel before raising the exit to $"
                            + plan.limitPrice().toPlainString(), outcomes);
            strategyRepository.save(strategy);
            return;
        }
        logRule(strategy, "PROFIT_HOLD", "RAISED",
                "Trailing exit up from $" + Monetary.round(resting.limitPrice()).toPlainString()
                        + " to $" + plan.limitPrice().toPlainString() + " — " + plan.describe(peak), outcomes);
        sellOrderSubmitter.submit(strategy, StrategyStage.PROFIT_EXIT, quantity, plan.limitPrice(),
                strategy.timeInForce(), StrategyLifecycleState.SELL_PLACED,
                "Profit hold exit raised to $" + plan.limitPrice().toPlainString() + " (" + plan.describe(peak) + ")",
                StrategyEventType.ORDER_SUBMITTED);
        strategyRepository.save(strategy);
    }

    /** The profit-hold sell currently working at the broker, or null when none is. */
    private static StrategyOrder workingProfitExit(List<StrategyOrder> orders) {
        if (orders == null) {
            return null;
        }
        return orders.stream()
                .filter(order -> order.stage() == StrategyStage.PROFIT_EXIT)
                .filter(order -> order.status() == StrategyOrderStatus.SUBMITTED
                        || order.status() == StrategyOrderStatus.PARTIALLY_FILLED)
                .max(java.util.Comparator.comparing(StrategyOrder::submittedAt,
                        java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder())))
                .orElse(null);
    }

    private void submitLegacyProfitExit(Strategy strategy, AlpacaPositionData position, BigDecimal latestPrice,
                                        List<StrategyEngine.RuleOutcome> outcomes) {
        if (strategy.alpacaTrailingStopEnabled()) {
            submitLegacyBrokerTrailingStop(strategy, position, latestPrice, outcomes);
            return;
        }
        logRule(strategy, "TARGET_SELL", "SATISFIED",
                "latestPrice=" + latestPrice.toPlainString()
                        + " >= targetPrice=" + strategy.targetSellPrice().toPlainString()
                        + ", quantity=" + strategy.targetSellQuantity(position.quantity()).toPlainString(), outcomes);
        logRule(strategy, "PROFIT_HOLD", "SKIPPED", "Disabled", outcomes);
        sellOrderSubmitter.submit(strategy, StrategyStage.TARGET_SELL, strategy.targetSellQuantity(position.quantity()), latestPrice,
                StrategyLifecycleState.SELL_PLACED, "Target sell submitted", StrategyEventType.TARGET_TRIGGERED);
    }

    private void submitLegacyBrokerTrailingStop(Strategy strategy, AlpacaPositionData position, BigDecimal latestPrice,
                                                List<StrategyEngine.RuleOutcome> outcomes) {
        logRule(strategy, "TARGET_SELL", "SATISFIED",
                "latestPrice=" + latestPrice.toPlainString()
                        + " >= targetPrice=" + strategy.targetSellPrice().toPlainString()
                        + ", quantity=" + strategy.targetSellQuantity(position.quantity()).toPlainString(), outcomes);
        logRule(strategy, "ALPACA_TRAILING_STOP", "SUBMITTED", "Broker trailing stop requested after trigger", outcomes);
        trailingStopSubmitter.submit(strategy, strategy.targetSellQuantity(position.quantity()),
                StrategyLifecycleState.SELL_PLACED,
                "Broker trailing stop sell submitted after trigger",
                StrategyEventType.ORDER_SUBMITTED);
    }

    private BigDecimal calculateProfitActivationThreshold(Strategy strategy, AlpacaPositionData position) {
        BigDecimal baseBuyPrice = position != null && position.avgEntryPrice() != null
                && position.avgEntryPrice().compareTo(BigDecimal.ZERO) > 0
                ? position.avgEntryPrice()
                : strategy.baseBuyLimitPrice();
        ThresholdType thresholdType = strategy.automaticStopSellThresholdType();
        BigDecimal thresholdValue = strategy.automaticStopSellThreshold();
        if (thresholdType == ThresholdType.PERCENTAGE) {
            BigDecimal percent = thresholdValue.divide(new BigDecimal("100"));
            return Monetary.round(baseBuyPrice.add(baseBuyPrice.multiply(percent)));
        }
        return Monetary.round(baseBuyPrice.add(thresholdValue));
    }

    private BigDecimal trailingThreshold(Strategy strategy) {
        BigDecimal high = strategy.highestObservedPriceAfterTarget();
        if (strategy.profitHoldType() == ProfitHoldType.FIXED_AMOUNT_TRAILING) {
            return Monetary.round(high.subtract(strategy.profitHoldAmount()));
        }
        return Monetary.round(high.multiply(BigDecimal.ONE.subtract(strategy.profitHoldPercent().divide(new BigDecimal("100")))));
    }

    private boolean hasNoDuplicateSellOrder(Strategy strategy, List<StrategyOrder> orders) {
        if (hasPendingOrFilledExitOrder(orders, StrategyStage.TARGET_SELL)
                || hasPendingOrFilledExitOrder(orders, StrategyStage.PROFIT_EXIT)) {
            return false;
        }
        List<AlpacaOrderData> remoteOpenOrders = alpacaClient.getOpenOrders(strategy.symbol());
        for (AlpacaOrderData order : remoteOpenOrders) {
            if ("sell".equalsIgnoreCase(order.side())) {
                LOGGER.warning("Duplicate sell order prevention: Found existing sell order on Alpaca for "
                        + strategy.symbol() + " orderId=" + order.orderId());
                return false;
            }
        }
        return true;
    }

    private BrokerManagedExitOrderDecision reconcileBrokerManagedExitOrder(
            Strategy strategy,
            List<StrategyOrder> orders,
            BigDecimal expectedQuantity,
            String ruleName,
            List<StrategyEngine.RuleOutcome> outcomes
    ) {
        StrategyOrder pendingExitOrder = latestPendingBrokerManagedExitOrder(orders).orElse(null);
        if (pendingExitOrder == null) {
            return BrokerManagedExitOrderDecision.NONE;
        }

        if (pendingExitOrder.requestedQuantity().compareTo(expectedQuantity) != 0) {
            logRule(strategy, ruleName, "REPLACE_REQUIRED",
                    "Position size changed; replacing broker sell order from qty="
                            + pendingExitOrder.requestedQuantity().toPlainString()
                            + " to qty=" + expectedQuantity.toPlainString(), outcomes);
            ExitOrderCanceller.Outcome outcome = cancelForReplacement(strategy, orders);
            if (outcome == ExitOrderCanceller.Outcome.FILLED) {
                logRule(strategy, ruleName, "FILL_DETECTED",
                        "Sell filled before the cancel took effect; recording the fill instead of replacing it", outcomes);
                return BrokerManagedExitOrderDecision.PRESENT;
            }
            if (outcome == ExitOrderCanceller.Outcome.STILL_WORKING) {
                logRule(strategy, ruleName, "REPLACE_DEFERRED",
                        "Broker has not confirmed the cancel yet; replacing on a later poll", outcomes);
                return BrokerManagedExitOrderDecision.PRESENT;
            }
            return BrokerManagedExitOrderDecision.REPLACE_AND_SUBMIT_NOW;
        }

        List<AlpacaOrderData> remoteOpenOrders = alpacaClient.getOpenOrders(strategy.symbol());
        boolean remoteOrderPresent = remoteOpenOrders.stream().anyMatch(remoteOrder ->
                "sell".equalsIgnoreCase(remoteOrder.side())
                        && (remoteOrder.orderId().equals(pendingExitOrder.alpacaOrderId())
                        || remoteOrder.clientOrderId().equals(pendingExitOrder.clientOrderId())));
        if (!remoteOrderPresent) {
            // Before canceling, verify the order wasn't already filled at the broker. This can
            // happen when the batch snapshot used by refreshOrderStatuses still had the order as
            // "open" (snapshot was built before the fill), so the fill wasn't applied to the
            // local order yet — but the live getOpenOrders() call above already reflects the fill.
            // Treating a filled order as "missing" would cancel the local record and place a new
            // sell into an empty position, leaving the strategy stuck.
            if (pendingExitOrder.alpacaOrderId() != null && !pendingExitOrder.alpacaOrderId().isBlank()) {
                Optional<AlpacaOrderData> specificOrder = alpacaClient.getOrder(pendingExitOrder.alpacaOrderId());
                if (specificOrder.isPresent()) {
                    String brokerStatus = BrokerOrderStatusUtil.normalize(specificOrder.get().status());
                    if ("filled".equals(brokerStatus) || "partially_filled".equals(brokerStatus)) {
                        // Order is already filled — mark local order as filled and let the poll
                        // complete the strategy lifecycle; no replacement needed.
                        pendingExitOrder.setStatus(StrategyOrderStatus.FILLED);
                        orderRepository.save(pendingExitOrder);
                        logRule(strategy, ruleName, "FILL_DETECTED",
                                "Sell order confirmed filled on broker; marking local order as filled", outcomes);
                        return BrokerManagedExitOrderDecision.PRESENT;
                    }
                }
            }
            logRule(strategy, ruleName, "REPLACE_REQUIRED",
                    "Pending local sell order missing on broker; canceling stale local order and replacing", outcomes);
            cancelBrokerManagedExitOrders(strategy, orders);
            return BrokerManagedExitOrderDecision.REPLACE_AND_SUBMIT_NOW;
        }

        logRule(strategy, ruleName, "SKIPPED", "Existing pending exit order already present", outcomes);
        return BrokerManagedExitOrderDecision.PRESENT;
    }

    /**
     * Cancels the working exit orders ahead of a replacement and reports what the broker confirmed.
     * A replacement is only safe once every one of them is confirmed gone: if one filled during the
     * cancel, that fill is recorded instead; if one has not settled, the replacement waits a poll.
     */
    private ExitOrderCanceller.Outcome cancelForReplacement(Strategy strategy, List<StrategyOrder> orders) {
        boolean filled = false;
        boolean stillWorking = false;
        for (StrategyOrder order : orders) {
            if (!order.isPending() || !isBrokerManagedExitStage(order.stage())) {
                continue;
            }
            ExitOrderCanceller.Outcome outcome = exitOrderCanceller.cancelAndConfirm(order);
            filled |= outcome == ExitOrderCanceller.Outcome.FILLED;
            stillWorking |= outcome == ExitOrderCanceller.Outcome.STILL_WORKING;
        }
        if (filled) {
            return ExitOrderCanceller.Outcome.FILLED;
        }
        if (stillWorking) {
            return ExitOrderCanceller.Outcome.STILL_WORKING;
        }
        // Broker-managed sells the local records do not know about would reserve the same shares.
        for (AlpacaOrderData remote : alpacaClient.getOpenOrders(strategy.symbol())) {
            if ("sell".equalsIgnoreCase(remote.side()) && isBrokerManagedClientOrderId(remote.clientOrderId())) {
                alpacaClient.cancelOrder(remote.orderId());
            }
        }
        stateMachine.transition(strategy, strategy.currentState(), StrategyEventType.ORDER_STATUS_UPDATED,
                "Broker-managed profit sell order canceled for replacement", "{}");
        return ExitOrderCanceller.Outcome.CANCELED;
    }

    /**
     * Places a target sell that replaces or restores an earlier one, priced by {@link TargetSellPricing}
     * rather than at the market. When an average-down explains the new share count, the strategy's own
     * target moves by the same factor so the grid and the working order agree.
     */
    private void submitReplacementTargetSell(
            Strategy strategy,
            AlpacaPositionData position,
            List<StrategyOrder> orders,
            BigDecimal quantity,
            String message,
            List<StrategyEngine.RuleOutcome> outcomes
    ) {
        StrategyOrder previous = TargetSellPricing.latestUnfilledTargetSell(orders);
        TargetSellPricing.Replacement replacement = TargetSellPricing.forReplacement(strategy, previous, position, orders);
        String detail = message + " at $" + replacement.limitPrice().toPlainString()
                + " (" + replacement.timeInForce().name() + ")";
        if (replacement.repriced()) {
            BigDecimal previousTarget = strategy.targetSellPrice();
            BigDecimal newTarget = Monetary.round(previousTarget.multiply(replacement.rescaleFactor()));
            strategy.setTargetSellPrice(newTarget);
            strategyRepository.save(strategy);
            detail += "; target repriced from $" + previousTarget.toPlainString()
                    + " to $" + newTarget.toPlainString() + " for the new average cost";
        }
        logRule(strategy, "TARGET_SELL", "REPLACED", detail, outcomes);
        sellOrderSubmitter.submit(strategy, StrategyStage.TARGET_SELL, quantity, replacement.limitPrice(),
                replacement.timeInForce(), StrategyLifecycleState.SELL_PLACED, detail, StrategyEventType.ORDER_SUBMITTED);
    }

    private void cancelBrokerManagedExitOrders(Strategy strategy, List<StrategyOrder> orders) {
        List<AlpacaOrderData> remoteOpenOrders = alpacaClient.getOpenOrders(strategy.symbol());
        for (StrategyOrder order : orders) {
            if (!order.isPending() || !isBrokerManagedExitStage(order.stage())) {
                continue;
            }
            if (order.alpacaOrderId() != null && !order.alpacaOrderId().isBlank()) {
                alpacaClient.cancelOrder(order.alpacaOrderId());
            }
            order.setStatus(com.neuralarc.model.StrategyOrderStatus.CANCELED);
            orderRepository.save(order);
        }
        for (AlpacaOrderData order : remoteOpenOrders) {
            if (!"sell".equalsIgnoreCase(order.side()) || !isBrokerManagedClientOrderId(order.clientOrderId())) {
                continue;
            }
            alpacaClient.cancelOrder(order.orderId());
        }
        stateMachine.transition(strategy, strategy.currentState(), StrategyEventType.ORDER_STATUS_UPDATED,
                "Broker-managed profit sell order canceled for replacement", "{}");
    }

    private boolean shouldRecoverMissingBrokerManagedExitOrder(Strategy strategy) {
        if (strategy.currentState() != StrategyLifecycleState.SELL_PLACED) {
            return false;
        }
        // Any working sell covers the position — a stop-loss sell included. Counting only target-sell
        // client ids read a working stop-loss sell as "missing", and the replacement opened a short.
        List<AlpacaOrderData> remoteOpenOrders = alpacaClient.getOpenOrders(strategy.symbol());
        boolean openSellExists = remoteOpenOrders.stream()
                .anyMatch(order -> "sell".equalsIgnoreCase(order.side()));
        return !openSellExists;
    }

    private boolean isBrokerManagedExitStage(StrategyStage stage) {
        return stage == StrategyStage.TARGET_SELL || stage == StrategyStage.PROFIT_EXIT;
    }

    private boolean isBrokerManagedClientOrderId(String clientOrderId) {
        if (clientOrderId == null || clientOrderId.isBlank()) {
            return false;
        }
        return clientOrderId.contains("_TARGET_SELL_") || clientOrderId.contains("_PROFIT_EXIT_");
    }

    private java.util.Optional<StrategyOrder> latestPendingBrokerManagedExitOrder(List<StrategyOrder> orders) {
        return orders.stream()
                .filter(StrategyOrder::isPending)
                .filter(order -> isBrokerManagedExitStage(order.stage()))
                .max(java.util.Comparator.comparing(StrategyOrder::submittedAt,
                                java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                        .thenComparing(StrategyOrder::id));
    }

    private enum BrokerManagedExitOrderDecision {
        NONE,
        PRESENT,
        REPLACE_AND_SUBMIT_NOW
    }

    private boolean hasPendingExitOrder(List<StrategyOrder> orders, StrategyStage stage) {
        return orders.stream().anyMatch(order -> order.stage() == stage && order.isPending());
    }

    private boolean hasFilledExitOrder(List<StrategyOrder> orders, StrategyStage stage) {
        return orders.stream().anyMatch(order -> order.stage() == stage
                && order.status() == com.neuralarc.model.StrategyOrderStatus.FILLED);
    }

    private boolean hasPendingOrFilledExitOrder(List<StrategyOrder> orders, StrategyStage stage) {
        return orders.stream().anyMatch(order -> order.stage() == stage && (order.isPending()
                || order.status() == com.neuralarc.model.StrategyOrderStatus.FILLED));
    }

    private void logRule(Strategy strategy, String ruleName, String status, String details,
                         List<StrategyEngine.RuleOutcome> outcomes) {
        LOGGER.info(() -> "[POLL][" + strategy.symbol() + "][" + ruleName + "][" + status + "] " + details);
        if (outcomes != null) {
            outcomes.add(new StrategyEngine.RuleOutcome(ruleName, status, details));
        }
    }

    @FunctionalInterface
    interface SellOrderSubmitter {
        StrategyOrder submit(Strategy strategy, StrategyStage stage, BigDecimal quantity, BigDecimal limitPrice,
                             TimeInForce timeInForce, StrategyLifecycleState lifecycleState, String message,
                             StrategyEventType eventType);

        default StrategyOrder submit(Strategy strategy, StrategyStage stage, BigDecimal quantity, BigDecimal limitPrice,
                                     StrategyLifecycleState lifecycleState, String message, StrategyEventType eventType) {
            return submit(strategy, stage, quantity, limitPrice, TimeInForce.DAY, lifecycleState, message, eventType);
        }
    }

    @FunctionalInterface
    interface TrailingStopSubmitter {
        StrategyOrder submit(Strategy strategy, BigDecimal quantity, StrategyLifecycleState lifecycleState,
                             String message, StrategyEventType eventType);
    }
}
