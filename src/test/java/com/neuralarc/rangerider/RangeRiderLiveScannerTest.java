package com.neuralarc.rangerider;

import com.neuralarc.api.AlpacaMarketDataApi;
import com.neuralarc.api.AlpacaMarketDataException;
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

class RangeRiderLiveScannerTest {
    private static final LocalDate TODAY = LocalDate.parse("2026-06-15");
    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-06-15T18:00:00Z"), ZoneOffset.UTC);

    @Test
    void averagesTheDailyOpenHighAndLowOverTheLookbackWindow() {
        RangeRiderLiveScanner scanner = new RangeRiderLiveScanner(new SteadyRangeFakeApi(), FIXED, ignored -> { });

        List<RangeRiderCandidate> candidates = scanner.candidates(List.of(" nvda "), 15);

        assertEquals(1, candidates.size());
        RangeRiderCandidate c = candidates.getFirst();
        assertEquals("NVDA", c.symbol());
        assertEquals(15, c.sessionsAnalyzed(), "only the requested number of completed sessions is averaged");
        assertEquals(new BigDecimal("100.00"), c.averageOpen());
        assertEquals(new BigDecimal("102.00"), c.averageHigh());
        assertEquals(new BigDecimal("98.00"), c.averageLow());
        // (102 - 98) / 98 * 100 = 4.08%
        assertEquals(new BigDecimal("4.08"), c.averageRangePercent());
        assertEquals(new BigDecimal("100.00"), c.rangeStabilityPercent(), "an identical range every day is perfectly stable");
    }

    @Test
    void expressesTheAveragesAsATypicalDipAndRallyAroundTheOpen() {
        RangeRiderLiveScanner scanner = new RangeRiderLiveScanner(new SteadyRangeFakeApi(), FIXED, ignored -> { });

        RangeRiderCandidate c = scanner.candidates(List.of("NVDA"), 15).getFirst();

        // Average open 100, average low 98, average high 102.
        assertEquals(new BigDecimal("2.0000"), c.averageDipPercent());
        assertEquals(new BigDecimal("2.0000"), c.averageRallyPercent());
    }

    @Test
    void ignoresTodaysFormingBarEntirely() {
        RangeRiderLiveScanner scanner = new RangeRiderLiveScanner(new SteadyRangeFakeApi(), FIXED, ignored -> { });

        RangeRiderCandidate c = scanner.candidates(List.of("NVDA"), 15).getFirst();

        // Today's bar prints a far wider 90/110 range and a $95 close; neither may reach the candidate.
        assertEquals(new BigDecimal("102.00"), c.averageHigh());
        assertEquals(new BigDecimal("98.00"), c.averageLow());
        assertEquals(new BigDecimal("100.00"), c.referencePrice(),
                "the anchor is the last completed close, never today's partial bar");
        assertTrue(c.sessions().stream().noneMatch(session -> session.date().isEqual(TODAY)));
    }

    @Test
    void producesTheSameCandidateWhetherOrNotTodayHasTradedYet() {
        RangeRiderCandidate withToday = new RangeRiderLiveScanner(new SteadyRangeFakeApi(), FIXED, ignored -> { })
                .candidates(List.of("NVDA"), 15).getFirst();
        RangeRiderCandidate beforeOpen = new RangeRiderLiveScanner(new NoTodayBarFakeApi(), FIXED, ignored -> { })
                .candidates(List.of("NVDA"), 15).getFirst();

        assertEquals(withToday.referencePrice(), beforeOpen.referencePrice());
        assertEquals(withToday.averageLow(), beforeOpen.averageLow());
        assertEquals(withToday.averageHigh(), beforeOpen.averageHigh());
        assertEquals(withToday.averageDipPercent(), beforeOpen.averageDipPercent());
    }

    @Test
    void keepsThePerSessionBreakdownForTheBacktest() {
        RangeRiderLiveScanner scanner = new RangeRiderLiveScanner(new SteadyRangeFakeApi(), FIXED, ignored -> { });

        RangeRiderCandidate c = scanner.candidates(List.of("NVDA"), 15).getFirst();

        assertEquals(15, c.sessions().size());
        RangeRiderSession first = c.sessions().getFirst();
        assertEquals(new BigDecimal("98"), first.low());
        assertEquals(new BigDecimal("102"), first.high());
        assertEquals(new BigDecimal("100"), first.open());
    }

    @Test
    void scoresAnErraticRangeAsLessStableThanASteadyOne() {
        RangeRiderLiveScanner scanner = new RangeRiderLiveScanner(new ErraticRangeFakeApi(), FIXED, ignored -> { });

        RangeRiderCandidate c = scanner.candidates(List.of("WILD"), 15).getFirst();

        assertTrue(c.rangeStabilityPercent().compareTo(new BigDecimal("100")) < 0,
                "alternating wide/narrow sessions are not a stable range");
        assertTrue(c.rangeStabilityPercent().compareTo(BigDecimal.ZERO) >= 0);
    }

    @Test
    void skipsSymbolsWithoutEnoughCompletedSessions() {
        List<String> log = new ArrayList<>();
        RangeRiderLiveScanner scanner = new RangeRiderLiveScanner(new ThinHistoryFakeApi(), FIXED, log::add);

        assertTrue(scanner.candidates(List.of("AMD"), 15).isEmpty());
        assertTrue(log.stream().anyMatch(line -> line.contains("Skipped AMD") && line.contains("completed session")));
    }

