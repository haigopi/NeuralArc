package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UntrackedSharesTest {
    @Test
    void sharesTheOrdersCannotExplainAreCounted() {
        // The EDRY case: one base buy of 10 in the timeline, 20 shares at the broker.
        assertEquals(10, UntrackedShares.count(20, 10));
    }

    @Test
    void aFullyExplainedPositionHasNoneAndNeitherDoesAnOverClaim() {
        assertEquals(0, UntrackedShares.count(10, 10));
        assertEquals(0, UntrackedShares.count(10, 25), "a row claiming more than the broker holds is a"
                + " different problem; it must not read as untracked shares");
        assertEquals(0, UntrackedShares.count(0, 0));
    }

    @Test
    void shortsAreNotCounted() {
        assertEquals(0, UntrackedShares.count(-40, 0), "a short is handed whole to one row, so the"
                + " difference says nothing about missing orders");
    }

    @Test
    void theStatusNoteOnlyAppearsWhenThereIsSomethingToSay() {
        assertEquals("", UntrackedShares.note(0));
        assertEquals(" | 10 shares untracked", UntrackedShares.note(10));
        assertEquals(" | 1 share untracked", UntrackedShares.note(1));
    }

    @Test
    void theTooltipSaysWhatIsAccountedForAndWhatToDo() {
        String tooltip = UntrackedShares.tooltip("EDRY", 20, 10);

        assertTrue(tooltip.contains("holds 20 EDRY"));
        assertTrue(tooltip.contains("account for 10"));
        assertTrue(tooltip.contains("order history"), "the operator needs the next step, not just the number");
        assertEquals("", UntrackedShares.tooltip("EDRY", 10, 0));
    }
}
