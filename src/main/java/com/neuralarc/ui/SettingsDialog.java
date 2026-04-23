package com.neuralarc.ui;

import com.neuralarc.model.BrokerType;
import com.neuralarc.security.CredentialManager;
import com.neuralarc.service.UserIdentityService;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.function.Function;

public class SettingsDialog extends JDialog {
    private static final Path SETTINGS_FILE = Path.of(System.getProperty("user.home"), ".neuralarc", "settings.properties");
    private static final Path CREDENTIALS_FILE = Path.of(System.getProperty("user.home"), ".neuralarc", "credentials.properties");
    private static final int OUTER_PADDING = 16;
    private static final int SECTION_GAP = 12;
    private static final int FIELD_GAP = 10;
    private static final int SECTION_INNER_PADDING = 10;

    private final JTextField emailField = new JTextField(25);
    private final JTextField apiKeyField = new JTextField(25);
    private final JPasswordField apiSecretField = new JPasswordField(25);
    private final JTextField endpointField = new JTextField("http://localhost:8080/events", 25);
    private final JCheckBox telemetryEnabled = new JCheckBox("Enable telemetry", false);
    private final JCheckBox saveCredentials = new JCheckBox("Save credentials locally", false);
    private final JButton verifyConnectionButton = new JButton("Verify Alpaca Connection");
    private final JLabel connectionStatus = new JLabel("Connection not verified");
    private final JComboBox<BrokerType> brokerBox = new JComboBox<>(BrokerType.values());
    private final CredentialManager credentialManager = new CredentialManager();
    private final UserIdentityService identityService = new UserIdentityService();
    private transient Function<ConnectionRequest, ConnectionResult> connectionVerifier;

    public SettingsDialog(JFrame owner) {
        super(owner, "Settings", true);
        setLayout(new BorderLayout(SECTION_GAP, SECTION_GAP));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(OUTER_PADDING, OUTER_PADDING, 0, OUTER_PADDING));

        JPanel userPanel = new JPanel(new GridLayout(0, 2, FIELD_GAP, FIELD_GAP));
        userPanel.setBorder(withInnerPadding(new TitledBorder("User Details")));
        userPanel.add(new JLabel("User email:"));
        userPanel.add(emailField);

        JPanel apiPanel = new JPanel(new GridLayout(0, 2, FIELD_GAP, FIELD_GAP));
        apiPanel.setBorder(withInnerPadding(new TitledBorder("Alpaca API Details")));
        apiPanel.add(new JLabel("Broker:"));
        apiPanel.add(brokerBox);
        apiPanel.add(new JLabel("API key:"));
        apiPanel.add(apiKeyField);
        apiPanel.add(new JLabel("API secret:"));
        apiPanel.add(apiSecretField);
        saveCredentials.setSelected(true);
        saveCredentials.setEnabled(false);
        apiPanel.add(new JLabel(""));
        apiPanel.add(saveCredentials);
        verifyConnectionButton.addActionListener(e -> verifyConnection());
        apiPanel.add(connectionStatus);
        apiPanel.add(verifyConnectionButton);

        JPanel telemetryPanel = new JPanel(new GridLayout(0, 1, FIELD_GAP, FIELD_GAP));
        telemetryPanel.setBorder(withInnerPadding(new TitledBorder("Telemetry")));
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
        JButton encryptSave = new JButton("Encrypt & Save");
        encryptSave.addActionListener(e -> saveAll());
        JButton close = new JButton("Close");
        close.addActionListener(e -> closeDialog());
        actions.add(encryptSave);
        actions.add(close);
        add(actions, BorderLayout.SOUTH);

        loadAll();
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
    public String getUserEmail() { return emailField.getText().trim(); }
    public String getApiKey() { return apiKeyField.getText().trim(); }
    public String getApiSecret() { return new String(apiSecretField.getPassword()); }
    public boolean hasRequiredSettings() {
        return !getUserEmail().isBlank()
                && !getApiKey().isBlank()
                && !getApiSecret().isBlank();
    }

    public void markConnectionStatus(boolean connected, String message) {
        connectionStatus.setText(message);
        connectionStatus.setForeground(connected ? new Color(34, 139, 34) : new Color(180, 30, 30));
    }

    private void saveAll() {
        String email = getUserEmail();
        if (email.isBlank()) {
            JOptionPane.showMessageDialog(this, "Please enter user email before saving.", "Missing Email", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            Files.createDirectories(SETTINGS_FILE.getParent());
            Properties settings = new Properties();
            settings.setProperty("userEmail", email);
            settings.setProperty("endpoint", getEndpoint());
            settings.setProperty("telemetryEnabled", String.valueOf(telemetryEnabled()));
            settings.setProperty("saveCredentials", "true");
            settings.setProperty("broker", brokerType() == null ? BrokerType.MOCK.name() : brokerType().name());
            try (var out = Files.newOutputStream(SETTINGS_FILE)) {
                settings.store(out, "NeuralArc settings");
            }

            credentialManager.save(getApiKey(), apiSecretField.getPassword(), CREDENTIALS_FILE, buildPassphrase(email));
            JOptionPane.showMessageDialog(this, "Settings saved successfully.", "Saved", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Failed to save settings.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadAll() {
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
            } catch (Exception ignored) {
            }
        }

        String email = getUserEmail();
        if (!email.isBlank()) {
            credentialManager.load(CREDENTIALS_FILE, buildPassphrase(email)).ifPresent(creds -> {
                apiKeyField.setText(creds[0]);
                apiSecretField.setText(creds[1]);
            });
        }
    }

    private String buildPassphrase(String email) {
        return identityService.generateUserId(email).substring(0, 16);
    }

    private void closeDialog() {
        setVisible(false);
    }

    private void verifyConnection() {
        if (connectionVerifier == null) {
            markConnectionStatus(false, "Verification unavailable");
            return;
        }
        ConnectionResult result = connectionVerifier.apply(new ConnectionRequest(brokerType(), getApiKey(), getApiSecret()));
        markConnectionStatus(result.connected(), result.message());
    }

    public record ConnectionRequest(BrokerType brokerType, String apiKey, String apiSecret) {}

    public record ConnectionResult(boolean connected, String message) {}

    private static Border withInnerPadding(Border border) {
        return BorderFactory.createCompoundBorder(border, new EmptyBorder(SECTION_INNER_PADDING, SECTION_INNER_PADDING, SECTION_INNER_PADDING, SECTION_INNER_PADDING));
    }
}