    @Test
    void skipsSymbolsTheMarketDataApiRejects() {
        List<String> log = new ArrayList<>();
        RangeRiderLiveScanner scanner = new RangeRiderLiveScanner(new FailingFakeApi(), FIXED, log::add);

        assertTrue(scanner.candidates(List.of("BAD"), 15).isEmpty());
        assertTrue(log.stream().anyMatch(line -> line.contains("Skipped BAD") && line.contains("no data entitlement")));
    }

    @Test
    void returnsNothingWithoutSymbols() {
        RangeRiderLiveScanner scanner = new RangeRiderLiveScanner(new SteadyRangeFakeApi(), FIXED, ignored -> { });

        assertTrue(scanner.candidates(List.of(), 15).isEmpty());
        assertTrue(scanner.candidates(null, 15).isEmpty());
    }

    @Test
    void parsesSymbolsWithoutInjectingDefaults() {
        assertEquals(List.of("AMD", "MSFT", "TSLA"), RangeRiderLiveScanner.parseSymbols(" amd, msft\nTSLA AMD "));
        assertTrue(RangeRiderLiveScanner.parseSymbols(" ").isEmpty());
        assertTrue(RangeRiderLiveScanner.parseSymbols(null).isEmpty());
    }

    /** 20 completed sessions with an identical 98–102 range, plus a much wider bar for today. */
    private static final class SteadyRangeFakeApi implements AlpacaMarketDataApi {
        @Override
        public List<MarketBar> getDailyBars(String symbol, LocalDate startDate, LocalDate endDate) {
            List<MarketBar> bars = new ArrayList<>();
            for (int i = 20; i >= 1; i--) {
                bars.add(bar(symbol, TODAY.minusDays(i) + "T20:00:00Z", "100", "102", "98", "100", "4000000"));
            }
            bars.add(bar(symbol, TODAY + "T20:00:00Z", "100", "110", "90", "95", "5000000"));
            return bars;
        }

        @Override
        public List<MarketBar> getIntradayBars(String symbol, LocalDate startDate, LocalDate endDate, int intervalMinutes) {
            return List.of();
        }
    }

    /** The same completed sessions, but the scan runs before today has printed a bar at all. */
    private static final class NoTodayBarFakeApi implements AlpacaMarketDataApi {
        @Override
        public List<MarketBar> getDailyBars(String symbol, LocalDate startDate, LocalDate endDate) {
            List<MarketBar> bars = new ArrayList<>();
            for (int i = 20; i >= 1; i--) {
                bars.add(bar(symbol, TODAY.minusDays(i) + "T20:00:00Z", "100", "102", "98", "100", "4000000"));
            }
            return bars;
        }

        @Override
        public List<MarketBar> getIntradayBars(String symbol, LocalDate startDate, LocalDate endDate, int intervalMinutes) {
            return List.of();
        }
    }

    /** Alternating wide and narrow sessions — the same average range, far less repeatable. */
    private static final class ErraticRangeFakeApi implements AlpacaMarketDataApi {
        @Override
        public List<MarketBar> getDailyBars(String symbol, LocalDate startDate, LocalDate endDate) {
            List<MarketBar> bars = new ArrayList<>();
            for (int i = 20; i >= 1; i--) {
                boolean wide = i % 2 == 0;
                bars.add(wide
                        ? bar(symbol, TODAY.minusDays(i) + "T20:00:00Z", "100", "106", "94", "100", "4000000")
                        : bar(symbol, TODAY.minusDays(i) + "T20:00:00Z", "100", "100.5", "99.5", "100", "4000000"));
            }
            return bars;
        }

        @Override
        public List<MarketBar> getIntradayBars(String symbol, LocalDate startDate, LocalDate endDate, int intervalMinutes) {
            return List.of();
        }
    }

    /** Only a handful of sessions — fewer than two thirds of the requested lookback. */
    private static final class ThinHistoryFakeApi implements AlpacaMarketDataApi {
        @Override
        public List<MarketBar> getDailyBars(String symbol, LocalDate startDate, LocalDate endDate) {
            List<MarketBar> bars = new ArrayList<>();
            for (int i = 4; i >= 1; i--) {
                bars.add(bar(symbol, TODAY.minusDays(i) + "T20:00:00Z", "100", "102", "98", "100", "4000000"));
            }
            return bars;
        }

        @Override
        public List<MarketBar> getIntradayBars(String symbol, LocalDate startDate, LocalDate endDate, int intervalMinutes) {
            return List.of();
        }
    }

    private static final class FailingFakeApi implements AlpacaMarketDataApi {
        @Override
        public List<MarketBar> getDailyBars(String symbol, LocalDate startDate, LocalDate endDate)
                throws AlpacaMarketDataException {
            throw new AlpacaMarketDataException("no data entitlement for " + symbol);
        }

        @Override
        public List<MarketBar> getIntradayBars(String symbol, LocalDate startDate, LocalDate endDate, int intervalMinutes) {
            return List.of();
        }
    }

    private static MarketBar bar(String symbol, String timestamp, String open, String high, String low, String close, String volume) {
        return new MarketBar(symbol, timestamp, new BigDecimal(open), new BigDecimal(high),
                new BigDecimal(low), new BigDecimal(close), new BigDecimal(volume));
    }
}
