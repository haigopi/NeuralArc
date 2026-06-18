package com.neuralarc.ui.chart;

import com.neuralarc.util.ThemeColors;

import java.awt.Color;

/**
 * Shared colors and fonts for the chart-based Risk Dashboard. Light, card-on-canvas theme that
 * matches the application's dialogs; positive/negative use the same green/red language as the grid.
 */
public final class ChartPalette {
    private ChartPalette() {}

    public static final Color CANVAS_BG = ThemeColors.color("NeuralArc.Dashboard.canvas", new Color(238, 240, 244));
    public static final Color CARD_BG = ThemeColors.color("NeuralArc.Dashboard.card", Color.WHITE);
    public static final Color CARD_BORDER = ThemeColors.color("NeuralArc.Section.border", new Color(208, 214, 222));
    public static final Color TEXT_PRIMARY = new Color(33, 43, 58);
    public static final Color TEXT_MUTED = new Color(108, 118, 132);
    public static final Color GRID = new Color(224, 228, 234);

    public static final Color POSITIVE = new Color(31, 150, 108);
    public static final Color NEGATIVE = new Color(201, 58, 58);
    public static final Color ACCENT = new Color(76, 110, 196);
    public static final Color WARN = new Color(196, 132, 38);

    /** Distinct, readable categorical series colors for donut slices / multi-series bars. */
    private static final Color[] SERIES = {
            new Color(76, 110, 196),   // blue
            new Color(31, 150, 108),   // green
            new Color(196, 132, 38),   // amber
            new Color(140, 98, 196),   // purple
            new Color(38, 166, 196),   // cyan
            new Color(196, 88, 132),   // pink
            new Color(120, 144, 60),   // olive
            new Color(196, 96, 64)     // terracotta
    };

    public static Color series(int index) {
        return SERIES[Math.floorMod(index, SERIES.length)];
    }

    /** Verdict / P&L color: green for gains, red for losses, neutral grey for flat. */
    public static Color signColor(double value) {
        if (value > 0) return POSITIVE;
        if (value < 0) return NEGATIVE;
        return TEXT_MUTED;
    }
}
