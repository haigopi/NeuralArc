package com.neuralarc.service;

import com.neuralarc.api.AlpacaClient;
import com.neuralarc.api.AlpacaOrderData;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyEventType;
import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyOrder;
import com.neuralarc.model.StrategyOrderSide;
import com.neuralarc.model.StrategyOrderStatus;
import com.neuralarc.model.StrategyOrderType;
import com.neuralarc.model.StrategyStage;
import com.neuralarc.model.StrategyStatus;
import com.neuralarc.util.BrokerOrderStatusUtil;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

final class ManualBuyOrderSubmitter {
    private final StrategyRepository strategyRepository;
    private final StrategyOrderRepository orderRepository;
    private final AlpacaClient alpacaClient;
    private final StrategyStateMachine stateMachine;
    private WorkspaceCodeResolver workspaceCodeResolver = WorkspaceCodeResolver.unassigned();

    void setWorkspaceCodeResolver(WorkspaceCodeResolver resolver) {
        this.workspaceCodeResolver = resolver == null ? WorkspaceCodeResolver.unassigned() : resolver;
    }

    ManualBuyOrderSubmitter(
            StrategyRepository strategyRepository,
            StrategyOrderRepository orderRepository,
            AlpacaClient alpacaClient,
            StrategyStateMachine stateMachine
    ) {
        this.strategyRepository = strategyRepository;
        this.orderRepository = orderRepository;
        this.alpacaClient = alpacaClient;
        this.stateMachine = stateMachine;
    }

    StrategyService.StrategyCreationResult submitMarket(String strategyId, int quantity) {
        return submit(strategyId, quantity, BigDecimal.ZERO, StrategyOrderType.MARKET);
    }

    StrategyService.StrategyCreationResult submitLimit(String strategyId, int quantity, BigDecimal limitPrice) {
        if (limitPrice == null || limitPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return StrategyService.StrategyCreationResult.failed("Limit price must be greater than zero");
        }
        return submit(strategyId, quantity, limitPrice, StrategyOrderType.LIMIT);
    }

    private StrategyService.StrategyCreationResult submit(
            String strategyId,
            int quantity,
            BigDecimal limitPrice,
            StrategyOrderType orderType
    ) {
        Optional<Strategy> maybeStrategy = strategyRepository.findById(strategyId);
        if (maybeStrategy.isEmpty()) {
            return StrategyService.StrategyCreationResult.failed("Strategy not found");
        }
        if (quantity <= 0) {
            return StrategyService.StrategyCreationResult.failed("Quantity must be greater than zero");
        }
        Strategy strategy = maybeStrategy.get();
        if (strategy.status() != StrategyStatus.ACTIVE && strategy.status() != StrategyStatus.PAUSED) {
            return StrategyService.StrategyCreationResult.failed("Only active or paused strategies can submit a manual buy");
        }

        String clientOrderId = StrategyService.buildClientOrderId(strategy, StrategyStage.MANUAL_BUY, workspaceCodeResolver);
        AlpacaOrderData submitted;
        try {
            submitted = orderType == StrategyOrderType.MARKET
                    ? alpacaClient.submitMarketBuyOrder(strategy.symbol(), quantity, clientOrderId)
                    : alpacaClient.submitLimitBuyOrder(strategy.symbol(), quantity, limitPrice, clientOrderId);
        } catch (RuntimeException ex) {
            String error = manualBuyFailureMessage(orderType, ex.getMessage());
            saveFailedManualBuy(strategy, quantity, limitPrice, orderType, clientOrderId, error);
            return StrategyService.StrategyCreationResult.failed(error);
        }
        if (submitted == null) {
            String error = manualBuyFailureMessage(orderType, "Broker returned no order response.");
            saveFailedManualBuy(strategy, quantity, limitPrice, orderType, clientOrderId, error);
            return StrategyService.StrategyCreationResult.failed(error);
        }
        Instant submittedAt = submitted.submittedAt() == null ? Instant.now() : submitted.submittedAt();
        StrategyOrderStatus status = StrategyService.mapOrderStatus(submitted.status());
        StrategyOrder order = new StrategyOrder(
                UUID.randomUUID().toString(),
                strategy.id(),
                StrategyStage.MANUAL_BUY,
                submitted.orderId(),
                clientOrderId,
                strategy.symbol(),
                StrategyOrderSide.BUY,
                orderType,
                orderType == StrategyOrderType.LIMIT ? limitPrice : BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.valueOf(quantity),
                submitted.filledQuantity(),
                submitted.filledAveragePrice(),
                status,
                submittedAt,
                Instant.now(),
                null,
                submitted.rawJson()
        );
        orderRepository.save(order);
        strategy.setLatestOrderStatus(BrokerOrderStatusUtil.normalize(submitted.status()));
        strategy.setLatestAlpacaOrderId(order.alpacaOrderId());
        strategy.setLastTriggeredRuleType("MANUAL_BUY");
        strategy.clearLastError();
        strategyRepository.save(strategy);
        if (status == StrategyOrderStatus.REJECTED || status == StrategyOrderStatus.FAILED) {
            String error = manualBuyFailureMessage(orderType, submitted.rawJson());
            strategy.setLastError(error);
            strategy.setLastEvent(error);
            strategyRepository.save(strategy);
            return StrategyService.StrategyCreationResult.failed(error);
        }
        stateMachine.transition(
                strategy,
                strategy.currentState() == null ? StrategyLifecycleState.BASE_BUY_FILLED : strategy.currentState(),
                StrategyEventType.ORDER_SUBMITTED,
                orderType == StrategyOrderType.MARKET
                        ? "Manual market buy order submitted"
                        : "Manual limit buy order submitted",
                submitted.rawJson()
        );
        return StrategyService.StrategyCreationResult.success(
                strategy.id(),
                order.id(),
                order.alpacaOrderId(),
                order.clientOrderId(),
                submitted.filledQuantity(),
                submitted.filledAveragePrice()
        );
    }

    private void saveFailedManualBuy(
            Strategy strategy,
            int quantity,
            BigDecimal limitPrice,
            StrategyOrderType orderType,
            String clientOrderId,
            String error
    ) {
        Instant now = Instant.now();
        StrategyOrder order = new StrategyOrder(
                UUID.randomUUID().toString(),
                strategy.id(),
                StrategyStage.MANUAL_BUY,
                null,
                clientOrderId,
                strategy.symbol(),
                StrategyOrderSide.BUY,
                orderType,
                orderType == StrategyOrderType.LIMIT ? limitPrice : BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.valueOf(quantity),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                StrategyOrderStatus.FAILED,
                now,
                now,
                null,
                "{\"error\":\"" + jsonEscape(error) + "\"}"
        );
        orderRepository.save(order);
        strategy.setLatestOrderStatus("failed");
        strategy.setLatestAlpacaOrderId("");
        strategy.setLastTriggeredRuleType("MANUAL_BUY");
        strategy.setLastError(error);
        strategy.setLastEvent(error);
        strategyRepository.save(strategy);
    }

    private String manualBuyFailureMessage(StrategyOrderType orderType, String detail) {
        String type = orderType == StrategyOrderType.MARKET ? "market" : "limit";
        String normalized = detail == null || detail.isBlank() ? "Broker rejected the order." : detail.trim();
        return "Manual " + type + " buy failed: " + normalized;
    }

    private String jsonEscape(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
