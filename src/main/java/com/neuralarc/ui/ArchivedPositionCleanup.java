package com.neuralarc.ui;

import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyOrder;
import com.neuralarc.model.StrategyOrderStatus;
import com.neuralarc.model.StrategyStatus;

import java.math.BigDecimal;
import java.util.List;

/**
 * Which past positions "Clean Archived Positions" may delete: strategies already off every grid
 * (archived or stopped) that never bought or sold a single share and have nothing working at the
 * broker — cancelled entries and ones archived after a failure. Trade History is built only from filled
 * orders, so deleting these loses no history; they only weigh down the strategy, order and event tables.
 */
final class ArchivedPositionCleanup {
    private ArchivedPositionCleanup() {
    }

    static boolean isCleanable(Strategy strategy, List<StrategyOrder> orders) {
        if (strategy == null) {
            return false;
        }
        StrategyStatus status = strategy.status();
        if (status != StrategyStatus.ARCHIVED && status != StrategyStatus.STOPPED) {
            return false;
        }
        for (StrategyOrder order : orders == null ? List.<StrategyOrder>of() : orders) {
            if (order.isPending()) {
                return false; // Something still working at the broker must stay tracked.
            }
            BigDecimal filled = order.filledQuantity() == null ? BigDecimal.ZERO : order.filledQuantity();
            if (filled.signum() > 0 || order.status() == StrategyOrderStatus.FILLED
                    || order.status() == StrategyOrderStatus.PARTIALLY_FILLED) {
                return false; // Any fill is Trade History.
            }
        }
        return true;
    }
}
