package com.neuralarc.ui;

import com.neuralarc.analytics.*;
import com.neuralarc.api.HttpAlpacaClient;
import com.neuralarc.api.TradingApi;
import com.neuralarc.api.TradingApiFactory;
import com.neuralarc.model.*;
import com.neuralarc.rules.RuleEvaluationService;
import com.neuralarc.service.FileStrategyExecutionEventRepository;
import com.neuralarc.service.FileStrategyOrderRepository;
import com.neuralarc.service.FileStrategyRepository;
import com.neuralarc.service.PricePoller;
import com.neuralarc.service.StrategyPersistenceManager;
import com.neuralarc.service.StrategyPersistenceManager.StrategyEntry;
import com.neuralarc.service.StrategyPollingService;
import com.neuralarc.service.StrategyService;
import com.neuralarc.service.StrategyValidator;
import com.neuralarc.service.TradingStrategyService;
import com.neuralarc.service.UserIdentityService;
import com.neuralarc.util.AppMetadata;
import com.neuralarc.util.FontLoader;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TradingFrame extends JFrame {
    private static final Font BASE_FONT = createBaseFont();
    private static final int OUTER_PADDING = 16;
    private static final DateTimeFormatter LOG_DATE_FORMAT = DateTimeFormatter.ofPattern("EEE, MMM");
    private static final DateTimeFormatter LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a");

    private final JLabel positionSummary = new JLabel("Position: -");
    private final JLabel ruleState = new JLabel("Rules: -");
    private final JLabel paperUnrealizedSummary = new JLabel("Paper Unrealized P&L Total: -");
    private final JLabel headerTotalsSeparator = new JLabel("|");
    private final JLabel liveUnrealizedSummary = new JLabel("Live Unrealized P&L Total: -");
    private final JLabel positionSectionTitle = new JLabel("Position");
    private final JLabel rulesSectionTitle = new JLabel("Rules Triggered");
    private final JLabel statusBar = new JLabel(" ● Not connected");
    private final JLabel statusStrategyCount = new JLabel("");
    private final JLabel headerStatus = new JLabel("Status: waiting for settings");
    private static final Color STATUS_OK = new Color(34, 139, 34);
    private static final Color STATUS_WARN = new Color(180, 100, 0);
    private static final Color STATUS_ERR = new Color(180, 30, 30);
    private static final Color TABLE_SELECTION_BG = new Color(192, 166, 240);
    private static final Color STATUS_TEXT_RUNNING = new Color(46, 125, 50);
    private static final Color STATUS_TEXT_PAUSED = new Color(180, 100, 0);
    private static final Color MODE_TEXT_ALPACA_PAPER = new Color(25, 118, 210);
    private static final Color MODE_TEXT_ALPACA_LIVE = new Color(183, 28, 28);
    private static final Color LOG_LINE_EVEN = new Color(63, 72, 82);
    private static final Color LOG_LINE_ODD = new Color(110, 118, 128);
    private static final Color HEADER_STATUS_DEFAULT = new Color(220, 220, 255);
    private static final Color HEADER_STATUS_LIVE_ALERT = new Color(255, 82, 82);
    private static final Color HEADER_STATUS_LIVE_ALERT_DIM = new Color(255, 205, 210);
    private static final Color HEADER_STATUS_LIVE_ACTIVE = new Color(46, 125, 50);
    private static final Color HEADER_STATUS_LIVE_ACTIVE_DIM = Color.WHITE;
    private final JTextPane eventLog = new JTextPane();
    private final JButton addStrategyButton = new JButton("📊 Add New Stock Strategy");
    private final JButton settingsButton = new JButton("⚙️ Settings");
    private final Timer liveModeBlinkTimer;
    private final Timer logFlushTimer;
    private final Timer pollingIndicatorTimer;
    private final Timer strategyPollingTimer;
    private final Path appLogFile = Path.of(System.getProperty("user.home"), ".neuralarc", "app.log");
    private final StringBuilder pendingLogWrites = new StringBuilder();

    private final UserIdentityService identityService = new UserIdentityService();
    private final List<ManagedStrategy> strategies = new ArrayList<>();
    private final StrategyTableModel strategyTableModel = new StrategyTableModel();
    private final JTable strategyTable = new JTable(strategyTableModel);

    private TradingApi tradingApi;
    private AnalyticsPublisher analyticsPublisher;
    private final SettingsDialog settingsDialog;
    private StrategyPersistenceManager persistenceManager;
    private final FileStrategyRepository strategyRepository;
    private final FileStrategyOrderRepository strategyOrderRepository;
    private final FileStrategyExecutionEventRepository strategyEventRepository;
    private boolean connectionOk;
    private boolean appLaunchedPublished;
    private String selectedStrategySymbol;
    private BrokerType currentBrokerType = BrokerType.ALPACA;
    private boolean preservingSelection;
    private Color liveBlinkPrimary = HEADER_STATUS_DEFAULT;
    private Color liveBlinkSecondary = HEADER_STATUS_DEFAULT;
    private boolean liveBlinkPrimaryActive;
    private int logLineCount;
    private boolean promptedDefaultStrategyDialog;
    private StrategyService strategyService;
    private StrategyPollingService strategyPollingService;

    public TradingFrame() {
        liveModeBlinkTimer = new Timer(500, e -> toggleLiveHeaderBlink());
        liveModeBlinkTimer.setInitialDelay(0);
        logFlushTimer = new Timer(10000, e -> flushLogsToFile());
        logFlushTimer.setInitialDelay(10000);
        logFlushTimer.start();
        pollingIndicatorTimer = new Timer(250, e -> strategyTable.repaint());
        pollingIndicatorTimer.setInitialDelay(250);
        pollingIndicatorTimer.start();
        setTitle("NeuralArc Trader Application");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        ((JComponent) getContentPane()).setBorder(new EmptyBorder(OUTER_PADDING, OUTER_PADDING, OUTER_PADDING, OUTER_PADDING));
        settingsDialog = new SettingsDialog(this);
        strategyRepository = new FileStrategyRepository(
                Path.of(System.getProperty("user.home"), ".neuralarc", "strategies-v2.json")
        );
        strategyOrderRepository = new FileStrategyOrderRepository(
                Path.of(System.getProperty("user.home"), ".neuralarc", "strategy-orders.json")
        );
        strategyEventRepository = new FileStrategyExecutionEventRepository(
                Path.of(System.getProperty("user.home"), ".neuralarc", "strategy-events.json")
        );
        refreshStrategyRuntimeServices();
        strategyPollingTimer = new Timer(10000, e -> strategyPollingService.pollActiveStrategies());
        strategyPollingTimer.setInitialDelay(10000);
        strategyPollingTimer.start();

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(35, 35, 45));
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(200, 200, 210)),
                new EmptyBorder(6, 8, 6, 8)
        ));

        headerStatus.setFont(BASE_FONT.deriveFont(Font.BOLD, 12f));
        headerStatus.setForeground(HEADER_STATUS_DEFAULT);
        headerStatus.setVerticalAlignment(SwingConstants.CENTER);
        headerStatus.setBorder(new EmptyBorder(0, 0, 0, 12));
        paperUnrealizedSummary.setBorder(new EmptyBorder(0, 8, 0, 8));
        headerTotalsSeparator.setBorder(new EmptyBorder(0, 2, 0, 2));
        liveUnrealizedSummary.setBorder(new EmptyBorder(0, 8, 0, 8));

        JPanel leftControls = new JPanel(new GridBagLayout());
        leftControls.setOpaque(false);
        GridBagConstraints headerLeftGbc = new GridBagConstraints();
        headerLeftGbc.gridx = 0;
        headerLeftGbc.gridy = 0;
        headerLeftGbc.anchor = GridBagConstraints.CENTER;
        leftControls.add(headerStatus, headerLeftGbc);

        JPanel headerTotalsPanel = new JPanel(new GridBagLayout());
        headerTotalsPanel.setOpaque(false);
        GridBagConstraints totalsGbc = new GridBagConstraints();
        totalsGbc.gridy = 0;
        totalsGbc.anchor = GridBagConstraints.CENTER;
        totalsGbc.gridx = 0;
        headerTotalsPanel.add(paperUnrealizedSummary, totalsGbc);
        totalsGbc.gridx = 1;
        headerTotalsPanel.add(headerTotalsSeparator, totalsGbc);
        totalsGbc.gridx = 2;
        headerTotalsPanel.add(liveUnrealizedSummary, totalsGbc);

        JPanel rightControls = new JPanel(new GridBagLayout());
        rightControls.setOpaque(false);
        GridBagConstraints rightControlsGbc = new GridBagConstraints();
        rightControlsGbc.gridy = 0;
        rightControlsGbc.anchor = GridBagConstraints.CENTER;
        rightControlsGbc.insets = new java.awt.Insets(0, 0, 0, 8);
        rightControlsGbc.gridx = 0;
        rightControls.add(addStrategyButton, rightControlsGbc);
        rightControlsGbc.gridx = 1;
        rightControls.add(settingsButton, rightControlsGbc);

        JButton killSwitchButton = new JButton("⚠️ KILL SWITCH");
        killSwitchButton.setFocusPainted(false);
        killSwitchButton.setFont(FontLoader.ui(Font.BOLD, 12f));
        killSwitchButton.setForeground(Color.WHITE);
        killSwitchButton.setBackground(new Color(180, 20, 20));
        killSwitchButton.setOpaque(true);
        killSwitchButton.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(120, 10, 10), 1, true),
                new EmptyBorder(8, 14, 8, 14)
        ));
        killSwitchButton.setMargin(new java.awt.Insets(8, 14, 8, 14));
        killSwitchButton.addActionListener(e -> killAllStrategies());
        rightControlsGbc.gridx = 2;
        rightControlsGbc.insets = new java.awt.Insets(0, 0, 0, 0);
        rightControls.add(killSwitchButton, rightControlsGbc);
        headerPanel.add(leftControls, BorderLayout.WEST);
        headerPanel.add(headerTotalsPanel, BorderLayout.CENTER);
        headerPanel.add(rightControls, BorderLayout.EAST);

        strategyTable.setRowHeight(34);
        strategyTable.setFillsViewportHeight(true);
        strategyTable.setRowSelectionAllowed(true);
        strategyTable.setColumnSelectionAllowed(false);
        strategyTable.setCellSelectionEnabled(false);
        strategyTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        strategyTable.setSelectionBackground(TABLE_SELECTION_BG);
        strategyTable.setSelectionForeground(new Color(20, 20, 30));
        strategyTable.setShowGrid(false);
        strategyTable.setIntercellSpacing(new Dimension(0, 6));
        StatusRowRenderer statusRowRenderer = new StatusRowRenderer();
        strategyTable.setDefaultRenderer(Object.class, statusRowRenderer);
        strategyTable.setDefaultRenderer(Number.class, statusRowRenderer);
        strategyTable.getColumnModel().getColumn(7).setCellRenderer(new PollingBarRenderer());
        strategyTable.getColumnModel().getColumn(9).setCellRenderer(new ActionsRenderer());
        strategyTable.getColumnModel().getColumn(9).setCellEditor(new ActionsEditor());
        strategyTable.getColumnModel().getColumn(7).setPreferredWidth(240);
        strategyTable.getColumnModel().getColumn(7).setMinWidth(220);
        strategyTable.getColumnModel().getColumn(9).setPreferredWidth(270);
        strategyTable.getColumnModel().getColumn(9).setMinWidth(270);

        // Make table sortable — click column headers to sort
        TableRowSorter<StrategyTableModel> sorter = new TableRowSorter<>(strategyTableModel);
        sorter.setSortable(7, false); // Polling countdown bar column — not sortable
        sorter.setSortable(9, false); // Actions button column — not sortable
        strategyTable.setRowSorter(sorter);

        JScrollPane strategyGrid = new JScrollPane(strategyTable);
        strategyGrid.setViewportBorder(new EmptyBorder(8, 10, 8, 10));
        strategyGrid.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Stock Strategies"),
                new EmptyBorder(8, 10, 8, 10)
        ));


        // ── Status bar ─────────────────────────────────────────────────────────
        // ── Status bar ─────────────────────────────────────────────────────────
        statusBar.setFont(BASE_FONT.deriveFont(12.5f));
        statusBar.setForeground(new Color(200, 100, 100));
        statusBar.setVerticalAlignment(SwingConstants.CENTER);
        statusBar.setBorder(new EmptyBorder(0, 6, 0, 16));

        statusStrategyCount.setFont(BASE_FONT.deriveFont(Font.ITALIC, 12f));
        statusStrategyCount.setForeground(new Color(150, 150, 160));
        statusStrategyCount.setVerticalAlignment(SwingConstants.CENTER);
        statusStrategyCount.setBorder(new EmptyBorder(0, 0, 0, 12));

        JButton faqsButton = new JButton("? Faqs");
        styleStatusActionButton(faqsButton);
        faqsButton.addActionListener(e -> new HelpDialog(this).setVisible(true));

        JButton submitFeatureButton = new JButton("+ Request New Feature");
        styleStatusActionButton(submitFeatureButton);
        submitFeatureButton.addActionListener(e -> openFeedbackDialog("Request New Feature"));

        JButton contactUsButton = new JButton("@ Contact Us");
        styleStatusActionButton(contactUsButton);
        contactUsButton.addActionListener(e -> openFeedbackDialog("Contact Us"));

        JLabel appLabel = new JLabel(AppMetadata.name() + "  " + AppMetadata.version());
        appLabel.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
        appLabel.setForeground(new Color(160, 160, 170));
        appLabel.setVerticalAlignment(SwingConstants.CENTER);
        appLabel.setBorder(new EmptyBorder(0, 12, 0, 8));

        JPanel statusLeft = new JPanel(new GridBagLayout());
        statusLeft.setOpaque(false);
        GridBagConstraints leftGbc = new GridBagConstraints();
        leftGbc.gridy = 0;
        leftGbc.anchor = GridBagConstraints.CENTER;
        leftGbc.insets = new java.awt.Insets(0, 0, 0, 10);
        leftGbc.gridx = 0;
        statusLeft.add(statusBar, leftGbc);
        leftGbc.gridx = 1;
        leftGbc.insets = new java.awt.Insets(0, 0, 0, 0);
        statusLeft.add(statusStrategyCount, leftGbc);

        JPanel statusRight = new JPanel(new GridBagLayout());
        statusRight.setOpaque(false);
        GridBagConstraints rightGbc = new GridBagConstraints();
        rightGbc.gridy = 0;
        rightGbc.anchor = GridBagConstraints.CENTER;
        rightGbc.insets = new java.awt.Insets(0, 0, 0, 10);
        rightGbc.gridx = 0;
        statusRight.add(appLabel, rightGbc);
        rightGbc.gridx = 1;
        statusRight.add(submitFeatureButton, rightGbc);
        rightGbc.gridx = 2;
        statusRight.add(contactUsButton, rightGbc);
        rightGbc.gridx = 3;
        rightGbc.insets = new java.awt.Insets(0, 0, 0, 0);
        statusRight.add(faqsButton, rightGbc);

        JPanel statusBarPanel = new JPanel(new BorderLayout());
        statusBarPanel.setBackground(new Color(35, 35, 45));
        statusBarPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(200, 200, 210)),
                new EmptyBorder(3, 4, 3, 4)
        ));
        statusBarPanel.add(statusLeft, BorderLayout.WEST);
        statusBarPanel.add(statusRight, BorderLayout.EAST);
        // ───────────────────────────────────────────────────────────────────────

        eventLog.setEditable(false);
        eventLog.setBorder(new EmptyBorder(8, 8, 8, 8));
        eventLog.setBackground(new Color(248, 249, 252));
        applyUiPolish();
        applyDataViewFonts();

        // Put event log and strategy grid in a vertical split so both are always visible
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(eventLog), strategyGrid);
        splitPane.setResizeWeight(0.5);
        splitPane.setDividerSize(6);
        splitPane.setBorder(null);

        JPanel positionSection = createDetailSection(positionSectionTitle, positionSummary);
        installCopyPopup(positionSection, positionSummary);
        JPanel rulesSection = createDetailSection(rulesSectionTitle, ruleState);

        JPanel detailSectionsPanel = new JPanel(new GridLayout(0, 1, 0, 8));
        detailSectionsPanel.setOpaque(false);
        detailSectionsPanel.add(positionSection);
        detailSectionsPanel.add(rulesSection);

        JPanel statusPanel = new JPanel(new BorderLayout(0, 10));
        statusPanel.setOpaque(false);
        statusPanel.setBorder(new EmptyBorder(8, 0, 14, 0));
        statusPanel.add(detailSectionsPanel, BorderLayout.CENTER);

        add(headerPanel, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);

        // Wrap status panels + status bar into one SOUTH panel
        JPanel southWrapper = new JPanel(new BorderLayout());
        southWrapper.setBorder(new EmptyBorder(8, 0, 0, 0));
        southWrapper.add(statusPanel, BorderLayout.CENTER);
        southWrapper.add(statusBarPanel, BorderLayout.SOUTH);
        add(southWrapper, BorderLayout.SOUTH);

        wireEvents();
        settingsDialog.setConnectionVerifier(request -> runConnectionTest(request.brokerType(), request.apiKey(), request.apiSecret(), true));
        strategyTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                if (preservingSelection) {
                    return;
                }
                if (strategyTable.getSelectedRow() < 0) {
                    return;
                }
                updateSelectedStrategy();
                refreshPanels();
            }
        });
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdownAllStrategies();
            }
        });
        setExtendedState(getExtendedState() | JFrame.MAXIMIZED_BOTH);
        setLocationRelativeTo(null);
    }

    private void applyUiPolish() {
        applyFontRecursively(this);

        styleHeaderButton(addStrategyButton);
        styleHeaderButton(settingsButton);

        // Buttons with emoji: use app font for text (emoji rendered by system fallback)
        applyEmojiFontToButton(addStrategyButton, 12f);
        applyEmojiFontToButton(settingsButton, 12f);
    }

    private void applyDataViewFonts() {
        eventLog.setFont(FontLoader.ui(Font.PLAIN, 10f));
        strategyTable.setFont(FontLoader.ui(Font.PLAIN, 12f));
        strategyTable.getTableHeader().setFont(FontLoader.ui(Font.BOLD, 12f));
        paperUnrealizedSummary.setFont(headerStatus.getFont());
        liveUnrealizedSummary.setFont(headerStatus.getFont());
        headerTotalsSeparator.setFont(headerStatus.getFont());
        paperUnrealizedSummary.setForeground(new Color(220, 230, 255));
        liveUnrealizedSummary.setForeground(new Color(220, 230, 255));
        headerTotalsSeparator.setForeground(new Color(180, 190, 215));
        paperUnrealizedSummary.setHorizontalAlignment(SwingConstants.CENTER);
        liveUnrealizedSummary.setHorizontalAlignment(SwingConstants.CENTER);
        positionSectionTitle.setFont(FontLoader.ui(Font.BOLD, 10f));
        rulesSectionTitle.setFont(FontLoader.ui(Font.BOLD, 10f));
        positionSummary.setFont(FontLoader.ui(Font.PLAIN, 10f));
        ruleState.setFont(FontLoader.ui(Font.PLAIN, 10f));
    }

    private void applyEmojiFontToButton(JButton button, float size) {
        // Use Poppins for the text; the JVM's composite font handles emoji glyph fallback
        button.setFont(FontLoader.ui(Font.PLAIN, size));
    }

    private JPanel createDetailSection(JLabel titleLabel, JLabel contentLabel) {
        titleLabel.setForeground(new Color(70, 70, 90));
        contentLabel.setForeground(new Color(35, 35, 45));
        contentLabel.setVerticalAlignment(SwingConstants.TOP);
        contentLabel.setBorder(new EmptyBorder(2, 0, 0, 0));

        JPanel section = new JPanel(new BorderLayout(0, 2));
        section.setOpaque(true);
        section.setBackground(new Color(248, 249, 252));
        section.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(210, 214, 223), 1, true),
                new EmptyBorder(10, 12, 10, 12)
        ));
        section.add(titleLabel, BorderLayout.NORTH);
        section.add(contentLabel, BorderLayout.CENTER);
        return section;
    }

    private void applyFontRecursively(Component component) {
        component.setFont(BASE_FONT);
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                applyFontRecursively(child);
            }
        }
    }

    private void styleHeaderButton(JButton button) {
        button.setFocusPainted(false);
        button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        button.setForeground(new Color(230, 230, 255));
        button.setBackground(new Color(60, 60, 90));
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setRolloverEnabled(true);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(100, 100, 160), 1, true),
                new EmptyBorder(7, 12, 7, 12)
        ));
    }

    private void styleStatusActionButton(JButton button) {
        button.setFocusPainted(false);
        button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        button.setFont(BASE_FONT.deriveFont(Font.BOLD, 12f));
        button.setForeground(new Color(220, 220, 255));
        button.setBackground(new Color(60, 60, 90));
        button.setOpaque(true);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(100, 100, 160), 1, true),
                new EmptyBorder(5, 12, 5, 12)
        ));
        button.setMargin(new java.awt.Insets(5, 12, 5, 12));
    }

    /**
     * Installs a right-click "Copy to Clipboard" popup on {@code panel} and its
     * {@code contentLabel}.  The copied text is the current text of the label.
     */
    private void installCopyPopup(JPanel panel, JLabel contentLabel) {
        JPopupMenu popup = new JPopupMenu();
        JMenuItem copyItem = new JMenuItem("📋 Copy to Clipboard");
        copyItem.setFont(BASE_FONT.deriveFont(Font.PLAIN, 12f));
        copyItem.addActionListener(e -> {
            String text = contentLabel.getText();
            if (text != null && !text.isBlank()) {
                Toolkit.getDefaultToolkit()
                        .getSystemClipboard()
                        .setContents(new StringSelection(text), null);
            }
        });
        popup.add(copyItem);

        MouseAdapter handler = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e)  { maybeShow(e); }
            @Override public void mouseReleased(MouseEvent e) { maybeShow(e); }
            private void maybeShow(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    popup.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        };
        panel.addMouseListener(handler);
        contentLabel.addMouseListener(handler);
    }

    private void openFeedbackDialog(String type) {
        FeedbackDialog dialog = new FeedbackDialog(this, type);
        FeedbackDialog.FeedbackData data = dialog.showDialog();
        if (data == null) {
            return;
        }
        log("[" + type + "] Received feedback from " + data.phoneNumber());
        JOptionPane.showMessageDialog(this,
                type + " submitted successfully.",
                "Submitted",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private static Font createBaseFont() {
        return FontLoader.ui(Font.PLAIN, 12);
    }

    private void wireEvents() {
        addStrategyButton.addActionListener(e -> addStrategy());
        settingsButton.addActionListener(e -> openSettingsDialog());
    }

    public void promptForRequiredSettings() {
        if (!settingsDialog.hasRequiredSettings()) {
            openSettingsDialog();
            return;
        }
        if (!autoInitializeConnection()) {
            openSettingsDialog();
        }
    }

    private void openSettingsDialog() {
        settingsDialog.setVisible(true);
        connectionOk = false;
        setStatus("Not connected — verify connection in Settings after changes.", STATUS_WARN);
        updateHeaderModeStatus(currentBrokerType);
        updateStatusBar();
        if (settingsDialog.hasRequiredSettings()) {
            autoInitializeConnection();
        }
    }

    private boolean autoInitializeConnection() {
        SettingsDialog.ConnectionResult result = runConnectionTest(
                settingsDialog.brokerType(),
                settingsDialog.getApiKey(),
                settingsDialog.getApiSecret(),
                false
        );
        return result.connected();
    }

    private void refreshStrategyRuntimeServices() {
        HttpAlpacaClient alpacaClient = new HttpAlpacaClient(
                settingsDialog.getApiKey(),
                settingsDialog.getApiSecret(),
                AppMetadata.alpacaBaseUrl(),
                AppMetadata.alpacaDataUrl()
        );
        strategyService = new StrategyService(
                strategyRepository,
                strategyOrderRepository,
                strategyEventRepository,
                alpacaClient,
                new StrategyValidator(),
                AppMetadata.liveTradingEnabled()
        );
        strategyPollingService = new StrategyPollingService(
                strategyRepository,
                strategyOrderRepository,
                strategyEventRepository,
                alpacaClient
        );
    }

    private SettingsDialog.ConnectionResult runConnectionTest(BrokerType brokerType, String apiKey, String apiSecret, boolean manualTrigger) {
        if (brokerType == null) {
            log("Connection test: FAILED (broker not set in Settings)");
            updateHeaderModeStatus(null);
            headerStatus.setText("Status: broker not configured");
            return new SettingsDialog.ConnectionResult(false, "Broker not configured");
        }
        if (settingsDialog.applicationMode() == ApplicationMode.LIVE && !AppMetadata.liveTradingEnabled()) {
            String message = "LIVE mode is disabled. Set trading.live.enabled=true in app.properties.";
            settingsDialog.markConnectionStatus(false, message);
            setStatus(message, STATUS_ERR);
            return new SettingsDialog.ConnectionResult(false, message);
        }

        tradingApi = TradingApiFactory.create(brokerType, settingsDialog.applicationMode());
        currentBrokerType = brokerType;
        tradingApi.authenticate(apiKey, apiSecret);
        connectionOk = tradingApi.testConnection();
        log((manualTrigger ? "Connection test: " : "Auto connection test: ") + (connectionOk ? "SUCCESS" : "FAILED"));
        if (connectionOk) {
            refreshStrategyRuntimeServices();
            setStatus("Connected — broker " + brokerType.name() + " ready.", STATUS_OK);
            updateHeaderModeStatus(brokerType);
            settingsDialog.markConnectionStatus(true, "Connected to " + brokerType.name());
            updateStatusBar();
            initPersistenceAndRestore();
            return new SettingsDialog.ConnectionResult(true, "Connected to " + brokerType.name());
        } else {
            setStatus("Connection failed — check API credentials in Settings.", STATUS_ERR);
            updateHeaderModeStatus(brokerType);
            settingsDialog.markConnectionStatus(false, "Connection failed");
            updateStatusBar();
            return new SettingsDialog.ConnectionResult(false, "Connection failed");
        }
    }

    private void initPersistenceAndRestore() {
        if (persistenceManager != null) {
            return; // already initialized this session
        }
        String passphrase = buildPassphrase();
        persistenceManager = new StrategyPersistenceManager(
                Path.of(System.getProperty("user.home"), ".neuralarc", "strategies.dat"),
                passphrase
        );
        ensureAnalyticsPublisher();
        restoreStrategies();
    }

    private String buildPassphrase() {
        return identityService.generateUserId(settingsDialog.getUserEmail()).substring(0, 16);
    }

    private void restoreStrategies() {
        List<StrategyEntry> entries = persistenceManager.load();
        if (entries.isEmpty()) {
            refreshPanels();
            updateStatusBar();
            maybePromptForDefaultStrategy();
            return;
        }
        for (StrategyEntry entry : entries) {
            if (findStrategy(entry.config().symbol()) != null) {
                continue; // already exists in current session (shouldn't happen, but guard)
            }
            String userId = identityService.generateUserId(settingsDialog.getUserEmail());
            StrategyConfig restoredConfig = entry.config();
            TradingStrategyService service = new TradingStrategyService(
                    tradingApi,
                    new RuleEvaluationService(),
                    analyticsPublisher,
                    msg -> log("[" + restoredConfig.symbol() + "] " + msg),
                    userId
            );
            service.configure(restoredConfig);
            ManagedStrategy managed = new ManagedStrategy(restoredConfig, service);
            managed.paused = entry.paused();
            resetPollingCountdown(managed);
            strategies.add(managed);
            if (!managed.paused) {
                startStrategy(managed, "STRATEGY_RESUMED");
                log("[" + restoredConfig.symbol() + "] Restored and resumed.");
            } else {
                log("[" + restoredConfig.symbol() + "] Restored (paused).");
            }
        }
        refreshStrategyTableData();
        if (!strategies.isEmpty()) {
            strategyTable.setRowSelectionInterval(0, 0);
        }
        updateSelectedStrategy();
        updateHeaderModeStatus(currentBrokerType);
        refreshPanels();
        updateStatusBar();
    }

    private void maybePromptForDefaultStrategy() {
        if (promptedDefaultStrategyDialog) {
            return;
        }
        promptedDefaultStrategyDialog = true;
        SwingUtilities.invokeLater(this::openDefaultStrategyDialogOnEmptyState);
    }

    private void openDefaultStrategyDialogOnEmptyState() {
        if (!connectionOk || tradingApi == null) {
            log("Auto setup: broker not connected. Please configure Settings before adding a strategy.");
            return;
        }
        addStrategy();
    }

    private void addStrategy() {
        if (!connectionOk || tradingApi == null) {
            JOptionPane.showMessageDialog(this, "Please complete Settings and verify the connection before adding a strategy.", "Connection Required", JOptionPane.WARNING_MESSAGE);
            return;
        }

        StrategyDialog dialog = new StrategyDialog(this, null);
        StrategyConfig config = dialog.showDialog();
        if (config == null) {
            return;
        }

        if (findStrategy(config.symbol()) != null) {
            JOptionPane.showMessageDialog(this, "A strategy for this symbol already exists. Use Edit on the grid row.", "Duplicate Symbol", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Strategy strategy = Strategy.fromConfig(
                UUID.randomUUID().toString(),
                config.symbol() + " Strategy",
                config,
                settingsDialog.applicationMode() == ApplicationMode.LIVE ? StrategyMode.LIVE : StrategyMode.PAPER
        );
        StrategyService.StrategyCreationResult creationResult = strategyService.createAndActivate(strategy);
        if (!creationResult.success()) {
            JOptionPane.showMessageDialog(
                    this,
                    "Failed to submit initial Alpaca limit buy order: " + creationResult.error(),
                    "Strategy Activation Failed",
                    JOptionPane.ERROR_MESSAGE
            );
            log("[" + config.symbol() + "] Strategy failed during initial order placement: " + creationResult.error());
            return;
        }
        log("[" + config.symbol() + "] Initial order submitted. clientOrderId=" + creationResult.clientOrderId());
        JOptionPane.showMessageDialog(
                this,
                "Initial Alpaca limit buy submitted successfully.\nOrder ID: " + creationResult.alpacaOrderId(),
                "Strategy Activated",
                JOptionPane.INFORMATION_MESSAGE
        );

        ensureAnalyticsPublisher();

        String userId = identityService.generateUserId(settingsDialog.getUserEmail());
        TradingStrategyService service = new TradingStrategyService(
                tradingApi,
                new RuleEvaluationService(),
                analyticsPublisher,
                msg -> log("[" + config.symbol() + "] " + msg),
                userId
        );
        service.configure(config);

        ManagedStrategy entry = new ManagedStrategy(config, service);
        strategies.add(entry);
        resetPollingCountdown(entry);
        updateHeaderModeStatus(currentBrokerType);
        startStrategy(entry, "STRATEGY_STARTED");
        persistStrategies();
        refreshStrategyTableData();
        int addedViewRow = strategyTable.convertRowIndexToView(strategies.size() - 1);
        if (addedViewRow >= 0) {
            strategyTable.setRowSelectionInterval(addedViewRow, addedViewRow);
        }
        updateSelectedStrategy();
        refreshPanels();
    }

    private void editStrategy(int viewRow) {
        int row = strategyTable.convertRowIndexToModel(viewRow);
        if (row < 0 || row >= strategies.size()) {
            return;
        }

        ManagedStrategy entry = strategies.get(row);
        StrategyDialog dialog = new StrategyDialog(this, entry.config);
        StrategyConfig updated = dialog.showDialog();
        if (updated == null) {
            return;
        }

        ManagedStrategy duplicate = findStrategy(updated.symbol());
        if (duplicate != null && duplicate != entry) {
            JOptionPane.showMessageDialog(this, "A strategy for this symbol already exists.", "Duplicate Symbol", JOptionPane.WARNING_MESSAGE);
            return;
        }

        boolean wasPaused = entry.paused;
        stopPoller(entry);
        entry.config = updated;
        entry.service.configure(updated);
        resetPollingCountdown(entry);
        if (!wasPaused) {
            startStrategy(entry, "STRATEGY_RESUMED");
        } else {
            entry.paused = true;
            stopPollingCountdown(entry);
        }

        updateHeaderModeStatus(currentBrokerType);
        persistStrategies();
        refreshStrategyTableData();
        refreshPanels();
    }

    private void togglePauseResume(int viewRow) {
        int row = strategyTable.convertRowIndexToModel(viewRow);
        if (row < 0 || row >= strategies.size()) {
            return;
        }

        ManagedStrategy entry = strategies.get(row);
        if (entry.paused) {
            startStrategy(entry, "STRATEGY_RESUMED");
        } else {
            stopPoller(entry);
            entry.paused = true;
            stopPollingCountdown(entry);
            log("Strategy paused for symbol " + entry.config.symbol());
            if (analyticsPublisher != null) {
                analyticsPublisher.publish(new AnalyticsEvent("STRATEGY_PAUSED").put("symbol", entry.config.symbol()));
            }
        }
        persistStrategies();
        refreshStrategyTableRow(row);
        updateStatusBar();
        refreshPanels();
    }

    private void deleteStrategy(int viewRow) {
        int row = strategyTable.convertRowIndexToModel(viewRow);
        if (row < 0 || row >= strategies.size()) {
            return;
        }

        ManagedStrategy entry = strategies.get(row);
        String statusLabel = entry.paused ? "Paused" : "Running";
        String modeLabel = entry.config.paperTrading() ? "Paper Trading" : "⚠️ Live Trading";
        String positionNote;
        if (tradingApi != null) {
            int shares = tradingApi.getPosition(entry.config.symbol()).getTotalShares();
            positionNote = shares > 0
                    ? "• Open position: " + shares + " share(s) held — these will NOT be automatically sold."
                    : "• No open position.";
        } else {
            positionNote = "• Position data not available (broker not connected).";
        }
        String message = "<html><body style='width:340px'>"
                + "<b>Permanently delete the \"" + entry.config.symbol() + "\" strategy?</b><br><br>"
                + "• Status: " + statusLabel + "<br>"
                + "• Mode: " + modeLabel + "<br>"
                + positionNote + "<br><br>"
                + "This will immediately stop polling and permanently remove the strategy from saved data.<br>"
                + "This action <b>cannot be undone</b>."
                + "</body></html>";
        int choice = JOptionPane.showConfirmDialog(
                this,
                message,
                "Delete Strategy — " + entry.config.symbol(),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }

        stopPoller(entry);
        stopPollingCountdown(entry);
        strategies.remove(row);
        log("Deleted strategy for symbol " + entry.config.symbol());
        if (analyticsPublisher != null) {
            analyticsPublisher.publish(new AnalyticsEvent("STRATEGY_DELETED").put("symbol", entry.config.symbol()));
        }

        if (strategies.isEmpty()) {
            selectedStrategySymbol = null;
        } else {
            int nextModelRow = Math.min(row, strategies.size() - 1);
            selectedStrategySymbol = strategies.get(nextModelRow).config.symbol();
        }

        updateHeaderModeStatus(currentBrokerType);
        persistStrategies();
        refreshStrategyTableData();
        if (selectedStrategySymbol != null) {
            restoreSelectedRow();
        } else {
            strategyTable.clearSelection();
        }
        updateStatusBar();
        refreshPanels();
    }

    private void startStrategy(ManagedStrategy entry, String eventType) {
        stopPoller(entry);
        entry.paused = false;
        entry.lastStartedBrokerType = currentBrokerType;
        startPollingCountdown(entry);
        entry.poller = new PricePoller();
        try {
            entry.poller.start(entry.config.pollingSeconds(), () -> {
                entry.service.onPriceTick();
                SwingUtilities.invokeLater(() -> {
                    markPollingCycleCompleted(entry);
                    refreshStrategyTableContent(); // refresh position columns without dropping selection
                    refreshPanels();
                });
            });
        } catch (IllegalStateException ex) {
            entry.paused = true;
            entry.poller = null;
            log("[" + entry.config.symbol() + "] Could not start strategy thread: " + ex.getMessage());
            updateStatusBar();
            refreshPanels();
            return;
        }
        log(("STRATEGY_RESUMED".equals(eventType) ? "Strategy resumed for symbol " : "Strategy started for symbol ") + entry.config.symbol());
        if (analyticsPublisher != null) {
            analyticsPublisher.publish(new AnalyticsEvent(eventType).put("symbol", entry.config.symbol()));
        }
        updateStatusBar();
    }

    private void stopPoller(ManagedStrategy entry) {
        if (entry.poller != null) {
            entry.poller.stop();
            entry.poller = null;
        }
        stopPollingCountdown(entry);
    }

    private void refreshPanels() {
        updateUnrealizedSummaries();
        ManagedStrategy entry = selectedManagedStrategy();
        if (entry == null) {
            positionSummary.setText("Position: -");
            ruleState.setText("Rules: -");
            return;
        }

        if (strategyTable.getSelectedRow() < 0) {
            SwingUtilities.invokeLater(this::restoreSelectedRow);
        }

        if (currentBrokerType == BrokerType.ALPACA && tradingApi != null) {
            Position p = tradingApi.getPosition(entry.config.symbol());
            positionSummary.setText(String.format(
                    "[%s]: Shares=%d | Stock Price=%s | Avg Cost=%s | MarketValue=%s | Invested=%s | Realized=%s | Unrealized=%s",
                    entry.config.symbol(),
                    p.getTotalShares(), p.getLastPrice().toPlainString(), p.getAverageCost(), p.marketValue(), p.totalInvested(), p.getRealizedPnl(), p.unrealizedPnl()));
        } else {
            positionSummary.setText("[" + entry.config.symbol() + "]: Position data available when broker is connected.");
        }
        ruleState.setText("Rules Inflight: " + entry.service.getState().triggeredRules() + " | Status: " + (entry.paused ? "PAUSED" : "RUNNING"));
    }

    private void updateUnrealizedSummaries() {
        if (strategies.isEmpty() || tradingApi == null) {
            paperUnrealizedSummary.setText("Paper Unrealized P&L Total: -");
            liveUnrealizedSummary.setText("Live Unrealized P&L Total: -");
            applyHeaderTotalsVisibility();
            return;
        }

        BigDecimal paperTotal = BigDecimal.ZERO;
        BigDecimal liveTotal = BigDecimal.ZERO;
        for (ManagedStrategy strategy : strategies) {
            Position position = tradingApi.getPosition(strategy.config.symbol());
            BigDecimal unrealized = position.unrealizedPnl();
            if (strategy.config.paperTrading()) {
                paperTotal = paperTotal.add(unrealized);
            } else {
                liveTotal = liveTotal.add(unrealized);
            }
        }
        paperUnrealizedSummary.setText("Paper Unrealized P&L Total: " + paperTotal.toPlainString());
        liveUnrealizedSummary.setText("Live Unrealized P&L Total: " + liveTotal.toPlainString());
        applyHeaderTotalsVisibility();
    }

    private void applyHeaderTotalsVisibility() {
        liveUnrealizedSummary.setVisible(true);
        headerTotalsSeparator.setVisible(true);
    }

    private void updateSelectedStrategy() {
        int viewRow = strategyTable.getSelectedRow();
        if (viewRow < 0) {
            return;
        }
        int modelRow = strategyTable.convertRowIndexToModel(viewRow);
        if (modelRow >= 0 && modelRow < strategies.size()) {
            selectedStrategySymbol = strategies.get(modelRow).config.symbol();
        }
    }

    private void restoreSelectedRow() {
        if (selectedStrategySymbol == null || strategies.isEmpty()) {
            return;
        }
        preservingSelection = true;
        int modelRow = -1;
        for (int i = 0; i < strategies.size(); i++) {
            if (strategies.get(i).config.symbol().equalsIgnoreCase(selectedStrategySymbol)) {
                modelRow = i;
                break;
            }
        }
        if (modelRow < 0) {
            selectedStrategySymbol = null;
            strategyTable.clearSelection();
            preservingSelection = false;
            return;
        }
        int viewRow = strategyTable.convertRowIndexToView(modelRow);
        if (viewRow >= 0 && strategyTable.getSelectedRow() != viewRow) {
            strategyTable.setRowSelectionInterval(viewRow, viewRow);
        }
        preservingSelection = false;
    }

    private void rememberSelectedStrategy() {
        int viewRow = strategyTable.getSelectedRow();
        if (viewRow < 0 || strategyTable.isEditing()) {
            return;
        }
        int modelRow = strategyTable.convertRowIndexToModel(viewRow);
        if (modelRow >= 0 && modelRow < strategies.size()) {
            selectedStrategySymbol = strategies.get(modelRow).config.symbol();
        }
    }

    private ManagedStrategy selectedManagedStrategy() {
        if (selectedStrategySymbol == null) {
            return null;
        }
        return findStrategy(selectedStrategySymbol);
    }

    private void refreshStrategyTableData() {
        rememberSelectedStrategy();
        preservingSelection = true;
        strategyTableModel.fireTableDataChanged();
        preservingSelection = false;
        SwingUtilities.invokeLater(this::restoreSelectedRow);
    }

    private void refreshStrategyTableRow(int modelRow) {
        rememberSelectedStrategy();
        preservingSelection = true;
        strategyTableModel.fireTableRowsUpdated(modelRow, modelRow);
        preservingSelection = false;
        SwingUtilities.invokeLater(this::restoreSelectedRow);
    }

    private void refreshStrategyTableContent() {
        if (strategies.isEmpty()) {
            return;
        }
        rememberSelectedStrategy();
        preservingSelection = true;
        strategyTableModel.fireTableRowsUpdated(0, strategies.size() - 1);
        preservingSelection = false;
        SwingUtilities.invokeLater(() -> {
            restoreSelectedRow();
            strategyTable.repaint();
        });
    }

    private ManagedStrategy findStrategy(String symbol) {
        for (ManagedStrategy strategy : strategies) {
            if (strategy.config.symbol().equalsIgnoreCase(symbol)) {
                return strategy;
            }
        }
        return null;
    }


    private void resetPollingCountdown(ManagedStrategy entry) {
        entry.pollIntervalMillis = Math.max(1L, entry.config.pollingSeconds()) * 1000L;
        entry.nextPollDueAtMillis = 0L;
        entry.countdownActive = false;
    }

    private void startPollingCountdown(ManagedStrategy entry) {
        resetPollingCountdown(entry);
        entry.countdownActive = true;
        entry.nextPollDueAtMillis = System.currentTimeMillis() + entry.pollIntervalMillis;
    }

    private void markPollingCycleCompleted(ManagedStrategy entry) {
        entry.pollIntervalMillis = Math.max(1L, entry.config.pollingSeconds()) * 1000L;
        entry.countdownActive = true;
        entry.nextPollDueAtMillis = System.currentTimeMillis() + entry.pollIntervalMillis;
    }

    private void stopPollingCountdown(ManagedStrategy entry) {
        entry.countdownActive = false;
        entry.nextPollDueAtMillis = 0L;
    }

    private int pollingProgressPercent(ManagedStrategy entry) {
        if (!entry.countdownActive || entry.pollIntervalMillis <= 0L) {
            return 0;
        }
        long remainingMillis = Math.max(0L, entry.nextPollDueAtMillis - System.currentTimeMillis());
        return (int) Math.min(100L, Math.round((remainingMillis * 100.0d) / entry.pollIntervalMillis));
    }

    private long pollingSecondsRemaining(ManagedStrategy entry) {
        if (!entry.countdownActive || entry.pollIntervalMillis <= 0L) {
            return 0L;
        }
        long remainingMillis = Math.max(0L, entry.nextPollDueAtMillis - System.currentTimeMillis());
        return (long) Math.ceil(remainingMillis / 1000.0d);
    }

    private void setStatus(String message, Color color) {
        SwingUtilities.invokeLater(() -> {
            statusBar.setText(" ● " + message);
            statusBar.setForeground(color);
        });
    }

    private String connectionModeStatus(BrokerType brokerType) {
        String mode = settingsDialog.applicationMode() == ApplicationMode.LIVE ? "Live" : "Paper";
        return "Broker: Alpaca | Mode: " + mode;
    }

    private String gridBrokerModeLabel() {
        return settingsDialog.applicationMode() == ApplicationMode.LIVE
                ? "Alpaca Live"
                : "Alpaca Paper Mode";
    }

    private Color gridBrokerModeColor() {
        return settingsDialog.applicationMode() == ApplicationMode.LIVE
                ? MODE_TEXT_ALPACA_LIVE
                : MODE_TEXT_ALPACA_PAPER;
    }

    private void updateStatusBar() {
        long running = strategies.stream().filter(s -> !s.paused).count();
        long paused  = strategies.stream().filter(s ->  s.paused).count();
        SwingUtilities.invokeLater(() -> {
            if (!connectionOk) {
                statusBar.setText(" ● Not connected");
                statusBar.setForeground(STATUS_ERR);
                statusStrategyCount.setText("");
            } else if (running > 0) {
                statusBar.setText(" ● Connected");
                statusBar.setForeground(STATUS_OK);
                statusStrategyCount.setText(running + " stock strategies running" +
                        (paused > 0 ? ",  " + paused + " paused" : ""));
            } else if (paused > 0) {
                statusBar.setText(" ● Connected — idle");
                statusBar.setForeground(STATUS_WARN);
                statusStrategyCount.setText(paused + " strategy paused");
            } else {
                statusBar.setText(" ● Connected — no strategies");
                statusBar.setForeground(STATUS_WARN);
                statusStrategyCount.setText("");
            }
        });
    }

    private void persistStrategies() {
        if (persistenceManager == null) {
            return;
        }
        List<StrategyEntry> entries = strategies.stream()
                .map(s -> new StrategyEntry(s.config, s.paused))
                .toList();
        try {
            persistenceManager.save(entries);
        } catch (Exception e) {
            log("Warning: failed to persist strategies – " + e.getMessage());
        }
    }

    private void ensureAnalyticsPublisher() {
        if (analyticsPublisher == null) {
            boolean analyticsAllowed = AppMetadata.analyticsEnabled() && settingsDialog.telemetryEnabled();
            TelemetryConfig telemetryConfig = new TelemetryConfig(
                    analyticsAllowed,
                    settingsDialog.getEndpoint(),
                    null,
                    "1.0.0"
            );
            analyticsPublisher = new HttpAnalyticsPublisher(telemetryConfig,
                    new AnalyticsQueue(Path.of(System.getProperty("user.home"), ".neuralarc", "analytics-queue.log")));
        }

        if (!appLaunchedPublished) {
            analyticsPublisher.publish(new AnalyticsEvent("APP_LAUNCHED")
                    .put("userId", identityService.generateUserId(settingsDialog.getUserEmail()))
                    .put("sessionId", UUID.randomUUID().toString())
                    .put("paperTrading", true));
            appLaunchedPublished = true;
        }
    }

    private void shutdownAllStrategies() {
        persistStrategies();
        logFlushTimer.stop();
        pollingIndicatorTimer.stop();
        strategyPollingTimer.stop();
        flushLogsToFile();
        for (ManagedStrategy strategy : strategies) {
            stopPoller(strategy);
        }
        PricePoller.shutdownExecutor();
        if (analyticsPublisher != null) {
            analyticsPublisher.publish(new AnalyticsEvent("APP_EXIT"));
            analyticsPublisher.shutdown();
        }
    }

    private void killAllStrategies() {
        if (strategies.isEmpty()) {
            log("[KILL SWITCH] No active strategies to stop.");
            return;
        }

        int stoppedCount = 0;
        for (ManagedStrategy strategy : strategies) {
            if (!strategy.paused) {
                stopPoller(strategy);
                strategy.paused = true;
                stopPollingCountdown(strategy);
                log("[" + strategy.config.symbol() + "] EMERGENCY STOP");
                stoppedCount++;
            }
        }

        persistStrategies();
        refreshStrategyTableData();
        updateStatusBar();
        refreshPanels();

        log("[KILL SWITCH] Stopped " + stoppedCount + " strategy(ies) and saved to file.");
        if (analyticsPublisher != null) {
            analyticsPublisher.publish(new AnalyticsEvent("KILL_SWITCH_ACTIVATED")
                    .put("strategiesStopped", stoppedCount));
        }
    }

    private void updateHeaderModeStatus(BrokerType brokerType) {
        BrokerType effectiveBroker = brokerType == null ? BrokerType.ALPACA : brokerType;
        headerStatus.setText(connectionModeStatus(effectiveBroker));
        boolean blinkLiveAlpaca = effectiveBroker == BrokerType.ALPACA && settingsDialog.applicationMode() == ApplicationMode.LIVE;
        if (!blinkLiveAlpaca) {
            liveModeBlinkTimer.stop();
            headerStatus.setForeground(HEADER_STATUS_DEFAULT);
            return;
        }

        if (!connectionOk) {
            liveBlinkPrimary = HEADER_STATUS_LIVE_ALERT;
            liveBlinkSecondary = HEADER_STATUS_LIVE_ALERT_DIM;
            liveBlinkPrimaryActive = true;
            headerStatus.setForeground(liveBlinkPrimary);
            if (!liveModeBlinkTimer.isRunning()) {
                liveModeBlinkTimer.start();
            }
            return;
        }

        liveBlinkPrimary = HEADER_STATUS_LIVE_ACTIVE;
        liveBlinkSecondary = HEADER_STATUS_LIVE_ACTIVE_DIM;
        liveBlinkPrimaryActive = true;
        headerStatus.setForeground(liveBlinkPrimary);
        if (!liveModeBlinkTimer.isRunning()) {
            liveModeBlinkTimer.start();
        }
    }

    private void toggleLiveHeaderBlink() {
        if (settingsDialog.applicationMode() != ApplicationMode.LIVE) {
            headerStatus.setForeground(HEADER_STATUS_DEFAULT);
            liveModeBlinkTimer.stop();
            return;
        }
        liveBlinkPrimaryActive = !liveBlinkPrimaryActive;
        headerStatus.setForeground(liveBlinkPrimaryActive ? liveBlinkPrimary : liveBlinkSecondary);
    }

    private boolean hasAnyRealTradingStrategy() {
        return strategies.stream().anyMatch(s -> !s.config.paperTrading());
    }

    private void log(String message) {
        String timestamp = formatLogTimestamp();
        SwingUtilities.invokeLater(() -> {
            String logEntry = "[" + timestamp + "] " + message + System.lineSeparator();
            appendLogEntry(logEntry);
            pendingLogWrites.append(logEntry);
        });
    }

    private void appendLogEntry(String logEntry) {
        StyledDocument document = eventLog.getStyledDocument();
        SimpleAttributeSet attributes = new SimpleAttributeSet();
        StyleConstants.setForeground(attributes, (logLineCount % 2 == 0) ? LOG_LINE_EVEN : LOG_LINE_ODD);
        StyleConstants.setFontFamily(attributes, eventLog.getFont().getFamily());
        StyleConstants.setFontSize(attributes, eventLog.getFont().getSize());
        try {
            document.insertString(document.getLength(), logEntry, attributes);
            logLineCount++;
        } catch (BadLocationException e) {
            throw new IllegalStateException("Failed to append log entry", e);
        }
        eventLog.setCaretPosition(document.getLength());
    }

    private void flushLogsToFile() {
        if (pendingLogWrites.isEmpty()) {
            return;
        }
        try {
            Files.createDirectories(appLogFile.getParent());
            Files.writeString(
                    appLogFile,
                    pendingLogWrites.toString(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
            pendingLogWrites.setLength(0);
        } catch (Exception e) {
            // Keep the in-memory buffer intact and continue running.
        }
    }

    private String formatLogTimestamp() {
        ZonedDateTime now = ZonedDateTime.now();
        int day = now.getDayOfMonth();
        return String.format("%s %d%s - %s",
                now.format(LOG_DATE_FORMAT),
                day,
                daySuffix(day),
                now.format(LOG_TIME_FORMAT));
    }

    private String daySuffix(int day) {
        if (day >= 11 && day <= 13) {
            return "th";
        }
        return switch (day % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
    }

    private final class StrategyTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {
                "Symbol", "Status", "Shares", "Avg Cost", "Stock Price", "Market Value", "Unrealized P&L", "Polling", "Broker + Mode", "Actions"
        };

        @Override public int getRowCount()    { return strategies.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            ManagedStrategy entry = strategies.get(rowIndex);
            if (columnIndex >= 2 && columnIndex <= 6 && tradingApi != null && currentBrokerType == BrokerType.ALPACA) {
                Position p = tradingApi.getPosition(entry.config.symbol());
                return switch (columnIndex) {
                    case 2 -> p.getTotalShares();
                    case 3 -> p.getTotalShares() > 0 ? p.getAverageCost().toPlainString() : "-";
                    case 4 -> p.getLastPrice().compareTo(BigDecimal.ZERO) > 0 ? p.getLastPrice().toPlainString() : "-";
                    case 5 -> p.getTotalShares() > 0 ? p.marketValue().toPlainString() : "-";
                    case 6 -> p.getTotalShares() > 0 ? p.unrealizedPnl().toPlainString() : "-";
                    default -> "";
                };
            }
            return switch (columnIndex) {
                case 0 -> entry.config.symbol();
                case 1 -> entry.paused ? "Paused" : "Running";
                case 2 -> "-";
                case 3 -> "-";
                case 4 -> "-";
                case 5 -> "-";
                case 6 -> "-";
                case 7 -> entry.config.pollingSeconds();
                case 8 -> gridBrokerModeLabel();
                case 9 -> entry.paused ? "Paused" : "Running";
                default -> "";
            };
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 9;
        }
    }

    private static final class ManagedStrategy {
        private StrategyConfig config;
        private final TradingStrategyService service;
        private PricePoller poller;
        private boolean paused;
        private BrokerType lastStartedBrokerType = BrokerType.ALPACA;
        private volatile long pollIntervalMillis;
        private volatile long nextPollDueAtMillis;
        private volatile boolean countdownActive;

        private ManagedStrategy(StrategyConfig config, TradingStrategyService service) {
            this.config = config;
            this.service = service;
            this.paused = true;
        }
    }

    private final class StatusRowRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            int modelRow = table.convertRowIndexToModel(row);
            if (modelRow >= 0 && modelRow < strategies.size()) {
                boolean paused = strategies.get(modelRow).paused;
                if (isSelected) {
                    setBackground(TABLE_SELECTION_BG);
                    setForeground(new Color(30, 30, 30));
                } else {
                    setBackground(table.getBackground());
                    if (column == 1) {
                        setForeground(paused ? STATUS_TEXT_PAUSED : STATUS_TEXT_RUNNING);
                    } else if (column == 8) {
                        setForeground(gridBrokerModeColor());
                    } else {
                        setForeground(table.getForeground());
                    }
                }
            }
            setOpaque(true);
            setHorizontalAlignment(CENTER);
            return this;
        }
    }

    private final class PollingBarRenderer extends JPanel implements TableCellRenderer {
        private final JProgressBar progressBar = new JProgressBar(0, 100);
        private final JLabel countdownLabel = new JLabel();

        private PollingBarRenderer() {
            super(new BorderLayout());
            setOpaque(true);
            setBorder(new EmptyBorder(4, 8, 4, 8));
            countdownLabel.setOpaque(false);
            countdownLabel.setFont(FontLoader.ui(Font.PLAIN, 10f));
            countdownLabel.setHorizontalAlignment(SwingConstants.LEFT);
            countdownLabel.setBorder(new EmptyBorder(0, 8, 0, 0));
            progressBar.setOpaque(true);
            progressBar.setBorder(BorderFactory.createEmptyBorder());
            progressBar.setStringPainted(false);
            progressBar.setPreferredSize(new Dimension(90, 12));
            progressBar.setComponentOrientation(java.awt.ComponentOrientation.RIGHT_TO_LEFT);
            progressBar.setForeground(new Color(94, 53, 177));
            add(progressBar, BorderLayout.WEST);
            add(countdownLabel, BorderLayout.CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            int modelRow = table.convertRowIndexToModel(row);
            ManagedStrategy strategy = strategies.get(modelRow);
            int progress = pollingProgressPercent(strategy);
            long secondsRemaining = pollingSecondsRemaining(strategy);
            long totalSeconds = Math.max(1L, strategy.config.pollingSeconds());

            setBackground(selectionAwareRowColor(isSelected, table));
            progressBar.setValue(progress);
            progressBar.setBackground(isSelected ? new Color(228, 217, 250) : new Color(232, 236, 242));
            progressBar.setForeground(strategy.paused ? STATUS_TEXT_PAUSED : new Color(94, 53, 177));
            countdownLabel.setForeground(isSelected ? new Color(30, 30, 30) : table.getForeground());
            countdownLabel.setText(strategy.paused ? "Paused" : secondsRemaining + "s / " + totalSeconds + "s");
            progressBar.setToolTipText(strategy.paused
                    ? "Polling paused"
                    : secondsRemaining + " seconds remaining out of " + totalSeconds + " seconds");
            return this;
        }
    }

    private final class ActionsRenderer extends JPanel implements TableCellRenderer {
        private final JButton editButton = new JButton("Edit");
        private final JButton toggleButton = new JButton();
        private final JButton deleteButton = new JButton("Delete");

        private ActionsRenderer() {
            super(new GridLayout(1, 3, 6, 0));
            setOpaque(true);
            styleActionButton(editButton, new Color(63, 81, 181));
            styleActionButton(toggleButton, new Color(198, 40, 40));
            styleActionButton(deleteButton, new Color(156, 39, 39));
            add(editButton);
            add(toggleButton);
            add(deleteButton);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            int modelRow = table.convertRowIndexToModel(row);
            boolean paused = strategies.get(modelRow).paused;
            toggleButton.setText(paused ? "Resume" : "Pause");
            styleActionButton(toggleButton, paused ? new Color(46, 125, 50) : new Color(198, 40, 40));
            setBackground(selectionAwareRowColor(isSelected, table));
            return this;
        }
    }

    private final class ActionsEditor extends AbstractCellEditor implements javax.swing.table.TableCellEditor {
        private final JPanel panel = new JPanel(new GridLayout(1, 3, 6, 0));
        private final JButton editButton = new JButton("Edit");
        private final JButton toggleButton = new JButton();
        private final JButton deleteButton = new JButton("Delete");
        private int viewRow = -1;

        private ActionsEditor() {
            panel.setOpaque(true);
            styleActionButton(editButton, new Color(63, 81, 181));
            styleActionButton(toggleButton, new Color(198, 40, 40));
            styleActionButton(deleteButton, new Color(156, 39, 39));
            editButton.addActionListener(e -> {
                fireEditingStopped();
                editStrategy(viewRow);
            });
            toggleButton.addActionListener(e -> {
                fireEditingStopped();
                togglePauseResume(viewRow);
            });
            deleteButton.addActionListener(e -> {
                fireEditingStopped();
                deleteStrategy(viewRow);
            });
            panel.add(editButton);
            panel.add(toggleButton);
            panel.add(deleteButton);
        }

        @Override
        public Object getCellEditorValue() {
            return "";
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            this.viewRow = row;
            if (table.getSelectedRow() != row) {
                table.setRowSelectionInterval(row, row);
            }
            int modelRow = table.convertRowIndexToModel(row);
            boolean paused = strategies.get(modelRow).paused;
            toggleButton.setText(paused ? "Resume" : "Pause");
            styleActionButton(toggleButton, paused ? new Color(46, 125, 50) : new Color(198, 40, 40));
            panel.setBackground(selectionAwareRowColor(true, table));
            return panel;
        }
    }

    private Color selectionAwareRowColor(boolean selected, JTable table) {
        return selected ? TABLE_SELECTION_BG : table.getBackground();
    }

    private void styleActionButton(JButton button, Color background) {
        button.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
        button.setMargin(new java.awt.Insets(4, 8, 4, 8));
        button.setFocusPainted(false);
        button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setHorizontalAlignment(SwingConstants.CENTER);
        button.setRolloverEnabled(true);
        button.setForeground(Color.WHITE);
        button.putClientProperty("actionButtonBase", background);
        button.putClientProperty("actionButtonHover", background.brighter());
        updateActionButtonColor(button, background);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(background.darker(), 1, true),
                new EmptyBorder(2, 6, 2, 6)
        ));
        if (!Boolean.TRUE.equals(button.getClientProperty("actionButtonHoverInstalled"))) {
            button.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    if (button.isEnabled()) {
                        updateActionButtonColor(button, (Color) button.getClientProperty("actionButtonHover"));
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    updateActionButtonColor(button, (Color) button.getClientProperty("actionButtonBase"));
                }
            });
            button.putClientProperty("actionButtonHoverInstalled", Boolean.TRUE);
        }
    }

    private void updateActionButtonColor(JButton button, Color background) {
        button.setBackground(background);
        button.setForeground(Color.WHITE);
    }
}
