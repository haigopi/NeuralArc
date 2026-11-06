package com.neuralarc.model;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Where scanner entries are planned relative to recent price history.
 *
 * <p>Every scanner plans a base buy; this decides how far down that buy is placed. The planned price is
 * never above the market whatever the mode, and a scanner's own, deeper entry is never raised.
 *
 * @param mode           how the entry level is chosen
 * @param anchor         the history window whose low {@link Mode#CUSTOM_PERCENT} measures from
 * @param percentAboveLow how far above that low to place the entry, in percent; zero is the low itself
 */
public record ScannerEntrySettings(Mode mode, Anchor anchor, BigDecimal percentAboveLow) {
    public static final BigDecimal MAX_PERCENT_ABOVE_LOW = new BigDecimal("100");

    public enum Mode {
        /** The lower of the last two weeks' low and the current low. */
        TWO_WEEK_LOW_OR_CURRENT("Last 2 weeks low or current low"),
        /** A chosen percentage above the low of a chosen window. */
        CUSTOM_PERCENT("Custom: percentage above a period low"),
        /** Read the 2-week, 2-month, 6-month and 1-year trends and predict a price. */
        AUTO_TREND("Automatic: predicted from the 2-week to 1-year trends");

        private final String label;

        Mode(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** A history window, measured in trading sessions. */
    public enum Anchor {
        LAST_WEEK("Last week low", 5),
        TWO_WEEKS("2 weeks low", 10),
        ONE_MONTH("1 month low", 21),
        SIX_MONTHS("6 months low", 126),
        ONE_YEAR("1 year low", 252);

        private final String label;
        private final int sessions;

        Anchor(String label, int sessions) {
            this.label = label;
            this.sessions = sessions;
        }

        public String label() {
            return label;
        }

        public int sessions() {
            return sessions;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public ScannerEntrySettings {
        mode = mode == null ? Mode.TWO_WEEK_LOW_OR_CURRENT : mode;
        anchor = anchor == null ? Anchor.TWO_WEEKS : anchor;
        percentAboveLow = percentAboveLow == null || percentAboveLow.signum() < 0
                ? BigDecimal.ZERO
                : percentAboveLow.min(MAX_PERCENT_ABOVE_LOW);
    }

    public static ScannerEntrySettings defaults() {
        return new ScannerEntrySettings(Mode.TWO_WEEK_LOW_OR_CURRENT, Anchor.TWO_WEEKS, BigDecimal.ZERO);
    }

    public static Mode parseMode(String value, Mode fallback) {
        return parse(Mode.values(), value, fallback);
    }

    public static Anchor parseAnchor(String value, Anchor fallback) {
        return parse(Anchor.values(), value, fallback);
    }

    private static <E extends Enum<E>> E parse(E[] values, String value, E fallback) {
        if (value == null) {
            return fallback;
        }
        String wanted = value.trim().toUpperCase(Locale.ROOT);
        for (E candidate : values) {
            if (candidate.name().equals(wanted)) {
                return candidate;
            }
        }
        return fallback;
    }

    /** How many sessions of daily history this setting needs to read. */
    public int sessionsNeeded() {
        return switch (mode) {
            case TWO_WEEK_LOW_OR_CURRENT -> Anchor.TWO_WEEKS.sessions();
            case CUSTOM_PERCENT -> anchor.sessions();
            case AUTO_TREND -> Anchor.ONE_YEAR.sessions();
        };
    }

    /** "Custom: 5% above the 1 month low". */
    public String summary() {
        return switch (mode) {
            case TWO_WEEK_LOW_OR_CURRENT -> mode.label();
            case CUSTOM_PERCENT -> percentAboveLow.stripTrailingZeros().toPlainString() + "% above the " + anchor.label();
            case AUTO_TREND -> mode.label();
        };
    }
}
