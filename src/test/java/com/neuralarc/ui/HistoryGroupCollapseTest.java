package com.neuralarc.ui;

import com.neuralarc.ui.HistoryTablePresenter.HistoryRow;
import com.neuralarc.ui.HistoryTablePresenter.HistoryRowStyle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryGroupCollapseTest {
    private final HistoryGroupCollapse collapse = new HistoryGroupCollapse();

    @Test
    void groupsStartFoldedShowingWhatTheTradesMade() {
        assertFalse(collapse.isExpanded("NVDA"));
        assertTrue(collapse.isVisible(row("NVDA", HistoryRowStyle.SELL_GAIN)), "a sell is the result, always shown");
        assertTrue(collapse.isVisible(row("NVDA", HistoryRowStyle.SELL_LOSS)));
        assertTrue(collapse.isVisible(row("NVDA", HistoryRowStyle.GROUP_HEADER)), "the line that opens it");
        assertTrue(collapse.isVisible(row("NVDA", HistoryRowStyle.SUBTOTAL)));
        assertFalse(collapse.isVisible(row("NVDA", HistoryRowStyle.BUY)), "the buys are what folding hides");
    }

    @Test
    void openingAGroupRevealsItsBuys() {
        collapse.toggle("NVDA");

        assertTrue(collapse.isExpanded("NVDA"));
        assertTrue(collapse.isVisible(row("NVDA", HistoryRowStyle.BUY)));
        assertFalse(collapse.isVisible(row("AAPL", HistoryRowStyle.BUY)), "only the group that was clicked opens");
    }

    @Test
    void clickingAgainFoldsItBack() {
        collapse.toggle("NVDA");
        collapse.toggle("NVDA");

        assertFalse(collapse.isExpanded("NVDA"));
        assertFalse(collapse.isVisible(row("NVDA", HistoryRowStyle.BUY)));
    }

    @Test
    void theGroupKeyIsMatchedTheWayTheTableWritesIt() {
        collapse.expand("Sep 22 2026");

        assertTrue(collapse.isExpanded("  sep 22 2026  "), "case and padding must not open a different group");
    }

    @Test
    void collapseAllFoldsEverythingBackDown() {
        collapse.expand("NVDA");
        collapse.expand("AAPL");

        collapse.collapseAll();

        assertEquals(0, collapse.expandedCount());
    }

    @Test
    void theMarkerSaysWhichWayTheGroupIsFolded() {
        assertEquals("▸ ", HistoryGroupCollapse.marker(false));
        assertEquals("▾ ", HistoryGroupCollapse.marker(true));
    }

    @Test
    void otherRowKindsAreLeftVisibleRatherThanDisappearing() {
        assertFalse(collapse.isVisible(null));
        assertFalse(collapse.isVisible(row("NVDA", HistoryRowStyle.FAILED)), "detail rows fold with the buys");
        collapse.expand("NVDA");
        assertTrue(collapse.isVisible(row("NVDA", HistoryRowStyle.FAILED)));
    }

    private static HistoryRow row(String groupKey, HistoryRowStyle style) {
        return new HistoryRow(groupKey, groupKey, "", "", "", "", "", "", "", "", "", "", null, 1, style);
    }
}
