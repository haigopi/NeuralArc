package com.neuralarc.profitshield;

import com.neuralarc.api.AlpacaMarketDataApi;
import com.neuralarc.api.AlpacaMarketDataException;
import com.neuralarc.model.MarketBar;
import com.neuralarc.model.StrategyMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfitShieldLiveScannerTest {
    private static final LocalDate TODAY = LocalDate.parse("2026-06-15");
    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-06-15T18:00:00Z"), ZoneOffset.UTC);

    private static ProfitShieldConfig config() {
        return ProfitShieldConfig.defaults(StrategyMode.PAPER);
    }

    @Test
    void measuresAQuietFlatNameAsLowVolatilityWithNoDrawdown() {
        ProfitShieldLiveScanner scanner = new ProfitShieldLiveScanner(new FlatFakeApi(), FIXED, ignored -> { });

        List<ProfitShieldCandidate> candidates = scanner.candidates(List.of(" msft "), config());

        assertEquals(1, candidates.size());
        ProfitShieldCandidate c = candidates.getFirst();
        assertEquals("MSFT", c.symbol());
        // Every completed session prints 99.50–100.50 around a $100 close: a $1.00 true range on $100.
        assertEquals(new BigDecimal("1.00"), c.atrPercent());
        assertEquals(new BigDecimal("1.00"), c.maxDrawdownPercent());
        assertEquals(new BigDecimal("0.50"), c.distanceFromHighPercent());
        assertEquals(new BigDecimal("100.00"), c.upSessionsPercent(), "every session closes at its open");
    }

    @Test
    void measuresTheDeepestPeakToTroughDeclineOverTheLookback() {
        ProfitShieldLiveScanner scanner = new ProfitShieldLiveScanner(new DrawdownFakeApi(), FIXED, ignored -> { });

        ProfitShieldCandidate c = scanner.candidates(List.of("DEEP"), config()).getFirst();

        // The series peaks at a $120 high and later prints an $84 low: (120 - 84) / 120 = 30%.
        assertEquals(new BigDecimal("30.00"), c.maxDrawdownPercent());
    }

    @Test
    void ignoresTodaysFormingBarForEveryHistoricalStatistic() {
        ProfitShieldCandidate withToday = new ProfitShieldLiveScanner(new FlatFakeApi(), FIXED, ignored -> { })
                .candidates(List.of("MSFT"), config()).getFirst();
        ProfitShieldCandidate beforeOpen = new ProfitShieldLiveScanner(new NoTodayBarFakeApi(), FIXED, ignored -> { })
                .candidates(List.of("MSFT"), config()).getFirst();

        // Today's bar in FlatFakeApi prints a wild 80/130 range; none of it may reach the statistics.
        assertEquals(beforeOpen.atrPercent(), withToday.atrPercent());
        assertEquals(beforeOpen.maxDrawdownPercent(), withToday.maxDrawdownPercent());
        assertEquals(beforeOpen.ma50(), withToday.ma50());
    }

    @Test
    void readsTheTrendStackFromTheMovingAverages() {
        ProfitShieldLiveScanner scanner = new ProfitShieldLiveScanner(new UptrendFakeApi(), FIXED, ignored -> { });

        ProfitShieldCandidate c = scanner.candidates(List.of("UP"), config()).getFirst();

        assertTrue(c.aboveMa50());
        assertTrue(c.aboveMa200());
        assertTrue(c.risingTrendStack());
        assertTrue(c.ma50().compareTo(c.ma200()) > 0);
    }

    @Test
    void marksABrokenTrendWhenPriceSitsBelowItsAverages() {
        ProfitShieldLiveScanner scanner = new ProfitShieldLiveScanner(new DowntrendFakeApi(), FIXED, ignored -> { });

        ProfitShieldCandidate c = scanner.candidates(List.of("DOWN"), config()).getFirst();

        assertFalse(c.aboveMa50());
        assertFalse(c.aboveMa200());
        assertFalse(c.risingTrendStack());
    }

    @Test
    void anchorsTheSupportShelfBelowTheCurrentPrice() {
        ProfitShieldLiveScanner scanner = new ProfitShieldLiveScanner(new FlatFakeApi(), FIXED, ignored -> { });

        ProfitShieldCandidate c = scanner.candidates(List.of("MSFT"), config()).getFirst();

        assertTrue(c.supportPrice().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(c.supportPrice().compareTo(c.currentPrice()) < 0);
        // The 20-session low of $99.50 is nearer than the flat $100 average, so it is the shelf.
        assertEquals(new BigDecimal("99.50"), c.supportPrice());
    }

    @Test
    void honorsTheConfiguredDrawdownLookbackWindow() {
        // A 20-session window cannot see the old spike-and-slide that a 126-session window measures.
        ProfitShieldConfig shortWindow = new ProfitShieldConfig(20, new BigDecimal("3"), new BigDecimal("20"),
                new BigDecimal("12"), 300_000L, new BigDecimal("5"), null,
                ProfitShieldConfig.TrendFilter.ABOVE_MA_50_AND_200, new BigDecimal("1"), new BigDecimal("3"),
                new BigDecimal("6"), 10, StrategyMode.PAPER, List.of());
        ProfitShieldLiveScanner scanner = new ProfitShieldLiveScanner(new DrawdownFakeApi(), FIXED, ignored -> { });

        ProfitShieldCandidate wide = scanner.candidates(List.of("DEEP"), config()).getFirst();
        ProfitShieldCandidate narrow = scanner.candidates(List.of("DEEP"), shortWindow).getFirst();

        assertEquals(20, narrow.sessionsAnalyzed());
        assertTrue(narrow.maxDrawdownPercent().compareTo(wide.maxDrawdownPercent()) < 0);
    }

    @Test
    void skipsSymbolsWithoutEnoughDailyHistory() {
        List<String> log = new ArrayList<>();
        ProfitShieldLiveScanner scanner = new ProfitShieldLiveScanner(new ThinHistoryFakeApi(), FIXED, log::add);

        assertTrue(scanner.candidates(List.of("AMD"), config()).isEmpty());
        assertTrue(log.stream().anyMatch(line -> line.contains("Skipped AMD") && line.contains("daily history")));
    }

    @Test
    void skipsSymbolsTheMarketDataApiRejects() {
        List<String> log = new ArrayList<>();
        ProfitShieldLiveScanner scanner = new ProfitShieldLiveScanner(new FailingFakeApi(), FIXED, log::add);

        assertTrue(scanner.candidates(List.of("BAD"), config()).isEmpty());
        assertTrue(log.stream().anyMatch(line -> line.contains("Skipped BAD") && line.contains("no data entitlement")));
    }

    @Test
    void returnsNothingWithoutSymbols() {
        ProfitShieldLiveScanner scanner = new ProfitShieldLiveScanner(new FlatFakeApi(), FIXED, ignored -> { });

        assertTrue(scanner.candidates(List.of(), config()).isEmpty());
        assertTrue(scanner.candidates(null, config()).isEmpty());
    }

    @Test
    void parsesSymbolsWithoutInjectingDefaults() {
        assertEquals(List.of("AMD", "MSFT", "TSLA"), ProfitShieldLiveScanner.parseSymbols(" amd, msft\nTSLA AMD "));
        assertTrue(ProfitShieldLiveScanner.parseSymbols(" ").isEmpty());
        assertTrue(ProfitShieldLiveScanner.parseSymbols(null).isEmpty());
    }

    // ---------------------------------------------------------------------

    /** 260 completed flat sessions at $100 (99.50–100.50), plus a wild forming bar for today. */
    private static final class FlatFakeApi implements AlpacaMarketDataApi {
        @Override
        public List<MarketBar> getDailyBars(String symbol, LocalDate startDate, LocalDate endDate) {
            List<MarketBar> bars = flatHistory(symbol);
            bars.add(bar(symbol, TODAY, "100", "130", "80", "100", "9000000"));
            return bars;
        }

        @Override
        public List<MarketBar> getIntradayBars(String symbol, LocalDate s, LocalDate e, int interval) { return List.of(); }
    }

    /** The same completed sessions, scanned before today has printed a bar at all. */
    private static final class NoTodayBarFakeApi implements AlpacaMarketDataApi {
        @Override
        public List<MarketBar> getDailyBars(String symbol, LocalDate startDate, LocalDate endDate) {
            List<MarketBar> bars = flatHistory(symbol);
            bars.add(bar(symbol, TODAY, "100", "100.50", "99.50", "100", "4000000"));
            return bars;
        }

        @Override
        public List<MarketBar> getIntradayBars(String symbol, LocalDate s, LocalDate e, int interval) { return List.of(); }
    }

    /** Flat, then an old spike to $120 followed by a slide to $84, then flat again near the end. */
    private static final class DrawdownFakeApi implements AlpacaMarketDataApi {
        @Override
        public List<MarketBar> getDailyBars(String symbol, LocalDate startDate, LocalDate endDate) {
            List<MarketBar> bars = new ArrayList<>();
            for (int i = 260; i >= 1; i--) {
                if (i == 120) {
                    bars.add(bar(symbol, TODAY.minusDays(i), "115", "120", "114", "118", "4000000"));
                } else if (i == 110) {
                    bars.add(bar(symbol, TODAY.minusDays(i), "90", "92", "84", "86", "4000000"));
                } else {
                    bars.add(bar(symbol, TODAY.minusDays(i), "100", "100.50", "99.50", "100", "4000000"));
                }
            }
            bars.add(bar(symbol, TODAY, "100", "100.50", "99.50", "100", "4000000"));
            return bars;
        }

        @Override
        public List<MarketBar> getIntradayBars(String symbol, LocalDate s, LocalDate e, int interval) { return List.of(); }
    }

    /** A steadily rising series so price leads the 50-day, which leads the 200-day. */
    private static final class UptrendFakeApi implements AlpacaMarketDataApi {
        @Override
        public List<MarketBar> getDailyBars(String symbol, LocalDate startDate, LocalDate endDate) {
            List<MarketBar> bars = new ArrayList<>();
            for (int i = 260; i >= 0; i--) {
                BigDecimal close = new BigDecimal(100 + (260 - i));
                bars.add(new MarketBar(symbol, TODAY.minusDays(i) + "T20:00:00Z", close,
                        close.add(BigDecimal.ONE), close.subtract(BigDecimal.ONE), close, new BigDecimal("4000000")));
            }
            return bars;
        }

        @Override
        public List<MarketBar> getIntradayBars(String symbol, LocalDate s, LocalDate e, int interval) { return List.of(); }
    }

    /** A steadily falling series so price trails both averages. */
    private static final class DowntrendFakeApi implements AlpacaMarketDataApi {
        @Override
        public List<MarketBar> getDailyBars(String symbol, LocalDate startDate, LocalDate endDate) {
            List<MarketBar> bars = new ArrayList<>();
            for (int i = 260; i >= 0; i--) {
                BigDecimal close = new BigDecimal(400 - (260 - i));
                bars.add(new MarketBar(symbol, TODAY.minusDays(i) + "T20:00:00Z", close,
                        close.add(BigDecimal.ONE), close.subtract(BigDecimal.ONE), close, new BigDecimal("4000000")));
            }
            return bars;
        }

        @Override
        public List<MarketBar> getIntradayBars(String symbol, LocalDate s, LocalDate e, int interval) { return List.of(); }
    }

    /** Fewer sessions than the scanner's minimum history requirement. */
    private static final class ThinHistoryFakeApi implements AlpacaMarketDataApi {
        @Override
        public List<MarketBar> getDailyBars(String symbol, LocalDate startDate, LocalDate endDate) {
            List<MarketBar> bars = new ArrayList<>();
            for (int i = 10; i >= 0; i--) {
                bars.add(bar(symbol, TODAY.minusDays(i), "100", "100.50", "99.50", "100", "4000000"));
            }
            return bars;
        }

        @Override
        public List<MarketBar> getIntradayBars(String symbol, LocalDate s, LocalDate e, int interval) { return List.of(); }
    }

    private static final class FailingFakeApi implements AlpacaMarketDataApi {
        @Override
        public List<MarketBar> getDailyBars(String symbol, LocalDate startDate, LocalDate endDate)
                throws AlpacaMarketDataException {
            throw new AlpacaMarketDataException("no data entitlement for " + symbol);
        }

        @Override
        public List<MarketBar> getIntradayBars(String symbol, LocalDate s, LocalDate e, int interval) { return List.of(); }
    }

    private static List<MarketBar> flatHistory(String symbol) {
        List<MarketBar> bars = new ArrayList<>();
        for (int i = 260; i >= 1; i--) {
            bars.add(bar(symbol, TODAY.minusDays(i), "100", "100.50", "99.50", "100", "4000000"));
        }
        return bars;
    }

    private static MarketBar bar(String symbol, LocalDate date, String open, String high, String low, String close, String volume) {
        return new MarketBar(symbol, date + "T20:00:00Z", new BigDecimal(open), new BigDecimal(high),
                new BigDecimal(low), new BigDecimal(close), new BigDecimal(volume));
    }
}
