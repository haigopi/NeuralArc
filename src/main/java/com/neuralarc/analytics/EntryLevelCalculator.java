package com.neuralarc.analytics;

import com.neuralarc.model.MarketBar;
import com.neuralarc.model.ScannerEntrySettings;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

/**
 * Turns the operator's {@link ScannerEntrySettings} and a stock's daily history into an entry level.
 *
 * <p>The level is never above the market, which is the lowest of the current price, today's open and
 * today's low: a limit above it fills at once at whatever the market asks. A zero result means there was
 * nothing to plan from and the caller keeps its own entry.
 */
public final class EntryLevelCalculator {
    private EntryLevelCalculator() {
    }

    /**
     * @param history   daily bars in any order, today's included when it has traded
     * @param open      today's open, or zero
     * @param todayLow  the low so far today (premarket low for premarket scanners), or zero
     */
    public static BigDecimal level(ScannerEntrySettings settings, BigDecimal current, BigDecimal open,
                                   BigDecimal todayLow, List<MarketBar> history) {
        ScannerEntrySettings safe = settings == null ? ScannerEntrySettings.defaults() : settings;
        BigDecimal market = PlannedEntryPrice.baseBuy(current, open, todayLow);
        if (market.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        List<MarketBar> ordered = oldestFirst(history);
        BigDecimal level = switch (safe.mode()) {
            case TWO_WEEK_LOW_OR_CURRENT -> lowOver(ordered, ScannerEntrySettings.Anchor.TWO_WEEKS.sessions(), todayLow);
            case CUSTOM_PERCENT -> customLevel(safe, ordered, todayLow);
            case AUTO_TREND -> TrendEntryModel.predict(ordered, market, todayLow)
                    .map(TrendEntryModel.Prediction::price).orElse(BigDecimal.ZERO);
        };
        if (level.signum() <= 0) {
            return market;
        }
        return level.min(market).setScale(2, RoundingMode.FLOOR);
    }

    private static BigDecimal customLevel(ScannerEntrySettings settings, List<MarketBar> ordered, BigDecimal todayLow) {
        BigDecimal anchorLow = lowOver(ordered, settings.anchor().sessions(), todayLow);
        if (anchorLow.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return anchorLow.multiply(BigDecimal.ONE.add(settings.percentAboveLow().movePointLeft(2)));
    }

    /** The lowest low over the last {@code sessions} bars and today's low; zero when unknown. */
    static BigDecimal lowOver(List<MarketBar> oldestFirst, int sessions, BigDecimal todayLow) {
        BigDecimal low = todayLow != null && todayLow.signum() > 0 ? todayLow : BigDecimal.ZERO;
        int from = Math.max(0, oldestFirst.size() - sessions);
        for (MarketBar bar : oldestFirst.subList(from, oldestFirst.size())) {
            BigDecimal barLow = bar.low() != null && bar.low().signum() > 0 ? bar.low() : BigDecimal.ZERO;
            if (barLow.signum() > 0 && (low.signum() == 0 || barLow.compareTo(low) < 0)) {
                low = barLow;
            }
        }
        return low;
    }

    private static List<MarketBar> oldestFirst(List<MarketBar> history) {
        if (history == null) {
            return List.of();
        }
        return history.stream()
                .filter(bar -> bar != null)
                .sorted(Comparator.comparing(MarketBar::timestamp, Comparator.nullsFirst(String::compareTo)))
                .toList();
    }
}
