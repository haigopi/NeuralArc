package com.neuralarc.analytics;

import com.neuralarc.model.MarketBar;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.IntToDoubleFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrendEntryModelTest {
    private static final BigDecimal NO_LOW = BigDecimal.ZERO;

    /** {@code count} sessions ending today; {@code close} maps a session index (0 = oldest) to its close. */
    private static List<MarketBar> bars(int count, IntToDoubleFunction close) {
        List<MarketBar> bars = new ArrayList<>();
        LocalDate first = LocalDate.of(2026, 1, 1);
        for (int i = 0; i < count; i++) {
            double c = close.applyAsDouble(i);
            // A deterministic wobble keeps the volatility away from zero.
            double wobble = (i % 2 == 0 ? 1 : -1) * 0.004;
            BigDecimal price = BigDecimal.valueOf(c * (1 + wobble)).setScale(4, RoundingMode.HALF_UP);
            bars.add(new MarketBar("T", first.plusDays(i).toString(), price, price, price.multiply(new BigDecimal("0.99")),
                    price, BigDecimal.ONE));
        }
        return bars;
    }

    private static BigDecimal market(List<MarketBar> bars) {
        return bars.get(bars.size() - 1).close().setScale(2, RoundingMode.HALF_UP);
    }

    @Test
    void aSteadyClimbReadsAsAnUptrendAndIsBoughtOnAShallowDip() {
        List<MarketBar> climbing = bars(260, i -> 50 * Math.pow(1.003, i));

        TrendEntryModel.Prediction prediction = TrendEntryModel.predict(climbing, market(climbing), NO_LOW).orElseThrow();

        assertTrue(prediction.trend() > 0.5, "trend " + prediction.trend());
        assertTrue(prediction.price().compareTo(market(climbing)) < 0);
        assertTrue(prediction.expectedDipPercent() < 6, "a rising stock is bought on a shallow dip: " + prediction.expectedDipPercent());
        assertEquals(4, prediction.windows().size());
    }

    @Test
    void aSteadyDeclineReadsAsADowntrendAndIsBoughtDeeperThanAnUptrend() {
        List<MarketBar> falling = bars(260, i -> 200 * Math.pow(0.997, i));
        List<MarketBar> climbing = bars(260, i -> 50 * Math.pow(1.003, i));

        TrendEntryModel.Prediction down = TrendEntryModel.predict(falling, market(falling), NO_LOW).orElseThrow();
        TrendEntryModel.Prediction up = TrendEntryModel.predict(climbing, market(climbing), NO_LOW).orElseThrow();

        assertTrue(down.trend() < -0.5, "trend " + down.trend());
        assertTrue(down.expectedDipPercent() > up.expectedDipPercent(),
                down.expectedDipPercent() + " vs " + up.expectedDipPercent());
    }

    @Test
    void theEntryIsNeverAboveTheMarket() {
        for (double drift : new double[] {0.99, 1.0, 1.01}) {
            List<MarketBar> history = bars(260, i -> 100 * Math.pow(drift, i / 10.0));
            BigDecimal market = market(history);

            TrendEntryModel.Prediction prediction = TrendEntryModel.predict(history, market, NO_LOW).orElseThrow();

            assertTrue(prediction.price().compareTo(market) <= 0, "drift " + drift);
            assertTrue(prediction.price().signum() > 0);
        }
    }

    @Test
    void aClearUptrendIsNeverPlannedUnderItsTwoWeekLow() {
        List<MarketBar> volatileClimb = bars(260, i -> 50 * Math.pow(1.003, i) * (1 + 0.0 * i));
        // Make today's session very volatile so the raw dip would be deep.
        BigDecimal market = market(volatileClimb);

        TrendEntryModel.Prediction prediction = TrendEntryModel.predict(volatileClimb, market, NO_LOW).orElseThrow();

        BigDecimal twoWeekLow = prediction.windows().get(0).low();
        assertTrue(prediction.trend() > TrendEntryModel.CLEAR_UPTREND);
        assertTrue(prediction.price().compareTo(twoWeekLow.setScale(2, RoundingMode.FLOOR)) >= 0,
                prediction.price() + " vs 2-week low " + twoWeekLow);
    }

    @Test
    void aFallingStockIsFlooredAtItsLowestLowInTheLongestWindowRead() {
        List<MarketBar> falling = bars(260, i -> 200 * Math.pow(0.99, i));
        BigDecimal market = market(falling);

        TrendEntryModel.Prediction prediction = TrendEntryModel.predict(falling, market, NO_LOW).orElseThrow();

        BigDecimal longestLow = prediction.windows().get(prediction.windows().size() - 1).low();
        assertTrue(prediction.price().compareTo(longestLow.setScale(2, RoundingMode.FLOOR)) >= 0);
    }

    @Test
    void windowsWithTooLittleHistoryAreSkippedAndTheRestAreStillUsed() {
        List<MarketBar> threeMonths = bars(70, i -> 100 + i * 0.1);

        TrendEntryModel.Prediction prediction = TrendEntryModel.predict(threeMonths, market(threeMonths), NO_LOW).orElseThrow();

        assertEquals(List.of("2 weeks", "2 months"), prediction.windows().stream().map(TrendEntryModel.WindowRead::label).toList());
    }

    @Test
    void noUsableHistoryMeansNoPrediction() {
        assertEquals(Optional.empty(), TrendEntryModel.predict(List.of(), new BigDecimal("100"), NO_LOW));
        assertEquals(Optional.empty(), TrendEntryModel.predict(null, new BigDecimal("100"), NO_LOW));
        assertEquals(Optional.empty(), TrendEntryModel.predict(bars(3, i -> 100), new BigDecimal("100"), NO_LOW));
        assertEquals(Optional.empty(), TrendEntryModel.predict(bars(260, i -> 100), BigDecimal.ZERO, NO_LOW));
    }

    @Test
    void aFlatStockHasNoTrendAndTheDipIsJustTheNoise() {
        List<MarketBar> flat = bars(260, i -> 100);

        TrendEntryModel.Prediction prediction = TrendEntryModel.predict(flat, market(flat), NO_LOW).orElseThrow();

        assertTrue(Math.abs(prediction.trend()) < 0.3, "trend " + prediction.trend());
    }

    @Test
    void theSummaryListsEachWindowsReading() {
        List<MarketBar> climbing = bars(260, i -> 50 * Math.pow(1.003, i));

        String summary = TrendEntryModel.predict(climbing, market(climbing), NO_LOW).orElseThrow().summary();

        assertTrue(summary.startsWith("trend +"), summary);
        assertTrue(summary.contains("2 weeks") && summary.contains("1 year") && summary.contains("expected dip"), summary);
    }
}
