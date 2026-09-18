package com.neuralarc.ui;

import com.neuralarc.model.Position;

/** Which position the bars column falls back to when no grid row is selected. */
final class PositionBarsSelection {
    private PositionBarsSelection() {
    }

    /** Holds shares and is below what was paid for them. */
    static boolean isLosing(ManagedStrategy entry) {
        if (entry == null || entry.strategy == null) {
            return false;
        }
        Position position = entry.cachedPosition();
        return position != null && position.getTotalShares() > 0 && position.unrealizedPnl().signum() < 0;
    }
}
