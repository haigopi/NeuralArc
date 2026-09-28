package com.neuralarc.ui;

import com.neuralarc.analytics.ExpectedRange;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StockChartProjectionTest {
    @Test
    void theChartProjectsTheRangeItsOwnClosesImply() {
        StockChartPanel panel = new StockChartPanel(data(wobbling(120, 240, 0.015)));

        ExpectedRange.Projection projection = panel.projection();

        assertTrue(projection.known());
        assertEquals(StockChartPanel.PROJECTION_SESSIONS, projection.sessions());
        assertTrue(panel.isProjectionVisible());
    }

    @Test
    void aStockWithNoVariationGetsNoConeAndNoWastedWidth() {
        double[] flat = new double[60];
        java.util.Arrays.fill(flat, 100.0);
        StockChartPanel panel = new StockChartPanel(data(flat));

        assertFalse(panel.projection().known());
        assertFalse(panel.isProjectionVisible(), "an empty cone must not take space from the candles");
    }

    @Test
    void theConeCanBeTurnedOff() {
        StockChartPanel panel = new StockChartPanel(data(wobbling(120, 240, 0.015)));

        panel.setProjectionVisible(false);

        assertFalse(panel.isProjectionVisible());
        assertTrue(panel.projection().known(), "the numbers stay available for the reading below the chart");
    }

    @Test
    void theReadingExplainsTheConeAndRefusesToCallItAForecast() {
        List<StockChartReadings.Reading> readings =
                StockChartReadings.describe(data(wobbling(120, 240, 0.015)));

        StockChartReadings.Reading projection = readings.stream()
                .filter(reading -> reading.title().equals(StockChartReadings.PROJECTION))
                .findFirst()
                .orElseThrow();
        assertEquals(StockChartReadings.Tone.NEUTRAL, projection.tone(), "a range has no direction to colour");
        assertTrue(projection.now().contains("two thirds"));
        assertTrue(projection.whatItIs().contains("not a forecast"));
        assertTrue(projection.whatItIs().contains("square root of time"));
    }

    private static StockChartData data(double[] closes) {
        return StockChartData.from("TEST", StockChartTestBars.fromCloses(closes), List.of());
    }

    private static double[] wobbling(int count, double start, double dailyMove) {
        double[] closes = new double[count];
        closes[0] = start;
        for (int i = 1; i < count; i++) {
            closes[i] = closes[i - 1] * (1 + (i % 2 == 0 ? dailyMove : -dailyMove));
        }
        return closes;
    }
}
