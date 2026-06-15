package com.neuralarc.service;

import com.neuralarc.model.StrategyOrder;
import com.neuralarc.model.StrategyOrderSide;
import com.neuralarc.model.StrategyOrderType;

import java.util.List;

/**
 * Guard rules for the right-click "Cancel Pending Limit Buy" action. An order is cancelable only
 * when it is an open (pending) limit BUY — never a filled position, and never a canceled, rejected,
 * or expired order (expired maps to canceled / terminal).
 */
public final class PendingBuyOrderGuard {
    private PendingBuyOrderGuard() {
    }

    public static boolean isCancelablePendingLimitBuy(StrategyOrder order) {
        return order != null
                && order.side() == StrategyOrderSide.BUY
                && order.orderType() == StrategyOrderType.LIMIT
                && order.isPending();
    }

    public static boolean hasCancelablePendingLimitBuy(List<StrategyOrder> orders) {
        return orders != null && orders.stream().anyMatch(PendingBuyOrderGuard::isCancelablePendingLimitBuy);
    }
}
