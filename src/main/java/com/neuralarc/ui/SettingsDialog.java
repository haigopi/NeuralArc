package com.neuralarc.ui;

import com.neuralarc.model.BrokerType;
import com.neuralarc.model.ApplicationMode;
import com.neuralarc.security.CredentialManager;
import com.neuralarc.service.UserIdentityService;
import com.neuralarc.util.FontLoader;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;

public class SettingsDialog extends JDialog {
    private static final Path SETTINGS_FILE = Path.of(System.getProperty("user.home"), ".neuralarc", "settings.properties");
    private static final Path CREDENTIALS_FILE_PAPER = Path.of(System.getProperty("user.home"), ".neuralarc", "credentials-paper.properties");
    private static final Path CREDENTIALS_FILE_LIVE = Path.of(System.getProperty("user.home"), ".neuralarc", "credentials-live.properties");
    private static final Path LEGACY_CREDENTIALS_FILE = Path.of(System.getProperty("user.home"), ".neuralarc", "credentials.properties");
    private static final int OUTER_PADDING = 16;
    private static final int SECTION_GAP = 12;
    private static final int FIELD_GAP = 10;
    private static final int SECTION_INNER_PADDING = 10;
    private static final Color DIALOG_BG = UIManager.getColor("Panel.background") != null
            ? UIManager.getColor("Panel.background")
            : Color.WHITE;
    private static final Color INPUT_BG = UIManager.getColor("TextField.background") != null
            ? UIManager.getColor("TextField.background")
            : Color.WHITE;
    private static final Color INPUT_BORDER = new Color(190, 190, 200);
    private static final Color TEXT_PRIMARY = UIManager.getColor("Label.foreground") != null
            ? UIManager.getColor("Label.foreground")
            : new Color(45, 45, 50);
    private static final Color TEXT_MUTED = UIManager.getColor("Label.disabledForeground") != null
            ? UIManager.getColor("Label.disabledForeground")
            : new Color(130, 130, 130);

    private final JTextField emailField = new JTextField(25);
    private final JTextField apiKeyField = new JTextField(25);
    private final JPasswordField apiSecretField = new JPasswordField(25);
    private final JLabel applicationModeLabel = new JLabel("Application mode:");
    private final JLabel apiKeyLabel = new JLabel("API key:");
    private final JLabel apiSecretLabel = new JLabel("API secret:");
    private final JTextField endpointField = new JTextField("http://localhost:8080/events", 25);
    private final JCheckBox telemetryEnabled = new JCheckBox("Enable telemetry", false);
    private final JCheckBox saveCredentials = new JCheckBox("Save credentials locally", false);
    private final JButton verifyConnectionButton = new JButton("Verify Connection");
    private final JLabel connectionStatus = new JLabel("Connection not verified");
    private final JComboBox<BrokerType> brokerBox = new JComboBox<>(BrokerType.values());
    private final JComboBox<ApplicationMode> appModeBox = new JComboBox<>(ApplicationMode.values());
    private final CredentialManager credentialManager = new CredentialManager();
    private final UserIdentityService identityService = new UserIdentityService();
    private transient Function<ConnectionRequest, ConnectionResult> connectionVerifier;
    private final Map<ApplicationMode, String[]> credentialCache = new EnumMap<>(ApplicationMode.class);
    private ApplicationMode displayedCredentialMode = ApplicationMode.PAPER;
    private boolean suppressModeSwitchHandling;
    private final Color defaultApiLabelColor = UIManager.getColor("Label.foreground");
    private final Color mockStatusMutedColor = UIManager.getColor("Label.disabledForeground") != null
            ? UIManager.getColor("Label.disabledForeground")
            : new Color(130, 130, 130);

    public SettingsDialog(JFrame owner) {
        super(owner, "Settings", true);
        setLayout(new BorderLayout(SECTION_GAP, SECTION_GAP));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(OUTER_PADDING, OUTER_PADDING, 0, OUTER_PADDING));

        JPanel userPanel = new JPanel(new GridLayout(0, 2, FIELD_GAP, FIELD_GAP));
        userPanel.setBorder(createSectionBorder("User Details"));
        userPanel.add(new JLabel("User Email:"));
        userPanel.add(emailField);

