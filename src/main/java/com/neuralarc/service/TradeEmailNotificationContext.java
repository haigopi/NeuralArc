package com.neuralarc.service;

import com.neuralarc.model.StrategyOrder;
import com.neuralarc.util.ClientOrderId;
import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

record TradeEmailNotificationContext(
        String workspaceName,
        String workspaceCode,
        BigDecimal strategyNetPnl,
        BigDecimal workspaceNetPnl,
        List<StrategyOrder> orderHistory
) {
    TradeEmailNotificationContext {
        workspaceName = workspaceName == null || workspaceName.isBlank() ? "Unassigned" : workspaceName.trim();
        workspaceCode = workspaceCode == null || workspaceCode.isBlank() ? ClientOrderId.UNASSIGNED_CODE : workspaceCode.trim();
        strategyNetPnl = Monetary.round(strategyNetPnl);
        workspaceNetPnl = Monetary.round(workspaceNetPnl);
        orderHistory = orderHistory == null ? List.of() : List.copyOf(orderHistory);
    }

    static TradeEmailNotificationContext singleOrder(StrategyOrder order) {
        List<StrategyOrder> history = new ArrayList<>();
        if (order != null) {
            history.add(order);
        }
        BigDecimal strategyPnl = StrategyOrderAccounting.realizedPnlForOrders(history);
        return new TradeEmailNotificationContext(
                "Unassigned",
                ClientOrderId.UNASSIGNED_CODE,
                strategyPnl,
                strategyPnl,
                history
        );
    }
}
