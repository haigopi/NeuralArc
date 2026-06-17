package com.neuralarc.gaprocket;

import com.neuralarc.api.AlpacaMarketDataApi;
import com.neuralarc.api.AlpacaMarketDataException;
import com.neuralarc.model.MarketBar;
import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Builds Gap Rocket candidates exclusively from operator-supplied symbols and live Alpaca market data.
 */
public final class GapRocketLiveScanner {
    private final AlpacaMarketDataApi marketDataApi;
    private final Clock clock;
    private final Consumer<String> log;

    public GapRocketLiveScanner(AlpacaMarketDataApi marketDataApi, Clock clock, Consumer<String> log) {
        this.marketDataApi = Objects.requireNonNull(marketDataApi, "marketDataApi");
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
        this.log = log == null ? ignored -> { } : log;
    }

    public List<GapRocketCandidate> candidates(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return List.of();
        }
        LocalDate today = LocalDate.now(clock);
        boolean spyGreen = isIndexGreen("SPY", today);
        boolean qqqGreen = isIndexGreen("QQQ", today);
        List<GapRocketCandidate> candidates = new ArrayList<>();
        for (String symbol : symbols.stream()
                .map(GapRocketLiveScanner::normalizeSymbol)
                .filter(normalized -> !normalized.isBlank())
                .distinct()
                .toList()) {
            try {
                buildCandidate(symbol, today, spyGreen, qqqGreen).ifPresent(candidates::add);
            } catch (AlpacaMarketDataException ex) {
                log.accept("[Gap Rocket] Skipped " + symbol + ": " + ex.getMessage());
            }
        }
        return candidates;
    }

    private boolean isIndexGreen(String symbol, LocalDate today) {
        try {
            List<MarketBar> dailyBars = marketDataApi.getDailyBars(symbol, today.minusDays(5), today);
            List<MarketBar> intradayBars = marketDataApi.getIntradayBars(symbol, today, today, 1);
            MarketBar prevDay = previousDailyBar(dailyBars, today);
            MarketBar latest = latestBar(intradayBars, dailyBars);
            if (prevDay == null || latest == null || prevDay.close().compareTo(BigDecimal.ZERO) <= 0) return true;
            BigDecimal current = valid(latest.close()) ? latest.close() : latest.open();
            return valid(current) && current.compareTo(prevDay.close()) > 0;
        } catch (AlpacaMarketDataException ex) {
            log.accept("[Gap Rocket] Could not fetch " + symbol + " for trend check; assuming green: " + ex.getMessage());
            return true;
        }
    }

    private java.util.Optional<GapRocketCandidate> buildCandidate(String symbol, LocalDate today, boolean spyGreen, boolean qqqGreen) throws AlpacaMarketDataException {
        List<MarketBar> dailyBars = marketDataApi.getDailyBars(symbol, today.minusDays(20), today);
        List<MarketBar> intradayBars = marketDataApi.getIntradayBars(symbol, today, today, 1);
        MarketBar previousDaily = previousDailyBar(dailyBars, today);
        MarketBar latestBar = latestBar(intradayBars, dailyBars);
        if (previousDaily == null || latestBar == null || previousDaily.close().compareTo(BigDecimal.ZERO) <= 0) {
            log.accept("[Gap Rocket] Skipped " + symbol + ": Alpaca did not return enough live bars.");
            return java.util.Optional.empty();
        }
        BigDecimal current = valid(latestBar.close()) ? latestBar.close() : latestBar.open();
        if (!valid(current)) {
            log.accept("[Gap Rocket] Skipped " + symbol + ": latest Alpaca price was missing.");
            return java.util.Optional.empty();
        }
        BigDecimal high = maxHigh(intradayBars, latestBar.high());
        BigDecimal low = minLow(intradayBars, latestBar.low());
        BigDecimal volume = sumVolume(intradayBars);
        BigDecimal avgVolume = averageDailyVolume(dailyBars, today);
        BigDecimal gapPercent = current.subtract(previousDaily.close())
                .multiply(new BigDecimal("100"))
                .divide(previousDaily.close(), 2, RoundingMode.HALF_UP);
        BigDecimal relativeVolume = valid(avgVolume)
                ? volume.divide(avgVolume, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal spreadPercent = valid(current) && high.compareTo(low) > 0
                ? high.subtract(low).multiply(new BigDecimal("100")).divide(current, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        return java.util.Optional.of(new GapRocketCandidate(
                symbol,
                symbol,
                gapPercent,
                volume.longValue(),
                relativeVolume,
                Monetary.round(current),
                Monetary.round(previousDaily.close()),
                Monetary.round(high),
                Monetary.round(low),
                null,
                "Live Alpaca market-data scan; no hardcoded ticker or price was used.",
                spyGreen,
                qqqGreen,
                spreadPercent,
                volume.compareTo(BigDecimal.ZERO) > 0,
                Monetary.round(current)
        ));
    }

    private MarketBar previousDailyBar(List<MarketBar> bars, LocalDate today) {
        if (bars == null || bars.isEmpty()) return null;
        return bars.stream()
                .filter(bar -> !barDate(bar).isEqual(today))
                .reduce((first, second) -> second)
                .orElse(bars.size() > 1 ? bars.get(bars.size() - 2) : null);
    }

    private MarketBar latestBar(List<MarketBar> intradayBars, List<MarketBar> dailyBars) {
        if (intradayBars != null && !intradayBars.isEmpty()) {
            return intradayBars.get(intradayBars.size() - 1);
        }
        return dailyBars == null || dailyBars.isEmpty() ? null : dailyBars.get(dailyBars.size() - 1);
    }

    private LocalDate barDate(MarketBar bar) {
        String timestamp = bar == null ? "" : bar.timestamp();
        if (timestamp == null || timestamp.length() < 10) return LocalDate.MIN;
        return LocalDate.parse(timestamp.substring(0, 10));
    }

    private BigDecimal maxHigh(List<MarketBar> bars, BigDecimal fallback) {
        return bars == null || bars.isEmpty() ? fallback : bars.stream().map(MarketBar::high).max(BigDecimal::compareTo).orElse(fallback);
    }

    private BigDecimal minLow(List<MarketBar> bars, BigDecimal fallback) {
        return bars == null || bars.isEmpty() ? fallback : bars.stream().map(MarketBar::low).min(BigDecimal::compareTo).orElse(fallback);
    }

    private BigDecimal sumVolume(List<MarketBar> bars) {
        if (bars == null) return BigDecimal.ZERO;
        return bars.stream().map(MarketBar::volume).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal averageDailyVolume(List<MarketBar> bars, LocalDate today) {
        if (bars == null) return BigDecimal.ZERO;
        List<BigDecimal> volumes = bars.stream().filter(bar -> !barDate(bar).isEqual(today)).map(MarketBar::volume).toList();
        if (volumes.isEmpty()) return BigDecimal.ZERO;
        return volumes.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(new BigDecimal(volumes.size()), 2, RoundingMode.HALF_UP);
    }

    private boolean valid(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    public static List<String> parseSymbols(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return java.util.Arrays.stream(raw.split("[,\\s]+"))
                .map(GapRocketLiveScanner::normalizeSymbol)
                .filter(symbol -> !symbol.isBlank())
                .distinct()
                .toList();
    }

    private static String normalizeSymbol(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    }
}
