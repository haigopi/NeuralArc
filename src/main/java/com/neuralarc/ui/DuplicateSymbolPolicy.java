package com.neuralarc.ui;

import com.neuralarc.model.PauseReason;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyStatus;

import java.util.List;
import java.util.Objects;

/**
 * Determines whether adding a new strategy for a given symbol and mode would constitute
 * a duplicate conflict, taking into account the operator-configured duplicate-symbol setting.
 *
 * <p>When {@code allowDuplicates} is {@code true}, duplicates are allowed only across different
 * workspaces. A same-symbol strategy in the same workspace still blocks. When {@code false}, a
 * conflict is reported whenever an <em>active</em> or system-paused operational strategy already
 * exists for the same symbol and mode.
 * Strategies that are user-canceled, manually canceled, stopped, completed, failed,
 * archived, or legacy paused-without-reason are not considered blocking — they represent
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
        return wouldBeDuplicate(symbol, mode, existingStrategies, allowDuplicates, "");
    }

    /**
     * Returns {@code true} when saving a strategy would conflict with another active
     * or paused strategy for the same symbol/mode. The supplied strategy id is ignored
     * so Edit can save an existing row without treating itself as a duplicate.
     */
    public static boolean wouldBeDuplicate(
            String symbol,
            StrategyMode mode,
            List<Strategy> existingStrategies,
            boolean allowDuplicates,
            String ignoredStrategyId
    ) {
        if (allowDuplicates) {
            return false;
        }
        String ignoredId = ignoredStrategyId == null ? "" : ignoredStrategyId;
        return existingStrategies.stream()
                .filter(s -> !s.id().equals(ignoredId))
                .filter(s -> s.mode() == mode)
                .filter(DuplicateSymbolPolicy::blocksDuplicate)
                .anyMatch(s -> s.symbol().equalsIgnoreCase(symbol));
    }

    /**
     * Workspace-aware duplicate check for creation and edit flows. When duplicate symbols are
     * enabled, the same symbol is still blocked inside the same workspace, but allowed in a
     * different workspace.
     */
    public static boolean wouldBeDuplicate(
            String symbol,
            StrategyMode mode,
            List<Strategy> existingStrategies,
            boolean allowDuplicates,
            String targetWorkspaceId,
            String ignoredStrategyId
    ) {
        String ignoredId = ignoredStrategyId == null ? "" : ignoredStrategyId;
        return existingStrategies.stream()
                .filter(s -> !s.id().equals(ignoredId))
                .filter(s -> s.mode() == mode)
                .filter(DuplicateSymbolPolicy::blocksDuplicate)
                .filter(s -> !allowDuplicates || sameWorkspace(s.workspaceId(), targetWorkspaceId))
                .anyMatch(s -> s.symbol().equalsIgnoreCase(symbol));
    }

    private static boolean blocksDuplicate(Strategy strategy) {
        if (strategy.status() == StrategyStatus.ACTIVE) {
            return true;
        }
        if (strategy.status() != StrategyStatus.PAUSED) {
            return false;
        }
        return strategy.pauseReason() == PauseReason.AUTO_MARKET_CLOSED
                || strategy.pauseReason() == PauseReason.MANUAL_MARKET_CLOSED_OVERRIDE
                || strategy.pauseReason() == PauseReason.SYSTEM_ERROR;
    }

    private static boolean sameWorkspace(String left, String right) {
        return Objects.equals(normalizeWorkspaceId(left), normalizeWorkspaceId(right));
    }

    private static String normalizeWorkspaceId(String workspaceId) {
        return workspaceId == null || workspaceId.isBlank() ? null : workspaceId.trim();
    }
}
