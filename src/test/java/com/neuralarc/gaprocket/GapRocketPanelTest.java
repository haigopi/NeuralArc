package com.neuralarc.gaprocket;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class GapRocketPanelTest {
    @Test
    void emptyStateShowsAnalyzeButtonAndFirstClickInvokesDialogLauncher() {
        AtomicBoolean opened = new AtomicBoolean(false);
        GapRocketPanel panel = new GapRocketPanel(() -> opened.set(true));
        assertEquals(GapRocketPanel.ANALYZE_BUTTON_TEXT, panel.analyzeButton().getText());
        assertFalse(GapRocketPanel.EMPTY_STATE_TEXT.contains("Gap Rocket is ready"));
        assertFalse(GapRocketPanel.EMPTY_STATE_TEXT.contains("NVDA"));
        assertTrue(GapRocketPanel.EMPTY_STATE_TEXT.contains("Market Trend Filter"));
        assertTrue(GapRocketPanel.EMPTY_STATE_TEXT.contains("Entry Style"));
        assertTrue(GapRocketPanel.EMPTY_STATE_TEXT.contains("Opening Range Breakout"));
        assertTrue(GapRocketPanel.EMPTY_STATE_TEXT.contains("Breakout Retest"));
        assertTrue(GapRocketPanel.EMPTY_STATE_TEXT.contains("SPY Green"));
        assertTrue(GapRocketPanel.EMPTY_STATE_TEXT.contains("QQQ Green"));
        assertTrue(GapRocketPanel.EMPTY_STATE_TEXT.contains("5 minutes reacts fastest"));
        panel.analyzeButton().doClick();
        assertTrue(opened.get());
    }
}
