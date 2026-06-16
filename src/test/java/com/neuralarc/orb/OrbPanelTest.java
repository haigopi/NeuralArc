package com.neuralarc.orb;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class OrbPanelTest {
    @Test
    void emptyStateShowsAnalyzeButtonAndLiveDataGuidance() {
        AtomicBoolean opened = new AtomicBoolean(false);
        OrbPanel panel = new OrbPanel(() -> opened.set(true));
        assertEquals(OrbPanel.ANALYZE_BUTTON_TEXT, panel.analyzeButton().getText());
        assertTrue(OrbPanel.EMPTY_STATE_TEXT.contains("live Alpaca data"));
        assertTrue(OrbPanel.EMPTY_STATE_TEXT.contains("AI news analysis"));
        assertFalse(OrbPanel.EMPTY_STATE_TEXT.contains("NVDA"));
        panel.analyzeButton().doClick();
        assertTrue(opened.get());
    }
}
