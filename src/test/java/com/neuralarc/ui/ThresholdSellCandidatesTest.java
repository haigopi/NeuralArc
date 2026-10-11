package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.neuralarc.ui.AverageDownTestEntries.position;
import static com.neuralarc.ui.AverageDownTestEntries.withWorkingSell;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThresholdSellCandidatesTest {
    /** BIG +40%, MID +10%, SMALL +2%, ten shares each. */
    static List<ThresholdSellCandidates.Candidate> sample() {
        return ThresholdSellCandidates.collect(List.of(
                position("SMALL", 10, "10", "10.20"),
                position("BIG", 10, "10", "14"),
                position("MID", 10, "10", "11")), id -> "");
    }

    @Test
    void listsTheBiggestWinnerFirstAndTheDeepestLoserLast() {
        List<ThresholdSellCandidates.Candidate> candidates = ThresholdSellCandidates.collect(List.of(
                position("DOWN", 10, "10", "5"),
                position("SMALL", 10, "10", "10.20"),
                position("BIG", 10, "10", "14")), id -> "");

        assertEquals(List.of("BIG", "SMALL", "DOWN"),
                candidates.stream().map(ThresholdSellCandidates.Candidate::symbol).toList());
    }

    @Test
    void leavesOutPositionsThatCannotBeSoldNow() {
        List<ManagedStrategy> scope = List.of(
                position("OK", 10, "10", "12"),
                withWorkingSell(position("BUSY", 10, "10", "12")),
                position("EMPTY", 0, "0", "12"));

        assertEquals(List.of("OK"),
                ThresholdSellCandidates.collect(scope, id -> "").stream().map(ThresholdSellCandidates.Candidate::symbol).toList());
        assertTrue(ThresholdSellCandidates.isSellable(scope.get(0)));
        assertFalse(ThresholdSellCandidates.isSellable(scope.get(1)));
        assertFalse(ThresholdSellCandidates.isSellable(scope.get(2)));
        assertFalse(ThresholdSellCandidates.isSellable(null));
    }

    @Test
    void computesPnlPercentAndProceedsFromTheCachedPosition() {
        ThresholdSellCandidates.Candidate big = sample().getFirst();

        assertEquals("BIG", big.symbol());
        assertEquals(0, new java.math.BigDecimal("40.00").compareTo(big.unrealizedPnl()));
        assertEquals(0, new java.math.BigDecimal("40.00").compareTo(big.pnlPercent()));
        assertEquals(0, new java.math.BigDecimal("140.00").compareTo(big.estimatedProceeds()));
    }

    @Test
    void anEmptyScopeYieldsNoCandidates() {
        assertTrue(ThresholdSellCandidates.collect(null, null).isEmpty());
    }
}
