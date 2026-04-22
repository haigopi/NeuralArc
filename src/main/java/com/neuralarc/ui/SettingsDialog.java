package com.neuralarc.ui;

import com.neuralarc.model.BrokerType;
import com.neuralarc.security.CredentialManager;
import com.neuralarc.service.UserIdentityService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class SettingsDialog extends JDialog {
    private static final Path SETTINGS_FILE = Path.of(System.getProperty("user.home"), ".neuralarc", "settings.properties");
    private static final Path CREDENTIALS_FILE = Path.of(System.getProperty("user.home"), ".neuralarc", "credentials.properties");

    private final JTextField emailField = new JTextField(25);
    private final JTextField apiKeyField = new JTextField(25);
    private final JPasswordField apiSecretField = new JPasswordField(25);
    private final JTextField endpointField = new JTextField("http://localhost:8080/events", 25);
    private final JCheckBox telemetryEnabled = new JCheckBox("Enable telemetry", false);
    private final JCheckBox saveCredentials = new JCheckBox("Save credentials locally", false);
    private final JComboBox<BrokerType> brokerBox = new JComboBox<>(BrokerType.values());
    private final CredentialManager credentialManager = new CredentialManager();
    private final UserIdentityService identityService = new UserIdentityService();

    public SettingsDialog(JFrame owner) {
        super(owner, "Settings", true);
        setLayout(new BorderLayout(10, 10));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(12, 12, 12, 12));

        JPanel userPanel = new JPanel(new GridLayout(0, 2, 8, 8));
        userPanel.setBorder(new TitledBorder("User Details"));
        userPanel.add(new JLabel("User email:"));
        userPanel.add(emailField);

        JPanel apiPanel = new JPanel(new GridLayout(0, 2, 8, 8));
        apiPanel.setBorder(new TitledBorder("API Details"));
        apiPanel.add(new JLabel("Broker:"));
        apiPanel.add(brokerBox);
        apiPanel.add(new JLabel("API key:"));
        apiPanel.add(apiKeyField);
        apiPanel.add(new JLabel("API secret:"));
        apiPanel.add(apiSecretField);
        apiPanel.add(new JLabel(""));
        apiPanel.add(saveCredentials);

        JPanel telemetryPanel = new JPanel(new GridLayout(0, 1, 8, 8));
        telemetryPanel.setBorder(new TitledBorder("Telemetry"));
        telemetryPanel.add(new JLabel("Analytics Endpoint URL:"));
        telemetryPanel.add(endpointField);
        telemetryPanel.add(telemetryEnabled);

        content.add(userPanel);
        content.add(Box.createVerticalStrut(10));
        content.add(apiPanel);
        content.add(Box.createVerticalStrut(10));
        content.add(telemetryPanel);

        add(content, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton encryptSave = new JButton("Encrypt & Save");
        encryptSave.addActionListener(e -> saveAll());
        JButton close = new JButton("Close");
        close.addActionListener(e -> setVisible(false));
        actions.add(encryptSave);
        actions.add(close);
        add(actions, BorderLayout.SOUTH);

        loadAll();
        pack();
        setLocationRelativeTo(owner);
    }

    public String getEndpoint() { return endpointField.getText().trim(); }
    public boolean telemetryEnabled() { return telemetryEnabled.isSelected(); }
    public boolean saveCredentials() { return saveCredentials.isSelected(); }
    public BrokerType brokerType() { return (BrokerType) brokerBox.getSelectedItem(); }
    public String getUserEmail() { return emailField.getText().trim(); }
    public String getApiKey() { return apiKeyField.getText().trim(); }
    public String getApiSecret() { return new String(apiSecretField.getPassword()); }

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
            settings.setProperty("saveCredentials", String.valueOf(saveCredentials()));
            settings.setProperty("broker", brokerType() == null ? BrokerType.MOCK.name() : brokerType().name());
            try (var out = Files.newOutputStream(SETTINGS_FILE)) {
                settings.store(out, "NeuralArc settings");
            }

            if (saveCredentials()) {
                credentialManager.save(getApiKey(), apiSecretField.getPassword(), CREDENTIALS_FILE, buildPassphrase(email));
            }
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
                saveCredentials.setSelected(Boolean.parseBoolean(settings.getProperty("saveCredentials", "false")));
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
}
