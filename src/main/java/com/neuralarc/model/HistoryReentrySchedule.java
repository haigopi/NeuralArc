package com.neuralarc.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Locale;
import java.util.UUID;

/**
 * A standing instruction to re-enter stocks from Trade History without being asked.
 *
 * <p>The manual picker exists because placing dozens of orders should be deliberate. A schedule gives
 * up that deliberation, so it is deliberately narrow: one run a week or fortnight, one group of
 * stocks ({@link Group}), and at most {@link #maxStocks()} of them. {@code lastRunDate} is persisted
 * because a fortnightly run cannot be worked out from the clock alone.
 */
public record HistoryReentrySchedule(
        String id,
        boolean enabled,
        StrategyMode mode,
        DayOfWeek day,
        LocalTime scanTimeEt,
        Cadence cadence,
        Group group,
        int maxStocks,
        LocalDate lastRunDate
) {
    /** How often the scan runs. */
    public enum Cadence {
        WEEKLY("Every week", 6),
        BIWEEKLY("Every 2 weeks", 13);

        private final String label;
        private final int minimumDaysBetweenRuns;

        Cadence(String label, int minimumDaysBetweenRuns) {
            this.label = label;
            this.minimumDaysBetweenRuns = minimumDaysBetweenRuns;
        }

        public String label() {
            return label;
        }

        /** Days that must pass after a run before the next one may fire. */
        public int minimumDaysBetweenRuns() {
            return minimumDaysBetweenRuns;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Which half of the history to re-enter — the same split the picker shows. */
    public enum Group {
        GAINS("Only stocks that closed in profit"),
        LOSSES("Only stocks that closed at a loss"),
        ALL("Every inactive stock");

        private final String label;

        Group(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public boolean accepts(java.math.BigDecimal realizedPnl) {
            boolean loss = realizedPnl != null && realizedPnl.signum() < 0;
            return switch (this) {
                case GAINS -> !loss;
                case LOSSES -> loss;
                case ALL -> true;
            };
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public static final int DEFAULT_MAX_STOCKS = 10;
    public static final int MAX_STOCKS_CEILING = 50;

    public HistoryReentrySchedule {
        id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        mode = mode == null ? StrategyMode.PAPER : mode;
        day = day == null ? DayOfWeek.MONDAY : day;
        scanTimeEt = scanTimeEt == null ? LocalTime.of(10, 0) : scanTimeEt;
        cadence = cadence == null ? Cadence.WEEKLY : cadence;
        group = group == null ? Group.GAINS : group;
        maxStocks = maxStocks <= 0 ? DEFAULT_MAX_STOCKS : Math.min(maxStocks, MAX_STOCKS_CEILING);
    }

    public static HistoryReentrySchedule defaults(StrategyMode mode) {
        return new HistoryReentrySchedule(null, false, mode, DayOfWeek.MONDAY, LocalTime.of(10, 0),
                Cadence.WEEKLY, Group.GAINS, DEFAULT_MAX_STOCKS, null);
    }

    public HistoryReentrySchedule withLastRunDate(LocalDate ranOn) {
        return new HistoryReentrySchedule(id, enabled, mode, day, scanTimeEt, cadence, group, maxStocks, ranOn);
    }

    /** "Every 2 weeks · Mon 10:00 ET · up to 10 stocks that closed in profit". */
    public String summary() {
        String dayName = day.name().charAt(0) + day.name().substring(1, 3).toLowerCase(Locale.ROOT);
        return cadence.label() + " · " + dayName + " " + scanTimeEt + " ET · up to " + maxStocks + " "
                + switch (group) {
                    case GAINS -> "stocks that closed in profit";
                    case LOSSES -> "stocks that closed at a loss";
                    case ALL -> "inactive stocks";
                };
    }
}
