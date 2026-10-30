package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CancelBuyOrdersSelectionTest {
    private static final Map<CancelBuyOrdersSelection.Kind, Integer> COUNTS = Map.of(
            CancelBuyOrdersSelection.Kind.ENTRY, 3,
            CancelBuyOrdersSelection.Kind.LOSS_LEVEL, 0,
            CancelBuyOrdersSelection.Kind.RED_PENDING, 2);

    @Test
    void onlyKindsWithRowsAreAvailable() {
        assertEquals(EnumSet.of(CancelBuyOrdersSelection.Kind.ENTRY, CancelBuyOrdersSelection.Kind.RED_PENDING),
                CancelBuyOrdersSelection.available(COUNTS));
        assertTrue(CancelBuyOrdersSelection.available(Map.of()).isEmpty());
        assertTrue(CancelBuyOrdersSelection.available(null).isEmpty());
    }

    @Test
    void staleTicksOnEmptyKindsAreDropped() {
        Set<CancelBuyOrdersSelection.Kind> effective = CancelBuyOrdersSelection.effective(
                EnumSet.of(CancelBuyOrdersSelection.Kind.LOSS_LEVEL, CancelBuyOrdersSelection.Kind.ENTRY), COUNTS);

        assertEquals(EnumSet.of(CancelBuyOrdersSelection.Kind.ENTRY), effective);
        assertTrue(CancelBuyOrdersSelection.effective(null, COUNTS).isEmpty());
    }

    @Test
    void totalAddsOnlyTheChosenKinds() {
        assertEquals(5, CancelBuyOrdersSelection.total(
                EnumSet.of(CancelBuyOrdersSelection.Kind.ENTRY, CancelBuyOrdersSelection.Kind.RED_PENDING), COUNTS));
    }

    @Test
    void onlyEntryAndLossLevelBuysTouchTheBroker() {
        assertTrue(CancelBuyOrdersSelection.Kind.ENTRY.touchesBroker());
        assertTrue(CancelBuyOrdersSelection.Kind.LOSS_LEVEL.touchesBroker());
        assertFalse(CancelBuyOrdersSelection.Kind.RED_PENDING.touchesBroker());
        assertFalse(CancelBuyOrdersSelection.Kind.GREEN_PENDING.touchesBroker());
    }
}
