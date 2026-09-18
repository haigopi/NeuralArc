package com.neuralarc.ui;

import javax.swing.Box;
import javax.swing.JPanel;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Rectangle;

/**
 * Layout helpers for settings dialogs whose labels should sit right next to compact fields.
 *
 * <p>Two rules keep such a dialog from growing wider than its window: inputs keep their own
 * preferred width (a trailing filler column takes the slack, so a percent box never stretches to the
 * dialog edge), and long help text wraps at a fixed width instead of forcing one long line.
 */
final class CompactFormLayout {
    private static final int LABEL_GAP = 8;
    private static final int ROW_GAP = 3;

    private CompactFormLayout() {
    }

    /** A grid of label / field / unit rows; fields keep their natural width. */
    static JPanel form() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        return panel;
    }

    /**
     * Adds {@code label field [unit]} at {@code row}. The label column is as wide as its widest label,
     * so every field in the form lines up, and nothing to the right of the unit stretches.
     */
    static void addRow(JPanel form, int row, Component label, Component field, Component unit) {
        form.add(label, cell(row, 0, 0, LABEL_GAP));
        form.add(field, cell(row, 1, 0, unit == null ? 0 : 6));
        if (unit != null) {
            form.add(unit, cell(row, 2, 0, 0));
        }
        form.add(Box.createHorizontalStrut(0), cell(row, 3, 1, 0));
    }

    /**
     * Wraps plain text as HTML so it renders about {@code widthPx} screen pixels wide, escaping markup
     * characters such as the {@code &} in P&L.
     */
    static String wrapped(String text, int widthPx) {
        String escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return "<html><body style='width:" + cssWidth(widthPx) + "px'>" + escaped + "</body></html>";
    }

    /**
     * The CSS width that renders {@code widthPx} screen pixels wide. Swing's HTML renderer treats a CSS
     * pixel as a 96-dpi unit on a 72-dpi basis, so {@code width:500px} lays out about 650 pixels wide.
     */
    static int cssWidth(int widthPx) {
        return Math.round(widthPx * 0.75f);
    }

    /**
     * A scroll-pane view that always matches the viewport width, so the pane only ever scrolls
     * vertically and never offers a horizontal scrollbar for a few pixels of overflow.
     */
    static JPanel widthTrackingPanel(LayoutManager layout) {
        return new WidthTrackingPanel(layout);
    }

    private static GridBagConstraints cell(int row, int column, double weightx, int rightGap) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = column;
        gbc.gridy = row;
        gbc.weightx = weightx;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(ROW_GAP, 0, ROW_GAP, rightGap);
        return gbc;
    }

    private static final class WidthTrackingPanel extends JPanel implements Scrollable {
        private WidthTrackingPanel(LayoutManager layout) {
            super(layout);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return orientation == SwingConstants.VERTICAL ? visibleRect.height - 16 : visibleRect.width;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
