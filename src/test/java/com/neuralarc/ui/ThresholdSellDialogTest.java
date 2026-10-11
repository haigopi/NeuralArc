package com.neuralarc.ui;

import com.neuralarc.model.SellSubmissionType;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Behaviour of the dialog's controls; builds the content only, so no window is shown. */
class ThresholdSellDialogTest {
    @Test
    void opensWithTheDefaultRuleAndEveryMatchTicked() {
        ThresholdSellDialog dialog = dialog();

        assertEquals(2, dialog.model().getRowCount(), "profit more than 5% matches BIG and MID; SMALL is only +2%");
        assertEquals(Set.of("BIG-id", "MID-id"), dialog.model().selectedIds());
    }

    @Test
    void primaryButtonStatesHowManySellsGoOutAndNeedsAtLeastOne() {
        ThresholdSellDialog dialog = dialog();
        int matched = dialog.model().getRowCount();

        assertEquals("Sell " + matched + " Positions", dialog.submitText());
        assertTrue(dialog.submitEnabled());

        dialog.model().clearSelection();
        assertFalse(dialog.submitEnabled());
        assertEquals("Sell", dialog.submitText());
    }

    @Test
    void editingTheRuleRefiltersTheGridAndTheSummary() {
        ThresholdSellDialog dialog = dialog();

        dialog.chooseRule("profit", "less than", 5, "%");

        assertEquals(1, dialog.model().getRowCount());
        assertEquals("SMALL", dialog.model().candidateAt(0).symbol());
        assertEquals("Sell 1 Position", dialog.submitText());
        assertTrue(dialog.ruleSummaryText().contains("1 of 3 open positions have profit less than 5%"),
                dialog.ruleSummaryText());
    }

    @Test
    void aRuleThatMatchesNothingDisablesTheSellButton() {
        ThresholdSellDialog dialog = dialog();

        dialog.chooseRule("loss", "more than", 5, "%");

        assertEquals(0, dialog.model().getRowCount());
        assertFalse(dialog.submitEnabled());
    }

    @Test
    void returnsTheRuleTheSellTypeAndOnlyTheTickedPositions() {
        ThresholdSellDialog dialog = dialog();
        dialog.chooseRule("profit", "more than", 5, "%");
        dialog.model().setValueAt(false, 0, ThresholdSellTableModel.INCLUDE);
        dialog.chooseMarketOrder();

        ThresholdSellSelection selection = dialog.selection();

        assertEquals(SellSubmissionType.MARKET, selection.submissionType());
        assertEquals(Set.of("MID-id"), selection.strategyIds());
        assertEquals("profit more than 5%", selection.rule().describe());
    }

    @Test
    void sellsLimitByDefault() {
        assertEquals(SellSubmissionType.LIMIT, dialog().selection().submissionType());
    }

    private static ThresholdSellDialog dialog() {
        return new ThresholdSellDialog(ThresholdSellCandidatesTest.sample(), "All Stocks · Paper");
    }
}
