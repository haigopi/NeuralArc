package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketReactionSummaryTest {
    @Test
    void aSteadyClimbReadsAsBuyersInControl() {
        MarketReactionSummary.Summary summary = summarize(StockChartTestBars.steady(10, 30, 300));

        assertEquals(MarketReactionSummary.Verdict.BUYERS, summary.verdict());
        assertTrue(summary.text().startsWith("Up "), summary.text());
        assertTrue(summary.text().contains("price holds above its moving averages"), summary.text());
    }

    @Test
    void aSteadySlideReadsAsSellersInControl() {
        MarketReactionSummary.Summary summary = summarize(StockChartTestBars.steady(30, 10, 300));

        assertEquals(MarketReactionSummary.Verdict.SELLERS, summary.verdict());
        assertTrue(summary.text().startsWith("Down "), summary.text());
        assertTrue(summary.text().contains("price sits below its moving averages"), summary.text());
    }

    @Test
    void theVerdictAgreesWithTheFullChartsOwnReadings() {
        // The summary is built from the chart guide's readings, so the two can never disagree.
        StockChartData data = StockChartData.from("TEST", StockChartTestBars.fromCloses(StockChartTestBars.steady(30, 10, 300)), List.of());
        StockChartReadings.Reading trend = StockChartReadings.describe(data).get(0);

        assertEquals(StockChartReadings.Tone.NEGATIVE, trend.tone());
        assertEquals(MarketReactionSummary.Verdict.SELLERS, MarketReactionSummary.summarize(data).verdict());
    }

    @Test
    void noDataGivesNoVerdict() {
        assertEquals(MarketReactionSummary.Summary.empty(), MarketReactionSummary.summarize(null));
    }

    private static MarketReactionSummary.Summary summarize(double[] closes) {
        return MarketReactionSummary.summarize(
                StockChartData.from("TEST", StockChartTestBars.fromCloses(closes), List.of()));
    }
}
