package com.neuralarc.service;

import com.neuralarc.model.StrategyOrder;
import com.neuralarc.model.StrategyStage;

import java.util.List;
import java.util.Optional;

public interface StrategyOrderRepository {
    void save(StrategyOrder order);

    List<StrategyOrder> findByStrategyId(String strategyId);

    Optional<StrategyOrder> findLatestByStrategyStage(String strategyId, StrategyStage stage);

    void deleteByStrategyId(String strategyId);
}
