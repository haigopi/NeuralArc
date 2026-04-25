package com.neuralarc.service;

import com.neuralarc.api.AlpacaClient;
import com.neuralarc.model.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class StrategyService {
    private final StrategyRepository strategyRepository;
    private final StrategyOrderRepository orderRepository;
    private final StrategyExecutionEventRepository eventRepository;
    private final AlpacaClient alpacaClient;
    private final StrategyValidator validator;
    private final boolean liveTradingEnabled;
    private final StrategyStateMachine stateMachine;
    private final StrategyEngine strategyEngine;

    public StrategyService(
            StrategyRepository strategyRepository,
            StrategyOrderRepository orderRepository,
            StrategyExecutionEventRepository eventRepository,
            AlpacaClient alpacaClient,
            StrategyValidator validator,
            boolean liveTradingEnabled
    ) {
        this.strategyRepository = strategyRepository;
        this.orderRepository = orderRepository;
        this.eventRepository = eventRepository;
        this.alpacaClient = alpacaClient;
        this.validator = validator;
        this.liveTradingEnabled = liveTradingEnabled;
        StrategyEventBus eventBus = new StrategyEventBus();
        this.stateMachine = new StrategyStateMachine(eventRepository, eventBus);
        this.strategyEngine = new StrategyEngine(strategyRepository, orderRepository, stateMachine, alpacaClient);
    }

    public StrategyCreationResult createAndActivate(Strategy strategy) {
        List<String> errors = validator.validate(strategy);
        if (strategy.mode() == StrategyMode.LIVE && !liveTradingEnabled) {
            errors.add("LIVE mode is disabled. Set trading.live.enabled=true to allow live trading.");
        }
        if (!errors.isEmpty()) {
            strategy.setStatus(StrategyStatus.FAILED);
            strategy.setCurrentState(StrategyLifecycleState.FAILED);
            strategy.setLastError(String.join("; ", errors));
            strategyRepository.save(strategy);
            stateMachine.transition(strategy, StrategyLifecycleState.FAILED, StrategyEventType.VALIDATION_FAILED, strategy.lastError(), "{}");
            return StrategyCreationResult.failed(strategy.lastError());
        }

        strategy.setStatus(StrategyStatus.ACTIVE);
        strategy.setCurrentState(StrategyLifecycleState.VALIDATED);
        strategy.clearLastError();
        strategyRepository.save(strategy);
        stateMachine.transition(strategy, StrategyLifecycleState.VALIDATED, StrategyEventType.STRATEGY_CREATED, "Strategy validated", "{}");

        StrategyOrder order = strategyEngine.submitBaseBuy(strategy);
        if (order == null || order.alpacaOrderId() == null || order.alpacaOrderId().isBlank()) {
            strategy.setStatus(StrategyStatus.FAILED);
            strategy.setCurrentState(StrategyLifecycleState.FAILED);
            String error = strategy.lastError() == null || strategy.lastError().isBlank()
                    ? "Failed to submit base buy order"
                    : strategy.lastError();
            strategy.setLastError(error);
            strategyRepository.save(strategy);
            stateMachine.transition(strategy, StrategyLifecycleState.FAILED, StrategyEventType.STRATEGY_FAILED, error, "{}");
            return StrategyCreationResult.failed(error);
        }
        return StrategyCreationResult.success(strategy.id(), order.id(), order.alpacaOrderId(), order.clientOrderId());
    }

    public Optional<Strategy> updateStrategy(Strategy strategy) {
        List<String> errors = validator.validate(strategy);
        if (!errors.isEmpty()) {
            strategy.setLastError(String.join("; ", errors));
            strategyRepository.save(strategy);
            return Optional.empty();
        }
        strategy.clearLastError();
        strategyRepository.save(strategy);
        stateMachine.transition(strategy, strategy.currentState(), StrategyEventType.STRATEGY_UPDATED, "Strategy updated", "{}");
        return Optional.of(strategy);
    }

    public void pause(String strategyId) {
        strategyRepository.findById(strategyId).ifPresent(strategy -> {
            strategy.setStatus(StrategyStatus.PAUSED);
            strategy.setCurrentState(StrategyLifecycleState.PAUSED);
            strategyRepository.save(strategy);
            stateMachine.transition(strategy, StrategyLifecycleState.PAUSED, StrategyEventType.STRATEGY_PAUSED, "Strategy paused", "{}");
        });
    }

    public void resume(String strategyId) {
        strategyRepository.findById(strategyId).ifPresent(strategy -> {
            strategy.setStatus(StrategyStatus.ACTIVE);
            if (strategy.currentState() == StrategyLifecycleState.PAUSED) {
                strategy.setCurrentState(StrategyLifecycleState.VALIDATED);
            }
            strategyRepository.save(strategy);
            stateMachine.transition(strategy, strategy.currentState(), StrategyEventType.STRATEGY_RESUMED, "Strategy resumed", "{}");
        });
    }

    public void stop(String strategyId) {
        strategyRepository.findById(strategyId).ifPresent(strategy -> {
            strategy.setStatus(StrategyStatus.STOPPED);
            strategy.setCurrentState(StrategyLifecycleState.STOPPED);
            strategyRepository.save(strategy);
            stateMachine.transition(strategy, StrategyLifecycleState.STOPPED, StrategyEventType.STRATEGY_STOPPED, "Strategy stopped", "{}");
        });
    }

    public void delete(String strategyId) {
        stop(strategyId);
        strategyRepository.deleteById(strategyId);
        orderRepository.deleteByStrategyId(strategyId);
        eventRepository.deleteByStrategyId(strategyId);
    }

    public StrategyCreationResult closePosition(String strategyId) {
        Optional<Strategy> maybeStrategy = strategyRepository.findById(strategyId);
        if (maybeStrategy.isEmpty()) {
            return StrategyCreationResult.failed("Strategy not found");
        }
        Strategy strategy = maybeStrategy.get();
        Optional<com.neuralarc.api.AlpacaPositionData> position = alpacaClient.getPosition(strategy.symbol());
        if (position.isEmpty() || !position.get().exists()) {
            return StrategyCreationResult.failed("No open position to close");
        }
        BigDecimal latestPrice = alpacaClient.getLatestPrice(strategy.symbol());
        int quantity = position.get().quantity().setScale(0, java.math.RoundingMode.DOWN).intValue();
        if (quantity <= 0) {
            return StrategyCreationResult.failed("No open quantity to close");
        }
        String clientOrderId = buildClientOrderId(strategy.id(), StrategyStage.CLOSE_POSITION);
        com.neuralarc.api.AlpacaOrderData submitted = alpacaClient.submitLimitSellOrder(strategy.symbol(), quantity, latestPrice, clientOrderId);
        StrategyOrder order = new StrategyOrder(
                java.util.UUID.randomUUID().toString(),
                strategy.id(),
                StrategyStage.CLOSE_POSITION,
                submitted.orderId(),
                clientOrderId,
                strategy.symbol(),
                StrategyOrderSide.SELL,
                StrategyOrderType.LIMIT,
                latestPrice,
                BigDecimal.ZERO,
                BigDecimal.valueOf(quantity),
                submitted.filledQuantity(),
                submitted.filledAveragePrice(),
                mapOrderStatus(submitted.status()),
                Instant.now(),
                Instant.now(),
                null,
                submitted.rawJson()
        );
        orderRepository.save(order);
        strategy.setLatestOrderStatus(order.status().name());
        strategy.setLatestAlpacaOrderId(order.alpacaOrderId());
        strategyRepository.save(strategy);
        stateMachine.transition(strategy, StrategyLifecycleState.SELL_PLACED, StrategyEventType.ORDER_SUBMITTED, "Close position order submitted", submitted.rawJson());
        return StrategyCreationResult.success(strategy.id(), order.id(), order.alpacaOrderId(), order.clientOrderId());
    }

    public static String buildClientOrderId(String strategyId, StrategyStage stage) {
        return "neuralarc-" + strategyId + "-" + stage.name() + "-" + System.currentTimeMillis();
    }

    public static StrategyOrderStatus mapOrderStatus(String alpacaStatus) {
        String normalized = alpacaStatus == null ? "" : alpacaStatus.trim().toLowerCase();
        return switch (normalized) {
            case "new", "accepted", "pending_new", "accepted_for_bidding" -> StrategyOrderStatus.SUBMITTED;
            case "partially_filled" -> StrategyOrderStatus.PARTIALLY_FILLED;
            case "filled" -> StrategyOrderStatus.FILLED;
            case "canceled", "expired" -> StrategyOrderStatus.CANCELED;
            case "rejected", "suspended" -> StrategyOrderStatus.REJECTED;
            case "pending_cancel", "pending_replace", "calculated" -> StrategyOrderStatus.PENDING;
            case "failed" -> StrategyOrderStatus.FAILED;
            default -> StrategyOrderStatus.PENDING;
        };
    }

    public record StrategyCreationResult(boolean success, String strategyId, String strategyOrderId, String alpacaOrderId, String clientOrderId, String error) {
        public static StrategyCreationResult success(String strategyId, String strategyOrderId, String alpacaOrderId, String clientOrderId) {
            return new StrategyCreationResult(true, strategyId, strategyOrderId, alpacaOrderId, clientOrderId, null);
        }

        public static StrategyCreationResult failed(String error) {
            return new StrategyCreationResult(false, null, null, null, null, error == null ? "Unknown error" : error);
        }
    }
}
