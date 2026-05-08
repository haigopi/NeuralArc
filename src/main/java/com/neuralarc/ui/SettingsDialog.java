package com.neuralarc.ui;

import com.neuralarc.model.BrokerType;
import com.neuralarc.model.ApplicationMode;
import com.neuralarc.service.AppSettingsService;
import com.neuralarc.util.AppMetadata;
import com.neuralarc.util.FontLoader;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

public class SettingsDialog extends JDialog {
    private static final Path APP_DATA_DIR = AppMetadata.appDataDirectory();
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
    private static final Color INPUT_DISABLED_BG = new Color(240, 242, 246);
    private static final Color INPUT_DISABLED_BORDER = new Color(214, 218, 225);
    private static final Color INPUT_DISABLED_TEXT = new Color(142, 148, 160);
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
    private final JTextField endpointField = new JTextField(AppMetadata.analyticsEndpointDefault(), 25);
    private final JCheckBox telemetryEnabled = new JCheckBox("Enable telemetry", true);
    private final JCheckBox autoPausePollingWhenMarketClosed = new JCheckBox("Auto pause polling when market is closed", AppSettingsService.DEFAULT_AUTO_PAUSE_POLLING_WHEN_MARKET_CLOSED);
    private final JCheckBox extendedHoursTradingEnabled = new JCheckBox("Enable extended-hours trading", AppSettingsService.DEFAULT_EXTENDED_HOURS_TRADING_ENABLED);
    private final JCheckBox allowDuplicateSymbolStrategies = new JCheckBox("Allow multiple strategies for the same symbol", AppSettingsService.DEFAULT_ALLOW_DUPLICATE_SYMBOL_STRATEGIES);
    private final JCheckBox emailOnBuyExpected = new JCheckBox("Buy order placed / waiting for fill", AppSettingsService.DEFAULT_EMAIL_ON_BUY_EXPECTED);
    private final JCheckBox emailOnSellExecuted = new JCheckBox("Sell order executed", AppSettingsService.DEFAULT_EMAIL_ON_SELL_EXECUTED);
    private final JCheckBox saveCredentials = new JCheckBox("Save credentials locally", false);
    private final JButton verifyConnectionButton = new JButton("Verify Connection");
    private final JButton exportStrategiesButton = new JButton("Export Strategies");
    private final JButton importStrategiesButton = new JButton("Import Strategies");
    private final JLabel connectionStatus = new JLabel("Connection not verified");
    private final JComboBox<BrokerType> brokerBox = new JComboBox<>(BrokerType.values());
    private final JComboBox<ApplicationMode> appModeBox = new JComboBox<>(ApplicationMode.values());
    private final AppSettingsService appSettingsService;
    private transient Function<ConnectionRequest, ConnectionResult> connectionVerifier;
    private transient Function<Path, StrategyTransferResult> strategyExportHandler;
    private transient Function<Path, StrategyTransferResult> strategyImportHandler;
    private transient Runnable deleteAllDataHandler;
    private final Map<ApplicationMode, String[]> credentialCache = new EnumMap<>(ApplicationMode.class);
    private final Map<ApplicationMode, String[]> appliedCredentialCache = new EnumMap<>(ApplicationMode.class);
    private ApplicationMode displayedCredentialMode = ApplicationMode.PAPER;
    private boolean suppressModeSwitchHandling;
    private final Color defaultApiLabelColor = UIManager.getColor("Label.foreground");
    private AppSettingsService.AppSettings appliedSettings = new AppSettingsService.AppSettings(
            "",
            true,
            AppSettingsService.DEFAULT_AUTO_PAUSE_POLLING_WHEN_MARKET_CLOSED,
            AppSettingsService.DEFAULT_EXTENDED_HOURS_TRADING_ENABLED,
            BrokerType.ALPACA,
            ApplicationMode.PAPER,
            AppSettingsService.DEFAULT_ALLOW_DUPLICATE_SYMBOL_STRATEGIES,
            AppSettingsService.DEFAULT_EMAIL_ON_BUY_EXPECTED,
            AppSettingsService.DEFAULT_EMAIL_ON_SELL_EXECUTED
    );
    private boolean savedDuringOpen;

    public SettingsDialog(JFrame owner) {
        this(owner, new AppSettingsService());
    }

    SettingsDialog(JFrame owner, AppSettingsService appSettingsService) {
        super(owner, "Settings", true);
        this.appSettingsService = appSettingsService;
        setLayout(new BorderLayout(SECTION_GAP, SECTION_GAP));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(OUTER_PADDING, OUTER_PADDING, 0, OUTER_PADDING));

