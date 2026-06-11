package com.neuralarc.ui;

import com.neuralarc.model.AiProviderHealthStatus;
import com.neuralarc.model.AiProviderType;
import com.neuralarc.model.AiRecommendationSettings;
import com.neuralarc.service.AiRecommendationProvider;
import com.neuralarc.service.AiRecommendationProviderFactory;
import com.neuralarc.util.FontLoader;
import com.neuralarc.util.ThemeColors;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.Duration;

public class AiRecommendationSettingsPanel extends JPanel {
    private static final int FIELD_GAP = 10;
    private static final int SECTION_INNER_PADDING = 10;
    private static final int FORM_LABEL_COLUMN_WIDTH = 210;
    private static final int FORM_VALUE_COLUMN_WIDTH = 320;
    private static final Color DIALOG_BG = UIManager.getColor("Panel.background") != null
            ? UIManager.getColor("Panel.background")
            : Color.WHITE;
    private static final Color INPUT_BG = UIManager.getColor("TextField.background") != null
            ? UIManager.getColor("TextField.background")
            : Color.WHITE;
    private static final Color INPUT_BORDER = ThemeColors.color("NeuralArc.Input.border", new Color(190, 190, 200));
    private static final Color INPUT_DISABLED_BG = new Color(240, 242, 246);
    private static final Color INPUT_DISABLED_BORDER = ThemeColors.color("NeuralArc.Input.border", new Color(214, 218, 225));
    private static final Color INPUT_DISABLED_TEXT = new Color(142, 148, 160);
    private static final Color TEXT_PRIMARY = UIManager.getColor("Label.foreground") != null
            ? UIManager.getColor("Label.foreground")
            : new Color(45, 45, 50);
    private static final Color TEXT_MUTED = UIManager.getColor("Label.disabledForeground") != null
            ? UIManager.getColor("Label.disabledForeground")
            : new Color(130, 130, 130);

    private final JRadioButton jetsonAiProvider = new JRadioButton("Local Jetson AI - Network", true);
    private final JRadioButton openAiProvider = new JRadioButton("OpenAI API", false);
    private final JTextField jetsonHostField = new JTextField(18);
    private final JTextField jetsonPortField = new JTextField(6);
    private final JTextField jetsonApiPathField = new JTextField(20);
    private final JTextField jetsonConnectTimeoutField = new JTextField(6);
    private final JTextField jetsonReadTimeoutField = new JTextField(6);
    private final JPasswordField openAiApiKeyField = new JPasswordField(25);
    private final JTextField openAiModelField = new JTextField(16);
    private final JTextField openAiTimeoutField = new JTextField(6);
    private final JButton testJetsonAiButton = new JButton("Test Jetson Connection");
    private final JButton testOpenAiButton = new JButton("Test OpenAI Connection");
    private final JLabel statusLabel = new JLabel("AI provider not tested");

    public AiRecommendationSettingsPanel() {
        super(new GridBagLayout());
        setOpaque(false);
        setBorder(createSectionBorder("AI Recommendation Provider"));
        buildUi();
        applyThemeRecursively(this);
        populate(AiRecommendationSettings.defaults());
    }

    public AiRecommendationSettings settings() {
        return new AiRecommendationSettings(
                selectedProviderType(),
                jetsonHostField.getText(),
                parsePositiveInt(jetsonPortField.getText(), AiRecommendationSettings.defaults().jetsonPort()),
                jetsonApiPathField.getText(),
                parseSeconds(jetsonConnectTimeoutField.getText(), AiRecommendationSettings.defaults().jetsonConnectionTimeout()),
                parseSeconds(jetsonReadTimeoutField.getText(), AiRecommendationSettings.defaults().jetsonReadTimeout()),
                new String(openAiApiKeyField.getPassword()),
                openAiModelField.getText(),
                parseSeconds(openAiTimeoutField.getText(), AiRecommendationSettings.defaults().openAiTimeout())
        );
    }

