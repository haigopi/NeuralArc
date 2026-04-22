package com.neuralarc.ui;

import com.neuralarc.model.BrokerType;

import javax.swing.*;
import java.awt.*;

public class SettingsDialog extends JDialog {
    private final JTextField endpointField = new JTextField("http://localhost:8080/events", 25);
    private final JCheckBox telemetryEnabled = new JCheckBox("Enable telemetry", false);
    private final JCheckBox saveCredentials = new JCheckBox("Save credentials locally", false);
    private final JComboBox<BrokerType> brokerBox = new JComboBox<>(BrokerType.values());

    public SettingsDialog(JFrame owner) {
        super(owner, "Settings", true);
        setLayout(new GridLayout(0, 1, 8, 8));
        add(new JLabel("Analytics Endpoint URL:"));
        add(endpointField);
        add(telemetryEnabled);
        add(saveCredentials);
        add(new JLabel("Broker:"));
        add(brokerBox);
        JButton close = new JButton("Close");
        close.addActionListener(e -> setVisible(false));
        add(close);
        pack();
        setLocationRelativeTo(owner);
    }

    public String getEndpoint() { return endpointField.getText().trim(); }
    public boolean telemetryEnabled() { return telemetryEnabled.isSelected(); }
    public boolean saveCredentials() { return saveCredentials.isSelected(); }
    public BrokerType brokerType() { return (BrokerType) brokerBox.getSelectedItem(); }
}
