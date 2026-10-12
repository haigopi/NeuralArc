package com.neuralarc.ui;

import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyOrderSide;
import com.neuralarc.model.StrategyStage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Builds the content only, so no window is shown. */
class BrokerFillReconcileDialogTest {
    private static BrokerFillReconciler.Proposal fix(String symbol) {
        return new BrokerFillReconciler.Proposal(BrokerFillReconciler.Kind.LINK, symbol + "-id", symbol, StrategyMode.LIVE,
                StrategyOrderSide.BUY, new BigDecimal("2"), new BigDecimal("10"), Instant.now(), "b-" + symbol, "", "filled",
                StrategyStage.MANUAL_BUY, null, new BigDecimal("2"), "No local record.");
    }

    @Test
    void theButtonStatesHowManyFixesGoAheadAndNeedsAtLeastOne() {
        BrokerFillReconcileDialog dialog = new BrokerFillReconcileDialog(List.of(fix("RDW"), fix("ASTS")), "All Stocks · Live");

        assertEquals("Apply 2 Fixes", dialog.submitText());
        assertTrue(dialog.submitEnabled());

        dialog.model().clearSelection();
        assertFalse(dialog.submitEnabled());
        assertEquals("Apply", dialog.submitText());
    }

    @Test
    void oneFixIsSingular() {
        BrokerFillReconcileDialog dialog = new BrokerFillReconcileDialog(List.of(fix("RDW")), null);

        assertEquals("Apply 1 Fix", dialog.submitText());
    }
}
