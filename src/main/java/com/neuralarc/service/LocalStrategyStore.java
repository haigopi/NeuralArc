package com.neuralarc.service;

import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyExecutionEvent;
import com.neuralarc.model.StrategyOrder;

import java.util.List;
import java.util.Optional;

public class LocalStrategyStore {
    private final StrategyRepository strategyRepository;
    private final StrategyOrderRepository orderRepository;
    private final StrategyExecutionEventRepository eventRepository;

    public LocalStrategyStore(
            StrategyRepository strategyRepository,
            StrategyOrderRepository orderRepository,
            StrategyExecutionEventRepository eventRepository
    ) {
        this.strategyRepository = strategyRepository;
        this.orderRepository = orderRepository;
        this.eventRepository = eventRepository;
    }

    public void saveStrategy(Strategy strategy) {
        strategyRepository.save(strategy);
    }

    public Optional<Strategy> findStrategy(String strategyId) {
        return strategyRepository.findById(strategyId);
    }

    public List<Strategy> findAllStrategies() {
        return strategyRepository.findAll();
    }

    public List<Strategy> findActiveStrategies() {
        return strategyRepository.findActive();
    }

    public void saveOrder(StrategyOrder order) {
        orderRepository.save(order);
    }

    public List<StrategyOrder> findOrders(String strategyId) {
        return orderRepository.findByStrategyId(strategyId);
    }

    public void saveEvent(StrategyExecutionEvent event) {
        eventRepository.save(event);
    }

    public List<StrategyExecutionEvent> findEvents(String strategyId) {
        return eventRepository.findByStrategyId(strategyId);
    }

    public void deleteStrategy(String strategyId) {
        strategyRepository.deleteById(strategyId);
        orderRepository.deleteByStrategyId(strategyId);
        eventRepository.deleteByStrategyId(strategyId);
    }
}
