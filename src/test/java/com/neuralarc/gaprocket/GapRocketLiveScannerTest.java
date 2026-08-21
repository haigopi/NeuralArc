package com.neuralarc.gaprocket;

import com.neuralarc.api.AlpacaMarketDataApi;
import com.neuralarc.model.MarketBar;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GapRocketLiveScannerTest {
    @Test
    void buildsCandidatesFromRequestedLiveSymbolsOnly() {
        FakeMarketDataApi api = new FakeMarketDataApi();
        GapRocketLiveScanner scanner = new GapRocketLiveScanner(
                api,
                Clock.fixed(Instant.parse("2026-06-13T14:00:00Z"), ZoneOffset.UTC),
                ignored -> { },
                0L,
                ignored -> { }
        );

        List<GapRocketCandidate> candidates = scanner.candidates(List.of(" amd ", "AMD", "msft"));

        // SPY and QQQ are fetched once for the market-trend check, then AMD and MSFT for the candidates.
        assertEquals(List.of("SPY", "QQQ", "AMD", "MSFT"), api.dailyRequests);
        assertEquals(List.of("SPY", "QQQ", "AMD", "MSFT"), api.intradayRequests);
        assertEquals(List.of("AMD", "MSFT"), candidates.stream().map(GapRocketCandidate::symbol).toList());
        assertTrue(candidates.stream().allMatch(candidate -> candidate.currentPrice().compareTo(BigDecimal.ZERO) > 0));
    }

    @Test
    void spyQqqGreenFlagsReflectLiveIndexData() {
        // SPY is red (current 95 < prev close 100); QQQ and AAPL are green (current 112 > prev close 100).
        GapRocketLiveScanner scanner = new GapRocketLiveScanner(
                new RedSpyFakeApi(),
                Clock.fixed(Instant.parse("2026-06-13T14:00:00Z"), ZoneOffset.UTC),
                ignored -> { },
                0L,
                ignored -> { }
        );

        List<GapRocketCandidate> candidates = scanner.candidates(List.of("AAPL"));

        assertFalse(candidates.isEmpty());
        assertFalse(candidates.getFirst().spyGreen(), "SPY should be red when price fell below prev close");
        assertTrue(candidates.getFirst().qqqGreen(), "QQQ should be green when price is above prev close");
    }

    @Test
    void skipsSymbolsWithNoBarNewerThanThePreviousSessionInsteadOfReportingAZeroGap() {
        // Premarket on the IEX feed: no intraday bars, and today has not printed a daily bar yet.
        // The newest bar available IS the prior session's, so a gap cannot be measured. Reporting
        // 0.00% here would surface as a bogus "gap below minimum" rejection for every symbol.
        List<String> log = new ArrayList<>();
        GapRocketLiveScanner scanner = new GapRocketLiveScanner(
                new PremarketNoDataFakeApi(),
                Clock.fixed(Instant.parse("2026-08-21T13:07:00Z"), ZoneOffset.UTC), // 9:07 ET, premarket
                log::add, 0L, ignored -> { });

        List<GapRocketCandidate> candidates = scanner.candidates(List.of("AIFU"));

        assertTrue(candidates.isEmpty(), "no live price means no candidate, not a 0% gap");
        assertTrue(log.stream().anyMatch(line -> line.contains("Skipped AIFU")
                        && line.contains("no premarket or session data yet")),
                "the log must name the missing data, not blame the gap filter: " + log);
    }

    @Test
    void stillBuildsCandidatesOncePremarketBarsExist() {
        // Same setup, but the feed now has premarket prints at 11.50 against a 10.00 prior close.
        GapRocketLiveScanner scanner = new GapRocketLiveScanner(
                new PremarketWithBarsFakeApi(),
                Clock.fixed(Instant.parse("2026-08-21T13:07:00Z"), ZoneOffset.UTC),
                ignored -> { }, 0L, ignored -> { });

        List<GapRocketCandidate> candidates = scanner.candidates(List.of("AIFU"));

        assertEquals(1, candidates.size());
        assertEquals(new BigDecimal("15.00"), candidates.getFirst().gapPercent());
        assertEquals(new BigDecimal("11.50"), candidates.getFirst().currentPrice());
        assertEquals(new BigDecimal("10.00"), candidates.getFirst().previousClose());
    }

    @Test
    void datesTheSessionInUsEasternRatherThanTheOperatorsLocalDay() {
        // 2026-08-22T01:30Z is still 2026-08-21 21:30 ET. A scanner keyed off a local (UTC) day
        // would look for "2026-08-22" bars and treat the 08-21 session bar as the previous day.
        GapRocketLiveScanner scanner = new GapRocketLiveScanner(
                new SessionClosedFakeApi(),
                Clock.fixed(Instant.parse("2026-08-22T01:30:00Z"), ZoneOffset.UTC),
                ignored -> { }, 0L, ignored -> { });

        List<GapRocketCandidate> candidates = scanner.candidates(List.of("AIFU"));

        assertEquals(1, candidates.size());
        // Today (ET) is 08-21 whose close is 11.50; the prior session 08-20 closed at 10.00.
        assertEquals(new BigDecimal("11.50"), candidates.getFirst().currentPrice());
        assertEquals(new BigDecimal("10.00"), candidates.getFirst().previousClose());
    }

    /** Premarket: no intraday bars and no daily bar for today. */
    private static final class PremarketNoDataFakeApi implements AlpacaMarketDataApi {
        @Override
        public List<MarketBar> getDailyBars(String symbol, LocalDate startDate, LocalDate endDate) {
            return List.of(
                    dayBar(symbol, "2026-08-19", "10.00"),
                    dayBar(symbol, "2026-08-20", "10.00"));
        }

        @Override
        public List<MarketBar> getIntradayBars(String symbol, LocalDate s, LocalDate e, int interval) {
            return List.of();
        }
    }

    /** Premarket with real prints: intraday bars exist for today. */
    private static final class PremarketWithBarsFakeApi implements AlpacaMarketDataApi {
        @Override
        public List<MarketBar> getDailyBars(String symbol, LocalDate startDate, LocalDate endDate) {
            return List.of(
                    dayBar(symbol, "2026-08-19", "10.00"),
                    dayBar(symbol, "2026-08-20", "10.00"));
        }

        @Override
        public List<MarketBar> getIntradayBars(String symbol, LocalDate s, LocalDate e, int interval) {
            return List.of(new MarketBar(symbol, "2026-08-21T12:00:00Z", new BigDecimal("11.00"),
                    new BigDecimal("11.60"), new BigDecimal("10.90"), new BigDecimal("11.50"),
                    new BigDecimal("250000")));
        }
    }

    /** After the close on 2026-08-21 ET: today's daily bar exists, no intraday bars returned. */
    private static final class SessionClosedFakeApi implements AlpacaMarketDataApi {
        @Override
        public List<MarketBar> getDailyBars(String symbol, LocalDate startDate, LocalDate endDate) {
            return List.of(
                    dayBar(symbol, "2026-08-20", "10.00"),
                    dayBar(symbol, "2026-08-21", "11.50"));
        }

        @Override
        public List<MarketBar> getIntradayBars(String symbol, LocalDate s, LocalDate e, int interval) {
            return List.of();
        }
    }

    private static MarketBar dayBar(String symbol, String date, String close) {
        return new MarketBar(symbol, date + "T20:00:00Z", new BigDecimal(close), new BigDecimal(close),
                new BigDecimal(close), new BigDecimal(close), new BigDecimal("1000000"));
    }

    @Test
    void parsesSymbolsWithoutInjectingDefaults() {
        assertEquals(List.of("AMD", "MSFT", "TSLA"), GapRocketLiveScanner.parseSymbols(" amd, msft\nTSLA AMD "));
        assertTrue(GapRocketLiveScanner.parseSymbols(" ").isEmpty());
    }

    @Test
    void pacesEveryMarketDataRequestAfterTheFirst() {
        FakeMarketDataApi api = new FakeMarketDataApi();
        List<Long> delays = new ArrayList<>();
        GapRocketLiveScanner scanner = new GapRocketLiveScanner(
                api,
                Clock.fixed(Instant.parse("2026-06-13T14:00:00Z"), ZoneOffset.UTC),
                ignored -> { },
                750L,
                delays::add
        );

        scanner.candidates(List.of("AMD", "MSFT"));

        // SPY, QQQ, AMD, and MSFT each require daily + intraday bars: 8 requests, 7 waits.
        assertEquals(7, delays.size());
        assertTrue(delays.stream().allMatch(delay -> delay == 750L));
    }

    /** SPY is red (current < prev close); all other symbols are green. */
    private static final class RedSpyFakeApi implements AlpacaMarketDataApi {
        @Override
        public List<MarketBar> getDailyBars(String symbol, LocalDate startDate, LocalDate endDate) {
            if ("SPY".equals(symbol)) {
                return List.of(
                        bar(symbol, "2026-06-12T20:00:00Z", "100", "105", "98", "100", "1000000"),
                        bar(symbol, "2026-06-13T14:00:00Z", "97",  "98",  "93", "95",  "800000")
                );
            }
            return List.of(
                    bar(symbol, "2026-06-12T20:00:00Z", "100", "105", "98", "100", "1000000"),
                    bar(symbol, "2026-06-13T14:00:00Z", "108", "112", "107", "110", "250000")
            );
        }

        @Override
        public List<MarketBar> getIntradayBars(String symbol, LocalDate startDate, LocalDate endDate, int intervalMinutes) {
            if ("SPY".equals(symbol)) {
                return List.of(bar(symbol, "2026-06-13T13:32:00Z", "96", "97", "93", "95", "400000"));
            }
            return List.of(
                    bar(symbol, "2026-06-13T13:31:00Z", "108", "111", "107", "110", "125000"),
                    bar(symbol, "2026-06-13T13:32:00Z", "110", "113", "109", "112", "150000")
            );
        }

        private MarketBar bar(String symbol, String timestamp, String open, String high, String low, String close, String volume) {
            return new MarketBar(symbol, timestamp, new BigDecimal(open), new BigDecimal(high),
                    new BigDecimal(low), new BigDecimal(close), new BigDecimal(volume));
        }
    }

    private static final class FakeMarketDataApi implements AlpacaMarketDataApi {
        private final List<String> dailyRequests = new ArrayList<>();
        private final List<String> intradayRequests = new ArrayList<>();

        @Override
        public List<MarketBar> getDailyBars(String symbol, LocalDate startDate, LocalDate endDate) {
            dailyRequests.add(symbol);
            return List.of(
                    bar(symbol, "2026-06-12T20:00:00Z", "100", "105", "98", "100", "1000000"),
                    bar(symbol, "2026-06-13T14:00:00Z", "108", "112", "107", "110", "250000")
            );
        }

        @Override
        public List<MarketBar> getIntradayBars(String symbol, LocalDate startDate, LocalDate endDate, int intervalMinutes) {
            intradayRequests.add(symbol);
            return List.of(
                    bar(symbol, "2026-06-13T13:31:00Z", "108", "111", "107", "110", "125000"),
                    bar(symbol, "2026-06-13T13:32:00Z", "110", "113", "109", "112", "150000")
            );
        }

        private MarketBar bar(String symbol, String timestamp, String open, String high, String low, String close, String volume) {
            return new MarketBar(symbol, timestamp, new BigDecimal(open), new BigDecimal(high),
                    new BigDecimal(low), new BigDecimal(close), new BigDecimal(volume));
        }
    }
}
