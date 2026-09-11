package com.neuralarc.analytics;

import java.util.ArrayList;
import java.util.List;

/**
 * Turning points in a price series: the highs and lows that stood out from the bars around them.
 * Old lows tend to act as support and old highs as resistance, so the chart labels them.
 */
public final class SwingPoints {
    private SwingPoints() {
    }

    public record Swing(int index, double price, boolean high) {
    }

    /**
     * A bar is a swing high when its high beats every other high within {@code window} bars on each
     * side, and a swing low likewise for lows. On a flat top or bottom the first bar counts. The newest
     * {@code window} bars cannot qualify yet: nothing has happened after them to confirm the turn.
     */
    public static List<Swing> find(double[] highs, double[] lows, int window) {
        List<Swing> swings = new ArrayList<>();
        if (window <= 0) {
            return swings;
        }
        for (int i = window; i < highs.length - window; i++) {
            if (isExtreme(highs, i, window, true)) {
                swings.add(new Swing(i, highs[i], true));
            }
            if (isExtreme(lows, i, window, false)) {
                swings.add(new Swing(i, lows[i], false));
            }
        }
        return swings;
    }

    private static boolean isExtreme(double[] values, int index, int window, boolean highest) {
        double value = values[index];
        for (int j = index - window; j <= index + window; j++) {
            if (j == index) {
                continue;
            }
            double other = values[j];
            // Strictly beyond earlier bars, at least level with later ones: the first bar of a flat turn wins.
            boolean beaten = highest
                    ? (j < index ? other >= value : other > value)
                    : (j < index ? other <= value : other < value);
            if (beaten) {
                return false;
            }
        }
        return true;
    }
}
