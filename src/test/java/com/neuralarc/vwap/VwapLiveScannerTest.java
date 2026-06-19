package com.neuralarc.vwap;

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

class VwapLiveScannerTest {

    @Test
    void buildsDiscountCandidateFromLiveBars() {
        VwapLiveScanner scanner = new VwapLiveScanner(
                new DiscountFakeApi(),
                Clock.fixed(Instant.parse("2026-06-15T18:00:00Z"), ZoneOffset.UTC),
                ignored -> { });

        List<VwapCandidate> candidates = scanner.candidates(List.of(" nvda "));

        assertEquals(1, candidates.size());
        VwapCandidate c = candidates.getFirst();
        assertEquals("NVDA", c.symbol());
        assertEquals(new BigDecimal("100.00"), c.currentPrice());
        // VWAP is the volume-weighted typical price of the two intraday bars (> current price).
        assertTrue(c.vwap().compareTo(c.currentPrice()) > 0, "VWAP should sit above the current price");
        assertTrue(c.discountPercent().compareTo(BigDecimal.ZERO) > 0, "price is below VWAP → positive discount");
        assertTrue(c.aboveMa50(), "current 100 should be above the ~95 MA50");
        assertTrue(c.relativeVolume().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void skipsSymbolsWithoutEnoughDailyHistory() {
        VwapLiveScanner scanner = new VwapLiveScanner(
                new ThinHistoryFakeApi(),
                Clock.fixed(Instant.parse("2026-06-15T18:00:00Z"), ZoneOffset.UTC),
                ignored -> { });

        assertTrue(scanner.candidates(List.of("AMD")).isEmpty());
    }

    @Test
    void parsesSymbolsWithoutInjectingDefaults() {
        assertEquals(List.of("AMD", "MSFT", "TSLA"), VwapLiveScanner.parseSymbols(" amd, msft\nTSLA AMD "));
        assertTrue(VwapLiveScanner.parseSymbols(" ").isEmpty());
    }

    /** 60 prior daily bars (closes ~95) plus two intraday bars whose VWAP is above the current price of 100. */
    private static final class DiscountFakeApi implements AlpacaMarketDataApi {
        @Override
        public List<MarketBar> getDailyBars(String symbol, LocalDate startDate, LocalDate endDate) {
            List<MarketBar> bars = new ArrayList<>();
            LocalDate today = LocalDate.parse("2026-06-15");
            for (int i = 60; i >= 1; i--) {
                LocalDate date = today.minusDays(i);
                bars.add(bar(symbol, date + "T20:00:00Z", "95", "96", "94", "95", "1000000"));
            }
            bars.add(bar(symbol, today + "T20:00:00Z", "104", "105", "100", "100", "1200000"));
            return bars;
        }

        @Override
        public List<MarketBar> getIntradayBars(String symbol, LocalDate startDate, LocalDate endDate, int intervalMinutes) {
            return List.of(
                    bar(symbol, "2026-06-15T14:00:00Z", "105", "105", "103", "104", "100000"),
                    bar(symbol, "2026-06-15T14:30:00Z", "104", "104", "100", "100", "100000")
            );
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
            return List.of(bar(symbol, "2026-06-15T14:00:00Z", "104", "105", "100", "100", "100000"));
        }
    }

    private static MarketBar bar(String symbol, String timestamp, String open, String high, String low, String close, String volume) {
        return new MarketBar(symbol, timestamp, new BigDecimal(open), new BigDecimal(high),
                new BigDecimal(low), new BigDecimal(close), new BigDecimal(volume));
    }
}
