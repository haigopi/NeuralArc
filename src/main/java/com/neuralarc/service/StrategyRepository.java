package com.neuralarc.service;

import com.neuralarc.model.Strategy;

import java.util.List;
import java.util.Optional;

public interface StrategyRepository {
    void save(Strategy strategy);

    Optional<Strategy> findById(String id);

    List<Strategy> findAll();

    List<Strategy> findActive();

    void deleteById(String id);
}
