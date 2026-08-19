package com.neuralarc.profitshield;

import com.neuralarc.api.AlpacaMarketDataApi;
import com.neuralarc.api.AlpacaMarketDataException;
import com.neuralarc.model.MarketBar;
import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Builds Profit Shield candidates from live Alpaca daily-bar market data only. Profit Shield is a
 * defensive, multi-day book, so it works on the daily chart: it measures the 14-day ATR as a percent
 * of price (how quiet the name is), the deepest peak-to-trough drawdown across the lookback window,
 * how far the price still sits below that window's high, the share of sessions that closed green, the
 * 20/50/200-day moving-average stack, and the nearest support shelf below price. No hardcoded
 * tickers, canned prices, or synthetic candidates.
 *
 * <p>Today's forming bar supplies the current price only; every historical statistic is computed from
 * completed sessions so a scan run mid-session matches one run before the open.
 */
public final class ProfitShieldLiveScanner {
    private static final int LOOKBACK_DAYS = 400;   // enough sessions for a 200-day MA plus buffer
    private static final int ATR_PERIOD = 14;
    private static final int SUPPORT_LOOKBACK = 20;
    private static final int MINIMUM_HISTORY = 60;

    private final AlpacaMarketDataApi marketDataApi;
    private final Clock clock;
    private final Consumer<String> log;

    public ProfitShieldLiveScanner(AlpacaMarketDataApi marketDataApi, Clock clock, Consumer<String> log) {
        this.marketDataApi = Objects.requireNonNull(marketDataApi, "marketDataApi");
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
        this.log = log == null ? ignored -> { } : log;
    }

    public List<ProfitShieldCandidate> candidates(List<String> symbols, ProfitShieldConfig config) {
        if (symbols == null || symbols.isEmpty()) {
            return List.of();
        }
        ProfitShieldConfig safeConfig = config == null ? ProfitShieldConfig.defaults(null) : config;
        LocalDate today = LocalDate.now(clock);
        List<ProfitShieldCandidate> candidates = new ArrayList<>();
        for (String symbol : symbols.stream()
                .map(ProfitShieldLiveScanner::normalizeSymbol)
                .filter(normalized -> !normalized.isBlank())
                .distinct()
                .toList()) {
            try {
                buildCandidate(symbol, today, safeConfig).ifPresent(candidates::add);
            } catch (AlpacaMarketDataException ex) {
                log.accept("[Profit Shield] Skipped " + symbol + ": " + ex.getMessage());
            }
        }
        return candidates;
    }

    private Optional<ProfitShieldCandidate> buildCandidate(String symbol, LocalDate today, ProfitShieldConfig config)
            throws AlpacaMarketDataException {
        List<MarketBar> dailyBars = marketDataApi.getDailyBars(symbol, today.minusDays(LOOKBACK_DAYS), today);
        if (dailyBars == null || dailyBars.size() < MINIMUM_HISTORY + 1) {
            log.accept("[Profit Shield] Skipped " + symbol + ": Alpaca did not return enough daily history.");
            return Optional.empty();
        }
        MarketBar latest = dailyBars.get(dailyBars.size() - 1);
        List<MarketBar> history = dailyBars.subList(0, dailyBars.size() - 1); // exclude the latest/forming bar
        MarketBar previousDaily = history.get(history.size() - 1);
        BigDecimal current = valid(latest.close()) ? latest.close() : latest.open();
        if (!valid(current) || !valid(previousDaily.close())) {
            log.accept("[Profit Shield] Skipped " + symbol + ": missing live price data.");
            return Optional.empty();
        }
        List<MarketBar> window = tail(history, config.drawdownLookbackSessions());
        BigDecimal windowHigh = window.stream().map(MarketBar::high).filter(this::valid)
                .max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        if (!valid(windowHigh)) {
            log.accept("[Profit Shield] Skipped " + symbol + ": could not establish a lookback high.");
            return Optional.empty();
        }
        BigDecimal atr = averageTrueRange(history);
        BigDecimal atrPercent = percentOf(atr, current);
        BigDecimal maxDrawdownPercent = maxDrawdownPercent(window);
        BigDecimal distanceFromHighPercent = windowHigh.subtract(current)
                .multiply(BigDecimal.valueOf(100)).divide(windowHigh, 2, RoundingMode.HALF_UP)
                .max(BigDecimal.ZERO);
        BigDecimal upSessionsPercent = upSessionsPercent(window);
        BigDecimal dayChangePercent = current.subtract(previousDaily.close())
                .multiply(BigDecimal.valueOf(100)).divide(previousDaily.close(), 2, RoundingMode.HALF_UP);
        BigDecimal ma20 = movingAverage(history, 20);
        BigDecimal ma50 = movingAverage(history, 50);
        BigDecimal ma200 = movingAverage(history, 200);
        BigDecimal avgVolume = averageVolume(history);
        BigDecimal relativeVolume = valid(avgVolume) && valid(latest.volume())
                ? latest.volume().divide(avgVolume, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal supportPrice = supportPrice(current, ma50, tail(history, SUPPORT_LOOKBACK));
        return Optional.of(new ProfitShieldCandidate(
                symbol,
                symbol,
                Monetary.round(current),
                Monetary.round(previousDaily.close()),
                dayChangePercent,
                avgVolume.longValue(),
                relativeVolume,
                valid(ma20) ? Monetary.round(ma20) : BigDecimal.ZERO,
                valid(ma50) ? Monetary.round(ma50) : BigDecimal.ZERO,
                valid(ma200) ? Monetary.round(ma200) : BigDecimal.ZERO,
                valid(ma50) && current.compareTo(ma50) > 0,
                valid(ma200) && current.compareTo(ma200) > 0,
                valid(ma50) && valid(ma200) && ma50.compareTo(ma200) > 0,
                atrPercent,
                maxDrawdownPercent,
                distanceFromHighPercent,
                upSessionsPercent,
                Monetary.round(supportPrice),
                window.size()
        ));
    }

    /**
     * Deepest peak-to-trough decline across the window, in percent. Walks the window forward tracking
     * the running high so an early spike followed by a slide is measured from that spike.
     */
    private BigDecimal maxDrawdownPercent(List<MarketBar> window) {
        BigDecimal peak = BigDecimal.ZERO;
        BigDecimal worst = BigDecimal.ZERO;
        for (MarketBar bar : window) {
            if (valid(bar.high()) && bar.high().compareTo(peak) > 0) {
                peak = bar.high();
            }
            if (!valid(peak) || !valid(bar.low())) {
                continue;
            }
            BigDecimal drawdown = peak.subtract(bar.low())
                    .multiply(BigDecimal.valueOf(100)).divide(peak, 2, RoundingMode.HALF_UP);
            if (drawdown.compareTo(worst) > 0) {
                worst = drawdown;
            }
        }
        return worst;
    }

    /** Share of the window's sessions that closed at or above their open — a plain resilience read. */
    private BigDecimal upSessionsPercent(List<MarketBar> window) {
        if (window.isEmpty()) {
            return BigDecimal.ZERO;
        }
        long up = window.stream()
                .filter(bar -> valid(bar.open()) && valid(bar.close()) && bar.close().compareTo(bar.open()) >= 0)
                .count();
        return BigDecimal.valueOf(up).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(window.size()), 2, RoundingMode.HALF_UP);
    }

