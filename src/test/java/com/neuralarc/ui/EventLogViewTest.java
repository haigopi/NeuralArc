package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import javax.swing.JTextPane;
import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventLogViewTest {
    private final JTextPane pane = new JTextPane();
    private final EventLogView view = new EventLogView(pane, 100, 500, (line, index) -> Color.WHITE, line -> false, Color.RED);

    @Test
    void enteringAFilterShowsOnlyMatchingLinesIgnoringCase() {
        view.append("[10:00] [Portfolio Refresh] Refreshed 83 positions\n");
        view.append("[10:01] [RULES][AAPL] analyzed\n");
        view.append("[10:02] [POSITION] NVDA shares=10\n");

        view.setFilter("position");

        assertEquals(2, view.shownLineCount());
        assertFalse(pane.getText().contains("RULES"));
    }

    @Test
    void newLinesAppearWhileTheyMatchTheFilter() {
        view.setFilter("Position");
        view.append("[10:03] [RULES][AAPL] analyzed\n");
        view.append("[10:04] [POSITION] MSFT closed\n");

        assertEquals(1, view.shownLineCount());
        view.setFilter("");
        assertEquals(2, view.shownLineCount(), "clearing the filter brings every kept line back");
    }

    @Test
    void clearingEmptiesTheOnScreenLog() {
        view.append("[10:00] one\n");
        view.append("[10:01] two\n");

        view.clear();

        assertEquals(0, view.shownLineCount());
        assertTrue(pane.getText().isEmpty());
        view.setFilter("");
        assertEquals(0, view.shownLineCount(), "cleared lines do not come back");
    }

    @Test
    void theFilterReachesLinesNoLongerOnScreen() {
        EventLogView small = new EventLogView(new JTextPane(), 2, 50, (line, index) -> Color.WHITE, line -> false, Color.RED);
        small.append("[1] POSITION early\n");
        small.append("[2] other\n");
        small.append("[3] other\n");
        small.append("[4] other\n");

        small.setFilter("POSITION");

        assertEquals(1, small.shownLineCount());
    }
}
