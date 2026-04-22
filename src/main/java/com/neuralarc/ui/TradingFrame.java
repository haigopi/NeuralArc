package com.neuralarc.ui;

import com.neuralarc.analytics.*;
import com.neuralarc.api.TradingApi;
import com.neuralarc.api.TradingApiFactory;
import com.neuralarc.model.*;
import com.neuralarc.rules.RuleEvaluationService;
import com.neuralarc.security.CredentialManager;
import com.neuralarc.service.PricePoller;
import com.neuralarc.service.TradingStrategyService;
import com.neuralarc.service.UserIdentityService;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.UUID;

public class TradingFrame extends JFrame {
    private static final Font BASE_FONT = createBaseFont();
    private static final int OUTER_PADDING = 16;

    private final JTextField emailField = new JTextField(20);
    private final JLabel userIdLabel = new JLabel("-");
    private final JTextField apiKeyField = new JTextField(16);
    private final JPasswordField apiSecretField = new JPasswordField(16);
    private final JComboBox<BrokerType> brokerBox = new JComboBox<>(BrokerType.values());
    private final JCheckBox saveCreds = new JCheckBox("Save credentials locally");
    private final JCheckBox paperMode = new JCheckBox("Paper trading mode", true);

    private final JTextField symbolField = new JTextField("NEO", 8);
    private final JTextField basePriceField = new JTextField("8.00", 8);
    private final JTextField baseQtyField = new JTextField("10", 8);
    private final JTextField stopActivationField = new JTextField("9.00", 8);
    private final JTextField sellTriggerField = new JTextField("10.00", 8);
    private final JTextField loss1PriceField = new JTextField("7.00", 8);
    private final JTextField loss1QtyField = new JTextField("5", 8);
    private final JTextField loss2PriceField = new JTextField("6.00", 8);
    private final JTextField loss2QtyField = new JTextField("5", 8);
    private final JTextField pollingField = new JTextField("2", 8);

    private final JCheckBox telemetryConsent = new JCheckBox("I consent to telemetry/analytics");
    private final JLabel positionSummary = new JLabel("Position: -");
    private final JLabel ruleState = new JLabel("Rules: -");
    private final JTextArea eventLog = new JTextArea(12, 80);
    private final JButton startButton = new JButton("Start");
    private final JButton stopButton = new JButton("Stop");
    private final JButton testConnectionButton = new JButton("Test Connection");
    private final JButton settingsButton = new JButton("Settings");

    private final UserIdentityService identityService = new UserIdentityService();
    private final CredentialManager credentialManager = new CredentialManager();
    private TradingApi tradingApi;
    private TradingStrategyService strategyService;
    private PricePoller poller;
    private AnalyticsPublisher analyticsPublisher;
    private SettingsDialog settingsDialog;
    private boolean connectionOk;

