package com.neuralarc.service;

import com.neuralarc.model.StrategyOrder;
import com.neuralarc.model.StrategyOrderSide;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * The last check before any sell reaches the broker: never sell more shares than the position still
 * has uncommitted.
 *
 * <p>On a margin account a sell larger than the position does not fail — it opens a short. That is how
 * MRVL went short: one poll submitted a stop-loss sell for its single share, and the missing-order
 * repair in the same poll, reading an order list from before that sell, placed a second full-size
 * target sell. Both filled. Whatever path asks for a sell, this caps it at the shares held minus the
 * shares already committed to sell orders still working, so a long-only strategy cannot go short.
 */
final class SellQuantityGuard {
    private SellQuantityGuard() {
    }

    /** How many of {@code requested} shares may be sold; zero means the sell must not be placed. */
    static int allowed(int requested, BigDecimal heldShares, List<StrategyOrder> strategyOrders) {
        if (requested <= 0 || heldShares == null) {
            return 0;
        }
        int held = heldShares.setScale(0, RoundingMode.DOWN).intValue();
        int available = held - committedToSells(strategyOrders);
        return Math.max(0, Math.min(requested, available));
    }

    /** Shares still to fill on this strategy's working sell orders. */
    static int committedToSells(List<StrategyOrder> strategyOrders) {
        if (strategyOrders == null) {
            return 0;
        }
        BigDecimal committed = BigDecimal.ZERO;
        for (StrategyOrder order : strategyOrders) {
            if (order == null || order.side() != StrategyOrderSide.SELL || !order.isPending()) {
                continue;
            }
            BigDecimal remaining = order.requestedQuantity().subtract(order.filledQuantity());
            if (remaining.signum() > 0) {
                committed = committed.add(remaining);
            }
        }
        return committed.setScale(0, RoundingMode.UP).intValue();
    }
}
