package com.neuralarc.service;

import com.neuralarc.api.AlpacaClient;
import com.neuralarc.api.AlpacaOrderData;
import com.neuralarc.model.*;
import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class StrategyService {
    private final StrategyRepository strategyRepository;
    private final StrategyOrderRepository orderRepository;
    private final StrategyExecutionEventRepository eventRepository;
    private final AlpacaClient alpacaClient;
    private final StrategyValidator validator;
    private final boolean liveTradingEnabled;

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
    }

    public StrategyCreationResult createAndActivate(Strategy strategy) {
        List<String> errors = validator.validate(strategy);
        if (strategy.mode() == StrategyMode.LIVE && !liveTradingEnabled) {
            errors.add("LIVE mode is disabled. Set trading.live.enabled=true to allow live trading.");
        }

        if (!errors.isEmpty()) {
            strategy.setStatus(StrategyStatus.FAILED);
            strategy.setLastError(String.join("; ", errors));
            strategyRepository.save(strategy);
            eventRepository.save(event(strategy.id(), StrategyEventType.VALIDATION_FAILED, strategy.lastError(), "{}"));
            return StrategyCreationResult.failed(strategy.lastError());
        }

        String clientOrderId = buildClientOrderId(strategy.id(), StrategyStage.INITIAL_BUY);
        AlpacaOrderData submitted = alpacaClient.submitLimitBuyOrder(
                strategy.symbol(),
                strategy.initialBuyQuantity(),
                strategy.initialBuyLimitPrice(),
                clientOrderId
        );

        StrategyOrderStatus initialStatus = mapOrderStatus(submitted.status());
        StrategyOrder order = new StrategyOrder(
                UUID.randomUUID().toString(),
                strategy.id(),
                StrategyStage.INITIAL_BUY,
                submitted.orderId(),
                clientOrderId,
                strategy.symbol(),
                StrategyOrderSide.BUY,
                StrategyOrderType.LIMIT,
                strategy.initialBuyLimitPrice(),
                strategy.initialBuyQuantity(),
                submitted.filledQuantity(),
                initialStatus,
                Instant.now(),
                initialStatus == StrategyOrderStatus.FILLED ? Instant.now() : null,
                submitted.rawJson()
        );
        orderRepository.save(order);

        if (submitted.orderId().isBlank() || initialStatus == StrategyOrderStatus.FAILED || initialStatus == StrategyOrderStatus.REJECTED) {
            strategy.setStatus(StrategyStatus.FAILED);
            String error = "Failed to submit initial buy order";
            strategy.setLastError(error);
            strategyRepository.save(strategy);
            eventRepository.save(event(strategy.id(), StrategyEventType.STRATEGY_FAILED, error, submitted.rawJson()));
            return StrategyCreationResult.failed(error);
        }

        strategy.setStatus(StrategyStatus.ACTIVE);
        strategy.clearLastError();
        strategyRepository.save(strategy);
        eventRepository.save(event(strategy.id(), StrategyEventType.ORDER_SUBMITTED,
                "Initial buy order submitted", submitted.rawJson()));
        return StrategyCreationResult.success(strategy.id(), order.id(), submitted.orderId(), clientOrderId);
    }

    public void pause(String strategyId) {
        strategyRepository.findById(strategyId).ifPresent(strategy -> {
            strategy.setStatus(StrategyStatus.PAUSED);
            strategyRepository.save(strategy);
            eventRepository.save(event(strategy.id(), StrategyEventType.STRATEGY_PAUSED, "Strategy paused", "{}"));
        });
    }

    public void resume(String strategyId) {
        strategyRepository.findById(strategyId).ifPresent(strategy -> {
            if (strategy.status() == StrategyStatus.PAUSED) {
                strategy.setStatus(StrategyStatus.ACTIVE);
                strategyRepository.save(strategy);
                eventRepository.save(event(strategy.id(), StrategyEventType.STRATEGY_RESUMED, "Strategy resumed", "{}"));
            }
        });
    }

    public void stop(String strategyId) {
        strategyRepository.findById(strategyId).ifPresent(strategy -> {
            strategy.setStatus(StrategyStatus.COMPLETED);
            strategyRepository.save(strategy);
            eventRepository.save(event(strategy.id(), StrategyEventType.STRATEGY_COMPLETED, "Strategy stopped", "{}"));
        });
    }

    private StrategyEventType toFailureEvent(StrategyStatus status) {
        return status == StrategyStatus.FAILED ? StrategyEventType.STRATEGY_FAILED : StrategyEventType.POLL_ERROR;
    }

    private StrategyExecutionEvent event(String strategyId, StrategyEventType type, String message, String metadataJson) {
        return new StrategyExecutionEvent(UUID.randomUUID().toString(), strategyId, type, message, metadataJson, Instant.now());
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

