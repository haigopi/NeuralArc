package com.neuralarc.rangerider;

import com.neuralarc.api.AlpacaMarketDataApi;
import com.neuralarc.api.AlpacaMarketDataException;
import com.neuralarc.model.MarketBar;
import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.math.MathContext;
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
 * Builds Range Rider candidates exclusively from operator-supplied symbols and live Alpaca daily-bar
 * market data. For each symbol it walks the last {@code lookbackSessions} completed sessions (15 ≈
 * three weeks), records every session's open, high, and low, and reduces them to the average open,
 * average high, and average low plus the average daily range and how stable that range has been.
 *
 * <p>Today's still-forming bar is excluded from the averages — only completed sessions describe a
 * repeatable daily range — but its price is carried through as the current price so the analyzer can
 * tell whether the planned entry is still reachable. No hardcoded tickers, canned prices, or synthetic
 * candidates.
 */
public final class RangeRiderLiveScanner {
    /** Sessions still worth analyzing when Alpaca returns a shorter history than requested. */
    private static final int MINIMUM_SESSION_RATIO_NUMERATOR = 2;
    private static final int MINIMUM_SESSION_RATIO_DENOMINATOR = 3;
    private static final int VOLUME_AVERAGE_PERIOD = 20;

    private final AlpacaMarketDataApi marketDataApi;
    private final Clock clock;
    private final Consumer<String> log;

    public RangeRiderLiveScanner(AlpacaMarketDataApi marketDataApi, Clock clock, Consumer<String> log) {
        this.marketDataApi = Objects.requireNonNull(marketDataApi, "marketDataApi");
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
        this.log = log == null ? ignored -> { } : log;
    }

    public List<RangeRiderCandidate> candidates(List<String> symbols, int lookbackSessions) {
        if (symbols == null || symbols.isEmpty()) {
            return List.of();
        }
        int sessions = lookbackSessions <= 0 ? RangeRiderConfig.DEFAULT_LOOKBACK_SESSIONS : lookbackSessions;
        LocalDate today = LocalDate.now(clock);
        List<RangeRiderCandidate> candidates = new ArrayList<>();
        for (String symbol : symbols.stream()
                .map(RangeRiderLiveScanner::normalizeSymbol)
                .filter(normalized -> !normalized.isBlank())
                .distinct()
                .toList()) {
            try {
                buildCandidate(symbol, today, sessions).ifPresent(candidates::add);
            } catch (AlpacaMarketDataException ex) {
                log.accept("[Range Rider] Skipped " + symbol + ": " + ex.getMessage());
            }
        }
        return candidates;
    }