        JPanel userPanel = new JPanel(new GridBagLayout());
        userPanel.setBorder(createSectionBorder("User Details"));
        GridBagConstraints userGbc = new GridBagConstraints();
        userGbc.gridx = 0;
        userGbc.gridy = 0;
        userGbc.weightx = 0.32;
        userGbc.fill = GridBagConstraints.HORIZONTAL;
        userGbc.anchor = GridBagConstraints.NORTHWEST;
        userGbc.insets = new Insets(0, 0, FIELD_GAP, FIELD_GAP);
        userPanel.add(new JLabel("User Email:"), userGbc);
        userGbc.gridx = 1;
        userGbc.weightx = 0.68;
        userGbc.insets = new Insets(0, 0, FIELD_GAP, 0);
        userPanel.add(emailField, userGbc);

        JPanel emailPreferences = new JPanel();
        emailPreferences.setLayout(new BoxLayout(emailPreferences, BoxLayout.Y_AXIS));
        emailPreferences.setOpaque(false);
        emailPreferences.setBorder(new EmptyBorder(2, 0, 2, 0));
        JLabel emailPreferenceDescription = new JLabel("<html><div style='max-width:420px; width:420px; line-height:1.35;'>"
                + "Choose which strategy emails should be sent to the user email above. "
                + "Buy notifications are sent when a buy order is placed and waiting for fill. "
                + "Sell notifications are sent when a sell order is filled."
                + "</div></html>");
        emailPreferenceDescription.setForeground(TEXT_MUTED);
        emailPreferenceDescription.setFont(FontLoader.ui(Font.PLAIN, 10f));
        emailOnBuyExpected.setAlignmentX(Component.LEFT_ALIGNMENT);
        emailOnSellExecuted.setAlignmentX(Component.LEFT_ALIGNMENT);
        emailPreferenceDescription.setAlignmentX(Component.LEFT_ALIGNMENT);
        emailPreferences.add(emailOnBuyExpected);
        emailPreferences.add(Box.createVerticalStrut(6));
        emailPreferences.add(emailOnSellExecuted);
        emailPreferences.add(Box.createVerticalStrut(8));
        emailPreferences.add(emailPreferenceDescription);
        userGbc.gridx = 0;
        userGbc.gridy = 1;
        userGbc.weightx = 0.32;
        userGbc.insets = new Insets(0, 0, 0, FIELD_GAP);
        userPanel.add(new JLabel("Email Communications:"), userGbc);
        userGbc.gridx = 1;
        userGbc.weightx = 0.68;
        userGbc.insets = new Insets(0, 0, 0, 0);
        userPanel.add(emailPreferences, userGbc);

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
        DialogButtonStyles.apply(verifyConnectionButton, "icons/verify.svg");
        verifyConnectionButton.addActionListener(e -> verifyConnection());
        brokerBox.addActionListener(e -> updateBrokerControlState());
        appModeBox.addActionListener(e -> onModeChanged());
        apiPanel.add(connectionStatus);
        apiPanel.add(verifyConnectionButton);

        JPanel telemetryPanel = new JPanel(new GridBagLayout());
        telemetryPanel.setOpaque(false);

        boolean analyticsGloballyEnabled = AppMetadata.analyticsEnabled();
        JLabel telemetryDescription = new JLabel(
                "<html><div style='max-width:320px; width:320px; line-height:1.35;'>"
                        + (analyticsGloballyEnabled
                            ? "To support auditing, fraud prevention, and anomaly detection, operational app telemetry "
                              + "can be streamed to our servers.<br><br>"
                              + "Telemetry remains anonymized and does not include personal user details."
                            : "<b>Analytics is currently disabled at the application level</b> "
                              + "(<code>app.analytics.enabled=false</code> in app.properties).<br><br>"
                              + "The checkbox below has no effect until analytics is re-enabled in app.properties.")
                        + "</div></html>"
        );
        telemetryDescription.setForeground(TEXT_MUTED);
        telemetryDescription.setFont(FontLoader.ui(Font.PLAIN, 10f));
        if (!analyticsGloballyEnabled) {
            telemetryEnabled.setEnabled(false);
            telemetryEnabled.setSelected(false);
            telemetryEnabled.setToolTipText(TooltipStyler.text(
                    "Analytics is disabled globally via app.analytics.enabled in app.properties"
            ));
        }

        GridBagConstraints telemetryLabelConstraints = new GridBagConstraints();
        telemetryLabelConstraints.gridx = 0;
        telemetryLabelConstraints.gridy = 0;
        telemetryLabelConstraints.weightx = 0.43;
        telemetryLabelConstraints.fill = GridBagConstraints.HORIZONTAL;
        telemetryLabelConstraints.anchor = GridBagConstraints.NORTHWEST;
        telemetryLabelConstraints.insets = new Insets(0, 0, 0, FIELD_GAP);
        telemetryPanel.add(new JLabel("Telemetry:"), telemetryLabelConstraints);

