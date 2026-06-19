package com.neuralarc.swing;

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
 * Builds Swing Vault candidates exclusively from operator-supplied symbols and live Alpaca daily-bar
 * market data. Swing Vault is a multi-day setup, so it works on the daily chart only: it computes the
 * recent swing high and the pullback from it, the 20/50/200-day moving-average trend stack, the
 * distance to rising 50-day support, relative volume, and a 14-day ATR for context. No hardcoded
 * tickers, canned prices, or synthetic candidates.
 */
public final class SwingLiveScanner {
    private static final int LOOKBACK_DAYS = 400;      // enough sessions for a 200-day MA plus buffer
    private static final int RECENT_HIGH_LOOKBACK = 40; // ~two months of sessions for the swing high
    private static final int ATR_PERIOD = 14;
    private static final int MINIMUM_HISTORY = 50;

    private final AlpacaMarketDataApi marketDataApi;
    private final Clock clock;
    private final Consumer<String> log;

    public SwingLiveScanner(AlpacaMarketDataApi marketDataApi, Clock clock, Consumer<String> log) {
        this.marketDataApi = Objects.requireNonNull(marketDataApi, "marketDataApi");
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
        this.log = log == null ? ignored -> { } : log;
    }

    public List<SwingCandidate> candidates(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return List.of();
        }
        LocalDate today = LocalDate.now(clock);
        List<SwingCandidate> candidates = new ArrayList<>();
        for (String symbol : symbols.stream()
                .map(SwingLiveScanner::normalizeSymbol)
                .filter(normalized -> !normalized.isBlank())
                .distinct()
                .toList()) {
            try {
                buildCandidate(symbol, today).ifPresent(candidates::add);
            } catch (AlpacaMarketDataException ex) {
                log.accept("[Swing Vault] Skipped " + symbol + ": " + ex.getMessage());
            }
        }
        return candidates;
    }

    private Optional<SwingCandidate> buildCandidate(String symbol, LocalDate today) throws AlpacaMarketDataException {
        List<MarketBar> dailyBars = marketDataApi.getDailyBars(symbol, today.minusDays(LOOKBACK_DAYS), today);
        if (dailyBars == null || dailyBars.size() < MINIMUM_HISTORY + 1) {
            log.accept("[Swing Vault] Skipped " + symbol + ": Alpaca did not return enough daily history.");
            return Optional.empty();
        }
        MarketBar latest = dailyBars.get(dailyBars.size() - 1);
        List<MarketBar> history = dailyBars.subList(0, dailyBars.size() - 1); // exclude the latest/forming bar
        if (history.size() < MINIMUM_HISTORY) {
            log.accept("[Swing Vault] Skipped " + symbol + ": not enough prior sessions for trend windows.");
            return Optional.empty();
        }
        MarketBar previousDaily = history.get(history.size() - 1);
        BigDecimal current = valid(latest.close()) ? latest.close() : latest.open();
        if (!valid(current) || previousDaily.close().compareTo(BigDecimal.ZERO) <= 0) {
            log.accept("[Swing Vault] Skipped " + symbol + ": missing live price data.");
            return Optional.empty();
        }
        BigDecimal recentHigh = recentHigh(history);
        if (!valid(recentHigh)) {
            log.accept("[Swing Vault] Skipped " + symbol + ": could not establish a recent swing high.");
            return Optional.empty();
        }
        BigDecimal pullbackPercent = recentHigh.subtract(current)
                .multiply(BigDecimal.valueOf(100)).divide(recentHigh, 2, RoundingMode.HALF_UP);
        BigDecimal dayChangePercent = current.subtract(previousDaily.close())
                .multiply(BigDecimal.valueOf(100)).divide(previousDaily.close(), 2, RoundingMode.HALF_UP);
        BigDecimal ma20 = movingAverage(history, 20);
        BigDecimal ma50 = movingAverage(history, 50);
        BigDecimal ma200 = movingAverage(history, 200);
        BigDecimal avgVolume = averageVolume(history, 20);
        BigDecimal relativeVolume = valid(avgVolume) && valid(latest.volume())
                ? latest.volume().divide(avgVolume, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal supportProximity = valid(ma50)
                ? current.subtract(ma50).multiply(BigDecimal.valueOf(100)).divide(ma50, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal atr = averageTrueRange(history, ATR_PERIOD);
        return Optional.of(new SwingCandidate(
                symbol,
                symbol,
                Monetary.round(current),
                Monetary.round(recentHigh),
                pullbackPercent,
                Monetary.round(previousDaily.close()),
                dayChangePercent,
                avgVolume.longValue(),
                relativeVolume,
                valid(ma20) ? Monetary.round(ma20) : BigDecimal.ZERO,
                valid(ma50) ? Monetary.round(ma50) : BigDecimal.ZERO,
                valid(ma200) ? Monetary.round(ma200) : BigDecimal.ZERO,
                valid(ma20) && current.compareTo(ma20) > 0,
                valid(ma50) && current.compareTo(ma50) > 0,
                valid(ma200) && current.compareTo(ma200) > 0,
                valid(ma50) && valid(ma200) && ma50.compareTo(ma200) > 0,
                supportProximity,
                Monetary.round(atr)
        ));
    }

    private BigDecimal recentHigh(List<MarketBar> history) {
        int count = Math.min(RECENT_HIGH_LOOKBACK, history.size());
        List<MarketBar> window = history.subList(history.size() - count, history.size());
        return window.stream().map(MarketBar::high).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
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

    private BigDecimal averageTrueRange(List<MarketBar> history, int period) {
        if (history.size() < 2) return BigDecimal.ZERO;
        int count = Math.min(period, history.size() - 1);
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

    private boolean valid(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    public static List<String> parseSymbols(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return java.util.Arrays.stream(raw.split("[,\\s]+"))
                .map(SwingLiveScanner::normalizeSymbol)
                .filter(symbol -> !symbol.isBlank())
                .distinct()
                .toList();
    }

    private static String normalizeSymbol(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    }
}
