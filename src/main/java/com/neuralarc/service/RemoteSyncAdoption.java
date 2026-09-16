package com.neuralarc.service;

import com.neuralarc.api.AlpacaPositionData;

import java.util.Set;

/**
 * Whether the broker sync should leave a symbol alone.
 *
 * <p>Deleting a strategy suppresses its symbol so the next sync does not resurrect it. That is right
 * for a symbol the operator is finished with, but a suppressed symbol whose shares are still sitting
 * in the account is a different case: skipping it leaves real stock untracked, invisible to the grid,
 * the totals and the risk report. Held shares therefore win over suppression, while a suppressed
 * symbol with nothing held stays deleted.
 *
 * <p>Pure so the rule can be tested without a database or a broker.
 */
final class RemoteSyncAdoption {
    private RemoteSyncAdoption() {
    }

    /**
     * @param position the broker's position for this symbol, or null when it holds none
     * @return true when the symbol should be skipped rather than adopted
     */
    static boolean skipSuppressed(String symbol, Set<String> suppressedSymbols, AlpacaPositionData position) {
        if (symbol == null || suppressedSymbols == null || !suppressedSymbols.contains(symbol)) {
            return false;
        }
        return !holdsShares(position);
    }

    private static boolean holdsShares(AlpacaPositionData position) {
        return position != null && position.exists();
    }
}
