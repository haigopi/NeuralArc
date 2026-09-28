package com.neuralarc.ui;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Which Trade History groups are folded, and what a folded one still shows.
 *
 * <p>A day or a symbol can run to dozens of rows, most of them buys. What an operator looks for is
 * what the trade made — the sells and the group's subtotal — so a folded group keeps exactly those
 * and hides the buys behind them. Groups start folded, because that view is the one worth opening on.
 *
 * <p>Folding is a display state, not a filter: every row stays in the table model and keeps its
 * place, so the search, the sell filter and the totals all still see the whole history.
 */
final class HistoryGroupCollapse {
    private final Set<String> expanded = new HashSet<>();

    /** Whether {@code groupKey}'s buys are on screen. */
    boolean isExpanded(String groupKey) {
        return expanded.contains(key(groupKey));
    }

    /** Folds an open group, or opens a folded one. */
    void toggle(String groupKey) {
        String key = key(groupKey);
        if (!expanded.remove(key)) {
            expanded.add(key);
        }
    }

    void expand(String groupKey) {
        expanded.add(key(groupKey));
    }

    void collapseAll() {
        expanded.clear();
    }

    int expandedCount() {
        return expanded.size();
    }

    /**
     * Whether a row is drawn right now. A group's header and subtotal always are, and so are its
     * sells — folding hides the buys that sit behind them, not the result itself.
     */
    boolean isVisible(HistoryTablePresenter.HistoryRow row) {
        if (row == null) {
            return false;
        }
        HistoryTablePresenter.HistoryRowStyle style = row.style();
        if (style == HistoryTablePresenter.HistoryRowStyle.GROUP_HEADER
                || style == HistoryTablePresenter.HistoryRowStyle.SUBTOTAL
                || HistoryTablePresenter.isSellStyle(style)) {
            return true;
        }
        return isExpanded(row.groupKey());
    }

    /** The marker the header carries, so a folded group reads as folded rather than as empty. */
    static String marker(boolean expanded) {
        return expanded ? "▾ " : "▸ ";
    }

    private static String key(String groupKey) {
        return groupKey == null ? "" : groupKey.trim().toLowerCase(Locale.ROOT);
    }
}
