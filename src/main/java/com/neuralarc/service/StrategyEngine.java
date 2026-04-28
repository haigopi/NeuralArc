package com.neuralarc.service;

import com.neuralarc.api.AlpacaClient;
import com.neuralarc.api.AlpacaOrderData;
import com.neuralarc.api.AlpacaPositionData;
import com.neuralarc.model.*;
import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public class StrategyEngine {
    private static final Logger LOGGER = Logger.getLogger(StrategyEngine.class.getName());

    private final StrategyRepository strategyRepository;
    private final StrategyOrderRepository orderRepository;
    private final StrategyStateMachine stateMachine;
    private final AlpacaClient alpacaClient;

    public StrategyEngine(
            StrategyRepository strategyRepository,
            StrategyOrderRepository orderRepository,
            StrategyStateMachine stateMachine,
            AlpacaClient alpacaClient
    ) {
        this.strategyRepository = strategyRepository;
        this.orderRepository = orderRepository;
        this.stateMachine = stateMachine;
        this.alpacaClient = alpacaClient;
    }

    public void reconcile(Strategy strategy) {
        refreshOrderStatuses(strategy);
        BigDecimal latestPrice = alpacaClient.getLatestPrice(strategy.symbol());
        Optional<AlpacaPositionData> position = alpacaClient.getPosition(strategy.symbol());
        List<StrategyOrder> orders = orderRepository.findByStrategyId(strategy.id());
        List<AlpacaOrderData> remoteOpenOrders = alpacaClient.getOpenOrders(strategy.symbol());
        logPoll(strategy, "POLL", "STARTED",
                "state=" + strategy.currentState().name()
                        + ", latestPrice=" + latestPrice.toPlainString()
                        + ", hasPosition=" + (position.isPresent() && position.get().exists())
                        + ", openOrders=" + remoteOpenOrders.size());

        if (ensureRemoteOrderPresence(strategy, orders, remoteOpenOrders, position)) {
            orders = orderRepository.findByStrategyId(strategy.id());
        }

        if (position.isPresent() && position.get().exists()) {
            if (strategy.automatedStopLossEnabled()) {
                stateMachine.transition(strategy, StrategyLifecycleState.STOP_LOSS_ACTIVE,
                        StrategyEventType.STOP_LOSS_ACTIVATED,
                        "Stop loss monitoring active",
                        "{\"symbol\":\"" + strategy.symbol() + "\"}");
                strategyRepository.save(strategy);
            }
            evaluateManagedStopLoss(strategy, position.get(), latestPrice, orders);
            evaluateTargetSellAndProfitHold(strategy, position.get(), latestPrice, orders);
        } else {
            logRule(strategy, "STOP_LOSS", "SKIPPED", "No open position");
            logRule(strategy, "TARGET_SELL", "SKIPPED", "No open position");
            logRule(strategy, "PROFIT_HOLD", "SKIPPED", "No open position");
            maybeRestartStrategy(strategy, orders);
        }

        maybeSubmitBuyLimit1(strategy, latestPrice, orders);
        maybeSubmitBuyLimit2(strategy, latestPrice, orders);
        strategy.setLastPolledAt(Instant.now());
        strategyRepository.save(strategy);
        logPoll(strategy, "POLL", "COMPLETED", "lastPolledAt=" + strategy.lastPolledAt());
    }

    public StrategyOrder submitBaseBuy(Strategy strategy) {
        return submitBuyOrder(strategy, StrategyStage.BASE_BUY, strategy.baseBuyQuantity(), strategy.baseBuyLimitPrice(),
                StrategyLifecycleState.BASE_BUY_PLACED, "Base buy order submitted");
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
                submitBaseBuy(strategy);
                return;
            }
        }

        reconcile(strategy);
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
                && strategy.buyLimit1Quantity() > 0
                && !isStageFilled(orders, StrategyStage.BUY_LIMIT_1)) {
            submitBuyOrder(strategy, StrategyStage.BUY_LIMIT_1, strategy.buyLimit1Quantity(), strategy.buyLimit1Price(),
                    StrategyLifecycleState.BUY_LIMIT_1_PLACED, "Buy Limit 1 recreated after missing Alpaca order");
            return true;
        }
        if (isStageFilled(orders, StrategyStage.BUY_LIMIT_1)
                && strategy.buyLimit2Quantity() > 0
                && !isStageFilled(orders, StrategyStage.BUY_LIMIT_2)) {
            submitBuyOrder(strategy, StrategyStage.BUY_LIMIT_2, strategy.buyLimit2Quantity(), strategy.buyLimit2Price(),
                    StrategyLifecycleState.BUY_LIMIT_2_PLACED, "Buy Limit 2 recreated after missing Alpaca order");
            return true;
        }
        return updatedLocalOrderState;
    }

    private void maybeSubmitBuyLimit1(Strategy strategy, BigDecimal latestPrice, List<StrategyOrder> orders) {
        if (strategy.buyLimit1Quantity() <= 0 || strategy.buyLimit1Price().compareTo(BigDecimal.ZERO) <= 0) {
            logRule(strategy, "BUY_LIMIT_1", "SKIPPED", "Not configured");
            return;
        }
        if (latestPrice.compareTo(strategy.buyLimit1Price()) > 0) {
            logRule(strategy, "BUY_LIMIT_1", "NOT_SATISFIED",
                    "latestPrice=" + latestPrice.toPlainString()
                            + " > triggerPrice=" + strategy.buyLimit1Price().toPlainString());
            return;
        }
        if (!isStageFilled(orders, StrategyStage.BASE_BUY)) {
            logRule(strategy, "BUY_LIMIT_1", "SKIPPED", "Base buy not fully filled");
            return;
        }
        if (hasPendingOrFilledStage(orders, StrategyStage.BUY_LIMIT_1)) {
            logRule(strategy, "BUY_LIMIT_1", "SKIPPED", "Existing pending or filled order already present");
            return;
        }
        logRule(strategy, "BUY_LIMIT_1", "SATISFIED",
                "latestPrice=" + latestPrice.toPlainString()
                        + " <= triggerPrice=" + strategy.buyLimit1Price().toPlainString()
                        + ", quantity=" + strategy.buyLimit1Quantity());
        submitBuyOrder(strategy, StrategyStage.BUY_LIMIT_1, strategy.buyLimit1Quantity(), strategy.buyLimit1Price(),
                StrategyLifecycleState.BUY_LIMIT_1_PLACED, "Buy Limit 1 submitted");
    }

    private void maybeSubmitBuyLimit2(Strategy strategy, BigDecimal latestPrice, List<StrategyOrder> orders) {
        if (strategy.buyLimit2Quantity() <= 0 || strategy.buyLimit2Price().compareTo(BigDecimal.ZERO) <= 0) {
            logRule(strategy, "BUY_LIMIT_2", "SKIPPED", "Not configured");
            return;
        }
        if (latestPrice.compareTo(strategy.buyLimit2Price()) > 0) {
            logRule(strategy, "BUY_LIMIT_2", "NOT_SATISFIED",
                    "latestPrice=" + latestPrice.toPlainString()
                            + " > triggerPrice=" + strategy.buyLimit2Price().toPlainString());
            return;
        }
        if (!isStageFilled(orders, StrategyStage.BUY_LIMIT_1)) {
            logRule(strategy, "BUY_LIMIT_2", "SKIPPED", "Buy Limit 1 not fully filled");
            return;
        }
        if (hasPendingOrFilledStage(orders, StrategyStage.BUY_LIMIT_2)) {
            logRule(strategy, "BUY_LIMIT_2", "SKIPPED", "Existing pending or filled order already present");
            return;
        }
        logRule(strategy, "BUY_LIMIT_2", "SATISFIED",
                "latestPrice=" + latestPrice.toPlainString()
                        + " <= triggerPrice=" + strategy.buyLimit2Price().toPlainString()
                        + ", quantity=" + strategy.buyLimit2Quantity());
        submitBuyOrder(strategy, StrategyStage.BUY_LIMIT_2, strategy.buyLimit2Quantity(), strategy.buyLimit2Price(),
                StrategyLifecycleState.BUY_LIMIT_2_PLACED, "Buy Limit 2 submitted");
    }

    private void evaluateManagedStopLoss(Strategy strategy, AlpacaPositionData position, BigDecimal latestPrice, List<StrategyOrder> orders) {
        if (!strategy.automatedStopLossEnabled()) {
            logRule(strategy, "STOP_LOSS", "SKIPPED", "Disabled");
            return;
        }
        if (hasPendingOrFilledStage(orders, StrategyStage.STOP_LOSS)) {
            logRule(strategy, "STOP_LOSS", "SKIPPED", "Existing pending or filled stop loss order already present");
            return;
        }
        BigDecimal stopThreshold = strategy.stopLossType() == StopLossType.PERCENT_BELOW_AVERAGE_COST
                ? Monetary.round(position.avgEntryPrice().multiply(BigDecimal.ONE.subtract(strategy.stopLossPercent().divide(new BigDecimal("100")))))
                : strategy.stopLossPrice();
        if (stopThreshold.compareTo(BigDecimal.ZERO) <= 0) {
            logRule(strategy, "STOP_LOSS", "SKIPPED", "Computed threshold is not positive");
            return;
        }
        if (latestPrice.compareTo(stopThreshold) > 0) {
            logRule(strategy, "STOP_LOSS", "NOT_SATISFIED",
                    "latestPrice=" + latestPrice.toPlainString()
                            + " > threshold=" + stopThreshold.toPlainString());
            return;
        }
        logRule(strategy, "STOP_LOSS", "SATISFIED",
                "latestPrice=" + latestPrice.toPlainString()
                        + " <= threshold=" + stopThreshold.toPlainString()
                        + ", quantity=" + position.quantity().toPlainString());
        submitSellOrder(strategy, StrategyStage.STOP_LOSS, position.quantity(), latestPrice,
                StrategyLifecycleState.SELL_PLACED, "Stop loss sell submitted", StrategyEventType.STOP_LOSS_TRIGGERED);
    }


    private void evaluateTargetSellAndProfitHold(Strategy strategy, AlpacaPositionData position, BigDecimal latestPrice, List<StrategyOrder> orders) {
        if (!strategy.targetSellEnabled() || strategy.targetSellPrice().compareTo(BigDecimal.ZERO) <= 0) {
            logRule(strategy, "TARGET_SELL", "SKIPPED", "Disabled or invalid target sell price");
            logRule(strategy, "PROFIT_HOLD", "SKIPPED", "Target sell is not active");
            return;
        }
        if (hasPendingOrFilledExitOrder(orders, StrategyStage.TARGET_SELL) || hasPendingOrFilledExitOrder(orders, StrategyStage.PROFIT_EXIT)) {
            logRule(strategy, "TARGET_SELL", "SKIPPED", "Existing pending or filled exit order already present");
            logRule(strategy, "PROFIT_HOLD", "SKIPPED", "Existing pending or filled exit order already present");
            return;
        }
        boolean profitHoldActive = strategy.currentState() == StrategyLifecycleState.PROFIT_HOLD_ACTIVE
                || strategy.highestObservedPriceAfterTarget().compareTo(BigDecimal.ZERO) > 0;
        if (!profitHoldActive && latestPrice.compareTo(strategy.targetSellPrice()) < 0) {
            logRule(strategy, "TARGET_SELL", "NOT_SATISFIED",
                    "latestPrice=" + latestPrice.toPlainString()
                            + " < targetPrice=" + strategy.targetSellPrice().toPlainString());
            logRule(strategy, "PROFIT_HOLD", "SKIPPED", "Target sell has not triggered yet");
            return;
        }

        if (!strategy.profitHoldEnabled()) {
            logRule(strategy, "TARGET_SELL", "SATISFIED",
                    "latestPrice=" + latestPrice.toPlainString()
                            + " >= targetPrice=" + strategy.targetSellPrice().toPlainString()
                            + ", quantity=" + strategy.targetSellQuantity(position.quantity()).toPlainString());
            logRule(strategy, "PROFIT_HOLD", "SKIPPED", "Disabled");
            submitSellOrder(strategy, StrategyStage.TARGET_SELL, strategy.targetSellQuantity(position.quantity()), latestPrice,
                    StrategyLifecycleState.SELL_PLACED, "Target sell submitted", StrategyEventType.TARGET_TRIGGERED);
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
                            + " >= targetPrice=" + strategy.targetSellPrice().toPlainString());
            logRule(strategy, "PROFIT_HOLD", "NOT_SATISFIED",
                    "latestPrice=" + latestPrice.toPlainString()
                            + " > trailingThreshold=" + threshold.toPlainString()
                            + ", highest=" + strategy.highestObservedPriceAfterTarget().toPlainString());
            strategyRepository.save(strategy);
            return;
        }
        logRule(strategy, "TARGET_SELL", "SATISFIED",
                "latestPrice=" + latestPrice.toPlainString()
                        + " >= targetPrice=" + strategy.targetSellPrice().toPlainString());
        logRule(strategy, "PROFIT_HOLD", "SATISFIED",
                "latestPrice=" + latestPrice.toPlainString()
                        + " <= trailingThreshold=" + threshold.toPlainString()
                        + ", highest=" + strategy.highestObservedPriceAfterTarget().toPlainString());
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
        // Manual/defensive exits (e.g., CLOSE_POSITION, STOP_LOSS) always complete the cycle.
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
        strategy.setLatestOrderStatus(status.name());
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
            case TARGET_SELL, PROFIT_EXIT, STOP_LOSS, LOSS_EXIT, CLOSE_POSITION -> status == StrategyOrderStatus.PARTIALLY_FILLED
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
            String message
    ) {
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
        orderRepository.save(order);
        strategy.setLatestOrderStatus(order.status().name());
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
        orderRepository.save(order);
        strategy.setLatestOrderStatus(order.status().name());
        strategy.setLatestAlpacaOrderId(submitted.orderId());
        strategy.setLastTriggeredRuleType(mapStageToRuleName(stage));
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
                || stage == StrategyStage.CLOSE_POSITION;
    }

    private boolean isProfitableExitStage(StrategyStage stage) {
        return stage == StrategyStage.TARGET_SELL || stage == StrategyStage.PROFIT_EXIT;
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

    private void logPoll(Strategy strategy, String scope, String status, String details) {
        LOGGER.info(() -> "[POLL][" + strategy.symbol() + "][" + scope + "][" + status + "] " + details);
    }

    private void logRule(Strategy strategy, String ruleName, String status, String details) {
        LOGGER.info(() -> "[POLL][" + strategy.symbol() + "][" + ruleName + "][" + status + "] " + details);
    }

    private String mapStageToRuleName(StrategyStage stage) {
        return switch (stage) {
            case BASE_BUY -> "BUY_RULE";
            case BUY_LIMIT_1 -> "LOSS_BUY_RULE";
            case BUY_LIMIT_2 -> "LOSS_INVESTMENT_BUY_RULE";
            case TARGET_SELL -> "SELL_RULE";
            case STOP_LOSS -> "STOP_LOSS_RULE";
            case LOSS_EXIT, PROFIT_EXIT, CLOSE_POSITION -> stage.name();
        };
    }
}
