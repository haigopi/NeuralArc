package com.neuralarc.analytics;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtectiveStopPriceTest {
    @Test
    void aStopWithRoomToBreatheIsLeftAlone() {
        assertEquals(new BigDecimal("95.00"),
                ProtectiveStopPrice.percentBelowEntry(new BigDecimal("100.00"), new BigDecimal("5")));
    }

    @Test
    void aStopThatCrowdsTheEntryIsPushedToTheMinimumGap() {
        // 0.05% under $100 is five cents — half the room a stop needs.
        assertEquals(new BigDecimal("99.90"),
                ProtectiveStopPrice.percentBelowEntry(new BigDecimal("100.00"), new BigDecimal("0.05")));
    }

    @Test
    void aPercentageStopThatUsedToRoundOntoTheEntryNowClearsIt() {
        // 0.5% under $1.00 is $0.995, which rounds half-up to the entry itself and is then rejected
        // for not being below the base buy.
        BigDecimal stop = ProtectiveStopPrice.percentBelowEntry(new BigDecimal("1.00"), new BigDecimal("0.5"));

        assertEquals(new BigDecimal("0.90"), stop);
        assertTrue(stop.compareTo(new BigDecimal("1.00")) < 0, "the stop must sit under the entry");
    }

    @Test
    void roundingWidensTheGapAndNeverNarrowsIt() {
        // 49.899 must not round up to 49.90, which would leave only ten cents minus a hair.
        assertEquals(new BigDecimal("49.89"),
                ProtectiveStopPrice.belowEntry(new BigDecimal("50.00"), new BigDecimal("49.899")));
    }

    @Test
    void aMissingOrZeroPercentageStillPlansAStopBelowTheEntry() {
        assertEquals(new BigDecimal("24.90"),
                ProtectiveStopPrice.percentBelowEntry(new BigDecimal("25.00"), BigDecimal.ZERO));
        assertEquals(new BigDecimal("24.90"),
                ProtectiveStopPrice.percentBelowEntry(new BigDecimal("25.00"), null));
        assertEquals(new BigDecimal("24.90"),
                ProtectiveStopPrice.belowEntry(new BigDecimal("25.00"), null));
    }

    @Test
    void anEntryTooSmallForATenCentGapKeepsTheStopStrictlyBelowIt() {
        BigDecimal stop = ProtectiveStopPrice.percentBelowEntry(new BigDecimal("0.05"), new BigDecimal("1"));

        assertEquals(new BigDecimal("0.04"), stop);
        assertTrue(stop.compareTo(new BigDecimal("0.05")) < 0);
    }

    @Test
    void withoutAnEntryThereIsNoStopToPlan() {
        assertEquals(new BigDecimal("0.00"), ProtectiveStopPrice.belowEntry(BigDecimal.ZERO, new BigDecimal("5")));
        assertEquals(new BigDecimal("0.00"), ProtectiveStopPrice.percentBelowEntry(null, new BigDecimal("5")));
    }
}