        JPanel apiPanel = new JPanel(new GridLayout(0, 2, FIELD_GAP, FIELD_GAP));
        apiPanel.setBorder(createSectionBorder("Alpaca API Details"));
        apiPanel.add(new JLabel("Broker:"));
        apiPanel.add(brokerBox);
        apiPanel.add(applicationModeLabel);
        apiPanel.add(appModeBox);
        apiPanel.add(apiKeyLabel);
        apiPanel.add(apiKeyField);
        apiPanel.add(apiSecretLabel);
        apiPanel.add(apiSecretField);
        saveCredentials.setSelected(true);
        saveCredentials.setEnabled(false);
        apiPanel.add(new JLabel(""));
        apiPanel.add(saveCredentials);
        verifyConnectionButton.addActionListener(e -> verifyConnection());
        brokerBox.addActionListener(e -> updateBrokerControlState());
        appModeBox.addActionListener(e -> onModeChanged());
        apiPanel.add(connectionStatus);
        apiPanel.add(verifyConnectionButton);

        JPanel telemetryPanel = new JPanel(new GridLayout(0, 1, FIELD_GAP, FIELD_GAP));
        telemetryPanel.setBorder(createSectionBorder("Telemetry"));
        telemetryPanel.add(new JLabel("Analytics Endpoint URL:"));
        telemetryPanel.add(endpointField);
        telemetryPanel.add(telemetryEnabled);

        content.add(userPanel);
        content.add(Box.createVerticalStrut(SECTION_GAP));
        content.add(apiPanel);
        content.add(Box.createVerticalStrut(SECTION_GAP));
        content.add(telemetryPanel);

        add(content, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.setBorder(new EmptyBorder(0, OUTER_PADDING, OUTER_PADDING, OUTER_PADDING));
        JButton encryptSave = new JButton("Encrypt, Save and Close");
        encryptSave.addActionListener(e -> {
            if (saveAll()) {
                closeDialog();
            }
        });
        JButton close = new JButton("Close");
        close.addActionListener(e -> closeDialog());
        actions.add(encryptSave);
        actions.add(close);
        add(actions, BorderLayout.SOUTH);

        applyDialogTheme();

        loadAll();
        updateBrokerControlState();
        pack();
        setLocationRelativeTo(owner);
    }

    public void setConnectionVerifier(Function<ConnectionRequest, ConnectionResult> connectionVerifier) {
        this.connectionVerifier = connectionVerifier;
    }

    public String getEndpoint() { return endpointField.getText().trim(); }
    public boolean telemetryEnabled() { return telemetryEnabled.isSelected(); }
    public boolean saveCredentials() { return saveCredentials.isSelected(); }
    public BrokerType brokerType() { return (BrokerType) brokerBox.getSelectedItem(); }
    public ApplicationMode applicationMode() {
        ApplicationMode mode = (ApplicationMode) appModeBox.getSelectedItem();
        return mode == null ? ApplicationMode.PAPER : mode;
    }
    public String getUserEmail() { return emailField.getText().trim(); }
    public String getApiKey() { return apiKeyField.getText().trim(); }
    public String getApiSecret() { return new String(apiSecretField.getPassword()); }
    public boolean hasRequiredSettings() {
        if (getUserEmail().isBlank()) {
            return false;
        }
        if (brokerType() == BrokerType.MOCK) {
            return true;
        }
        return !getApiKey().isBlank() && !getApiSecret().isBlank();
    }

    public void markConnectionStatus(boolean connected, String message) {
        connectionStatus.setText(message);
        connectionStatus.setForeground(connected ? new Color(34, 139, 34) : new Color(180, 30, 30));
    }

