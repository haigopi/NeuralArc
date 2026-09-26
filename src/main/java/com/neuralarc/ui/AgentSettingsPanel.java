package com.neuralarc.ui;

import com.neuralarc.model.AgentSettings;
import com.neuralarc.util.FontLoader;
import com.neuralarc.util.ThemeColors;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
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
 * Settings for the read-only AI analyst agent: the Anthropic key, the model, and the two limits that
 * cap what one run may spend. Laid out like {@link PositionValidationSettingsPanel} so the settings
 * screen reads as one page.
 *
 * <p>The key is typed into a password field and stored encrypted, like the broker credentials, and
 * is never written to the log or to a strategy record.
 */
public class AgentSettingsPanel extends JPanel {
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

    private final JCheckBox enabledCheckbox = new JCheckBox("Enable the AI analyst", false);
    private final JPasswordField apiKeyField = new JPasswordField(25);
    private final JTextField modelField = new JTextField(18);
    private final JTextField maxTurnsField = new JTextField(6);
    private final JTextField maxToolCallsField = new JTextField(6);

    public AgentSettingsPanel() {
        super(new GridBagLayout());
        setOpaque(false);
        setBorder(createSectionBorder("AI Analyst Agent (read-only)"));
        buildUi();
        applyThemeRecursively(this);
        populate(AgentSettings.defaults());
    }

    public AgentSettings settings() {
        return new AgentSettings(
                enabledCheckbox.isSelected(),
                new String(apiKeyField.getPassword()),
                modelField.getText(),
                parsePositiveInt(maxTurnsField.getText(), AgentSettings.DEFAULT_MAX_TURNS),
                parsePositiveInt(maxToolCallsField.getText(), AgentSettings.DEFAULT_MAX_TOOL_CALLS)
        );
    }

    public void populate(AgentSettings settings) {
        AgentSettings safe = settings == null ? AgentSettings.defaults() : settings;
        enabledCheckbox.setSelected(safe.enabled());
        apiKeyField.setText(safe.apiKey());
        modelField.setText(safe.model());
        maxTurnsField.setText(String.valueOf(safe.maxTurns()));
        maxToolCallsField.setText(String.valueOf(safe.maxToolCalls()));
        updateControlState();
    }

    private void buildUi() {
        addFormRow(0, "AI Analyst:", enabledCheckbox);
        addFormRow(1, "", mutedDescription(
                "Asks Claude to study your open positions and the market using NeuralArc's own data, then write "
                        + "what it sees. It can only read: it cannot place, change or cancel an order, and every "
                        + "recommendation still has to be reviewed and saved by you."));
        addFormRow(2, "Anthropic API Key:", apiKeyField);
        addFormRow(3, "", mutedDescription(
                "Stored encrypted in your local database, like your broker keys. Each run is billed to this key "
                        + "by Anthropic."));
        addFormRow(4, "Model:", modelField);
        addFormRow(5, "Max Exchanges Per Run:", maxTurnsField);
        addFormRow(6, "Max Tool Calls Per Run:", maxToolCallsField);
        addFormRow(7, "", mutedDescription(
                "Cost ceilings. A run stops at whichever limit it reaches first and reports what it found so far, "
                        + "so a looping analyst cannot keep spending. Defaults: "
                        + AgentSettings.DEFAULT_MAX_TURNS + " exchanges, "
                        + AgentSettings.DEFAULT_MAX_TOOL_CALLS + " tool calls."));
        enabledCheckbox.addActionListener(event -> updateControlState());
    }

    private void updateControlState() {
        boolean enabled = enabledCheckbox.isSelected();
        setEnabledWithStyle(apiKeyField, enabled);
        setEnabledWithStyle(modelField, enabled);
        setEnabledWithStyle(maxTurnsField, enabled);
        setEnabledWithStyle(maxToolCallsField, enabled);
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
