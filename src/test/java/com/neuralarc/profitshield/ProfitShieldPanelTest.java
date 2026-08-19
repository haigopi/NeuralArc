package com.neuralarc.profitshield;

import com.neuralarc.model.StrategyMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfitShieldPanelTest {
    @Test
    void emptyStateShowsAnalyzeButtonAndFirstClickInvokesDialogLauncher() {
        AtomicBoolean opened = new AtomicBoolean(false);
        ProfitShieldPanel panel = new ProfitShieldPanel(() -> opened.set(true), true);

        assertEquals(ProfitShieldPanel.ANALYZE_BUTTON_TEXT, panel.analyzeButton().getText());
        panel.analyzeButton().doClick();
        assertTrue(opened.get());
    }

    @Test
    void emptyStateExplainsEveryDefensiveControlWithoutNamingTickers() {
        String text = ProfitShieldPanel.EMPTY_STATE_TEXT;

        assertTrue(text.contains("Maximum Daily Volatility"));
        assertTrue(text.contains("Maximum Drawdown"));
        assertTrue(text.contains("Maximum Distance Below High"));
        assertTrue(text.contains("Protective Stop"));
        assertTrue(text.contains("live Alpaca daily bars"));
        // Market-data discipline: the guidance must never advertise hardcoded candidates.
        assertFalse(text.contains("NVDA"));
        assertFalse(text.contains("AAPL"));
    }

    @Test
    void dialogRoundTripsAConfigThroughItsFields() {
        ProfitShieldConfig original = new ProfitShieldConfig(90, new BigDecimal("2.5"), new BigDecimal("15"),
                new BigDecimal("8"), 750_000L, new BigDecimal("20"), new BigDecimal("500"),
                ProfitShieldConfig.TrendFilter.ABOVE_MA_50, new BigDecimal("0.5"), new BigDecimal("2"),
                new BigDecimal("4"), 6, StrategyMode.PAPER, java.util.List.of("MSFT", "KO"));

        ProfitShieldAnalysisDialog dialog = new ProfitShieldAnalysisDialog(null, StrategyMode.PAPER, original);
        ProfitShieldConfig readBack = dialog.config();

        assertEquals(original, readBack);
        assertFalse(dialog.accepted(), "a freshly built dialog has not been accepted yet");
        assertEquals(ProfitShieldAnalysisDialog.RunMode.ANALYZE, dialog.runMode());
        dialog.dispose();
    }

    @Test
    void dialogFallsBackToDefaultsWhenNoPriorConfigExists() {
        ProfitShieldAnalysisDialog dialog = new ProfitShieldAnalysisDialog(null, StrategyMode.LIVE, null);

        assertEquals(ProfitShieldConfig.defaults(StrategyMode.LIVE), dialog.config());
        dialog.dispose();
    }
}
