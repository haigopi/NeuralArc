package com.neuralarc.ui;

import com.neuralarc.model.StrategyLifecycleState;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds rows that claim a share of a broker position already accounted for by another row.
 *
 * <p>Several strategies on one symbol are legitimate - a position can be worked in parts - so this
 * never flags a row the broker position still covers. It only reports rows left with nothing after
 * {@link BrokerPositionAllocator} has divided the position, which say they hold shares and have no
 * order working at the broker. Those are the leftovers behind "two AVGO rows, one AVGO position".
 */
final class DuplicatePositionReconciler {
    private DuplicatePositionReconciler() {
    }

    /** One same-symbol row after allocation. */
    record Row(
            String strategyId,
            StrategyLifecycleState state,
            int localClaim,
            int allocatedShares,
            boolean hasPendingBrokerOrder
    ) {
    }

    /**
     * @param sameSymbolRows every in-scope row for one mode and symbol
     * @return ids of the rows whose claim the broker position cannot cover
     */
    static List<String> overClaimedStrategyIds(List<Row> sameSymbolRows) {
        if (sameSymbolRows == null || sameSymbolRows.size() < 2) {
            return List.of();
        }
        int allocatedTotal = sameSymbolRows.stream().mapToInt(Row::allocatedShares).sum();
        if (allocatedTotal <= 0) {
            // The broker holds nothing for this symbol; the existing missing-position rule owns that case.
            return List.of();
        }
        List<String> overClaimed = new ArrayList<>();
        for (Row row : sameSymbolRows) {
            if (row.allocatedShares() > 0 || row.hasPendingBrokerOrder()) {
                continue;
            }
            if (row.localClaim() > 0 || holdsPositionByLifecycle(row.state())) {
                overClaimed.add(row.strategyId());
            }
        }
        return List.copyOf(overClaimed);
    }

    /** True when the row presents itself as holding shares, rather than waiting to buy them. */
    private static boolean holdsPositionByLifecycle(StrategyLifecycleState state) {
        return state == StrategyLifecycleState.BASE_BUY_FILLED
                || state == StrategyLifecycleState.BASE_BUY_PARTIALLY_FILLED
                || state == StrategyLifecycleState.BUY_LIMIT_1_FILLED
                || state == StrategyLifecycleState.BUY_LIMIT_1_PARTIALLY_FILLED
                || state == StrategyLifecycleState.BUY_LIMIT_2_FILLED
                || state == StrategyLifecycleState.BUY_LIMIT_2_PARTIALLY_FILLED
                || state == StrategyLifecycleState.SELL_PLACED
                || state == StrategyLifecycleState.SELL_PARTIALLY_FILLED;
    }
}
