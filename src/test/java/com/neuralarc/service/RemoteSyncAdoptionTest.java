package com.neuralarc.service;

import com.neuralarc.api.AlpacaPositionData;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteSyncAdoptionTest {
    private static final Set<String> SUPPRESSED = Set.of("BOXL");

    @Test
    void aDeletedSymbolWithNothingHeldStaysDeleted() {
        assertTrue(RemoteSyncAdoption.skipSuppressed("BOXL", SUPPRESSED, null));
        assertTrue(RemoteSyncAdoption.skipSuppressed("BOXL", SUPPRESSED, flat("BOXL")));
    }

    @Test
    void sharesActuallyHeldOutweighTheSuppression() {
        // Leaving these untracked hides stock that is really in the account.
        assertFalse(RemoteSyncAdoption.skipSuppressed("BOXL", SUPPRESSED, held("BOXL", "1")));
    }

    @Test
    void aSymbolThatWasNeverSuppressedIsNeverSkipped() {
        assertFalse(RemoteSyncAdoption.skipSuppressed("RKLB", SUPPRESSED, null));
        assertFalse(RemoteSyncAdoption.skipSuppressed("RKLB", SUPPRESSED, held("RKLB", "2")));
    }

    @Test
    void missingInputsNeverSkip() {
        assertFalse(RemoteSyncAdoption.skipSuppressed(null, SUPPRESSED, null));
        assertFalse(RemoteSyncAdoption.skipSuppressed("BOXL", null, null));
        assertFalse(RemoteSyncAdoption.skipSuppressed("BOXL", Set.of(), null));
    }

    private static AlpacaPositionData held(String symbol, String quantity) {
        return new AlpacaPositionData(symbol, new BigDecimal(quantity), new BigDecimal("6.74"),
                new BigDecimal("4.21"), "{}");
    }

    private static AlpacaPositionData flat(String symbol) {
        return new AlpacaPositionData(symbol, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "{}");
    }
}
