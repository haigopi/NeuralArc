package com.neuralarc.service;

import com.neuralarc.api.AlpacaMarketDataApi;
import com.neuralarc.api.AlpacaMarketDataException;
import com.neuralarc.model.AutoAnalyzeResult;
import com.neuralarc.model.MarketBar;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AutoAnalyzeService}.
 * Uses an inline stub {@code FakeMarketDataApi} — no mocking library required.
 */
class AutoAnalyzeServiceTest {

    @Test
    void todaysSnapshotUsesLatestIntradayPriceAndDailyOpenHighLow() throws Exception {
        List<MarketBar> daily = List.of(
                bar("AAPL", "10.00", "12.00", "9.00", "11.50"),
                bar("AAPL", "20.00", "25.00", "18.00", "24.00")
        );
        List<MarketBar> intraday = List.of(
                bar("AAPL", "23.00", "23.50", "22.80", "23.25"),
                bar("AAPL", "23.25", "24.20", "23.10", "24.10")
        );

        AutoAnalyzeService service = serviceWithDailyAndIntraday(daily, intraday);
        AutoAnalyzeResult result = service.analyze("AAPL", 6, 15);

        assertEquals(new BigDecimal("24.10"), result.todayStockPrice());
        assertEquals(new BigDecimal("20.00"), result.todayOpen());
        assertEquals(new BigDecimal("25.00"), result.todayHighSoFar());
        assertEquals(new BigDecimal("18.00"), result.todayLowSoFar());
        assertTrue(result.todayCloseAvailable());
        assertEquals(new BigDecimal("24.00"), result.todayClose());
    }

    @Test
    void todaysCloseIsMarkedUnavailableWhenNoCloseData() throws Exception {
        List<MarketBar> daily = List.of(bar("AAPL", "10.00", "10.50", "9.80", "0.00"));
        AutoAnalyzeService service = serviceWithDailyAndIntraday(daily, Collections.emptyList());
        AutoAnalyzeResult result = service.analyze("AAPL", 6, 15);

        assertFalse(result.todayCloseAvailable());
        assertEquals(new BigDecimal("0.00"), result.todayClose());
    }

    @Test
    void todaysSnapshotFallsBackToMostRecentAnalyzedDailyBarWhenSnapshotFetchIsEmpty() throws Exception {
        LocalDate today = LocalDate.now();
        List<MarketBar> analysisDaily = List.of(
                barOnDate("AAPL", today.minusDays(2), "100.00", "104.00", "99.00", "103.00"),
                barOnDate("AAPL", today.minusDays(1), "103.50", "106.00", "102.00", "105.25")
        );

        AlpacaMarketDataApi api = new AlpacaMarketDataApi() {
            @Override
            public List<MarketBar> getDailyBars(String symbol, LocalDate startDate, LocalDate endDate) {
                if (startDate.equals(today.minusDays(7)) && endDate.equals(today.plusDays(1))) {
                    return Collections.emptyList();
                }
                return analysisDaily;
            }

            @Override
            public List<MarketBar> getIntradayBars(String symbol, LocalDate startDate, LocalDate endDate, int intervalMinutes) {
                return Collections.emptyList();
            }
        };

        AutoAnalyzeResult result = new AutoAnalyzeService(api).analyze("AAPL", 6, 15);

        assertEquals(new BigDecimal("105.25"), result.todayStockPrice());
        assertEquals(new BigDecimal("103.50"), result.todayOpen());
        assertEquals(new BigDecimal("106.00"), result.todayHighSoFar());
        assertEquals(new BigDecimal("102.00"), result.todayLowSoFar());
        assertTrue(result.todayCloseAvailable());
        assertEquals(new BigDecimal("105.25"), result.todayClose());
    }

    // -------------------------------------------------------------------------
    // Windowed low/high ranges
    // -------------------------------------------------------------------------

