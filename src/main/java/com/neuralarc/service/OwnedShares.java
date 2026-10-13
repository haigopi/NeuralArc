package com.neuralarc.service;

import com.neuralarc.model.StrategyOrder;
import com.neuralarc.model.StrategyOrderSide;
import com.neuralarc.model.StrategyOrderStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** The shares one entry holds according to its own orders, as opposed to what the broker nets per symbol. */
public final class OwnedShares {
    private OwnedShares() {
    }

    /** Filled and part-filled buys minus filled and part-filled sells, never below zero. */
    public static int net(List<StrategyOrder> orders) {
        if (orders == null || orders.isEmpty()) {
            return 0;
        }
        BigDecimal net = BigDecimal.ZERO;
        for (StrategyOrder order : orders) {
            if (order == null || order.side() == null || !countsAsFilled(order)) {
                continue;
            }
            BigDecimal quantity = filledQuantity(order);
            net = order.side() == StrategyOrderSide.BUY ? net.add(quantity) : net.subtract(quantity);
        }
        return Math.max(0, net.setScale(0, RoundingMode.DOWN).intValue());
    }

    /** Whether the order's fills count: filled, or partly filled and still working. */
    public static boolean countsAsFilled(StrategyOrder order) {
        return order != null
                && (order.status() == StrategyOrderStatus.FILLED || order.status() == StrategyOrderStatus.PARTIALLY_FILLED);
    }

    public static BigDecimal filledQuantity(StrategyOrder order) {
        if (order == null) {
            return BigDecimal.ZERO;
        }
        if (order.filledQuantity() != null && order.filledQuantity().compareTo(BigDecimal.ZERO) > 0) {
            return order.filledQuantity();
        }
        if (order.status() == StrategyOrderStatus.FILLED
                && order.requestedQuantity() != null
                && order.requestedQuantity().compareTo(BigDecimal.ZERO) > 0) {
            return order.requestedQuantity();
        }
        return BigDecimal.ZERO;
    }
}
