package com.neuralarc.ui;

import com.neuralarc.util.FontLoader;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Font;

final class DialogButtonStyles {
    private static final Color BUTTON_BG = new Color(60, 60, 90);
    private static final Color BUTTON_BORDER = new Color(100, 100, 160);
    private static final Color BUTTON_TEXT = new Color(220, 220, 255);

    private DialogButtonStyles() {
    }

    static void apply(JButton button) {
        button.setFocusPainted(false);
        button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        button.setFont(FontLoader.ui(Font.BOLD, 12f));
        button.setForeground(BUTTON_TEXT);
        button.setBackground(BUTTON_BG);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BUTTON_BORDER, 1, true),
                new EmptyBorder(7, 12, 7, 12)
        ));
        button.setMargin(new java.awt.Insets(5, 12, 5, 12));
    }
}
