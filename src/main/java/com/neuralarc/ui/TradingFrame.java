package com.neuralarc.ui;

import com.neuralarc.analytics.*;
import com.neuralarc.api.TradingApi;
import com.neuralarc.api.TradingApiFactory;
import com.neuralarc.model.*;
import com.neuralarc.rules.RuleEvaluationService;
import com.neuralarc.service.PricePoller;
import com.neuralarc.service.StrategyPersistenceManager;
import com.neuralarc.service.StrategyPersistenceManager.StrategyEntry;
import com.neuralarc.service.TradingStrategyService;
import com.neuralarc.service.UserIdentityService;
import com.neuralarc.util.FontLoader;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntConsumer;

public class TradingFrame extends JFrame {
    private static final Font BASE_FONT = createBaseFont();
    private static final int OUTER_PADDING = 16;
    private static final DateTimeFormatter LOG_DATE_FORMAT = DateTimeFormatter.ofPattern("EEE, MMM");
    private static final DateTimeFormatter LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a");

    private final JLabel positionSummary = new JLabel("Position: -");    private final JLabel ruleState = new JLabel("Rules: -");
    private final JLabel statusBar = new JLabel(" ● Not connected");
    private final JLabel statusStrategyCount = new JLabel("");
    private final JLabel headerStatus = new JLabel("Status: waiting for settings");
    private static final Color STATUS_OK = new Color(34, 139, 34);
    private static final Color STATUS_WARN = new Color(180, 100, 0);
    private static final Color STATUS_ERR = new Color(180, 30, 30);
    private final JTextArea eventLog = new JTextArea(12, 80);
    private final JButton testConnectionButton = new JButton("📡 Test Connection");
    private final JButton addStrategyButton = new JButton("📊 Add New Stock Strategy");
    private final JButton settingsButton = new JButton("⚙️ Settings");
    private final JButton contactUsButton = new JButton("Contact Us");
    private final JButton submitFeedbackButton = new JButton("Submit Feedback");

    private final UserIdentityService identityService = new UserIdentityService();
    private final List<ManagedStrategy> strategies = new ArrayList<>();
    private final StrategyTableModel strategyTableModel = new StrategyTableModel();
    private final JTable strategyTable = new JTable(strategyTableModel);

    private TradingApi tradingApi;
    private AnalyticsPublisher analyticsPublisher;
    private final SettingsDialog settingsDialog;
    private StrategyPersistenceManager persistenceManager;
    private boolean connectionOk;
    private boolean appLaunchedPublished;