    @Test
    void rangeWindowsExposeLowAndHighValues() throws Exception {
        List<MarketBar> daily = List.of(
                bar("AAPL", "10.00", "11.00", "9.00",  "10.50"),
                bar("AAPL", "12.00", "15.00", "11.00", "12.50"),
                bar("AAPL", "14.00", "13.00", "8.00",  "14.50")
        );
        AutoAnalyzeService service = serviceWithDailyBars(daily);
        AutoAnalyzeResult result = service.analyze("AAPL", 6, 15);

        // FakeMarketDataApi returns the same daily list for all date ranges,
        // so all range windows collapse to the same min/max in this stub.
        assertEquals(new BigDecimal("8.00"), result.oneWeekLow());
        assertEquals(new BigDecimal("15.00"), result.oneWeekHigh());
        assertEquals(new BigDecimal("8.00"), result.twoWeekLow());
        assertEquals(new BigDecimal("15.00"), result.twoWeekHigh());
        assertEquals(new BigDecimal("8.00"), result.oneMonthLow());
        assertEquals(new BigDecimal("15.00"), result.oneMonthHigh());
        assertEquals(new BigDecimal("8.00"), result.twoMonthLow());
        assertEquals(new BigDecimal("15.00"), result.twoMonthHigh());
        assertEquals(new BigDecimal("8.00"), result.fourMonthLow());
        assertEquals(new BigDecimal("15.00"), result.fourMonthHigh());
        assertEquals(new BigDecimal("8.00"), result.sixMonthLow());
        assertEquals(new BigDecimal("15.00"), result.sixMonthHigh());
        assertEquals(new BigDecimal("8.00"), result.eightMonthLow());
        assertEquals(new BigDecimal("15.00"), result.eightMonthHigh());
        assertEquals(new BigDecimal("8.00"), result.oneYearLow());
        assertEquals(new BigDecimal("15.00"), result.oneYearHigh());
    }

    @Test
    void oneYearLowAndHighAreDerivedFromYearlyFetch() throws Exception {
        // The stub returns the same list for all getDailyBars calls,
        // so 1-year low/high should match min/max of that list.
        List<MarketBar> daily = List.of(
                bar("AAPL", "10.00", "20.00", "5.00", "10.50"),
                bar("AAPL", "12.00", "18.00", "7.00", "12.50")
        );
        AutoAnalyzeService service = serviceWithDailyBars(daily);
        AutoAnalyzeResult result = service.analyze("AAPL", 6, 15);

        assertEquals(new BigDecimal("5.00"), result.oneYearLow());
        assertEquals(new BigDecimal("20.00"), result.oneYearHigh());
    }

    // -------------------------------------------------------------------------
    // Average daily open
    // -------------------------------------------------------------------------

    @Test
    void averageDailyOpenIsComputedCorrectly() throws Exception {
        List<MarketBar> daily = List.of(
                bar("AAPL", "10.00", "11.00", "9.00", "10.50"),
                bar("AAPL", "12.00", "13.00", "11.00", "12.50"),
                bar("AAPL", "14.00", "15.00", "13.00", "14.50")
        );
        AutoAnalyzeService service = serviceWithDailyBars(daily);
        AutoAnalyzeResult result = service.analyze("AAPL", 6, 15);

        // (10 + 12 + 14) / 3 = 12.00
        assertEquals(new BigDecimal("12.00"), result.averageDailyOpen());
    }

    // -------------------------------------------------------------------------
    // Average daily close
    // -------------------------------------------------------------------------

    @Test
    void averageDailyCloseIsComputedCorrectly() throws Exception {
        List<MarketBar> daily = List.of(
                bar("AAPL", "10.00", "11.00", "9.00", "10.50"),
                bar("AAPL", "12.00", "13.00", "11.00", "12.50"),
                bar("AAPL", "14.00", "15.00", "13.00", "14.50")
        );
        AutoAnalyzeService service = serviceWithDailyBars(daily);
        AutoAnalyzeResult result = service.analyze("AAPL", 6, 15);

        // (10.50 + 12.50 + 14.50) / 3 = 12.50
        assertEquals(new BigDecimal("12.50"), result.averageDailyClose());
    }

    // -------------------------------------------------------------------------
    // Average daily low
    // -------------------------------------------------------------------------

    @Test
    void averageDailyLowIsComputedCorrectly() throws Exception {
        List<MarketBar> daily = List.of(
                bar("AAPL", "10.00", "11.00", "9.00",  "10.50"),
                bar("AAPL", "12.00", "13.00", "11.00", "12.50"),
                bar("AAPL", "14.00", "15.00", "13.00", "14.50")
        );
        AutoAnalyzeService service = serviceWithDailyBars(daily);
        AutoAnalyzeResult result = service.analyze("AAPL", 6, 15);

        // (9 + 11 + 13) / 3 = 11.00
        assertEquals(new BigDecimal("11.00"), result.averageDailyLow());
    }

    // -------------------------------------------------------------------------
    // Average daily high
    // -------------------------------------------------------------------------

