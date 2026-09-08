package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortfolioActionsMenuTest {
    @Test
    void itemTextPutsTheDescriptionUnderTheLabel() {
        String html = PortfolioActionsMenu.itemHtml(
                "Remove All Closed Positions",
                "Archive finished trades; history keeps every fill.");

        assertTrue(html.startsWith("<html><div>Remove All Closed Positions</div>"));
        assertTrue(html.contains("Archive finished trades; history keeps every fill."));
        assertTrue(html.contains("color:#8e97a8"), "the description uses the muted secondary style");
    }

    @Test
    void itemTextWithoutDescriptionRendersOneLine() {
        String html = PortfolioActionsMenu.itemHtml("Resume All", null);

        assertFalse(html.contains("color:#8e97a8"));
        assertTrue(html.contains("Resume All"));
    }

    @Test
    void labelsAndDescriptionsAreEscapedSoMarkupCannotBreakTheMenu() {
        String html = PortfolioActionsMenu.itemHtml("Buys < Sells", "Uses <b>bold</b> & more");

        assertTrue(html.contains("Buys &lt; Sells"));
        assertTrue(html.contains("Uses &lt;b&gt;bold&lt;/b&gt; &amp; more"));
    }

    @Test
    void disabledEntryKeepsItsLabelDescriptionAndTooltip() {
        PortfolioActionsMenu.Entry entry = new PortfolioActionsMenu.Entry(
                "Promote All to Live", "Recreate paper strategies in live mode.", "icons/x.svg", () -> { });

        PortfolioActionsMenu.Entry disabled = entry.disabledWith("Unavailable while viewing LIVE mode.");

        assertFalse(disabled.enabled());
        assertTrue(entry.enabled(), "disabling returns a copy and leaves the original alone");
        assertTrue(disabled.description().equals(entry.description()));
        assertTrue(disabled.disabledTooltip().contains("LIVE"));
    }
}
