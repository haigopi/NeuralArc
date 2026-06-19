package com.neuralarc.swing;

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

class SwingLiveScannerTest {

    @Test
    void buildsPullbackCandidateFromLiveDailyBars() {
        SwingLiveScanner scanner = new SwingLiveScanner(
                new PullbackFakeApi(),
                Clock.fixed(Instant.parse("2026-06-15T18:00:00Z"), ZoneOffset.UTC),
                ignored -> { });

        List<SwingCandidate> candidates = scanner.candidates(List.of(" nvda "));

        assertEquals(1, candidates.size());
        SwingCandidate c = candidates.getFirst();
        assertEquals("NVDA", c.symbol());
        assertEquals(new BigDecimal("100.00"), c.currentPrice());
        // Recent high (110) sits above the current price → a positive pullback.
        assertEquals(new BigDecimal("110.00"), c.recentHigh());
        assertTrue(c.pullbackPercent().compareTo(BigDecimal.ZERO) > 0, "price below recent high → positive pullback");
        assertTrue(c.aboveMa50(), "current 100 should be above the ~96 MA50");
        assertTrue(c.relativeVolume().compareTo(BigDecimal.ZERO) > 0);
        assertEquals(new BigDecimal("96.00"), c.previousClose());
    }

    @Test
    void skipsSymbolsWithoutEnoughDailyHistory() {
        SwingLiveScanner scanner = new SwingLiveScanner(
                new ThinHistoryFakeApi(),
                Clock.fixed(Instant.parse("2026-06-15T18:00:00Z"), ZoneOffset.UTC),
                ignored -> { });

        assertTrue(scanner.candidates(List.of("AMD")).isEmpty());
    }

    @Test
    void parsesSymbolsWithoutInjectingDefaults() {
        assertEquals(List.of("AMD", "MSFT", "TSLA"), SwingLiveScanner.parseSymbols(" amd, msft\nTSLA AMD "));
        assertTrue(SwingLiveScanner.parseSymbols(" ").isEmpty());
    }

    /** 60 prior daily bars (close 96, high 110) plus today's bar closing at 100 below the recent high. */
    private static final class PullbackFakeApi implements AlpacaMarketDataApi {
        @Override
        public List<MarketBar> getDailyBars(String symbol, LocalDate startDate, LocalDate endDate) {
            List<MarketBar> bars = new ArrayList<>();
            LocalDate today = LocalDate.parse("2026-06-15");
            for (int i = 60; i >= 1; i--) {
                LocalDate date = today.minusDays(i);
                bars.add(bar(symbol, date + "T20:00:00Z", "96", "110", "94", "96", "1000000"));
            }
            bars.add(bar(symbol, today + "T20:00:00Z", "99", "101", "98", "100", "1200000"));
            return bars;
        }

        @Override
        public List<MarketBar> getIntradayBars(String symbol, LocalDate startDate, LocalDate endDate, int intervalMinutes) {
            return List.of();
        }
    }

    /** Only a handful of daily bars — not enough for the moving-average windows. */
    private static final class ThinHistoryFakeApi implements AlpacaMarketDataApi {
        @Override
        public List<MarketBar> getDailyBars(String symbol, LocalDate startDate, LocalDate endDate) {
            return List.of(
                    bar(symbol, "2026-06-11T20:00:00Z", "95", "96", "94", "95", "1000000"),
                    bar(symbol, "2026-06-12T20:00:00Z", "95", "96", "94", "95", "1000000"));
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
