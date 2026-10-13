package com.neuralarc.ui;

import javax.swing.Icon;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;

/**
 * The look of "a liquidation is running": the red-to-amber blink of the Liquidate Portfolio button, as
 * a small dot for places that cannot flash a whole control, such as a tab. Colours and cadence live here
 * so the button and the dot are one signal and cannot drift apart.
 */
final class LiquidationPulse {
    /** Phase colours: the button's blink between these two. */
    static final Color PRIMARY = new Color(210, 52, 38);
    static final Color ALTERNATE = new Color(255, 136, 0);
    /** Milliseconds between phase changes, the same beat as the button. */
    static final int INTERVAL_MILLIS = 550;

    private LiquidationPulse() {
    }

    /** A dot that shows the current phase; the owner repaints it when the phase flips. */
    static final class DotIcon implements Icon {
        private static final int DOT = 8;
        private static final int GAP = 6;
        private boolean alternate;

        void setAlternate(boolean alternate) {
            this.alternate = alternate;
        }

        boolean alternate() {
            return alternate;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color core = alternate ? ALTERNATE : PRIMARY;
                int top = y + (getIconHeight() - DOT) / 2;
                // A soft halo so the dot reads as a live lamp rather than a bullet point.
                g2.setColor(new Color(core.getRed(), core.getGreen(), core.getBlue(), 70));
                g2.fill(new Ellipse2D.Double(x - 2, top - 2, DOT + 4, DOT + 4));
                g2.setColor(core);
                g2.fill(new Ellipse2D.Double(x, top, DOT, DOT));
            } finally {
                g2.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return DOT + GAP / 2;
        }

        @Override
        public int getIconHeight() {
            return 12;
        }
    }
}
