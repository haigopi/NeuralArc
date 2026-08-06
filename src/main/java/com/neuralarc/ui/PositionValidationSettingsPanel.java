package com.neuralarc.ui;

import com.neuralarc.service.AppSettingsService;
import com.neuralarc.util.FontLoader;
import com.neuralarc.util.ThemeColors;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/**
 * Settings for the synchronized position-validation batching engine: the catch-up window used to
 * group nearby-due strategies into one broker request, an optional max-attempts-before-pause cap
 * (0 = disabled/unlimited, preserving today's continuous-polling behavior), and adaptive pacing.
 * Extracted from {@link SettingsDialog} because that dialog is already at its line-count ceiling.
 */
public class PositionValidationSettingsPanel extends JPanel {
    private static final int FIELD_GAP = 10;
    private static final int SECTION_INNER_PADDING = 10;
    private static final int FORM_LABEL_COLUMN_WIDTH = 280;
    private static final Color TEXT_PRIMARY = UIManager.getColor("Label.foreground") != null
            ? UIManager.getColor("Label.foreground")
            : new Color(45, 45, 50);
    private static final Color TEXT_MUTED = UIManager.getColor("Label.disabledForeground") != null
            ? UIManager.getColor("Label.disabledForeground")
            : new Color(130, 130, 130);
    private static final Color INPUT_BG = UIManager.getColor("TextField.background") != null
            ? UIManager.getColor("TextField.background")
            : Color.WHITE;
    private static final Color INPUT_BORDER = ThemeColors.color("NeuralArc.Input.border", new Color(190, 190, 200));

    private final JTextField validationBatchWindowSecondsField = new JTextField(6);
    private final JTextField maxValidationAttemptsBeforePauseField = new JTextField(6);
    private final JCheckBox adaptivePacingEnabledCheckbox = new JCheckBox("Relax polling when data is unchanged", true);
    private final JTextField adaptivePacingMaxMultiplierField = new JTextField(6);

    public PositionValidationSettingsPanel() {
        super(new GridBagLayout());
        setOpaque(false);
        setBorder(createSectionBorder("Position Validation Batching"));
        buildUi();
        applyThemeRecursively(this);
        populate(null);
    }

    public void populate(AppSettingsService.AppSettings settings) {
        int batchWindow = settings == null
                ? AppSettingsService.DEFAULT_VALIDATION_BATCH_WINDOW_SECONDS
                : settings.validationBatchWindowSeconds();
        int maxAttempts = settings == null
                ? AppSettingsService.DEFAULT_MAX_VALIDATION_ATTEMPTS_BEFORE_PAUSE
                : settings.maxValidationAttemptsBeforePause();
        boolean adaptiveEnabled = settings == null
                ? AppSettingsService.DEFAULT_ADAPTIVE_PACING_ENABLED
                : settings.adaptivePacingEnabled();
        int adaptiveMax = settings == null
                ? AppSettingsService.DEFAULT_ADAPTIVE_PACING_MAX_MULTIPLIER
                : settings.adaptivePacingMaxMultiplier();
        validationBatchWindowSecondsField.setText(String.valueOf(batchWindow));
        maxValidationAttemptsBeforePauseField.setText(String.valueOf(maxAttempts));
        adaptivePacingEnabledCheckbox.setSelected(adaptiveEnabled);
        adaptivePacingMaxMultiplierField.setText(String.valueOf(adaptiveMax));
        updateControlState();
    }

    public int validationBatchWindowSeconds() {
        return parsePositiveInt(validationBatchWindowSecondsField.getText(), AppSettingsService.DEFAULT_VALIDATION_BATCH_WINDOW_SECONDS);
    }

    public int maxValidationAttemptsBeforePause() {
        return parseNonNegativeInt(maxValidationAttemptsBeforePauseField.getText(), AppSettingsService.DEFAULT_MAX_VALIDATION_ATTEMPTS_BEFORE_PAUSE);
    }

    public boolean adaptivePacingEnabled() {
        return adaptivePacingEnabledCheckbox.isSelected();
    }

    public int adaptivePacingMaxMultiplier() {
        return parsePositiveInt(adaptivePacingMaxMultiplierField.getText(), AppSettingsService.DEFAULT_ADAPTIVE_PACING_MAX_MULTIPLIER);
    }