    public TradingFrame() {
        setTitle("NeuralArc Trader Application");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        ((JComponent) getContentPane()).setBorder(new EmptyBorder(OUTER_PADDING, OUTER_PADDING, OUTER_PADDING, OUTER_PADDING));
        settingsDialog = new SettingsDialog(this);

        JPanel inputPanel = new JPanel(new GridLayout(0, 4, 10, 10));
        inputPanel.setBorder(new EmptyBorder(0, 0, 12, 0));
        inputPanel.add(new JLabel("User email")); inputPanel.add(emailField);
        inputPanel.add(new JLabel("Unique user ID")); inputPanel.add(userIdLabel);
        inputPanel.add(new JLabel("API key")); inputPanel.add(apiKeyField);
        inputPanel.add(new JLabel("API secret")); inputPanel.add(apiSecretField);
        inputPanel.add(new JLabel("Broker")); inputPanel.add(brokerBox);
        inputPanel.add(saveCreds); inputPanel.add(paperMode);
        inputPanel.add(new JLabel("Symbol")); inputPanel.add(symbolField);
        inputPanel.add(new JLabel("Base buy price")); inputPanel.add(basePriceField);
        inputPanel.add(new JLabel("Base buy quantity")); inputPanel.add(baseQtyField);
        inputPanel.add(new JLabel("Stop activation price")); inputPanel.add(stopActivationField);
        inputPanel.add(new JLabel("Sell trigger price")); inputPanel.add(sellTriggerField);
        inputPanel.add(new JLabel("Loss buy level 1 price")); inputPanel.add(loss1PriceField);
        inputPanel.add(new JLabel("Loss buy level 1 qty")); inputPanel.add(loss1QtyField);
        inputPanel.add(new JLabel("Loss buy level 2 price")); inputPanel.add(loss2PriceField);
        inputPanel.add(new JLabel("Loss buy level 2 qty")); inputPanel.add(loss2QtyField);
        inputPanel.add(new JLabel("Polling interval seconds")); inputPanel.add(pollingField);

        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controlPanel.setBorder(new EmptyBorder(8, 0, 8, 0));
        controlPanel.add(testConnectionButton);
        controlPanel.add(startButton);
        controlPanel.add(stopButton);
        controlPanel.add(settingsButton);

        JPanel disclosurePanel = new JPanel(new GridLayout(0, 1, 0, 6));
        disclosurePanel.setBorder(new EmptyBorder(8, 0, 0, 0));
        disclosurePanel.add(telemetryConsent);
        disclosurePanel.add(new JLabel("If enabled, this app sends limited trading activity and performance events to the publisher analytics API."));
        disclosurePanel.add(new JLabel("Paper trading is recommended before live trading."));

        eventLog.setEditable(false);
        eventLog.setBorder(new EmptyBorder(8, 8, 8, 8));
        applyUiPolish();

        add(inputPanel, BorderLayout.NORTH);
        add(new JScrollPane(eventLog), BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout());
        south.setBorder(new EmptyBorder(10, 0, 0, 0));
        south.add(controlPanel, BorderLayout.NORTH);
        JPanel statusPanel = new JPanel(new GridLayout(0, 1, 0, 6));
        statusPanel.add(positionSummary);
        statusPanel.add(ruleState);
        statusPanel.add(disclosurePanel);
        south.add(statusPanel, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        wireEvents();
        startButton.setEnabled(false);
        stopButton.setEnabled(false);
        setExtendedState(getExtendedState() | JFrame.MAXIMIZED_BOTH);
        setLocationRelativeTo(null);
    }

    private void applyUiPolish() {
        applyFontRecursively(this);

        Border fieldBorder = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(210, 210, 210), 1, true),
                new EmptyBorder(8, 10, 8, 10)
        );
        styleField(emailField, fieldBorder);
        styleField(apiKeyField, fieldBorder);
        styleField(apiSecretField, fieldBorder);
        styleField(symbolField, fieldBorder);
        styleField(basePriceField, fieldBorder);
        styleField(baseQtyField, fieldBorder);
        styleField(stopActivationField, fieldBorder);
        styleField(sellTriggerField, fieldBorder);
        styleField(loss1PriceField, fieldBorder);
        styleField(loss1QtyField, fieldBorder);
        styleField(loss2PriceField, fieldBorder);
        styleField(loss2QtyField, fieldBorder);
        styleField(pollingField, fieldBorder);