    private boolean saveAll() {
        String email = getUserEmail();
        if (email.isBlank()) {
            JOptionPane.showMessageDialog(this, "Please enter user email before saving.", "Missing Email", JOptionPane.WARNING_MESSAGE);
            return false;
        }

        cacheCurrentModeCredentials();
        String paperKey = apiKeyForMode(ApplicationMode.PAPER, email);
        String liveKey = apiKeyForMode(ApplicationMode.LIVE, email);
        if (!paperKey.isBlank() && !liveKey.isBlank() && paperKey.equals(liveKey)) {
            JOptionPane.showMessageDialog(this,
                    "Paper and Live API keys must be different.",
                    "Duplicate API Key",
                    JOptionPane.WARNING_MESSAGE);
            return false;
        }

        try {
            Files.createDirectories(SETTINGS_FILE.getParent());
            Properties settings = new Properties();
            settings.setProperty("userEmail", email);
            settings.setProperty("endpoint", getEndpoint());
            settings.setProperty("telemetryEnabled", String.valueOf(telemetryEnabled()));
            settings.setProperty("saveCredentials", "true");
            settings.setProperty("broker", brokerType() == null ? BrokerType.MOCK.name() : brokerType().name());
            settings.setProperty("applicationMode", applicationMode().name());
            try (var out = Files.newOutputStream(SETTINGS_FILE)) {
                settings.store(out, "NeuralArc settings");
            }

            for (ApplicationMode mode : ApplicationMode.values()) {
                String[] creds = credentialCache.get(mode);
                if (creds == null) {
                    continue;
                }
                String key = creds[0] == null ? "" : creds[0].trim();
                String secret = creds[1] == null ? "" : creds[1];
                if (key.isBlank() || secret.isBlank()) {
                    continue;
                }
                credentialManager.save(key, secret.toCharArray(), credentialFileForMode(mode), buildPassphrase(email));
            }
            JOptionPane.showMessageDialog(this, "Settings saved successfully.", "Saved", JOptionPane.INFORMATION_MESSAGE);
            return true;
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Failed to save settings.", "Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private void loadAll() {
        suppressModeSwitchHandling = true;
        if (Files.exists(SETTINGS_FILE)) {
            Properties settings = new Properties();
            try (var in = Files.newInputStream(SETTINGS_FILE)) {
                settings.load(in);
                emailField.setText(settings.getProperty("userEmail", ""));
                endpointField.setText(settings.getProperty("endpoint", endpointField.getText()));
                telemetryEnabled.setSelected(Boolean.parseBoolean(settings.getProperty("telemetryEnabled", "false")));
                saveCredentials.setSelected(true);
                String broker = settings.getProperty("broker", BrokerType.MOCK.name());
                brokerBox.setSelectedItem(BrokerType.valueOf(broker));
                String mode = settings.getProperty("applicationMode", ApplicationMode.PAPER.name());
                appModeBox.setSelectedItem(ApplicationMode.valueOf(mode));
            } catch (Exception ignored) {
                appModeBox.setSelectedItem(ApplicationMode.PAPER);
            }
        }

        String email = getUserEmail();
        if (!email.isBlank()) {
            for (ApplicationMode mode : ApplicationMode.values()) {
                credentialCache.put(mode, loadCredentialsForMode(mode, email));
            }
            // Legacy migration fallback for first run after upgrade.
            String[] paperCreds = credentialCache.get(ApplicationMode.PAPER);
            boolean hasPaperCreds = paperCreds != null && !paperCreds[0].isBlank() && !paperCreds[1].isBlank();
            if (!hasPaperCreds && Files.exists(LEGACY_CREDENTIALS_FILE)) {
                credentialManager.load(LEGACY_CREDENTIALS_FILE, buildPassphrase(email))
                        .ifPresent(creds -> credentialCache.put(ApplicationMode.PAPER, creds));
            }
        }
        displayedCredentialMode = applicationMode();
        applyModeCredentialsToFields(displayedCredentialMode);
        suppressModeSwitchHandling = false;
    }

    private void onModeChanged() {
        if (suppressModeSwitchHandling) {
            return;
        }
        cacheCurrentModeCredentials();
        ApplicationMode selectedMode = applicationMode();
        applyModeCredentialsToFields(selectedMode);
        displayedCredentialMode = selectedMode;
    }

    private void cacheCurrentModeCredentials() {
        credentialCache.put(displayedCredentialMode, new String[]{getApiKey(), getApiSecret()});
    }

    private void applyModeCredentialsToFields(ApplicationMode mode) {
        String[] creds = credentialCache.get(mode);
        if (creds == null) {
            String email = getUserEmail();
            creds = email.isBlank() ? new String[]{"", ""} : loadCredentialsForMode(mode, email);
            credentialCache.put(mode, creds);
        }
        apiKeyField.setText(creds[0]);
        apiSecretField.setText(creds[1]);
    }

    private String[] loadCredentialsForMode(ApplicationMode mode, String email) {
        return credentialManager.load(credentialFileForMode(mode), buildPassphrase(email))
                .orElse(new String[]{"", ""});
    }

    private String apiKeyForMode(ApplicationMode mode, String email) {
        String[] creds = credentialCache.get(mode);
        if (creds != null && creds[0] != null && !creds[0].isBlank()) {
            return creds[0].trim();
        }
        if (email.isBlank()) {
            return "";
        }
        return credentialManager.load(credentialFileForMode(mode), buildPassphrase(email))
                .map(v -> v[0].trim())
                .orElse("");
    }

    private Path credentialFileForMode(ApplicationMode mode) {
        return mode == ApplicationMode.LIVE ? CREDENTIALS_FILE_LIVE : CREDENTIALS_FILE_PAPER;
    }

    private String buildPassphrase(String email) {
        return identityService.generateUserId(email).substring(0, 16);
    }

    private void closeDialog() {
        setVisible(false);
    }

    private void verifyConnection() {
        if (brokerType() == BrokerType.MOCK) {
            markConnectionStatus(true, "MOCK broker selected (no API verification required)");
            return;
        }
        if (connectionVerifier == null) {
            markConnectionStatus(false, "Verification unavailable");
            return;
        }
        ConnectionResult result = connectionVerifier.apply(new ConnectionRequest(brokerType(), getApiKey(), getApiSecret()));
        markConnectionStatus(result.connected(), result.message());
    }

    private void updateBrokerControlState() {
        boolean alpacaSelected = brokerType() == BrokerType.ALPACA;
        apiKeyField.setEnabled(alpacaSelected);
        apiSecretField.setEnabled(alpacaSelected);
        appModeBox.setEnabled(alpacaSelected);
        verifyConnectionButton.setEnabled(alpacaSelected);
        Color labelColor = alpacaSelected ? defaultApiLabelColor : TEXT_MUTED;
        applicationModeLabel.setForeground(labelColor);
        apiKeyLabel.setForeground(labelColor);
        apiSecretLabel.setForeground(labelColor);
        if (!alpacaSelected) {
            connectionStatus.setText("MOCK broker selected");
            connectionStatus.setForeground(mockStatusMutedColor);
        } else if ("MOCK broker selected".equals(connectionStatus.getText())) {
            connectionStatus.setText("Connection not verified");
            connectionStatus.setForeground(new Color(180, 30, 30));
        }
    }

    public record ConnectionRequest(BrokerType brokerType, String apiKey, String apiSecret) {}

    public record ConnectionResult(boolean connected, String message) {}

    private Border createSectionBorder(String title) {
        TitledBorder border = new TitledBorder(title);
        border.setTitleColor(TEXT_PRIMARY);
        border.setTitleFont(FontLoader.ui(Font.BOLD, 12f));
        return withInnerPadding(border);
    }

    private void applyDialogTheme() {
        applyThemeRecursively(getContentPane());
        getContentPane().setBackground(DIALOG_BG);
    }

    private void applyThemeRecursively(Component component) {
        component.setFont(FontLoader.ui(Font.PLAIN, 12f));
        if (component instanceof JPanel panel) {
            panel.setBackground(DIALOG_BG);
        }
        if (component instanceof JLabel label) {
            label.setForeground(TEXT_PRIMARY);
        }
        if (component instanceof JTextField field) {
            styleInput(field);
        }
        if (component instanceof JPasswordField field) {
            styleInput(field);
        }
        if (component instanceof JComboBox<?> comboBox) {
            comboBox.setBackground(INPUT_BG);
            comboBox.setForeground(TEXT_PRIMARY);
            comboBox.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(INPUT_BORDER, 1, true),
                    new EmptyBorder(3, 6, 3, 6)
            ));
        }
        if (component instanceof JCheckBox checkBox) {
            checkBox.setBackground(DIALOG_BG);
            checkBox.setForeground(TEXT_PRIMARY);
        }
        if (component instanceof JButton button) {
            button.setFont(FontLoader.ui(Font.BOLD, 12f));
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
        input.setSelectionColor(new Color(114, 130, 176));
        input.setSelectedTextColor(TEXT_PRIMARY);
        input.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(INPUT_BORDER, 1, true),
                new EmptyBorder(4, 8, 4, 8)
        ));
    }

    private static Border withInnerPadding(Border border) {
        return BorderFactory.createCompoundBorder(border, new EmptyBorder(SECTION_INNER_PADDING, SECTION_INNER_PADDING, SECTION_INNER_PADDING, SECTION_INNER_PADDING));
    }
}
