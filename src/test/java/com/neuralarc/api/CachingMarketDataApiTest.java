package com.neuralarc.api;

import com.neuralarc.model.MarketBar;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CachingMarketDataApiTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 16);

    @Test
    void theFourDailyWindowsOneAnalysisAsksForCostASingleFetch() throws Exception {
        // What AutoAnalyzeService requests per symbol: a year, the same year again, the last week
        // ending tomorrow, and two years. All fit inside the widest, so one fetch answers them all.
        RecordingApi api = new RecordingApi();
        CachingMarketDataApi cache = new CachingMarketDataApi(api);

        assertEquals(366, cache.getDailyBars("AAPL", TODAY.minusMonths(12), TODAY).size());
        cache.getDailyBars("AAPL", TODAY.minusYears(1), TODAY);
        cache.getDailyBars("AAPL", TODAY.minusDays(7), TODAY.plusDays(1));
        cache.getDailyBars("AAPL", TODAY.minusDays(730), TODAY);

        assertEquals(1, api.dailyCalls.size(), "expected one fetch, got " + api.dailyCalls);
    }

    @Test
    void theWidenedFetchReachesTomorrowSoTheSnapshotWindowIsCovered() throws Exception {
        // The snapshot window ends tomorrow. If widening stopped at today this would refetch, and a
        // careless implementation would instead return a truncated week.
        RecordingApi api = new RecordingApi();
        CachingMarketDataApi cache = new CachingMarketDataApi(api);

        cache.getDailyBars("AAPL", TODAY.minusMonths(12), TODAY);
        List<MarketBar> week = cache.getDailyBars("AAPL", TODAY.minusDays(7), TODAY.plusDays(1));

        assertEquals(1, api.dailyCalls.size());
        assertEquals(9, week.size(), "seven days back through tomorrow, inclusive at both ends");
        assertEquals(TODAY.plusDays(1).toString(), week.get(week.size() - 1).timestamp().substring(0, 10),
                "the slice must reach tomorrow, which is what the snapshot window asks for");
    }

    @Test
    void aSliceReturnsOnlyTheDatesAskedForInTheOrderTheBrokerGaveThem() throws Exception {
        RecordingApi api = new RecordingApi();
        CachingMarketDataApi cache = new CachingMarketDataApi(api);
        cache.getDailyBars("AAPL", TODAY.minusMonths(12), TODAY);

        List<MarketBar> slice = cache.getDailyBars("AAPL", TODAY.minusDays(3), TODAY.minusDays(1));

        assertEquals(List.of(
                        TODAY.minusDays(3).toString(),
                        TODAY.minusDays(2).toString(),
                        TODAY.minusDays(1).toString()),
                slice.stream().map(bar -> bar.timestamp().substring(0, 10)).toList());
    }

    @Test
    void aRangeTheCacheOnlyPartlyCoversIsFetchedAgainRatherThanTruncated() throws Exception {
        // The dangerous case: serving the overlap would look like a complete history and silently
        // drop the older half.
        RecordingApi api = new RecordingApi();
        CachingMarketDataApi cache = new CachingMarketDataApi(api);
        cache.getDailyBars("AAPL", TODAY.minusDays(30), TODAY);

        List<MarketBar> older = cache.getDailyBars("AAPL", TODAY.minusDays(2000), TODAY);

        assertEquals(2, api.dailyCalls.size(), "the uncovered range must be refetched");
        assertEquals(2001, older.size(), "and must come back complete");
    }

    @Test
    void eachSymbolIsCachedSeparately() throws Exception {
        RecordingApi api = new RecordingApi();
        CachingMarketDataApi cache = new CachingMarketDataApi(api);

        cache.getDailyBars("AAPL", TODAY.minusDays(10), TODAY);
        cache.getDailyBars("MSFT", TODAY.minusDays(10), TODAY);
        cache.getDailyBars("AAPL", TODAY.minusDays(5), TODAY);

        assertEquals(2, api.dailyCalls.size());
    }

    @Test
    void aStaleEntryIsFetchedAgain() throws Exception {
        RecordingApi api = new RecordingApi();
        AtomicLong now = new AtomicLong(0L);
        CachingMarketDataApi cache = new CachingMarketDataApi(api, 1_000L, now::get);

        cache.getDailyBars("AAPL", TODAY.minusDays(10), TODAY);
        now.set(999L);
        cache.getDailyBars("AAPL", TODAY.minusDays(10), TODAY);
        assertEquals(1, api.dailyCalls.size(), "still fresh");

        now.set(1_000L);
        cache.getDailyBars("AAPL", TODAY.minusDays(10), TODAY);
        assertEquals(2, api.dailyCalls.size(), "past the TTL the market may have moved");
    }

    @Test
    void intradayBarsAreReusedOnlyForTheIdenticalRequest() throws Exception {
        RecordingApi api = new RecordingApi();
        CachingMarketDataApi cache = new CachingMarketDataApi(api);

        cache.getIntradayBars("AAPL", TODAY.minusDays(7), TODAY, 15);
        cache.getIntradayBars("AAPL", TODAY.minusDays(7), TODAY, 15);
        assertEquals(1, api.intradayCalls.size());

        // A different interval is different data, never served from the same entry.
        cache.getIntradayBars("AAPL", TODAY.minusDays(7), TODAY, 1);
        assertEquals(2, api.intradayCalls.size());
    }

    @Test
    void aFailedFetchIsNotRememberedAsAnEmptyHistory() throws Exception {
        RecordingApi api = new RecordingApi();
        CachingMarketDataApi cache = new CachingMarketDataApi(api);
        api.failure = new AlpacaMarketDataException("Alpaca API rate limit exceeded (HTTP 429).");

        assertThrows(AlpacaMarketDataException.class, () -> cache.getDailyBars("AAPL", TODAY.minusDays(10), TODAY));

        api.failure = null;
        List<MarketBar> bars = cache.getDailyBars("AAPL", TODAY.minusDays(10), TODAY);

        assertTrue(bars.size() > 0, "the retry must really fetch, not return a cached failure");
        assertEquals(2, api.dailyCalls.size());
    }

    @Test
    void missingArgumentsArePassedStraightThroughRatherThanCached() throws Exception {
        RecordingApi api = new RecordingApi();
        CachingMarketDataApi cache = new CachingMarketDataApi(api);

        cache.getDailyBars("", TODAY.minusDays(5), TODAY);
        cache.getDailyBars("", TODAY.minusDays(5), TODAY);

        assertEquals(2, api.dailyCalls.size(), "nothing to key a cache on");
    }

    @Test
    void aBarWithAnUnreadableTimestampIsKeptRatherThanSilentlyDropped() throws Exception {
        RecordingApi api = new RecordingApi();
        api.extraBar = new MarketBar("AAPL", "not-a-timestamp", BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE);
        CachingMarketDataApi cache = new CachingMarketDataApi(api);
        cache.getDailyBars("AAPL", TODAY.minusDays(10), TODAY);

        List<MarketBar> slice = cache.getDailyBars("AAPL", TODAY.minusDays(2), TODAY);

        assertTrue(slice.stream().anyMatch(bar -> "not-a-timestamp".equals(bar.timestamp())));
    }

    @Test
    void theDelegateIsUsedUnchangedWhenNothingIsCachedYet() throws Exception {
        RecordingApi api = new RecordingApi();
        CachingMarketDataApi cache = new CachingMarketDataApi(api);

        List<MarketBar> first = cache.getIntradayBars("AAPL", TODAY.minusDays(1), TODAY, 5);

        assertEquals(1, api.intradayCalls.size());
        assertSame(first, cache.getIntradayBars("AAPL", TODAY.minusDays(1), TODAY, 5));
    }

    /** Returns one bar per day in the requested range, so coverage bugs show up as missing days. */
    private static final class RecordingApi implements AlpacaMarketDataApi {
        private final List<String> dailyCalls = new ArrayList<>();
        private final List<String> intradayCalls = new ArrayList<>();
        private AlpacaMarketDataException failure;
        private MarketBar extraBar;

        @Override
        public List<MarketBar> getDailyBars(String symbol, LocalDate startDate, LocalDate endDate)
                throws AlpacaMarketDataException {
            dailyCalls.add(symbol + " " + startDate + ".." + endDate);
            if (failure != null) {
                throw failure;
            }
            List<MarketBar> bars = new ArrayList<>();
            for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
                bars.add(bar(symbol, date));
            }
            if (extraBar != null) {
                bars.add(extraBar);
            }
            return bars;
        }

        @Override
        public List<MarketBar> getIntradayBars(String symbol, LocalDate startDate, LocalDate endDate, int intervalMinutes)
                throws AlpacaMarketDataException {
            intradayCalls.add(symbol + " " + startDate + ".." + endDate + " @" + intervalMinutes);
            if (failure != null) {
                throw failure;
            }
            return List.of(bar(symbol, endDate));
        }

        private static MarketBar bar(String symbol, LocalDate date) {
            return new MarketBar(symbol, date + "T00:00:00Z", BigDecimal.ONE, BigDecimal.ONE,
                    BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE);
        }
    }
}
