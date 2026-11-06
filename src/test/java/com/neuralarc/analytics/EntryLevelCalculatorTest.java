package com.neuralarc.analytics;

import com.neuralarc.model.MarketBar;
import com.neuralarc.model.ScannerEntrySettings;
import com.neuralarc.model.ScannerEntrySettings.Anchor;
import com.neuralarc.model.ScannerEntrySettings.Mode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntryLevelCalculatorTest {
    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    /** One bar per session, oldest first; every session's low comes from {@code lows}. */
    private static List<MarketBar> history(double... lows) {
        List<MarketBar> bars = new ArrayList<>();
        LocalDate first = LocalDate.of(2026, 1, 1);
        for (int i = 0; i < lows.length; i++) {
            BigDecimal low = BigDecimal.valueOf(lows[i]);
            BigDecimal high = low.add(BigDecimal.TEN);
            bars.add(new MarketBar("T", first.plusDays(i).toString(), low.add(BigDecimal.ONE), high, low,
                    low.add(new BigDecimal("2")), BigDecimal.ONE));
        }
        return bars;
    }

    /** 300 sessions: the year's low (50) is long ago, then a 6-month low of 80, a month low of 90, a week low of 97. */
    private static List<MarketBar> longHistory() {
        double[] lows = new double[300];
        for (int i = 0; i < lows.length; i++) {
            int fromEnd = lows.length - 1 - i;
            lows[i] = fromEnd < 5 ? 97 + fromEnd : fromEnd < 21 ? 90 + fromEnd % 3 : fromEnd < 126 ? 80 + fromEnd % 5 : 50 + fromEnd % 7;
        }
        return history(lows);
    }

    private static ScannerEntrySettings custom(Anchor anchor, String percent) {
        return new ScannerEntrySettings(Mode.CUSTOM_PERCENT, anchor, d(percent));
    }

    @Test
    void theDefaultIsTheLowerOfTheTwoWeekLowAndTheCurrentLow() {
        List<MarketBar> history = history(95, 96, 94, 97, 98, 99, 96, 97, 98, 99, 100, 101);

        BigDecimal level = EntryLevelCalculator.level(ScannerEntrySettings.defaults(), d("102"), d("101"), d("100"), history);

        assertEquals(d("94.00"), level, "the 2-week low (94) is under today's low");
    }

    @Test
    void theDefaultFallsToTodaysLowWhenTodayBrokeTheTwoWeekLow() {
        List<MarketBar> history = history(95, 96, 97, 98, 99);

        BigDecimal level = EntryLevelCalculator.level(ScannerEntrySettings.defaults(), d("93.50"), d("96"), d("92.75"), history);

        assertEquals(d("92.75"), level);
    }

    @Test
    void theTwoWeekWindowIsTenSessions() {
        // The 80 is eleven sessions back, so it is outside two weeks.
        List<MarketBar> history = history(80, 95, 95, 95, 95, 95, 95, 95, 95, 95, 95);

        BigDecimal level = EntryLevelCalculator.level(ScannerEntrySettings.defaults(), d("100"), d("100"), d("99"), history);

        assertEquals(d("95.00"), level);
    }

    @Test
    void customUsesAPercentageAboveTheChosenPeriodLow() {
        List<MarketBar> history = longHistory();
        BigDecimal market = d("120");

        assertEquals(d("97.00"), EntryLevelCalculator.level(custom(Anchor.LAST_WEEK, "0"), market, market, market, history));
        assertEquals(d("94.50"), EntryLevelCalculator.level(custom(Anchor.ONE_MONTH, "5"), market, market, market, history),
                "5% above the month's low of 90");
        assertEquals(d("88.00"), EntryLevelCalculator.level(custom(Anchor.SIX_MONTHS, "10"), market, market, market, history),
                "10% above the six months' low of 80");
        assertEquals(d("55.00"), EntryLevelCalculator.level(custom(Anchor.ONE_YEAR, "10"), market, market, market, history),
                "10% above the year's low of 50");
    }

    @Test
    void customIsNeverAboveTheMarket() {
        List<MarketBar> history = longHistory();

        BigDecimal level = EntryLevelCalculator.level(custom(Anchor.ONE_MONTH, "50"), d("100"), d("100"), d("100"), history);

        assertEquals(d("100.00"), level, "50% above 90 would be 135, above the market");
    }

    @Test
    void withoutHistoryTheLevelIsTheMarket() {
        BigDecimal level = EntryLevelCalculator.level(custom(Anchor.ONE_YEAR, "5"), d("100"), d("101"), d("99.5"), List.of());

        assertEquals(d("99.50"), level);
    }

    @Test
    void autoUsesThePredictionAndStaysUnderTheMarket() {
        List<MarketBar> history = longHistory();
        ScannerEntrySettings auto = new ScannerEntrySettings(Mode.AUTO_TREND, Anchor.TWO_WEEKS, BigDecimal.ZERO);

        BigDecimal level = EntryLevelCalculator.level(auto, d("110"), d("110"), d("109"), history);

        assertTrue(level.signum() > 0 && level.compareTo(d("109")) <= 0, level.toPlainString());
    }

    @Test
    void autoFallsBackToTheMarketWithoutHistory() {
        ScannerEntrySettings auto = new ScannerEntrySettings(Mode.AUTO_TREND, Anchor.TWO_WEEKS, BigDecimal.ZERO);

        assertEquals(d("100.00"), EntryLevelCalculator.level(auto, d("100"), d("102"), d("101"), List.of()));
    }

    @Test
    void nothingIsPlannedWithoutAPrice() {
        assertEquals(BigDecimal.ZERO, EntryLevelCalculator.level(ScannerEntrySettings.defaults(), null, null, null, history(90)));
    }

    @Test
    void theOrderOfTheBarsDoesNotMatter() {
        List<MarketBar> history = new ArrayList<>(longHistory());
        Collections.reverse(history);

        assertEquals(d("97.00"), EntryLevelCalculator.level(custom(Anchor.LAST_WEEK, "0"), d("120"), d("120"), d("120"), history));
    }
}
