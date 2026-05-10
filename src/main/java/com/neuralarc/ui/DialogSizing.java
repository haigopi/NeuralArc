package com.neuralarc.ui;

import javax.swing.JComponent;
import javax.swing.JDialog;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;

final class DialogSizing {
    private static final int DEFAULT_MARGIN = 48;
    private static final double DEFAULT_MAX_HEIGHT_RATIO = 0.92d;

    private DialogSizing() {
    }

    static Dimension preferredViewportSize(
            JComponent content,
            int minWidth,
            int minHeight,
            int maxWidth,
            int maxHeight
    ) {
        Dimension preferred = content == null ? new Dimension(minWidth, minHeight) : content.getPreferredSize();
        int width = clamp(preferred.width, minWidth, maxWidth);
        int height = clamp(preferred.height, minHeight, maxHeight);
        return new Dimension(width, height);
    }

    static void packAndFit(JDialog dialog, int minWidth, int minHeight) {
        packAndFit(dialog, minWidth, minHeight, DEFAULT_MARGIN, DEFAULT_MAX_HEIGHT_RATIO);
    }

    static void packAndFit(JDialog dialog, int minWidth, int minHeight, int margin, double maxHeightRatio) {
        dialog.pack();
        Dimension preferred = dialog.getPreferredSize();
        Rectangle usableBounds = usableScreenBounds(dialog);
        int maxWidth = Math.max(minWidth, usableBounds.width - margin);
        int maxHeight = Math.max(minHeight, Math.min(
                usableBounds.height - margin,
                (int) Math.round(usableBounds.height * maxHeightRatio)
        ));
        int width = clamp(preferred.width, minWidth, maxWidth);
        int height = clamp(preferred.height, minHeight, maxHeight);
        dialog.setSize(width, height);
        dialog.setMinimumSize(new Dimension(Math.min(width, minWidth), Math.min(height, minHeight)));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private static Rectangle usableScreenBounds(JDialog dialog) {
        GraphicsConfiguration configuration = dialog.getGraphicsConfiguration();
        if (configuration == null) {
            configuration = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice()
                    .getDefaultConfiguration();
        }
        Rectangle bounds = configuration.getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration);
        return new Rectangle(
                bounds.x + insets.left,
                bounds.y + insets.top,
                Math.max(320, bounds.width - insets.left - insets.right),
                Math.max(320, bounds.height - insets.top - insets.bottom)
        );
    }
}
