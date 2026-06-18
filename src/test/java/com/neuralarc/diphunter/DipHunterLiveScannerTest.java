package com.neuralarc.diphunter;

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

class DipHunterLiveScannerTest {

    @Test
    void buildsPullbackCandidateFromLiveBars() {
        DipHunterLiveScanner scanner = new DipHunterLiveScanner(
                new PullbackFakeApi(),
                Clock.fixed(Instant.parse("2026-06-15T18:00:00Z"), ZoneOffset.UTC),
                ignored -> { });

        List<DipHunterCandidate> candidates = scanner.candidates(List.of(" nvda "));

        assertEquals(1, candidates.size());
        DipHunterCandidate c = candidates.getFirst();
        assertEquals("NVDA", c.symbol());
        // Recent 20-day high is 110, current price is 100 → ~9.09% pullback.
        assertEquals(new BigDecimal("9.09"), c.pullbackPercent());
        assertTrue(c.aboveMa20(), "current 100 should be above the ~95 MA20");
        assertTrue(c.aboveMa50(), "current 100 should be above the ~95 MA50");
        assertTrue(c.intradayReversal(), "current 100 is well above the intraday low of 99");
        assertTrue(c.currentPrice().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(c.relativeVolume().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void skipsSymbolsWithoutEnoughDailyHistory() {
        DipHunterLiveScanner scanner = new DipHunterLiveScanner(
                new ThinHistoryFakeApi(),
                Clock.fixed(Instant.parse("2026-06-15T18:00:00Z"), ZoneOffset.UTC),
                ignored -> { });

        assertTrue(scanner.candidates(List.of("AMD")).isEmpty());
    }

    @Test
    void parsesSymbolsWithoutInjectingDefaults() {
        assertEquals(List.of("AMD", "MSFT", "TSLA"), DipHunterLiveScanner.parseSymbols(" amd, msft\nTSLA AMD "));
        assertTrue(DipHunterLiveScanner.parseSymbols(" ").isEmpty());
    }

    /** Returns 60 prior daily bars (closes ~95, one recent high of 110) plus a current intraday price of 100. */
    private static final class PullbackFakeApi implements AlpacaMarketDataApi {
        @Override
        public List<MarketBar> getDailyBars(String symbol, LocalDate startDate, LocalDate endDate) {
            List<MarketBar> bars = new ArrayList<>();
            LocalDate today = LocalDate.parse("2026-06-15");
            for (int i = 60; i >= 1; i--) {
                LocalDate date = today.minusDays(i);
                // One bar in the recent 20-day window spikes to a high of 110 (the recent high).
                String high = i == 10 ? "110" : "96";
                bars.add(bar(symbol, date + "T20:00:00Z", "95", high, "94", "95", "1000000"));
            }
            // Today's daily bar (excluded from history by date).
            bars.add(bar(symbol, today + "T20:00:00Z", "100", "101", "99", "100", "1200000"));
            return bars;
        }

        @Override
        public List<MarketBar> getIntradayBars(String symbol, LocalDate startDate, LocalDate endDate, int intervalMinutes) {
            return List.of(
                    bar(symbol, "2026-06-15T14:00:00Z", "99", "100", "99", "99.50", "700000"),
                    bar(symbol, "2026-06-15T14:30:00Z", "99.50", "100.50", "99", "100", "800000")
            );
        }
    }

    /** Only a handful of daily bars — not enough for the recent-high window. */
    private static final class ThinHistoryFakeApi implements AlpacaMarketDataApi {
        @Override
        public List<MarketBar> getDailyBars(String symbol, LocalDate startDate, LocalDate endDate) {
            return List.of(
                    bar(symbol, "2026-06-11T20:00:00Z", "95", "96", "94", "95", "1000000"),
                    bar(symbol, "2026-06-12T20:00:00Z", "95", "96", "94", "95", "1000000"));
        }

        @Override
        public List<MarketBar> getIntradayBars(String symbol, LocalDate startDate, LocalDate endDate, int intervalMinutes) {
            return List.of(bar(symbol, "2026-06-15T14:00:00Z", "99", "100", "99", "100", "700000"));
        }
    }

    private static MarketBar bar(String symbol, String timestamp, String open, String high, String low, String close, String volume) {
        return new MarketBar(symbol, timestamp, new BigDecimal(open), new BigDecimal(high),
                new BigDecimal(low), new BigDecimal(close), new BigDecimal(volume));
    }
}