    public void populate(AiRecommendationSettings settings) {
        AiRecommendationSettings safe = settings == null ? AiRecommendationSettings.defaults() : settings;
        jetsonAiProvider.setSelected(safe.providerType() == AiProviderType.JETSON_LOCAL);
        openAiProvider.setSelected(safe.providerType() == AiProviderType.OPENAI);
        jetsonHostField.setText(safe.jetsonHost());
        jetsonPortField.setText(String.valueOf(safe.jetsonPort()));
        jetsonApiPathField.setText(safe.jetsonApiPath());
        jetsonConnectTimeoutField.setText(String.valueOf(safe.jetsonConnectionTimeout().toSeconds()));
        jetsonReadTimeoutField.setText(String.valueOf(safe.jetsonReadTimeout().toSeconds()));
        openAiApiKeyField.setText(safe.openAiApiKey());
        openAiModelField.setText(safe.openAiModel());
        openAiTimeoutField.setText(String.valueOf(safe.openAiTimeout().toSeconds()));
        statusLabel.setText(statusTextFor(safe));
        statusLabel.setForeground(TEXT_MUTED);
        updateControlState();
    }

    private void buildUi() {
        ButtonGroup aiProviderGroup = new ButtonGroup();
        aiProviderGroup.add(jetsonAiProvider);
        aiProviderGroup.add(openAiProvider);

        JPanel providerChoices = new JPanel(new FlowLayout(FlowLayout.LEFT, FIELD_GAP, 0));
        providerChoices.setOpaque(false);
        providerChoices.add(jetsonAiProvider);
        providerChoices.add(openAiProvider);
        addFormRow(0, "Provider:", providerChoices, false);

        JLabel description = mutedDescription(
                "Choose one AI provider for stock recommendations. AI output is decision support only and never places trades automatically."
        );
        addFormRow(1, "", description, false);
        addFormRow(2, "Jetson Host/IP:", jetsonHostField, true);
        addFormRow(3, "Jetson Port:", jetsonPortField, true);
        addFormRow(4, "Jetson API Path:", jetsonApiPathField, true);
        addFormRow(5, "Jetson Connection Timeout:", jetsonConnectTimeoutField, true);
        addFormRow(6, "Jetson Read Timeout:", jetsonReadTimeoutField, true);
        addFormRow(7, "OpenAI API Key:", openAiApiKeyField, true);
        addFormRow(8, "OpenAI Model:", openAiModelField, true);
        addFormRow(9, "OpenAI Timeout:", openAiTimeoutField, true);

        JPanel actions = new JPanel(new BorderLayout(FIELD_GAP, 0));
        actions.setOpaque(false);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        DialogButtonStyles.apply(testJetsonAiButton, "icons/verify.svg");
        DialogButtonStyles.apply(testOpenAiButton, "icons/verify.svg");
        testJetsonAiButton.addActionListener(e -> testProvider(AiProviderType.JETSON_LOCAL));
        testOpenAiButton.addActionListener(e -> testProvider(AiProviderType.OPENAI));
        buttons.add(testJetsonAiButton);
        buttons.add(testOpenAiButton);
        actions.add(statusLabel, BorderLayout.CENTER);
        actions.add(buttons, BorderLayout.EAST);
        addFormRow(10, "Status:", actions, false);

        jetsonAiProvider.addActionListener(e -> updateControlState());
        openAiProvider.addActionListener(e -> updateControlState());
    }