    public TradingFrame() {
        setTitle("NeuralArc Trader Application");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        ((JComponent) getContentPane()).setBorder(new EmptyBorder(OUTER_PADDING, OUTER_PADDING, OUTER_PADDING, OUTER_PADDING));
        settingsDialog = new SettingsDialog(this);

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(35, 35, 45));
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(200, 200, 210)),
                new EmptyBorder(6, 8, 6, 8)
        ));

        headerStatus.setFont(BASE_FONT.deriveFont(Font.BOLD, 12f));
        headerStatus.setForeground(new Color(220, 220, 255));

        JPanel leftControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftControls.setOpaque(false);
        leftControls.add(headerStatus);
        JPanel rightControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightControls.setOpaque(false);
        rightControls.add(addStrategyButton);
        rightControls.add(testConnectionButton);
        rightControls.add(settingsButton);

        JButton killSwitchButton = new JButton("⚠️ KILL SWITCH");
        killSwitchButton.setFocusPainted(false);
        killSwitchButton.setFont(FontLoader.ui(Font.BOLD, 12f));
        killSwitchButton.setForeground(Color.WHITE);
        killSwitchButton.setBackground(new Color(180, 20, 20));
        killSwitchButton.setOpaque(true);
        killSwitchButton.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(120, 10, 10), 1, true),
                new EmptyBorder(6, 12, 6, 12)
        ));
        killSwitchButton.addActionListener(e -> killAllStrategies());
        rightControls.add(killSwitchButton);
        headerPanel.add(leftControls, BorderLayout.WEST);
        headerPanel.add(rightControls, BorderLayout.EAST);

        strategyTable.setRowHeight(32);
        strategyTable.setFillsViewportHeight(true);
        strategyTable.setDefaultRenderer(Object.class, new StatusRowRenderer());
        strategyTable.getColumnModel().getColumn(7).setCellRenderer(new ButtonRenderer());
        strategyTable.getColumnModel().getColumn(7).setCellEditor(new ButtonEditor(this::editStrategy));
        strategyTable.getColumnModel().getColumn(8).setCellRenderer(new ButtonRenderer());
        strategyTable.getColumnModel().getColumn(8).setCellEditor(new ButtonEditor(this::togglePauseResume));

        // Make table sortable — click column headers to sort
        TableRowSorter<StrategyTableModel> sorter = new TableRowSorter<>(strategyTableModel);
        sorter.setSortable(7, false); // Edit button column — not sortable
        sorter.setSortable(8, false); // Pause/Resume column — not sortable
        strategyTable.setRowSorter(sorter);

        JScrollPane strategyGrid = new JScrollPane(strategyTable);
        strategyGrid.setBorder(BorderFactory.createTitledBorder("Stock Strategies"));


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
        faqsButton.setFocusPainted(false);
        faqsButton.setFont(BASE_FONT.deriveFont(Font.BOLD, 12f));
        faqsButton.setForeground(new Color(220, 220, 255));
        faqsButton.setBackground(new Color(60, 60, 90));
        faqsButton.setOpaque(true);
        faqsButton.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(100, 100, 160), 1, true),
                new EmptyBorder(4, 12, 4, 12)
        ));
        faqsButton.addActionListener(e -> new HelpDialog(this).setVisible(true));
        contactUsButton.setFocusPainted(false);
        contactUsButton.setFont(BASE_FONT.deriveFont(Font.BOLD, 12f));
        contactUsButton.addActionListener(e -> new ContactUsDialog(this).setVisible(true));
        submitFeedbackButton.setFocusPainted(false);
        submitFeedbackButton.setFont(BASE_FONT.deriveFont(Font.BOLD, 12f));
        submitFeedbackButton.addActionListener(e -> new SubmitFeedbackDialog(this).setVisible(true));

        JLabel appLabel = new JLabel("NeuralArc Trader  v1.0");
        appLabel.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
        appLabel.setForeground(new Color(160, 160, 170));
        appLabel.setVerticalAlignment(SwingConstants.CENTER);
        appLabel.setBorder(new EmptyBorder(0, 12, 0, 8));

        JPanel statusLeft = new JPanel(new GridBagLayout());
        statusLeft.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new java.awt.Insets(0, 0, 0, 0);
        statusLeft.add(statusBar, gbc);
        statusLeft.add(statusStrategyCount, gbc);

        JPanel statusRight = new JPanel(new GridBagLayout());
        statusRight.setOpaque(false);
        statusRight.add(appLabel, gbc);
        statusRight.add(contactUsButton, gbc);
        statusRight.add(submitFeedbackButton, gbc);
        statusRight.add(faqsButton, gbc);

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
        eventLog.setLineWrap(true);
        eventLog.setWrapStyleWord(false);
        applyUiPolish();

        // Put event log and strategy grid in a vertical split so both are always visible
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(eventLog), strategyGrid);
        splitPane.setResizeWeight(0.5);
        splitPane.setDividerSize(6);
        splitPane.setBorder(null);

        JPanel statusPanel = new JPanel(new GridLayout(0, 1, 0, 6));
        statusPanel.setBorder(new EmptyBorder(8, 0, 0, 0));
        statusPanel.add(positionSummary);
        statusPanel.add(ruleState);

        add(headerPanel, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);

        // Wrap status panels + status bar into one SOUTH panel
        JPanel southWrapper = new JPanel(new BorderLayout());
        southWrapper.setBorder(new EmptyBorder(8, 0, 0, 0));
        southWrapper.add(statusPanel, BorderLayout.CENTER);
        southWrapper.add(statusBarPanel, BorderLayout.SOUTH);
        add(southWrapper, BorderLayout.SOUTH);

        wireEvents();
        strategyTable.getSelectionModel().addListSelectionListener(e -> refreshPanels());
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

        styleHeaderButton(testConnectionButton);
        styleHeaderButton(addStrategyButton);
        styleHeaderButton(settingsButton);

        // Buttons with emoji: use Poppins for text (emoji rendered by system fallback)
        applyEmojiFontToButton(testConnectionButton, 14f);
        applyEmojiFontToButton(addStrategyButton, 14f);
        applyEmojiFontToButton(settingsButton, 14f);
    }

    private void applyEmojiFontToButton(JButton button, float size) {
        // Use Poppins for the text; the JVM's composite font handles emoji glyph fallback
        button.setFont(FontLoader.ui(Font.PLAIN, size));
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
        button.setForeground(new Color(230, 230, 255));
        button.setBackground(new Color(60, 60, 90));
        button.setOpaque(true);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(100, 100, 160), 1, true),
                new EmptyBorder(7, 12, 7, 12)
        ));
    }

    private static Font createBaseFont() {
        return FontLoader.ui(Font.PLAIN, 14);
    }

    private void wireEvents() {
        testConnectionButton.addActionListener(e -> testConnection());
        addStrategyButton.addActionListener(e -> addStrategy());
        settingsButton.addActionListener(e -> openSettingsDialog());
    }

    public void promptForRequiredSettings() {
        if (!settingsDialog.hasRequiredSettings()) {
            openSettingsDialog();
            return;
        }
        autoInitializeConnection();
    }

    private void openSettingsDialog() {
        settingsDialog.setVisible(true);
        connectionOk = false;
        setStatus("Not connected — re-run Test Connection after changing settings.", STATUS_WARN);
        updateStatusBar();
        if (settingsDialog.hasRequiredSettings()) {
            autoInitializeConnection();
        }
    }


    private void testConnection() {
        runConnectionTest(true);
    }

    private void autoInitializeConnection() {
        runConnectionTest(false);
    }

    private void runConnectionTest(boolean manualTrigger) {
        BrokerType brokerType = settingsDialog.brokerType();
        if (brokerType == null) {
            log("Connection test: FAILED (broker not set in Settings)");
            headerStatus.setText("Status: broker not configured");
            return;
        }

        tradingApi = TradingApiFactory.create(brokerType);
        tradingApi.authenticate(settingsDialog.getApiKey(), settingsDialog.getApiSecret());
        connectionOk = tradingApi.testConnection();
        log((manualTrigger ? "Connection test: " : "Auto connection test: ") + (connectionOk ? "SUCCESS" : "FAILED"));
        if (connectionOk) {
            setStatus("Connected — broker " + brokerType.name() + " ready.", STATUS_OK);
            headerStatus.setText("Status: connected to " + brokerType.name());
            updateStatusBar();
            initPersistenceAndRestore();
        } else {
            setStatus("Connection failed — check API credentials in Settings.", STATUS_ERR);
            headerStatus.setText("Status: connection failed");
            updateStatusBar();
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
            strategies.add(managed);
            if (!managed.paused) {
                startStrategy(managed, "STRATEGY_RESUMED");
                log("[" + restoredConfig.symbol() + "] Restored and resumed.");
            } else {
                log("[" + restoredConfig.symbol() + "] Restored (paused).");
            }
        }
        strategyTableModel.fireTableDataChanged();
        if (!strategies.isEmpty()) {
            strategyTable.setRowSelectionInterval(0, 0);
        }
        updateStatusBar();
    }

    private void addStrategy() {
        if (!connectionOk || tradingApi == null) {
            JOptionPane.showMessageDialog(this, "Please complete Settings and Test Connection before adding a strategy.", "Connection Required", JOptionPane.WARNING_MESSAGE);
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
        startStrategy(entry, "STRATEGY_STARTED");
        persistStrategies();
        strategyTableModel.fireTableDataChanged();
        strategyTable.setRowSelectionInterval(strategies.size() - 1, strategies.size() - 1);
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
        if (!wasPaused) {
            startStrategy(entry, "STRATEGY_RESUMED");
        } else {
            entry.paused = true;
        }

        persistStrategies();
        strategyTableModel.fireTableDataChanged();
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
            log("Strategy paused for symbol " + entry.config.symbol());
            if (analyticsPublisher != null) {
                analyticsPublisher.publish(new AnalyticsEvent("STRATEGY_PAUSED").put("symbol", entry.config.symbol()));
            }
        }
        persistStrategies();
        strategyTableModel.fireTableRowsUpdated(row, row);
        updateStatusBar();
        refreshPanels();
    }

    private void startStrategy(ManagedStrategy entry, String eventType) {
        stopPoller(entry);
        entry.paused = false;
        entry.poller = new PricePoller();
        entry.poller.start(entry.config.pollingSeconds(), () -> {
            entry.service.onPriceTick();
            SwingUtilities.invokeLater(() -> {
                strategyTableModel.fireTableDataChanged(); // refresh position columns
                refreshPanels();
            });
        });
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
    }

    private void refreshPanels() {
        int viewRow = strategyTable.getSelectedRow();
        int row = (viewRow >= 0) ? strategyTable.convertRowIndexToModel(viewRow) : -1;
        if (row < 0) {
            if (strategies.isEmpty() || tradingApi == null) {
                positionSummary.setText("Position: -");
                ruleState.setText("Rules: -");
                return;
            }
            row = 0;
        }

        ManagedStrategy entry = strategies.get(row);
        Position p = tradingApi.getPosition(entry.config.symbol());
        positionSummary.setText(String.format(
                "Position[%s]: shares=%d avgCost=%s marketValue=%s invested=%s realized=%s unrealized=%s",
                entry.config.symbol(),
                p.getTotalShares(), p.getAverageCost(), p.marketValue(), p.totalInvested(), p.getRealizedPnl(), p.unrealizedPnl()));
        ruleState.setText("Rules triggered: " + entry.service.getState().triggeredRules() + " | Status: " + (entry.paused ? "PAUSED" : "RUNNING"));
    }

    private ManagedStrategy findStrategy(String symbol) {
        for (ManagedStrategy strategy : strategies) {
            if (strategy.config.symbol().equalsIgnoreCase(symbol)) {
                return strategy;
            }
        }
        return null;
    }

    private void setStatus(String message, Color color) {
        SwingUtilities.invokeLater(() -> {
            statusBar.setText(" ● " + message);
            statusBar.setForeground(color);
        });
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
                statusStrategyCount.setText(running + " running" +
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
            TelemetryConfig telemetryConfig = new TelemetryConfig(
                    settingsDialog.telemetryEnabled(),
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
        for (ManagedStrategy strategy : strategies) {
            stopPoller(strategy);
        }
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
                log("[" + strategy.config.symbol() + "] EMERGENCY STOP");
                stoppedCount++;
            }
        }

        persistStrategies();
        strategyTableModel.fireTableDataChanged();
        updateStatusBar();
        refreshPanels();

        log("[KILL SWITCH] Stopped " + stoppedCount + " strategy(ies) and saved to file.");
        if (analyticsPublisher != null) {
            analyticsPublisher.publish(new AnalyticsEvent("KILL_SWITCH_ACTIVATED")
                    .put("strategiesStopped", stoppedCount));
        }
    }

    private void log(String message) {
        String timestamp = formatLogTimestamp();
        SwingUtilities.invokeLater(() -> {
            String logEntry = "[" + timestamp + "] " + message + System.lineSeparator();
            eventLog.append(logEntry);
            eventLog.setCaretPosition(eventLog.getDocument().getLength());
        });
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
                "Symbol", "Status", "Shares", "Avg Cost", "Unrealized P&L", "Polling (s)", "Mode", "Edit", "Pause/Resume"
        };

        @Override public int getRowCount()    { return strategies.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            ManagedStrategy entry = strategies.get(rowIndex);
            if (columnIndex >= 2 && columnIndex <= 4 && tradingApi != null) {
                Position p = tradingApi.getPosition(entry.config.symbol());
                return switch (columnIndex) {
                    case 2 -> p.getTotalShares();
                    case 3 -> p.getTotalShares() > 0 ? p.getAverageCost().toPlainString() : "-";
                    case 4 -> p.getTotalShares() > 0 ? p.unrealizedPnl().toPlainString() : "-";
                    default -> "";
                };
            }
            return switch (columnIndex) {
                case 0 -> entry.config.symbol();
                case 1 -> entry.paused ? "Paused" : "Running";
                case 2 -> "-";
                case 3 -> "-";
                case 4 -> "-";
                case 5 -> entry.config.pollingSeconds();
                case 6 -> entry.config.paperTrading() ? "Paper" : "Live";
                case 7 -> "Edit";
                case 8 -> entry.paused ? "Resume" : "Pause";
                default -> "";
            };
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 7 || columnIndex == 8;
        }
    }

    private static final class ManagedStrategy {
        private StrategyConfig config;
        private final TradingStrategyService service;
        private PricePoller poller;
        private boolean paused;

        private ManagedStrategy(StrategyConfig config, TradingStrategyService service) {
            this.config = config;
            this.service = service;
            this.paused = true;
        }
    }

    /** Colors table rows based on strategy running/paused status. */
    private final class StatusRowRenderer extends DefaultTableCellRenderer {
        private static final Color COLOR_RUNNING      = new Color(230, 248, 230); // soft green
        private static final Color COLOR_RUNNING_SEL  = new Color(180, 225, 180);
        private static final Color COLOR_PAUSED       = new Color(255, 248, 220); // soft amber
        private static final Color COLOR_PAUSED_SEL   = new Color(240, 220, 160);

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            int modelRow = table.convertRowIndexToModel(row);
            if (modelRow >= 0 && modelRow < strategies.size()) {
                boolean paused = strategies.get(modelRow).paused;
                if (isSelected) {
                    setBackground(paused ? COLOR_PAUSED_SEL : COLOR_RUNNING_SEL);
                } else {
                    setBackground(paused ? COLOR_PAUSED : COLOR_RUNNING);
                }
                setForeground(new Color(30, 30, 30));
            }
            setHorizontalAlignment(CENTER);
            return this;
        }
    }

    private static final class ButtonRenderer extends JButton implements TableCellRenderer {
        private ButtonRenderer() {
            setOpaque(true);
            setFocusPainted(false);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            setText(value == null ? "" : value.toString());
            return this;
        }
    }

    private static final class ButtonEditor extends AbstractCellEditor implements TableCellEditor {
        private final JButton button = new JButton();
        private final IntConsumer onClick;
        private int row = -1;
        private String text = "";

        private ButtonEditor(IntConsumer onClick) {
            this.onClick = onClick;
            button.setFocusPainted(false);
            button.addActionListener(e -> fireEditingStopped());
        }

        @Override
        public Object getCellEditorValue() {
            if (row >= 0) {
                onClick.accept(row);
            }
            return text;
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            this.row = row;
            this.text = value == null ? "" : value.toString();
            button.setText(text);
            return button;
        }
    }
}