        GridBagConstraints telemetryContentConstraints = new GridBagConstraints();
        telemetryContentConstraints.gridx = 1;
        telemetryContentConstraints.gridy = 0;
        telemetryContentConstraints.weightx = 0.57;
        telemetryContentConstraints.fill = GridBagConstraints.HORIZONTAL;
        telemetryContentConstraints.anchor = GridBagConstraints.NORTHWEST;
        telemetryContentConstraints.insets = new Insets(0, 0, 0, 0);

        JPanel telemetryContent = new JPanel();
        telemetryContent.setLayout(new BoxLayout(telemetryContent, BoxLayout.Y_AXIS));
        telemetryContent.setOpaque(false);
        telemetryContent.setBorder(new EmptyBorder(2, 0, 2, 0));
        telemetryEnabled.setAlignmentX(Component.LEFT_ALIGNMENT);
        telemetryDescription.setAlignmentX(Component.LEFT_ALIGNMENT);
        telemetryContent.add(telemetryEnabled);
        telemetryContent.add(Box.createVerticalStrut(10));
        telemetryContent.add(telemetryDescription);
        telemetryPanel.add(telemetryContent, telemetryContentConstraints);

        JPanel marketHoursPanel = new JPanel(new GridBagLayout());
        marketHoursPanel.setOpaque(false);
        GridBagConstraints marketHoursLabelConstraints = new GridBagConstraints();
        marketHoursLabelConstraints.gridx = 0;
        marketHoursLabelConstraints.gridy = 0;
        marketHoursLabelConstraints.weightx = 0.43;
        marketHoursLabelConstraints.fill = GridBagConstraints.HORIZONTAL;
        marketHoursLabelConstraints.anchor = GridBagConstraints.NORTHWEST;
        marketHoursLabelConstraints.insets = new Insets(0, 0, 0, FIELD_GAP);
        marketHoursPanel.add(new JLabel("Session Controls:"), marketHoursLabelConstraints);

        JLabel autoPauseDescription = new JLabel("<html><div style='max-width:320px; width:320px; line-height:1.35;'>"
                + "Reduces API usage by pausing strategy polling outside regular market hours. "
                + "Active strategies resume automatically when the market opens, unless manually paused by the user."
                + "</div></html>");
        autoPauseDescription.setForeground(TEXT_MUTED);
        autoPauseDescription.setFont(FontLoader.ui(Font.PLAIN, 10f));

        JLabel extendedHoursDescription = new JLabel("<html><div style='max-width:320px; width:320px; line-height:1.35;'>"
                + "Allows eligible orders during pre-market and after-hours sessions. "
                + "This may involve lower liquidity, wider spreads, and higher risk."
                + "</div></html>");
        extendedHoursDescription.setForeground(TEXT_MUTED);
        extendedHoursDescription.setFont(FontLoader.ui(Font.PLAIN, 10f));

        JLabel allowDuplicateSymbolsDescription = new JLabel("<html><div style='max-width:320px; width:320px; line-height:1.35;'>"
                + "When enabled, multiple strategies can be added for the same stock symbol. "
                + "By default, only one active or paused strategy per symbol is allowed."
                + "</div></html>");
        allowDuplicateSymbolsDescription.setForeground(TEXT_MUTED);
        allowDuplicateSymbolsDescription.setFont(FontLoader.ui(Font.PLAIN, 10f));

        JPanel marketHoursContent = new JPanel();
        marketHoursContent.setLayout(new BoxLayout(marketHoursContent, BoxLayout.Y_AXIS));
        marketHoursContent.setOpaque(false);
        marketHoursContent.setBorder(new EmptyBorder(2, 0, 2, 0));
        autoPausePollingWhenMarketClosed.setAlignmentX(Component.LEFT_ALIGNMENT);
        autoPauseDescription.setAlignmentX(Component.LEFT_ALIGNMENT);
        extendedHoursTradingEnabled.setAlignmentX(Component.LEFT_ALIGNMENT);
        extendedHoursDescription.setAlignmentX(Component.LEFT_ALIGNMENT);
        allowDuplicateSymbolStrategies.setAlignmentX(Component.LEFT_ALIGNMENT);
        allowDuplicateSymbolsDescription.setAlignmentX(Component.LEFT_ALIGNMENT);
        marketHoursContent.add(autoPausePollingWhenMarketClosed);
        marketHoursContent.add(Box.createVerticalStrut(6));
        marketHoursContent.add(autoPauseDescription);
        marketHoursContent.add(Box.createVerticalStrut(12));
        marketHoursContent.add(extendedHoursTradingEnabled);
        marketHoursContent.add(Box.createVerticalStrut(6));
        marketHoursContent.add(extendedHoursDescription);
        marketHoursContent.add(Box.createVerticalStrut(12));
        marketHoursContent.add(allowDuplicateSymbolStrategies);
        marketHoursContent.add(Box.createVerticalStrut(6));
        marketHoursContent.add(allowDuplicateSymbolsDescription);