    private void testProvider(AiProviderType providerType) {
        AiRecommendationSettings settings = settings();
        if (providerType == AiProviderType.JETSON_LOCAL && settings.jetsonHost().isBlank()) {
            markStatus(false, "Jetson: Host Required");
            return;
        }
        if (providerType == AiProviderType.OPENAI && settings.openAiApiKey().isBlank()) {
            markStatus(false, "OpenAI: Missing API Key");
            return;
        }
        setTestButtonsEnabled(false);
        statusLabel.setText(providerType == AiProviderType.OPENAI ? "OpenAI: Checking..." : "Jetson: Checking...");
        statusLabel.setForeground(TEXT_MUTED);
        SwingWorker<AiProviderHealthStatus, Void> worker = new SwingWorker<>() {
            @Override
            protected AiProviderHealthStatus doInBackground() {
                AiRecommendationSettings providerSettings = new AiRecommendationSettings(
                        providerType,
                        settings.jetsonHost(),
                        settings.jetsonPort(),
                        settings.jetsonApiPath(),
                        settings.jetsonConnectionTimeout(),
                        settings.jetsonReadTimeout(),
                        settings.openAiApiKey(),
                        settings.openAiModel(),
                        settings.openAiTimeout()
                );
                AiRecommendationProvider provider = AiRecommendationProviderFactory.create(providerSettings);
                return provider.healthCheck();
            }

            @Override
            protected void done() {
                try {
                    AiProviderHealthStatus status = get();
                    markStatus(status.healthy(), status.statusText());
                } catch (Exception ex) {
                    markStatus(false, providerType == AiProviderType.OPENAI ? "OpenAI: Check Failed" : "Jetson: Unreachable");
                } finally {
                    setTestButtonsEnabled(true);
                }
            }
        };
        worker.execute();
    }

    private void updateControlState() {
        boolean jetsonSelected = selectedProviderType() == AiProviderType.JETSON_LOCAL;
        setEnabledWithStyle(jetsonHostField, jetsonSelected);
        setEnabledWithStyle(jetsonPortField, jetsonSelected);
        setEnabledWithStyle(jetsonApiPathField, jetsonSelected);
        setEnabledWithStyle(jetsonConnectTimeoutField, jetsonSelected);
        setEnabledWithStyle(jetsonReadTimeoutField, jetsonSelected);
        setEnabledWithStyle(openAiApiKeyField, !jetsonSelected);
        setEnabledWithStyle(openAiModelField, !jetsonSelected);
        setEnabledWithStyle(openAiTimeoutField, !jetsonSelected);
        setTestButtonsEnabled(true);
        statusLabel.setText(statusTextFor(settings()));
        statusLabel.setForeground(TEXT_MUTED);
    }

    private AiProviderType selectedProviderType() {
        return openAiProvider.isSelected() ? AiProviderType.OPENAI : AiProviderType.JETSON_LOCAL;
    }

    private void setTestButtonsEnabled(boolean enabled) {
        boolean jetsonSelected = selectedProviderType() == AiProviderType.JETSON_LOCAL;
        testJetsonAiButton.setEnabled(enabled && jetsonSelected);
        testOpenAiButton.setEnabled(enabled && !jetsonSelected);
    }

    private void markStatus(boolean healthy, String message) {
        statusLabel.setText(message);
        statusLabel.setForeground(healthy ? new Color(34, 139, 34) : new Color(180, 30, 30));
    }

    private String statusTextFor(AiRecommendationSettings settings) {
        if (settings.providerType() == AiProviderType.OPENAI) {
            return settings.openAiApiKey().isBlank() ? "OpenAI: Missing API Key" : "OpenAI: Configured";
        }
        return settings.jetsonHost().isBlank() ? "Jetson: Unreachable" : "Jetson: Not Tested";
    }

