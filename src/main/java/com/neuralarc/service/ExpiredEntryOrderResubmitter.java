package com.neuralarc.service;

import com.neuralarc.api.AlpacaClient;
import com.neuralarc.api.AlpacaOrderData;
import com.neuralarc.api.AlpacaPositionData;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyOrder;
import com.neuralarc.model.StrategyStage;

import java.util.List;
import java.util.Optional;
import java.util.Comparator;

final class ExpiredEntryOrderResubmitter {
    private final StrategyOrderRepository orderRepository;
    private final AlpacaClient alpacaClient;

    ExpiredEntryOrderResubmitter(StrategyOrderRepository orderRepository, AlpacaClient alpacaClient) {
        this.orderRepository = orderRepository;
        this.alpacaClient = alpacaClient;
    }

    boolean canAutoRetryFailed(Strategy strategy) {
        List<AlpacaOrderData> remoteOpenOrders = alpacaClient.getOpenOrders(strategy.symbol());
        Optional<AlpacaPositionData> position = alpacaClient.getPosition(strategy.symbol());
        return remoteOpenOrders.isEmpty() && (position.isEmpty() || !position.get().exists());
    }

    boolean canAutoResubmit(Strategy strategy) {
        Optional<StrategyStage> stage = resolveStage(strategy);
        if (stage.isEmpty()) {
            return false;
        }
        List<AlpacaOrderData> remoteOpenOrders = alpacaClient.getOpenOrders(strategy.symbol());
        if (!remoteOpenOrders.isEmpty()) {
            return false;
        }
        Optional<AlpacaPositionData> position = alpacaClient.getPosition(strategy.symbol());
        return switch (stage.get()) {
            case BASE_BUY -> position.isEmpty() || !position.get().exists();
            case BUY_LIMIT_1, BUY_LIMIT_2 -> position.isPresent() && position.get().exists();
            case MANUAL_BUY -> resolveOrder(strategy).isPresent()
                    && position.isPresent()
                    && position.get().exists();
            default -> false;
        };
    }

    Optional<StrategyStage> resolveStage(Strategy strategy) {
        return resolveOrder(strategy).map(StrategyOrder::stage)
                .or(() -> StrategyStageSupport.stageForRuleType(strategy == null ? null : strategy.lastTriggeredRuleType()))
                .or(() -> Optional.of(StrategyStage.BASE_BUY));
    }

    Optional<StrategyOrder> resolveOrder(Strategy strategy) {
        if (strategy == null) {
            return Optional.empty();
        }
        if (strategy.latestAlpacaOrderId() != null && !strategy.latestAlpacaOrderId().isBlank()) {
            Optional<StrategyOrder> order = orderRepository.findByAlpacaOrderId(strategy.latestAlpacaOrderId())
                    .filter(ExpiredEntryOrderResubmitter::isAutoResubmittableBuyStage);
            if (order.isPresent()) {
                return order;
            }
        }
        Optional<StrategyStage> stage = StrategyStageSupport.stageForRuleType(strategy.lastTriggeredRuleType())
                .filter(ExpiredEntryOrderResubmitter::isAutoResubmittableBuyStage);
        if (stage.isPresent()) {
            return orderRepository.findLatestByStrategyStage(strategy.id(), stage.get())
                    .filter(ExpiredEntryOrderResubmitter::isAutoResubmittableBuyStage);
        }
        return orderRepository.findByStrategyId(strategy.id()).stream()
                .filter(ExpiredEntryOrderResubmitter::isAutoResubmittableBuyStage)
                .max(Comparator.comparing(StrategyOrder::submittedAt, Comparator.nullsLast(Comparator.naturalOrder())));
    }

    private static boolean isAutoResubmittableBuyStage(StrategyStage stage) {
        return stage == StrategyStage.BASE_BUY
                || stage == StrategyStage.BUY_LIMIT_1
                || stage == StrategyStage.BUY_LIMIT_2
                || stage == StrategyStage.MANUAL_BUY;
    }

    private static boolean isAutoResubmittableBuyStage(StrategyOrder order) {
        if (order == null) {
            return false;
        }
        StrategyStage stage = order.stage();
        return stage == StrategyStage.BASE_BUY
                || stage == StrategyStage.BUY_LIMIT_1
                || stage == StrategyStage.BUY_LIMIT_2
                || stage == StrategyStage.MANUAL_BUY;
    }
}