    /**
     * Nearest structural shelf below the current price: the higher of the 50-day average and the
     * recent session low. Returns zero when neither sits below price, so the analyzer falls back to
     * the flat percent stop.
     */
    private BigDecimal supportPrice(BigDecimal current, BigDecimal ma50, List<MarketBar> recent) {
        BigDecimal recentLow = recent.stream().map(MarketBar::low).filter(this::valid)
                .min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal shelf = BigDecimal.ZERO;
        if (valid(ma50) && ma50.compareTo(current) < 0) {
            shelf = ma50;
        }
        if (valid(recentLow) && recentLow.compareTo(current) < 0 && recentLow.compareTo(shelf) > 0) {
            shelf = recentLow;
        }
        return shelf;
    }

    private BigDecimal movingAverage(List<MarketBar> history, int period) {
        if (history.size() < period) return BigDecimal.ZERO;
        List<MarketBar> window = history.subList(history.size() - period, history.size());
        BigDecimal sum = window.stream().map(MarketBar::close).reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal averageVolume(List<MarketBar> history) {
        List<MarketBar> window = tail(history, 20);
        if (window.isEmpty()) return BigDecimal.ZERO;
        BigDecimal sum = window.stream().map(MarketBar::volume).reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(window.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal averageTrueRange(List<MarketBar> history) {
        if (history.size() < 2) return BigDecimal.ZERO;
        int count = Math.min(ATR_PERIOD, history.size() - 1);
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = history.size() - count; i < history.size(); i++) {
            MarketBar bar = history.get(i);
            BigDecimal prevClose = history.get(i - 1).close();
            BigDecimal highLow = bar.high().subtract(bar.low()).abs();
            BigDecimal highClose = bar.high().subtract(prevClose).abs();
            BigDecimal lowClose = bar.low().subtract(prevClose).abs();
            sum = sum.add(highLow.max(highClose).max(lowClose));
        }
        return sum.divide(BigDecimal.valueOf(count), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal percentOf(BigDecimal value, BigDecimal base) {
        if (!valid(value) || !valid(base)) return BigDecimal.ZERO;
        return value.multiply(BigDecimal.valueOf(100)).divide(base, 2, RoundingMode.HALF_UP);
    }

    private static List<MarketBar> tail(List<MarketBar> bars, int count) {
        int size = Math.min(Math.max(count, 0), bars.size());
        return bars.subList(bars.size() - size, bars.size());
    }

    private boolean valid(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    public static List<String> parseSymbols(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.split("[,\\s]+"))
                .map(ProfitShieldLiveScanner::normalizeSymbol)
                .filter(symbol -> !symbol.isBlank())
                .distinct()
                .toList();
    }

    private static String normalizeSymbol(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    }
}
