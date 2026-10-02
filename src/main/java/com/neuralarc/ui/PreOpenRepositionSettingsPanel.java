package com.neuralarc.ui;

import com.neuralarc.model.PreOpenRepositionSettings;
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
 * Settings for re-posting expired day entries before the bell: whether to do it, how long before the
 * open, and whether GTC entries are included. Laid out like the other settings sections.
 */
public class PreOpenRepositionSettingsPanel extends JPanel {
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

    private final JCheckBox enabledCheckbox = new JCheckBox("Re-post expired entries before the open", false);
    private final JTextField minutesField = new JTextField(6);
    private final JCheckBox dayOrdersOnlyCheckbox = new JCheckBox("DAY entries only", true);

    public PreOpenRepositionSettingsPanel() {
        super(new GridBagLayout());
        setOpaque(false);
        setBorder(createSectionBorder("Pre-open Reposition"));
        buildUi();
        applyThemeRecursively(this);
        populate(PreOpenRepositionSettings.defaults());
    }

    public PreOpenRepositionSettings settings() {
        return new PreOpenRepositionSettings(
                enabledCheckbox.isSelected(),
                parsePositiveInt(minutesField.getText(), PreOpenRepositionSettings.DEFAULT_MINUTES_BEFORE_OPEN),
                dayOrdersOnlyCheckbox.isSelected()
        );
    }

    public void populate(PreOpenRepositionSettings settings) {
        PreOpenRepositionSettings safe = settings == null ? PreOpenRepositionSettings.defaults() : settings;
        enabledCheckbox.setSelected(safe.enabled());
        minutesField.setText(String.valueOf(safe.minutesBeforeOpen()));
        dayOrdersOnlyCheckbox.setSelected(safe.dayOrdersOnly());
        updateControlState();
    }

    private void buildUi() {
        addFormRow(0, "Pre-open Reposition:", enabledCheckbox);
        addFormRow(1, "", mutedDescription(
                "A DAY limit buy that never fills dies at the close and the strategy sits expired until someone "
                        + "notices. With this on, every expired entry is re-posted automatically before the next "
                        + "open, with its base buy recalculated from the session that just closed — a touch under "
                        + "yesterday's close, never below the week's low, and never above the close itself."));
        addFormRow(2, "Minutes before the open:", minutesField);
        addFormRow(3, "", mutedDescription(
                "Between " + PreOpenRepositionSettings.MIN_MINUTES_BEFORE_OPEN + " and "
                        + PreOpenRepositionSettings.MAX_MINUTES_BEFORE_OPEN + " minutes; "
                        + PreOpenRepositionSettings.DEFAULT_MINUTES_BEFORE_OPEN + " by default. Weekends and market "
                        + "holidays are skipped, and NeuralArc must be running at that time."));
        addFormRow(4, "Which entries:", dayOrdersOnlyCheckbox);
        addFormRow(5, "", mutedDescription(
                "Unchecked, every expired entry is re-posted whatever its time in force. A GTC order is still "
                        + "working at the broker and has missed nothing, which is why DAY entries alone are the "
                        + "default."));
        enabledCheckbox.addActionListener(event -> updateControlState());
    }

    private void updateControlState() {
        boolean enabled = enabledCheckbox.isSelected();
        setEnabledWithStyle(minutesField, enabled);
        dayOrdersOnlyCheckbox.setEnabled(enabled);
        dayOrdersOnlyCheckbox.setForeground(enabled ? TEXT_PRIMARY : TEXT_MUTED);
    }

    private int parsePositiveInt(String value, int fallback) {
        try {
            int parsed = Integer.parseInt(value == null ? "" : value.trim());
            return parsed > 0 ? parsed : fallback;
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
