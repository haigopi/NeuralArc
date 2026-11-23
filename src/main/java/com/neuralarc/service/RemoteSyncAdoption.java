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

    /**
     * Whether a broker symbol is an ordinary ticker the strategy engine can trade.
     *
     * <p>Brokers also report corporate-action and derivative instruments as positions — a contingent
     * value right such as {@code 825CVR012}, a CUSIP-style id, an option contract. They are held but
     * not tradable here, so adopting one only creates a row whose orders can never work. Every such
     * identifier carries a digit, which no equity ticker does; share classes use {@code .} or
     * {@code -} (BRK.B). The position still shows in the broker reconciliation report.
     */
    static boolean isOrdinaryTicker(String symbol) {
        return symbol != null && symbol.matches("[A-Za-z]+([.\\-][A-Za-z]+)?");
    }

    private static boolean holdsShares(AlpacaPositionData position) {
        return position != null && position.exists();
    }
}
