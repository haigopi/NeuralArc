package com.neuralarc.diphunter;

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
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Builds Dip Hunter candidates exclusively from operator-supplied symbols and live Alpaca market data.
 * Computes the pullback off the recent high, moving-average trend confirmation, relative volume, and
 * an intraday reversal signal. No hardcoded tickers, canned prices, or synthetic candidates.
 */
public final class DipHunterLiveScanner {
    private static final int LOOKBACK_DAYS = 80;            // enough sessions for a 50-day MA
    private static final int RECENT_HIGH_WINDOW = 20;       // recent high over ~one month
    private static final BigDecimal REVERSAL_MARGIN = new BigDecimal("0.005"); // 0.5% bounce off the low

    private final AlpacaMarketDataApi marketDataApi;
    private final Clock clock;
    private final Consumer<String> log;

    public DipHunterLiveScanner(AlpacaMarketDataApi marketDataApi, Clock clock, Consumer<String> log) {
        this.marketDataApi = Objects.requireNonNull(marketDataApi, "marketDataApi");
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
        this.log = log == null ? ignored -> { } : log;
    }

    public List<DipHunterCandidate> candidates(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return List.of();
        }
        LocalDate today = LocalDate.now(clock);
        List<DipHunterCandidate> candidates = new ArrayList<>();
        for (String symbol : symbols.stream()
                .map(DipHunterLiveScanner::normalizeSymbol)
                .filter(normalized -> !normalized.isBlank())
                .distinct()
                .toList()) {
            try {
                buildCandidate(symbol, today).ifPresent(candidates::add);
            } catch (AlpacaMarketDataException ex) {
                log.accept("[Dip Hunter] Skipped " + symbol + ": " + ex.getMessage());
            }
        }
        return candidates;
    }

    private Optional<DipHunterCandidate> buildCandidate(String symbol, LocalDate today) throws AlpacaMarketDataException {
        List<MarketBar> dailyBars = marketDataApi.getDailyBars(symbol, today.minusDays(LOOKBACK_DAYS), today);
        List<MarketBar> intradayBars = marketDataApi.getIntradayBars(symbol, today, today, 5);
        List<MarketBar> history = priorDailyBars(dailyBars, today);
        if (history.size() < RECENT_HIGH_WINDOW) {
            log.accept("[Dip Hunter] Skipped " + symbol + ": Alpaca did not return enough daily history.");
            return Optional.empty();
        }
        MarketBar previousDaily = history.get(history.size() - 1);
        MarketBar latestBar = latestBar(intradayBars, dailyBars);
        if (latestBar == null || previousDaily.close().compareTo(BigDecimal.ZERO) <= 0) {
            log.accept("[Dip Hunter] Skipped " + symbol + ": missing live price data.");
            return Optional.empty();
        }
        BigDecimal current = valid(latestBar.close()) ? latestBar.close() : latestBar.open();
        if (!valid(current)) {
            log.accept("[Dip Hunter] Skipped " + symbol + ": latest Alpaca price was missing.");
            return Optional.empty();
        }
        BigDecimal recentHigh = recentHigh(history);
        if (!valid(recentHigh)) {
            return Optional.empty();
        }
        BigDecimal pullbackPercent = recentHigh.subtract(current)
                .multiply(BigDecimal.valueOf(100)).divide(recentHigh, 2, RoundingMode.HALF_UP);
        BigDecimal dayChangePercent = current.subtract(previousDaily.close())
                .multiply(BigDecimal.valueOf(100)).divide(previousDaily.close(), 2, RoundingMode.HALF_UP);
        BigDecimal ma20 = movingAverage(history, 20);
        BigDecimal ma50 = movingAverage(history, 50);
        BigDecimal avgVolume = averageVolume(history, 20);
        BigDecimal todayVolume = sumVolume(intradayBars);
        BigDecimal relativeVolume = valid(avgVolume)
                ? todayVolume.divide(avgVolume, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal intradayHigh = maxHigh(intradayBars, latestBar.high());
        BigDecimal intradayLow = minLow(intradayBars, latestBar.low());
        boolean intradayReversal = valid(intradayLow)
                && current.compareTo(intradayLow.multiply(BigDecimal.ONE.add(REVERSAL_MARGIN))) >= 0;
        BigDecimal spreadPercent = valid(current) && intradayHigh.compareTo(intradayLow) > 0
                ? intradayHigh.subtract(intradayLow).multiply(BigDecimal.valueOf(100)).divide(current, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        return Optional.of(new DipHunterCandidate(
                symbol,
                symbol,
                pullbackPercent,
                dayChangePercent,
                avgVolume.longValue(),
                relativeVolume,
                Monetary.round(current),
                Monetary.round(previousDaily.close()),
                Monetary.round(recentHigh),
                valid(ma20) ? Monetary.round(ma20) : BigDecimal.ZERO,
                valid(ma50) ? Monetary.round(ma50) : BigDecimal.ZERO,
                valid(ma20) && current.compareTo(ma20) > 0,
                valid(ma50) && current.compareTo(ma50) > 0,
                intradayReversal,
                spreadPercent,
                Monetary.round(current)
        ));
    }

    private List<MarketBar> priorDailyBars(List<MarketBar> bars, LocalDate today) {
        if (bars == null) return List.of();
        return bars.stream().filter(bar -> !barDate(bar).isEqual(today)).toList();
    }

    private MarketBar latestBar(List<MarketBar> intradayBars, List<MarketBar> dailyBars) {
        if (intradayBars != null && !intradayBars.isEmpty()) {
            return intradayBars.get(intradayBars.size() - 1);
        }
        return dailyBars == null || dailyBars.isEmpty() ? null : dailyBars.get(dailyBars.size() - 1);
    }

    private BigDecimal recentHigh(List<MarketBar> history) {
        return history.stream()
                .skip(Math.max(0, history.size() - RECENT_HIGH_WINDOW))
                .map(MarketBar::high)
                .filter(this::valid)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal movingAverage(List<MarketBar> history, int period) {
        if (history.size() < period) return BigDecimal.ZERO;
        List<MarketBar> window = history.subList(history.size() - period, history.size());
        BigDecimal sum = window.stream().map(MarketBar::close).reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal averageVolume(List<MarketBar> history, int period) {
        if (history.isEmpty()) return BigDecimal.ZERO;
        int count = Math.min(period, history.size());
        List<MarketBar> window = history.subList(history.size() - count, history.size());
        BigDecimal sum = window.stream().map(MarketBar::volume).reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal sumVolume(List<MarketBar> bars) {
        if (bars == null) return BigDecimal.ZERO;
        return bars.stream().map(MarketBar::volume).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal maxHigh(List<MarketBar> bars, BigDecimal fallback) {
        return bars == null || bars.isEmpty() ? fallback : bars.stream().map(MarketBar::high).max(BigDecimal::compareTo).orElse(fallback);
    }

    private BigDecimal minLow(List<MarketBar> bars, BigDecimal fallback) {
        return bars == null || bars.isEmpty() ? fallback : bars.stream().map(MarketBar::low).min(BigDecimal::compareTo).orElse(fallback);
    }

    private LocalDate barDate(MarketBar bar) {
        String timestamp = bar == null ? "" : bar.timestamp();
        if (timestamp == null || timestamp.length() < 10) return LocalDate.MIN;
        return LocalDate.parse(timestamp.substring(0, 10));
    }

    private boolean valid(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    public static List<String> parseSymbols(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return java.util.Arrays.stream(raw.split("[,\\s]+"))
                .map(DipHunterLiveScanner::normalizeSymbol)
                .filter(symbol -> !symbol.isBlank())
                .distinct()
                .toList();
    }

    private static String normalizeSymbol(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    }
}
