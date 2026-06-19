package com.neuralarc.vwap;

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
 * Builds VWAP Desk candidates exclusively from operator-supplied symbols and live Alpaca market data.
 * Computes the intraday VWAP from today's bars, the discount of the current price below VWAP, the
 * 50/200-day moving-average trend, relative volume, and the intraday spread. No hardcoded tickers,
 * canned prices, or synthetic candidates.
 */
public final class VwapLiveScanner {
    private static final int LOOKBACK_DAYS = 300;      // enough sessions for a 200-day MA
    private static final int INTRADAY_INTERVAL_MIN = 5;

    private final AlpacaMarketDataApi marketDataApi;
    private final Clock clock;
    private final Consumer<String> log;

    public VwapLiveScanner(AlpacaMarketDataApi marketDataApi, Clock clock, Consumer<String> log) {
        this.marketDataApi = Objects.requireNonNull(marketDataApi, "marketDataApi");
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
        this.log = log == null ? ignored -> { } : log;
    }

    public List<VwapCandidate> candidates(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return List.of();
        }
        LocalDate today = LocalDate.now(clock);
        List<VwapCandidate> candidates = new ArrayList<>();
        for (String symbol : symbols.stream()
                .map(VwapLiveScanner::normalizeSymbol)
                .filter(normalized -> !normalized.isBlank())
                .distinct()
                .toList()) {
            try {
                buildCandidate(symbol, today).ifPresent(candidates::add);
            } catch (AlpacaMarketDataException ex) {
                log.accept("[VWAP Desk] Skipped " + symbol + ": " + ex.getMessage());
            }
        }
        return candidates;
    }

    private Optional<VwapCandidate> buildCandidate(String symbol, LocalDate today) throws AlpacaMarketDataException {
        List<MarketBar> dailyBars = marketDataApi.getDailyBars(symbol, today.minusDays(LOOKBACK_DAYS), today);
        List<MarketBar> intradayBars = marketDataApi.getIntradayBars(symbol, today, today, INTRADAY_INTERVAL_MIN);
        List<MarketBar> history = priorDailyBars(dailyBars, today);
        if (history.size() < 50) {
            log.accept("[VWAP Desk] Skipped " + symbol + ": Alpaca did not return enough daily history.");
            return Optional.empty();
        }
        if (intradayBars == null || intradayBars.isEmpty()) {
            log.accept("[VWAP Desk] Skipped " + symbol + ": no intraday bars to compute VWAP.");
            return Optional.empty();
        }
        MarketBar previousDaily = history.get(history.size() - 1);
        MarketBar latestBar = intradayBars.get(intradayBars.size() - 1);
        BigDecimal current = valid(latestBar.close()) ? latestBar.close() : latestBar.open();
        if (!valid(current) || previousDaily.close().compareTo(BigDecimal.ZERO) <= 0) {
            log.accept("[VWAP Desk] Skipped " + symbol + ": missing live price data.");
            return Optional.empty();
        }
        BigDecimal vwap = computeVwap(intradayBars);
        if (!valid(vwap)) {
            log.accept("[VWAP Desk] Skipped " + symbol + ": VWAP could not be computed (no traded volume).");
            return Optional.empty();
        }
        BigDecimal discountPercent = vwap.subtract(current)
                .multiply(BigDecimal.valueOf(100)).divide(vwap, 2, RoundingMode.HALF_UP);
        BigDecimal dayChangePercent = current.subtract(previousDaily.close())
                .multiply(BigDecimal.valueOf(100)).divide(previousDaily.close(), 2, RoundingMode.HALF_UP);
        BigDecimal ma50 = movingAverage(history, 50);
        BigDecimal ma200 = movingAverage(history, 200);
        BigDecimal avgVolume = averageVolume(history, 20);
        BigDecimal todayVolume = sumVolume(intradayBars);
        BigDecimal relativeVolume = valid(avgVolume)
                ? todayVolume.divide(avgVolume, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal intradayHigh = maxHigh(intradayBars, latestBar.high());
        BigDecimal intradayLow = minLow(intradayBars, latestBar.low());
        BigDecimal spreadPercent = valid(current) && intradayHigh.compareTo(intradayLow) > 0
                ? intradayHigh.subtract(intradayLow).multiply(BigDecimal.valueOf(100)).divide(current, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        return Optional.of(new VwapCandidate(
                symbol,
                symbol,
                Monetary.round(current),
                Monetary.round(vwap),
                discountPercent,
                Monetary.round(previousDaily.close()),
                dayChangePercent,
                avgVolume.longValue(),
                relativeVolume,
                valid(ma50) ? Monetary.round(ma50) : BigDecimal.ZERO,
                valid(ma200) ? Monetary.round(ma200) : BigDecimal.ZERO,
                valid(ma50) && current.compareTo(ma50) > 0,
                valid(ma200) && current.compareTo(ma200) > 0,
                spreadPercent
        ));
    }

    /** Volume-weighted average of each intraday bar's typical price (high+low+close)/3. */
    private BigDecimal computeVwap(List<MarketBar> bars) {
        BigDecimal pvSum = BigDecimal.ZERO;
        BigDecimal volSum = BigDecimal.ZERO;
        for (MarketBar bar : bars) {
            BigDecimal volume = bar.volume() == null ? BigDecimal.ZERO : bar.volume();
            if (volume.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal typical = bar.high().add(bar.low()).add(bar.close())
                    .divide(BigDecimal.valueOf(3), 6, RoundingMode.HALF_UP);
            pvSum = pvSum.add(typical.multiply(volume));
            volSum = volSum.add(volume);
        }
        if (volSum.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return pvSum.divide(volSum, 4, RoundingMode.HALF_UP);
    }

    private List<MarketBar> priorDailyBars(List<MarketBar> bars, LocalDate today) {
        if (bars == null) return List.of();
        return bars.stream().filter(bar -> !barDate(bar).isEqual(today)).toList();
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
                .map(VwapLiveScanner::normalizeSymbol)
                .filter(symbol -> !symbol.isBlank())
                .distinct()
                .toList();
    }

    private static String normalizeSymbol(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    }
}
