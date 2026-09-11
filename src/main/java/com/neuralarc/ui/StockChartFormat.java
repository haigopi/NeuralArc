package com.neuralarc.ui;

import java.util.Locale;

/** Number formatting shared by the stock chart and its plain-language guide. */
final class StockChartFormat {
    private StockChartFormat() {
    }

    /** Two decimals, or four for sub-dollar stocks so small moves stay visible. */
    static String price(double value) {
        return Math.abs(value) >= 1 || value == 0
                ? String.format(Locale.US, "%.2f", value)
                : String.format(Locale.US, "%.4f", value);
    }

    static String money(double value) {
        return (value < 0 ? "-$" : "$") + price(Math.abs(value));
    }

    /**
     * A price change, signed, at the precision of the stock's price: a 7-cent move on a $3.74 stock
     * reads "-$0.07", while sub-dollar stocks keep four decimals.
     */
    static String change(double change, double referencePrice) {
        String digits = Math.abs(referencePrice) >= 1
                ? String.format(Locale.US, "%.2f", Math.abs(change))
                : String.format(Locale.US, "%.4f", Math.abs(change));
        return (change < 0 ? "-$" : change > 0 ? "+$" : "$") + digits;
    }

    static String percent(double value) {
        return String.format(Locale.US, "%.1f%%", value);
    }

    static String signedPercent(double value) {
        return (value > 0 ? "+" : "") + String.format(Locale.US, "%.2f%%", value);
    }

    /** 43,001,600 as 43.0M; volumes are easier to compare at a glance. */
    static String compact(double value) {
        double magnitude = Math.abs(value);
        if (magnitude >= 1_000_000_000d) {
            return String.format(Locale.US, "%.2fB", value / 1_000_000_000d);
        }
        if (magnitude >= 1_000_000d) {
            return String.format(Locale.US, "%.1fM", value / 1_000_000d);
        }
        if (magnitude >= 1_000d) {
            return String.format(Locale.US, "%.0fK", value / 1_000d);
        }
        return String.format(Locale.US, "%.0f", value);
    }
}