    private void buildUi() {
        addFormRow(0, "Batch Catch-Up Window (seconds):", validationBatchWindowSecondsField);
        addFormRow(1, "", mutedDescription(
                "Strategies due within this many seconds of each other are grouped into one combined "
                        + "broker request instead of separate calls."));
        addFormRow(2, "Max Attempts Before Pause (0 = unlimited):", maxValidationAttemptsBeforePauseField);
        addFormRow(3, "", mutedDescription(
                "If the shared broker snapshot fails this many times in a row, validation pauses until "
                        + "you click Refresh Now. 0 keeps polling indefinitely, matching today's behavior."));
        addFormRow(4, "Adaptive Pacing:", adaptivePacingEnabledCheckbox);
        addFormRow(5, "Adaptive Pacing Max Multiplier:", adaptivePacingMaxMultiplierField);
        addFormRow(6, "", mutedDescription(
                "When a strategy's price and position stay unchanged across cycles, its polling interval "
                        + "is relaxed up to this multiplier to reduce broker traffic."));
        adaptivePacingEnabledCheckbox.addActionListener(e -> updateControlState());
    }

    private void updateControlState() {
        setEnabledWithStyle(adaptivePacingMaxMultiplierField, adaptivePacingEnabledCheckbox.isSelected());
    }

    private int parsePositiveInt(String value, int fallback) {
        try {
            int parsed = Integer.parseInt(value == null ? "" : value.trim());
            return parsed > 0 ? parsed : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private int parseNonNegativeInt(String value, int fallback) {
        try {
            int parsed = Integer.parseInt(value == null ? "" : value.trim());
            return parsed >= 0 ? parsed : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private void addFormRow(int row, String labelText, Component valueComponent) {
        JLabel label = new JLabel(labelText);
        label.setPreferredSize(new Dimension(FORM_LABEL_COLUMN_WIDTH, label.getPreferredSize().height));
        GridBagConstraints labelGbc = new GridBagConstraints();
        labelGbc.gridx = 0;
        labelGbc.gridy = row;
        labelGbc.anchor = GridBagConstraints.NORTHWEST;
        labelGbc.insets = new Insets(0, 0, FIELD_GAP, FIELD_GAP);

        GridBagConstraints valueGbc = new GridBagConstraints();
        valueGbc.gridx = 1;
        valueGbc.gridy = row;
        valueGbc.weightx = 1;
        valueGbc.fill = GridBagConstraints.HORIZONTAL;
        valueGbc.anchor = GridBagConstraints.NORTHWEST;
        valueGbc.insets = new Insets(0, 0, FIELD_GAP, 0);

        add(label, labelGbc);
        add(valueComponent, valueGbc);
    }

    private JLabel mutedDescription(String text) {
        JLabel label = new JLabel("<html><div style='width:360px;'>" + text + "</div></html>");
        label.setForeground(TEXT_MUTED);
        label.setFont(FontLoader.ui(Font.PLAIN, 10f));
        return label;
    }

    private void applyThemeRecursively(Component component) {
        component.setFont(FontLoader.ui(component.getFont().getStyle(), component.getFont().getSize2D()));
        if (component instanceof JTextField input) {
            styleInput(input);
        }
        if (component instanceof JCheckBox checkBox) {
            checkBox.setOpaque(false);
            checkBox.setForeground(TEXT_PRIMARY);
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                applyThemeRecursively(child);
            }
        }
    }

    private void styleInput(JTextField input) {
        input.setBackground(INPUT_BG);
        input.setForeground(TEXT_PRIMARY);
        input.setCaretColor(TEXT_PRIMARY);
        input.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(INPUT_BORDER, 1, true),
                new EmptyBorder(4, 8, 4, 8)
        ));
    }

    private void setEnabledWithStyle(JTextField input, boolean enabled) {
        input.setEnabled(enabled);
        input.setForeground(enabled ? TEXT_PRIMARY : TEXT_MUTED);
    }

    private Border createSectionBorder(String title) {
        TitledBorder border = new TitledBorder(title);
        border.setTitleFont(FontLoader.ui(Font.BOLD, 12f));
        border.setTitleColor(ThemeColors.color("NeuralArc.Section.titleForeground", TEXT_PRIMARY));
        return BorderFactory.createCompoundBorder(border, new EmptyBorder(
                SECTION_INNER_PADDING, SECTION_INNER_PADDING, SECTION_INNER_PADDING, SECTION_INNER_PADDING
        ));
    }
}
