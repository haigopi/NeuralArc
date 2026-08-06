package com.neuralarc.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdaptivePollingPacerTest {
    private final AdaptivePollingPacer pacer = new AdaptivePollingPacer();
    private static final BigDecimal PRICE = new BigDecimal("10.00");
    private static final BigDecimal QTY = new BigDecimal("5");
    private static final BigDecimal AVG_ENTRY = new BigDecimal("9.50");

    @Test
    void unknownStrategyDefaultsToFullSpeed() {
        assertEquals(1L, pacer.pacingMultiplier("unknown", 4L));
    }

    @Test
    void staysAtFullSpeedBelowThreeUnchangedCycles() {
        pacer.recordObservation("s1", PRICE, QTY, AVG_ENTRY);
        pacer.recordObservation("s1", PRICE, QTY, AVG_ENTRY);
        assertEquals(1L, pacer.pacingMultiplier("s1", 4L));
    }

    @Test
    void relaxesToTwoXAfterThreeUnchangedCycles() {
        // The first observation establishes the baseline (unchangedCycles=0); each identical
        // follow-up increments it, so 4 total observations reach unchangedCycles=3.
        for (int i = 0; i < 4; i++) {
            pacer.recordObservation("s1", PRICE, QTY, AVG_ENTRY);
        }
        assertEquals(2L, pacer.pacingMultiplier("s1", 4L));
    }

    @Test
    void relaxesToConfiguredMaxAfterTenUnchangedCycles() {
        for (int i = 0; i < 11; i++) {
            pacer.recordObservation("s1", PRICE, QTY, AVG_ENTRY);
        }
        assertEquals(4L, pacer.pacingMultiplier("s1", 4L));
        assertEquals(7L, pacer.pacingMultiplier("s1", 7L));
    }

    @Test
    void anyChangeResetsToFullSpeedImmediately() {
        for (int i = 0; i < 11; i++) {
            pacer.recordObservation("s1", PRICE, QTY, AVG_ENTRY);
        }
        assertEquals(4L, pacer.pacingMultiplier("s1", 4L));

        pacer.recordObservation("s1", new BigDecimal("10.01"), QTY, AVG_ENTRY);

        assertEquals(1L, pacer.pacingMultiplier("s1", 4L));
    }

    @Test
    void quantityOrAvgEntryChangeAlsoResetsToFullSpeed() {
        for (int i = 0; i < 5; i++) {
            pacer.recordObservation("s1", PRICE, QTY, AVG_ENTRY);
        }
        pacer.recordObservation("s1", PRICE, new BigDecimal("6"), AVG_ENTRY);
        assertEquals(1L, pacer.pacingMultiplier("s1", 4L));
    }

    @Test
    void resetClearsObservationHistory() {
        for (int i = 0; i < 10; i++) {
            pacer.recordObservation("s1", PRICE, QTY, AVG_ENTRY);
        }
        pacer.reset("s1");
        assertEquals(1L, pacer.pacingMultiplier("s1", 4L));
    }

    @Test
    void maxMultiplierIsClampedToAtLeastOne() {
        for (int i = 0; i < 10; i++) {
            pacer.recordObservation("s1", PRICE, QTY, AVG_ENTRY);
        }
        assertEquals(1L, pacer.pacingMultiplier("s1", 0L));
    }
}
