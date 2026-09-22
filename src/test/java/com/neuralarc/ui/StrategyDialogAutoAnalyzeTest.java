package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import static com.neuralarc.ui.AverageDownTestEntries.position;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class StrategyDialogAutoAnalyzeTest {
    @Test
    void theGridActionOpensOnAutoAnalyzeWithThePositionsSymbol() {
        StrategyDialog dialog = new StrategyDialog(null, position("NVDA", 10, "0", "180").toEditConfig());

        boolean runs = dialog.prepareAutoAnalyze();

        assertEquals(1, dialog.selectedTabIndex());
        assertEquals("NVDA", dialog.autoAnalyzeSymbol());
        assertFalse(runs, "without an Alpaca connection there is nothing to run; the tab says to connect");
        dialog.dispose();
    }
}
