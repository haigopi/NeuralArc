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
 * <p>A workspace holds at most one live row per symbol. That is not negotiable by the operator
 * setting: two rows for one symbol inside a workspace share the broker's single netted position, so
 * the second row shows the first one's entry price and P&amp;L. The All Stocks view is a view over
 * every workspace rather than a workspace of its own, so the same symbol legitimately appears there
 * more than once — once per workspace holding it.
 *
 * <p>{@code allowDuplicates} therefore governs only whether a symbol may be worked in <em>different</em>
 * workspaces of the same mode. When {@code false}, one live row per symbol per mode; when
 * {@code true}, one live row per symbol per workspace.
 *
 * <p>"Live" means a row that is still on the Current Strategies grid: created (pending review or
 * placement), active, or paused. Completed, stopped, failed and archived rows are finished trades in
 * history — they hold no position and must never prevent trading that symbol again.
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
     * Returns {@code true} when saving a strategy would conflict with another live strategy for the
     * same symbol/mode. The supplied strategy id is ignored so Edit can save an existing row without
     * treating itself as a duplicate.
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
     * Workspace-aware duplicate check for creation and edit flows.
     *
     * <p>Two rules, applied in order. A symbol is unique among the live rows of one workspace no
     * matter how the setting stands, because the broker nets a symbol into a single position and a
     * second row in the same workspace would show the first one's shares, entry price and P&amp;L.
     * Beyond that, {@code allowDuplicates} decides whether the symbol may also be worked from a
     * different workspace of the same mode.
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
        List<Strategy> sameSymbolInMode = existingStrategies.stream()
                .filter(s -> !s.id().equals(ignoredId))
                .filter(s -> s.mode() == mode)
                .filter(s -> s.symbol().equalsIgnoreCase(symbol))
                .toList();
        boolean takenInTargetWorkspace = sameSymbolInMode.stream()
                .filter(s -> sameWorkspace(s.workspaceId(), targetWorkspaceId))
                .anyMatch(DuplicateSymbolPolicy::occupiesSymbolInWorkspace);
        if (takenInTargetWorkspace) {
            return true;
        }
        return !allowDuplicates && sameSymbolInMode.stream().anyMatch(DuplicateSymbolPolicy::blocksDuplicate);
    }

    /**
     * Whether an existing row already owns this symbol inside its workspace.
     *
     * <p>Stricter than {@link #blocksDuplicate}, which governs the looser cross-workspace rule. Any
     * row still on the Current Strategies grid counts, {@code CREATED} included: a pending-review
     * import or an unplaced manual addition is about to claim the symbol's position, and admitting a
     * second one is what made a freshly imported row display an existing holding's entry and P&amp;L.
     * Completed, stopped, failed and archived rows are history and hold nothing.
     */
    private static boolean occupiesSymbolInWorkspace(Strategy strategy) {
        return switch (strategy.status()) {
            case CREATED, ACTIVE, PAUSED -> true;
            case COMPLETED, FAILED, STOPPED, ARCHIVED -> false;
        };
    }

    /**
     * Whether an existing row blocks the symbol across the whole mode. Deliberately looser than
     * {@link #occupiesSymbolInWorkspace}: a row the operator paused by hand, or one whose limit buy
     * they cancelled, should not stop the symbol being taken up in another workspace.
     */
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
