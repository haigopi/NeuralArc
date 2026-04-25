package com.neuralarc.service;

import com.neuralarc.model.StrategyExecutionEvent;

import java.util.List;

public interface StrategyExecutionEventRepository {
    void save(StrategyExecutionEvent event);

    List<StrategyExecutionEvent> findByStrategyId(String strategyId);
}

