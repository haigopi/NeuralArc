package com.neuralarc.service;

import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyOrder;
import com.neuralarc.model.StrategyWorkspace;
import com.neuralarc.util.ClientOrderId;
import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

final class RepositoryTradeEmailNotificationContextProvider implements TradeEmailNotificationContextProvider {
    private final StrategyRepository strategyRepository;
    private final StrategyOrderRepository orderRepository;
    private final WorkspaceRepository workspaceRepository;

    RepositoryTradeEmailNotificationContextProvider(
            StrategyRepository strategyRepository,
            StrategyOrderRepository orderRepository,
            WorkspaceRepository workspaceRepository
    ) {
        this.strategyRepository = strategyRepository;
        this.orderRepository = orderRepository;
        this.workspaceRepository = workspaceRepository;
    }

    @Override
    public TradeEmailNotificationContext contextFor(Strategy strategy, StrategyOrder order) {
        if (strategy == null || orderRepository == null) {
            return TradeEmailNotificationContext.singleOrder(order);
        }
        List<StrategyOrder> history = historyFor(strategy, order);
        BigDecimal strategyPnl = StrategyOrderAccounting.realizedPnlForOrders(history);
        return new TradeEmailNotificationContext(
                workspaceName(strategy),
                workspaceCode(strategy),
                strategyPnl,
                workspacePnl(strategy, history),
                history
        );
    }

    private List<StrategyOrder> historyFor(Strategy strategy, StrategyOrder currentOrder) {
        List<StrategyOrder> history = new ArrayList<>(orderRepository.findByStrategyId(strategy.id()));
        if (currentOrder != null && history.stream().noneMatch(order -> Objects.equals(order.id(), currentOrder.id()))) {
            history.add(currentOrder);
        }
        history.sort(Comparator
                .comparing(StrategyOrder::submittedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(StrategyOrder::updatedAt, Comparator.nullsLast(Comparator.naturalOrder())));
        return history;
    }

    private BigDecimal workspacePnl(Strategy target, List<StrategyOrder> targetHistory) {
        if (strategyRepository == null) {
            return StrategyOrderAccounting.realizedPnlForOrders(targetHistory);
        }
        BigDecimal total = BigDecimal.ZERO;
        boolean includedTarget = false;
        for (Strategy strategy : strategyRepository.findAll()) {
            if (strategy == null || strategy.mode() != StrategyMode.LIVE || !sameWorkspace(target, strategy)) {
                continue;
            }
            List<StrategyOrder> orders = strategy.id().equals(target.id())
                    ? targetHistory
                    : orderRepository.findByStrategyId(strategy.id());
            total = total.add(StrategyOrderAccounting.realizedPnlForOrders(orders));
            includedTarget = includedTarget || strategy.id().equals(target.id());
        }
        if (!includedTarget) {
            total = total.add(StrategyOrderAccounting.realizedPnlForOrders(targetHistory));
        }
        return Monetary.round(total);
    }

    private boolean sameWorkspace(Strategy left, Strategy right) {
        return Objects.equals(left.workspaceId(), right.workspaceId());
    }

    private String workspaceName(Strategy strategy) {
        if (strategy.workspaceId() == null || workspaceRepository == null) {
            return "Unassigned";
        }
        return workspaceRepository.findById(strategy.workspaceId())
                .map(StrategyWorkspace::name)
                .orElse("Workspace " + strategy.workspaceId());
    }

    private String workspaceCode(Strategy strategy) {
        if (strategy.workspaceId() == null || workspaceRepository == null) {
            return ClientOrderId.UNASSIGNED_CODE;
        }
        return workspaceRepository.findById(strategy.workspaceId())
                .map(StrategyWorkspace::code)
                .orElse(ClientOrderId.UNASSIGNED_CODE);
    }
}
