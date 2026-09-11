package com.neuralarc.analytics;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwingPointsTest {
    @Test
    void findsTheHighestHighAndLowestLowWithinTheWindow() {
        double[] highs = {5, 6, 7, 10, 7, 6, 5, 4, 6, 5};
        double[] lows = {4, 5, 6, 9, 6, 5, 4, 3, 5, 4};

        List<SwingPoints.Swing> swings = SwingPoints.find(highs, lows, 2);

        assertEquals(List.of(new SwingPoints.Swing(3, 10, true), new SwingPoints.Swing(7, 3, false)), swings);
    }

    @Test
    void theFirstBarOfAFlatTopIsTheTurningPoint() {
        double[] highs = {1, 2, 5, 5, 2, 1, 0};
        double[] lows = {1, 1, 1, 1, 1, 1, 1};

        assertEquals(List.of(new SwingPoints.Swing(2, 5, true)), SwingPoints.find(highs, lows, 2));
    }

    @Test
    void theNewestBarsAreNotConfirmedYet() {
        double[] rising = {1, 2, 3, 4, 5, 6, 7, 8};

        assertTrue(SwingPoints.find(rising, rising, 2).stream().noneMatch(SwingPoints.Swing::high),
                "the latest high has nothing after it to prove it was a turn");
    }
}
