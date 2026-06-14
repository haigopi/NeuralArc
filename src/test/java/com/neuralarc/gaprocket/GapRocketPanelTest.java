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
        panel.analyzeButton().doClick();
        assertTrue(opened.get());
    }
}
