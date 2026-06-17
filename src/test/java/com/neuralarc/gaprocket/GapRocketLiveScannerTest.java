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
                ignored -> { }
        );

        List<GapRocketCandidate> candidates = scanner.candidates(List.of("AAPL"));

        assertFalse(candidates.isEmpty());
        assertFalse(candidates.getFirst().spyGreen(), "SPY should be red when price fell below prev close");
        assertTrue(candidates.getFirst().qqqGreen(), "QQQ should be green when price is above prev close");
    }

    @Test
    void parsesSymbolsWithoutInjectingDefaults() {
        assertEquals(List.of("AMD", "MSFT", "TSLA"), GapRocketLiveScanner.parseSymbols(" amd, msft\nTSLA AMD "));
        assertTrue(GapRocketLiveScanner.parseSymbols(" ").isEmpty());
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
