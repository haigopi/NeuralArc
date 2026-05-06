package com.neuralarc.ui;

import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyStatus;

import java.util.List;

/**
 * Determines whether adding a new strategy for a given symbol and mode would constitute
 * a duplicate conflict, taking into account the operator-configured duplicate-symbol setting.
 *
 * <p>When {@code allowDuplicates} is {@code true}, no conflict is ever reported.
 * When {@code false} (the default), a conflict is reported only when an <em>active or
 * paused</em> strategy already exists for the same symbol and mode. Strategies that are
 * stopped, completed, failed, or archived are not considered blocking — they represent
 * finished trades in history and should not prevent new strategies for the same symbol.
 */
public final class DuplicateSymbolPolicy {

    private DuplicateSymbolPolicy() {}

    /**
     * Returns {@code true} when adding a strategy for {@code symbol}/{@code mode} would
     * conflict with an existing strategy, given the current operator setting.
     *
     * @param symbol            the stock symbol being added (case-insensitive)
     * @param mode              the target strategy mode (PAPER or LIVE)
     * @param existingStrategies all strategies currently known to the repository
     * @param allowDuplicates   when {@code true} the operator permits multiple strategies
     *                          per symbol; the method always returns {@code false}
     * @return {@code true} if the add should be blocked as a duplicate
     */
    public static boolean wouldBeDuplicate(
            String symbol,
            StrategyMode mode,
            List<Strategy> existingStrategies,
            boolean allowDuplicates
    ) {
        if (allowDuplicates) {
            return false;
        }
        return existingStrategies.stream()
                .filter(s -> s.mode() == mode)
                .filter(s -> s.status() == StrategyStatus.ACTIVE || s.status() == StrategyStatus.PAUSED)
                .anyMatch(s -> s.symbol().equalsIgnoreCase(symbol));
    }
}

