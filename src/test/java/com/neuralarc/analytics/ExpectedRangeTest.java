package com.neuralarc.analytics;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpectedRangeTest {
    @Test
    void aStockThatHasNotMovedProjectsNoBand() {
        double[] flat = new double[30];
        java.util.Arrays.fill(flat, 100.0);

        ExpectedRange.Projection projection = ExpectedRange.project(flat, 21);

        assertFalse(projection.known(), "no variation means nothing to project, not a zero-width certainty");
        assertTrue(projection.describe().contains("Not enough"));
    }

    @Test
    void theBandWidensWithTheSquareRootOfTime() {
        double[] closes = wobbling(120, 100, 0.02);

        ExpectedRange.Projection week = ExpectedRange.project(closes, 5);
        ExpectedRange.Projection month = ExpectedRange.project(closes, 20);

        double weekWidth = week.oneSigmaHigh().doubleValue() - week.oneSigmaLow().doubleValue();
        double monthWidth = month.oneSigmaHigh().doubleValue() - month.oneSigmaLow().doubleValue();
        assertTrue(monthWidth > weekWidth);
        // Four times the days is about twice the width, not four times.
        assertEquals(2.0, monthWidth / weekWidth, 0.15);
    }

    @Test
    void twoSigmaSitsOutsideOneSigmaOnBothSides() {
        ExpectedRange.Projection projection = ExpectedRange.project(wobbling(120, 250, 0.015), 21);

        assertTrue(projection.twoSigmaHigh().compareTo(projection.oneSigmaHigh()) > 0);
        assertTrue(projection.twoSigmaLow().compareTo(projection.oneSigmaLow()) < 0);
        assertTrue(projection.oneSigmaLow().compareTo(projection.lastPrice()) < 0);
        assertTrue(projection.oneSigmaHigh().compareTo(projection.lastPrice()) > 0);
    }

    @Test
    void theLowerEdgeStaysAboveZeroEvenOverAbsurdHorizons() {
        ExpectedRange.Projection projection = ExpectedRange.project(wobbling(120, 5, 0.09), 500);

        assertTrue(projection.twoSigmaLow().compareTo(BigDecimal.ZERO) > 0,
                "a lognormal band cannot price a stock below zero, however far out it runs");
    }

    @Test
    void aNoisierStockGetsAWiderBand() {
        ExpectedRange.Projection calm = ExpectedRange.project(wobbling(120, 100, 0.005), 21);
        ExpectedRange.Projection wild = ExpectedRange.project(wobbling(120, 100, 0.04), 21);

        assertTrue(wild.horizonSigmaPercent() > calm.horizonSigmaPercent() * 3);
    }

    @Test
    void tooFewPricesMeanNoProjectionRatherThanAConfidentOne() {
        assertFalse(ExpectedRange.project(new double[]{100, 101, 102}, 21).known());
        assertFalse(ExpectedRange.project(null, 21).known());
        assertFalse(ExpectedRange.project(wobbling(60, 100, 0.02), 0).known(), "a zero horizon has no band");
    }

    @Test
    void theSentenceSaysItIsARangeAndNotATarget() {
        String described = ExpectedRange.project(wobbling(120, 246, 0.018), 21).describe();

        assertTrue(described.contains("two thirds"));
        assertTrue(described.contains("A range, not a target"));
        assertTrue(described.contains("nothing about which way"));
    }

    @Test
    void theConeWidensDayByDayFromTodaysPrice() {
        double sigma = 0.02;
        double today = ExpectedRange.edge(100, sigma, 0, 1);
        double tomorrow = ExpectedRange.edge(100, sigma, 1, 1);
        double inTenDays = ExpectedRange.edge(100, sigma, 10, 1);

        assertEquals(100, today, 0.0001, "the cone starts at the last traded price");
        assertTrue(tomorrow > today && inTenDays > tomorrow);
        assertEquals(100 * Math.exp(-0.02 * Math.sqrt(10)), ExpectedRange.edge(100, sigma, 10, -1), 0.0001);
    }

    /** A series that moves by a fixed percentage each day, alternating direction. */
    private static double[] wobbling(int count, double start, double dailyMove) {
        double[] closes = new double[count];
        closes[0] = start;
        for (int i = 1; i < count; i++) {
            closes[i] = closes[i - 1] * (1 + (i % 2 == 0 ? dailyMove : -dailyMove));
        }
        return closes;
    }
}
