package com.neuralarc.ui;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The kinds of buy the consolidated Cancel Buy Orders dialog can withdraw, and the pure rules for
 * turning an operator's ticks into the set of rows to act on.
 *
 * <p>The four kinds never overlap on one row: an entry buy has no shares yet, a loss-level buy sits
 * under a held position, and red/green recommendations were never sent to the broker.
 */
final class CancelBuyOrdersSelection {
    enum Kind {
        ENTRY("Entry limit buys with no shares yet",
                "First limit buy still waiting; those strategies pause.",
                PortfolioActionsSupport.BulkAction.CANCEL_UNFILLED_ENTRY_BUYS),
        LOSS_LEVEL("Loss-level (averaging-down) buys",
                "Extra buys below held positions; the shares and their stops stay.",
                PortfolioActionsSupport.BulkAction.CANCEL_LOSS_LEVEL_BUYS),
        RED_PENDING("Red pending recommendations",
                "Never sent to the broker; limit is above the current price.",
                PortfolioActionsSupport.BulkAction.CANCEL_AMBER_PENDING_BUYS),
        GREEN_PENDING("Green pending recommendations",
                "Never sent to the broker; limit is below the current price.",
                PortfolioActionsSupport.BulkAction.CANCEL_GREEN_PENDING_BUYS);

        private final String label;
        private final String description;
        private final PortfolioActionsSupport.BulkAction matcher;

        Kind(String label, String description, PortfolioActionsSupport.BulkAction matcher) {
            this.label = label;
            this.description = description;
            this.matcher = matcher;
        }

        String label() {
            return label;
        }

        String description() {
            return description;
        }

        PortfolioActionsSupport.BulkAction matcher() {
            return matcher;
        }

        /** Entry and loss-level buys are broker orders; the coloured kinds are local rows only. */
        boolean touchesBroker() {
            return this == ENTRY || this == LOSS_LEVEL;
        }
    }

    private CancelBuyOrdersSelection() {
    }

    /** Kinds that have at least one row, in menu order. */
    static Set<Kind> available(Map<Kind, Integer> counts) {
        Set<Kind> available = EnumSet.noneOf(Kind.class);
        for (Kind kind : Kind.values()) {
            if (counts != null && counts.getOrDefault(kind, 0) > 0) {
                available.add(kind);
            }
        }
        return available;
    }

    /** Drops ticks for kinds with nothing to cancel, so a stale tick cannot select an empty set. */
    static Set<Kind> effective(Set<Kind> ticked, Map<Kind, Integer> counts) {
        Set<Kind> result = EnumSet.noneOf(Kind.class);
        if (ticked != null) {
            result.addAll(ticked);
        }
        result.retainAll(available(counts));
        return result;
    }

    static int total(Set<Kind> kinds, Map<Kind, Integer> counts) {
        int total = 0;
        for (Kind kind : kinds) {
            total += counts.getOrDefault(kind, 0);
        }
        return total;
    }

    static Map<Kind, Integer> emptyCounts() {
        return new EnumMap<>(Kind.class);
    }
}