    @Test
    void averageDailyHighIsComputedCorrectly() throws Exception {
        List<MarketBar> daily = List.of(
                bar("AAPL", "10.00", "11.00", "9.00",  "10.50"),
                bar("AAPL", "12.00", "13.00", "11.00", "12.50"),
                bar("AAPL", "14.00", "15.00", "13.00", "14.50")
        );
        AutoAnalyzeService service = serviceWithDailyBars(daily);
        AutoAnalyzeResult result = service.analyze("AAPL", 6, 15);

        // (11 + 13 + 15) / 3 = 13.00
        assertEquals(new BigDecimal("13.00"), result.averageDailyHigh());
    }

    // -------------------------------------------------------------------------
    // Threshold number (intraday close average)
    // -------------------------------------------------------------------------

    @Test
    void thresholdNumberIsAverageOfIntradayCloses() throws Exception {
        List<MarketBar> intraday = List.of(
                bar("AAPL", "10.00", "10.50", "9.50", "10.20"),
                bar("AAPL", "10.20", "10.80", "9.80", "10.60"),
                bar("AAPL", "10.60", "11.00", "10.00", "10.80")
        );
        AutoAnalyzeService service = serviceWithDailyAndIntraday(
                List.of(bar("AAPL", "10.00", "11.00", "9.00", "10.50")),
                intraday
        );
        AutoAnalyzeResult result = service.analyze("AAPL", 6, 15);

        // (10.20 + 10.60 + 10.80) / 3 = 10.53(3...) → rounds to 10.53
        assertEquals(new BigDecimal("10.53"), result.thresholdNumber());
    }

    // -------------------------------------------------------------------------
    // Empty data handling
    // -------------------------------------------------------------------------

    @Test
    void emptyDailyBarsThrowsAutoAnalyzeException() {
        AutoAnalyzeService service = serviceWithDailyBars(Collections.emptyList());
        AutoAnalyzeException ex = assertThrows(AutoAnalyzeException.class,
                () -> service.analyze("AAPL", 6, 15));
        assertTrue(ex.getMessage().toLowerCase().contains("no daily market data"));
    }

    @Test
    void emptyIntradayBarsResultsInZeroThreshold() throws Exception {
        AutoAnalyzeService service = serviceWithDailyAndIntraday(
                List.of(bar("AAPL", "10.00", "11.00", "9.00", "10.50")),
                Collections.emptyList()
        );
        AutoAnalyzeResult result = service.analyze("AAPL", 6, 15);
        assertEquals(new BigDecimal("0.00"), result.thresholdNumber());
        assertEquals(0, result.intradayBarsProcessed());
    }

    // -------------------------------------------------------------------------
    // Invalid interval
    // -------------------------------------------------------------------------

    @Test
    void invalidIntervalZeroThrowsAutoAnalyzeException() {
        AutoAnalyzeService service = serviceWithDailyBars(
                List.of(bar("AAPL", "10.00", "11.00", "9.00", "10.50")));
        AutoAnalyzeException ex = assertThrows(AutoAnalyzeException.class,
                () -> service.analyze("AAPL", 6, 0));
        assertTrue(ex.getMessage().toLowerCase().contains("interval"));
    }

    @Test
    void invalidIntervalNegativeThrowsAutoAnalyzeException() {
        AutoAnalyzeService service = serviceWithDailyBars(
                List.of(bar("AAPL", "10.00", "11.00", "9.00", "10.50")));
        AutoAnalyzeException ex = assertThrows(AutoAnalyzeException.class,
                () -> service.analyze("AAPL", 6, -5));
        assertTrue(ex.getMessage().toLowerCase().contains("interval"));
    }

    @Test
    void monthsBackSupportsTwelveMonths() throws Exception {
        AutoAnalyzeService service = serviceWithDailyBars(
                List.of(bar("AAPL", "10.00", "11.00", "9.00", "10.50")));
        AutoAnalyzeResult result = service.analyze("AAPL", 12, 15);
        assertEquals("AAPL", result.symbol());
    }

    @Test
    void monthsBackAboveTwelveThrowsAutoAnalyzeException() {
        AutoAnalyzeService service = serviceWithDailyBars(
                List.of(bar("AAPL", "10.00", "11.00", "9.00", "10.50")));
        AutoAnalyzeException ex = assertThrows(AutoAnalyzeException.class,
                () -> service.analyze("AAPL", 13, 15));
        assertTrue(ex.getMessage().toLowerCase().contains("months back"));
    }

