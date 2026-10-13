package com.neuralarc.ui;

import com.neuralarc.model.StrategyOrder;
import com.neuralarc.service.OwnedShares;
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
        return OwnedShares.net(orders);
    }

    /** Whether the order's fills count towards the strategy's shares: filled, or partly filled and still working. */
    static boolean countsAsFilled(StrategyOrder order) {
        return order != null
                && (order.status() == StrategyOrderStatus.FILLED || order.status() == StrategyOrderStatus.PARTIALLY_FILLED);
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
