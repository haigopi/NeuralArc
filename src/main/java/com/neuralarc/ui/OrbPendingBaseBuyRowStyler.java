package com.neuralarc.ui;

import com.neuralarc.model.Position;
import com.neuralarc.model.Strategy;
import com.neuralarc.util.ThemeColors;

import java.awt.Color;
import java.math.BigDecimal;

/** Row colour for a scanner row waiting to place its base buy: red when that base buy is above the current price. */
final class OrbPendingBaseBuyRowStyler {
    enum PriceDirection {
        RED_ABOVE_MARKET,
        GREEN_GAINER
    }

    static final Color BASE_BUY_ABOVE_CURRENT = ThemeColors.color(
            "NeuralArc.Orb.pendingBaseBuyAboveCurrentForeground",
            new Color(255, 112, 100)
    );
    static final Color BASE_BUY_BELOW_CURRENT = ThemeColors.color(
            "NeuralArc.Orb.pendingBaseBuyBelowCurrentForeground",
            new Color(129, 199, 132)
    );
    private static final Color GREEN_DARK = new Color(27, 94, 32);
    private static final Color GREEN_TINT = new Color(222, 255, 226);
    private static final Color RED_DARK = new Color(183, 28, 28);
    private static final Color RED_TINT = new Color(255, 228, 225);
    private static final double READABLE_CONTRAST = 4.5;

    private OrbPendingBaseBuyRowStyler() {
    }

    static Color foreground(Strategy strategy, Position position) {
        return foreground(strategy, position, null);
    }

    /**
     * The row colour, chosen to stay readable on {@code background}: the theme's colour when it has
     * enough contrast, else a darker or lighter shade of the same hue. A selected row sits on the
     * selection blue, where plain green or red is unreadable.
     */
    static Color foreground(Strategy strategy, Position position, Color background) {
        PriceDirection direction = priceDirection(strategy, position);
        return switch (direction) {
            case RED_ABOVE_MARKET -> readable(background, BASE_BUY_ABOVE_CURRENT, RED_DARK, RED_TINT);
            case GREEN_GAINER -> readable(background, BASE_BUY_BELOW_CURRENT, GREEN_DARK, GREEN_TINT);
            case null -> null;
        };
    }

    /** The first shade with readable contrast against {@code background}, else the best of them. */
    static Color readable(Color background, Color preferred, Color... alternatives) {
        if (background == null) {
            return preferred;
        }
        Color best = preferred;
        double bestContrast = contrast(preferred, background);
        if (bestContrast >= READABLE_CONTRAST) {
            return preferred;
        }
        for (Color candidate : alternatives) {
            double ratio = contrast(candidate, background);
            if (ratio >= READABLE_CONTRAST) {
                return candidate;
            }
            if (ratio > bestContrast) {
                best = candidate;
                bestContrast = ratio;
            }
        }
        return best;
    }

    /** WCAG contrast ratio between two colours, 1 (identical) to 21 (black on white). */
    static double contrast(Color first, Color second) {
        double a = luminance(first);
        double b = luminance(second);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    private static double luminance(Color color) {
        return 0.2126 * channel(color.getRed()) + 0.7152 * channel(color.getGreen()) + 0.0722 * channel(color.getBlue());
    }

    private static double channel(int value) {
        double v = value / 255.0;
        return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    static PriceDirection priceDirection(Strategy strategy, Position position) {
        if (!PendingBaseBuyPlacementSupport.isPendingBaseBuyPlacement(strategy)) {
            return null;
        }
        BigDecimal baseBuyPrice = strategy.baseBuyLimitPrice();
        BigDecimal currentPrice = position == null ? BigDecimal.ZERO : position.getLastPrice();
        if (baseBuyPrice == null
                || currentPrice == null
                || baseBuyPrice.compareTo(BigDecimal.ZERO) <= 0
                || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        int comparison = baseBuyPrice.compareTo(currentPrice);
        if (comparison > 0) {
            return PriceDirection.RED_ABOVE_MARKET;
        }
        if (comparison < 0) {
            return PriceDirection.GREEN_GAINER;
        }
        return null;
    }
}
