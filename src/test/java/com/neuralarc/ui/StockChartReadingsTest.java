package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StockChartReadingsTest {
    @Test
    void describesASteadyDowntrendInPlainWords() {
        StockChartReadings.Reading trend = reading(
                StockChartData.from("NIO", StockChartTestBars.fromCloses(StockChartTestBars.steady(20, 5, 400)), List.of()),
                StockChartReadings.TREND);

        assertTrue(trend.now().contains("below all 6 moving averages"), trend.now());
        assertTrue(trend.now().contains("steady downtrend"), trend.now());
        assertEquals(StockChartReadings.Tone.NEGATIVE, trend.tone());
        assertTrue(trend.whatItIs().contains("Each candle is one trading day"));
    }

    @Test
    void describesASteadyUptrend() {
        StockChartReadings.Reading trend = reading(
                StockChartData.from("NIO", StockChartTestBars.fromCloses(StockChartTestBars.steady(5, 20, 400)), List.of()),
                StockChartReadings.TREND);

        assertTrue(trend.now().contains("above all 6 moving averages"), trend.now());
        assertTrue(trend.now().contains("steady uptrend"), trend.now());
        assertEquals(StockChartReadings.Tone.POSITIVE, trend.tone());
    }

    @Test
    void flagsAnOversoldRsiAndSaysWhatThatMeans() {
        StockChartReadings.Reading rsi = reading(
                StockChartData.from("NIO", StockChartTestBars.fromCloses(StockChartTestBars.steady(20, 5, 400)), List.of()),
                StockChartReadings.RSI);

        assertTrue(rsi.now().contains("oversold"), rsi.now());
        assertTrue(rsi.now().contains("can stay low for weeks"), "the caveat is part of the reading");
        assertEquals(StockChartReadings.Tone.CAUTION, rsi.tone());
    }

    @Test
    void namesTheNearestSupportAndResistance() {
        StockChartReadings.Reading turningPoints = reading(
                StockChartData.from("NIO", StockChartTestBars.fromCloses(StockChartTestBars.swings()), List.of()),
                StockChartReadings.TURNING_POINTS);

        assertTrue(turningPoints.now().contains("Nearest support below: $6.90"), turningPoints.now());
        assertTrue(turningPoints.now().contains("Nearest resistance above: $12.10"), turningPoints.now());
    }

    @Test
    void relatesThePriceToTheStrategysOwnLevels() {
        StockChartReadings.Reading plan = reading(
                StockChartData.from("NIO", StockChartTestBars.fromCloses(StockChartTestBars.swings()), List.of(
                        new StockChartLevels.Level(StockChartLevels.Kind.AVERAGE_COST, "Your avg cost", 10.0, ""),
                        new StockChartLevels.Level(StockChartLevels.Kind.TARGET, "Target", 12.0, ""),
                        new StockChartLevels.Level(StockChartLevels.Kind.STOP_LOSS, "Stop loss", 7.0, ""))),
                StockChartReadings.PLAN);

        assertTrue(plan.now().contains("20.0% below your average cost of $10.00"), plan.now());
        assertTrue(plan.now().contains("The target at $12.00 is 50.0% above the current price"), plan.now());
        assertTrue(plan.now().contains("The stop-loss at $7.00 is 12.5% below the current price"), plan.now());
        assertEquals(StockChartReadings.Tone.NEGATIVE, plan.tone());
    }

    @Test
    void thePlanSectionAppearsOnlyWhenTheStrategyHasLevels() {
        List<StockChartReadings.Reading> readings = StockChartReadings.describe(
                StockChartData.from("NIO", StockChartTestBars.fromCloses(StockChartTestBars.swings()), List.of()));

        assertTrue(readings.stream().noneMatch(r -> r.title().equals(StockChartReadings.PLAN)));
    }

    @Test
    void explainsThatVolumeComesFromThePartialIexFeed() {
        StockChartReadings.Reading volume = reading(
                StockChartData.from("NIO", StockChartTestBars.fromCloses(StockChartTestBars.swings()), List.of()),
                StockChartReadings.VOLUME);

        assertTrue(volume.whatItIs().contains("IEX"), volume.whatItIs());
        assertTrue(volume.now().contains("50-day average"), volume.now());
    }

    @Test
    void readsFallingAccumulationAsDistribution() {
        StockChartReadings.Reading accumulation = reading(
                StockChartData.from("NIO", StockChartTestBars.fromCloses(StockChartTestBars.steady(20, 5, 400)), List.of()),
                StockChartReadings.ACCUMULATION);

        assertTrue(accumulation.now().contains("distribution"), accumulation.now());
        assertEquals(StockChartReadings.Tone.NEGATIVE, accumulation.tone());
    }

    @Test
    void everySectionExplainsWhatThePanelIs() {
        List<StockChartReadings.Reading> readings = StockChartReadings.describe(
                StockChartData.from("NIO", StockChartTestBars.fromCloses(StockChartTestBars.swings()), List.of(
                        new StockChartLevels.Level(StockChartLevels.Kind.AVERAGE_COST, "Your avg cost", 10.0, ""))));

        assertEquals(7, readings.size());
        for (StockChartReadings.Reading reading : readings) {
            assertTrue(reading.whatItIs().length() > 80, reading.title());
            assertTrue(!reading.now().isBlank(), reading.title());
        }
    }

    @Test
    void noHistoryMeansNoReadings() {
        assertTrue(StockChartReadings.describe(StockChartData.from("NIO", List.of(), List.of())).isEmpty());
    }

    private static StockChartReadings.Reading reading(StockChartData data, String title) {
        return StockChartReadings.describe(data).stream()
                .filter(reading -> reading.title().equals(title))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no reading titled " + title));
    }
}