        GridBagConstraints marketHoursContentConstraints = new GridBagConstraints();
        marketHoursContentConstraints.gridx = 1;
        marketHoursContentConstraints.gridy = 0;
        marketHoursContentConstraints.weightx = 0.57;
        marketHoursContentConstraints.fill = GridBagConstraints.HORIZONTAL;
        marketHoursContentConstraints.anchor = GridBagConstraints.NORTHWEST;
        marketHoursPanel.add(marketHoursContent, marketHoursContentConstraints);

        JPanel dangerZonePanel = new JPanel(new GridLayout(0, 1, FIELD_GAP, FIELD_GAP));
        dangerZonePanel.setBorder(createSectionBorder("Danger Zone"));
        JButton deleteAllDataButton = new JButton("Delete All Data");
        DialogButtonStyles.apply(deleteAllDataButton, "icons/delete.svg");
        deleteAllDataButton.setForeground(new Color(180, 30, 30));
        deleteAllDataButton.addActionListener(e -> deleteAllData());

        JLabel dangerDescription = new JLabel("<html><div style='width:100%;color:#9AA0A8;'>"
                + "Deletes local settings, saved credentials, strategies, and cached app data. This action cannot be undone."
                + "</div></html>");
        dangerDescription.setForeground(new Color(154, 160, 168));
        dangerDescription.setFont(FontLoader.ui(Font.PLAIN, 10f));
        dangerZonePanel.add(deleteAllDataButton);
        dangerZonePanel.add(dangerDescription);

        JPanel strategyTransferPanel = new JPanel(new GridLayout(0, 1, FIELD_GAP, FIELD_GAP));
        strategyTransferPanel.setBorder(createSectionBorder("Strategy Transfer"));
        DialogButtonStyles.apply(exportStrategiesButton, "icons/save.svg");
        DialogButtonStyles.apply(importStrategiesButton, "icons/apply.svg");
        exportStrategiesButton.addActionListener(e -> exportStrategies());
        importStrategiesButton.addActionListener(e -> importStrategies());
        JLabel transferDescription = new JLabel("<html><div style='width:100%;color:#9AA0A8;'>"
                + "Export/import strategies JSON only. Settings, credentials, orders, and logs are not included."
                + "</div></html>");
        transferDescription.setForeground(new Color(154, 160, 168));
        transferDescription.setFont(FontLoader.ui(Font.PLAIN, 10f));
        strategyTransferPanel.add(exportStrategiesButton);
        strategyTransferPanel.add(importStrategiesButton);
        strategyTransferPanel.add(transferDescription);

        content.add(userPanel);
        content.add(Box.createVerticalStrut(SECTION_GAP));
        content.add(apiPanel);
        content.add(Box.createVerticalStrut(SECTION_GAP));
        content.add(createCollapsibleSection("Telemetry", telemetryPanel, true));
        content.add(Box.createVerticalStrut(SECTION_GAP));
        content.add(createCollapsibleSection("Trading Hours", marketHoursPanel, true));
        content.add(Box.createVerticalStrut(SECTION_GAP));
        content.add(strategyTransferPanel);
        content.add(Box.createVerticalStrut(SECTION_GAP));
        content.add(dangerZonePanel);

        JScrollPane contentScroll = new JScrollPane(content);
        contentScroll.setBorder(null);
        contentScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        contentScroll.getVerticalScrollBar().setUnitIncrement(16);
        contentScroll.getViewport().setBackground(DIALOG_BG);
        contentScroll.setPreferredSize(new Dimension(720, 680));
        add(contentScroll, BorderLayout.CENTER);

