package com.neuralarc.service;

import com.neuralarc.api.AlpacaClient;
import com.neuralarc.api.AlpacaOrderData;
import com.neuralarc.api.AlpacaPositionData;
import com.neuralarc.model.*;
import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class StrategyPollingService {
    private static final Logger LOGGER = Logger.getLogger(StrategyPollingService.class.getName());

    private final StrategyRepository strategyRepository;
    private final StrategyOrderRepository orderRepository;
    private final StrategyExecutionEventRepository eventRepository;
    private final AlpacaClient alpacaClient;

    public StrategyPollingService(
            StrategyRepository strategyRepository,
            StrategyOrderRepository orderRepository,
            StrategyExecutionEventRepository eventRepository,
            AlpacaClient alpacaClient
    ) {
        this.strategyRepository = strategyRepository;
        this.orderRepository = orderRepository;
        this.eventRepository = eventRepository;
        this.alpacaClient = alpacaClient;
    }

    public void pollActiveStrategies() {
        for (Strategy strategy : strategyRepository.findActive()) {
            pollStrategy(strategy.id());
        }
    }

    public void pollStrategy(String strategyId) {
        Optional<Strategy> maybeStrategy = strategyRepository.findById(strategyId);
        if (maybeStrategy.isEmpty()) {
            return;
        }
        Strategy strategy = maybeStrategy.get();
        if (strategy.status() != StrategyStatus.ACTIVE) {
            return;
        }

        try {
            refreshOrderStatuses(strategy);
            BigDecimal latestPrice = alpacaClient.getLatestPrice(strategy.symbol());
            Optional<AlpacaPositionData> position = alpacaClient.getPosition(strategy.symbol());

            maybeSubmitLossLevel1(strategy, latestPrice);
            maybeSubmitLossLevel2(strategy, latestPrice);
            maybeSubmitSell(strategy, latestPrice, position);

            strategy.setLastPolledAt(Instant.now());
            strategyRepository.save(strategy);
            eventRepository.save(event(strategy.id(), StrategyEventType.POLL_SUCCESS,
                    "Poll completed", "{\"latestPrice\":\"" + latestPrice.toPlainString() + "\"}"));
        } catch (Exception ex) {
            strategy.setLastPolledAt(Instant.now());
            strategy.setLastError(ex.getMessage());
            strategyRepository.save(strategy);
            eventRepository.save(event(strategy.id(), StrategyEventType.POLL_ERROR,
                    ex.getMessage(), "{}"));
            LOGGER.log(Level.WARNING, "Polling failed for strategy " + strategy.id(), ex);
        }
    }

    private void refreshOrderStatuses(Strategy strategy) {
        List<StrategyOrder> orders = orderRepository.findByStrategyId(strategy.id());
        for (StrategyOrder order : orders) {
            if (order.isTerminal() || order.alpacaOrderId() == null || order.alpacaOrderId().isBlank()) {
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
            order.setRawResponseJson(data.rawJson());
            if (status == StrategyOrderStatus.FILLED && order.filledAt() == null) {
                order.setFilledAt(Instant.now());
            }
            orderRepository.save(order);
            eventRepository.save(event(strategy.id(), StrategyEventType.ORDER_STATUS_UPDATED,
                    "Order status updated for stage " + order.stage(), data.rawJson()));
        }
    }

    private void maybeSubmitLossLevel1(Strategy strategy, BigDecimal latestPrice) {
        if (latestPrice.compareTo(strategy.lossBuyLevel1Price()) > 0) {
            return;
        }
        Optional<StrategyOrder> initial = orderRepository.findLatestByStrategyStage(strategy.id(), StrategyStage.INITIAL_BUY);
        if (initial.isEmpty() || initial.get().status() != StrategyOrderStatus.FILLED) {
            return;
        }
        submitBuyIfAllowed(strategy, StrategyStage.LOSS_BUY_LEVEL_1, strategy.lossBuyLevel1Quantity(), strategy.lossBuyLevel1Price());
    }

    private void maybeSubmitLossLevel2(Strategy strategy, BigDecimal latestPrice) {
        if (latestPrice.compareTo(strategy.lossBuyLevel2Price()) > 0) {
            return;
        }
        Optional<StrategyOrder> level1 = orderRepository.findLatestByStrategyStage(strategy.id(), StrategyStage.LOSS_BUY_LEVEL_1);
        if (level1.isEmpty() || level1.get().status() != StrategyOrderStatus.FILLED) {
            return;
        }
        submitBuyIfAllowed(strategy, StrategyStage.LOSS_BUY_LEVEL_2, strategy.lossBuyLevel2Quantity(), strategy.lossBuyLevel2Price());
    }

    private void maybeSubmitSell(Strategy strategy, BigDecimal latestPrice, Optional<AlpacaPositionData> maybePosition) {
        if (maybePosition.isEmpty() || !maybePosition.get().exists()) {
            return;
        }
        if (hasPendingOrder(strategy.id(), StrategyStage.SELL) || hasFilledOrder(strategy.id(), StrategyStage.SELL)) {
            return;
        }

        BigDecimal target = strategy.targetSellPrice();
        boolean shouldSell;
        if (!strategy.profitHoldEnabled()) {
            shouldSell = latestPrice.compareTo(target) >= 0;
        } else {
            if (!strategy.profitHoldArmed()) {
                if (latestPrice.compareTo(target) < 0) {
                    return;
                }
                strategy.armProfitHold(latestPrice);
                strategyRepository.save(strategy);
                eventRepository.save(event(strategy.id(), StrategyEventType.PROFIT_HOLD_ARMED,
                        "Profit hold armed", "{\"price\":\"" + latestPrice.toPlainString() + "\"}"));
            }
            strategy.updateHighestPriceAfterTarget(latestPrice);
            BigDecimal threshold = profitHoldThreshold(strategy.highestPriceAfterTarget(), strategy.profitHoldPercentOrAmount());
            shouldSell = latestPrice.compareTo(threshold) <= 0;
            if (!shouldSell) {
                eventRepository.save(event(strategy.id(), StrategyEventType.PROFIT_HOLD_UPDATED,
                        "Profit hold waiting", "{\"highest\":\"" + strategy.highestPriceAfterTarget().toPlainString() + "\",\"threshold\":\"" + threshold.toPlainString() + "\"}"));
            }
        }

        if (!shouldSell) {
            return;
        }

        int qty = maybePosition.get().quantity().intValue();
        if (qty <= 0) {
            return;
        }
        String clientOrderId = StrategyService.buildClientOrderId(strategy.id(), StrategyStage.SELL);
        AlpacaOrderData result = alpacaClient.submitLimitSellOrder(strategy.symbol(), qty, latestPrice, clientOrderId);
        StrategyOrder order = new StrategyOrder(
                UUID.randomUUID().toString(),
                strategy.id(),
                StrategyStage.SELL,
                result.orderId(),
                clientOrderId,
                strategy.symbol(),
                StrategyOrderSide.SELL,
                StrategyOrderType.LIMIT,
                latestPrice,
                qty,
                result.filledQuantity(),
                StrategyService.mapOrderStatus(result.status()),
                Instant.now(),
                null,
                result.rawJson()
        );
        orderRepository.save(order);
        eventRepository.save(event(strategy.id(), StrategyEventType.ORDER_SUBMITTED,
                "Sell order submitted", result.rawJson()));
    }

    private void submitBuyIfAllowed(Strategy strategy, StrategyStage stage, int quantity, BigDecimal price) {
        if (strategy.status() == StrategyStatus.PAUSED
                || strategy.status() == StrategyStatus.COMPLETED
                || strategy.status() == StrategyStatus.FAILED) {
            return;
        }
        if (hasPendingOrder(strategy.id(), stage) || hasFilledOrder(strategy.id(), stage)) {
            return;
        }

        RiskProjection projection = projectedRisk(strategy, quantity, price);
        if (!projection.allowed) {
            eventRepository.save(event(strategy.id(), StrategyEventType.POLL_ERROR,
                    projection.reason, "{}"));
            strategy.setLastError(projection.reason);
            strategyRepository.save(strategy);
            return;
        }

        String clientOrderId = StrategyService.buildClientOrderId(strategy.id(), stage);
        AlpacaOrderData submitted = alpacaClient.submitLimitBuyOrder(strategy.symbol(), quantity, price, clientOrderId);
        StrategyOrder order = new StrategyOrder(
                UUID.randomUUID().toString(),
                strategy.id(),
                stage,
                submitted.orderId(),
                clientOrderId,
                strategy.symbol(),
                StrategyOrderSide.BUY,
                StrategyOrderType.LIMIT,
                price,
                quantity,
                submitted.filledQuantity(),
                StrategyService.mapOrderStatus(submitted.status()),
                Instant.now(),
                null,
                submitted.rawJson()
        );
        orderRepository.save(order);
        eventRepository.save(event(strategy.id(), StrategyEventType.ORDER_SUBMITTED,
                "Buy order submitted for stage " + stage, submitted.rawJson()));
    }

    private boolean hasPendingOrder(String strategyId, StrategyStage stage) {
        return orderRepository.findLatestByStrategyStage(strategyId, stage)
                .map(StrategyOrder::isPending)
                .orElse(false);
    }

    private boolean hasFilledOrder(String strategyId, StrategyStage stage) {
        return orderRepository.findLatestByStrategyStage(strategyId, stage)
                .map(o -> o.status() == StrategyOrderStatus.FILLED)
                .orElse(false);
    }

    private RiskProjection projectedRisk(Strategy strategy, int newOrderQty, BigDecimal newOrderPrice) {
        List<StrategyOrder> orders = orderRepository.findByStrategyId(strategy.id());
        int projectedQty = newOrderQty;
        BigDecimal projectedCapital = Monetary.round(newOrderPrice.multiply(BigDecimal.valueOf(newOrderQty)));

        for (StrategyOrder order : orders) {
            if (order.side() != StrategyOrderSide.BUY) {
                continue;
            }
            if (order.status() == StrategyOrderStatus.CANCELED
                    || order.status() == StrategyOrderStatus.REJECTED
                    || order.status() == StrategyOrderStatus.FAILED) {
                continue;
            }
            projectedQty += order.quantity();
            projectedCapital = Monetary.round(projectedCapital.add(
                    order.limitPrice().multiply(BigDecimal.valueOf(order.quantity()))));
        }

        if (projectedQty > strategy.maxTotalQuantity()) {
            return RiskProjection.deny("Projected quantity exceeds maxTotalQuantity");
        }
        if (projectedCapital.compareTo(strategy.maxCapitalAllowed()) > 0) {
            return RiskProjection.deny("Projected capital exceeds maxCapitalAllowed");
        }
        return RiskProjection.permit();
    }

    private BigDecimal profitHoldThreshold(BigDecimal highest, BigDecimal percentOrAmount) {
        if (percentOrAmount.compareTo(BigDecimal.ONE) <= 0) {
            return Monetary.round(highest.multiply(BigDecimal.ONE.subtract(percentOrAmount)));
        }
        BigDecimal percent = percentOrAmount.divide(new BigDecimal("100"));
        return Monetary.round(highest.multiply(BigDecimal.ONE.subtract(percent)));
    }

    private StrategyExecutionEvent event(String strategyId, StrategyEventType type, String message, String metadataJson) {
        return new StrategyExecutionEvent(UUID.randomUUID().toString(), strategyId, type, message, metadataJson, Instant.now());
    }

    private record RiskProjection(boolean allowed, String reason) {
        private static RiskProjection permit() { return new RiskProjection(true, ""); }
        private static RiskProjection deny(String reason) { return new RiskProjection(false, reason); }
    }
}

