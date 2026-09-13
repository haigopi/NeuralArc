package com.neuralarc.ui;

import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JScrollPane;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;

final class DialogSizing {
    private static final int DEFAULT_MARGIN = 48;
    private static final double DEFAULT_MAX_HEIGHT_RATIO = 0.92d;
    /** Share of the usable screen height a dialog with long, scrolling content may grow to. */
    static final double TALL_HEIGHT_RATIO = 0.88d;

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

    /**
     * Like {@link #packAndFit(JDialog, int, int)}, then {@link #growTall(JDialog, int)}: the width stays
     * as packed while the height uses the room the screen has.
     */
    static void packAndFitTall(JDialog dialog, int minWidth, int minHeight) {
        packAndFit(dialog, minWidth, minHeight);
        growTall(dialog, minHeight);
    }

    /**
     * Grows the dialog's height, never its width, until its scrolling content shows in full or it
     * reaches {@link #TALL_HEIGHT_RATIO} of the screen. A dialog with nothing to scroll grows to that
     * height so charts get the room; one whose content already fits keeps its size.
     */
    static void growTall(JDialog dialog, int minHeight) {
        dialog.validate();
        Rectangle usableBounds = usableScreenBounds(dialog);
        int tallest = Math.max(minHeight, Math.min(
                usableBounds.height - DEFAULT_MARGIN,
                (int) Math.round(usableBounds.height * TALL_HEIGHT_RATIO)
        ));
        int height = tallHeight(dialog.getHeight(), hiddenContentHeight(dialog.getContentPane()), tallest);
        if (height != dialog.getHeight()) {
            dialog.setSize(dialog.getWidth(), height);
        }
    }

    /** The height after growing by the content scrolled out of view, capped at {@code tallest}; never shrinks. */
    static int tallHeight(int currentHeight, int hiddenContentHeight, int tallest) {
        int wanted = hiddenContentHeight < 0 ? tallest : currentHeight + hiddenContentHeight;
        return Math.max(currentHeight, Math.min(wanted, tallest));
    }

    /** How much of the first scroll pane's content is out of view, or -1 when nothing scrolls. */
    static int hiddenContentHeight(Container root) {
        JScrollPane scrollPane = firstScrollPane(root);
        if (scrollPane == null || scrollPane.getViewport().getView() == null) {
            return -1;
        }
        Component view = scrollPane.getViewport().getView();
        return Math.max(0, view.getPreferredSize().height - scrollPane.getViewport().getHeight());
    }

    private static JScrollPane firstScrollPane(Container root) {
        if (root == null) {
            return null;
        }
        for (Component child : root.getComponents()) {
            if (child instanceof JScrollPane scrollPane) {
                return scrollPane;
            }
            if (child instanceof Container nested) {
                JScrollPane found = firstScrollPane(nested);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
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
