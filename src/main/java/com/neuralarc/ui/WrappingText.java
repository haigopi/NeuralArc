package com.neuralarc.ui;

import com.neuralarc.util.FontLoader;

import javax.swing.BorderFactory;
import javax.swing.JTextArea;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;

/**
 * A read-only paragraph that wraps at word boundaries. Its preferred height is measured at its
 * container's real width: a plain wrapping text area reports its height before it has been given a
 * width, so a layout manager would size every paragraph as a single line and cut the rest off.
 */
final class WrappingText extends JTextArea {
    private final int fallbackWidth;

    WrappingText(String text, float size, Color color, int fallbackWidth) {
        super(text);
        this.fallbackWidth = fallbackWidth;
        setLineWrap(true);
        setWrapStyleWord(true);
        setEditable(false);
        setFocusable(false);
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder());
        setFont(FontLoader.ui(Font.PLAIN, size));
        setForeground(color);
        setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    @Override
    public Dimension getPreferredSize() {
        int width = availableWidth();
        if (getWidth() != width) {
            super.setSize(width, Short.MAX_VALUE); // Wrap to this width before measuring.
        }
        return new Dimension(width, super.getPreferredSize().height);
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }

    private int availableWidth() {
        Container parent = getParent();
        if (parent == null || parent.getWidth() <= 0) {
            return fallbackWidth;
        }
        Insets insets = parent.getInsets();
        return Math.max(40, parent.getWidth() - insets.left - insets.right);
    }
}