        styleButton(testConnectionButton);
        styleButton(startButton);
        styleButton(stopButton);
        styleButton(settingsButton);
    }

    private void applyFontRecursively(Component component) {
        component.setFont(BASE_FONT);
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                applyFontRecursively(child);
            }
        }
    }

    private void styleField(JComponent field, Border border) {
        field.setBorder(border);
        field.setPreferredSize(new Dimension(field.getPreferredSize().width, 36));
    }

    private void styleButton(JButton button) {
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200), 1, true),
                new EmptyBorder(8, 14, 8, 14)
        ));
    }

    private static Font createBaseFont() {
        if (isFontAvailable("Poppins")) {
            return new Font("Poppins", Font.PLAIN, 14);
        }
        return new Font("SansSerif", Font.PLAIN, 14);
    }

    private static boolean isFontAvailable(String fontName) {
        for (String family : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
            if (fontName.equalsIgnoreCase(family)) {
                return true;
            }
        }
        return false;
    }

    private void wireEvents() {
        emailField.addCaretListener(e -> updateUserId());
        testConnectionButton.addActionListener(e -> testConnection());
        startButton.addActionListener(e -> startStrategy());
        stopButton.addActionListener(e -> stopStrategy());
        settingsButton.addActionListener(e -> settingsDialog.setVisible(true));
    }

    private void updateUserId() {
        String id = identityService.generateUserId(emailField.getText());
        userIdLabel.setText(identityService.shortUserId(id));
        startButton.setEnabled(isValidInput() && connectionOk);
    }

    private boolean isValidInput() {
        return !emailField.getText().isBlank()
                && !symbolField.getText().isBlank()
                && !apiKeyField.getText().isBlank()
                && apiSecretField.getPassword().length > 0;
    }

    private void testConnection() {
        tradingApi = TradingApiFactory.create((BrokerType) brokerBox.getSelectedItem());
        String secret = new String(apiSecretField.getPassword());
        tradingApi.authenticate(apiKeyField.getText(), secret);
        connectionOk = tradingApi.testConnection();
        log("Connection test: " + (connectionOk ? "SUCCESS" : "FAILED"));
        startButton.setEnabled(isValidInput() && connectionOk);

        if (saveCreds.isSelected() || settingsDialog.saveCredentials()) {
            credentialManager.save(apiKeyField.getText(), apiSecretField.getPassword(),
                    Path.of(System.getProperty("user.home"), ".neuralarc", "credentials.properties"),
                    identityService.generateUserId(emailField.getText()).substring(0, 16));
        }
    }

    private void startStrategy() {
        TelemetryConfig telemetryConfig = new TelemetryConfig(
                telemetryConsent.isSelected() || settingsDialog.telemetryEnabled(),
                settingsDialog.getEndpoint(),
                null,
                "1.0.0"
        );
        analyticsPublisher = new HttpAnalyticsPublisher(telemetryConfig,
                new AnalyticsQueue(Path.of(System.getProperty("user.home"), ".neuralarc", "analytics-queue.log")));
        analyticsPublisher.publish(new AnalyticsEvent("APP_LAUNCHED")
                .put("userId", identityService.generateUserId(emailField.getText()))
                .put("sessionId", UUID.randomUUID().toString())
                .put("paperTrading", paperMode.isSelected()));

        StrategyConfig config = new StrategyConfig(
                symbolField.getText().trim(),
                new BigDecimal(basePriceField.getText().trim()),
                Integer.parseInt(baseQtyField.getText().trim()),
                new BigDecimal(stopActivationField.getText().trim()),
                new BigDecimal(sellTriggerField.getText().trim()),
                new BigDecimal(loss1PriceField.getText().trim()),
                Integer.parseInt(loss1QtyField.getText().trim()),
                new BigDecimal(loss2PriceField.getText().trim()),
                Integer.parseInt(loss2QtyField.getText().trim()),
                Integer.parseInt(pollingField.getText().trim()),
                paperMode.isSelected()
        );

        strategyService = new TradingStrategyService(
                tradingApi,
                new RuleEvaluationService(),
                analyticsPublisher,
                this::log,
                identityService.generateUserId(emailField.getText())
        );
        strategyService.configure(config);
        poller = new PricePoller();
        poller.start(config.pollingSeconds(), () -> {
            strategyService.onPriceTick();
            SwingUtilities.invokeLater(this::refreshPanels);
        });
        log("Strategy started for symbol " + config.symbol());
        analyticsPublisher.publish(new AnalyticsEvent("STRATEGY_STARTED").put("symbol", config.symbol()));
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
    }

    private void stopStrategy() {
        if (poller != null) {
            poller.stop();
        }
        if (analyticsPublisher != null) {
            analyticsPublisher.publish(new AnalyticsEvent("STRATEGY_STOPPED"));
            analyticsPublisher.shutdown();
        }
        log("Strategy stopped");
        startButton.setEnabled(connectionOk);
        stopButton.setEnabled(false);
    }

    private void refreshPanels() {
        Position p = tradingApi.getPosition(symbolField.getText().trim());
        positionSummary.setText(String.format(
                "Position: shares=%d avgCost=%s marketValue=%s invested=%s realized=%s unrealized=%s",
                p.getTotalShares(), p.getAverageCost(), p.marketValue(), p.totalInvested(), p.getRealizedPnl(), p.unrealizedPnl()));
        ruleState.setText("Rules triggered: " + strategyService.getState().triggeredRules());
    }

    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            eventLog.append(message + System.lineSeparator());
            eventLog.setCaretPosition(eventLog.getDocument().getLength());
        });
    }
}