        JPanel actions = new JPanel(new BorderLayout());
        actions.setBorder(new EmptyBorder(0, OUTER_PADDING, OUTER_PADDING, OUTER_PADDING));
        JButton helpFaq = new JButton("Help & FAQ");
        DialogButtonStyles.apply(helpFaq, "icons/faqs.svg");
        helpFaq.addActionListener(e -> new HelpDialog(owner).setVisible(true));
        JButton encryptSave = new JButton("Encrypt, Save and Close");
        DialogButtonStyles.apply(encryptSave, "icons/save.svg");
        encryptSave.addActionListener(e -> executeSaveAndClose(this::saveAll, this::closeDialog));
        JButton close = new JButton("Close");
        DialogButtonStyles.apply(close, "icons/close.svg");
        close.addActionListener(e -> closeDialog());
        JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightActions.add(encryptSave);
        rightActions.add(close);
        actions.add(helpFaq, BorderLayout.WEST);
        actions.add(rightActions, BorderLayout.EAST);
        add(actions, BorderLayout.SOUTH);

        applyDialogTheme();

        loadAll();
        updateBrokerControlState();
        pack();
        Rectangle screenBounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int minDialogWidth = 720;
        int minDialogHeight = 680;
        int w = Math.max(minDialogWidth, Math.min(getPreferredSize().width, screenBounds.width - 48));
        int h = Math.max(minDialogHeight, Math.min(getPreferredSize().height, (int) (screenBounds.height * 0.92)));
        setSize(new Dimension(w, h));
        setMinimumSize(new Dimension(minDialogWidth, minDialogHeight));
        setResizable(true);
        setLocationRelativeTo(owner);
    }

    public void setConnectionVerifier(Function<ConnectionRequest, ConnectionResult> connectionVerifier) {
        this.connectionVerifier = connectionVerifier;
    }

    public void setStrategyExportHandler(Function<Path, StrategyTransferResult> strategyExportHandler) {
        this.strategyExportHandler = strategyExportHandler;
    }

    public void setStrategyImportHandler(Function<Path, StrategyTransferResult> strategyImportHandler) {
        this.strategyImportHandler = strategyImportHandler;
    }

    public void setDeleteAllDataHandler(Runnable deleteAllDataHandler) {
        this.deleteAllDataHandler = deleteAllDataHandler;
    }

    public String getEndpoint() { return endpointField.getText().trim(); }
    public boolean telemetryEnabled() { return telemetryEnabled.isSelected(); }
    public boolean autoPausePollingWhenMarketClosed() { return autoPausePollingWhenMarketClosed.isSelected(); }
    public boolean extendedHoursTradingEnabled() { return extendedHoursTradingEnabled.isSelected(); }
    public boolean allowDuplicateSymbolStrategies() { return allowDuplicateSymbolStrategies.isSelected(); }
    public boolean emailOnBuyExpected() { return emailOnBuyExpected.isSelected(); }
    public boolean emailOnSellExecuted() { return emailOnSellExecuted.isSelected(); }
    public boolean saveCredentials() { return saveCredentials.isSelected(); }
    public BrokerType brokerType() { return (BrokerType) brokerBox.getSelectedItem(); }
    public ApplicationMode applicationMode() {
        ApplicationMode mode = (ApplicationMode) appModeBox.getSelectedItem();
        return mode == null ? ApplicationMode.PAPER : mode;
    }
    public BrokerType appliedBrokerType() { return appliedSettings.brokerType(); }
    public ApplicationMode appliedApplicationMode() { return appliedSettings.applicationMode(); }
    public boolean appliedExtendedHoursTradingEnabled() { return appliedSettings.extendedHoursTradingEnabled(); }
    public boolean appliedAutoPausePollingWhenMarketClosed() { return appliedSettings.autoPausePollingWhenMarketClosed(); }
    public boolean appliedAllowDuplicateSymbolStrategies() { return appliedSettings.allowDuplicateSymbolStrategies(); }
    public String getUserEmail() { return emailField.getText().trim(); }
    public String getApiKey() { return apiKeyField.getText().trim(); }
    public String getApiSecret() { return new String(apiSecretField.getPassword()); }
    public String appliedUserEmail() { return appliedSettings.userEmail(); }
    public String savedApiKey(ApplicationMode mode) {
        String[] creds = appliedCredentialCache.get(mode == null ? ApplicationMode.PAPER : mode);
        return creds == null ? "" : creds[0];
    }
    public String savedApiSecret(ApplicationMode mode) {
        String[] creds = appliedCredentialCache.get(mode == null ? ApplicationMode.PAPER : mode);
        return creds == null ? "" : creds[1];
    }
    public boolean wasSavedDuringOpen() { return savedDuringOpen; }public void prepareForOpen() {
        savedDuringOpen = false;
        loadAll();
        updateBrokerControlState();
    }
    public void selectBrokerAndMode(BrokerType brokerType, ApplicationMode mode) {
        suppressModeSwitchHandling = true;
        if (brokerType != null) {
            brokerBox.setSelectedItem(brokerType);
        }
        if (mode != null) {
            appModeBox.setSelectedItem(mode);
        }
        displayedCredentialMode = applicationMode();
        applyModeCredentialsToFields(displayedCredentialMode);
        suppressModeSwitchHandling = false;
        updateBrokerControlState();
    }
    public boolean hasRequiredSettings() {
        if (getUserEmail().isBlank()) {
            return false;
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
            appSettingsService.save(new AppSettingsService.AppSettings(
                    email,
                    telemetryEnabled(),
                    autoPausePollingWhenMarketClosed(),
                    extendedHoursTradingEnabled(),
                    brokerType() == null ? BrokerType.ALPACA : brokerType(),
                    applicationMode(),
                    allowDuplicateSymbolStrategies(),
                    emailOnBuyExpected(),
                    emailOnSellExecuted()
            ));
            appSettingsService.saveEndpoint(getEndpoint());
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
                appSettingsService.saveCredentials(mode, key, secret);
            }
            appliedSettings = appSettingsService.load();
            syncAppliedCredentialCache();
            savedDuringOpen = true;
            JOptionPane.showMessageDialog(this, "Settings saved successfully.", "Saved", JOptionPane.INFORMATION_MESSAGE);
            return true;
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to save settings.", "Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private void loadAll() {
        suppressModeSwitchHandling = true;
        appliedSettings = appSettingsService.load();
        emailField.setText(appliedSettings.userEmail());
        endpointField.setText(appSettingsService.loadEndpoint());
        telemetryEnabled.setSelected(appliedSettings.telemetryEnabled());
        autoPausePollingWhenMarketClosed.setSelected(appliedSettings.autoPausePollingWhenMarketClosed());
        extendedHoursTradingEnabled.setSelected(appliedSettings.extendedHoursTradingEnabled());
        allowDuplicateSymbolStrategies.setSelected(appliedSettings.allowDuplicateSymbolStrategies());
        emailOnBuyExpected.setSelected(appliedSettings.emailOnBuyExpected());
        emailOnSellExecuted.setSelected(appliedSettings.emailOnSellExecuted());
        saveCredentials.setSelected(true);
        brokerBox.setSelectedItem(appliedSettings.brokerType());
        appModeBox.setSelectedItem(appliedSettings.applicationMode());

        String email = getUserEmail();
        if (!email.isBlank()) {
            for (ApplicationMode mode : ApplicationMode.values()) {
                credentialCache.put(mode, appSettingsService.loadCredentials(mode));
            }
        }
        displayedCredentialMode = applicationMode();
        applyModeCredentialsToFields(displayedCredentialMode);
        syncAppliedCredentialCache();
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
            creds = email.isBlank() ? new String[]{"", ""} : appSettingsService.loadCredentials(mode);
            credentialCache.put(mode, creds);
        }
        apiKeyField.setText(creds[0]);
        apiSecretField.setText(creds[1]);
    }

    private String apiKeyForMode(ApplicationMode mode, String email) {
        String[] creds = credentialCache.get(mode);
        if (creds != null && creds[0] != null && !creds[0].isBlank()) {
            return creds[0].trim();
        }
        String[] loaded = appSettingsService.loadCredentials(mode);
        return loaded[0] == null ? "" : loaded[0].trim();
    }

    private void closeDialog() {
        setVisible(false);
    }

    private void deleteAllData() {
        String message = "<html><body style='width:340px'>"
                + "<b>Permanently delete all NeuralArc local data?</b><br><br>"
                + "The following will be erased from <code>" + APP_DATA_DIR + "</code>:<br>"
                + "• User settings and preferences<br>"
                + "• Saved API credentials (paper &amp; live)<br>"
                + "• All saved trading strategies<br>"
                + "• Analytics queue and app logs<br><br>"
                + "Any active strategies will stop immediately. Open positions will <b>not</b> be automatically closed.<br><br>"
                + "After deletion, open positions and orders from Alpaca will be reloaded automatically.<br><br>"
                + "This action <b>cannot be undone</b>."
                + "</body></html>";
        int choice = JOptionPane.showConfirmDialog(
                this,
                message,
                "Delete All Local Data",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            // 1. Truncate DB tables and reload from Alpaca via the TradingFrame handler.
            if (deleteAllDataHandler != null) {
                deleteAllDataHandler.run();
            }

            // 2. Delete credential and settings files (but NOT neuralarc.db — its
            //    connection stays open; resetAllData() already cleared the tables).
            deleteNonDatabaseFiles(APP_DATA_DIR);

            // 3. Reset dialog UI state.
            credentialCache.clear();
            emailField.setText("");
            apiKeyField.setText("");
            apiSecretField.setText("");
            endpointField.setText(AppMetadata.analyticsEndpointDefault());
            telemetryEnabled.setSelected(true);
            autoPausePollingWhenMarketClosed.setSelected(AppSettingsService.DEFAULT_AUTO_PAUSE_POLLING_WHEN_MARKET_CLOSED);
            extendedHoursTradingEnabled.setSelected(AppSettingsService.DEFAULT_EXTENDED_HOURS_TRADING_ENABLED);
            allowDuplicateSymbolStrategies.setSelected(AppSettingsService.DEFAULT_ALLOW_DUPLICATE_SYMBOL_STRATEGIES);
            emailOnBuyExpected.setSelected(AppSettingsService.DEFAULT_EMAIL_ON_BUY_EXPECTED);
            emailOnSellExecuted.setSelected(AppSettingsService.DEFAULT_EMAIL_ON_SELL_EXECUTED);
            brokerBox.setSelectedItem(BrokerType.ALPACA);
            appModeBox.setSelectedItem(ApplicationMode.PAPER);
            displayedCredentialMode = ApplicationMode.PAPER;
            appliedSettings = new AppSettingsService.AppSettings(
                    "",
                    true,
                    AppSettingsService.DEFAULT_AUTO_PAUSE_POLLING_WHEN_MARKET_CLOSED,
                    AppSettingsService.DEFAULT_EXTENDED_HOURS_TRADING_ENABLED,
                    BrokerType.ALPACA,
                    ApplicationMode.PAPER,
                    AppSettingsService.DEFAULT_ALLOW_DUPLICATE_SYMBOL_STRATEGIES,
                    AppSettingsService.DEFAULT_EMAIL_ON_BUY_EXPECTED,
                    AppSettingsService.DEFAULT_EMAIL_ON_SELL_EXECUTED
            );
            appliedCredentialCache.clear();
            connectionStatus.setText("All local data deleted — reloading from Alpaca…");
            connectionStatus.setForeground(TEXT_MUTED);
            updateBrokerControlState();
            JOptionPane.showMessageDialog(this,
                    "All local data deleted. Open positions and orders will be reloaded from Alpaca.",
                    "Deleted", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Failed to delete all local data.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Deletes all files under {@code root} except the SQLite database file
     * ({@code neuralarc.db}), which must remain accessible via the open JDBC
     * connection for the remainder of the session.
     */
    private void deleteNonDatabaseFiles(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder())
                    .filter(p -> !p.getFileName().toString().equals("neuralarc.db")
                              && !p.getFileName().toString().startsWith("neuralarc.db-"))
                    .forEach(path -> {
                        try {
                            if (!Files.isDirectory(path) || isEmptyDirectory(path)) {
                                Files.deleteIfExists(path);
                            }
                        } catch (IOException ex) {
                            throw new IllegalStateException("Failed to delete " + path, ex);
                        }
                    });
        } catch (IllegalStateException ex) {
            if (ex.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw ex;
        }
    }

    private static boolean isEmptyDirectory(Path dir) {
        try (Stream<Path> s = Files.list(dir)) {
            return s.findFirst().isEmpty();
        } catch (IOException ignored) {
            return false;
        }
    }

    private void verifyConnection() {
        if (connectionVerifier == null) {
            markConnectionStatus(false, "Verification unavailable");
            return;
        }
        ConnectionResult result = connectionVerifier.apply(new ConnectionRequest(brokerType(), getApiKey(), getApiSecret()));
        markConnectionStatus(result.connected(), result.message());
    }

    private void exportStrategies() {
        if (strategyExportHandler == null) {
            JOptionPane.showMessageDialog(this,
                    "Strategy export is unavailable right now.",
                    "Export Unavailable",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export Strategies");
        chooser.setSelectedFile(Path.of("strategies-export.json").toFile());
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path targetPath = chooser.getSelectedFile().toPath();
        StrategyTransferResult result = strategyExportHandler.apply(targetPath);
        JOptionPane.showMessageDialog(this,
                result.message(),
                result.success() ? "Export Complete" : "Export Failed",
                result.success() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
    }

    private void importStrategies() {
        if (strategyImportHandler == null) {
            JOptionPane.showMessageDialog(this,
                    "Strategy import is unavailable right now.",
                    "Import Unavailable",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Import Strategies");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path sourcePath = chooser.getSelectedFile().toPath();
        StrategyTransferResult result = strategyImportHandler.apply(sourcePath);
        JOptionPane.showMessageDialog(this,
                result.message(),
                result.success() ? "Import Complete" : "Import Failed",
                result.success() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
    }

    private void updateBrokerControlState() {
        apiKeyField.setEnabled(true);
        apiSecretField.setEnabled(true);
        appModeBox.setEnabled(true);
        verifyConnectionButton.setEnabled(true);
        applyInputEnabledState(apiKeyField, true);
        applyInputEnabledState(apiSecretField, true);
        applicationModeLabel.setForeground(defaultApiLabelColor);
        apiKeyLabel.setForeground(defaultApiLabelColor);
        apiSecretLabel.setForeground(defaultApiLabelColor);
        if ("Connection not verified".equals(connectionStatus.getText()) || connectionStatus.getText().isBlank()) {
            connectionStatus.setText("Connection not verified");
            connectionStatus.setForeground(new Color(180, 30, 30));
        }
    }

    static boolean executeSaveAndClose(java.util.function.BooleanSupplier saveAction, Runnable closeAction) {
        if (!saveAction.getAsBoolean()) {
            return false;
        }
        closeAction.run();
        return true;
    }

    public record ConnectionRequest(BrokerType brokerType, String apiKey, String apiSecret) {}

    public record ConnectionResult(boolean connected, String message) {}

    public record StrategyTransferResult(boolean success, String message) {}

    private JComponent createCollapsibleSection(String title, JComponent sectionContent, boolean initiallyExpanded) {
        JPanel container = new JPanel(new BorderLayout(0, 8));
        container.setOpaque(false);
        container.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(INPUT_BORDER, 1, true),
                new EmptyBorder(10, 12, 10, 12)
        ));

        JButton toggleButton = new JButton();
        toggleButton.setOpaque(false);
        toggleButton.setContentAreaFilled(false);
        toggleButton.setBorderPainted(false);
        toggleButton.setFocusPainted(false);
        toggleButton.setHorizontalAlignment(SwingConstants.LEFT);
        toggleButton.setFont(FontLoader.ui(Font.BOLD, 12f));
        toggleButton.setForeground(TEXT_PRIMARY);
        toggleButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        toggleButton.setMargin(new Insets(0, 0, 0, 0));

        JPanel contentWrapper = new JPanel(new BorderLayout());
        contentWrapper.setOpaque(false);
        contentWrapper.add(sectionContent, BorderLayout.CENTER);
        contentWrapper.setVisible(initiallyExpanded);

        updateCollapsibleSectionButton(toggleButton, title, initiallyExpanded);
        toggleButton.addActionListener(e -> {
            boolean expanded = !contentWrapper.isVisible();
            contentWrapper.setVisible(expanded);
            updateCollapsibleSectionButton(toggleButton, title, expanded);
            container.revalidate();
            container.repaint();
            pack();
        });

        container.add(toggleButton, BorderLayout.NORTH);
        container.add(contentWrapper, BorderLayout.CENTER);
        return container;
    }

    private void updateCollapsibleSectionButton(JButton button, String title, boolean expanded) {
        button.setText((expanded ? "▼  " : "▶  ") + title);
    }

    private void syncAppliedCredentialCache() {
        appliedCredentialCache.clear();
        String email = appliedSettings.userEmail();
        if (email == null || email.isBlank()) {
            for (ApplicationMode mode : ApplicationMode.values()) {
                appliedCredentialCache.put(mode, new String[]{"", ""});
            }
            return;
        }
        for (ApplicationMode mode : ApplicationMode.values()) {
            appliedCredentialCache.put(mode, appSettingsService.loadCredentials(mode));
        }
    }


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
        input.setDisabledTextColor(INPUT_DISABLED_TEXT);
        input.setSelectionColor(new Color(114, 130, 176));
        input.setSelectedTextColor(TEXT_PRIMARY);
        input.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(INPUT_BORDER, 1, true),
                new EmptyBorder(4, 8, 4, 8)
        ));
    }

    private void applyInputEnabledState(JTextField input, boolean enabled) {
        input.setBackground(enabled ? INPUT_BG : INPUT_DISABLED_BG);
        input.setForeground(enabled ? TEXT_PRIMARY : INPUT_DISABLED_TEXT);
        input.setCaretColor(enabled ? TEXT_PRIMARY : INPUT_DISABLED_TEXT);
        input.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(enabled ? INPUT_BORDER : INPUT_DISABLED_BORDER, 1, true),
                new EmptyBorder(4, 8, 4, 8)
        ));
    }

    private static Border withInnerPadding(Border border) {
        return BorderFactory.createCompoundBorder(border, new EmptyBorder(SECTION_INNER_PADDING, SECTION_INNER_PADDING, SECTION_INNER_PADDING, SECTION_INNER_PADDING));
    }
}
