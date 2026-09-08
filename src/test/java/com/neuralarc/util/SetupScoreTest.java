package com.neuralarc.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SetupScoreTest {
    private static final BigDecimal IDEAL_LOW = new BigDecimal("4");
    private static final BigDecimal IDEAL_HIGH = new BigDecimal("10");
    private static final BigDecimal MIN = new BigDecimal("3");
    private static final BigDecimal MAX = new BigDecimal("15");

    @Test
    void anythingInsideTheIdealBandScoresFullMarks() {
        for (String value : new String[]{"4", "6", "8", "10"}) {
            assertEquals(30, SetupScore.bandQuality(new BigDecimal(value), IDEAL_LOW, IDEAL_HIGH, MIN, MAX, 30),
                    value + "% is a textbook setup");
        }
    }

    @Test
    void theIdealBandDoesNotMoveWhenTheOperatorWidensTheFilter() {
        // The old scoring keyed off the midpoint of min/max, so widening the filter to 0.1-50% moved
        // "perfect" to a 25% collapse and left a 6% pullback scoring a fifth of the points.
        int narrow = SetupScore.bandQuality(new BigDecimal("6"), IDEAL_LOW, IDEAL_HIGH, MIN, MAX, 30);
        int wide = SetupScore.bandQuality(new BigDecimal("6"), IDEAL_LOW, IDEAL_HIGH,
                new BigDecimal("0.1"), new BigDecimal("50"), 30);

        assertEquals(30, narrow);
        assertEquals(30, wide);
    }

    @Test
    void scoreTapersTowardTheOuterBounds() {
        int atBand = SetupScore.bandQuality(new BigDecimal("4"), IDEAL_LOW, IDEAL_HIGH, MIN, MAX, 30);
        int belowBand = SetupScore.bandQuality(new BigDecimal("3.5"), IDEAL_LOW, IDEAL_HIGH, MIN, MAX, 30);
        int aboveBand = SetupScore.bandQuality(new BigDecimal("13"), IDEAL_LOW, IDEAL_HIGH, MIN, MAX, 30);

        assertEquals(30, atBand);
        assertTrue(belowBand > 0 && belowBand < 30);
        assertTrue(aboveBand > 0 && aboveBand < 30);
        assertTrue(aboveBand < belowBand, "13% is further from the band than 3.5%");
    }

    @Test
    void outsideTheOperatorBoundsScoresNothing() {
        assertEquals(0, SetupScore.bandQuality(new BigDecimal("2"), IDEAL_LOW, IDEAL_HIGH, MIN, MAX, 30));
        assertEquals(0, SetupScore.bandQuality(new BigDecimal("20"), IDEAL_LOW, IDEAL_HIGH, MIN, MAX, 30));
        assertEquals(0, SetupScore.bandQuality(null, IDEAL_LOW, IDEAL_HIGH, MIN, MAX, 30));
    }

    @Test
    void anIdealBandOutsideTheOperatorBoundsIsClampedIntoThem() {
        // Operator wants 0.5-2% only; the setup's 4-10% ideal is clamped so the band still means
        // "as good as this filter allows" instead of scoring everything zero.
        int best = SetupScore.bandQuality(new BigDecimal("2"), IDEAL_LOW, IDEAL_HIGH,
                new BigDecimal("0.5"), new BigDecimal("2"), 30);

        assertEquals(30, best);
    }

    @Test
    void proximityScoresTheSameEitherSideOfSupport() {
        int justAbove = SetupScore.proximityQuality(new BigDecimal("2"), new BigDecimal("3"), new BigDecimal("15"), 20);
        int justBelow = SetupScore.proximityQuality(new BigDecimal("-2"), new BigDecimal("3"), new BigDecimal("15"), 20);

        assertEquals(20, justAbove);
        assertEquals(20, justBelow);
    }

    @Test
    void proximityFadesWithDistanceAndIsZeroBeyondTheLimit() {
        int near = SetupScore.proximityQuality(new BigDecimal("5"), new BigDecimal("3"), new BigDecimal("15"), 20);
        int far = SetupScore.proximityQuality(new BigDecimal("12"), new BigDecimal("3"), new BigDecimal("15"), 20);

        assertTrue(near > far);
        assertTrue(far > 0);
        assertEquals(0, SetupScore.proximityQuality(new BigDecimal("20"), new BigDecimal("3"), new BigDecimal("15"), 20));
        assertEquals(0, SetupScore.proximityQuality(null, new BigDecimal("3"), new BigDecimal("15"), 20));
    }
}
