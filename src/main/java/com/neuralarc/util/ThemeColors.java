package com.neuralarc.util;

import java.awt.Color;
import javax.swing.UIManager;

/**
 * Resolves semantic application colors from UIManager keys defined in the
 * FlatLaf custom defaults (resources/themes/FlatLaf.properties), falling back
 * to a hard-coded value when no look-and-feel is installed (e.g. headless tests).
 */
public final class ThemeColors {
    private ThemeColors() {
    }

    public static Color color(String key, Color fallback) {
        Color color = UIManager.getColor(key);
        return color != null ? color : fallback;
    }
}
