package com.neuralarc.ui;

import com.neuralarc.model.TimeInForce;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.neuralarc.ui.AverageDownTestEntries.position;
import static com.neuralarc.ui.AverageDownTestEntries.withWorkingSell;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Behaviour of the dialog's controls; builds the content only, so no window is shown. */
class AverageLosingPositionsDialogTest {
    @Test
    void fixedShareCountIsEditableOnlyForControlledAdd() {
        AverageLosingPositionsDialog dialog = dialog();

        assertFalse(dialog.fixedQuantityEditable(), "double-down is the default, so the fixed count does not apply");
        dialog.chooseFixedQuantity(3);
        assertTrue(dialog.fixedQuantityEditable());
    }

    @Test
    void pullbackAndTimeInForceApplyOnlyToLimitBuys() {
        AverageLosingPositionsDialog dialog = dialog();

        assertTrue(dialog.limitControlsEditable());
        dialog.chooseMarketOrder();
        assertFalse(dialog.limitControlsEditable(), "market buys ignore pullback and time in force");
    }

    @Test
    void primaryButtonStatesHowManyOrdersGoOutAndNeedsAtLeastOne() {
        AverageLosingPositionsDialog dialog = dialog();

        assertEquals("Average Down 2 Positions", dialog.submitText());
        assertTrue(dialog.submitEnabled());

        dialog.model().clearSelection();
        assertFalse(dialog.submitEnabled());
    }

    @Test
    void returnsThePlanWithTheTickedPositions() {
        AverageLosingPositionsDialog dialog = dialog();
        dialog.model().setValueAt(false, 1, AverageDownTableModel.INCLUDE);
        dialog.chooseFixedQuantity(3);

        AverageLosingPositionsSelection selection = dialog.selection();

        assertEquals(Set.of(dialog.model().candidateAt(0).strategyId()), selection.strategyIds());
        assertEquals(AverageLosingPositionsSelection.QuantityMode.FIXED_INPUT_QUANTITY, selection.quantityMode());
        assertEquals(3, selection.quantity());
        assertEquals(TimeInForce.GTC, selection.timeInForce(), "the Settings default time in force is preselected");
    }

    private static AverageLosingPositionsDialog dialog() {
        return new AverageLosingPositionsDialog(AverageDownCandidates.collect(List.of(
                position("KLC", 4, "2.66", "2.58"),
                withWorkingSell(position("RKTO", 1, "0.85", "0.69")),
                position("CURI", 2, "3.53", "2.66")
        ), PortfolioActionsSupport.BulkAction.AVERAGE_LOSING_POSITIONS::matches, id -> ""),
                "ORB Engine · Live", TimeInForce.GTC);
    }
}
