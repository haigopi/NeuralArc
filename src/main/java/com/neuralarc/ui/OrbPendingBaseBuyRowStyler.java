package com.neuralarc.ui;

import com.neuralarc.model.Position;
import com.neuralarc.model.Strategy;
import com.neuralarc.util.ThemeColors;

import java.awt.Color;
import java.math.BigDecimal;

final class OrbPendingBaseBuyRowStyler {
    static final Color BASE_BUY_ABOVE_CURRENT = ThemeColors.color(
            "NeuralArc.Orb.pendingBaseBuyAboveCurrentForeground",
            new Color(180, 140, 0)
    );
    static final Color BASE_BUY_BELOW_CURRENT = ThemeColors.color(
            "NeuralArc.Orb.pendingBaseBuyBelowCurrentForeground",
            new Color(46, 125, 50)
    );

    private OrbPendingBaseBuyRowStyler() {
    }

    static Color foreground(Strategy strategy, Position position) {
        if (!OrbCoordinator.isPendingOrderPlacement(strategy)) {
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
            return BASE_BUY_ABOVE_CURRENT;
        }
        if (comparison < 0) {
            return BASE_BUY_BELOW_CURRENT;
        }
        return null;
    }
}
