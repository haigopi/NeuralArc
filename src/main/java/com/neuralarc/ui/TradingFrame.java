package com.neuralarc.ui;

import com.neuralarc.analytics.*;
import com.neuralarc.api.HttpAlpacaClient;
import com.neuralarc.api.AlpacaTradeUpdateEvent;
import com.neuralarc.api.TradingApi;
import com.neuralarc.model.*;
import com.neuralarc.api.HttpAlpacaMarketDataApi;
import com.neuralarc.db.AppDatabase;
import com.neuralarc.db.SqliteStrategyExecutionEventRepository;
import com.neuralarc.db.SqliteStrategyOrderRepository;
import com.neuralarc.db.SqliteStrategyRepository;
import com.neuralarc.service.AutoAnalyzeResultStore;
import com.neuralarc.service.FeedbackEmailService;
import com.neuralarc.service.MarketHoursService;
import com.neuralarc.service.OnboardingStateStore;
import com.neuralarc.service.PersistentAggregatePnlStore;
import com.neuralarc.service.AppSettingsService;
import com.neuralarc.service.StrategyPollingService;
import com.neuralarc.service.StrategyService;
import com.neuralarc.service.UserIdentityService;
import com.neuralarc.util.AppMetadata;
import com.neuralarc.util.BrokerOrderStatusUtil;
import com.neuralarc.util.FontLoader;
import com.neuralarc.util.Monetary;
import com.neuralarc.util.SvgIconLoader;
import org.json.JSONArray;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.RowSorter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
import javax.swing.plaf.basic.BasicProgressBarUI;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
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
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.math.BigDecimal;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class TradingFrame extends JFrame {
    private static final Font BASE_FONT = createBaseFont();
    private static final int OUTER_PADDING = 16;
    private static final DateTimeFormatter LOG_DATE_FORMAT = DateTimeFormatter.ofPattern("EEE, MMM");
    private static final DateTimeFormatter LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a");
    private static final DateTimeFormatter RULE_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("EEE, MMM d yyyy, h:mm a");
    private static final DateTimeFormatter NEXT_OPEN_FORMAT = DateTimeFormatter.ofPattern("EEE, MMM d yyyy h:mm a z");

    private final JLabel positionSummary = new JLabel("Position: -");
    private final JLabel ruleState = new JLabel("Rules: -");
    private final JLabel paperUnrealizedSummary = new JLabel("Paper Unrealized P&L Total: -");
    private final JLabel headerTotalsSeparator = new JLabel("|");
    private final JLabel liveUnrealizedSummary = new JLabel("Live Unrealized P&L Total: -");
    private final JLabel positionSectionTitle = new JLabel("Position");
    private final JLabel rulesSectionTitle = new JLabel("Rules Triggered");
    private final JLabel statusBar = new JLabel("Broker: Not connected");
    private final JLabel statusStrategyCount = new JLabel("Strategies: Active 0 | Inactive 0");
    private final JLabel pollingSummary = new JLabel("Poll: -");
    private final JLabel marketStatus = new JLabel("Market: Unknown");
    private final JLabel streamStatus = new JLabel("Trade Stream: idle");
    private final JLabel marketValueStatus = new JLabel("Market Value: -");
    private final JLabel cpuUsageStatus = new JLabel("CPU: -");
    private final JLabel memoryUsageStatus = new JLabel("Memory: -");
    private final JLabel headerStatus = new JLabel("Status: waiting for settings");
    private static final Color STATUS_OK = new Color(34, 139, 34);
    private static final Color STATUS_WARN = new Color(180, 100, 0);
    private static final Color STATUS_ERR = new Color(180, 30, 30);
    private static final Color TABLE_SELECTION_BG     = new Color(255, 242, 80);   // yellow row highlight
    private static final Color TABLE_SELECTION_FG     = new Color(25,  20,  5);    // near-black text on yellow
    private static final Color TABLE_SELECTION_BAR_BG = new Color(230, 208, 30);   // progress-bar unfilled on yellow row
    private static final Color STATUS_TEXT_RUNNING = new Color(46, 125, 50);
    private static final Color STATUS_TEXT_PAUSED = new Color(180, 100, 0);
    private static final Color MODE_TEXT_ALPACA_PAPER = new Color(25, 118, 210);
    private static final Color MODE_TEXT_ALPACA_LIVE = new Color(183, 28, 28);
    private static final Color BOTTOM_STATUS_ACCENT = new Color(180, 160, 110);
    private static final Color HISTORY_BUY_BG = new Color(227, 242, 253);
    private static final Color HISTORY_BUY_FG = new Color(13, 71, 161);
    private static final Color HISTORY_SELL_GAIN_BG = new Color(232, 245, 233);
    private static final Color HISTORY_SELL_GAIN_FG = new Color(27, 94, 32);
    private static final Color HISTORY_SELL_LOSS_BG = new Color(255, 235, 238);
    private static final Color HISTORY_SELL_LOSS_FG = new Color(183, 28, 28);
    private static final Color HISTORY_SELL_FLAT_BG = new Color(255, 248, 225);
    private static final Color HISTORY_SELL_FLAT_FG = new Color(111, 79, 0);
    private static final Color HISTORY_FAILED_BG = new Color(255, 243, 224);
    private static final Color HISTORY_FAILED_FG = new Color(140, 80, 0);
    private static final Color HISTORY_COMPLETED_BG = new Color(245, 245, 245);
    private static final Color HISTORY_COMPLETED_FG = new Color(78, 84, 94);
    private static final Color HISTORY_SUBTOTAL_BG  = new Color(215, 225, 240);
    private static final Color HISTORY_SUBTOTAL_FG  = new Color(28, 48, 80);
    private static final Color HISTORY_GROUP_BORDER = new Color(173, 181, 189);
    private static final Color LOG_LINE_EVEN = new Color(63, 72, 82);
    private static final Color LOG_LINE_ODD = new Color(110, 118, 128);
    private static final int MAX_EVENT_LOG_LINES = 1500;
    private static final long CLOSED_MARKET_POLL_INTERVAL_MILLIS = 10L * 60L * 1000L;
    private static final Color HEADER_STATUS_DEFAULT = new Color(220, 220, 255);
    private static final Color HEADER_STATUS_LIVE_ALERT = new Color(255, 82, 82);
    private static final Color HEADER_STATUS_LIVE_ALERT_DIM = new Color(255, 205, 210);
    private static final Color HEADER_STATUS_LIVE_ACTIVE = new Color(46, 125, 50);
    private static final Color HEADER_STATUS_LIVE_ACTIVE_DIM = Color.WHITE;
    private final JTextPane eventLog = new JTextPane();
    private final JButton addStrategyButton = new JButton("Add New Stock Strategy");
    private final JButton portfolioActionsButton = new JButton("Portfolio Actions");
    private final JButton settingsButton = new JButton("Settings");
    private final JButton legalDisclosureButton = new JButton("Legal Disclosure");
    private final Timer liveModeBlinkTimer;
    private final Timer logFlushTimer;
    private final Timer pollingIndicatorTimer;
    private final Timer strategyPollingTimer;
    private final Timer connectionRetryTimer;
    private final ExecutorService uiPollingExecutor;
    private final AppSettingsService appSettingsService = new AppSettingsService();
    private final MarketHoursService marketHoursService = new MarketHoursService();
    private final Path appLogFile = AppMetadata.appDataDirectory().resolve("app.log");
    private final LegalDisclosureController legalDisclosureController = new LegalDisclosureController();
    private boolean legalDisclosureAccepted;
    private final StringBuilder pendingLogWrites = new StringBuilder();

    private final UserIdentityService identityService = new UserIdentityService();
    private final HistoryTablePresenter historyTablePresenter = new HistoryTablePresenter();
    private final HistoryRowStyler historyRowStyler = new HistoryRowStyler();
    private final MarketStatusPresenter marketStatusPresenter = new MarketStatusPresenter();
    private final PollingCellPresenter pollingCellPresenter = new PollingCellPresenter();
    private final StatusBarPresenter statusBarPresenter = new StatusBarPresenter();
    private final StrategyActionsPresenter strategyActionsPresenter = new StrategyActionsPresenter();
    private final StrategyTablePresenter strategyTablePresenter = new StrategyTablePresenter();
    private final SystemMetricsPresenter systemMetricsPresenter = new SystemMetricsPresenter();
    private final List<ManagedStrategy> strategies = new ArrayList<>();
    private final List<HistoryTablePresenter.HistoryRow> filledOrderRows = new ArrayList<>();
    private final StrategyGridTableModel strategyTableModel = new StrategyGridTableModel(
            strategies,
            this::displayStatusLabel,
            this::gridBrokerModeLabel,
            strategyTablePresenter
    );
    private final HistoryGridTableModel filledOrdersTableModel = new HistoryGridTableModel(filledOrderRows);
    private final JTable strategyTable = new JTable(strategyTableModel) {
        @Override
        public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
            Component c = super.prepareRenderer(renderer, row, column);
            // Force the custom selection colour even on macOS Aqua LAF, which otherwise
            // paints its own system-accent stripe and ignores the renderer's background.
            if (isCellSelected(row, column)) {
                c.setBackground(TABLE_SELECTION_BG);
                c.setForeground(TABLE_SELECTION_FG);
            }
            return c;
        }
    };
    private final JTable filledOrdersTable = new JTable(filledOrdersTableModel);
    private final JTabbedPane strategyTabs = new JTabbedPane();

    private TradingApi tradingApi;
    private AnalyticsPublisher analyticsPublisher;
    private final SettingsDialog settingsDialog;
    private final SqliteStrategyRepository strategyRepository;
    private final SqliteStrategyOrderRepository strategyOrderRepository;
    private final SqliteStrategyExecutionEventRepository strategyEventRepository;
    private final PersistentAggregatePnlStore aggregatePnlStore;
    private boolean connectionOk;
    private boolean connectionRetryPending;
    private boolean appLaunchedPublished;
    private String selectedStrategyId;
    private BrokerType currentBrokerType = BrokerType.ALPACA;
    private boolean preservingSelection;
    private final AtomicBoolean pollingCycleInFlight = new AtomicBoolean(false);
    private Color liveBlinkPrimary = HEADER_STATUS_DEFAULT;
    private Color liveBlinkSecondary = HEADER_STATUS_DEFAULT;
    private boolean liveBlinkPrimaryActive;
    private int logLineCount;
    private boolean promptedDefaultStrategyDialog;
    private StrategyService strategyService;
    private StrategyPollingService strategyPollingService;
    private long lastBrokerBackedUiRefreshAtMillis;
    private String runtimeApiKey = "";
    private String runtimeApiSecret = "";
    private volatile HttpAlpacaClient paperModeClient;
    private volatile HttpAlpacaClient liveModeClient;
    private volatile long lastBatchGridPriceRefreshAtMillis;
    private volatile boolean batchGridPriceRefreshRequestedFromStream;
    private volatile long lastLoggedSnapshotIntervalMillis = -1L;
    private volatile long lastClosedMarketPollingCycleAtMillis;
    private final AutoAnalyzeResultStore autoAnalyzeResultStore = new AutoAnalyzeResultStore();
    private final OnboardingStateStore onboardingStateStore = new OnboardingStateStore();
    private final TradingRuntimeSupport tradingRuntimeSupport;
    private final StrategyActionsController strategyActionsController;
    private final TradeStreamLifecycleCoordinator tradeStreamLifecycleCoordinator;
    private final ConnectionLifecycleCoordinator connectionLifecycleCoordinator;

    public TradingFrame() {
        liveModeBlinkTimer = new Timer(500, ignored -> toggleLiveHeaderBlink());
        liveModeBlinkTimer.setInitialDelay(0);
        logFlushTimer = new Timer(10000, ignored -> flushLogsToFile());
        logFlushTimer.setInitialDelay(10000);
        logFlushTimer.start();
        pollingIndicatorTimer = new Timer(250, e -> {
            strategyTable.repaint();
            filledOrdersTable.repaint();
        });
        pollingIndicatorTimer.setInitialDelay(250);
        pollingIndicatorTimer.start();
        connectionRetryTimer = new Timer(10000, ignored -> retryBrokerConnectionIfConfigured());
        connectionRetryTimer.setInitialDelay(10000);
        connectionRetryTimer.setRepeats(false);
        uiPollingExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "neuralarc-ui-polling");
            thread.setDaemon(true);
            return thread;
        });
        setTitle("NeuralArc Trader Application");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        ((JComponent) getContentPane()).setBorder(new EmptyBorder(OUTER_PADDING, OUTER_PADDING, OUTER_PADDING, OUTER_PADDING));
        settingsDialog = new SettingsDialog(this);
        AppDatabase appDatabase = AppDatabase.getInstance();
        strategyRepository = new SqliteStrategyRepository(appDatabase);
        strategyOrderRepository = new SqliteStrategyOrderRepository(appDatabase);
        strategyEventRepository = new SqliteStrategyExecutionEventRepository(appDatabase);
        tradingRuntimeSupport = new TradingRuntimeSupport(
                strategyRepository,
                strategyOrderRepository,
                strategyEventRepository,
                appSettingsService,
                marketHoursService
        );
        strategyActionsController = new StrategyActionsController(new StrategyActionsController.Gateway() {
            @Override
            public int toModelRow(int viewRow) {
                return strategyTable.convertRowIndexToModel(viewRow);
            }

            @Override
            public int strategiesSize() {
                return strategies.size();
            }

            @Override
            public StrategyActionsController.ActionEntry entryAt(int modelRow) {
                ManagedStrategy entry = strategies.get(modelRow);
                return new StrategyActionsController.ActionEntry() {
                    @Override public Strategy strategy() { return entry.strategy; }
                    @Override public boolean isPaused() { return entry.isPaused(); }
                    @Override public boolean isPauseResumeBusy() { return entry.isPauseResumeBusy(); }
                    @Override public void setPauseResumeBusy(boolean value) { entry.setPauseResumeBusy(value); }
                    @Override public void setPauseResumeBusyText(String value) { entry.setPauseResumeBusyText(value); }
                    @Override public void syncFrom(Strategy strategy) { entry.syncFrom(strategy); }
                };
            }

            @Override public StrategyService strategyService() { return strategyService; }
            @Override public Optional<Strategy> findStrategyById(String strategyId) { return strategyRepository.findById(strategyId); }
            @Override public void refreshStrategyTableRow(int modelRow) { TradingFrame.this.refreshStrategyTableRow(modelRow); }
            @Override public void refreshStrategyTableData() { TradingFrame.this.refreshStrategyTableData(); }
            @Override public void refreshPanels() { TradingFrame.this.refreshPanels(); }
            @Override public void updateStatusBar() { TradingFrame.this.updateStatusBar(); }
            @Override public void restoreSelectedRow() { TradingFrame.this.restoreSelectedRow(); }
            @Override public void updateSelectedStrategy() { TradingFrame.this.updateSelectedStrategy(); }
            @Override public void syncStrategiesFromRepository() { TradingFrame.this.syncStrategiesFromRepository(); }
            @Override public void clearStrategySelection() { strategyTable.clearSelection(); }

            @Override
            public void startPollingCountdown(String strategyId) {
                ManagedStrategy entry = TradingFrame.this.findStrategyById(strategyId);
                if (entry != null) {
                    TradingFrame.this.startPollingCountdown(entry);
                }
            }

            @Override
            public void stopPollingCountdown(String strategyId) {
                ManagedStrategy entry = TradingFrame.this.findStrategyById(strategyId);
                if (entry != null) {
                    TradingFrame.this.stopPollingCountdown(entry);
                }
            }

            @Override
            public void resetPollingCountdown(String strategyId) {
                ManagedStrategy entry = TradingFrame.this.findStrategyById(strategyId);
                if (entry != null) {
                    TradingFrame.this.resetPollingCountdown(entry);
                }
            }

            @Override public Position loadPositionForStrategy(Strategy strategy) { return TradingFrame.this.loadPositionForStrategy(strategy); }
            @Override public boolean hasOpenPosition(Strategy strategy) { return TradingFrame.this.loadPositionForStrategy(strategy).getTotalShares() > 0; }
            @Override public StrategyService.StrategyCreationResult sellPosition(Strategy strategy) { return strategyService.closePosition(strategy.id()); }
            @Override public BigDecimal realizedPnlForStrategy(String strategyId) { return TradingFrame.this.realizedPnlForStrategy(strategyId); }
            @Override public String closePaperAccountState(Strategy strategy) { return TradingFrame.this.closePaperAccountState(strategy); }
            @Override public void updateHeaderModeStatus(BrokerType brokerType) { TradingFrame.this.updateHeaderModeStatus(brokerType); }
            @Override public BrokerType currentBrokerType() { return currentBrokerType; }
            @Override public boolean hasBrokerPositionAccess() { return currentBrokerType == BrokerType.ALPACA || tradingApi != null; }
            @Override public void setSelectedStrategyId(String strategyId) { selectedStrategyId = strategyId; }
            @Override public String selectedStrategyId() { return selectedStrategyId; }
            @Override public void removeStrategyAt(int modelRow) { strategies.remove(modelRow); }
            @Override public void addArchivedRealized(StrategyMode mode, BigDecimal amount) { aggregatePnlStore.addArchivedRealized(mode, amount); }
            @Override public void log(String message) { TradingFrame.this.log(message); }

            @Override
            public void publishAnalytics(AnalyticsEvent event) {
                if (analyticsPublisher != null && event != null) {
                    analyticsPublisher.publish(event);
                }
            }

            @Override
            public int confirm(String message, String title, int optionType, int messageType) {
                return JOptionPane.showConfirmDialog(TradingFrame.this, message, title, optionType, messageType);
            }

            @Override
            public void showMessage(String message, String title, int messageType) {
                JOptionPane.showMessageDialog(TradingFrame.this, message, title, messageType);
            }

            @Override
            public StrategyActionsController.PromotionDialogResult showLivePromotionDialog(
                    StrategyService.LivePromotionPreview preview,
                    String realizedPnl,
                    String unrealizedPnl
            ) {
                LivePromotionDialog dialog = new LivePromotionDialog(TradingFrame.this, preview, realizedPnl, unrealizedPnl);
                boolean proceed = dialog.showDialog();
                return new StrategyActionsController.PromotionDialogResult(proceed, dialog.shouldClosePaperPositions());
            }

            @Override
            public void runBackgroundTask(
                    StrategyActionsController.ThrowingRunnable background,
                    Runnable onSuccess,
                    java.util.function.Consumer<Exception> onFailure,
                    Runnable onFinally
            ) {
                SwingWorker<Void, Void> worker = new SwingWorker<>() {
                    @Override
                    protected Void doInBackground() throws Exception {
                        background.run();
                        return null;
                    }

                    @Override
                    protected void done() {
                        try {
                            get();
                            onSuccess.run();
                        } catch (Exception ex) {
                            onFailure.accept(ex);
                        } finally {
                            onFinally.run();
                        }
                    }
                };
                worker.execute();
            }
        });
        tradeStreamLifecycleCoordinator = new TradeStreamLifecycleCoordinator(new TradeStreamLifecycleCoordinator.Gateway() {
            @Override
            public boolean webSocketEnabled() {
                return AppMetadata.alpacaTradingEventsWebSocketEnabled();
            }

            @Override
            public String streamUrl(boolean liveMode) {
                return AppMetadata.alpacaTradingEventsWebSocketUrl(liveMode);
            }

            @Override
            public void updateStreamStatus(String status, Color color) {
                TradingFrame.this.updateStreamStatus(status, color);
            }

            @Override
            public void log(String message) {
                TradingFrame.this.log(message);
            }

            @Override
            public boolean canProcessTradeUpdates() {
                return strategyPollingService != null;
            }

            @Override
            public void onTradeUpdate(AlpacaTradeUpdateEvent event) {
                if (strategyPollingService != null) {
                    strategyPollingService.onTradeUpdate(event);
                }
            }

            @Override
            public void refreshDisplayedPositionFromStream(String symbol) {
                TradingFrame.this.refreshDisplayedPositionFromStream(symbol);
            }

            @Override
            public void invokeLater(Runnable runnable) {
                SwingUtilities.invokeLater(runnable);
            }

            @Override
            public void syncStrategiesFromRepository() {
                TradingFrame.this.syncStrategiesFromRepository();
            }

            @Override
            public void refreshStrategyTableContent() {
                TradingFrame.this.refreshStrategyTableContent();
            }

            @Override
            public void refreshPanels() {
                TradingFrame.this.refreshPanels();
            }

            @Override
            public void updateStatusBar() {
                TradingFrame.this.updateStatusBar();
            }
        });
        connectionLifecycleCoordinator = new ConnectionLifecycleCoordinator(new ConnectionLifecycleCoordinator.Gateway() {
            @Override
            public TradingRuntimeSupport.ConnectionAttemptResult attemptConnection(
                    BrokerType brokerType,
                    ApplicationMode mode,
                    String apiKey,
                    String apiSecret
            ) {
                return tradingRuntimeSupport.attemptConnection(brokerType, mode, apiKey, apiSecret);
            }

            @Override
            public void log(String message) {
                TradingFrame.this.log(message);
            }

            @Override
            public void updateHeaderModeStatus(BrokerType brokerType) {
                TradingFrame.this.updateHeaderModeStatus(brokerType);
            }

            @Override
            public void setHeaderStatusText(String text) {
                headerStatus.setText(text);
            }

            @Override
            public void markConnectionStatus(boolean connected, String message) {
                settingsDialog.markConnectionStatus(connected, message);
            }

            @Override
            public void applySuccessfulRuntimeConnection(
                    BrokerType brokerType,
                    TradingApi candidateApi,
                    String apiKey,
                    String apiSecret,
                    ApplicationMode mode
            ) {
                tradingApi = candidateApi;
                currentBrokerType = brokerType;
                connectionOk = true;
                refreshStrategyRuntimeServices(apiKey, apiSecret, mode);
                startTradingEventStreamIfConfigured(apiKey, apiSecret);
                setStatus("Connected - broker " + brokerType.name() + " ready.", STATUS_OK);
                updateHeaderModeStatus(brokerType);
                updateStatusBar();
                initPersistenceAndRestore();
            }

            @Override
            public void applyFailedRuntimeConnection(BrokerType brokerType) {
                stopTradingEventStream();
                connectionOk = false;
                connectionRetryPending = true;
                setStatus("FAILED Retrying...", STATUS_ERR);
                scheduleConnectionRetry();
                updateHeaderModeStatus(brokerType);
                updateStatusBar();
            }

            @Override
            public void stopConnectionRetryTimer() {
                connectionRetryTimer.stop();
            }

            @Override
            public boolean isConnectionRetryTimerRunning() {
                return connectionRetryTimer.isRunning();
            }

            @Override
            public void restartConnectionRetryTimer() {
                connectionRetryTimer.restart();
            }

            @Override
            public void setConnectionRetryPending(boolean pending) {
                connectionRetryPending = pending;
            }

            @Override
            public boolean isConnectionRetryPending() {
                return connectionRetryPending;
            }

            @Override
            public BrokerType appliedBrokerType() {
                return settingsDialog.appliedBrokerType();
            }

            @Override
            public ApplicationMode appliedApplicationMode() {
                return settingsDialog.appliedApplicationMode();
            }

            @Override
            public String savedApiKey(ApplicationMode mode) {
                return settingsDialog.savedApiKey(mode);
            }

            @Override
            public String savedApiSecret(ApplicationMode mode) {
                return settingsDialog.savedApiSecret(mode);
            }


            @Override
            public Color statusErrorColor() {
                return STATUS_ERR;
            }

            @Override
            public void setStatus(String message, Color tone) {
                TradingFrame.this.setStatus(message, tone);
            }
        });
        aggregatePnlStore = new PersistentAggregatePnlStore(
                AppMetadata.appDataDirectory().resolve("aggregate-pnl.json")
        );
        legalDisclosureAccepted = legalDisclosureController.loadAccepted();
        refreshStrategyRuntimeServices(
                settingsDialog.savedApiKey(settingsDialog.appliedApplicationMode()),
                settingsDialog.savedApiSecret(settingsDialog.appliedApplicationMode()),
                settingsDialog.appliedApplicationMode()
        );
        settingsDialog.setStrategyExportHandler(this::exportStrategiesToFile);
        settingsDialog.setStrategyImportHandler(this::importStrategiesFromFile);
        strategyPollingTimer = new Timer(1000, e -> {
            triggerPollingCycle();
        });
        strategyPollingTimer.setInitialDelay(1000);
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
        rightControls.add(portfolioActionsButton, rightControlsGbc);
        rightControlsGbc.gridx = 2;
        rightControls.add(settingsButton, rightControlsGbc);

        JButton killSwitchButton = new JButton("KILL SWITCH");
        applyButtonIcon(killSwitchButton, "icons/kill-switch.svg", 15);
        killSwitchButton.setFocusPainted(false);
        killSwitchButton.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        killSwitchButton.setFont(FontLoader.ui(Font.BOLD, 12f));
        killSwitchButton.setForeground(Color.WHITE);
        killSwitchButton.setBackground(new Color(180, 20, 20));
        killSwitchButton.setOpaque(true);
        killSwitchButton.setContentAreaFilled(true);
        killSwitchButton.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(120, 10, 10), 1, true),
                new EmptyBorder(8, 14, 8, 14)
        ));
        killSwitchButton.setMargin(new java.awt.Insets(8, 14, 8, 14));
        killSwitchButton.addMouseListener(new MouseAdapter() {
            private static final Color BASE_BG     = new Color(180, 20, 20);
            private static final Color BASE_BORDER  = new Color(120, 10, 10);
            private static final Color HOVER_BG    = new Color(210, 32, 32);
            private static final Color HOVER_BORDER = new Color(148, 15, 15);
            private static final Color PRESS_BG    = new Color(148, 14, 14);
            private static final Color PRESS_BORDER = new Color(95,  6,  6);
            @Override public void mouseEntered(MouseEvent e) {
                if (killSwitchButton.isEnabled()) {
                    killSwitchButton.setBackground(HOVER_BG);
                    killSwitchButton.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(HOVER_BORDER, 1, true),
                            new EmptyBorder(8, 14, 8, 14)));
                }
            }
            @Override public void mouseExited(MouseEvent e) {
                killSwitchButton.setBackground(BASE_BG);
                killSwitchButton.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(BASE_BORDER, 1, true),
                        new EmptyBorder(8, 14, 8, 14)));
            }
            @Override public void mousePressed(MouseEvent e) {
                if (killSwitchButton.isEnabled() && e.getButton() == MouseEvent.BUTTON1) {
                    killSwitchButton.setBackground(PRESS_BG);
                    killSwitchButton.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(PRESS_BORDER, 2, true),
                            new EmptyBorder(7, 13, 7, 13)));
                }
            }
            @Override public void mouseReleased(MouseEvent e) {
                if (killSwitchButton.contains(e.getPoint()) && killSwitchButton.isEnabled()) {
                    killSwitchButton.setBackground(HOVER_BG);
                    killSwitchButton.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(HOVER_BORDER, 1, true),
                            new EmptyBorder(8, 14, 8, 14)));
                } else {
                    killSwitchButton.setBackground(BASE_BG);
                    killSwitchButton.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(BASE_BORDER, 1, true),
                            new EmptyBorder(8, 14, 8, 14)));
                }
            }
        });
        killSwitchButton.addActionListener(e -> killAllStrategies());
        rightControlsGbc.gridx = 3;
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
        strategyTable.setSelectionForeground(TABLE_SELECTION_FG);
        strategyTable.setRowMargin(6);
        strategyTable.setShowGrid(false);
        strategyTable.setIntercellSpacing(new Dimension(0, 6));
        StatusRowRenderer statusRowRenderer = new StatusRowRenderer();
        strategyTable.setDefaultRenderer(Object.class, statusRowRenderer);
        strategyTable.setDefaultRenderer(Number.class, statusRowRenderer);
        strategyTable.getColumnModel().getColumn(7).setCellRenderer(new PollingBarRenderer());
        strategyTable.getColumnModel().getColumn(9).setCellRenderer(new ActionsRenderer());
        strategyTable.getColumnModel().getColumn(7).setPreferredWidth(240);
        strategyTable.getColumnModel().getColumn(7).setMinWidth(220);
        strategyTable.getColumnModel().getColumn(9).setPreferredWidth(500);
        strategyTable.getColumnModel().getColumn(9).setMinWidth(480);

        // Handle clicks in the Actions column via a mouse listener instead of a cell editor.
        // Using mousePressed (not mouseClicked) gives instant response — mouseClicked only fires
        // when press and release land on the exact same pixel, which feels laggy.
        strategyTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                int viewRow = strategyTable.rowAtPoint(e.getPoint());
                int viewCol = strategyTable.columnAtPoint(e.getPoint());

                // Select the clicked row first so the full row highlights yellow immediately.
                if (viewRow >= 0 && viewRow < strategyTable.getRowCount()
                        && strategyTable.getSelectedRow() != viewRow) {
                    strategyTable.setRowSelectionInterval(viewRow, viewRow);
                }

                // Dispatch the action buttons (column 9 only) via five equal zones.
                // Use invokeLater so the action runs AFTER ALL mousePressed handlers
                // (ours + BasicTableUI) have finished — this is critical because:
                //   • BasicTableUI fires its own mousePressed AFTER ours (LIFO order).
                //   • Without deferral, dialogs opened here block BasicTableUI from
                //     ever running, leaving the table in a broken state on first click.
                if (e.getButton() != java.awt.event.MouseEvent.BUTTON1) return;
                if (viewRow < 0 || viewRow >= strategies.size() || viewCol != 9) return;
                java.awt.Rectangle cellRect = strategyTable.getCellRect(viewRow, viewCol, false);
                int xInCell  = e.getX() - cellRect.x;
                int section  = Math.max(1, cellRect.width / 5);
                final int capturedRow     = viewRow;
                final int capturedX       = xInCell;
                final int capturedSection = section;
                SwingUtilities.invokeLater(() -> {
                    if (capturedX < capturedSection) {
                        editStrategy(capturedRow);
                    } else if (capturedX < capturedSection * 2) {
                        togglePauseResume(capturedRow);
                    } else if (capturedX < capturedSection * 3) {
                        sellStrategy(capturedRow);
                    } else if (capturedX < capturedSection * 4) {
                        previewLivePromotion(capturedRow);
                    } else {
                        deleteStrategy(capturedRow);
                    }
                });
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                strategyTable.setCursor(java.awt.Cursor.getDefaultCursor());
            }
        });
        strategyTable.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e) {
                // Only show HAND cursor when hovering over the action-buttons column of
                // an actual data row — NOT over the empty viewport space below the rows.
                int viewRow = strategyTable.rowAtPoint(e.getPoint());
                int viewCol = strategyTable.columnAtPoint(e.getPoint());
                if (viewRow >= 0 && viewCol == 9) {
                    strategyTable.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
                } else {
                    strategyTable.setCursor(java.awt.Cursor.getDefaultCursor());
                }
            }
        });

        // Make table sortable — click column headers to sort
        TableRowSorter<StrategyGridTableModel> sorter = new TableRowSorter<>(strategyTableModel);
        sorter.setSortable(7, false); // Polling countdown bar column — not sortable
        sorter.setSortable(9, false); // Actions button column — not sortable
        sorter.setRowFilter(new RowFilter<>() {
            @Override
            public boolean include(Entry<? extends StrategyGridTableModel, ? extends Integer> entry) {
                int modelRow = entry.getIdentifier();
                if (modelRow < 0 || modelRow >= strategies.size()) {
                    return false;
                }
                return includeInCurrentStrategiesTab(strategies.get(modelRow));
            }
        });
        strategyTable.setRowSorter(sorter);

        JScrollPane strategyGrid = new JScrollPane(strategyTable);
        strategyGrid.setOpaque(false);
        strategyGrid.setBackground(new Color(0, 0, 0, 0));
        strategyGrid.getViewport().setOpaque(false);
        strategyGrid.getViewport().setBackground(new Color(0, 0, 0, 0));
        javax.swing.border.TitledBorder strategyGridTitle = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(208, 214, 222), 1, true),
                "Stock Strategies"
        );
        strategyGridTitle.setTitleFont(FontLoader.ui(Font.BOLD, 10f));
        strategyGridTitle.setTitleColor(new Color(78, 84, 94));
        strategyGrid.setBorder(BorderFactory.createCompoundBorder(
                new EmptyBorder(10, 0, 0, 0),
                strategyGridTitle
        ));

        filledOrdersTable.setRowHeight(30);
        filledOrdersTable.setFillsViewportHeight(true);
        filledOrdersTable.setRowSelectionAllowed(true);
        filledOrdersTable.setColumnSelectionAllowed(false);
        filledOrdersTable.setCellSelectionEnabled(false);
        filledOrdersTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        filledOrdersTable.setSelectionBackground(TABLE_SELECTION_BG);
        filledOrdersTable.setSelectionForeground(TABLE_SELECTION_FG);
        filledOrdersTable.setRowMargin(0);
        filledOrdersTable.setShowGrid(false);
        filledOrdersTable.setIntercellSpacing(new Dimension(0, 0));
        filledOrdersTable.setDefaultRenderer(Object.class, new HistoryRowRenderer());
        filledOrdersTable.setDefaultRenderer(Number.class, new HistoryRowRenderer());
        TableRowSorter<HistoryGridTableModel> filledSorter = new TableRowSorter<>(filledOrdersTableModel);
        filledOrdersTable.setRowSorter(filledSorter);

        JScrollPane filledOrdersGrid = new JScrollPane(filledOrdersTable);
        filledOrdersGrid.setOpaque(false);
        filledOrdersGrid.setBackground(new Color(0, 0, 0, 0));
        filledOrdersGrid.getViewport().setOpaque(false);
        filledOrdersGrid.getViewport().setBackground(new Color(0, 0, 0, 0));
        javax.swing.border.TitledBorder filledOrdersTitle = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(208, 214, 222), 1, true),
                "Trade History"
        );
        filledOrdersTitle.setTitleFont(FontLoader.ui(Font.BOLD, 10f));
        filledOrdersTitle.setTitleColor(new Color(78, 84, 94));
        filledOrdersGrid.setBorder(BorderFactory.createCompoundBorder(
                new EmptyBorder(10, 0, 0, 0),
                filledOrdersTitle
        ));

        strategyTabs.setBorder(new EmptyBorder(0, 0, 0, 0));
        strategyTabs.addTab("Current Strategies", strategyGrid);
        strategyTabs.addTab("Trade History", filledOrdersGrid);


        // ── Status bar ─────────────────────────────────────────────────────────
        // ── Status bar ─────────────────────────────────────────────────────────
        statusBar.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
        statusBar.setForeground(BOTTOM_STATUS_ACCENT);
        statusBar.setVerticalAlignment(SwingConstants.CENTER);
        statusBar.setBorder(new EmptyBorder(0, 6, 0, 16));

        statusStrategyCount.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
        statusStrategyCount.setForeground(new Color(150, 150, 160));
        statusStrategyCount.setVerticalAlignment(SwingConstants.CENTER);
        statusStrategyCount.setBorder(new EmptyBorder(0, 0, 0, 12));
        marketStatus.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
        marketStatus.setForeground(BOTTOM_STATUS_ACCENT);
        marketStatus.setVerticalAlignment(SwingConstants.CENTER);
        marketStatus.setBorder(new EmptyBorder(0, 0, 0, 12));
        streamStatus.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
        streamStatus.setForeground(BOTTOM_STATUS_ACCENT);
        streamStatus.setVerticalAlignment(SwingConstants.CENTER);
        streamStatus.setBorder(new EmptyBorder(0, 12, 0, 0));
        marketValueStatus.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
        marketValueStatus.setForeground(BOTTOM_STATUS_ACCENT);
        marketValueStatus.setVerticalAlignment(SwingConstants.CENTER);
        marketValueStatus.setBorder(new EmptyBorder(0, 12, 0, 0));
        cpuUsageStatus.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
        cpuUsageStatus.setForeground(BOTTOM_STATUS_ACCENT);
        cpuUsageStatus.setVerticalAlignment(SwingConstants.CENTER);
        cpuUsageStatus.setBorder(new EmptyBorder(0, 12, 0, 0));
        memoryUsageStatus.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
        memoryUsageStatus.setForeground(BOTTOM_STATUS_ACCENT);
        memoryUsageStatus.setVerticalAlignment(SwingConstants.CENTER);
        memoryUsageStatus.setBorder(new EmptyBorder(0, 12, 0, 0));

        JButton faqsButton = new JButton("Faqs");
        applyButtonIcon(faqsButton, "icons/faqs.svg", 15);
        styleStatusActionButton(faqsButton);
        faqsButton.addActionListener(e -> new HelpDialog(this).setVisible(true));
        JButton updatesButton = new JButton("Check Updates");
        applyButtonIcon(updatesButton, "icons/check-for-updates.svg", 15);
        styleStatusActionButton(updatesButton);
        updatesButton.addActionListener(e -> UpdateCheckSupport.checkForUpdates(this, updatesButton));
        applyButtonIcon(legalDisclosureButton, "icons/legal-disclosure.svg", 15);
        styleStatusActionButton(legalDisclosureButton);
        legalDisclosureButton.addActionListener(e -> showLegalDisclosureDialog(false));

        JButton submitFeatureButton = new JButton("Request New Feature");
        applyButtonIcon(submitFeatureButton, "icons/request-new-feature.svg", 15);
        styleStatusActionButton(submitFeatureButton);
        submitFeatureButton.addActionListener(e -> openRequestNewFeatureDialog());

        JButton contactUsButton = new JButton("Contact Us / Feedback");
        applyButtonIcon(contactUsButton, "icons/contact-us.svg", 15);
        styleStatusActionButton(contactUsButton);
        contactUsButton.addActionListener(e -> openContactUsDialog());

        JButton moreButton = new JButton("Actions");
        applyButtonIcon(moreButton, "icons/actions.svg", 15);
        styleStatusActionButton(moreButton);

        JPopupMenu moreMenu = new JPopupMenu();
        moreMenu.add(createStatusMenuItem("Submit Bug", "icons/submit-bug.svg",
                this::openSubmitBugDialog));
        moreMenu.add(createStatusMenuItem("Request New Feature", "icons/request-new-feature.svg",
                () -> openRequestNewFeatureDialog()));
        moreMenu.add(createStatusMenuItem("Contact Us / Feedback", "icons/contact-us.svg",
                () -> openContactUsDialog()));
        moreMenu.add(createStatusMenuItem("Check for Updates", "icons/check-for-updates.svg",
                () -> UpdateCheckSupport.checkForUpdates(this, moreButton)));
        moreMenu.add(createStatusMenuItem("Legal Disclosure", "icons/legal-disclosure.svg",
                () -> showLegalDisclosureDialog(false)));
        moreButton.addActionListener(e -> moreMenu.show(moreButton, 0, moreButton.getHeight()));

        JLabel appLabel = new JLabel(AppMetadata.name() + "  " + AppMetadata.displayVersion() + " | Patent Pending™");
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
        leftGbc.insets = new java.awt.Insets(0, 0, 0, 8);
        statusLeft.add(statusStrategyCount, leftGbc);
        leftGbc.gridx = 2;
        leftGbc.insets = new java.awt.Insets(0, 0, 0, 8);
        statusLeft.add(pollingSummary, leftGbc);
        leftGbc.gridx = 3;
        leftGbc.insets = new java.awt.Insets(0, 0, 0, 8);
        statusLeft.add(marketStatus, leftGbc);
        leftGbc.gridx = 4;
        leftGbc.insets = new java.awt.Insets(0, 0, 0, 8);
        statusLeft.add(streamStatus, leftGbc);
        leftGbc.gridx = 5;
        leftGbc.insets = new java.awt.Insets(0, 0, 0, 8);
        statusLeft.add(marketValueStatus, leftGbc);
        leftGbc.gridx = 6;
        leftGbc.insets = new java.awt.Insets(0, 0, 0, 8);
        statusLeft.add(cpuUsageStatus, leftGbc);
        leftGbc.gridx = 7;
        leftGbc.insets = new java.awt.Insets(0, 0, 0, 8);
        statusLeft.add(memoryUsageStatus, leftGbc);

        JPanel statusRight = new JPanel(new GridBagLayout());
        statusRight.setOpaque(false);
        GridBagConstraints rightGbc = new GridBagConstraints();
        rightGbc.gridy = 0;
        rightGbc.anchor = GridBagConstraints.CENTER;
        rightGbc.insets = new java.awt.Insets(0, 0, 0, 10);
        rightGbc.gridx = 0;
        statusRight.add(appLabel, rightGbc);
        rightGbc.gridx = 1;
        statusRight.add(moreButton, rightGbc);
        rightGbc.gridx = 2;
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

        JScrollPane eventLogScrollPane = new JScrollPane(eventLog);
        eventLogScrollPane.setOpaque(false);
        eventLogScrollPane.setBackground(new Color(0, 0, 0, 0));
        eventLogScrollPane.getViewport().setOpaque(false);
        eventLogScrollPane.getViewport().setBackground(new Color(0, 0, 0, 0));
        javax.swing.border.TitledBorder eventLogTitle = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(208, 214, 222), 1, true),
                "Logs"
        );
        eventLogTitle.setTitleFont(FontLoader.ui(Font.BOLD, 10f));
        eventLogTitle.setTitleColor(new Color(78, 84, 94));
        eventLogScrollPane.setBorder(BorderFactory.createCompoundBorder(
                new EmptyBorder(10, 0, 0, 0),
                eventLogTitle
        ));

        // Put event log and strategy grid in a vertical split so both are always visible
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                eventLogScrollPane, strategyTabs);
        splitPane.setResizeWeight(0.5);
        splitPane.setDividerSize(6);
        splitPane.setBorder(null);
        splitPane.setOpaque(false);
        splitPane.setBackground(new Color(0, 0, 0, 0));
        if (splitPane.getUI() instanceof BasicSplitPaneUI splitPaneUi) {
            BasicSplitPaneDivider divider = splitPaneUi.getDivider();
            divider.setBorder(BorderFactory.createEmptyBorder());
            divider.setBackground(new Color(189, 198, 210));
        }

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
        updateLegalDisclosureUiState();
        settingsDialog.setConnectionVerifier(request -> runConnectionTest(
                request.brokerType(),
                settingsDialog.applicationMode(),
                request.apiKey(),
                request.apiSecret(),
                true,
                true
        ));
        strategyTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                if (preservingSelection) {
                    return;
                }
                if (strategyTable.getSelectedRow() < 0) {
                    return;
                }
                if (updateSelectedStrategy()) {
                    refreshPanels();
                }
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
        styleHeaderButton(portfolioActionsButton);
        styleHeaderButton(settingsButton);
        applyButtonIcon(addStrategyButton, "icons/add-stock-strategy.svg", 16);
        applyButtonIcon(portfolioActionsButton, "icons/actions.svg", 16);
        applyButtonIcon(settingsButton, "icons/settings.svg", 16);
    }

    private void applyDataViewFonts() {
        eventLog.setFont(FontLoader.ui(Font.PLAIN, 10f));
        strategyTable.setFont(FontLoader.ui(Font.PLAIN, 12f));
        strategyTable.getTableHeader().setFont(FontLoader.ui(Font.BOLD, 10f));
        strategyTable.getTableHeader().setOpaque(true);
        strategyTable.getTableHeader().setBackground(new Color(228, 233, 240));
        strategyTable.getTableHeader().setForeground(new Color(82, 88, 98));
        strategyTable.getTableHeader().setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(204, 210, 218)));
        filledOrdersTable.setFont(FontLoader.ui(Font.PLAIN, 12f));
        filledOrdersTable.getTableHeader().setFont(FontLoader.ui(Font.BOLD, 10f));
        filledOrdersTable.getTableHeader().setOpaque(true);
        filledOrdersTable.getTableHeader().setBackground(new Color(228, 233, 240));
        filledOrdersTable.getTableHeader().setForeground(new Color(82, 88, 98));
        filledOrdersTable.getTableHeader().setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(204, 210, 218)));
        paperUnrealizedSummary.setFont(headerStatus.getFont());
        liveUnrealizedSummary.setFont(headerStatus.getFont());
        headerTotalsSeparator.setFont(headerStatus.getFont());
        paperUnrealizedSummary.setForeground(new Color(220, 230, 255));
        liveUnrealizedSummary.setForeground(new Color(220, 230, 255));
        headerTotalsSeparator.setForeground(new Color(180, 190, 215));
        paperUnrealizedSummary.setHorizontalAlignment(SwingConstants.CENTER);
        liveUnrealizedSummary.setHorizontalAlignment(SwingConstants.CENTER);
        pollingSummary.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
        pollingSummary.setForeground(BOTTOM_STATUS_ACCENT);
        pollingSummary.setVerticalAlignment(SwingConstants.CENTER);
        pollingSummary.setBorder(new EmptyBorder(0, 0, 0, 8));
        positionSectionTitle.setFont(FontLoader.ui(Font.BOLD, 10f));
        rulesSectionTitle.setFont(FontLoader.ui(Font.BOLD, 10f));
        positionSummary.setFont(FontLoader.ui(Font.PLAIN, 10f));
        ruleState.setFont(FontLoader.ui(Font.PLAIN, 10f));
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

    // ── Shared colours for the dark-background (header / footer) buttons ──────
    private static final Color DARK_BTN_BG           = new Color(60,  60,  90);
    private static final Color DARK_BTN_BORDER        = new Color(100, 100, 160);
    private static final Color DARK_BTN_BG_HOVER      = new Color(80,  80,  118);
    private static final Color DARK_BTN_BORDER_HOVER  = new Color(128, 128, 196);
    private static final Color DARK_BTN_BG_PRESSED    = new Color(42,  42,  68);
    private static final Color DARK_BTN_BORDER_PRESSED= new Color(85,  85,  148);

    private void styleHeaderButton(JButton button) {
        button.setFocusPainted(false);
        button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        button.setForeground(new Color(230, 230, 255));
        button.setBackground(DARK_BTN_BG);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setRolloverEnabled(true);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DARK_BTN_BORDER, 1, true),
                new EmptyBorder(7, 12, 7, 12)
        ));
        button.setIconTextGap(8);
        installDarkButtonInteraction(button,
                new EmptyBorder(7, 12, 7, 12),
                new EmptyBorder(6, 11, 6, 11));
    }

    private void styleStatusActionButton(JButton button) {
        button.setFocusPainted(false);
        button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        button.setFont(BASE_FONT.deriveFont(Font.BOLD, 12f));
        button.setForeground(new Color(220, 220, 255));
        button.setBackground(DARK_BTN_BG);
        button.setOpaque(true);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DARK_BTN_BORDER, 1, true),
                new EmptyBorder(5, 12, 5, 12)
        ));
        button.setMargin(new java.awt.Insets(5, 12, 5, 12));
        button.setIconTextGap(8);
        installDarkButtonInteraction(button,
                new EmptyBorder(5, 12, 5, 12),
                new EmptyBorder(4, 11, 4, 11));
    }

    private JMenuItem createStatusMenuItem(String text, String iconPath, Runnable action) {
        JMenuItem item = new JMenuItem(text, SvgIconLoader.load(iconPath, 14));
        item.setFont(BASE_FONT.deriveFont(Font.PLAIN, 12f));
        item.setForeground(new Color(225, 228, 236));
        item.setBackground(new Color(46, 49, 60));
        item.setOpaque(true);
        item.setBorder(new EmptyBorder(8, 10, 8, 12));
        item.setIconTextGap(10);
        item.addActionListener(e -> action.run());
        return item;
    }

    /**
     * Attaches hover + press mouse feedback to a dark-background button.
     * Guards against double-installation via a client property.
     *
     * @param normalInner  inner EmptyBorder for the normal/hover state
     * @param pressedInner inner EmptyBorder for the pressed state (1 px less each
     *                     side to compensate for the thicker 2-px outer border)
     */
    private void installDarkButtonInteraction(JButton button,
                                              javax.swing.border.EmptyBorder normalInner,
                                              javax.swing.border.EmptyBorder pressedInner) {
        if (Boolean.TRUE.equals(button.getClientProperty("darkBtnInteractInstalled"))) {
            return;
        }
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (button.isEnabled()) {
                    button.setBackground(DARK_BTN_BG_HOVER);
                    button.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(DARK_BTN_BORDER_HOVER, 1, true),
                            normalInner));
                }
            }
            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(DARK_BTN_BG);
                button.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(DARK_BTN_BORDER, 1, true),
                        normalInner));
            }
            @Override
            public void mousePressed(MouseEvent e) {
                if (button.isEnabled() && e.getButton() == MouseEvent.BUTTON1) {
                    button.setBackground(DARK_BTN_BG_PRESSED);
                    button.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(DARK_BTN_BORDER_PRESSED, 2, true),
                            pressedInner));
                }
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                if (button.contains(e.getPoint()) && button.isEnabled()) {
                    button.setBackground(DARK_BTN_BG_HOVER);
                    button.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(DARK_BTN_BORDER_HOVER, 1, true),
                            normalInner));
                } else {
                    button.setBackground(DARK_BTN_BG);
                    button.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(DARK_BTN_BORDER, 1, true),
                            normalInner));
                }
            }
        });
        button.putClientProperty("darkBtnInteractInstalled", Boolean.TRUE);
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

    private void openRequestNewFeatureDialog() {
        RequestNewFeatureDialog dialog = new RequestNewFeatureDialog(
                this,
                settingsDialog.getUserEmail(),
                FeedbackEmailService.fromConfiguration()
        );
        if (dialog.showDialog()) {
            log("[Request New Feature] Sent and copied to " + settingsDialog.getUserEmail());
        }
    }

    private void openContactUsDialog() {
        ContactUsDialog dialog = new ContactUsDialog(
                this,
                settingsDialog.getUserEmail(),
                FeedbackEmailService.fromConfiguration()
        );
        if (dialog.showDialog()) {
            log("[Contact Us / Feedback] Sent and copied to " + settingsDialog.getUserEmail());
        }
    }

    private void openSubmitBugDialog() {
        SubmitBugDialog dialog = new SubmitBugDialog(
                this,
                settingsDialog.getUserEmail(),
                FeedbackEmailService.fromConfiguration()
        );
        if (dialog.showDialog()) {
            log("[Submit Bug] Sent and copied to " + settingsDialog.getUserEmail());
        }
    }

    private static Font createBaseFont() {
        return FontLoader.ui(Font.PLAIN, 12);
    }

    private void wireEvents() {
        addStrategyButton.addActionListener(e -> addStrategy());
        portfolioActionsButton.addActionListener(e -> showPortfolioActionsMenu());
        settingsButton.addActionListener(e -> openSettingsDialog());
    }

    private void togglePauseResume(int viewRow) {
        strategyActionsController.togglePauseResume(viewRow);
    }

    private void sellStrategy(int viewRow) {
        strategyActionsController.sellPosition(viewRow);
    }

    private void previewLivePromotion(int viewRow) {
        strategyActionsController.previewLivePromotion(viewRow);
    }

    private void deleteStrategy(int viewRow) {
        strategyActionsController.deleteStrategy(viewRow);
    }

    private boolean hasOpenPosition(Strategy strategy) {
        return loadPositionForStrategy(strategy).getTotalShares() > 0;
    }

    private StrategyService.StrategyCreationResult sellPosition(Strategy strategy) {
        StrategyService modeAwareService = strategyServiceForMode(strategy.mode());
        if (modeAwareService == null) {
            return StrategyService.StrategyCreationResult.failed(
                    "Broker client is not configured for " + strategy.mode().name() + " mode."
            );
        }
        return modeAwareService.closePosition(strategy.id());
    }

    private StrategyService strategyServiceForMode(StrategyMode mode) {
        ApplicationMode applicationMode = mode == StrategyMode.LIVE ? ApplicationMode.LIVE : ApplicationMode.PAPER;
        HttpAlpacaClient client = alpacaClientForMode(applicationMode);
        return tradingRuntimeSupport.createStrategyService(client, mode);
    }

    private void showPortfolioActionsMenu() {
        JPopupMenu menu = new JPopupMenu();
        menu.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(70, 76, 90), 1, true),
                new EmptyBorder(4, 4, 4, 4)
        ));
        menu.add(createStatusMenuItem("Sell Profitable Positions", "icons/submit.svg",
                () -> handlePortfolioSellAction(PortfolioSellScope.PROFITABLE)));
        menu.add(createStatusMenuItem("Sell All Open Positions", "icons/close.svg",
                () -> handlePortfolioSellAction(PortfolioSellScope.ALL_OPEN)));
        menu.add(createStatusMenuItem("Sell Losing Positions", "icons/delete.svg",
                () -> handlePortfolioSellAction(PortfolioSellScope.LOSS_ONLY)));
        menu.show(portfolioActionsButton, 0, portfolioActionsButton.getHeight());
    }

    private void handlePortfolioSellAction(PortfolioSellScope scope) {
        List<ManagedStrategy> targets = strategies.stream()
                .filter(scope::matches)
                .toList();
        if (targets.isEmpty()) {
            JOptionPane.showMessageDialog(this, scope.emptyMessage(), scope.dialogTitle(), JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int choice = JOptionPane.showConfirmDialog(
                this,
                buildPortfolioSellConfirmation(scope, targets),
                scope.dialogTitle(),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }

        new SwingWorker<PortfolioSellBatchResult, Void>() {
            @Override
            protected PortfolioSellBatchResult doInBackground() {
                List<String> successes = new ArrayList<>();
                List<String> failures = new ArrayList<>();
                for (ManagedStrategy entry : targets) {
                    StrategyService.StrategyCreationResult result = sellPosition(entry.strategy);
                    if (result.success()) {
                        successes.add(entry.strategy.symbol());
                    } else {
                        failures.add(entry.strategy.symbol() + ": " + result.error());
                    }
                }
                return new PortfolioSellBatchResult(successes, failures);
            }

            @Override
            protected void done() {
                try {
                    PortfolioSellBatchResult result = get();
                    syncStrategiesFromRepository();
                    refreshStrategyTableData();
                    updateSelectedStrategy();
                    refreshPanels();
                    updateStatusBar();
                    log(scope.logPrefix() + " submitted for " + result.successes().size() + " strategy(ies).");
                    if (!result.failures().isEmpty()) {
                        log(scope.logPrefix() + " failures: " + String.join(" | ", result.failures()));
                    }
                    JOptionPane.showMessageDialog(
                            TradingFrame.this,
                            buildPortfolioSellResultMessage(scope, result),
                            scope.dialogTitle(),
                            result.failures().isEmpty() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE
                    );
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(
                            TradingFrame.this,
                            "Failed to submit the requested sell orders: " + ex.getMessage(),
                            scope.dialogTitle(),
                            JOptionPane.ERROR_MESSAGE
                    );
                }
            }
        }.execute();
    }

    private String buildPortfolioSellConfirmation(PortfolioSellScope scope, List<ManagedStrategy> targets) {
        String symbols = targets.stream()
                .limit(6)
                .map(entry -> entry.strategy.symbol())
                .collect(Collectors.joining(", "));
        String ellipsis = targets.size() > 6 ? ", ..." : "";
        return "<html><body style='width:360px'>"
                + "<b>" + scope.confirmHeading(targets.size()) + "</b><br><br>"
                + "Symbols: " + symbols + ellipsis + "<br><br>"
                + "Each strategy will submit a manual limit sell using its latest broker price."
                + "<br>Strategies configured to repeat after exit can re-initiate after the position fully closes."
                + "</body></html>";
    }

    private String buildPortfolioSellResultMessage(PortfolioSellScope scope, PortfolioSellBatchResult result) {
        StringBuilder sb = new StringBuilder("<html><body style='width:360px'>");
        sb.append("<b>").append(scope.menuLabel()).append("</b><br><br>");
        sb.append("Submitted: ").append(result.successes().size());
        if (!result.successes().isEmpty()) {
            sb.append("<br>").append(String.join(", ", result.successes()));
        }
        if (!result.failures().isEmpty()) {
            sb.append("<br><br><b>Failed:</b><br>").append(String.join("<br>", result.failures()));
        }
        sb.append("</body></html>");
        return sb.toString();
    }

    public void promptForRequiredSettings() {
        // First-launch flow: disclosure -> settings -> (after connect) auto add strategy.
        if (!ensureLegalDisclosureAccepted()) {
            return;
        }
        maybeShowFirstRunOnboarding();
        if (!settingsDialog.hasRequiredSettings()) {
            openSettingsDialog();
            return;
        }
        if (!autoInitializeConnection()) {
            openSettingsDialog();
        }
    }

    private void maybeShowFirstRunOnboarding() {
        if (onboardingStateStore.isCompleted()) {
            return;
        }
        FirstRunOnboardingDialog dialog = new FirstRunOnboardingDialog(this);
        if (dialog.showDialog()) {
            onboardingStateStore.markCompleted();
        }
    }

    private void openSettingsDialog() {
        stopTradingEventStream();
        settingsDialog.prepareForOpen();
        settingsDialog.setVisible(true);
        if (settingsDialog.wasSavedDuringOpen()) {
            connectionOk = false;
            setStatus("Not connected — verify connection in Settings after changes.", STATUS_WARN);
            updateHeaderModeStatus(currentBrokerType);
            updateStatusBar();
            autoInitializeConnection();
        }
    }

    private boolean autoInitializeConnection() {
        return connectionLifecycleCoordinator.autoInitializeConnection();
    }

    private void refreshStrategyRuntimeServices(String apiKey, String apiSecret, ApplicationMode mode) {
        runtimeApiKey = apiKey == null ? "" : apiKey;
        runtimeApiSecret = apiSecret == null ? "" : apiSecret;
        refreshCachedAlpacaClients();
        HttpAlpacaClient runtimeClient = alpacaClientForMode(mode);
        if (runtimeClient == null) {
            return;
        }
        if (strategyPollingService != null) {
            strategyPollingService.shutdown();
        }
        TradingRuntimeSupport.RuntimeServices runtimeServices = tradingRuntimeSupport.createRuntimeServices(
                runtimeClient,
                mode,
                new StrategyPollingService.PollListener() {
            @Override
            public void onPollStarted(String strategyId) {
                SwingUtilities.invokeLater(() -> onStrategyPollStarted(strategyId));
            }

            @Override
            public void onPollCompleted(String strategyId) {
                SwingUtilities.invokeLater(() -> onStrategyPollCompleted(strategyId));
            }

            @Override
            public void onPollFailed(String strategyId) {
                SwingUtilities.invokeLater(() -> onStrategyPollFailed(strategyId));
            }
        }
        );
        strategyService = runtimeServices.strategyService();
        strategyPollingService = runtimeServices.strategyPollingService();
    }

    private void refreshCachedAlpacaClients() {
        TradingRuntimeSupport.RuntimeClients runtimeClients = tradingRuntimeSupport.createClients(
                settingsDialog,
                runtimeApiKey,
                runtimeApiSecret
        );
        paperModeClient = runtimeClients.paperModeClient();
        liveModeClient = runtimeClients.liveModeClient();
    }

    private HttpAlpacaClient alpacaClientForMode(ApplicationMode mode) {
        return mode == ApplicationMode.LIVE ? liveModeClient : paperModeClient;
    }

    private SettingsDialog.ConnectionResult runConnectionTest(BrokerType brokerType, ApplicationMode mode, String apiKey, String apiSecret, boolean manualTrigger, boolean applyRuntimeChanges) {
        return connectionLifecycleCoordinator.runConnectionTest(
                brokerType,
                mode,
                apiKey,
                apiSecret,
                manualTrigger,
                applyRuntimeChanges
        );
    }

    private void scheduleConnectionRetry() {
        connectionLifecycleCoordinator.scheduleConnectionRetry();
    }

    private void retryBrokerConnectionIfConfigured() {
        connectionLifecycleCoordinator.retryBrokerConnectionIfConfigured();
    }

    private void initPersistenceAndRestore() {
        ensureAnalyticsPublisher();
        restoreStrategies();
    }

    private void triggerPollingCycle() {
        if (strategyPollingService == null || !shouldRunPollingCycleNow() || !pollingCycleInFlight.compareAndSet(false, true)) {
            return;
        }
        uiPollingExecutor.submit(() -> {
            try {
                int dueStrategies = strategyPollingService.pollDueStrategies();
                List<Strategy> stored = strategyRepository.findAll();
                boolean refreshBrokerSnapshots = shouldRunBatchGridPriceRefresh(stored) && hasStrategiesNeedingBrokerSnapshots(stored);
                Map<String, Position> positionSnapshots = refreshBrokerSnapshots
                        ? loadPositionSnapshotsForStrategies(stored)
                        : Map.of();
                SwingUtilities.invokeLater(() -> {
                    try {
                        syncStrategies(stored);
                        applyPositionSnapshots(positionSnapshots);
                        if ((dueStrategies > 0 || !positionSnapshots.isEmpty()) && shouldRunBrokerBackedUiRefresh()) {
                            refreshStrategyTableContent();
                            refreshPanels();
                        }
                        updateStatusBar();
                    } finally {
                        pollingCycleInFlight.set(false);
                    }
                });
            } catch (Exception ex) {
                log("Polling cycle failed: " + ex.getMessage());
                SwingUtilities.invokeLater(() -> {
                    updateStatusBar();
                    pollingCycleInFlight.set(false);
                });
            }
        });
    }

    private boolean shouldRunPollingCycleNow() {
        if (!shouldSuppressBrokerBackedRefreshForClosedMarket()) {
            return true;
        }
        long now = System.currentTimeMillis();
        if (now - lastClosedMarketPollingCycleAtMillis >= CLOSED_MARKET_POLL_INTERVAL_MILLIS) {
            lastClosedMarketPollingCycleAtMillis = now;
            return true;
        }
        return false;
    }

    private boolean shouldRunBatchGridPriceRefresh(List<Strategy> stored) {
        long now = System.currentTimeMillis();
        long refreshIntervalMillis = BrokerSnapshotRefreshPolicy.resolveIntervalMillis(stored);
        logSnapshotIntervalIfChanged(refreshIntervalMillis);
        if (batchGridPriceRefreshRequestedFromStream) {
            batchGridPriceRefreshRequestedFromStream = false;
            lastBatchGridPriceRefreshAtMillis = now;
            return true;
        }
        if (now - lastBatchGridPriceRefreshAtMillis >= refreshIntervalMillis) {
            lastBatchGridPriceRefreshAtMillis = now;
            return true;
        }
        return false;
    }

    private void logSnapshotIntervalIfChanged(long refreshIntervalMillis) {
        if (refreshIntervalMillis <= 0L || refreshIntervalMillis == lastLoggedSnapshotIntervalMillis) {
            return;
        }
        lastLoggedSnapshotIntervalMillis = refreshIntervalMillis;
        log("[POLL][SNAPSHOT] interval=" + (refreshIntervalMillis / 1000L)
                + "s policy=min-with-floor(2s) eligibility=ACTIVE-only");
    }

    private Map<String, Position> loadPositionSnapshotsForStrategies(List<Strategy> stored) {
        if (stored == null || stored.isEmpty() || currentBrokerType != BrokerType.ALPACA) {
            return Map.of();
        }
        Map<String, Position> snapshots = new LinkedHashMap<>();
        loadPositionSnapshotsForMode(stored, StrategyMode.PAPER, alpacaClientForMode(ApplicationMode.PAPER), snapshots);
        loadPositionSnapshotsForMode(stored, StrategyMode.LIVE, alpacaClientForMode(ApplicationMode.LIVE), snapshots);
        return snapshots;
    }

    private void loadPositionSnapshotsForMode(
            List<Strategy> stored,
            StrategyMode mode,
            HttpAlpacaClient client,
            Map<String, Position> target
    ) {
        if (client == null) {
            return;
        }
        List<Strategy> strategiesForMode = stored.stream()
                .filter(strategy -> strategy.mode() == mode)
                .filter(this::includeInBrokerSnapshotRefresh)
                .filter(strategy -> strategy.symbol() != null && !strategy.symbol().isBlank())
                .toList();
        if (strategiesForMode.isEmpty()) {
            return;
        }
        List<String> symbols = new ArrayList<>();
        for (Strategy strategy : strategiesForMode) {
            if (!symbols.contains(strategy.symbol().toUpperCase(Locale.ROOT))) {
                symbols.add(strategy.symbol().toUpperCase(Locale.ROOT));
            }
        }
        if (symbols.isEmpty()) {
            return;
        }
        Map<String, BigDecimal> latestPrices = client.getLatestPrices(symbols);
        Map<String, com.neuralarc.api.AlpacaPositionData> positionsBySymbol = client.getPositions().stream()
                .collect(Collectors.toMap(
                        position -> position.symbol().toUpperCase(Locale.ROOT),
                        position -> position,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        for (Strategy strategy : strategiesForMode) {
            Position snapshot = new Position(strategy.symbol());
            com.neuralarc.api.AlpacaPositionData remotePosition = positionsBySymbol.get(strategy.symbol().toUpperCase(Locale.ROOT));
            if (remotePosition != null && remotePosition.exists()) {
                int quantity = remotePosition.quantity().setScale(0, java.math.RoundingMode.DOWN).intValue();
                if (quantity > 0) {
                    snapshot.applyBuy(quantity, remotePosition.avgEntryPrice());
                }
                if (remotePosition.marketPrice() != null && remotePosition.marketPrice().compareTo(BigDecimal.ZERO) > 0) {
                    snapshot.setLastPrice(remotePosition.marketPrice());
                }
            }
            BigDecimal latestPrice = latestPrices.get(strategy.symbol().toUpperCase(Locale.ROOT));
            if (latestPrice != null && latestPrice.compareTo(BigDecimal.ZERO) > 0) {
                snapshot.setLastPrice(latestPrice);
            }
            target.put(strategy.id(), snapshot);
        }
    }

    private void applyPositionSnapshots(Map<String, Position> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return;
        }
        for (ManagedStrategy entry : strategies) {
            Position snapshot = snapshots.get(entry.strategy.id());
            if (snapshot == null) {
                continue;
            }
            entry.setCachedPosition(snapshot);
        }
    }

    private SettingsDialog.StrategyTransferResult exportStrategiesToFile(Path targetPath) {
        if (targetPath == null) {
            return new SettingsDialog.StrategyTransferResult(false, "Export path is missing.");
        }
        try {
            Path exportParent = targetPath.toAbsolutePath().getParent();
            if (exportParent != null) {
                Files.createDirectories(exportParent);
            }
            strategyRepository.flushNow();
            String content = strategyRepository.exportJson(true);
            JSONArray parsed = new JSONArray(content.isBlank() ? "[]" : content);
            Files.writeString(targetPath, content);
            return new SettingsDialog.StrategyTransferResult(true,
                    "Exported " + parsed.length() + " strategies to " + targetPath.toAbsolutePath());
        } catch (Exception ex) {
            return new SettingsDialog.StrategyTransferResult(false,
                    "Failed to export strategies: " + ex.getMessage());
        }
    }

    private SettingsDialog.StrategyTransferResult importStrategiesFromFile(Path sourcePath) {
        if (sourcePath == null || !Files.exists(sourcePath)) {
            return new SettingsDialog.StrategyTransferResult(false, "Import file does not exist.");
        }
        try {
            String incoming = Files.readString(sourcePath);
            JSONArray parsed = new JSONArray(incoming.isBlank() ? "[]" : incoming);
            strategyRepository.replaceAllFromJson(parsed.toString());
            syncStrategiesFromRepository();
            refreshStrategyTableData();
            refreshPanels();
            updateStatusBar();
            return new SettingsDialog.StrategyTransferResult(true,
                    "Imported " + parsed.length() + " strategies from " + sourcePath.toAbsolutePath());
        } catch (Exception ex) {
            return new SettingsDialog.StrategyTransferResult(false,
                    "Failed to import strategies: " + ex.getMessage());
        }
    }

    private void restoreStrategies() {
        strategies.clear();
        List<Strategy> storedStrategies = strategyRepository.findAll();
        List<Strategy> syncedRemoteStrategies = strategyService.syncRemoteStrategies();
        storedStrategies = strategyRepository.findAll();
        for (Strategy strategy : storedStrategies) {
            strategy = strategyService.recoverStaleRestartFailure(strategy.id()).orElse(strategy);
            ManagedStrategy managed = new ManagedStrategy(strategy);
            resetPollingCountdown(managed);
            strategies.add(managed);
            log("[" + strategy.symbol() + "] Restored (" + strategy.status().name() + ").");
        }
        for (Strategy strategy : syncedRemoteStrategies) {
            log("[" + strategy.symbol() + "] Synced from Alpaca and resumed locally.");
        }
        if (storedStrategies.isEmpty()) {
            refreshPanels();
            updateStatusBar();
            maybePromptForDefaultStrategy();
            return;
        }
        refreshStrategyTableData();
        if (!strategies.isEmpty() && strategyTable.getRowCount() > 0) {
            strategyTable.setRowSelectionInterval(0, 0);
        } else {
            strategyTable.clearSelection();
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
        if (!ensureLegalDisclosureAccepted()) {
            return;
        }
        addStrategy();
    }

    private void addStrategy() {
        if (!ensureLegalDisclosureAccepted()) {
            return;
        }
        if (!connectionOk || tradingApi == null) {
            JOptionPane.showMessageDialog(this, "Please complete Settings and verify the connection before adding a strategy.", "Connection Required", JOptionPane.WARNING_MESSAGE);
            return;
        }

        HttpAlpacaMarketDataApi marketDataApi = connectionOk && !runtimeApiKey.isBlank()
                ? new HttpAlpacaMarketDataApi(runtimeApiKey, runtimeApiSecret) : null;
        StrategyDialog dialog = new StrategyDialog(this, null, marketDataApi, autoAnalyzeResultStore);
        StrategyConfig config = dialog.showDialog();
        if (config == null) {
            return;
        }

        StrategyMode targetMode = settingsDialog.appliedApplicationMode() == ApplicationMode.LIVE ? StrategyMode.LIVE : StrategyMode.PAPER;
        if (findStrategy(config.symbol(), targetMode, false) != null) {
            JOptionPane.showMessageDialog(this, "A strategy for this symbol already exists. Use Edit on the grid row.", "Duplicate Symbol", JOptionPane.WARNING_MESSAGE);
            return;
        }
        boolean symbolExistsInRepository = strategyRepository.findAll().stream()
                .filter(existing -> existing.status() != StrategyStatus.ARCHIVED)
                .anyMatch(existing -> existing.symbol().equalsIgnoreCase(config.symbol()) && existing.mode() == targetMode);
        if (symbolExistsInRepository) {
            JOptionPane.showMessageDialog(this, "A strategy for this symbol already exists. Use Edit on the grid row.", "Duplicate Symbol", JOptionPane.WARNING_MESSAGE);
            syncStrategiesFromRepository();
            refreshStrategyTableData();
            return;
        }

        Strategy strategy = Strategy.fromConfig(
                UUID.randomUUID().toString(),
                config.symbol() + " Strategy",
                config,
                targetMode
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
        log("[" + config.symbol() + "] Initial order submitted. rule=BASE_BUY, price=$"
                + strategy.baseBuyLimitPrice().toPlainString()
                + ", clientOrderId=" + creationResult.clientOrderId());
        JOptionPane.showMessageDialog(
                this,
                "Initial Alpaca limit buy submitted successfully.\nOrder ID: " + creationResult.alpacaOrderId(),
                "Strategy Activated",
                JOptionPane.INFORMATION_MESSAGE
        );

        ensureAnalyticsPublisher();
        syncStrategiesFromRepository();
        updateHeaderModeStatus(currentBrokerType);
        refreshStrategyTableData();
        selectedStrategyId = strategy.id();
        restoreSelectedRow();
        int selectedModelRow = strategyTable.getSelectedRow() >= 0
                ? strategyTable.convertRowIndexToModel(strategyTable.getSelectedRow())
                : strategies.size() - 1;
        int addedViewRow = safeConvertModelRowToView(selectedModelRow);
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
        HttpAlpacaMarketDataApi marketDataApi = connectionOk && !runtimeApiKey.isBlank()
                ? new HttpAlpacaMarketDataApi(runtimeApiKey, runtimeApiSecret) : null;
        StrategyDialog dialog = new StrategyDialog(this, entry.toConfig(), marketDataApi, autoAnalyzeResultStore);
        StrategyConfig updated = dialog.showDialog();
        if (updated == null) {
            return;
        }

        ManagedStrategy duplicate = findStrategy(updated.symbol(), entry.strategy.mode(), false);
        if (duplicate != null && duplicate != entry) {
            JOptionPane.showMessageDialog(this, "A strategy for this symbol already exists.", "Duplicate Symbol", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Strategy updatedStrategy = Strategy.fromConfig(entry.strategy.id(), entry.strategy.name(), updated, entry.strategy.mode());
        updatedStrategy.setStatus(entry.strategy.status());
        updatedStrategy.setCurrentState(entry.strategy.currentState());
        updatedStrategy.setLastPolledAt(entry.strategy.lastPolledAt());
        updatedStrategy.setLastEvent(entry.strategy.lastEvent());
        updatedStrategy.setLatestOrderStatus(entry.strategy.latestOrderStatus());
        updatedStrategy.setLatestAlpacaOrderId(entry.strategy.latestAlpacaOrderId());
        updatedStrategy.setLastError(entry.strategy.lastError());
        Optional<Strategy> updatedResult = strategyService.updateStrategy(updatedStrategy);
        if (updatedResult.isEmpty()) {
            JOptionPane.showMessageDialog(
                    this,
                    "Failed to update strategy. Please review values and try again.",
                    "Strategy Update Failed",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }
        entry.syncFrom(updatedResult.get());
        resetPollingCountdown(entry);
        updateHeaderModeStatus(currentBrokerType);
        refreshStrategyTableData();
        refreshPanels();
    }

    private String closePaperAccountState(Strategy strategy) {
        if (strategy == null) {
            return "";
        }
        HttpAlpacaClient paperClient = alpacaClientForStrategyMode(StrategyMode.PAPER);
        if (paperClient == null) {
            return "Paper cleanup skipped because saved paper credentials are not available.";
        }

        int canceled = 0;
        for (com.neuralarc.api.AlpacaOrderData openOrder : paperClient.getOpenOrders(strategy.symbol())) {
            if (paperClient.cancelOrder(openOrder.orderId())) {
                canceled++;
            }
        }

        int closedQuantity = 0;
        Optional<com.neuralarc.api.AlpacaPositionData> paperPosition = paperClient.getPosition(strategy.symbol());
        if (paperPosition.isPresent() && paperPosition.get().exists()) {
            int quantity = paperPosition.get().quantity().setScale(0, java.math.RoundingMode.DOWN).intValue();
            BigDecimal latestPrice = paperPosition.get().marketPrice().compareTo(BigDecimal.ZERO) > 0
                    ? paperPosition.get().marketPrice()
                    : paperClient.getLatestPrice(strategy.symbol());
            if (quantity > 0 && latestPrice.compareTo(BigDecimal.ZERO) > 0) {
                String clientOrderId = "neuralarc-paper-close-" + strategy.id() + "-" + System.currentTimeMillis();
                com.neuralarc.api.AlpacaOrderData sellOrder = paperClient.submitLimitSellOrder(strategy.symbol(), quantity, latestPrice, clientOrderId);
                if (sellOrder.orderId() != null && !sellOrder.orderId().isBlank()) {
                    closedQuantity = quantity;
                }
            }
        }

        if (canceled == 0 && closedQuantity == 0) {
            return "Paper cleanup requested, but there were no open paper orders or paper position to close.";
        }
        return "Paper cleanup requested: canceled " + canceled + " paper order(s) and submitted close order for " + closedQuantity + " paper share(s).";
    }

    private void stopPoller(ManagedStrategy entry) {
        stopPollingCountdown(entry);
    }

    private void refreshPanels() {
        lastBrokerBackedUiRefreshAtMillis = System.currentTimeMillis();
        updateUnrealizedSummaries();
        ManagedStrategy entry = selectedManagedStrategy();
        if (entry == null) {
            positionSummary.setText("Position: -");
            ruleState.setText("Rules: -");
            ruleState.setToolTipText(null);
            return;
        }

        if (strategyTable.getSelectedRow() < 0) {
            SwingUtilities.invokeLater(this::restoreSelectedRow);
        }

        List<StrategyOrder> strategyOrders = strategyOrderRepository.findByStrategyId(entry.strategy.id());
        Optional<StrategyOrder> pendingOrder = latestPendingOrder(strategyOrders);
        StrategyOrder latestOrder = latestOrder(strategyOrders).orElse(null);

        if (currentBrokerType == BrokerType.ALPACA && tradingApi != null) {
            Position p = displayedPosition(entry);
            if (p.getTotalShares() == 0 && (pendingOrder.isPresent() || isWaitingForFill(entry.strategy))) {
                String orderStatus = entry.strategy.latestOrderStatus() == null || entry.strategy.latestOrderStatus().isBlank()
                        ? "PENDING"
                        : entry.strategy.latestOrderStatus();
                String stockPriceDisplay = p.getLastPrice().compareTo(BigDecimal.ZERO) > 0
                        ? p.getLastPrice().toPlainString()
                        : "-";
                positionSummary.setText("[" + entry.strategy.symbol() + "]: Waiting Fill — order submitted to Alpaca"
                        + " (status: " + orderStatus + ", Stock Price=" + stockPriceDisplay + ")");
            } else {
                positionSummary.setText(String.format(
                        "[%s]: Shares=%d | Stock Price=%s | Avg Cost=%s | MarketValue=%s | Invested=%s | Realized=%s | Unrealized=%s",
                        entry.strategy.symbol(),
                        p.getTotalShares(), p.getLastPrice().toPlainString(), p.getAverageCost(), p.marketValue(), p.totalInvested(), p.getRealizedPnl(), p.unrealizedPnl()));
            }
        } else {
            positionSummary.setText("[" + entry.strategy.symbol() + "]: Position data available when broker is connected.");
        }
        ruleState.setText(buildRuleTriggeredShortSummary(entry.strategy, entry, latestOrder, pendingOrder.orElse(null)));
        ruleState.setToolTipText(TooltipStyler.html(
                buildRuleTriggeredSummary(entry.strategy, latestOrder, pendingOrder.orElse(null)),
                320
        ));
    }

    private boolean shouldRunBrokerBackedUiRefresh() {
        long now = System.currentTimeMillis();
        if (now - lastBrokerBackedUiRefreshAtMillis >= 5_000L) {
            lastBrokerBackedUiRefreshAtMillis = now;
            return true;
        }
        return false;
    }

    private boolean isWaitingForFill(Strategy strategy) {
        String latestOrderStatus = strategy.latestOrderStatus();
        if (latestOrderStatus != null) {
            if (BrokerOrderStatusUtil.isWaitingForFill(latestOrderStatus)) {
                return true;
            }
        }
        StrategyLifecycleState state = strategy.currentState();
        return state == StrategyLifecycleState.BASE_BUY_PLACED
                || state == StrategyLifecycleState.BASE_BUY_PARTIALLY_FILLED
                || state == StrategyLifecycleState.BUY_LIMIT_1_PLACED
                || state == StrategyLifecycleState.BUY_LIMIT_1_PARTIALLY_FILLED
                || state == StrategyLifecycleState.BUY_LIMIT_2_PLACED
                || state == StrategyLifecycleState.BUY_LIMIT_2_PARTIALLY_FILLED
                || state == StrategyLifecycleState.SELL_PLACED
                || state == StrategyLifecycleState.SELL_PARTIALLY_FILLED;
    }

    private String buildRuleTriggeredShortSummary(Strategy strategy, ManagedStrategy entry, StrategyOrder latestOrder, StrategyOrder pendingOrder) {
        StrategyLifecycleState state = strategy.currentState();
        String stateDisplay = displayStatusLabel(strategy);

        if (stateDisplay.isEmpty()) {
            return "Rules: -";
        }

        BigDecimal displayPrice = resolveDisplayPrice(entry, latestOrder);
        String priceDisplay = displayPrice.compareTo(BigDecimal.ZERO) > 0 ? " @ $" + displayPrice.toPlainString() : "";
        Instant placedAt = latestOrder == null ? null : latestOrder.submittedAt();
        String dateDisplay = placedAt == null ? "" : " on " + formatTimestampForDisplay(placedAt);
        String waitingDisplay = buildWaitingDurationDisplay(pendingOrder);
        return "Rules: " + stateDisplay + priceDisplay + dateDisplay + waitingDisplay;
    }

    private String formatLifecycleStateForDisplay(StrategyLifecycleState state) {
        return strategyTablePresenter.formatLifecycleStateForDisplay(state);
    }

    private String displayStatusLabel(Strategy strategy) {
        return strategyTablePresenter.displayStatusLabel(
                strategy,
                shouldSuppressBrokerBackedRefreshForClosedMarket(),
                strategy != null && isWaitingForFill(strategy),
                strategy != null && strategy.status() == StrategyStatus.FAILED && isQueueableSessionError(strategy.lastError())
        );
    }

    private boolean isQueueableSessionError(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("market is closed")
                || normalized.contains("outside market hours")
                || normalized.contains("extended_hours")
                || normalized.contains("time_in_force")
                || normalized.contains("session");
    }

    private String formatTimestampForDisplay(Instant timestamp) {
        ZonedDateTime zdt = timestamp.atZone(java.time.ZoneId.systemDefault());
        return zdt.format(RULE_TIMESTAMP_FORMAT);
    }

    private String buildRuleTriggeredSummary(Strategy strategy, StrategyOrder latestOrder, StrategyOrder pendingOrder) {
        AppSettingsService.AppSettings settings = appSettingsService.load();
        String lastTriggeredRule = strategy.lastTriggeredRuleType() == null || strategy.lastTriggeredRuleType().isBlank()
                ? "-"
                : strategy.lastTriggeredRuleType();
        String latestOrderStatus = strategy.latestOrderStatus() == null || strategy.latestOrderStatus().isBlank()
                ? "-"
                : strategy.latestOrderStatus();
        String stopLossValue = strategy.stopLossType() == StopLossType.PERCENT_BELOW_AVERAGE_COST
                ? strategy.stopLossPercent().toPlainString() + "% below avg cost"
                : strategy.stopLossPrice().toPlainString();
        String profitHoldValue;
        if (!strategy.profitHoldEnabled()) {
            profitHoldValue = "Disabled";
        } else if (strategy.profitHoldType() == ProfitHoldType.FIXED_AMOUNT_TRAILING) {
            profitHoldValue = "Fixed $" + strategy.profitHoldAmount().toPlainString();
        } else {
            profitHoldValue = strategy.profitHoldPercent().toPlainString() + "%";
        }
        String cycleBehaviorValue = strategy.restartAfterExitEnabled()
                ? "Repeat after profitable exit (optional)"
                : "Do not repeat";
        String nextOpenValue = marketHoursService.nextMarketOpen(settings.extendedHoursTradingEnabled())
                .atZone(java.time.ZoneId.systemDefault())
                .format(NEXT_OPEN_FORMAT);
        String extendedHoursValue = settings.extendedHoursTradingEnabled() ? "Enabled" : "Disabled";
        String pauseReasonValue = strategy.pauseReason() == null ? PauseReason.NONE.name() : strategy.pauseReason().name();
        String orderPlaced = latestOrder == null || latestOrder.submittedAt() == null
                ? "-"
                : formatTimestampForDisplay(latestOrder.submittedAt());
        String waitingDuration = pendingOrder == null || pendingOrder.submittedAt() == null
                ? "-"
                : humanDuration(Duration.between(pendingOrder.submittedAt(), Instant.now()));
        return "<b>Last Triggered:</b> " + lastTriggeredRule
                + " &nbsp;|&nbsp; <b>Latest Order:</b> " + latestOrderStatus
                + "<br><b>Order Placed On:</b> " + orderPlaced
                + "<br><b>Waiting Duration:</b> " + waitingDuration
                + "<br><b>Base Buy:</b> <= " + strategy.baseBuyLimitPrice().toPlainString() + " x " + strategy.baseBuyQuantity()
                + "<br><b>Loss Buy Levels:</b> " + (strategy.lossBuyLevelsEnabled() ? "Enabled" : "Disabled")
                + "<br><b>Buy Limit 1:</b> " + (strategy.lossBuyLevelsEnabled()
                    ? "<= " + strategy.buyLimit1Price().toPlainString() + " x " + strategy.buyLimit1Quantity()
                    : "Disabled")
                + "<br><b>Buy Limit 2:</b> " + (strategy.lossBuyLevelsEnabled()
                    ? "<= " + strategy.buyLimit2Price().toPlainString() + " x " + strategy.buyLimit2Quantity()
                    : "Disabled")
                + "<br><b>Stop Loss:</b> " + stopLossValue
                + "<br><b>Target Sell:</b> >= " + strategy.targetSellPrice().toPlainString()
                + "<br><b>Profit Hold:</b> " + profitHoldValue
                + "<br><b>Pause Reason:</b> " + pauseReasonValue
                + "<br><b>Extended Hours:</b> " + extendedHoursValue
                + "<br><b>Next Market Open:</b> " + nextOpenValue
                + "<br><b>Cycle Behavior:</b> " + cycleBehaviorValue
                + " (stop-loss/manual close do not restart)";
    }

    private Optional<StrategyOrder> latestOrder(List<StrategyOrder> orders) {
        return orders.stream().max(Comparator.comparing(StrategyOrder::submittedAt));
    }

    private Optional<StrategyOrder> latestPendingOrder(List<StrategyOrder> orders) {
        return orders.stream()
                .filter(StrategyOrder::isPending)
                .max(Comparator.comparing(StrategyOrder::submittedAt));
    }

    private BigDecimal resolveDisplayPrice(ManagedStrategy entry, StrategyOrder latestOrder) {
        if (latestOrder != null && latestOrder.limitPrice().compareTo(BigDecimal.ZERO) > 0) {
            return latestOrder.limitPrice();
        }
        Position position = entry.cachedPosition();
        return position.getLastPrice();
    }

    private String buildWaitingDurationDisplay(StrategyOrder pendingOrder) {
        if (pendingOrder == null || pendingOrder.submittedAt() == null) {
            return "";
        }
        return " | Waiting " + humanDuration(Duration.between(pendingOrder.submittedAt(), Instant.now()));
    }

    private String humanDuration(Duration duration) {
        long totalSeconds = Math.max(0L, duration.getSeconds());
        long days = totalSeconds / 86_400L;
        long hours = (totalSeconds % 86_400L) / 3_600L;
        long minutes = (totalSeconds % 3_600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (days > 0) {
            return days + "d " + hours + "h " + minutes + "m";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }

    private void updateUnrealizedSummaries() {
        BigDecimal paperTotal = BigDecimal.ZERO;
        BigDecimal liveTotal = BigDecimal.ZERO;
        BigDecimal paperRealized = aggregatePnlStore.archivedRealized(StrategyMode.PAPER);
        BigDecimal liveRealized = aggregatePnlStore.archivedRealized(StrategyMode.LIVE);
        for (ManagedStrategy strategy : strategies) {
            BigDecimal unrealized = tradingApi == null
                    ? BigDecimal.ZERO
                    : strategy.cachedPosition().unrealizedPnl();
            BigDecimal realized = realizedPnlForStrategy(strategy.strategy.id());
            if (strategy.strategy.mode() == StrategyMode.PAPER) {
                paperTotal = paperTotal.add(unrealized);
                paperRealized = paperRealized.add(realized);
            } else {
                liveTotal = liveTotal.add(unrealized);
                liveRealized = liveRealized.add(realized);
            }
        }
        paperUnrealizedSummary.setText("Paper P&L (Unrealized/Realized): "
                + Monetary.round(paperTotal).toPlainString()
                + " / "
                + Monetary.round(paperRealized).toPlainString());
        liveUnrealizedSummary.setText("Live P&L (Unrealized/Realized): "
                + Monetary.round(liveTotal).toPlainString()
                + " / "
                + Monetary.round(liveRealized).toPlainString());
        applyHeaderTotalsVisibility();
    }

    private BigDecimal realizedPnlForStrategy(String strategyId) {
        List<StrategyOrder> filledOrders = strategyOrderRepository.findByStrategyId(strategyId).stream()
                .filter(order -> order.status() == StrategyOrderStatus.FILLED || order.status() == StrategyOrderStatus.PARTIALLY_FILLED)
                .filter(order -> order.filledQuantity().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator
                        .comparing(StrategyOrder::filledAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(StrategyOrder::submittedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        BigDecimal positionQty = BigDecimal.ZERO;
        BigDecimal averageCost = BigDecimal.ZERO;
        BigDecimal realized = BigDecimal.ZERO;

        for (StrategyOrder order : filledOrders) {
            BigDecimal quantity = order.filledQuantity();
            BigDecimal fillPrice = order.filledAveragePrice().compareTo(BigDecimal.ZERO) > 0
                    ? order.filledAveragePrice()
                    : order.limitPrice();

            if (order.side() == StrategyOrderSide.BUY) {
                BigDecimal runningCost = averageCost.multiply(positionQty).add(fillPrice.multiply(quantity));
                positionQty = positionQty.add(quantity);
                if (positionQty.compareTo(BigDecimal.ZERO) > 0) {
                    averageCost = runningCost.divide(positionQty, 8, java.math.RoundingMode.HALF_UP);
                }
                continue;
            }

            BigDecimal sellQty = quantity.min(positionQty.max(BigDecimal.ZERO));
            if (sellQty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            realized = realized.add(fillPrice.subtract(averageCost).multiply(sellQty));
            positionQty = positionQty.subtract(sellQty);
            if (positionQty.compareTo(BigDecimal.ZERO) == 0) {
                averageCost = BigDecimal.ZERO;
            }
        }

        return Monetary.round(realized);
    }

    private void applyHeaderTotalsVisibility() {
        liveUnrealizedSummary.setVisible(true);
        headerTotalsSeparator.setVisible(true);
    }

    private boolean updateSelectedStrategy() {
        int viewRow = strategyTable.getSelectedRow();
        if (viewRow < 0) {
            return false;
        }
        int modelRow = strategyTable.convertRowIndexToModel(viewRow);
        if (modelRow >= 0 && modelRow < strategies.size()) {
            String newId = strategies.get(modelRow).strategy.id();
            boolean changed = selectedStrategyId == null || !selectedStrategyId.equals(newId);
            selectedStrategyId = newId;
            return changed;
        }
        return false;
    }

    private void restoreSelectedRow() {
        if (selectedStrategyId == null || strategies.isEmpty()) {
            return;
        }
        preservingSelection = true;
        int modelRow = -1;
        for (int i = 0; i < strategies.size(); i++) {
            if (strategies.get(i).strategy.id().equals(selectedStrategyId)) {
                modelRow = i;
                break;
            }
        }
        if (modelRow < 0) {
            selectedStrategyId = null;
            strategyTable.clearSelection();
            preservingSelection = false;
            return;
        }
        int viewRow = safeConvertModelRowToView(modelRow);
        if (viewRow >= 0) {
            if (strategyTable.getSelectedRow() != viewRow) {
                strategyTable.setRowSelectionInterval(viewRow, viewRow);
            }
        } else if (strategyTable.getRowCount() > 0) {
            strategyTable.setRowSelectionInterval(0, 0);
            int firstModelRow = strategyTable.convertRowIndexToModel(0);
            if (firstModelRow >= 0 && firstModelRow < strategies.size()) {
                selectedStrategyId = strategies.get(firstModelRow).strategy.id();
            }
        } else {
            selectedStrategyId = null;
            strategyTable.clearSelection();
        }
        preservingSelection = false;
    }

    private int safeConvertModelRowToView(int modelRow) {
        if (modelRow < 0 || modelRow >= strategyTableModel.getRowCount()) {
            return -1;
        }
        RowSorter<?> sorter = strategyTable.getRowSorter();
        if (sorter != null) {
            try {
                if (modelRow >= sorter.getModelRowCount()) {
                    return -1;
                }
            } catch (RuntimeException ignored) {
                return -1;
            }
        }
        try {
            return strategyTable.convertRowIndexToView(modelRow);
        } catch (RuntimeException ignored) {
            return -1;
        }
    }

    private void rememberSelectedStrategy() {
        int viewRow = strategyTable.getSelectedRow();
        if (viewRow < 0 || strategyTable.isEditing()) {
            return;
        }
        int modelRow = strategyTable.convertRowIndexToModel(viewRow);
        if (modelRow >= 0 && modelRow < strategies.size()) {
            selectedStrategyId = strategies.get(modelRow).strategy.id();
        }
    }

    private ManagedStrategy selectedManagedStrategy() {
        if (selectedStrategyId == null) {
            return null;
        }
        return strategies.stream()
                .filter(entry -> entry.strategy.id().equals(selectedStrategyId))
                .findFirst()
                .orElse(null);
    }

    private void refreshStrategyTableData() {
        rememberSelectedStrategy();
        preservingSelection = true;
        strategyTableModel.fireTableDataChanged();
        refreshFilledOrdersTableData();
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
            strategyTableModel.fireTableDataChanged();
            refreshFilledOrdersTableData();
            strategyTable.clearSelection();
            selectedStrategyId = null;
            return;
        }
        // Row count can change between polls; full refresh keeps sorter/model indexes consistent.
        rememberSelectedStrategy();
        preservingSelection = true;
        strategyTableModel.fireTableDataChanged();
        refreshFilledOrdersTableData();
        preservingSelection = false;
        SwingUtilities.invokeLater(() -> {
            restoreSelectedRow();
            strategyTable.repaint();
            filledOrdersTable.repaint();
        });
    }

    private boolean includeInCurrentStrategiesTab(ManagedStrategy entry) {
        if (entry == null || entry.strategy == null) {
            return false;
        }
        if (entry.strategy.status() == StrategyStatus.ARCHIVED || entry.strategy.status() == StrategyStatus.STOPPED) {
            return false;
        }
        // Keep showing rows that still have live exposure on the broker side.
        return entry.strategy.status() == StrategyStatus.ACTIVE
                || isWaitingForFill(entry.strategy)
                || entry.cachedPosition().getTotalShares() > 0;
    }

    private boolean includeInBrokerSnapshotRefresh(Strategy strategy) {
        return BrokerSnapshotRefreshPolicy.eligibleForBrokerSnapshot(strategy);
    }

    private boolean hasStrategiesNeedingBrokerSnapshots(List<Strategy> stored) {
        if (stored == null || stored.isEmpty()) {
            return false;
        }
        for (Strategy strategy : stored) {
            if (includeInBrokerSnapshotRefresh(strategy)) {
                return true;
            }
        }
        return false;
    }

    private void refreshFilledOrdersTableData() {
        List<HistoryTablePresenter.HistorySource> sources = new ArrayList<>();
        for (ManagedStrategy entry : strategies) {
            List<StrategyOrder> orders = strategyOrderRepository.findByStrategyId(entry.strategy.id());
            sources.add(new HistoryTablePresenter.HistorySource(
                    entry.strategy.symbol(),
                    gridBrokerModeLabel(entry.strategy),
                    displayStatusLabel(entry.strategy),
                    entry.strategy.currentState() == null ? "-" : formatLifecycleStateForDisplay(entry.strategy.currentState()),
                    entry.strategy.latestOrderStatus(),
                    entry.strategy.lastPolledAt(),
                    entry.strategy.status(),
                    orders
            ));
        }
        filledOrderRows.clear();
        filledOrderRows.addAll(historyTablePresenter.buildRows(sources, this::formatTimestampForDisplay));
        filledOrdersTableModel.fireTableDataChanged();
    }

    private ManagedStrategy findStrategy(String symbol) {
        return findStrategy(symbol, null, true);
    }

    private ManagedStrategy findStrategy(String symbol, StrategyMode mode, boolean includeArchived) {
        for (ManagedStrategy strategy : strategies) {
            if (!strategy.strategy.symbol().equalsIgnoreCase(symbol)) {
                continue;
            }
            if (mode != null && strategy.strategy.mode() != mode) {
                continue;
            }
            if (!includeArchived && strategy.strategy.status() == StrategyStatus.ARCHIVED) {
                continue;
            }
                return strategy;
        }
        return null;
    }

    private void syncStrategiesFromRepository() {
        List<Strategy> stored = strategyRepository.findAll();
        syncStrategies(stored);
    }

    private void syncStrategies(List<Strategy> stored) {
        // Remove accidental duplicate in-memory entries first (same persisted strategy id).
        java.util.HashSet<String> seenIds = new java.util.HashSet<>();
        strategies.removeIf(entry -> !seenIds.add(entry.strategy.id()));
        for (Strategy strategy : stored) {
            ManagedStrategy existing = strategies.stream()
                    .filter(entry -> entry.strategy.id().equals(strategy.id()))
                    .findFirst()
                    .orElse(null);
            if (existing == null) {
                strategies.add(new ManagedStrategy(strategy));
            } else {
                existing.syncFrom(strategy);
            }
        }
        strategies.removeIf(entry -> stored.stream().noneMatch(strategy -> strategy.id().equals(entry.strategy.id())));
        for (ManagedStrategy entry : strategies) {
            resetPollingCountdown(entry);
        }
    }


    private void resetPollingCountdown(ManagedStrategy entry) {
        entry.pollIntervalMillis = Math.max(1L, entry.strategy.pollingIntervalSeconds()) * 1000L;
        if (entry.pollInFlight) {
            return;
        }
        if (shouldShowPollingIndicator(entry)) {
            long baseTime = entry.strategy.lastPolledAt() == null
                    ? System.currentTimeMillis()
                    : entry.strategy.lastPolledAt().toEpochMilli();
            long derivedNextDueAtMillis = baseTime + entry.pollIntervalMillis;
            if (!entry.countdownActive || entry.nextPollDueAtMillis <= 0L) {
                entry.countdownActive = true;
                entry.nextPollDueAtMillis = derivedNextDueAtMillis;
                return;
            }
            if (entry.strategy.lastPolledAt() != null
                    && Math.abs(derivedNextDueAtMillis - entry.nextPollDueAtMillis) > 500L) {
                entry.nextPollDueAtMillis = derivedNextDueAtMillis;
            }
        } else {
            entry.countdownActive = false;
            entry.nextPollDueAtMillis = 0L;
        }
    }

    private void startPollingCountdown(ManagedStrategy entry) {
        resetPollingCountdown(entry);
        entry.countdownActive = true;
        entry.nextPollDueAtMillis = System.currentTimeMillis() + entry.pollIntervalMillis;
    }

    private void markPollingCycleCompleted(ManagedStrategy entry) {
        entry.pollIntervalMillis = Math.max(1L, entry.strategy.pollingIntervalSeconds()) * 1000L;
        entry.countdownActive = true;
        entry.nextPollDueAtMillis = System.currentTimeMillis() + entry.pollIntervalMillis;
    }

    private void stopPollingCountdown(ManagedStrategy entry) {
        entry.countdownActive = false;
        entry.nextPollDueAtMillis = 0L;
    }

    private void onStrategyPollStarted(String strategyId) {
        ManagedStrategy entry = findStrategyById(strategyId);
        if (entry == null) {
            return;
        }
        entry.pollInFlight = true;
        entry.countdownActive = false;
        refreshStrategyTableRow(strategyId);
    }

    private void onStrategyPollCompleted(String strategyId) {
        ManagedStrategy entry = findStrategyById(strategyId);
        if (entry == null) {
            return;
        }
        entry.pollInFlight = false;
        markPollingCycleCompleted(entry);
        refreshStrategyTableRow(strategyId);
    }

    private void onStrategyPollFailed(String strategyId) {
        ManagedStrategy entry = findStrategyById(strategyId);
        if (entry == null) {
            return;
        }
        entry.pollInFlight = false;
        stopPollingCountdown(entry);
        refreshStrategyTableRow(strategyId);
    }

    private void refreshStrategyTableRow(String strategyId) {
        ManagedStrategy entry = findStrategyById(strategyId);
        if (entry == null) {
            return;
        }
        int modelRow = strategies.indexOf(entry);
        if (modelRow < 0) {
            return;
        }
        strategyTableModel.fireTableRowsUpdated(modelRow, modelRow);
        if (selectedStrategyId != null && selectedStrategyId.equals(strategyId)) {
            refreshPanels();
        }
    }

    private int pollingProgressPercent(ManagedStrategy entry) {
        ensurePollingCountdownScheduled(entry);
        return pollingCellPresenter.pollingProgressPercent(
                shouldShowPollingIndicator(entry),
                entry == null ? 0L : entry.pollIntervalMillis,
                entry == null ? 0L : entry.nextPollDueAtMillis,
                System.currentTimeMillis()
        );
    }

    private long pollingSecondsRemaining(ManagedStrategy entry) {
        ensurePollingCountdownScheduled(entry);
        return pollingCellPresenter.pollingSecondsRemaining(
                shouldShowPollingIndicator(entry),
                entry == null ? 0L : entry.pollIntervalMillis,
                entry == null ? 0L : entry.nextPollDueAtMillis,
                System.currentTimeMillis()
        );
    }

    private boolean shouldShowPollingIndicator(ManagedStrategy entry) {
        return entry != null
                && pollingCellPresenter.shouldShowPollingIndicator(entry.strategy.status(), isWaitingForFill(entry.strategy));
    }

    private void ensurePollingCountdownScheduled(ManagedStrategy entry) {
        if (entry == null || !shouldShowPollingIndicator(entry) || entry.pollIntervalMillis <= 0L) {
            return;
        }
        if (entry.nextPollDueAtMillis <= 0L) {
            entry.countdownActive = true;
            entry.nextPollDueAtMillis = System.currentTimeMillis() + entry.pollIntervalMillis;
        }
    }

    private ManagedStrategy findStrategyById(String strategyId) {
        if (strategyId == null || strategyId.isBlank()) {
            return null;
        }
        for (ManagedStrategy entry : strategies) {
            if (strategyId.equals(entry.strategy.id())) {
                return entry;
            }
        }
        return null;
    }

    private boolean shouldSuppressBrokerBackedRefreshForClosedMarket() {
        if (tradingApi == null || currentBrokerType != BrokerType.ALPACA) {
            return false;
        }
        AppSettingsService.AppSettings settings = appSettingsService.load();
        if (!settings.autoPausePollingWhenMarketClosed()) {
            return false;
        }
        return !marketHoursService.isTradingSessionOpen(settings.extendedHoursTradingEnabled());
    }

    private boolean isAutoPausedForClosedMarket(ManagedStrategy entry) {
        return entry != null
                && shouldSuppressBrokerBackedRefreshForClosedMarket()
                && ((entry.strategy.status() == StrategyStatus.PAUSED
                    && entry.strategy.pauseReason() == PauseReason.AUTO_MARKET_CLOSED)
                    || (entry.strategy.status() == StrategyStatus.ACTIVE
                    && entry.strategy.pauseReason() == PauseReason.MANUAL_MARKET_CLOSED_OVERRIDE));
    }

    private void setStatus(String message, Color color) {
        SwingUtilities.invokeLater(() -> {
            if (message != null && message.startsWith("FAILED")) {
                statusBar.setText("<html>Broker: <b>FAILED</b> Retrying...</html>");
            } else {
                statusBar.setText("Broker: " + message);
            }
            statusBar.setForeground(color == null ? BOTTOM_STATUS_ACCENT : color);
        });
    }

    private String connectionModeStatus(BrokerType brokerType) {
        String mode = settingsDialog.appliedApplicationMode() == ApplicationMode.LIVE ? "Live" : "Paper";
        return "Broker: Alpaca | Mode: " + mode;
    }

    private String gridBrokerModeLabel(Strategy strategy) {
        if (strategy == null) {
            return "Alpaca";
        }
        return strategy.mode() == StrategyMode.LIVE ? "Alpaca Live" : "Alpaca Paper";
    }

    private Color gridBrokerModeColor(Strategy strategy) {
        return strategy != null && strategy.mode() == StrategyMode.LIVE
                ? MODE_TEXT_ALPACA_LIVE
                : MODE_TEXT_ALPACA_PAPER;
    }

    private void updateStatusBar() {
        long running = strategies.stream().filter(s -> s.strategy.status() == StrategyStatus.ACTIVE).count();
        long inactive = Math.max(0L, strategies.size() - running);
        AppSettingsService.AppSettings settings = appSettingsService.load();
        boolean regularMarketOpen = marketHoursService.isRegularMarketHours();
        boolean tradingSessionOpen = marketHoursService.isTradingSessionOpen(settings.extendedHoursTradingEnabled());
        MarketStatusPresenter.MarketStatusViewModel marketStatusViewModel = marketStatusPresenter.present(
                settings,
                regularMarketOpen,
                tradingSessionOpen,
                Instant.now(),
                marketHoursService.nextMarketOpen(settings.extendedHoursTradingEnabled())
        );
        String cpuText = formatCpuUsageText();
        String memoryText = formatMemoryUsageText();
        String marketValueText = formatMarketValueText();
        StrategyPollingService.PollCycleSnapshot pollSnapshot = strategyPollingService == null
                ? null
                : strategyPollingService.lastPollCycleSnapshot();
        StatusBarPresenter.StatusBarViewModel statusBarViewModel = statusBarPresenter.present(
                new StatusBarPresenter.StatusBarState(
                        running,
                        inactive,
                        pollSnapshot != null && pollSnapshot.cycleEvaluated(),
                        pollSnapshot != null && pollSnapshot.marketClosedSuppressed(),
                        pollSnapshot == null ? 0 : pollSnapshot.due(),
                        pollSnapshot == null ? 0 : pollSnapshot.skippedNotDue(),
                        connectionRetryPending,
                        connectionOk,
                        marketStatusViewModel.label(),
                        marketStatusViewModel.tooltip(),
                        marketStatusViewModel.openForUi(),
                        marketValueText,
                        cpuText,
                        memoryText
                )
        );
        SwingUtilities.invokeLater(() -> {
            statusStrategyCount.setText(statusBarViewModel.strategyCountText());
            pollingSummary.setText(statusBarViewModel.pollingText());
            pollingSummary.setForeground(statusToneColor(statusBarViewModel.pollingTone()));
            marketStatus.setText(statusBarViewModel.marketText());
            marketStatus.setForeground(statusToneColor(statusBarViewModel.marketTone()));
            marketStatus.setToolTipText(TooltipStyler.text(statusBarViewModel.marketTooltip()));
            marketValueStatus.setText(statusBarViewModel.marketValueText());
            cpuUsageStatus.setText(statusBarViewModel.cpuText());
            memoryUsageStatus.setText(statusBarViewModel.memoryText());
            statusBar.setText(statusBarViewModel.brokerText());
            statusBar.setForeground(statusToneColor(statusBarViewModel.brokerTone()));
        });
    }

    private Color statusToneColor(StatusBarPresenter.Tone tone) {
        return switch (tone) {
            case OK -> STATUS_OK;
            case WARN -> STATUS_WARN;
            case ERR -> STATUS_ERR;
            case MUTED -> new Color(120, 120, 120);
            case DEFAULT -> BOTTOM_STATUS_ACCENT;
        };
    }


    private String formatMarketValueText() {
        return systemMetricsPresenter.formatMarketValueText(strategies);
    }

    private String formatCpuUsageText() {
        return systemMetricsPresenter.formatCpuUsageText();
    }

    private String formatMemoryUsageText() {
        return systemMetricsPresenter.formatMemoryUsageText();
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
                    new AnalyticsQueue(AppMetadata.appDataDirectory().resolve("analytics-queue.log")));
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
        stopTradingEventStream();
        connectionRetryTimer.stop();
        logFlushTimer.stop();
        pollingIndicatorTimer.stop();
        strategyPollingTimer.stop();
        uiPollingExecutor.shutdownNow();
        if (strategyPollingService != null) {
            strategyPollingService.shutdown();
        }
        flushLogsToFile();
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
            if (strategy.strategy.status() == StrategyStatus.ACTIVE) {
                strategyService.pause(strategy.strategy.id());
                stopPollingCountdown(strategy);
                log("[" + strategy.strategy.symbol() + "] EMERGENCY STOP");
                stoppedCount++;
            }
        }

        syncStrategiesFromRepository();
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
        boolean blinkLiveAlpaca = effectiveBroker == BrokerType.ALPACA && settingsDialog.appliedApplicationMode() == ApplicationMode.LIVE;
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
        if (settingsDialog.appliedApplicationMode() != ApplicationMode.LIVE) {
            headerStatus.setForeground(HEADER_STATUS_DEFAULT);
            liveModeBlinkTimer.stop();
            return;
        }
        liveBlinkPrimaryActive = !liveBlinkPrimaryActive;
        headerStatus.setForeground(liveBlinkPrimaryActive ? liveBlinkPrimary : liveBlinkSecondary);
    }

    private boolean hasAnyRealTradingStrategy() {
        return strategies.stream().anyMatch(s -> s.strategy.mode() == StrategyMode.LIVE);
    }

    private Position displayedPosition(ManagedStrategy entry) {
        return entry == null ? new Position("") : entry.cachedPosition();
    }

    private Position loadPositionForStrategy(Strategy strategy) {
        if (strategy == null) {
            return new Position("");
        }
        HttpAlpacaClient client = alpacaClientForStrategyMode(strategy.mode());
        if (client == null) {
            return new Position(strategy.symbol());
        }
        Optional<com.neuralarc.api.AlpacaPositionData> remote = client.getPosition(strategy.symbol());
        Position position = new Position(strategy.symbol());
        if (remote.isPresent() && remote.get().exists()) {
            com.neuralarc.api.AlpacaPositionData remotePosition = remote.get();
            int quantity = remotePosition.quantity().setScale(0, java.math.RoundingMode.DOWN).intValue();
            if (quantity > 0) {
                position.applyBuy(quantity, remotePosition.avgEntryPrice());
                position.setLastPrice(remotePosition.marketPrice());
            }
        } else {
            BigDecimal latestPrice = client.getLatestPrice(strategy.symbol());
            if (latestPrice.compareTo(BigDecimal.ZERO) > 0) {
                position.setLastPrice(latestPrice);
            }
        }
        return position;
    }

    private HttpAlpacaClient alpacaClientForStrategyMode(StrategyMode mode) {
        ApplicationMode applicationMode = mode == StrategyMode.LIVE ? ApplicationMode.LIVE : ApplicationMode.PAPER;
        return alpacaClientForMode(applicationMode);
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
        trimEventLog(document);
        eventLog.setCaretPosition(document.getLength());
    }

    private void trimEventLog(StyledDocument document) {
        javax.swing.text.Element root = document.getDefaultRootElement();
        while (root.getElementCount() > MAX_EVENT_LOG_LINES) {
            javax.swing.text.Element firstLine = root.getElement(0);
            if (firstLine == null) {
                break;
            }
            int removeLength = firstLine.getEndOffset();
            try {
                document.remove(0, removeLength);
                logLineCount = Math.max(0, logLineCount - 1);
            } catch (BadLocationException e) {
                break;
            }
            root = document.getDefaultRootElement();
        }
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


    private final class HistoryRowRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setOpaque(true);
            int modelRow = table.convertRowIndexToModel(row);
            if (modelRow < 0 || modelRow >= filledOrderRows.size()) {
                return this;
            }
            HistoryTablePresenter.HistoryRow rowData = filledOrderRows.get(modelRow);
            HistoryRowStyler.CellStyle cellStyle = historyRowStyler.style(
                    table,
                    row,
                    column,
                    isSelected,
                    rowData,
                    filledOrderRows,
                    new HistoryRowStyler.Palette(
                            TABLE_SELECTION_BG,
                            TABLE_SELECTION_FG,
                            HISTORY_GROUP_BORDER,
                            HISTORY_BUY_BG,
                            HISTORY_BUY_FG,
                            HISTORY_SELL_GAIN_BG,
                            HISTORY_SELL_GAIN_FG,
                            HISTORY_SELL_LOSS_BG,
                            HISTORY_SELL_LOSS_FG,
                            HISTORY_SELL_FLAT_BG,
                            HISTORY_SELL_FLAT_FG,
                            HISTORY_FAILED_BG,
                            HISTORY_FAILED_FG,
                            HISTORY_COMPLETED_BG,
                            HISTORY_COMPLETED_FG,
                            HISTORY_SUBTOTAL_BG,
                            HISTORY_SUBTOTAL_FG
                    )
            );
            setBackground(cellStyle.background());
            setForeground(cellStyle.foreground());
            setHorizontalAlignment(cellStyle.horizontalAlignment());
            setBorder(cellStyle.border());
            int fontStyle = (cellStyle.bold() ? Font.BOLD : Font.PLAIN) | (cellStyle.italic() ? Font.ITALIC : Font.PLAIN);
            setFont(getFont().deriveFont(fontStyle));
            if (cellStyle.blankText()) {
                setText("");
            }
            return this;
        }
    }


    private final class StatusRowRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            int modelRow = table.convertRowIndexToModel(row);
            if (modelRow >= 0 && modelRow < strategies.size()) {
                boolean paused = strategies.get(modelRow).isPaused();
                if (isSelected) {
                    setBackground(TABLE_SELECTION_BG);
                    setForeground(TABLE_SELECTION_FG);
                } else {
                    setBackground(table.getBackground());
                    if (column == 1) {
                        if (strategies.get(modelRow).strategy.status() == StrategyStatus.ARCHIVED) {
                            setForeground(new Color(108, 117, 125));
                        } else if (strategies.get(modelRow).strategy.pauseReason() == PauseReason.SYSTEM_ERROR) {
                            setForeground(STATUS_ERR);
                        } else {
                            setForeground(paused ? STATUS_TEXT_PAUSED : STATUS_TEXT_RUNNING);
                        }
                    } else if (column == 8) {
                        setForeground(gridBrokerModeColor(strategies.get(modelRow).strategy));
                    } else {
                        setForeground(table.getForeground());
                    }
                }
            }
            setOpaque(true);
            setHorizontalAlignment(alignmentForColumn(column));
            return this;
        }

        private int alignmentForColumn(int column) {
            return switch (column) {
                case 1 -> CENTER;
                default -> LEFT;
            };
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
            progressBar.setOpaque(false);
            progressBar.setBorder(BorderFactory.createEmptyBorder());
            progressBar.setStringPainted(false);
            // Fixed thin size — the wrapper panel enforces this height.
            progressBar.setPreferredSize(new Dimension(94, 8));
            progressBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 8));
            progressBar.setComponentOrientation(java.awt.ComponentOrientation.RIGHT_TO_LEFT);
            progressBar.setForeground(new Color(66, 133, 244));
            // Custom UI: pill-shaped fill, no visible track background.
            progressBar.setUI(new BasicProgressBarUI() {
                @Override
                public void paint(Graphics g, JComponent c) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    int w = c.getWidth();
                    int h = c.getHeight();
                    int arc = h; // fully rounded pill ends
                    int filled = (int) Math.round(w * progressBar.getPercentComplete());
                    // Optional faint track pill
                    Color trackColor = progressBar.getBackground();
                    if (trackColor != null && trackColor.getAlpha() > 0) {
                        g2.setColor(trackColor);
                        g2.fillRoundRect(0, 0, w, h, arc, arc);
                    }
                    // Filled portion, clipped to pill shape
                    if (filled > 0) {
                        g2.setColor(progressBar.getForeground());
                        g2.setClip(new java.awt.geom.RoundRectangle2D.Float(0, 0, w, h, arc, arc));
                        g2.fillRect(0, 0, filled, h);
                    }
                    g2.dispose();
                }
            });
            // Wrap bar in a GridBagLayout panel so it stays vertically centred at 6 px
            // regardless of how tall the containing row is.
            JPanel barWrapper = new JPanel(new GridBagLayout());
            barWrapper.setOpaque(false);
            barWrapper.setPreferredSize(new Dimension(96, 0)); // fixed width, height from parent
            barWrapper.add(progressBar, new GridBagConstraints());
            add(barWrapper, BorderLayout.WEST);
            add(countdownLabel, BorderLayout.CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            int modelRow = table.convertRowIndexToModel(row);
            ManagedStrategy strategy = strategies.get(modelRow);
            PollingCellPresenter.PollingCellViewModel viewModel = pollingCellPresenter.present(
                    new PollingCellPresenter.PollingCellState(
                            strategy.strategy.status(),
                            isWaitingForFill(strategy.strategy),
                            strategy.isPaused(),
                            strategy.pauseLabel(),
                            strategy.pauseTooltip(),
                            strategy.pollInFlight,
                            isAutoPausedForClosedMarket(strategy) && shouldSuppressBrokerBackedRefreshForClosedMarket(),
                            strategy.pollIntervalMillis,
                            strategy.nextPollDueAtMillis,
                            strategy.strategy.pollingIntervalSeconds(),
                            isSelected
                    ),
                    new PollingCellPresenter.PollingCellPalette(
                            table.getBackground(),
                            table.getForeground(),
                            TABLE_SELECTION_BG,
                            TABLE_SELECTION_FG,
                            TABLE_SELECTION_BAR_BG,
                            new Color(76, 96, 120, 70),
                            new Color(124, 246, 196),
                            STATUS_TEXT_PAUSED,
                            new Color(60, 30, 140),
                            new Color(66, 133, 244)
                    ),
                    System.currentTimeMillis()
            );

            setBackground(viewModel.rowBackground());
            progressBar.setValue(viewModel.progress());
            progressBar.setBackground(viewModel.trackBackground());
            progressBar.setForeground(viewModel.progressForeground());
            countdownLabel.setForeground(viewModel.labelForeground());
            countdownLabel.setText(viewModel.labelText());
            String tooltipText = TooltipStyler.text(viewModel.tooltip());
            setToolTipText(tooltipText);
            progressBar.setToolTipText(tooltipText);
            countdownLabel.setToolTipText(tooltipText);
            return this;
        }
    }

    private final class ActionsRenderer extends JPanel implements TableCellRenderer {
        private final JButton editButton = new JButton("Edit");
        private final JButton toggleButton = new JButton();
        private final JButton sellButton = new JButton("Sell");
        private final JButton promoteButton = new JButton("Promote to Live");
        private final JButton deleteButton = new JButton("Delete");

        private ActionsRenderer() {
            super(new GridLayout(1, 5, 6, 0));
            setOpaque(true);
            applyButtonIcon(editButton, "icons/edit.svg", 13);
            applyButtonIcon(toggleButton, "icons/pause.svg", 13);
            applyButtonIcon(sellButton, "icons/submit.svg", 13);
            applyButtonIcon(promoteButton, "icons/add-stock-strategy.svg", 13);
            applyButtonIcon(deleteButton, "icons/delete.svg", 13);
            styleActionButton(editButton, new Color(63, 81, 181));
            styleActionButton(toggleButton, new Color(198, 40, 40));
            styleActionButton(sellButton, new Color(230, 81, 0));
            styleActionButton(promoteButton, new Color(25, 118, 210));
            styleActionButton(deleteButton, new Color(156, 39, 39));
            add(editButton);
            add(toggleButton);
            add(sellButton);
            add(promoteButton);
            add(deleteButton);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            int modelRow = table.convertRowIndexToModel(row);
            ManagedStrategy strategy = strategies.get(modelRow);
            StrategyActionsPresenter.StrategyActionsViewModel actionsViewModel = strategyActionsPresenter.present(
                    new StrategyActionsPresenter.StrategyActionsState(
                            strategy.strategy.status() == StrategyStatus.ARCHIVED,
                            strategy.isPaused(),
                            strategy.isPauseResumeBusy(),
                            strategy.pauseResumeBusyText(),
                            strategy.strategy.mode() == StrategyMode.PAPER,
                            strategy.cachedPosition().getTotalShares() > 0
                    )
            );
            toggleButton.setText(actionsViewModel.toggleText());
            styleActionButton(toggleButton, actionsViewModel.toggleColor());
            toggleButton.setEnabled(actionsViewModel.toggleEnabled());
            sellButton.setEnabled(actionsViewModel.sellEnabled());
            styleActionButton(sellButton, actionsViewModel.sellColor());
            promoteButton.setEnabled(actionsViewModel.promoteEnabled());
            styleActionButton(promoteButton, actionsViewModel.promoteColor());
            setBackground(selectionAwareRowColor(isSelected, table));
            return this;
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
        button.setHorizontalTextPosition(SwingConstants.RIGHT);
        button.setVerticalTextPosition(SwingConstants.CENTER);
        button.setRolloverEnabled(true);
        button.setForeground(Color.WHITE);
        button.setIconTextGap(6);
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

    private void applyButtonIcon(JButton button, String resourcePath, int size) {
        button.setIcon(SvgIconLoader.load(resourcePath, size));
        button.setHorizontalTextPosition(SwingConstants.RIGHT);
        button.setVerticalTextPosition(SwingConstants.CENTER);
    }

    private void startTradingEventStreamIfConfigured(String apiKey, String apiSecret) {
        tradeStreamLifecycleCoordinator.start(
                apiKey,
                apiSecret,
                settingsDialog.appliedApplicationMode() == ApplicationMode.LIVE
        );
    }

    private void stopTradingEventStream() {
        tradeStreamLifecycleCoordinator.stop();
    }


    private void refreshDisplayedPositionFromStream(String symbol) {
        if (tradingApi == null || symbol == null || symbol.isBlank()) {
            return;
        }
        // Request one immediate batch refresh on the next polling tick.
        batchGridPriceRefreshRequestedFromStream = true;
        ManagedStrategy entry = findStrategy(symbol);
        if (entry == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            refreshStrategyTableContent();
            if (selectedStrategyId != null && selectedStrategyId.equals(entry.strategy.id())) {
                refreshPanels();
            }
        });
    }

    private void updateStreamStatus(String status, Color color) {
        SwingUtilities.invokeLater(() -> {
            String normalized = status == null || status.isBlank() ? "idle" : status;
            streamStatus.setText("Trade Stream: " + normalized);
            streamStatus.setForeground(color == null ? BOTTOM_STATUS_ACCENT : color);
        });
    }

    private boolean ensureLegalDisclosureAccepted() {
        if (legalDisclosureAccepted) {
            return true;
        }
        boolean accepted = showLegalDisclosureDialog(true);
        if (!accepted) {
            JOptionPane.showMessageDialog(this,
                    "You must accept the Legal Disclosure before adding stock strategies.",
                    "Disclosure Required",
                    JOptionPane.WARNING_MESSAGE);
        }
        return accepted;
    }

    private boolean showLegalDisclosureDialog(boolean requireAcceptance) {
        return legalDisclosureController.showDisclosure(this, legalDisclosureAccepted, requireAcceptance, accepted -> {
            legalDisclosureAccepted = accepted;
            updateLegalDisclosureUiState();
        });
    }

    private void updateLegalDisclosureUiState() {
        legalDisclosureButton.setForeground(legalDisclosureAccepted
                ? new Color(220, 255, 220)
                : new Color(255, 235, 190));
    }

    private record PortfolioSellBatchResult(List<String> successes, List<String> failures) {
    }

    private enum PortfolioSellScope {
        PROFITABLE("Sell Profitable Positions") {
            @Override
            boolean matches(ManagedStrategy entry) {
                Position position = entry.cachedPosition();
                return position.getTotalShares() > 0 && position.unrealizedPnl().compareTo(BigDecimal.ZERO) > 0;
            }

            @Override
            String confirmHeading(int count) {
                return "Sell " + count + " profitable position(s)?";
            }

            @Override
            String emptyMessage() {
                return "There are no profitable open positions to sell.";
            }
        },
        ALL_OPEN("Sell All Open Positions") {
            @Override
            boolean matches(ManagedStrategy entry) {
                return entry.cachedPosition().getTotalShares() > 0;
            }

            @Override
            String confirmHeading(int count) {
                return "Sell all " + count + " open position(s)?";
            }

            @Override
            String emptyMessage() {
                return "There are no open positions to sell.";
            }
        },
        LOSS_ONLY("Sell Losing Positions") {
            @Override
            boolean matches(ManagedStrategy entry) {
                Position position = entry.cachedPosition();
                return position.getTotalShares() > 0 && position.unrealizedPnl().compareTo(BigDecimal.ZERO) < 0;
            }

            @Override
            String confirmHeading(int count) {
                return "Sell " + count + " losing position(s)?";
            }

            @Override
            String emptyMessage() {
                return "There are no losing open positions to sell.";
            }
        };

        private final String menuLabel;

        PortfolioSellScope(String menuLabel) {
            this.menuLabel = menuLabel;
        }

        abstract boolean matches(ManagedStrategy entry);

        abstract String confirmHeading(int count);

        abstract String emptyMessage();

        String menuLabel() {
            return menuLabel;
        }

        String dialogTitle() {
            return menuLabel;
        }

        String logPrefix() {
            return "[" + menuLabel + "]";
        }
    }
}
