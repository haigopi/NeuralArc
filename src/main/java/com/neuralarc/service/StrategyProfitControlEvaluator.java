package com.neuralarc.service;

import com.neuralarc.api.AlpacaClient;
import com.neuralarc.api.AlpacaOrderData;
import com.neuralarc.api.AlpacaPositionData;
import com.neuralarc.model.ProfitControlMode;
import com.neuralarc.model.ProfitHoldType;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyEventType;
import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyOrder;
import com.neuralarc.model.StrategyStage;
import com.neuralarc.model.ThresholdType;
import com.neuralarc.model.TrailingType;
import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.util.List;
import java.util.logging.Logger;

final class StrategyProfitControlEvaluator {
    private static final Logger LOGGER = Logger.getLogger(StrategyProfitControlEvaluator.class.getName());

    private final StrategyRepository strategyRepository;
    private final StrategyOrderRepository orderRepository;
    private final StrategyStateMachine stateMachine;
    private final AlpacaClient alpacaClient;
    private final SellOrderSubmitter sellOrderSubmitter;
    private final TrailingStopSubmitter trailingStopSubmitter;

    StrategyProfitControlEvaluator(
            StrategyRepository strategyRepository,
            StrategyOrderRepository orderRepository,
            StrategyStateMachine stateMachine,
            AlpacaClient alpacaClient,
            SellOrderSubmitter sellOrderSubmitter,
            TrailingStopSubmitter trailingStopSubmitter
    ) {
        this.strategyRepository = strategyRepository;
        this.orderRepository = orderRepository;
        this.stateMachine = stateMachine;
        this.alpacaClient = alpacaClient;
        this.sellOrderSubmitter = sellOrderSubmitter;
        this.trailingStopSubmitter = trailingStopSubmitter;
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
            sellOrderSubmitter.submit(strategy, StrategyStage.TARGET_SELL, expectedQuantity, latestPrice,
                    StrategyLifecycleState.SELL_PLACED, "Sell trigger order replaced after position update",
                    StrategyEventType.ORDER_SUBMITTED);
            return;
        }
        if (hasPendingOrFilledExitOrder(orders, StrategyStage.TARGET_SELL)
                || hasPendingOrFilledExitOrder(orders, StrategyStage.PROFIT_EXIT)) {
            logRule(strategy, "SELL_TRIGGER", "SKIPPED", "Existing pending or filled exit order already present", outcomes);
            return;
        }
        if (shouldRecoverMissingBrokerManagedExitOrder(strategy)) {
            logRule(strategy, "SELL_TRIGGER", "RECOVERED", "Missing broker sell order detected; placing replacement", outcomes);
            sellOrderSubmitter.submit(strategy, StrategyStage.TARGET_SELL, expectedQuantity, latestPrice,
                    StrategyLifecycleState.SELL_PLACED, "Sell trigger order restored after missing broker order",
                    StrategyEventType.ORDER_SUBMITTED);
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
                    sellOrderSubmitter.submit(strategy, StrategyStage.TARGET_SELL, expectedQuantity, latestPrice,
                            StrategyLifecycleState.SELL_PLACED,
                            "Target sell order replaced after position update",
                            StrategyEventType.ORDER_SUBMITTED);
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
                    sellOrderSubmitter.submit(strategy, StrategyStage.TARGET_SELL, expectedQuantity, latestPrice,
                            StrategyLifecycleState.SELL_PLACED,
                            "Target sell restored after missing broker order",
                            StrategyEventType.ORDER_SUBMITTED);
                }
                return;
            }
        }
        if (hasPendingOrFilledExitOrder(orders, StrategyStage.TARGET_SELL)
                || hasPendingOrFilledExitOrder(orders, StrategyStage.PROFIT_EXIT)) {
            logRule(strategy, "TARGET_SELL", "SKIPPED", "Existing pending or filled exit order already present", outcomes);
            logRule(strategy, "PROFIT_HOLD", "SKIPPED", "Existing pending or filled exit order already present", outcomes);
            return;
        }

        boolean profitHoldActive = strategy.currentState() == StrategyLifecycleState.PROFIT_HOLD_ACTIVE
                || strategy.highestObservedPriceAfterTarget().compareTo(BigDecimal.ZERO) > 0;
        if (!profitHoldActive && latestPrice.compareTo(activationThreshold) < 0) {
            logRule(strategy, "TARGET_SELL", "NOT_SATISFIED",
                    "latestPrice=" + latestPrice.toPlainString()
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
                            + " >= " + activationLabel + "=" + activationThreshold.toPlainString(), outcomes);
            logRule(strategy, "PROFIT_HOLD", "NOT_SATISFIED",
                    "latestPrice=" + latestPrice.toPlainString()
                            + " > trailingThreshold=" + threshold.toPlainString()
                            + ", highest=" + strategy.highestObservedPriceAfterTarget().toPlainString(), outcomes);
            strategyRepository.save(strategy);
            return;
        }

        logRule(strategy, "TARGET_SELL", "SATISFIED",
                "latestPrice=" + latestPrice.toPlainString()
                        + " >= " + activationLabel + "=" + activationThreshold.toPlainString(), outcomes);
        logRule(strategy, "PROFIT_HOLD", "SATISFIED",
                "latestPrice=" + latestPrice.toPlainString()
                        + " <= trailingThreshold=" + threshold.toPlainString()
                        + ", highest=" + strategy.highestObservedPriceAfterTarget().toPlainString(), outcomes);
        sellOrderSubmitter.submit(strategy, StrategyStage.PROFIT_EXIT, strategy.targetSellQuantity(position.quantity()), latestPrice,
                StrategyLifecycleState.SELL_PLACED, "Profit hold exit submitted", StrategyEventType.ORDER_SUBMITTED);
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
            cancelBrokerManagedExitOrders(strategy, orders);
            return BrokerManagedExitOrderDecision.REPLACE_AND_SUBMIT_NOW;
        }

        List<AlpacaOrderData> remoteOpenOrders = alpacaClient.getOpenOrders(strategy.symbol());
        boolean remoteOrderPresent = remoteOpenOrders.stream().anyMatch(remoteOrder ->
                "sell".equalsIgnoreCase(remoteOrder.side())
                        && (remoteOrder.orderId().equals(pendingExitOrder.alpacaOrderId())
                        || remoteOrder.clientOrderId().equals(pendingExitOrder.clientOrderId())));
        if (!remoteOrderPresent) {
            logRule(strategy, ruleName, "REPLACE_REQUIRED",
                    "Pending local sell order missing on broker; canceling stale local order and replacing", outcomes);
            cancelBrokerManagedExitOrders(strategy, orders);
            return BrokerManagedExitOrderDecision.REPLACE_AND_SUBMIT_NOW;
        }

        logRule(strategy, ruleName, "SKIPPED", "Existing pending exit order already present", outcomes);
        return BrokerManagedExitOrderDecision.PRESENT;
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
        return alpacaClient.getOpenOrders(strategy.symbol()).stream()
                .noneMatch(order -> "sell".equalsIgnoreCase(order.side())
                        && isBrokerManagedClientOrderId(order.clientOrderId()));
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
                             StrategyLifecycleState lifecycleState, String message, StrategyEventType eventType);
    }

    @FunctionalInterface
    interface TrailingStopSubmitter {
        StrategyOrder submit(Strategy strategy, BigDecimal quantity, StrategyLifecycleState lifecycleState,
                             String message, StrategyEventType eventType);
    }
}
