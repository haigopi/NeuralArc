package com.neuralarc.ui;

import com.neuralarc.util.FontLoader;
import com.neuralarc.util.SvgIconLoader;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

final class DialogButtonStyles {
    // ── Base state ────────────────────────────────────────────────────────────
    private static final Color BUTTON_BG          = new Color(250, 250, 250);
    private static final Color BUTTON_BORDER      = new Color(100, 100, 160);
    private static final Color BUTTON_TEXT        = new Color(20, 20, 25);
    // ── Hover state ───────────────────────────────────────────────────────────
    private static final Color BUTTON_BG_HOVER     = new Color(224, 230, 255);
    private static final Color BUTTON_BORDER_HOVER = new Color(72, 82, 180);
    // ── Pressed state ─────────────────────────────────────────────────────────
    private static final Color BUTTON_BG_PRESSED     = new Color(198, 210, 252);
    private static final Color BUTTON_BORDER_PRESSED = new Color(50, 60, 155);

    private DialogButtonStyles() {
    }

    static void apply(JButton button) {
        button.setFocusPainted(false);
        button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        button.setFont(FontLoader.ui(Font.BOLD, 10f));
        button.setForeground(BUTTON_TEXT);
        button.setBackground(BUTTON_BG);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorderPainted(true);
        button.putClientProperty("JButton.arc", 14);
        button.setBorder(normalBorder());
        button.setMargin(new java.awt.Insets(5, 12, 5, 12));
        button.setIconTextGap(8);
        button.setHorizontalTextPosition(SwingConstants.RIGHT);
        button.setVerticalTextPosition(SwingConstants.CENTER);

        // Install hover + press feedback exactly once per button.
        if (!Boolean.TRUE.equals(button.getClientProperty("dialogButtonInteractInstalled"))) {
            button.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    if (button.isEnabled()) {
                        button.setBackground(BUTTON_BG_HOVER);
                        button.setBorder(hoverBorder());
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    button.setBackground(BUTTON_BG);
                    button.setBorder(normalBorder());
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    if (button.isEnabled() && e.getButton() == MouseEvent.BUTTON1) {
                        button.setBackground(BUTTON_BG_PRESSED);
                        button.setBorder(pressedBorder());
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (button.contains(e.getPoint()) && button.isEnabled()) {
                        // Cursor is still over the button — return to hover state.
                        button.setBackground(BUTTON_BG_HOVER);
                        button.setBorder(hoverBorder());
                    } else {
                        button.setBackground(BUTTON_BG);
                        button.setBorder(normalBorder());
                    }
                }
            });
            button.putClientProperty("dialogButtonInteractInstalled", Boolean.TRUE);
        }
    }

    static void apply(JButton button, String iconResourcePath) {
        apply(button);
        button.setIcon(SvgIconLoader.load(iconResourcePath, 15, BUTTON_BORDER));
    }

    // ── Border helpers ────────────────────────────────────────────────────────

    private static javax.swing.border.Border normalBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BUTTON_BORDER, 1, true),
                new EmptyBorder(7, 12, 7, 12)
        );
    }

    private static javax.swing.border.Border hoverBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BUTTON_BORDER_HOVER, 1, true),
                new EmptyBorder(7, 12, 7, 12)
        );
    }

    private static javax.swing.border.Border pressedBorder() {
        // 2-px border with 1-px less inner padding on each side so the button
        // doesn't shift/grow.
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BUTTON_BORDER_PRESSED, 2, true),
                new EmptyBorder(6, 11, 6, 11)
        );
    }
}
