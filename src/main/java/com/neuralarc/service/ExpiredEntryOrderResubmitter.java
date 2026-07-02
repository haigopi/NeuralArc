package com.neuralarc.service;

import com.neuralarc.api.AlpacaClient;
import com.neuralarc.api.AlpacaOrderData;
import com.neuralarc.api.AlpacaPositionData;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyStage;

import java.util.List;
import java.util.Optional;

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
            default -> false;
        };
    }

    Optional<StrategyStage> resolveStage(Strategy strategy) {
        if (strategy == null) {
            return Optional.empty();
        }
        if (strategy.latestAlpacaOrderId() != null && !strategy.latestAlpacaOrderId().isBlank()) {
            Optional<StrategyStage> stageFromOrder = orderRepository.findByAlpacaOrderId(strategy.latestAlpacaOrderId())
                    .map(order -> order.stage())
                    .filter(stage -> stage == StrategyStage.BASE_BUY
                            || stage == StrategyStage.BUY_LIMIT_1
                            || stage == StrategyStage.BUY_LIMIT_2);
            if (stageFromOrder.isPresent()) {
                return stageFromOrder;
            }
        }
        return StrategyStageSupport.stageForRuleType(strategy.lastTriggeredRuleType())
                .or(() -> Optional.of(StrategyStage.BASE_BUY));
    }
}
