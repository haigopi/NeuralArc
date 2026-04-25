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

public class StrategyEngine {
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
            evaluateOptionalLossExit(strategy, position.get(), latestPrice, orders);
            evaluateTargetSellAndProfitHold(strategy, position.get(), latestPrice, orders);
        } else {
            maybeRestartStrategy(strategy, orders);
        }

        maybeSubmitBuyLimit1(strategy, latestPrice, orders);
        maybeSubmitBuyLimit2(strategy, latestPrice, orders);
        strategy.setLastPolledAt(Instant.now());
        strategyRepository.save(strategy);
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
            return;
        }
        if (latestPrice.compareTo(strategy.buyLimit1Price()) > 0) {
            return;
        }
        if (!isStageFilled(orders, StrategyStage.BASE_BUY)) {
            return;
        }
        if (hasPendingOrFilledStage(orders, StrategyStage.BUY_LIMIT_1)) {
            return;
        }
        submitBuyOrder(strategy, StrategyStage.BUY_LIMIT_1, strategy.buyLimit1Quantity(), strategy.buyLimit1Price(),
                StrategyLifecycleState.BUY_LIMIT_1_PLACED, "Buy Limit 1 submitted");
    }

    private void maybeSubmitBuyLimit2(Strategy strategy, BigDecimal latestPrice, List<StrategyOrder> orders) {
        if (strategy.buyLimit2Quantity() <= 0 || strategy.buyLimit2Price().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        if (latestPrice.compareTo(strategy.buyLimit2Price()) > 0) {
            return;
        }
        if (!isStageFilled(orders, StrategyStage.BUY_LIMIT_1)) {
            return;
        }
        if (hasPendingOrFilledStage(orders, StrategyStage.BUY_LIMIT_2)) {
            return;
        }
        submitBuyOrder(strategy, StrategyStage.BUY_LIMIT_2, strategy.buyLimit2Quantity(), strategy.buyLimit2Price(),
                StrategyLifecycleState.BUY_LIMIT_2_PLACED, "Buy Limit 2 submitted");
    }

    private void evaluateManagedStopLoss(Strategy strategy, AlpacaPositionData position, BigDecimal latestPrice, List<StrategyOrder> orders) {
        if (!strategy.automatedStopLossEnabled()) {
            return;
        }
        if (hasPendingOrFilledStage(orders, StrategyStage.STOP_LOSS)) {
            return;
        }
        BigDecimal stopThreshold = strategy.stopLossType() == StopLossType.PERCENT_BELOW_AVERAGE_COST
                ? Monetary.round(position.avgEntryPrice().multiply(BigDecimal.ONE.subtract(strategy.stopLossPercent().divide(new BigDecimal("100")))))
                : strategy.stopLossPrice();
        if (stopThreshold.compareTo(BigDecimal.ZERO) <= 0 || latestPrice.compareTo(stopThreshold) > 0) {
            return;
        }
        submitSellOrder(strategy, StrategyStage.STOP_LOSS, position.quantity(), latestPrice,
                StrategyLifecycleState.SELL_PLACED, "Stop loss sell submitted", StrategyEventType.STOP_LOSS_TRIGGERED);
    }

    private void evaluateOptionalLossExit(Strategy strategy, AlpacaPositionData position, BigDecimal latestPrice, List<StrategyOrder> orders) {
        if (!strategy.optionalLossExitEnabled()) {
            return;
        }
        if (hasPendingOrFilledExitOrder(orders, StrategyStage.LOSS_EXIT)) {
            return;
        }
        if (latestPrice.compareTo(strategy.optionalLossExitPrice()) > 0) {
            return;
        }
        boolean noMoreConfiguredStages = strategy.buyLimit2Quantity() <= 0
                || isStageFilled(orders, StrategyStage.BUY_LIMIT_2)
                || (strategy.buyLimit2Quantity() > 0 && !isStageFilled(orders, StrategyStage.BUY_LIMIT_1));
        if (!noMoreConfiguredStages) {
            return;
        }
        submitSellOrder(strategy, StrategyStage.LOSS_EXIT, position.quantity(), latestPrice,
                StrategyLifecycleState.SELL_PLACED, "Optional loss exit submitted", StrategyEventType.OPTIONAL_LOSS_EXIT_TRIGGERED);
    }

    private void evaluateTargetSellAndProfitHold(Strategy strategy, AlpacaPositionData position, BigDecimal latestPrice, List<StrategyOrder> orders) {
        if (!strategy.targetSellEnabled() || strategy.targetSellPrice().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        if (hasPendingOrFilledExitOrder(orders, StrategyStage.TARGET_SELL) || hasPendingOrFilledExitOrder(orders, StrategyStage.PROFIT_EXIT)) {
            return;
        }
        boolean profitHoldActive = strategy.currentState() == StrategyLifecycleState.PROFIT_HOLD_ACTIVE
                || strategy.highestObservedPriceAfterTarget().compareTo(BigDecimal.ZERO) > 0;
        if (!profitHoldActive && latestPrice.compareTo(strategy.targetSellPrice()) < 0) {
            return;
        }

        if (!strategy.profitHoldEnabled()) {
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
            strategyRepository.save(strategy);
            return;
        }
        submitSellOrder(strategy, StrategyStage.PROFIT_EXIT, strategy.targetSellQuantity(position.quantity()), latestPrice,
                StrategyLifecycleState.SELL_PLACED, "Profit hold exit submitted", StrategyEventType.ORDER_SUBMITTED);
    }

    private void maybeRestartStrategy(Strategy strategy, List<StrategyOrder> orders) {
        if (!strategy.restartAfterExitEnabled()) {
            if (hasFilledExitOrder(orders)) {
                stateMachine.transition(strategy, StrategyLifecycleState.COMPLETED,
                        StrategyEventType.STRATEGY_COMPLETED,
                        "Strategy cycle completed",
                        "{}");
                strategyRepository.save(strategy);
            }
            return;
        }
        if (!hasFilledExitOrder(orders) || hasPendingStage(orders, StrategyStage.BASE_BUY)) {
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
            AlpacaOrderData data = latest.get();
            StrategyOrderStatus status = StrategyService.mapOrderStatus(data.status());
            order.setStatus(status);
            order.setFilledQuantity(data.filledQuantity());
            order.setFilledAveragePrice(data.filledAveragePrice());
            order.setRawResponseJson(data.rawJson());
            if (status == StrategyOrderStatus.FILLED && order.filledAt() == null) {
                order.setFilledAt(Instant.now());
            }
            orderRepository.save(order);
            strategy.setLatestOrderStatus(status.name());
            strategy.setLatestAlpacaOrderId(order.alpacaOrderId());
            transitionForOrderUpdate(strategy, order, status);
            strategyRepository.save(strategy);
        }
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
                Instant.now(),
                Instant.now(),
                null,
                submitted.rawJson()
        );
        orderRepository.save(order);
        strategy.setLatestOrderStatus(order.status().name());
        strategy.setLatestAlpacaOrderId(submitted.orderId());
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
                Instant.now(),
                Instant.now(),
                null,
                submitted.rawJson()
        );
        orderRepository.save(order);
        strategy.setLatestOrderStatus(order.status().name());
        strategy.setLatestAlpacaOrderId(submitted.orderId());
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

    private boolean hasFilledExitOrder(List<StrategyOrder> orders) {
        return orders.stream().anyMatch(order ->
                (order.stage() == StrategyStage.TARGET_SELL
                        || order.stage() == StrategyStage.PROFIT_EXIT
                        || order.stage() == StrategyStage.STOP_LOSS
                        || order.stage() == StrategyStage.LOSS_EXIT
                        || order.stage() == StrategyStage.CLOSE_POSITION)
                        && order.status() == StrategyOrderStatus.FILLED);
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
}
