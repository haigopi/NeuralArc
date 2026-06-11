package com.neuralarc.ui;

import javax.swing.border.Border;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;

/**
 * Inner border that reserves a trailing zone and paints a thin separator plus a
 * vector down-chevron, giving popup-menu buttons a split-button dropdown look.
 * Drawn with Graphics primitives (not a font glyph) so it renders identically
 * regardless of the UI font's symbol coverage, and follows the button's
 * foreground/enabled state. Compose inside the button's CompoundBorder so the
 * zone survives hover/pressed border swaps.
 */
final class DropdownChevronBorder implements Border {
    private static final int ZONE_WIDTH = 16;
    private static final int CHEVRON_WIDTH = 8;
    private static final int CHEVRON_HEIGHT = 4;

    @Override
    public Insets getBorderInsets(Component c) {
        return new Insets(0, 0, 0, ZONE_WIDTH);
    }

    @Override
    public boolean isBorderOpaque() {
        return false;
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color fg = c.getForeground();
            boolean enabled = c.isEnabled();
            int zoneX = x + width - ZONE_WIDTH;

            g2.setColor(new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), enabled ? 70 : 40));
            g2.drawLine(zoneX, y + 4, zoneX, y + height - 5);

            g2.setColor(new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), enabled ? 255 : 110));
            g2.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int cx = zoneX + (ZONE_WIDTH - CHEVRON_WIDTH) / 2 + 1;
            int cy = y + (height - CHEVRON_HEIGHT) / 2;
            g2.drawLine(cx, cy, cx + CHEVRON_WIDTH / 2, cy + CHEVRON_HEIGHT);
            g2.drawLine(cx + CHEVRON_WIDTH / 2, cy + CHEVRON_HEIGHT, cx + CHEVRON_WIDTH, cy);
        } finally {
            g2.dispose();
        }
    }
}
