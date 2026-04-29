package com.neuralarc.ui;

import com.neuralarc.util.FontLoader;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.JTextComponent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;

final class SupportDialogStyles {
    static final Color DIALOG_BG = new Color(246, 248, 252);
    static final Color CARD_BG = Color.WHITE;
    static final Color CARD_BORDER = new Color(220, 225, 234);
    static final Color TEXT_PRIMARY = new Color(32, 38, 49);
    static final Color TEXT_SECONDARY = new Color(97, 107, 126);
    static final Color HERO_BG = new Color(236, 241, 250);
    static final Color INPUT_BORDER = new Color(198, 206, 220);
    static final Color INPUT_BG = Color.WHITE;
    static final Color READONLY_BG = new Color(243, 246, 251);

    static final Font TITLE_FONT = FontLoader.ui(Font.BOLD, 19f);
    static final Font SUBTITLE_FONT = FontLoader.ui(Font.PLAIN, 12f);
    static final Font SECTION_FONT = FontLoader.ui(Font.BOLD, 12f);
    static final Font LABEL_FONT = FontLoader.ui(Font.BOLD, 11f);
    static final Font FIELD_FONT = FontLoader.ui(Font.PLAIN, 12f);

    private SupportDialogStyles() {
    }

    static JPanel createHeroPanel(String title, String subtitle) {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(HERO_BG);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(208, 216, 229), 1, true),
                new EmptyBorder(18, 18, 18, 18)
        ));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(TITLE_FONT);
        titleLabel.setForeground(TEXT_PRIMARY);

        JTextArea subtitleArea = new JTextArea(subtitle);
        subtitleArea.setEditable(false);
        subtitleArea.setOpaque(false);
        subtitleArea.setLineWrap(true);
        subtitleArea.setWrapStyleWord(true);
        subtitleArea.setFont(SUBTITLE_FONT);
        subtitleArea.setForeground(TEXT_SECONDARY);
        subtitleArea.setBorder(null);

        panel.add(titleLabel, BorderLayout.NORTH);
        panel.add(subtitleArea, BorderLayout.CENTER);
        return panel;
    }

    static JPanel createSectionPanel(String title) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(true);
        panel.setBackground(CARD_BG);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CARD_BORDER, 1, true),
                new EmptyBorder(14, 14, 14, 14)
        ));

        JLabel sectionLabel = new JLabel(title);
        sectionLabel.setFont(SECTION_FONT);
        sectionLabel.setForeground(TEXT_PRIMARY);
        sectionLabel.setBorder(new EmptyBorder(0, 0, 10, 0));
        panel.add(sectionLabel, BorderLayout.NORTH);
        return panel;
    }

    static JLabel createFieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(LABEL_FONT);
        label.setForeground(TEXT_PRIMARY);
        return label;
    }

    static JTextField createTextField(int columns) {
        JTextField field = new JTextField(columns);
        styleTextComponent(field);
        return field;
    }

    static JTextArea createTextArea(int rows, int columns) {
        JTextArea area = new JTextArea(rows, columns);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        styleTextComponent(area);
        return area;
    }

    static JTextField createReadOnlyField(String value) {
        JTextField field = createTextField(28);
        field.setText(value == null ? "" : value);
        field.setEditable(false);
        field.setBackground(READONLY_BG);
        field.setForeground(TEXT_SECONDARY);
        return field;
    }

    static JScrollPane wrapTextArea(JTextArea textArea, int preferredHeight) {
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setBorder(BorderFactory.createLineBorder(INPUT_BORDER, 1, true));
        scrollPane.setPreferredSize(new Dimension(420, preferredHeight));
        scrollPane.getViewport().setBackground(INPUT_BG);
        return scrollPane;
    }

    static void applyDialogTheme(Container container) {
        if (container instanceof JComponent component) {
            component.setOpaque(true);
            Color existing = component.getBackground();
            if (existing == null || (!existing.equals(CARD_BG) && !existing.equals(HERO_BG) && !existing.equals(INPUT_BG) && !existing.equals(READONLY_BG))) {
                component.setBackground(DIALOG_BG);
            }
        }
        for (java.awt.Component child : container.getComponents()) {
            if (child instanceof JLabel label) {
                if (label.getFont() == null || !label.getFont().isBold()) {
                    label.setForeground(TEXT_PRIMARY);
                }
            }
            if (child instanceof Container nestedContainer) {
                if (!(nestedContainer instanceof JPanel panel
                        && panel.getBackground() != null
                        && (panel.getBackground().equals(CARD_BG) || panel.getBackground().equals(HERO_BG)))
                        && nestedContainer instanceof JComponent nestedComponent) {
                    Color existing = nestedComponent.getBackground();
                    if (existing == null || (!existing.equals(INPUT_BG) && !existing.equals(READONLY_BG))) {
                        nestedComponent.setBackground(DIALOG_BG);
                    }
                }
                applyDialogTheme(nestedContainer);
            }
            if (child instanceof JScrollPane scrollPane) {
                scrollPane.setBackground(DIALOG_BG);
                scrollPane.getViewport().setBackground(DIALOG_BG);
            }
        }
    }

    private static void styleTextComponent(JTextComponent component) {
        component.setFont(FIELD_FONT);
        component.setBackground(INPUT_BG);
        component.setForeground(TEXT_PRIMARY);
        component.setCaretColor(TEXT_PRIMARY);
        component.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(INPUT_BORDER, 1, true),
                new EmptyBorder(8, 10, 8, 10)
        ));
    }
}
