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

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.IntConsumer;

public class TradingFrame extends JFrame {
    private static final Font BASE_FONT = createBaseFont();
    private static final int OUTER_PADDING = 16;

    private final JCheckBox telemetryConsent = new JCheckBox("I consent to telemetry/analytics");
    private final JLabel positionSummary = new JLabel("Position: -");
    private final JLabel ruleState = new JLabel("Rules: -");
    private final JTextArea eventLog = new JTextArea(12, 80);
    private final JButton testConnectionButton = new JButton("Test Connection");
    private final JButton addStrategyButton = new JButton("Add New Stock Strategy");
    private final JButton settingsButton = new JButton("Settings");

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

        JPanel controlPanel = new JPanel(new BorderLayout());
        controlPanel.setBorder(new EmptyBorder(8, 0, 8, 0));
        JPanel leftControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftControls.add(addStrategyButton);
        JPanel rightControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightControls.add(testConnectionButton);
        rightControls.add(settingsButton);
        controlPanel.add(leftControls, BorderLayout.WEST);
        controlPanel.add(rightControls, BorderLayout.EAST);

        strategyTable.setRowHeight(32);
        strategyTable.setFillsViewportHeight(true);
        strategyTable.getColumnModel().getColumn(4).setCellRenderer(new ButtonRenderer());
        strategyTable.getColumnModel().getColumn(4).setCellEditor(new ButtonEditor(this::editStrategy));
        strategyTable.getColumnModel().getColumn(5).setCellRenderer(new ButtonRenderer());
        strategyTable.getColumnModel().getColumn(5).setCellEditor(new ButtonEditor(this::togglePauseResume));
        JScrollPane strategyGrid = new JScrollPane(strategyTable);
        strategyGrid.setBorder(BorderFactory.createTitledBorder("Stock Strategies"));

        JPanel disclosurePanel = new JPanel(new GridLayout(0, 1, 0, 6));
        disclosurePanel.setBorder(new EmptyBorder(8, 0, 0, 0));
        disclosurePanel.add(telemetryConsent);
        disclosurePanel.add(new JLabel("If enabled, this app sends limited trading activity and performance events to the publisher analytics API."));
        disclosurePanel.add(new JLabel("Paper trading is recommended before live trading."));

        eventLog.setEditable(false);
        eventLog.setBorder(new EmptyBorder(8, 8, 8, 8));
        applyUiPolish();

        add(controlPanel, BorderLayout.NORTH);
        add(new JScrollPane(eventLog), BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout());
        south.setBorder(new EmptyBorder(10, 0, 0, 0));
        south.add(strategyGrid, BorderLayout.CENTER);
        JPanel statusPanel = new JPanel(new GridLayout(0, 1, 0, 6));
        statusPanel.add(positionSummary);
        statusPanel.add(ruleState);
        statusPanel.add(disclosurePanel);
        south.add(statusPanel, BorderLayout.SOUTH);
        add(south, BorderLayout.SOUTH);

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

        styleButton(testConnectionButton);
        styleButton(addStrategyButton);
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

    private void styleButton(JButton button) {
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200), 1, true),
                new EmptyBorder(8, 14, 8, 14)
        ));
    }

    private static Font createBaseFont() {
        if (isPoppinsAvailable()) {
            return new Font("Poppins", Font.PLAIN, 14);
        }
        return new Font("SansSerif", Font.PLAIN, 14);
    }

    private static boolean isPoppinsAvailable() {
        for (String family : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
            if ("Poppins".equalsIgnoreCase(family)) {
                return true;
            }
        }
        return false;
    }

    private void wireEvents() {
        testConnectionButton.addActionListener(e -> testConnection());
        addStrategyButton.addActionListener(e -> addStrategy());
        settingsButton.addActionListener(e -> openSettingsDialog());
    }

    public void promptForRequiredSettings() {
        if (!settingsDialog.hasRequiredSettings()) {
            openSettingsDialog();
        }
    }

    private void openSettingsDialog() {
        settingsDialog.setVisible(true);
        connectionOk = false;
    }


    private void testConnection() {
        BrokerType brokerType = settingsDialog.brokerType();
        if (brokerType == null) {
            log("Connection test: FAILED (broker not set in Settings)");
            return;
        }

        tradingApi = TradingApiFactory.create(brokerType);
        tradingApi.authenticate(settingsDialog.getApiKey(), settingsDialog.getApiSecret());
        connectionOk = tradingApi.testConnection();
        log("Connection test: " + (connectionOk ? "SUCCESS" : "FAILED"));

        if (connectionOk) {
            initPersistenceAndRestore();
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
            TradingStrategyService service = new TradingStrategyService(
                    tradingApi,
                    new RuleEvaluationService(),
                    analyticsPublisher,
                    this::log,
                    userId
            );
            service.configure(entry.config());
            ManagedStrategy managed = new ManagedStrategy(entry.config(), service);
            managed.paused = entry.paused();
            strategies.add(managed);
            if (!managed.paused) {
                startStrategy(managed, "STRATEGY_RESUMED");
                log("Restored and resumed strategy for " + entry.config().symbol());
            } else {
                log("Restored strategy for " + entry.config().symbol() + " (paused)");
            }
        }
        strategyTableModel.fireTableDataChanged();
        if (!strategies.isEmpty()) {
            strategyTable.setRowSelectionInterval(0, 0);
        }
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
                this::log,
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

    private void editStrategy(int row) {
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

    private void togglePauseResume(int row) {
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
        refreshPanels();
    }

    private void startStrategy(ManagedStrategy entry, String eventType) {
        stopPoller(entry);
        entry.paused = false;
        entry.poller = new PricePoller();
        entry.poller.start(entry.config.pollingSeconds(), () -> {
            entry.service.onPriceTick();
            SwingUtilities.invokeLater(this::refreshPanels);
        });
        log(("STRATEGY_RESUMED".equals(eventType) ? "Strategy resumed for symbol " : "Strategy started for symbol ") + entry.config.symbol());
        if (analyticsPublisher != null) {
            analyticsPublisher.publish(new AnalyticsEvent(eventType).put("symbol", entry.config.symbol()));
        }
    }

    private void stopPoller(ManagedStrategy entry) {
        if (entry.poller != null) {
            entry.poller.stop();
            entry.poller = null;
        }
    }

    private void refreshPanels() {
        int row = strategyTable.getSelectedRow();
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
                    telemetryConsent.isSelected() || settingsDialog.telemetryEnabled(),
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

    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            eventLog.append(message + System.lineSeparator());
            eventLog.setCaretPosition(eventLog.getDocument().getLength());
        });
    }

    private final class StrategyTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {
                "Symbol", "Status", "Polling (s)", "Mode", "Edit", "Pause/Resume"
        };

        @Override
        public int getRowCount() {
            return strategies.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            ManagedStrategy entry = strategies.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> entry.config.symbol();
                case 1 -> entry.paused ? "Paused" : "Running";
                case 2 -> entry.config.pollingSeconds();
                case 3 -> entry.config.paperTrading() ? "Paper" : "Live";
                case 4 -> "Edit";
                case 5 -> entry.paused ? "Resume" : "Pause";
                default -> "";
            };
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 4 || columnIndex == 5;
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