    private Optional<RangeRiderCandidate> buildCandidate(String symbol, LocalDate today, int lookbackSessions)
            throws AlpacaMarketDataException {
        List<MarketBar> dailyBars = marketDataApi.getDailyBars(symbol, today.minusDays(calendarLookback(lookbackSessions)), today);
        if (dailyBars == null || dailyBars.isEmpty()) {
            log.accept("[Range Rider] Skipped " + symbol + ": Alpaca returned no daily bars.");
            return Optional.empty();
        }
        // Today's bar is still forming: it would skew the averages and make the same scan return
        // different plans at different times of day. Range Rider works on completed sessions only.
        List<MarketBar> history = dailyBars.stream().filter(bar -> !barDate(bar).isEqual(today)).toList();
        int minimumSessions = Math.max(3,
                lookbackSessions * MINIMUM_SESSION_RATIO_NUMERATOR / MINIMUM_SESSION_RATIO_DENOMINATOR);
        if (history.size() < minimumSessions) {
            log.accept("[Range Rider] Skipped " + symbol + ": only " + history.size()
                    + " completed session(s) available, need at least " + minimumSessions + ".");
            return Optional.empty();
        }
        List<MarketBar> window = history.subList(history.size() - Math.min(lookbackSessions, history.size()), history.size());
        MarketBar lastCompleted = history.get(history.size() - 1);
        BigDecimal reference = lastCompleted.close();
        if (!valid(reference)) {
            log.accept("[Range Rider] Skipped " + symbol + ": the last completed session has no close price.");
            return Optional.empty();
        }
        List<RangeRiderSession> sessions = window.stream()
                .map(bar -> new RangeRiderSession(barDate(bar), bar.open(), bar.high(), bar.low(), bar.close()))
                .toList();
        BigDecimal averageOpen = average(sessions.stream().map(RangeRiderSession::open).toList());
        BigDecimal averageHigh = average(sessions.stream().map(RangeRiderSession::high).toList());
        BigDecimal averageLow = average(sessions.stream().map(RangeRiderSession::low).toList());
        if (!valid(averageOpen) || !valid(averageLow) || averageHigh.compareTo(averageLow) <= 0) {
            log.accept("[Range Rider] Skipped " + symbol + ": daily highs and lows do not form a usable range.");
            return Optional.empty();
        }
        // Express the average low and average high as distances from the average open. Absolute price
        // levels from three weeks ago go stale the moment a stock drifts; these percentages do not, so
        // they can be applied to whatever price the next session actually opens at.
        BigDecimal averageDipPercent = percentOf(averageOpen.subtract(averageLow), averageOpen);
        BigDecimal averageRallyPercent = percentOf(averageHigh.subtract(averageOpen), averageOpen);
        List<BigDecimal> dailyRanges = sessions.stream().map(RangeRiderSession::rangePercent).toList();
        BigDecimal averageRangePercent = average(dailyRanges).setScale(2, RoundingMode.HALF_UP);
        BigDecimal rangeStabilityPercent = stability(dailyRanges);
        BigDecimal averageVolume = averageVolume(window);
        BigDecimal relativeVolume = valid(averageVolume) && valid(lastCompleted.volume())
                ? lastCompleted.volume().divide(averageVolume, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal priorClose = history.size() >= 2 ? history.get(history.size() - 2).close() : reference;
        BigDecimal dayChangePercent = valid(priorClose)
                ? percentOf(reference.subtract(priorClose), priorClose)
                : BigDecimal.ZERO;
        return Optional.of(new RangeRiderCandidate(
                symbol,
                symbol,
                Monetary.round(reference),
                Monetary.round(averageOpen),
                Monetary.round(averageHigh),
                Monetary.round(averageLow),
                averageRangePercent,
                averageDipPercent,
                averageRallyPercent,
                rangeStabilityPercent,
                Monetary.round(priorClose),
                dayChangePercent,
                averageVolume.longValue(),
                relativeVolume,
                sessions
        ));
    }

    private static BigDecimal percentOf(BigDecimal amount, BigDecimal base) {
        if (base == null || base.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return amount.multiply(BigDecimal.valueOf(100)).divide(base, 4, RoundingMode.HALF_UP);
    }

    /**
     * Calendar days needed to cover {@code sessions} trading days: roughly seven calendar days per five
     * trading days, plus a fortnight of slack for holidays and long weekends.
     */
    private static int calendarLookback(int sessions) {
        return sessions * 7 / 5 + 14;
    }

    /**
     * Range repeatability as 0–100: 100 minus the coefficient of variation of the per-session ranges.
     * A stock whose daily range is nearly the same every day scores near 100; one whose range swings
     * wildly from session to session scores near 0.
     */
    private static BigDecimal stability(List<BigDecimal> dailyRanges) {
        BigDecimal mean = average(dailyRanges);
        if (mean.compareTo(BigDecimal.ZERO) <= 0 || dailyRanges.size() < 2) {
            return BigDecimal.ZERO;
        }
        BigDecimal varianceSum = BigDecimal.ZERO;
        for (BigDecimal value : dailyRanges) {
            BigDecimal delta = value.subtract(mean);
            varianceSum = varianceSum.add(delta.multiply(delta));
        }
        BigDecimal variance = varianceSum.divide(BigDecimal.valueOf(dailyRanges.size()), 8, RoundingMode.HALF_UP);
        BigDecimal deviation = variance.sqrt(MathContext.DECIMAL64);
        BigDecimal coefficient = deviation.multiply(BigDecimal.valueOf(100)).divide(mean, 2, RoundingMode.HALF_UP);
        BigDecimal stability = BigDecimal.valueOf(100).subtract(coefficient);
        if (stability.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        return stability.min(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal average(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), 6, RoundingMode.HALF_UP);
    }

    private static BigDecimal averageVolume(List<MarketBar> window) {
        if (window.isEmpty()) {
            return BigDecimal.ZERO;
        }
        int count = Math.min(VOLUME_AVERAGE_PERIOD, window.size());
        List<MarketBar> recent = window.subList(window.size() - count, window.size());
        BigDecimal sum = recent.stream().map(MarketBar::volume).reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    private static LocalDate barDate(MarketBar bar) {
        String timestamp = bar == null ? "" : bar.timestamp();
        if (timestamp == null || timestamp.length() < 10) {
            return LocalDate.MIN;
        }
        try {
            return LocalDate.parse(timestamp.substring(0, 10));
        } catch (RuntimeException ex) {
            return LocalDate.MIN;
        }
    }

    private static boolean valid(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    public static List<String> parseSymbols(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return java.util.Arrays.stream(raw.split("[,\\s]+"))
                .map(RangeRiderLiveScanner::normalizeSymbol)
                .filter(symbol -> !symbol.isBlank())
                .distinct()
                .toList();
    }

    private static String normalizeSymbol(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    }
}
