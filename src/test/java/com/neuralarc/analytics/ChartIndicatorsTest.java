package com.neuralarc.analytics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChartIndicatorsTest {
    @Test
    void emaSeedsWithTheSimpleAverageThenWeightsRecentPrices() {
        double[] ema = ChartIndicators.ema(new double[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, 3);

        assertTrue(Double.isNaN(ema[1]), "not enough history yet");
        assertEquals(2.0, ema[2], 1e-9, "the simple average of 1, 2, 3");
        assertEquals(3.0, ema[3], 1e-9, "2 + (2/4) x (4 - 2)");
        assertEquals(9.0, ema[9], 1e-9, "on a steady rise it lags one step behind");
    }

    @Test
    void emaStartsAfterLeadingGaps() {
        double[] ema = ChartIndicators.ema(new double[]{Double.NaN, Double.NaN, 1, 2, 3}, 2);

        assertTrue(Double.isNaN(ema[2]));
        assertEquals(1.5, ema[3], 1e-9);
        assertEquals(2.5, ema[4], 1e-9);
    }

    @Test
    void smaAveragesTheWindow() {
        double[] sma = ChartIndicators.sma(new double[]{2, 4, 6, 8}, 2);

        assertTrue(Double.isNaN(sma[0]));
        assertEquals(3.0, sma[1], 1e-9);
        assertEquals(7.0, sma[3], 1e-9);
    }

    @Test
    void rsiIsFiftyWhenUpAndDownMovesBalance() {
        double[] closes = new double[15];
        for (int i = 0; i < closes.length; i++) {
            closes[i] = i % 2 == 0 ? 10 : 11;
        }
        double[] rsi = ChartIndicators.rsi(closes, 14);

        assertTrue(Double.isNaN(rsi[13]), "needs 14 changes, so 15 closes");
        assertEquals(50.0, rsi[14], 1e-9);
    }

    @Test
    void rsiIsOneHundredWhenPriceOnlyRisesAndZeroWhenItOnlyFalls() {
        double[] rising = new double[20];
        double[] falling = new double[20];
        for (int i = 0; i < 20; i++) {
            rising[i] = 10 + i;
            falling[i] = 40 - i;
        }

        assertEquals(100.0, ChartIndicators.rsi(rising, 14)[19], 1e-9);
        assertEquals(0.0, ChartIndicators.rsi(falling, 14)[19], 1e-9);
    }

    @Test
    void macdIsTheGapBetweenAveragesAndTheHistogramIsTheGapToItsSignal() {
        double[] closes = new double[80];
        for (int i = 0; i < closes.length; i++) {
            closes[i] = 20 + i * 0.1 + Math.sin(i / 3.0);
        }
        ChartIndicators.Macd macd = ChartIndicators.macd(closes, 12, 26, 9);
        double[] fast = ChartIndicators.ema(closes, 12);
        double[] slow = ChartIndicators.ema(closes, 26);

        assertTrue(Double.isNaN(macd.line()[24]));
        assertFalse(Double.isNaN(macd.line()[25]), "defined once the 26-day average is");
        assertTrue(Double.isNaN(macd.signal()[32]));
        assertFalse(Double.isNaN(macd.signal()[33]), "then 9 more days for the signal line");
        for (int i = 33; i < closes.length; i++) {
            assertEquals(fast[i] - slow[i], macd.line()[i], 1e-9);
            assertEquals(macd.line()[i] - macd.signal()[i], macd.histogram()[i], 1e-9);
        }
    }

    @Test
    void accumulationDistributionAddsVolumeOnStrongClosesAndSubtractsOnWeakOnes() {
        double[] line = ChartIndicators.accumulationDistribution(
                new double[]{10, 10, 10},
                new double[]{8, 8, 10},
                new double[]{10, 8, 10},
                new double[]{100, 50, 70});

        assertEquals(100.0, line[0], 1e-9, "closed at the high: all 100 added");
        assertEquals(50.0, line[1], 1e-9, "closed at the low: 50 taken away");
        assertEquals(50.0, line[2], 1e-9, "no range that day: nothing changes");
    }
}
