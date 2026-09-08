package com.neuralarc.ui;

import com.neuralarc.model.StrategyOrder;
import com.neuralarc.model.StrategyOrderSide;
import com.neuralarc.model.StrategyOrderStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

final class StrategyOrderFillSupport {
    private StrategyOrderFillSupport() {
    }

    static BigDecimal resolvedFillPrice(StrategyOrder order) {
        if (order == null) {
            return BigDecimal.ZERO;
        }
        if (order.filledAveragePrice() != null && order.filledAveragePrice().compareTo(BigDecimal.ZERO) > 0) {
            return order.filledAveragePrice();
        }
        return order.limitPrice() == null ? BigDecimal.ZERO : order.limitPrice();
    }

    /**
     * Shares this strategy's own filled orders say it still holds: filled buys minus filled sells.
     * Used to divide a single broker position between several strategies on the same symbol.
     */
    static int netFilledShares(List<StrategyOrder> orders) {
        if (orders == null || orders.isEmpty()) {
            return 0;
        }
        BigDecimal net = BigDecimal.ZERO;
        for (StrategyOrder order : orders) {
            if (order == null || order.side() == null) {
                continue;
            }
            if (order.status() != StrategyOrderStatus.FILLED && order.status() != StrategyOrderStatus.PARTIALLY_FILLED) {
                continue;
            }
            BigDecimal quantity = resolvedFilledQuantity(order);
            net = order.side() == StrategyOrderSide.BUY ? net.add(quantity) : net.subtract(quantity);
        }
        return Math.max(0, net.setScale(0, RoundingMode.DOWN).intValue());
    }

    static BigDecimal resolvedFilledQuantity(StrategyOrder order) {
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