    // -------------------------------------------------------------------------
    // Symbol normalisation
    // -------------------------------------------------------------------------

    @Test
    void symbolIsUpperCasedInResult() throws Exception {
        AutoAnalyzeService service = serviceWithDailyBars(
                List.of(bar("aapl", "10.00", "11.00", "9.00", "10.50")));
        AutoAnalyzeResult result = service.analyze("aapl", 6, 15);
        assertEquals("AAPL", result.symbol());
    }

    @Test
    void blankSymbolThrowsAutoAnalyzeException() {
        AutoAnalyzeService service = serviceWithDailyBars(Collections.emptyList());
        assertThrows(AutoAnalyzeException.class,
                () -> service.analyze("  ", 6, 15));
    }

    // -------------------------------------------------------------------------
    // Single bar edge case
    // -------------------------------------------------------------------------

    @Test
    void singleDailyBarResultEqualsThatBar() throws Exception {
        MarketBar only = bar("AAPL", "5.00", "6.00", "4.50", "5.25");
        AutoAnalyzeService service = serviceWithDailyBars(List.of(only));
        AutoAnalyzeResult result = service.analyze("AAPL", 6, 15);

        assertEquals(new BigDecimal("5.00"), result.averageDailyOpen());
        assertEquals(new BigDecimal("5.25"), result.averageDailyClose());
        assertEquals(new BigDecimal("4.50"), result.averageDailyLow());
        assertEquals(new BigDecimal("6.00"), result.averageDailyHigh());
    }

    // -------------------------------------------------------------------------
    // Service error handling (API throws)
    // -------------------------------------------------------------------------

    @Test
    void serviceWrapsApiExceptionInAutoAnalyzeException() {
        FakeMarketDataApi api = new FakeMarketDataApi(null, null);
        api.dailyException = new AlpacaMarketDataException("rate limit exceeded");
        AutoAnalyzeService service = new AutoAnalyzeService(api);
        AutoAnalyzeException ex = assertThrows(AutoAnalyzeException.class,
                () -> service.analyze("AAPL", 6, 15));
        assertTrue(ex.getMessage().toLowerCase().contains("daily bars"));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private AutoAnalyzeService serviceWithDailyBars(List<MarketBar> daily) {
        return new AutoAnalyzeService(new FakeMarketDataApi(daily, Collections.emptyList()));
    }

    private AutoAnalyzeService serviceWithDailyAndIntraday(List<MarketBar> daily, List<MarketBar> intraday) {
        return new AutoAnalyzeService(new FakeMarketDataApi(daily, intraday));
    }

    /** Creates a bar with the given prices; symbol is set to the given symbol. */
    private MarketBar bar(String symbol, String open, String high, String low, String close) {
        return new MarketBar(symbol, "2025-01-01T10:00:00Z",
                new BigDecimal(open), new BigDecimal(high),
                new BigDecimal(low), new BigDecimal(close),
                BigDecimal.ZERO);
    }

    private MarketBar barOnDate(String symbol, LocalDate date, String open, String high, String low, String close) {
        return new MarketBar(symbol, date + "T20:00:00Z",
                new BigDecimal(open), new BigDecimal(high),
                new BigDecimal(low), new BigDecimal(close),
                BigDecimal.ZERO);
    }

    // -------------------------------------------------------------------------
    // Stub
    // -------------------------------------------------------------------------

    private static class FakeMarketDataApi implements AlpacaMarketDataApi {
        private final List<MarketBar> daily;
        private final List<MarketBar> intraday;
        AlpacaMarketDataException dailyException;
        AlpacaMarketDataException intradayException;

        FakeMarketDataApi(List<MarketBar> daily, List<MarketBar> intraday) {
            this.daily    = daily    != null ? daily    : Collections.emptyList();
            this.intraday = intraday != null ? intraday : Collections.emptyList();
        }

        @Override
        public List<MarketBar> getDailyBars(String symbol, LocalDate startDate, LocalDate endDate)
                throws AlpacaMarketDataException {
            if (dailyException != null) throw dailyException;
            return daily;
        }

        @Override
        public List<MarketBar> getIntradayBars(String symbol, LocalDate startDate, LocalDate endDate, int intervalMinutes)
                throws AlpacaMarketDataException {
            if (intervalMinutes <= 0) {
                throw new AlpacaMarketDataException("Interval must be a positive number of minutes");
            }
            if (intradayException != null) throw intradayException;
            return intraday;
        }
    }
}