    private int parsePositiveInt(String value, int fallback) {
        try {
            int parsed = Integer.parseInt(value == null ? "" : value.trim());
            return parsed > 0 ? parsed : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private Duration parseSeconds(String value, Duration fallback) {
        try {
            long seconds = Long.parseLong(value == null ? "" : value.trim());
            return seconds > 0 ? Duration.ofSeconds(seconds) : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private void addFormRow(int row, String labelText, Component valueComponent, boolean singleLine) {
        JLabel label = new JLabel(labelText);
        label.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        label.setPreferredSize(new java.awt.Dimension(FORM_LABEL_COLUMN_WIDTH, label.getPreferredSize().height));
        GridBagConstraints labelGbc = new GridBagConstraints();
        labelGbc.gridx = 0;
        labelGbc.gridy = row;
        labelGbc.weightx = 0;
        labelGbc.fill = GridBagConstraints.NONE;
        labelGbc.anchor = GridBagConstraints.NORTHWEST;
        labelGbc.insets = new Insets(0, 0, FIELD_GAP, FIELD_GAP);

        GridBagConstraints valueGbc = new GridBagConstraints();
        valueGbc.gridx = 1;
        valueGbc.gridy = row;
        valueGbc.weightx = 1;
        valueGbc.fill = singleLine ? GridBagConstraints.HORIZONTAL : GridBagConstraints.BOTH;
        valueGbc.anchor = GridBagConstraints.NORTHWEST;
        valueGbc.insets = new Insets(0, 0, FIELD_GAP, 0);

        if (singleLine && valueComponent instanceof javax.swing.JComponent component) {
            constrainFormValueWidth(component);
        }
        add(label, labelGbc);
        add(valueComponent, valueGbc);
    }

    private void constrainFormValueWidth(javax.swing.JComponent component) {
        if (component instanceof JTextField || component instanceof javax.swing.JComboBox<?>) {
            java.awt.Dimension preferred = component.getPreferredSize();
            int width = Math.min(FORM_VALUE_COLUMN_WIDTH, preferred.width > 0 ? preferred.width : FORM_VALUE_COLUMN_WIDTH);
            component.setPreferredSize(new java.awt.Dimension(width, preferred.height));
            component.setMaximumSize(new java.awt.Dimension(width, preferred.height));
        }
    }

    private JLabel mutedDescription(String text) {
        JLabel label = new JLabel("<html><div style='width:360px;'>" + text + "</div></html>");
        label.setForeground(TEXT_MUTED);
        label.setFont(FontLoader.ui(java.awt.Font.PLAIN, 10f));
        return label;
    }

    private void applyThemeRecursively(Component component) {
        component.setFont(FontLoader.ui(component.getFont().getStyle(), component.getFont().getSize2D()));
        if (component instanceof JTextField input) {
            styleInput(input);
        }
        if (component instanceof JRadioButton radioButton) {
            radioButton.setBackground(DIALOG_BG);
            radioButton.setForeground(TEXT_PRIMARY);
        }
        if (component instanceof JButton button) {
            button.setFont(FontLoader.ui(java.awt.Font.BOLD, 12f));
        }
        if (component instanceof java.awt.Container container) {
            for (Component child : container.getComponents()) {
                applyThemeRecursively(child);
            }
        }
    }

    private void styleInput(JTextField input) {
        input.setBackground(INPUT_BG);
        input.setForeground(TEXT_PRIMARY);
        input.setCaretColor(TEXT_PRIMARY);
        input.setDisabledTextColor(INPUT_DISABLED_TEXT);
        input.setSelectionColor(new Color(114, 130, 176));
        input.setSelectedTextColor(TEXT_PRIMARY);
        input.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(INPUT_BORDER, 1, true),
                new EmptyBorder(4, 8, 4, 8)
        ));
    }

    private void setEnabledWithStyle(JTextField input, boolean enabled) {
        input.setEnabled(enabled);
        input.setBackground(enabled ? INPUT_BG : INPUT_DISABLED_BG);
        input.setForeground(enabled ? TEXT_PRIMARY : INPUT_DISABLED_TEXT);
        input.setCaretColor(enabled ? TEXT_PRIMARY : INPUT_DISABLED_TEXT);
        input.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(enabled ? INPUT_BORDER : INPUT_DISABLED_BORDER, 1, true),
                new EmptyBorder(4, 8, 4, 8)
        ));
    }

    private Border createSectionBorder(String title) {
        TitledBorder border = new TitledBorder(title);
        border.setTitleFont(FontLoader.ui(java.awt.Font.BOLD, 12f));
        border.setTitleColor(ThemeColors.color("NeuralArc.Section.titleForeground", TEXT_PRIMARY));
        return BorderFactory.createCompoundBorder(border, new EmptyBorder(
                SECTION_INNER_PADDING,
                SECTION_INNER_PADDING,
                SECTION_INNER_PADDING,
                SECTION_INNER_PADDING
        ));
    }
}
