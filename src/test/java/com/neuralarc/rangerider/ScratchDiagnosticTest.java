package com.neuralarc.rangerider;

import com.neuralarc.api.AlpacaMarketDataApi;
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
import java.util.Random;

/** TEMPORARY diagnostic — prints what the analyzer does with realistic price data. */
class ScratchDiagnosticTest {
    private static final LocalDate TODAY = LocalDate.parse("2026-06-15");
    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-06-15T18:00:00Z"), ZoneOffset.UTC);

    @Test
    void diagnose() {
        List<RangeRiderCandidate> candidates = new ArrayList<>();
        candidates.add(scan("RANGEBOUND", new Fake(175, 0.0, 0.020, 7)));
        candidates.add(scan("TRENDUP", new Fake(170, 0.010, 0.020, 11)));
        candidates.add(scan("WIDE", new Fake(60, 0.0, 0.045, 3)));
        candidates.add(scan("MEGACAP", new Fake(230, 0.002, 0.013, 5)));

        RangeRiderConfig cfg = RangeRiderConfig.defaults(StrategyMode.PAPER);
        RangeRiderAnalyzer analyzer = new RangeRiderAnalyzer(FIXED, System.out::println);

        for (RangeRiderCandidate c : candidates) {
            if (c == null) continue;
            BigDecimal entry = analyzer.plannedEntryPrice(c, cfg);
            BigDecimal target = analyzer.plannedTargetPrice(c, cfg);
            BigDecimal fill = analyzer.sameDayFillRatePercent(c, cfg);
            System.out.println("=== " + c.symbol()
                    + " avgLow=" + c.averageLow() + " avgOpen=" + c.averageOpen() + " avgHigh=" + c.averageHigh()
                    + " avgRange%=" + c.averageRangePercent() + " dip%=" + c.averageDipPercent()
                    + " rally%=" + c.averageRallyPercent() + " stability=" + c.rangeStabilityPercent()
                    + " ref=" + c.referencePrice()
                    + " || entry=" + entry + " target=" + target
                    + " touchRate=" + analyzer.entryTouchRatePercent(c, cfg)
                    + " fillRate=" + fill
                    + " score=" + analyzer.score(c, cfg, fill));
        }
        System.out.println(">>> RECOMMENDED: " + analyzer.analyze(candidates, cfg).size());
    }

    private RangeRiderCandidate scan(String symbol, Fake api) {
        List<RangeRiderCandidate> result = new RangeRiderLiveScanner(api, FIXED, System.out::println)
                .candidates(List.of(symbol), 15);
        return result.isEmpty() ? null : result.getFirst();
    }

    /** Synthetic but realistic daily bars: a base price, a daily drift, a daily range %, and noise. */
    private static final class Fake implements AlpacaMarketDataApi {
        private final double base;
        private final double drift;
        private final double rangePct;
        private final long seed;

        Fake(double base, double drift, double rangePct, long seed) {
            this.base = base;
            this.drift = drift;
            this.rangePct = rangePct;
            this.seed = seed;
        }

        @Override
        public List<MarketBar> getDailyBars(String symbol, LocalDate startDate, LocalDate endDate) {
            Random random = new Random(seed);
            List<MarketBar> bars = new ArrayList<>();
            double price = base;
            for (int i = 20; i >= 0; i--) {
                price = price * (1 + drift + (random.nextDouble() - 0.5) * 0.012);
                double range = price * rangePct * (0.75 + random.nextDouble() * 0.5);
                double low = price - range * random.nextDouble();
                double high = low + range;
                double open = low + range * random.nextDouble();
                double close = low + range * random.nextDouble();
                bars.add(new MarketBar(symbol, TODAY.minusDays(i) + "T20:00:00Z",
                        bd(open), bd(high), bd(low), bd(close), new BigDecimal("4000000")));
            }
            return bars;
        }

        @Override
        public List<MarketBar> getIntradayBars(String s, LocalDate a, LocalDate b, int i) {
            return List.of();
        }

        private static BigDecimal bd(double v) {
            return BigDecimal.valueOf(Math.round(v * 100) / 100.0);
        }
    }
}
