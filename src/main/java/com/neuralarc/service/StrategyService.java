package com.neuralarc.service;

import com.neuralarc.api.AlpacaClient;
import com.neuralarc.model.*;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class StrategyService {
    private final StrategyRepository strategyRepository;
    private final StrategyOrderRepository orderRepository;
    private final StrategyExecutionEventRepository eventRepository;
    private final AlpacaClient alpacaClient;
    private final StrategyValidator validator;
    private final boolean liveTradingEnabled;
    private final StrategyMode defaultStrategyMode;
    private final StrategyStateMachine stateMachine;
    private final StrategyEngine strategyEngine;

    public StrategyService(
            StrategyRepository strategyRepository,
            StrategyOrderRepository orderRepository,
            StrategyExecutionEventRepository eventRepository,
            AlpacaClient alpacaClient,
            StrategyValidator validator,
            boolean liveTradingEnabled,
            StrategyMode defaultStrategyMode
    ) {
        this.strategyRepository = strategyRepository;
        this.orderRepository = orderRepository;
        this.eventRepository = eventRepository;
        this.alpacaClient = alpacaClient;
        this.validator = validator;
        this.liveTradingEnabled = liveTradingEnabled;
        this.defaultStrategyMode = defaultStrategyMode == null ? StrategyMode.PAPER : defaultStrategyMode;
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
            cancelPendingRemoteOrders(strategy);
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
            strategyEngine.resumeStrategy(strategy);
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
        strategyRepository.findById(strategyId).ifPresent(this::cancelPendingRemoteOrders);
        stop(strategyId);
        strategyRepository.deleteById(strategyId);
        orderRepository.deleteByStrategyId(strategyId);
        eventRepository.deleteByStrategyId(strategyId);
    }

    public List<Strategy> syncRemoteStrategies() {
        Set<String> localSymbols = strategyRepository.findAll().stream()
                .map(Strategy::symbol)
                .map(String::toUpperCase)
                .collect(Collectors.toCollection(HashSet::new));

        Map<String, List<com.neuralarc.api.AlpacaOrderData>> openOrdersBySymbol = alpacaClient.getOpenOrders().stream()
                .filter(order -> order.symbol() != null && !order.symbol().isBlank())
                .collect(Collectors.groupingBy(order -> order.symbol().toUpperCase(), HashMap::new, Collectors.toList()));
        Map<String, com.neuralarc.api.AlpacaPositionData> positionsBySymbol = alpacaClient.getPositions().stream()
                .filter(position -> position.symbol() != null && !position.symbol().isBlank())
                .collect(Collectors.toMap(position -> position.symbol().toUpperCase(), position -> position, (left, right) -> left, HashMap::new));

        Set<String> remoteSymbols = new HashSet<>(openOrdersBySymbol.keySet());
        remoteSymbols.addAll(positionsBySymbol.keySet());

        List<Strategy> created = new java.util.ArrayList<>();
        for (String symbol : remoteSymbols) {
            if (localSymbols.contains(symbol)) {
                continue;
            }
            Strategy strategy = buildRemoteStrategy(symbol, openOrdersBySymbol.getOrDefault(symbol, List.of()), positionsBySymbol.get(symbol));
            strategyRepository.save(strategy);
            for (com.neuralarc.api.AlpacaOrderData order : openOrdersBySymbol.getOrDefault(symbol, List.of())) {
                orderRepository.save(buildRemoteOrder(strategy, order));
            }
            stateMachine.transition(strategy, strategy.currentState(), StrategyEventType.STRATEGY_RESUMED,
                    "Remote Alpaca strategy synced and resumed", "{}");
            created.add(strategy);
        }
        return created;
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
        Instant submittedAt = submitted.submittedAt() == null ? Instant.now() : submitted.submittedAt();
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
                submittedAt,
                Instant.now(),
                null,
                submitted.rawJson()
        );
        orderRepository.save(order);
        strategy.setLatestOrderStatus(order.status().name());
        strategy.setLatestAlpacaOrderId(order.alpacaOrderId());
        strategy.setLastTriggeredRuleType("CLOSE_POSITION");
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

    private void cancelPendingRemoteOrders(Strategy strategy) {
        List<com.neuralarc.api.AlpacaOrderData> openOrders = alpacaClient.getOpenOrders(strategy.symbol());
        if (openOrders.isEmpty()) {
            return;
        }
        boolean canceledAny = false;
        for (com.neuralarc.api.AlpacaOrderData order : openOrders) {
            if (alpacaClient.cancelOrder(order.orderId())) {
                canceledAny = true;
                orderRepository.findByStrategyId(strategy.id()).stream()
                        .filter(localOrder -> localOrder.isPending())
                        .filter(localOrder -> order.orderId().equals(localOrder.alpacaOrderId())
                                || order.clientOrderId().equals(localOrder.clientOrderId()))
                        .forEach(localOrder -> {
                            localOrder.setStatus(StrategyOrderStatus.CANCELED);
                            localOrder.setRawResponseJson(order.rawJson());
                            orderRepository.save(localOrder);
                        });
            }
        }
        if (canceledAny) {
            stateMachine.transition(strategy, strategy.currentState(), StrategyEventType.ORDER_STATUS_UPDATED,
                    "Open Alpaca orders canceled for strategy " + strategy.symbol(), "{}");
        }
    }

    private Strategy buildRemoteStrategy(
            String symbol,
            List<com.neuralarc.api.AlpacaOrderData> openOrders,
            com.neuralarc.api.AlpacaPositionData position
    ) {
        com.neuralarc.api.AlpacaOrderData latestOpenOrder = openOrders.stream()
                .max(Comparator.comparing(com.neuralarc.api.AlpacaOrderData::orderId))
                .orElse(null);
        BigDecimal baseBuyPrice = position != null && position.avgEntryPrice().compareTo(BigDecimal.ZERO) > 0
                ? position.avgEntryPrice()
                : latestOpenOrder != null && latestOpenOrder.limitPrice().compareTo(BigDecimal.ZERO) > 0
                ? latestOpenOrder.limitPrice()
                : BigDecimal.ONE;
        int baseBuyQuantity = position != null && position.quantity().compareTo(BigDecimal.ZERO) > 0
                ? position.quantity().intValue()
                : requestedQuantity(latestOpenOrder);
        if (baseBuyQuantity <= 0) {
            baseBuyQuantity = 1;
        }
        StrategyLifecycleState state = determineRemoteState(latestOpenOrder, position);
        Strategy strategy = new Strategy(
                UUID.randomUUID().toString(),
                symbol + " Remote Strategy",
                symbol,
                defaultStrategyMode,
                StrategyStatus.ACTIVE,
                state,
                baseBuyPrice,
                baseBuyQuantity,
                BigDecimal.ZERO,
                0,
                BigDecimal.ZERO,
                0,
                false,
                StopLossType.FIXED_PRICE,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                false,
                BigDecimal.ZERO,
                false,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                true,
                false,
                ProfitHoldType.PERCENT_TRAILING,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                false,
                Math.max(baseBuyQuantity, baseBuyQuantity * 2),
                baseBuyPrice.multiply(BigDecimal.valueOf(Math.max(baseBuyQuantity, 1L) * 2L)),
                30,
                Instant.now(),
                Instant.now()
        );
        strategy.setLastEvent("Synced from Alpaca remote state");
        strategy.setLatestOrderStatus(latestOpenOrder == null ? "" : latestOpenOrder.status());
        strategy.setLatestAlpacaOrderId(latestOpenOrder == null ? "" : latestOpenOrder.orderId());
        return strategy;
    }

    private StrategyOrder buildRemoteOrder(Strategy strategy, com.neuralarc.api.AlpacaOrderData remoteOrder) {
        Instant submittedAt = remoteOrder.submittedAt() == null ? Instant.now() : remoteOrder.submittedAt();
        return new StrategyOrder(
                UUID.randomUUID().toString(),
                strategy.id(),
                mapRemoteStage(remoteOrder),
                remoteOrder.orderId(),
                remoteOrder.clientOrderId().isBlank() ? buildClientOrderId(strategy.id(), mapRemoteStage(remoteOrder)) : remoteOrder.clientOrderId(),
                strategy.symbol(),
                "sell".equalsIgnoreCase(remoteOrder.side()) ? StrategyOrderSide.SELL : StrategyOrderSide.BUY,
                StrategyOrderType.LIMIT,
                remoteOrder.limitPrice(),
                BigDecimal.ZERO,
                BigDecimal.valueOf(requestedQuantity(remoteOrder)),
                remoteOrder.filledQuantity(),
                remoteOrder.filledAveragePrice(),
                mapOrderStatus(remoteOrder.status()),
                submittedAt,
                Instant.now(),
                null,
                remoteOrder.rawJson()
        );
    }

    private StrategyStage mapRemoteStage(com.neuralarc.api.AlpacaOrderData remoteOrder) {
        if ("sell".equalsIgnoreCase(remoteOrder.side())) {
            return StrategyStage.TARGET_SELL;
        }
        return StrategyStage.BASE_BUY;
    }

    private StrategyLifecycleState determineRemoteState(com.neuralarc.api.AlpacaOrderData latestOpenOrder, com.neuralarc.api.AlpacaPositionData position) {
        if (latestOpenOrder != null && "sell".equalsIgnoreCase(latestOpenOrder.side())) {
            return StrategyLifecycleState.SELL_PLACED;
        }
        if (position != null && position.exists()) {
            return StrategyLifecycleState.BASE_BUY_FILLED;
        }
        if (latestOpenOrder != null && "buy".equalsIgnoreCase(latestOpenOrder.side())) {
            return StrategyLifecycleState.BASE_BUY_PLACED;
        }
        return StrategyLifecycleState.VALIDATED;
    }

    private int requestedQuantity(com.neuralarc.api.AlpacaOrderData order) {
        if (order == null || order.rawJson() == null || order.rawJson().isBlank()) {
            return 0;
        }
        try {
            JSONObject json = new JSONObject(order.rawJson());
            return new BigDecimal(json.optString("qty", "0")).intValue();
        } catch (Exception ex) {
            return 0;
        }
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
