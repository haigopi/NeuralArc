package com.neuralarc.ui;

import com.neuralarc.analytics.AnalyticsEvent;
import com.neuralarc.analytics.AnalyticsPublisher;
import com.neuralarc.analytics.AnalyticsQueue;
import com.neuralarc.analytics.HttpAnalyticsPublisher;
import com.neuralarc.analytics.LossHarvesting;
import com.neuralarc.analytics.LossRecoveryPlan;
import com.neuralarc.analytics.TelemetryConfig;
import com.neuralarc.analytics.WorkspaceAccounting;
import com.neuralarc.api.AlpacaPositionData;
import com.neuralarc.api.AlpacaTradeUpdateEvent;
import com.neuralarc.api.HttpAlpacaClient;
import com.neuralarc.api.HttpAlpacaMarketDataApi;
import com.neuralarc.api.TradingApi;
import com.neuralarc.db.AppDatabase;
import com.neuralarc.db.SqliteRemoteSyncSuppressionRepository;
import com.neuralarc.db.SqliteScanHistoryRepository;
import com.neuralarc.db.SqliteStrategyExecutionEventRepository;
import com.neuralarc.db.SqliteStrategyOrderRepository;
import com.neuralarc.db.SqliteStrategyRepository;
import com.neuralarc.db.SqliteWorkspaceRepository;
import com.neuralarc.diphunter.DipHunterAnalysisDialog;
import com.neuralarc.diphunter.DipHunterConfig;
import com.neuralarc.diphunter.DipHunterPanel;
import com.neuralarc.earningshunter.EarningsHunterAnalysisDialog;
import com.neuralarc.earningshunter.EarningsHunterConfig;
import com.neuralarc.earningshunter.EarningsHunterPanel;
import com.neuralarc.gaprocket.GapRocketAnalysisDialog;
import com.neuralarc.gaprocket.GapRocketConfig;
import com.neuralarc.gaprocket.GapRocketPanel;
import com.neuralarc.model.ApplicationMode;
import com.neuralarc.model.BrokerType;
import com.neuralarc.model.DipHunterSchedule;
import com.neuralarc.model.GapAndGoSchedule;
import com.neuralarc.model.MarketBar;
import com.neuralarc.model.OrbSchedule;
import com.neuralarc.model.PauseReason;
import com.neuralarc.model.Position;
import com.neuralarc.model.ProfitControlMode;
import com.neuralarc.model.ProfitHoldType;
import com.neuralarc.model.ProfitShieldSchedule;
import com.neuralarc.model.RangeRiderSchedule;
import com.neuralarc.model.RecommendationType;
import com.neuralarc.model.RepositionSubmissionType;
import com.neuralarc.model.ScanHistoryEntry;
import com.neuralarc.model.SellSubmissionType;
import com.neuralarc.model.SmartPicksSchedule;
import com.neuralarc.model.SmartPicksSimulationSelection;
import com.neuralarc.model.StopLossType;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyConfig;
import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyOrder;
import com.neuralarc.model.StrategyOrderSide;
import com.neuralarc.model.StrategyOrderStatus;
import com.neuralarc.model.StrategyOrderType;
import com.neuralarc.model.StrategyRecommendation;
import com.neuralarc.model.StrategyStage;
import com.neuralarc.model.StrategyStatus;
import com.neuralarc.model.StrategyWorkspace;
import com.neuralarc.model.StrategyWorkspaceTemplate;
import com.neuralarc.model.SwingSchedule;
import com.neuralarc.model.ThresholdType;
import com.neuralarc.model.TimeInForce;
import com.neuralarc.model.TrailingType;
import com.neuralarc.model.TrendingStock;
import com.neuralarc.model.VwapSchedule;
import com.neuralarc.orb.OrbAnalysisDialog;
import com.neuralarc.orb.OrbConfig;
import com.neuralarc.orb.OrbPanel;
import com.neuralarc.profitshield.ProfitShieldAnalysisDialog;
import com.neuralarc.profitshield.ProfitShieldConfig;
import com.neuralarc.profitshield.ProfitShieldPanel;
import com.neuralarc.rangerider.RangeRiderAnalysisDialog;
import com.neuralarc.rangerider.RangeRiderConfig;
import com.neuralarc.rangerider.RangeRiderPanel;
import com.neuralarc.service.AppSettingsService;
import com.neuralarc.service.AsyncLogUploadService;
import com.neuralarc.service.AutoAnalyzeResultStore;
import com.neuralarc.service.AutoRiskAdjustmentService;
import com.neuralarc.service.FeedbackEmailService;
import com.neuralarc.service.GitHubReleaseUpdateService;
import com.neuralarc.service.HttpAlpacaScreenerClient;
import com.neuralarc.service.LogArchiveService;
import com.neuralarc.service.LogUploadStatusStore;
import com.neuralarc.service.MarketHoursService;
import com.neuralarc.service.OnboardingStateStore;
import com.neuralarc.service.PendingBuyOrderGuard;
import com.neuralarc.service.PortfolioEmailScheduleService;
import com.neuralarc.service.PortfolioSnapshotEmailService;
import com.neuralarc.service.ReconciliationService;
import com.neuralarc.service.RotatingLogWriter;
import com.neuralarc.service.SpacesLogUploader;
import com.neuralarc.service.StrategyApplyService;
import com.neuralarc.service.StrategyEngine;
import com.neuralarc.service.StrategyPollingService;
import com.neuralarc.service.StrategyService;
import com.neuralarc.service.TradeEmailNotificationService;
import com.neuralarc.service.TrendingStocksService;
import com.neuralarc.service.UserIdentityService;
import com.neuralarc.service.WorkspaceService;
import com.neuralarc.swing.SwingAnalysisDialog;
import com.neuralarc.swing.SwingConfig;
import com.neuralarc.swing.SwingPanel;
import com.neuralarc.util.AppMetadata;
import com.neuralarc.util.BrokerOrderStatusUtil;
import com.neuralarc.util.FontLoader;
import com.neuralarc.util.Monetary;
import com.neuralarc.util.SvgIconLoader;
import com.neuralarc.util.ThemeColors;
import com.neuralarc.vwap.VwapAnalysisDialog;
import com.neuralarc.vwap.VwapConfig;
import com.neuralarc.vwap.VwapPanel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.ToolTipManager;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicProgressBarUI;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;
import org.json.JSONArray;

public class TradingFrame extends JFrame {

  private static final Font BASE_FONT = createBaseFont();
  private static final int OUTER_PADDING = 16;
  private static final DateTimeFormatter LOG_DATE_FORMAT = DateTimeFormatter.ofPattern("EEE, MMM");
  private static final DateTimeFormatter LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a");
  private static final DateTimeFormatter RULE_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("EEE, MMM d yyyy, h:mm a");
  private static final DateTimeFormatter NEXT_OPEN_FORMAT = DateTimeFormatter.ofPattern("EEE, MMM d yyyy h:mm a z");
  private static final int GRID_SEARCH_MIN_STOCK_COUNT = 9;

  // Wraps onto further lines: a long position summary used to be cut off with an ellipsis.
  private final WrappingText positionSummary = new WrappingText("Position: -", 10f,
      ThemeColors.color("NeuralArc.Detail.foreground", new Color(35, 35, 45)), 420);
  private final JLabel ruleState = new JLabel("Rules: -");
  private PositionBarsColumn positionBarsColumn;
  private final JLabel paperUnrealizedSummary = new JLabel("Paper Unrealized P&L Total: -");
  private final JLabel headerTotalsSeparator = new JLabel("|");
  private final JLabel liveUnrealizedSummary = new JLabel("Live Unrealized P&L Total: -");
  private final JToggleButton paperViewButton = new JToggleButton("Paper");
  private final JToggleButton liveViewButton = new JToggleButton("Live");
  private final JLabel positionSectionTitle = new JLabel("Position");
  private final JLabel rulesSectionTitle = new JLabel("Rules Triggered");
  private final JLabel statusBar = new JLabel("Not connected");
  private final JLabel statusStrategyCount = new JLabel("Strategies 0  Active 0  Inactive 0  History 0");
  private final JLabel pollingSummary = new JLabel("Ready");
  private final JLabel marketStatus = new JLabel("Unknown");
  private final JLabel streamStatus = new JLabel("idle");
  private final JLabel availableFundsStatus = new JLabel("-");
  private final JLabel marketValueStatus = new JLabel("-");
  private final JLabel investedValueStatus = new JLabel("-");
  private final JLabel pendingBuyStatus = new JLabel("0");
  private final JLabel gainingPositionsStatus = new JLabel("0");
  private final JLabel losingPositionsStatus = new JLabel("0");
  private final JLabel pendingSellStatus = new JLabel("0");
  private final JLabel cpuUsageStatus = new JLabel("-");
  private final JLabel memoryUsageStatus = new JLabel("-");
  private final JLabel compactStatusSummary = new JLabel("Broker Not connected   Market Unknown");
  private final JButton statusDetailsButton = new JButton("Details");
  private final JLabel headerStatus = new JLabel("Status: waiting for settings");
  private static final Color STATUS_OK = ThemeColors.color("NeuralArc.statusOk", new Color(34, 139, 34));
  private static final Color STATUS_WARN = ThemeColors.color("NeuralArc.statusWarn", new Color(180, 100, 0));
  private static final Color STATUS_ERR = ThemeColors.color("NeuralArc.statusError", new Color(180, 30, 30));
  private static final Color PAPER_HEADER_BG = new Color(35, 35, 45);
  private static final Color PAPER_STATUS_BG = new Color(35, 35, 45);
  private static final Color LIVE_HEADER_BG = new Color(45, 32, 34);
  private static final Color LIVE_STATUS_BG = new Color(44, 30, 32);
  private static final Color UPDATE_FLASH_BG = new Color(185, 112, 0);
  private static final Color UPDATE_FLASH_BORDER = new Color(255, 184, 65);
  private static final Color CAPTURE_ACTIVE_BG = new Color(210, 52, 38);
  private static final Color CAPTURE_ACTIVE_BG_ALT = new Color(255, 136, 0);
  private static final Color CAPTURE_ACTIVE_BORDER = new Color(255, 187, 74);
  private static final Color CAPTURE_ACTIVE_BORDER_ALT = new Color(255, 96, 80);
  private static final Color CAPTURE_INDICATOR_ACTIVE_TEXT = ThemeColors.color("NeuralArc.captureIndicatorActive", new Color(19, 102, 74));
  private static final Color CAPTURE_INDICATOR_IDLE_TEXT = ThemeColors.color("NeuralArc.captureIndicatorIdle", new Color(86, 92, 104));
  private static final Color TABLE_SELECTION_BG = ThemeColors.color("NeuralArc.Table.selectionBackground", new Color(201, 220, 252));
  private static final Color TABLE_SELECTION_FG = ThemeColors.color("NeuralArc.Table.selectionForeground", new Color(10, 35, 100));
  private static final Color TABLE_SELECTION_BORDER = ThemeColors.color("NeuralArc.Table.selectionBorder", new Color(66, 133, 244)); // left accent stripe on selected row
  private static final Color TABLE_SELECTION_BAR_BG = ThemeColors.color("NeuralArc.Table.selectionBarBackground",
      new Color(170, 198, 245)); // progress-bar unfilled on selected row
  private static final Color TABLE_ROW_BG_EVEN = ThemeColors.color("NeuralArc.Table.rowBackgroundEven", new Color(245, 247, 250));
  private static final Color TABLE_ROW_BG_ODD = ThemeColors.color("NeuralArc.Table.rowBackgroundOdd", new Color(239, 243, 248));
  private static final Color TABLE_OUTER_BORDER_COLOR = ThemeColors.color("NeuralArc.Table.outerBorder", new Color(232, 236, 242));
  private static final Color PNL_POSITIVE_FG = PnlCellStyleSupport.POSITIVE;
  private static final Color PNL_NEGATIVE_FG = PnlCellStyleSupport.NEGATIVE;
  private static final Color STATUS_TEXT_RUNNING = ThemeColors.color("NeuralArc.statusRunning", new Color(46, 125, 50));
  private static final Color STATUS_TEXT_PAUSED = ThemeColors.color("NeuralArc.statusPaused", new Color(180, 100, 0));
  private static final Color MODE_TEXT_ALPACA_PAPER = ThemeColors.color("NeuralArc.modePaper", new Color(25, 118, 210));
  private static final Color MODE_TEXT_ALPACA_LIVE = ThemeColors.color("NeuralArc.modeLive", new Color(183, 28, 28));
  private static final Color BOTTOM_STATUS_ACCENT = new Color(180, 160, 110);
  private static final Color BOTTOM_STATUS_MARKET_VALUE = new Color(108, 201, 168);
  private static final Color HISTORY_BUY_BG = ThemeColors.color("NeuralArc.History.buyBackground", new Color(227, 242, 253));
  private static final Color HISTORY_BUY_FG = ThemeColors.color("NeuralArc.History.buyForeground", new Color(13, 71, 161));
  private static final Color HISTORY_SELL_GAIN_BG = ThemeColors.color("NeuralArc.History.sellGainBackground", new Color(232, 245, 233));
  private static final Color HISTORY_SELL_GAIN_FG = ThemeColors.color("NeuralArc.History.sellGainForeground", new Color(27, 94, 32));
  private static final Color HISTORY_SELL_LOSS_BG = ThemeColors.color("NeuralArc.History.sellLossBackground", new Color(255, 235, 238));
  private static final Color HISTORY_SELL_LOSS_FG = ThemeColors.color("NeuralArc.History.sellLossForeground", new Color(183, 28, 28));
  private static final Color HISTORY_SELL_FLAT_BG = ThemeColors.color("NeuralArc.History.sellFlatBackground", new Color(255, 248, 225));
  private static final Color HISTORY_SELL_FLAT_FG = ThemeColors.color("NeuralArc.History.sellFlatForeground", new Color(111, 79, 0));
  private static final Color HISTORY_FAILED_BG = ThemeColors.color("NeuralArc.History.failedBackground", new Color(255, 243, 224));
  private static final Color HISTORY_FAILED_FG = ThemeColors.color("NeuralArc.History.failedForeground", new Color(140, 80, 0));
  private static final Color HISTORY_COMPLETED_BG = ThemeColors.color("NeuralArc.History.completedBackground", new Color(245, 245, 245));
  private static final Color HISTORY_COMPLETED_FG = ThemeColors.color("NeuralArc.History.completedForeground", new Color(78, 84, 94));
  private static final Color HISTORY_SUBTOTAL_BG = ThemeColors.color("NeuralArc.History.subtotalBackground", new Color(215, 225, 240));
  private static final Color HISTORY_SUBTOTAL_FG = ThemeColors.color("NeuralArc.History.subtotalForeground", new Color(28, 48, 80));
  private static final Color HISTORY_GROUP_BORDER = ThemeColors.color("NeuralArc.History.groupBorder", new Color(173, 181, 189));
  private static final Color LOG_LINE_EVEN = ThemeColors.color("NeuralArc.Log.lineEven", new Color(238, 242, 247));
  private static final Color LOG_LINE_ODD = ThemeColors.color("NeuralArc.Log.lineOdd", new Color(224, 231, 240));
  private static final Color LOG_LINE_PROCESSING = ThemeColors.color("NeuralArc.Log.processing", new Color(147, 197, 253));
  private static final Color LOG_LINE_SUCCESS = ThemeColors.color("NeuralArc.Log.success", new Color(125, 211, 168));
  private static final Color LOG_LINE_WARNING = ThemeColors.color("NeuralArc.Log.warning", new Color(251, 191, 36));
  private static final Color LOG_LINE_FAILURE = ThemeColors.color("NeuralArc.Log.failure", new Color(183, 28, 28));
  private static final Color LOG_LINE_FAILURE_BG = ThemeColors.color("NeuralArc.Log.failureBackground", new Color(255, 245, 157));
  private static final int MAX_EVENT_LOG_LINES = 1500;
  private static final long CLOSED_MARKET_RECONCILE_POLL_INTERVAL_MILLIS = 60L * 1000L;
  private static final long CLOSED_MARKET_POLL_INTERVAL_MILLIS = 10L * 60L * 1000L;
  private static final int STREAM_RECONNECT_BASE_DELAY_MILLIS = 2 * 60 * 1000;
  private static final int STREAM_RECONNECT_MAX_DELAY_MILLIS = 30 * 60 * 1000;
  private static final int STREAM_RECONNECT_RESET_HOUR = 6;
  /** Grid column 0 — see {@link StrategyGridTableModel#COLUMNS}. */
  private static final int STRATEGY_SHARES_COLUMN = 0;
  private static final int STRATEGY_STOCK_PRICE_COLUMN = 6;
  private static final int STRATEGY_PNL_COLUMN = 7;
  private static final int STRATEGY_PNL_PERCENT_COLUMN = 8;
  private static final int STRATEGY_TIF_COLUMN = 12;
  /**
   * Calendar days of history behind a loss-recovery plan: about a month of sessions.
   */
  private static final int LOSS_RECOVERY_LOOKBACK_DAYS = 45;
  private static final long STOCK_PRICE_TOOLTIP_TTL_MILLIS = 30_000L;
  private static final long BROKER_POSITION_SNAPSHOT_TTL_MILLIS = 15_000L;
  /**
   * Today's open never moves and the high/low drift slowly, so one batch call per 30s is ample.
   */
  private static final long DAILY_BAR_SNAPSHOT_TTL_MILLIS = 30_000L;
  /**
   * Gap between polling ticks that indicates the system was suspended (slept).
   */
  private static final long WAKE_GAP_DETECTION_MS = 30_000L;
  private static final Color HEADER_STATUS_DEFAULT = new Color(220, 220, 255);
  private static final Color HEADER_STATUS_LIVE_ALERT = new Color(255, 82, 82);
  private static final Color HEADER_STATUS_LIVE_ALERT_DIM = new Color(255, 205, 210);
  private static final Color HEADER_STATUS_LIVE_ACTIVE = new Color(46, 125, 50);
  private static final Color HEADER_STATUS_LIVE_ACTIVE_DIM = Color.WHITE;
  private final JTextPane eventLog = new JTextPane();
  // Keeps 5,000 recent lines so a filter can reach past the 1,500 shown.
  private final EventLogView eventLogView = new EventLogView(eventLog, MAX_EVENT_LOG_LINES, 5_000,
      this::logEntryColor, EventLogSeverity::isFailure, LOG_LINE_FAILURE_BG);
  private final JButton addStrategyButton = new JButton("New Strategy");
  private final JButton smartPicksButton = new JButton("Smart Picks");
  private final JPopupMenu smartPicksMenu = new JPopupMenu();
  private final JButton riskDashboardButton = new JButton("Risk Analysis");
  private final JButton portfolioActionsButton = new JButton("Portfolio");
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
  private final PortfolioSnapshotEmailService portfolioSnapshotEmailService = new PortfolioSnapshotEmailService(appSettingsService);
  private final PortfolioEmailScheduleService portfolioEmailScheduler = new PortfolioEmailScheduleService(
      marketHoursService, java.time.Clock.systemUTC(),
      slot -> SwingUtilities.invokeLater(() -> emailPortfolioSnapshot(slot.label(), null)),
      message -> SwingUtilities.invokeLater(() -> log(message)));
  private final RotatingLogWriter rotatingLogWriter = new RotatingLogWriter(AppMetadata.appDataDirectory().resolve("logs"));
  private final LegalDisclosureController legalDisclosureController = new LegalDisclosureController();
  private boolean legalDisclosureAccepted;
  private final StringBuilder pendingLogWrites = new StringBuilder();
  private Timer updateAvailableFlashTimer;
  private boolean updateAvailableNoticeActive;
  private boolean updateAvailableFlashOn;
  private Timer capturePortfolioPulseTimer;
  private boolean capturePortfolioPulseOn;
  private final PortfolioCaptureUiStateStore capturePortfolioUiStates = new PortfolioCaptureUiStateStore();
  // Automation state per mode + tab: each workspace runs its own liquidation monitor.
  private final Map<PortfolioCaptureUiStateStore.Key, PortfolioCaptureAutomationState> captureAutomationStates =
      new java.util.concurrent.ConcurrentHashMap<>();
  private final JButton stopAllLiquidationsButton = new JButton("Stop Liquidations");

  private final UserIdentityService identityService = new UserIdentityService();
  private final UserActionLogSupport userActionLog = new UserActionLogSupport(this::log);
  private final AppUninstallController appUninstallController;
  private final SupportActionsController supportActionsController;
  private final HistoryTablePresenter historyTablePresenter = new HistoryTablePresenter();
  private final HistoryRowStyler historyRowStyler = new HistoryRowStyler();
  private final Map<String, StockPriceTooltipSnapshot> stockPriceTooltipSnapshots = new ConcurrentHashMap<>();
  private final Set<String> stockPriceTooltipRefreshesInFlight = ConcurrentHashMap.newKeySet();
  private final Map<ApplicationMode, BrokerPositionsCacheEntry> brokerPositionSnapshotCache = new ConcurrentHashMap<>();
  private final MarketStatusPresenter marketStatusPresenter = new MarketStatusPresenter();
  private final PollingCellPresenter pollingCellPresenter = new PollingCellPresenter();
  private final PositionValidationCellPresenter positionValidationCellPresenter = new PositionValidationCellPresenter();
  private final ToastNotifier toastNotifier = new ToastNotifier(this);
  private final StatusBarPresenter statusBarPresenter = new StatusBarPresenter();
  private final StrategyActionsPresenter strategyActionsPresenter = new StrategyActionsPresenter();
  private final StrategyGridLayoutPresenter strategyGridLayoutPresenter = new StrategyGridLayoutPresenter();
  private final RuleTriggeredHistoryPresenter ruleTriggeredHistoryPresenter = new RuleTriggeredHistoryPresenter();
  private final StrategyTablePresenter strategyTablePresenter =
      new StrategyTablePresenter(this::untrackedSharesFor);
  private final StrategyOpenPnlCalculator openPnlCalculator = new StrategyOpenPnlCalculator();
  private final SystemMetricsPresenter systemMetricsPresenter = new SystemMetricsPresenter();
  private final PortfolioScopePresenter portfolioScopePresenter = new PortfolioScopePresenter();
  private final KillSwitchController killSwitchController;
  private final JButton refreshPortfolioButton = new JButton("Refresh");
  private final JButton capturePortfolioButton = new JButton("Liquidate Portfolio");
  private final JLabel capturePortfolioIndicator = new JLabel("");
  // Slot the indicator is laid out in. Its width is what BorderLayout leaves between the search
  // controls and the pinned action buttons, so it is the width the status line has to fit into.
  private JPanel capturePortfolioIndicatorPanel;
  // Last fully-rendered status line. Cached so a window resize only re-trims the existing text
  // instead of recomputing the workspace accounting snapshot on every resize event.
  private String capturePortfolioIndicatorFullText = "";
  private final JButton footerActionsButton = new JButton("Actions");
  private final JPopupMenu footerActionsMenu = new JPopupMenu();
  private final PortfolioRefreshController portfolioRefreshController;
  private final PortfolioActionsController portfolioActionsController;
  private final PortfolioCaptureRuns portfolioCaptureRuns;
  private final AccountEquityRecorder accountEquityRecorder;
  private final com.neuralarc.ui.chart.IntradayValueChart accountEquityChart =
      new com.neuralarc.ui.chart.IntradayValueChart(java.time.ZoneId.systemDefault());
  // Reads Alpaca account equity every 30s so each minute of the day gets its point.
  private Timer accountEquitySampleTimer;
  private final AtomicBoolean accountEquityFetchInFlight = new AtomicBoolean(false);
  private final WorkspaceValueRecorder workspaceValueRecorder;
  private final com.neuralarc.ui.chart.IntradayValueChart workspaceValueChart =
      new com.neuralarc.ui.chart.IntradayValueChart("Workspace Value", java.time.ZoneId.systemDefault());
  private final List<ManagedStrategy> strategies = new ArrayList<>();
  private final List<HistoryTablePresenter.HistoryRow> filledOrderRows = new ArrayList<>();
  private final StrategyGridTableModel strategyTableModel = new StrategyGridTableModel(
      strategies,
      this::displayStatusLabel,
      strategyTablePresenter
  );
  private final HistoryGridTableModel filledOrdersTableModel = new HistoryGridTableModel(filledOrderRows);
  private final JTable strategyTable = new JTable(strategyTableModel) {
    @Override
    public String getToolTipText(java.awt.event.MouseEvent event) {
      int viewRow = rowAtPoint(event.getPoint());
      int viewCol = columnAtPoint(event.getPoint());
      if (viewRow < 0 || viewCol < 0) {
        return null;
      }
      int modelRow = convertRowIndexToModel(viewRow);
      if (modelRow < 0 || modelRow >= strategies.size()) {
        return null;
      }
      if (viewCol == StrategyGridLayoutPresenter.ACTIONS_COLUMN_INDEX) {
        return actionTooltipForHover(viewRow, event.getX());
      }
      if (viewCol == STRATEGY_STOCK_PRICE_COLUMN) {
        return strategyStockPriceTooltip(viewRow);
      }
      if (viewCol == STRATEGY_TIF_COLUMN) {
        return strategyTimeInForceTooltip(strategies.get(modelRow).strategy);
      }
      if (viewCol != StrategyGridLayoutPresenter.STATUS_COLUMN_INDEX) {
        return null;
      }
      Strategy strategy = strategies.get(modelRow).strategy;
      String normalized = BrokerOrderStatusUtil.normalize(strategy.latestOrderStatus());
      String statusText = String.valueOf(getValueAt(viewRow, viewCol));
      StringBuilder tooltip = new StringBuilder()
          .append("<b>Status:</b> ")
          .append(escapeHtml(statusText));
      if (BrokerOrderStatusUtil.isWaitingForFill(strategy.latestOrderStatus())) {
        tooltip.append("<br>Waiting for broker fill/update.");
      }
      String reason = strategy.lastError() == null || strategy.lastError().isBlank()
          ? ""
          : strategy.lastError();
      boolean brokerReachabilityReason = isBrokerReachabilityTooltipReason(normalized, reason);
      if ("rejected".equals(normalized)) {
        String rejectedReason = reason.isBlank()
            ? "Broker rejected this order. Review configuration and submit again."
            : reason;
        tooltip.append("<br><b style='color:#ff6b6b;'>Rejected - action required</b><br>")
            .append(escapeHtml(rejectedReason));
      } else if (!reason.isBlank()
          && !isExpiredOrderTooltipReason(normalized, reason)
          && (!brokerReachabilityReason || !connectionOk || connectionRetryPending)) {
        tooltip.append("<br>").append(escapeHtml(reason));
      }
      return TooltipStyler.html(
          tooltip.toString(),
          360
      );
    }

    @Override
    public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
      Component c = super.prepareRenderer(renderer, row, column);
      // Force the custom selection colour even on macOS Aqua LAF, which otherwise
      // paints its own system-accent stripe and ignores the renderer's background.
      boolean rowSelected = isRowSelected(row);
      if (rowSelected) {
        c.setBackground(TABLE_SELECTION_BG);
        c.setForeground(TABLE_SELECTION_FG);
      }
      Color orbPendingBaseBuyForeground = orbPendingBaseBuyForeground(row);
      if (orbPendingBaseBuyForeground != null) {
        c.setForeground(orbPendingBaseBuyForeground);
      }
      // Left accent stripe: a 3-px blue bar on column 0 of selected rows.
      if (c instanceof JComponent jc) {
        StrategyGridSelectionStyler.applySelectionBorder(jc, rowSelected, column, TABLE_SELECTION_BORDER);
      }
      return c;
    }
  };
  private final JTable filledOrdersTable = new JTable(filledOrdersTableModel) {
    @Override
    public String getToolTipText(MouseEvent event) {
      return tradeHistoryCellTooltip(event);
    }
  };
  private static final String GAP_ROCKET_WORKSPACE_CODE = "GAPROCKET";
  private static final String ORB_WORKSPACE_CODE = "ORB";
  private static final String DIP_HUNTER_WORKSPACE_CODE = "DIP";
  private static final String VWAP_WORKSPACE_CODE = "VWAP";
  private static final String SWING_WORKSPACE_CODE = "SWING";
  private static final String RANGE_RIDER_WORKSPACE_CODE = "RANGE";
  private static final String PROFIT_SHIELD_WORKSPACE_CODE = "SHIELD";
  private static final String EARNINGS_HUNTER_WORKSPACE_CODE = "EARNINGS";
  private static final String STRATEGIES_GRID_CARD = "strategiesGrid";
  private static final String GAP_ROCKET_EMPTY_CARD = "gapRocketEmpty";
  private static final String ORB_EMPTY_CARD = "orbEmpty";
  private static final String DIP_HUNTER_EMPTY_CARD = "dipHunterEmpty";
  private static final String VWAP_EMPTY_CARD = "vwapEmpty";
  private static final String SWING_EMPTY_CARD = "swingEmpty";
  private static final String RANGE_RIDER_EMPTY_CARD = "rangeRiderEmpty";
  private static final String PROFIT_SHIELD_EMPTY_CARD = "profitShieldEmpty";
  private static final String EARNINGS_HUNTER_EMPTY_CARD = "earningsHunterEmpty";
  private static final String SMART_PICKS_EMPTY_CARD = "smartPicksEmpty";
  private final JTabbedPane strategyTabs = new JTabbedPane();
  private final JTextField currentStrategiesSearchField = new JTextField(24);
  private final JTextField tradeHistorySearchField = new JTextField(24);
  private final JRadioButton profitableSellsFilterButton = new JRadioButton("Profitable Sells");
  private final JRadioButton lossSellsFilterButton = new JRadioButton("Loss Sells");
  private final JRadioButton bothSellsFilterButton = new JRadioButton("Both", true);
  private final JButton tradeHistoryGroupByButton = new JButton("Group By Menu: Date");
  private final JButton reenterHistoryButton = new JButton("Re-enter Inactive Stocks");
  // Declared here, with the other Trade History toolbar buttons, because the search-panel field
  // initializers below build that toolbar: a button declared further down is still null by then.
  private final JButton historyReentryScheduleButton = new JButton();
  private final JPanel currentStrategiesSearchPanel = createGridSearchPanel("Search stocks:", currentStrategiesSearchField);
  private final JPanel tradeHistorySearchPanel = createGridSearchPanel("Search stocks:", tradeHistorySearchField);
  private final JButton gapRocketAnalyzeButton = new JButton(GapRocketPanel.ANALYZE_BUTTON_TEXT);
  private final JButton gapRocketPlaceOrdersButton = new JButton("Place All Pending Limit Buys");
  private final JLabel gapRocketScheduleStatusLabel = new JLabel();
  private final JButton gapRocketCancelScheduleButton = new JButton("Cancel Schedule");
  private final JButton orbAnalyzeButton = new JButton(OrbPanel.ANALYZE_BUTTON_TEXT);
  private final JLabel orbScheduleStatusLabel = new JLabel();
  private final JButton orbCancelScheduleButton = new JButton("Cancel Schedule");
  private final JButton dipHunterAnalyzeButton = new JButton(DipHunterPanel.ANALYZE_BUTTON_TEXT);
  private final JLabel dipHunterScheduleStatusLabel = new JLabel();
  private final JButton dipHunterCancelScheduleButton = new JButton("Cancel Schedule");
  private final JButton vwapAnalyzeButton = new JButton(VwapPanel.ANALYZE_BUTTON_TEXT);
  private final JLabel vwapScheduleStatusLabel = new JLabel();
  private final JButton vwapCancelScheduleButton = new JButton("Cancel Schedule");
  private final JButton swingAnalyzeButton = new JButton(SwingPanel.ANALYZE_BUTTON_TEXT);
  private final JLabel swingScheduleStatusLabel = new JLabel();
  private final JButton swingCancelScheduleButton = new JButton("Cancel Schedule");
  private final JButton rangeRiderAnalyzeButton = new JButton(RangeRiderPanel.ANALYZE_BUTTON_TEXT);
  private final JLabel rangeRiderScheduleStatusLabel = new JLabel();
  private final JButton rangeRiderCancelScheduleButton = new JButton("Cancel Schedule");
  private final JButton profitShieldAnalyzeButton = new JButton(ProfitShieldPanel.ANALYZE_BUTTON_TEXT);
  private final JLabel profitShieldScheduleStatusLabel = new JLabel();
  private final JButton profitShieldCancelScheduleButton = new JButton("Cancel Schedule");
  private final JButton earningsHunterAnalyzeButton = new JButton(EarningsHunterPanel.ANALYZE_BUTTON_TEXT);
  private final ScanHistoryTablePanel gapRocketScanHistoryPanel = new ScanHistoryTablePanel();
  private final ScanHistoryTablePanel orbScanHistoryPanel = new ScanHistoryTablePanel();
  private final ScanHistoryTablePanel dipHunterScanHistoryPanel = new ScanHistoryTablePanel();
  private final ScanHistoryTablePanel vwapScanHistoryPanel = new ScanHistoryTablePanel();
  private final ScanHistoryTablePanel swingScanHistoryPanel = new ScanHistoryTablePanel();
  private final ScanHistoryTablePanel rangeRiderScanHistoryPanel = new ScanHistoryTablePanel();
  private final ScanHistoryTablePanel profitShieldScanHistoryPanel = new ScanHistoryTablePanel();
  private final ScanHistoryTablePanel earningsHunterScanHistoryPanel = new ScanHistoryTablePanel();
  private final ScanHistoryTablePanel smartPicksScanHistoryPanel = new ScanHistoryTablePanel();
  private SmartPicksWorkspaceView smartPicksWorkspaceView;
  private JPanel headerPanel;
  private final EnumMap<StrategyMode, GapRocketConfig> lastGapRocketConfigs = new EnumMap<>(StrategyMode.class);
  private final EnumMap<StrategyMode, OrbConfig> lastOrbConfigs = new EnumMap<>(StrategyMode.class);
  private final EnumMap<StrategyMode, DipHunterConfig> lastDipHunterConfigs = new EnumMap<>(StrategyMode.class);
  private final EnumMap<StrategyMode, VwapConfig> lastVwapConfigs = new EnumMap<>(StrategyMode.class);
  private final EnumMap<StrategyMode, SwingConfig> lastSwingConfigs = new EnumMap<>(StrategyMode.class);
  private final EnumMap<StrategyMode, RangeRiderConfig> lastRangeRiderConfigs = new EnumMap<>(StrategyMode.class);
  private final EnumMap<StrategyMode, ProfitShieldConfig> lastProfitShieldConfigs = new EnumMap<>(StrategyMode.class);
  private final EnumMap<StrategyMode, EarningsHunterConfig> lastEarningsHunterConfigs = new EnumMap<>(StrategyMode.class);
  private CardLayout strategiesGridCardLayout;
  private JPanel strategiesGridCardPanel;
  private BottomStatusBars bottomStatusBars;
  private TableRowSorter<StrategyGridTableModel> strategySorter;
  private TableRowSorter<HistoryGridTableModel> filledOrdersSorter;
  private TradeHistoryGroupBy tradeHistoryGroupBy = TradeHistoryGroupBy.DATE;
  private StrategyMode selectedViewMode = StrategyMode.PAPER;
  private boolean updatingModeButtons;
  private boolean liveModeConfirmedThisSession;

  private TradingApi tradingApi;
  private AnalyticsPublisher analyticsPublisher;
  private final SettingsDialog settingsDialog;
  private final SqliteStrategyRepository strategyRepository;
  private final SqliteStrategyOrderRepository strategyOrderRepository;
  private final SqliteStrategyExecutionEventRepository strategyEventRepository;
  private final SqliteWorkspaceRepository workspaceRepository;
  private final SqliteScanHistoryRepository scanHistoryRepository;
  private final com.neuralarc.db.SqliteAgentToolCallRepository agentToolCallRepository;
  private final com.neuralarc.db.SqliteHistoryReentryScheduleRepository historyReentryScheduleRepository;
  private final com.neuralarc.service.HistoryReentryScheduleService historyReentryScheduleService =
      new com.neuralarc.service.HistoryReentryScheduleService(marketHoursService, java.time.Clock.systemUTC(),
          schedule -> SwingUtilities.invokeLater(() -> runScheduledHistoryReentry(schedule)), this::log);
  private AgentAnalystDialog agentAnalystDialog;
  private final SqliteRemoteSyncSuppressionRepository remoteSyncSuppressionRepository;
  private final GapAndGoCoordinator gapAndGoCoordinator;
  private final SmartPicksWorkspaceCoordinator smartPicksWorkspaceCoordinator;
  private final OrbCoordinator orbCoordinator;
  private final DipHunterCoordinator dipHunterCoordinator;
  private final VwapCoordinator vwapCoordinator;
  private final SwingCoordinator swingCoordinator;
  private final RangeRiderCoordinator rangeRiderCoordinator;
  private final ProfitShieldCoordinator profitShieldCoordinator;
  private final EarningsHunterCoordinator earningsHunterCoordinator;
  private final AutoRiskAdjustmentService autoRiskAdjustmentService;
  private final WorkspaceService workspaceService;
  // Dynamic strategy-workspace tabs; null workspace = the All Stocks view.
  private StrategyWorkspaceTabs strategyWorkspaceTabs;
  private String selectedWorkspaceId;
  private final WorkspaceSummaryPresenter workspaceSummaryPresenter = new WorkspaceSummaryPresenter();
  private final WorkspaceGridAnalyticsBar workspaceGridAnalyticsBar = new WorkspaceGridAnalyticsBar(BASE_FONT);
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
  private boolean promptedDefaultStrategyDialog;
  private StrategyService strategyService;
  private StrategyPollingService strategyPollingService;
  private StrategyPollingService companionLivePollingService;
  private long lastBrokerBackedUiRefreshAtMillis;
  private volatile long lastDailyBarFetchAtMillis;
  private String runtimeApiKey = "";
  private String runtimeApiSecret = "";
  private volatile HttpAlpacaClient paperModeClient;
  private volatile HttpAlpacaClient liveModeClient;
  private final AvailableFundsStatusState availableFundsStatusState = new AvailableFundsStatusState();
  private volatile String availableFundsText = "Funds Available: -";
  private final AtomicBoolean availableFundsFetchInFlight = new AtomicBoolean(false);
  private static final long AVAILABLE_FUNDS_REFRESH_INTERVAL_MILLIS = 30000L;
  private volatile long lastLoggedSnapshotIntervalMillis = -1L;
  private volatile long lastClosedMarketPollingCycleAtMillis;
  private volatile boolean startupMarketClosedRepairAuditLogged;
  private final AutoAnalyzeResultStore autoAnalyzeResultStore = new AutoAnalyzeResultStore();
  private final OnboardingStateStore onboardingStateStore = new OnboardingStateStore();
  private final TradingRuntimeSupport tradingRuntimeSupport;
  private final StrategyActionsController strategyActionsController;
  private final TradeStreamLifecycleCoordinator tradeStreamLifecycleCoordinator;
  private volatile boolean streamReconnectAvailable;
  private volatile boolean streamRecoverySyncPending;
  private volatile boolean showStreamReconnectFailureDialog;
  private volatile String lastStreamErrorMessage = "";
  private Timer streamReconnectRetryTimer;
  private int streamReconnectAttempt;
  private LocalDate lastStreamBackoffResetDate;
  /**
   * Wall-clock millis of the last polling tick. Used to detect system-sleep gaps. EDT-only.
   */
  private long lastPollingTickMillis;
  private final ConnectionLifecycleCoordinator connectionLifecycleCoordinator;
  private final StartupCredentialCoordinator startupCredentialCoordinator;
  private AsyncLogUploadService asyncLogUploadService;

  public TradingFrame() {
    com.neuralarc.api.ApiRequestLogConfig.setVerboseJsonLogging(appSettingsService.loadVerboseApiJsonLoggingEnabled());
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
    AtomicInteger uiPollingThreadIndex = new AtomicInteger(1);
    uiPollingExecutor = Executors.newFixedThreadPool(2, runnable -> {
      Thread thread = new Thread(runnable, "neuralarc-ui-polling-" + uiPollingThreadIndex.getAndIncrement());
      thread.setDaemon(true);
      return thread;
    });
    setTitle("NeuralArc Trader Application");
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setLayout(new BorderLayout());
    ((JComponent) getContentPane()).setBorder(new EmptyBorder(OUTER_PADDING, OUTER_PADDING, OUTER_PADDING, OUTER_PADDING));
    settingsDialog = new SettingsDialog(this);
    appUninstallController = new AppUninstallController(
        this,
        userActionLog,
        this::log,
        this::shutdownAllStrategies,
        () -> {
          dispose();
          System.exit(0);
        }
    );
    supportActionsController = new SupportActionsController(
        this,
        settingsDialog::getUserEmail,
        userActionLog,
        this::log
    );
    AppDatabase appDatabase = AppDatabase.getInstance();
    strategyRepository = new SqliteStrategyRepository(appDatabase);
    strategyOrderRepository = new SqliteStrategyOrderRepository(appDatabase);
    strategyEventRepository = new SqliteStrategyExecutionEventRepository(appDatabase);
    workspaceRepository = new SqliteWorkspaceRepository(appDatabase);
    scanHistoryRepository = new SqliteScanHistoryRepository(appDatabase);
    agentToolCallRepository = new com.neuralarc.db.SqliteAgentToolCallRepository(appDatabase);
    historyReentryScheduleRepository = new com.neuralarc.db.SqliteHistoryReentryScheduleRepository(appDatabase);
    remoteSyncSuppressionRepository = new SqliteRemoteSyncSuppressionRepository(appDatabase);
    gapAndGoCoordinator = new GapAndGoCoordinator(
        new GapAndGoCoordinatorUi(), appDatabase, strategyRepository,
        appSettingsService, marketHoursService, scanHistoryRepository, uiPollingExecutor);
    strategyTablePresenter.setWorkspaceNameLookup(this::workspaceNameOrNull);
    smartPicksWorkspaceCoordinator = new SmartPicksWorkspaceCoordinator(new SmartPicksWorkspaceCoordinator.Ui() {
      @Override
      public boolean connectionOk() {
        return connectionOk;
      }

      @Override
      public boolean workspaceExists(String workspaceId) {
        return workspaceId != null && workspaceService.findById(workspaceId).isPresent();
      }

      @Override
      public String runScan(SmartPicksSchedule schedule, SmartPicksWorkspaceKind kind) {
        return runSmartPicks(kind.automationStrategy(), schedule.mode(), schedule.quantity(), schedule.term(),
            schedule.workspaceId(), schedule.executeAfterScan(), "[Smart Picks][" + kind.title() + "]");
      }

      @Override
      public void onScheduleChanged() {
        SwingUtilities.invokeLater(TradingFrame.this::refreshNewStrategyButtonPresentation);
      }

      @Override
      public void log(String message) {
        TradingFrame.this.log(message);
      }
    }, new com.neuralarc.db.SqliteSmartPicksScheduleRepository(appDatabase), scanHistoryRepository,
        marketHoursService, uiPollingExecutor, java.time.Clock.systemUTC());
    java.util.concurrent.ExecutorService accountEquityWriter = java.util.concurrent.Executors.newSingleThreadExecutor(runnable -> {
      Thread thread = new Thread(runnable, "account-equity-writer");
      thread.setDaemon(true);
      return thread;
    });
    accountEquityRecorder = new AccountEquityRecorder(
        new com.neuralarc.db.SqliteAccountEquityRepository(appDatabase), accountEquityWriter,
        java.time.Clock.systemUTC(), this::log);
    workspaceValueRecorder = new WorkspaceValueRecorder(
        new com.neuralarc.db.SqliteWorkspaceValueRepository(appDatabase), accountEquityWriter,
        java.time.Clock.systemUTC(), this::log);
    orbCoordinator = new OrbCoordinator(new OrbCoordinatorUi(), appDatabase, strategyRepository,
        appSettingsService, marketHoursService, scanHistoryRepository, uiPollingExecutor);
    dipHunterCoordinator = new DipHunterCoordinator(new DipHunterCoordinatorUi(), appDatabase, strategyRepository,
        appSettingsService, marketHoursService, scanHistoryRepository, uiPollingExecutor);
    vwapCoordinator = new VwapCoordinator(new VwapCoordinatorUi(), appDatabase, strategyRepository,
        appSettingsService, marketHoursService, scanHistoryRepository, uiPollingExecutor);
    swingCoordinator = new SwingCoordinator(new SwingCoordinatorUi(), appDatabase, strategyRepository,
        appSettingsService, marketHoursService, scanHistoryRepository, uiPollingExecutor);
    rangeRiderCoordinator = new RangeRiderCoordinator(new RangeRiderCoordinatorUi(), appDatabase, strategyRepository,
        marketHoursService, scanHistoryRepository, uiPollingExecutor);
    profitShieldCoordinator = new ProfitShieldCoordinator(new ProfitShieldCoordinatorUi(), appDatabase,
        strategyRepository, marketHoursService, scanHistoryRepository, uiPollingExecutor);
    earningsHunterCoordinator = new EarningsHunterCoordinator(new EarningsHunterCoordinatorUi(),
        strategyRepository, scanHistoryRepository, uiPollingExecutor);
    autoRiskAdjustmentService = new AutoRiskAdjustmentService(strategyRepository, marketHoursService,
        java.time.Clock.systemUTC(), this::latestPriceForAutoAdjust, this::log);
    workspaceService = new WorkspaceService(workspaceRepository, strategyRepository);
    portfolioRefreshController = new PortfolioRefreshController(
        strategyRepository,
        strategyOrderRepository,
        strategyEventRepository,
        uiPollingExecutor,
        new PortfolioRefreshController.Gateway() {
          @Override
          public boolean isConnected() {
            return connectionOk;
          }

          @Override
          public BrokerType brokerType() {
            return currentBrokerType;
          }

          @Override
          public HttpAlpacaClient alpacaClientForMode(ApplicationMode mode) {
            return TradingFrame.this.alpacaClientForMode(mode);
          }

          @Override
          public boolean modeActive(StrategyMode mode) {
            return ModeActivity.runsFor(mode, selectedViewMode);
          }

          @Override
          public void onRefreshStarted() {
            setPortfolioRefreshButtonBusy(true);
          }

          @Override
          public void onRefreshFinished() {
            setPortfolioRefreshButtonBusy(false);
          }

          @Override
          public void syncStrategies(List<Strategy> strategies) {
            TradingFrame.this.syncStrategies(strategies);
          }

          @Override
          public int adoptUnknownBrokerSymbols() {
            return TradingFrame.this.adoptUnknownBrokerSymbols();
          }

          @Override
          public void applyPositionSnapshots(Map<String, Position> snapshots) {
            TradingFrame.this.applyPositionSnapshots(snapshots);
          }

          @Override
          public void handleInvalidBrokerMissingStrategies(List<Strategy> invalidStrategies) {
            TradingFrame.this.handleInvalidBrokerMissingStrategies(invalidStrategies);
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

          @Override
          public void log(String message) {
            TradingFrame.this.log(message);
          }

          @Override
          public void showConnectionRequired() {
            JOptionPane.showMessageDialog(
                TradingFrame.this,
                "Connect to Alpaca before refreshing the portfolio.",
                "Portfolio Refresh",
                JOptionPane.WARNING_MESSAGE
            );
          }

          @Override
          public void showRefreshFailed(String message) {
            JOptionPane.showMessageDialog(
                TradingFrame.this,
                "Failed to refresh portfolio: " + message,
                "Portfolio Refresh Failed",
                JOptionPane.ERROR_MESSAGE
            );
          }
        }
    );
    portfolioActionsController = new PortfolioActionsController(new PortfolioActionsController.Gateway() {
      @Override
      public void openAgentAnalyst() {
        TradingFrame.this.openAgentAnalyst();
      }

      @Override
      public List<ManagedStrategy> strategies() {
        return strategies;
      }

      @Override
      public List<ManagedStrategy> currentStrategies() {
        return strategies.stream()
            .filter(TradingFrame.this::includeInCurrentStrategiesTab)
            .filter(entry -> matchesPortfolioActionScope(entry.strategy, selectedViewMode, selectedWorkspaceId))
            .toList();
      }

      @Override
      public List<ManagedStrategy> scopedStrategies() {
        return strategies.stream()
            .filter(entry -> matchesPortfolioActionScope(entry.strategy, selectedViewMode, selectedWorkspaceId))
            .toList();
      }

      @Override
      public StrategyService strategyService() {
        return strategyService;
      }

      @Override
      public StrategyService strategyServiceForMode(StrategyMode mode) {
        return TradingFrame.this.strategyServiceForMode(mode);
      }

      @Override
      public StrategyMode selectedViewMode() {
        return selectedViewMode;
      }

      @Override
      public StrategyService.ArchiveResult archiveStrategy(String strategyId, String reason) {
        if (strategyService == null) {
          return StrategyService.ArchiveResult.failed("strategy service is not configured");
        }
        return strategyService.archiveStrategy(strategyId, reason);
      }

      @Override
      public Optional<List<SharesAndTimeInForcePlan.Change>> chooseSharesAndTimeInForce(SharesAndTimeInForcePlan plan) {
        return new SharesAndTimeInForceDialog(TradingFrame.this, plan, selectedScopeLabel()).showDialog();
      }

      @Override
      public List<StrategyOrder> ordersForStrategy(String strategyId) {
        return strategyOrderRepository.findByStrategyId(strategyId);
      }

      @Override
      public StrategyService.ArchiveResult deleteArchivedPosition(String strategyId) {
        return TradingFrame.this.deleteArchivedPosition(strategyId);
      }

      @Override
      public StrategyService.ArchiveResult deleteLocalTradeHistoryStrategy(String strategyId) {
        return TradingFrame.this.deleteLocalTradeHistoryStrategy(strategyId);
      }

      @Override
      public StrategyService.ArchiveResult deleteLocalPaperStrategy(String strategyId) {
        return TradingFrame.this.deleteLocalPaperStrategy(strategyId);
      }

      @Override
      public StrategyService.ArchiveResult deletePendingBaseBuyStrategy(String strategyId) {
        return TradingFrame.this.deletePendingBaseBuyStrategy(strategyId);
      }

      @Override
      public StrategyService.ArchiveResult deleteCleanableGridStrategy(String strategyId) {
        return TradingFrame.this.deleteCleanableGridStrategy(strategyId);
      }

      @Override
      public StrategyService.StrategyCreationResult sellPosition(
          Strategy strategy,
          SellSubmissionType submissionType,
          StrategyService.SellExecutionSource executionSource
      ) {
        return TradingFrame.this.sellPosition(strategy, submissionType, executionSource);
      }

      @Override
      public StrategyService.StrategyCreationResult placeSellTriggerOrder(
          Strategy strategy,
          StrategyService.SellExecutionSource executionSource
      ) {
        StrategyService modeAwareService = strategyServiceForMode(strategy.mode());
        if (modeAwareService == null) {
          return StrategyService.StrategyCreationResult.failed(
              "Broker client is not configured for " + strategy.mode().name() + " mode."
          );
        }
        return modeAwareService.placeSellTriggerOrder(strategy.id(), executionSource);
      }

      @Override
      public StrategyService.StrategyCreationResult placePendingBaseBuy(Strategy strategy) {
        return TradingFrame.this.placePendingBaseBuy(strategy);
      }

      @Override
      public StrategyService.StrategyCreationResult readjustLosingPendingBaseBuy(ManagedStrategy entry) {
        return TradingFrame.this.readjustLosingPendingBaseBuy(entry);
      }

      @Override
      public Optional<AverageLosingPositionsSelection> chooseAverageLosingPositions(List<ManagedStrategy> targets) {
        return TradingFrame.this.chooseAverageLosingPositions(targets);
      }

      @Override
      public Optional<BigDecimal> chooseSellProfitThresholdPercent(List<ManagedStrategy> targets) {
        return TradingFrame.this.chooseSellProfitThresholdPercent(targets);
      }

      @Override
      public Optional<Strategy> updateStrategy(Strategy strategy) {
        return TradingFrame.this.updateStrategyForMode(strategy);
      }

      @Override
      public StrategyService.StrategyCreationResult buyMoreAtMarket(Strategy strategy, int quantity) {
        return TradingFrame.this.buyMoreAtMarket(strategy, quantity);
      }

      @Override
      public StrategyService.StrategyCreationResult buyMoreAtLimit(
          Strategy strategy, int quantity, BigDecimal limitPrice, TimeInForce timeInForce) {
        return TradingFrame.this.buyMoreAtLimit(strategy, quantity, limitPrice, false, timeInForce);
      }

      @Override
      public ManualPortfolioImportService.ImportResult importManualStocks(List<PortfolioStockImportDialog.ImportedStockDraft> drafts) {
        ManualPortfolioImportService service = new ManualPortfolioImportService(new ManualPortfolioImportService.Gateway() {
          @Override
          public com.neuralarc.service.StrategyRepository repository() {
            return strategyRepository;
          }

          @Override
          public String targetWorkspaceId() {
            return selectedWorkspaceForNewStrategy();
          }

          @Override
          public StrategyMode targetMode() {
            return selectedViewMode;
          }

          @Override
          public boolean allowDuplicateSymbols() {
            return settingsDialog.appliedAllowDuplicateSymbolStrategies();
          }

          @Override
          public int defaultPollingSeconds() {
            return settingsDialog.appliedDefaultStrategyPollingSeconds();
          }

          @Override
          public boolean defaultRepeatCycleAfterProfitExitEnabled() {
            return settingsDialog.appliedDefaultRepeatCycleAfterProfitExitEnabled();
          }

          @Override
          public boolean defaultResubmitOnExpiryEnabled() {
            return settingsDialog.appliedDefaultResubmitOnExpiryEnabled();
          }

          @Override
          public com.neuralarc.api.AlpacaMarketDataApi marketDataApi() {
            return connectionOk && !runtimeApiKey.isBlank()
                ? new HttpAlpacaMarketDataApi(runtimeApiKey, runtimeApiSecret)
                : null;
          }

          @Override
          public void assignWorkspace(Strategy strategy, String workspaceId) {
            NewStrategyWorkspaceAssignment.apply(strategy, workspaceId, workspaceService);
          }
        });
        return service.importDrafts(drafts);
      }

      @Override
      public com.neuralarc.api.AlpacaMarketDataApi marketDataApiForMode(StrategyMode mode) {
        return connectionOk && !runtimeApiKey.isBlank()
            ? new HttpAlpacaMarketDataApi(runtimeApiKey, runtimeApiSecret)
            : null;
      }

      @Override
      public int defaultStrategyPollingSeconds() {
        return settingsDialog.appliedDefaultStrategyPollingSeconds();
      }

      @Override
      public boolean defaultRepeatCycleAfterProfitExitEnabled() {
        return settingsDialog.appliedDefaultRepeatCycleAfterProfitExitEnabled();
      }

      @Override
      public boolean defaultResubmitOnExpiryEnabled() {
        return settingsDialog.appliedDefaultResubmitOnExpiryEnabled();
      }

      @Override
      public boolean allowDuplicateSymbols() {
        return settingsDialog.appliedAllowDuplicateSymbolStrategies();
      }

      @Override
      public String selectedWorkspaceForNewStrategy() {
        return TradingFrame.this.selectedWorkspaceForNewStrategy();
      }

      @Override
      public String selectedModeLabel() {
        return TradingFrame.this.selectedModeLabel();
      }

      @Override
      public JMenuItem createMenuItem(String text, String iconPath, Runnable action) {
        return TradingFrame.this.createStatusMenuItem(text, iconPath, action);
      }

      @Override
      public JMenuItem createMenuItem(String label, String description, String iconPath, Runnable action) {
        return TradingFrame.this.createDescribedStatusMenuItem(label, description, iconPath, action);
      }

      @Override
      public JMenu createSubMenu(String label, String iconPath) {
        return TradingFrame.this.createStatusSubMenu(label, iconPath);
      }

      @Override
      public int confirm(Object message, String title, int optionType, int messageType) {
        return JOptionPane.showConfirmDialog(TradingFrame.this, message, title, optionType, messageType);
      }

      @Override
      public void showMessage(Object message, String title, int messageType) {
        JOptionPane.showMessageDialog(TradingFrame.this, message, title, messageType);
      }

      @Override
      public void syncStrategiesFromRepository() {
        TradingFrame.this.syncStrategiesFromRepository();
      }

      @Override
      public void refreshStrategyTableData() {
        TradingFrame.this.refreshStrategyTableData();
      }

      @Override
      public void updateSelectedStrategy() {
        TradingFrame.this.updateSelectedStrategy();
      }

      @Override
      public void refreshPanels() {
        TradingFrame.this.refreshPanels();
      }

      @Override
      public void updateStatusBar() {
        TradingFrame.this.updateStatusBar();
      }

      @Override
      public void log(String message) {
        TradingFrame.this.log(message);
      }

      @Override
      public void actionStarted(String actionName) {
        userActionLog.started(actionName);
      }

      @Override
      public void actionCompleted(String actionName, String detail) {
        userActionLog.completed(actionName, detail);
      }

      @Override
      public void actionSkipped(String actionName, String reason) {
        userActionLog.skipped(actionName, reason);
      }

      @Override
      public void actionCanceled(String actionName) {
        userActionLog.canceled(actionName);
      }

      @Override
      public void actionFailed(String actionName, String reason) {
        userActionLog.failed(actionName, reason);
      }
    });
    portfolioCaptureRuns = new PortfolioCaptureRuns(
        new PortfolioCaptureRuns.Host() {
          @Override
          public List<ManagedStrategy> strategies() {
            return strategies;
          }

          @Override
          public BigDecimal realizedPnlForStrategy(String strategyId) {
            return TradingFrame.this.realizedPnlForStrategy(strategyId);
          }

          @Override
          public StrategyService.StrategyCreationResult sellPosition(
              ManagedStrategy entry,
              SellSubmissionType submissionType,
              StrategyService.SellExecutionSource executionSource
          ) {
            return TradingFrame.this.sellPosition(entry.strategy, submissionType, executionSource);
          }

          @Override
          public int cancelPendingBaseBuys(StrategyMode mode) {
            return TradingFrame.this.cancelPendingBaseBuysForAutomation(mode);
          }

          @Override
          public String runSmartPicksAutomation(PortfolioCaptureConfig config) {
            return TradingFrame.this.runSmartPicksAutomation(config);
          }

          @Override
          public boolean tradingSessionOpen() {
            return TradingFrame.this.isMarketOpenForUi();
          }

          @Override
          public String nextTradingSessionOpenDisplay() {
            return TradingFrame.this.nextTradingSessionOpenDisplay();
          }

          @Override
          public void onMonitoringChanged(PortfolioCaptureRuns.Scope scope, boolean active, PortfolioCaptureConfig config) {
            TradingFrame.this.updateCapturePortfolioUi(scope, active);
          }

          @Override
          public void onSnapshotUpdated(PortfolioCaptureRuns.Scope scope, PortfolioCaptureConfig config) {
            TradingFrame.this.updateCapturePortfolioIndicator(scope);
          }

          @Override
          public void onAutomationStateChanged(PortfolioCaptureRuns.Scope scope, PortfolioCaptureAutomationState state) {
            TradingFrame.this.updateCaptureAutomationState(scope, state);
          }

          @Override
          public void onExecutionStarted(PortfolioCaptureRuns.Scope scope) {
            setCapturePortfolioBusy(scope, true);
          }

          @Override
          public void onExecutionFinished(PortfolioCaptureRuns.Scope scope, PortfolioCaptureExecutionResult result,
              boolean targetTriggered) {
            setCapturePortfolioBusy(scope, false);
            syncStrategiesFromRepository();
            refreshStrategyTableContent();
            refreshPanels();
            updateStatusBar();
            showPortfolioCaptureSummary(result, targetTriggered);
          }

          @Override
          public void log(String message) {
            TradingFrame.this.log(message);
          }
        },
        new PortfolioCaptureCalculator(),
        AppMetadata.appDataDirectory(),
        new PortfolioCaptureHistoryStore(AppMetadata.appDataDirectory().resolve("portfolio-capture-history.json"))
    );
    tradingRuntimeSupport = new TradingRuntimeSupport(
        strategyRepository,
        strategyOrderRepository,
        strategyEventRepository,
        appSettingsService,
        marketHoursService,
        workspaceRepository
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
          @Override
          public Strategy strategy() {
            return entry.strategy;
          }

          @Override
          public boolean isPaused() {
            return entry.isPaused();
          }

          @Override
          public boolean isPauseResumeBusy() {
            return entry.isPauseResumeBusy();
          }

          @Override
          public void setPauseResumeBusy(boolean value) {
            entry.setPauseResumeBusy(value);
          }

          @Override
          public void setPauseResumeBusyText(String value) {
            entry.setPauseResumeBusyText(value);
          }

          @Override
          public void syncFrom(Strategy strategy) {
            entry.syncFrom(strategy);
          }
        };
      }

      @Override
      public StrategyService strategyService() {
        return strategyService;
      }

      @Override
      public StrategyService liveStrategyService() {
        return strategyServiceForMode(StrategyMode.LIVE);
      }

      @Override
      public Optional<Strategy> findStrategyById(String strategyId) {
        return strategyRepository.findById(strategyId);
      }

      @Override
      public void refreshStrategyTableRow(int modelRow) {
        TradingFrame.this.refreshStrategyTableRow(modelRow);
      }

      @Override
      public void refreshStrategyTableData() {
        TradingFrame.this.refreshStrategyTableData();
      }

      @Override
      public void refreshPanels() {
        TradingFrame.this.refreshPanels();
      }

      @Override
      public void updateStatusBar() {
        TradingFrame.this.updateStatusBar();
      }

      @Override
      public void restoreSelectedRow() {
        TradingFrame.this.restoreSelectedRow();
      }

      @Override
      public void updateSelectedStrategy() {
        TradingFrame.this.updateSelectedStrategy();
      }

      @Override
      public void syncStrategiesFromRepository() {
        TradingFrame.this.syncStrategiesFromRepository();
      }

      @Override
      public void clearStrategySelection() {
        strategyTable.clearSelection();
      }

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

      @Override
      public Position loadPositionForStrategy(Strategy strategy) {
        return TradingFrame.this.loadPositionForStrategy(strategy);
      }

      @Override
      public boolean hasOpenPosition(Strategy strategy) {
        return TradingFrame.this.loadPositionForStrategy(strategy).getTotalShares() > 0;
      }

      @Override
      public StrategyService.ArchiveResult archiveStrategy(String strategyId, String reason) {
        if (strategyService == null) {
          return StrategyService.ArchiveResult.failed("strategy service is not configured");
        }
        return strategyService.archiveStrategy(strategyId, reason);
      }

      @Override
      public Optional<SellSubmissionType> chooseSellSubmissionType(Strategy strategy) {
        Object[] options = {"Limit Sell", "Market Sell", "Cancel"};
        String message = "<html><body style='width:360px'>"
            + "<b>Select sell submission type for " + strategy.symbol() + "</b><br><br>"
            + "<b>Limit Sell</b>: submits a limit order at the latest broker price.<br>"
            + "<b>Market Sell</b>: submits a market order for immediate execution at market prices."
            + "</body></html>";
        int choice = JOptionPane.showOptionDialog(
            TradingFrame.this,
            message,
            "Sell Type — " + strategy.symbol(),
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            options[0]
        );
        if (choice == 0) {
          return Optional.of(SellSubmissionType.LIMIT);
        }
        if (choice == 1) {
          return Optional.of(SellSubmissionType.MARKET);
        }
        return Optional.empty();
      }

      @Override
      public Optional<RepositionSubmissionType> chooseRepositionSubmissionType(Strategy strategy) {
        return RepositionSubmissionTypeDialog.show(TradingFrame.this, strategy);
      }

      @Override
      public Optional<TimeInForce> chooseRepositionTimeInForce(Strategy strategy) {
        return RepositionTimeInForceDialog.show(TradingFrame.this, strategy);
      }

      @Override
      public StrategyService.StrategyCreationResult sellPosition(Strategy strategy, SellSubmissionType submissionType) {
        return TradingFrame.this.sellPosition(strategy, submissionType);
      }

      @Override
      public Optional<Integer> chooseMarketBuyQuantity(Strategy strategy) {
        return TradingFrame.this.chooseMarketBuyQuantity(strategy);
      }

      @Override
      public Optional<ManualLimitBuySelection> chooseLimitBuy(Strategy strategy, BigDecimal currentPrice) {
        return ManualLimitBuyDialog.show(
            TradingFrame.this,
            strategy,
            currentPrice,
            settingsDialog.appliedManualBuyTimeInForce()
        );
      }

      @Override
      public BigDecimal currentPriceForStrategy(Strategy strategy) {
        ManagedStrategy entry = strategy == null ? null : TradingFrame.this.findStrategyById(strategy.id());
        return entry == null ? BigDecimal.ZERO : entry.cachedPosition().getLastPrice();
      }

      @Override
      public StrategyService.StrategyCreationResult buyMoreAtMarket(Strategy strategy, int quantity) {
        return TradingFrame.this.buyMoreAtMarket(strategy, quantity);
      }

      @Override
      public StrategyService.StrategyCreationResult buyMoreAtLimit(
          Strategy strategy,
          int quantity,
          BigDecimal limitPrice,
          boolean repositionAfterExpiry,
          TimeInForce timeInForce
      ) {
        return TradingFrame.this.buyMoreAtLimit(strategy, quantity, limitPrice, repositionAfterExpiry, timeInForce);
      }

      @Override
      public StrategyService.StrategyCreationResult repositionExpiredStrategy(
          String strategyId,
          RepositionSubmissionType submissionType,
          TimeInForce timeInForce
      ) {
        StrategyService service = strategyRepository.findById(strategyId)
            .map(strategy -> strategyServiceForMode(strategy.mode()))
            .orElse(strategyService);
        if (service == null) {
          return StrategyService.StrategyCreationResult.failed("strategy service is not configured");
        }
        return service.repositionExpiredStrategy(strategyId, submissionType, timeInForce);
      }

      @Override
      public StrategyService.LimitBuyCancelResult cancelPendingLimitBuys(Strategy strategy) {
        StrategyService service = strategyServiceForMode(strategy.mode());
        if (service == null) {
          return StrategyService.LimitBuyCancelResult.failed(
              "Broker client is not configured for " + strategy.mode().name() + " mode.");
        }
        return service.cancelPendingLimitBuys(strategy.id());
      }

      @Override
      public boolean hasCancelablePendingLimitBuy(Strategy strategy) {
        return strategy != null
            && PendingBuyOrderGuard.hasCancelablePendingLimitBuy(
            strategyOrderRepository.findByStrategyId(strategy.id()));
      }

      @Override
      public void excludeFromPortfolioCaptureIfRunning(String strategyId) {
        portfolioCaptureRuns.excludeStrategyFromActiveCapture(strategyId);
      }

      @Override
      public BigDecimal realizedPnlForStrategy(String strategyId) {
        return TradingFrame.this.realizedPnlForStrategy(strategyId);
      }

      @Override
      public String closePaperAccountState(Strategy strategy) {
        return TradingFrame.this.closePaperAccountState(strategy);
      }

      @Override
      public void updateHeaderModeStatus(BrokerType brokerType) {
        TradingFrame.this.updateHeaderModeStatus(brokerType);
      }

      @Override
      public BrokerType currentBrokerType() {
        return currentBrokerType;
      }

      @Override
      public boolean hasBrokerPositionAccess() {
        return currentBrokerType == BrokerType.ALPACA || tradingApi != null;
      }

      @Override
      public boolean marketOpenForUi() {
        return TradingFrame.this.isMarketOpenForUi();
      }

      @Override
      public void setSelectedStrategyId(String strategyId) {
        selectedStrategyId = strategyId;
      }

      @Override
      public String selectedStrategyId() {
        return selectedStrategyId;
      }

      @Override
      public void removeStrategyAt(int modelRow) {
        strategies.remove(modelRow);
      }

      @Override
      public void log(String message) {
        TradingFrame.this.log(message);
      }

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
        return new StrategyActionsController.PromotionDialogResult(
            proceed,
            dialog.shouldClosePaperPositions(),
            dialog.baseBuyPrice(),
            dialog.baseBuyQty(),
            dialog.lossBuyLevelsEnabled(),
            dialog.buyLevel1Price(),
            dialog.buyLevel1Qty(),
            dialog.buyLevel2Price(),
            dialog.buyLevel2Qty(),
            dialog.targetSellPrice()
        );
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
    killSwitchController = new KillSwitchController(new KillSwitchController.Gateway() {
      @Override
      public List<ManagedStrategy> strategies() {
        return strategies;
      }

      @Override
      public void pauseStrategy(String strategyId) {
        strategyService.pause(strategyId);
      }

      @Override
      public void stopPollingCountdown(ManagedStrategy strategy) {
        TradingFrame.this.stopPollingCountdown(strategy);
      }

      @Override
      public void syncStrategiesFromRepository() {
        TradingFrame.this.syncStrategiesFromRepository();
      }

      @Override
      public void refreshStrategyTableData() {
        TradingFrame.this.refreshStrategyTableData();
      }

      @Override
      public void updateStatusBar() {
        TradingFrame.this.updateStatusBar();
      }

      @Override
      public void refreshPanels() {
        TradingFrame.this.refreshPanels();
      }

      @Override
      public void log(String message) {
        TradingFrame.this.tradeLog(message);
      }

      @Override
      public void publishAnalytics(AnalyticsEvent event) {
        if (analyticsPublisher != null && event != null) {
          analyticsPublisher.publish(event);
        }
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
      public void onStreamError(String message) {
        TradingFrame.this.onTradeStreamError(message);
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
      public Optional<String> onTradeUpdate(AlpacaTradeUpdateEvent event) {
        if (strategyPollingService != null) {
          return strategyPollingService.onTradeUpdate(event);
        }
        return Optional.empty();
      }

      @Override
      public void refreshDisplayedPositionFromStream(String strategyId) {
        TradingFrame.this.refreshDisplayedPositionFromStream(strategyId);
      }

      @Override
      public void showTradeEventToast(TradeEventToastFormatter.ToastMessage message) {
        toastNotifier.show(message);
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
        refreshStrategyRuntimeServices(
            savedApiKeyForSelectedMode(),
            savedApiSecretForSelectedMode(),
            selectedApplicationMode()
        );
        restartTradingEventStreamForSelectedMode();
        refreshStrategyTableData();
        setStatus("Connected - broker " + brokerType.name() + " ready.", STATUS_OK);
        updateHeaderModeStatus(brokerType);
        updateStatusBar();
        initPersistenceAndRestore();
        portfolioRefreshController.refresh(false);
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
    startupCredentialCoordinator = new StartupCredentialCoordinator(
        new StartupCredentialCoordinator.Ui() {
          @Override
          public ApplicationMode appliedApplicationMode() {
            return settingsDialog.appliedApplicationMode();
          }

          @Override
          public BrokerType appliedBrokerType() {
            return settingsDialog.appliedBrokerType();
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
          public boolean liveTradingEnabled() {
            return AppMetadata.liveTradingEnabled();
          }

          @Override
          public void log(String message) {
            TradingFrame.this.log(message);
          }

          @Override
          public void setVerifyingStatus(String message) {
            setStatus(message, STATUS_WARN);
          }

          @Override
          public SettingsDialog.ConnectionResult applyConnectionAttempt(
              TradingRuntimeSupport.ConnectionAttemptResult attempt,
              BrokerType brokerType,
              ApplicationMode mode,
              String apiKey,
              String apiSecret
          ) {
            return connectionLifecycleCoordinator.applyConnectionAttempt(
                attempt, brokerType, mode, apiKey, apiSecret, false, true);
          }

          @Override
          public void showCredentialProblem(String title, String headline, String summary) {
            JOptionPane.showMessageDialog(
                TradingFrame.this,
                "<html><body style='width:360px'><b>" + headline + "</b><br><br>"
                    + summary + ".<br><br>"
                    + "Update the credentials in Settings and verify the connection."
                    + "</body></html>",
                title,
                JOptionPane.WARNING_MESSAGE
            );
          }

          @Override
          public void openSettings() {
            openSettingsDialog();
          }

          @Override
          public void runInBackground(Runnable task) {
            Thread worker = new Thread(task, "startup-credential-check");
            worker.setDaemon(true);
            worker.start();
          }

          @Override
          public void runOnUiThread(Runnable task) {
            SwingUtilities.invokeLater(task);
          }
        },
        new StartupCredentialValidator((mode, apiKey, apiSecret) -> tradingRuntimeSupport.attemptConnection(
            settingsDialog.appliedBrokerType(), mode, apiKey, apiSecret))
    );
    legalDisclosureAccepted = legalDisclosureController.loadAccepted();
    refreshStrategyRuntimeServices(
        settingsDialog.savedApiKey(selectedApplicationMode()),
        settingsDialog.savedApiSecret(selectedApplicationMode()),
        selectedApplicationMode()
    );
    settingsDialog.setStrategyExportHandler(this::exportStrategiesToFile);
    settingsDialog.setStrategyImportHandler(this::importStrategiesFromFile);
    settingsDialog.setAlpacaAccountChangedHandler(this::resetLocalTradingDataForAlpacaAccountChange);
    settingsDialog.setPortfolioSnapshotSender(email -> emailPortfolioSnapshot("On request", email));
    portfolioSnapshotEmailService.setLog(message -> SwingUtilities.invokeLater(() -> log(message)));
    strategyPollingTimer = new Timer(1000, e -> {
      triggerPollingCycle();
    });
    strategyPollingTimer.setInitialDelay(1000);
    strategyPollingTimer.start();

    headerPanel = new JPanel(new BorderLayout());
    headerPanel.setBackground(PAPER_HEADER_BG);
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

    JPanel headerInfoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
    headerInfoPanel.setOpaque(false);
    headerInfoPanel.add(createModeSwitchPanel());
    headerInfoPanel.add(headerStatus);
    headerInfoPanel.add(paperUnrealizedSummary);
    headerInfoPanel.add(headerTotalsSeparator);
    headerInfoPanel.add(liveUnrealizedSummary);

    JPanel headerControlsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    headerControlsPanel.setOpaque(false);
    headerControlsPanel.add(smartPicksButton);
    headerControlsPanel.add(refreshPortfolioButton);
    headerControlsPanel.add(riskDashboardButton);
    headerControlsPanel.add(settingsButton);

    JButton killSwitchButton = new JButton("KILL SWITCH");
    applyButtonIcon(killSwitchButton, "icons/kill-switch.svg", 15);
    killSwitchButton.setToolTipText(TooltipStyler.text(
        "Pauses active strategies, cancels open Alpaca orders for their symbols, stops polling countdowns, and saves local state. "
            + "It does not automatically liquidate positions.",
        320
    ));
    styleHeaderDangerButton(killSwitchButton);
    killSwitchButton.addActionListener(e -> killAllStrategies());
    configureButtonShortcut(killSwitchButton, KeyEvent.VK_K,
        KeyStroke.getKeyStroke(KeyEvent.VK_K, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK),
        "killSwitch");
    stopAllLiquidationsButton.setText("Stop Liquidations");
    applyButtonIcon(stopAllLiquidationsButton, "icons/kill-switch.svg", 15);
    styleHeaderDangerButton(stopAllLiquidationsButton);
    stopAllLiquidationsButton.addActionListener(e -> stopAllLiquidations());
    updateStopAllLiquidationsButton();
    headerControlsPanel.add(stopAllLiquidationsButton);
    headerControlsPanel.add(killSwitchButton);

    JPanel headerInfoWrapper = new JPanel(new GridBagLayout());
    headerInfoWrapper.setOpaque(false);
    GridBagConstraints infoWrapperGbc = new GridBagConstraints();
    infoWrapperGbc.gridx = 0;
    infoWrapperGbc.gridy = 0;
    infoWrapperGbc.weightx = 1.0;
    infoWrapperGbc.anchor = GridBagConstraints.WEST;
    infoWrapperGbc.fill = GridBagConstraints.HORIZONTAL;
    headerInfoWrapper.add(headerInfoPanel, infoWrapperGbc);

    JPanel headerControlsWrapper = new JPanel(new GridBagLayout());
    headerControlsWrapper.setOpaque(false);
    headerControlsWrapper.add(headerControlsPanel);

    headerPanel.add(headerInfoWrapper, BorderLayout.CENTER);
    headerPanel.add(headerControlsWrapper, BorderLayout.EAST);

    strategyTable.setRowHeight(34);
    strategyTable.setFillsViewportHeight(true);
    strategyTable.setRowSelectionAllowed(true);
    strategyTable.setColumnSelectionAllowed(false);
    strategyTable.setCellSelectionEnabled(false);
    // Shift-click and ctrl-click select a block of rows so the right-click menu can act on all of
    // them at once; actions that need per-row input stay disabled while more than one is selected.
    strategyTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    strategyTable.setSelectionBackground(TABLE_SELECTION_BG);
    strategyTable.setSelectionForeground(TABLE_SELECTION_FG);
    strategyTable.setRowMargin(0);
    strategyTable.setShowGrid(false);
    strategyTable.setIntercellSpacing(new Dimension(0, 0));
    StatusRowRenderer statusRowRenderer = new StatusRowRenderer();
    strategyTable.setDefaultRenderer(Object.class, statusRowRenderer);
    strategyTable.setDefaultRenderer(Number.class, statusRowRenderer);
    strategyTable.getColumnModel().getColumn(STRATEGY_PNL_COLUMN).setCellRenderer(new UnrealizedPnLRenderer());
    strategyTable.getColumnModel().getColumn(STRATEGY_PNL_PERCENT_COLUMN).setCellRenderer(new UnrealizedPnLRenderer());
    strategyTable.getColumnModel().getColumn(StrategyGridLayoutPresenter.POLLING_COLUMN_INDEX).setCellRenderer(new PollingBarRenderer());
    strategyTable.getColumnModel().getColumn(StrategyGridLayoutPresenter.ACTIONS_COLUMN_INDEX).setCellRenderer(new ActionsRenderer());
    // Preferred widths express the desired layout when there is room; minimums are kept
    // low so the table can shrink to fit a narrow window instead of overflowing and
    // clipping the right-hand Actions column. Symbol holds a short ticker, so it stays
    // tight and the spare preferred width goes mostly to Status.
    strategyTable.getColumnModel().getColumn(0).setPreferredWidth(72);
    strategyTable.getColumnModel().getColumn(0).setMinWidth(50);
    strategyTable.getColumnModel().getColumn(1).setPreferredWidth(92);
    strategyTable.getColumnModel().getColumn(1).setMinWidth(54);
    // Price block (Avg Entry, Open, Today's Low, Today's High, Current Price) — all
    // short numeric cells, kept compact so Status keeps the spare width.
    for (int priceColumn = 2; priceColumn <= 6; priceColumn++) {
      strategyTable.getColumnModel().getColumn(priceColumn).setPreferredWidth(96);
      strategyTable.getColumnModel().getColumn(priceColumn).setMinWidth(64);
    }
    // P&L % sits beside P&L and holds a short percentage, so it stays narrow.
    strategyTable.getColumnModel().getColumn(STRATEGY_PNL_PERCENT_COLUMN).setPreferredWidth(84);
    strategyTable.getColumnModel().getColumn(STRATEGY_PNL_PERCENT_COLUMN).setMinWidth(62);
    strategyTable.getColumnModel().getColumn(StrategyGridLayoutPresenter.STATUS_COLUMN_INDEX).setPreferredWidth(420);
    strategyTable.getColumnModel().getColumn(StrategyGridLayoutPresenter.STATUS_COLUMN_INDEX).setMinWidth(170);
    strategyTable.getColumnModel().getColumn(STRATEGY_TIF_COLUMN).setPreferredWidth(112);
    strategyTable.getColumnModel().getColumn(STRATEGY_TIF_COLUMN).setMinWidth(84);
    strategyTable.getColumnModel().getColumn(13).setPreferredWidth(150);
    strategyTable.getColumnModel().getColumn(13).setMinWidth(90);
    strategyTable.getColumnModel().getColumn(14).setPreferredWidth(140);
    strategyTable.getColumnModel().getColumn(14).setMinWidth(88);
    applyStrategyGridColumnLayout();

    // Handle clicks in the Actions column via a mouse listener instead of a cell editor.
    // Using mousePressed (not mouseClicked) gives instant response — mouseClicked only fires
    // when press and release land on the exact same pixel, which feels laggy.
    strategyTable.addMouseListener(new java.awt.event.MouseAdapter() {
      @Override
      public void mousePressed(java.awt.event.MouseEvent e) {
        if (maybeShowStrategyGridCopyPopup(e)) {
          return;
        }
        int viewRow = strategyTable.rowAtPoint(e.getPoint());
        int viewCol = strategyTable.columnAtPoint(e.getPoint());

        // Select the clicked row first so the full row highlights yellow immediately.
        if (viewRow >= 0 && viewRow < strategyTable.getRowCount()
            && strategyTable.getSelectedRow() != viewRow) {
          strategyTable.setRowSelectionInterval(viewRow, viewRow);
        }

        if (e.getButton() == java.awt.event.MouseEvent.BUTTON1
            && e.getClickCount() == 2
            && viewRow >= 0
            && viewCol != StrategyGridLayoutPresenter.ACTIONS_COLUMN_INDEX) {
          final int capturedRow = viewRow;
          SwingUtilities.invokeLater(() -> editStrategy(capturedRow));
          return;
        }

        if (e.getButton() == java.awt.event.MouseEvent.BUTTON1
            && viewRow >= 0 && viewRow < strategies.size()
            && viewCol == StrategyGridLayoutPresenter.POLLING_COLUMN_INDEX
            && refreshNowHotspotAtMousePoint(viewRow, e.getX())) {
          ManagedStrategy refreshTarget = strategies.get(strategyTable.convertRowIndexToModel(viewRow));
          SwingUtilities.invokeLater(() -> strategyPollingService.pollStrategyNow(refreshTarget.strategy.id()));
          return;
        }

        // Dispatch the action buttons via the same fixed-width zones used by the renderer.
        // Use invokeLater so the action runs AFTER ALL mousePressed handlers
        // (ours + BasicTableUI) have finished — this is critical because:
        //   • BasicTableUI fires its own mousePressed AFTER ours (LIFO order).
        //   • Without deferral, dialogs opened here block BasicTableUI from
        //     ever running, leaving the table in a broken state on first click.
          if (e.getButton() != java.awt.event.MouseEvent.BUTTON1) {
              return;
          }
          if (viewRow < 0 || viewRow >= strategies.size()
              || viewCol != StrategyGridLayoutPresenter.ACTIONS_COLUMN_INDEX) {
              return;
          }
        ManagedStrategy clickedStrategy = strategies.get(strategyTable.convertRowIndexToModel(viewRow));
        boolean promoteVisible = actionViewModelFor(clickedStrategy).promoteVisible();
        java.awt.Rectangle cellRect = strategyTable.getCellRect(viewRow, viewCol, false);
        int xInCell = e.getX() - cellRect.x;
        StrategyGridActionLayout.Action action = StrategyGridActionLayout.actionAt(cellRect.width, xInCell, promoteVisible);
        if (action == StrategyGridActionLayout.Action.NONE) {
          return;
        }
        final int capturedRow = viewRow;
        final StrategyGridActionLayout.Action capturedAction = action;
        SwingUtilities.invokeLater(() -> {
          switch (capturedAction) {
            case CHART -> openStockChart(capturedRow);
            case ANALYZE -> autoAnalyzeStrategy(capturedRow);
            case EDIT -> editStrategy(capturedRow);
            case TOGGLE -> togglePauseResume(capturedRow);
            case SELL -> sellStrategy(capturedRow);
            case PROMOTE -> previewLivePromotion(capturedRow);
            case DELETE -> deleteStrategy(capturedRow);
            case NONE -> {
            }
          }
        });
      }

      @Override
      public void mouseExited(java.awt.event.MouseEvent e) {
        strategyTable.setCursor(java.awt.Cursor.getDefaultCursor());
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        maybeShowStrategyGridCopyPopup(e);
      }
    });
    strategyTable.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
      @Override
      public void mouseMoved(java.awt.event.MouseEvent e) {
        // Only show HAND cursor when hovering over the action-buttons column of
        // an actual data row — NOT over the empty viewport space below the rows.
        int viewRow = strategyTable.rowAtPoint(e.getPoint());
        int viewCol = strategyTable.columnAtPoint(e.getPoint());
        boolean overAction = viewRow >= 0 && viewCol == StrategyGridLayoutPresenter.ACTIONS_COLUMN_INDEX
            && actionAtMousePoint(viewRow, e.getX()) != StrategyGridActionLayout.Action.NONE;
        boolean overRefreshNow = viewRow >= 0 && viewCol == StrategyGridLayoutPresenter.POLLING_COLUMN_INDEX
            && refreshNowHotspotAtMousePoint(viewRow, e.getX());
        if (overAction || overRefreshNow) {
          strategyTable.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        } else {
          strategyTable.setCursor(java.awt.Cursor.getDefaultCursor());
        }
      }
    });
    ToolTipManager.sharedInstance().registerComponent(strategyTable);

    // Make table sortable — click column headers to sort
    strategySorter = new TableRowSorter<>(strategyTableModel);
    strategySorter.setComparator(0, (left, right) -> compareNumericCells(left, right));
    // Price block + Market Value: Avg Entry, Open, Low, High, Current Price, Market Value.
    for (int numericColumn : new int[]{2, 3, 4, 5, 6, 9}) {
      strategySorter.setComparator(numericColumn, (left, right) -> compareNumericCells(left, right));
    }
    // P&L and P&L % sort by value, with rows that have no position ("-") last either way.
    Comparator<Object> pnlComparator = (left, right) -> {
      BigDecimal leftValue = sortableNumericValue(left);
      BigDecimal rightValue = sortableNumericValue(right);
      if (leftValue == null && rightValue == null) {
        return 0;
      }
      if (leftValue == null) {
        return 1;
      }
      if (rightValue == null) {
        return -1;
      }
      return leftValue.compareTo(rightValue);
    };
    strategySorter.setComparator(STRATEGY_PNL_COLUMN, pnlComparator);
    strategySorter.setComparator(STRATEGY_PNL_PERCENT_COLUMN, pnlComparator);
    strategySorter.setSortable(StrategyGridLayoutPresenter.POLLING_COLUMN_INDEX, false); // Polling countdown bar column — not sortable
    strategySorter.setSortable(StrategyGridLayoutPresenter.ACTIONS_COLUMN_INDEX, false); // Actions button column — not sortable
    strategySorter.setSortKeys(List.of(new RowSorter.SortKey(STRATEGY_PNL_COLUMN, SortOrder.DESCENDING)));
    applyCurrentStrategiesRowFilter();
    strategyTable.setRowSorter(strategySorter);
    strategyTable.getTableHeader().addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        maybeShowStrategyHeaderCopyPopup(e);
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        maybeShowStrategyHeaderCopyPopup(e);
      }
    });

    JScrollPane strategyGrid = new JScrollPane(strategyTable);
    strategyGrid.setOpaque(false);
    strategyGrid.setBackground(new Color(0, 0, 0, 0));
    strategyGrid.getViewport().setOpaque(false);
    strategyGrid.getViewport().setBackground(new Color(0, 0, 0, 0));
    strategyGrid.setBorder(BorderFactory.createCompoundBorder(
        new EmptyBorder(10, 0, 0, 0),
        BorderFactory.createLineBorder(TABLE_OUTER_BORDER_COLOR, 1, true)
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
    filledOrdersSorter = new TableRowSorter<>(filledOrdersTableModel);
    filledOrdersSorter.setComparator(6, (left, right) -> compareHistoryNumericCells(left, right));
    filledOrdersSorter.setComparator(7, (left, right) -> compareHistoryNumericCells(left, right));
    filledOrdersSorter.setComparator(8, (left, right) -> compareHistoryNumericCells(left, right));
    filledOrdersSorter.setComparator(9, (left, right) -> compareHistoryNumericCells(left, right));
    configureTradeHistorySorting();
    configureFilledOrdersColumnWidths();
    applyTradeHistoryRowFilter();
    filledOrdersTable.setRowSorter(filledOrdersSorter);
    filledOrdersTable.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent event) {
        maybeShowTradeHistoryRowPopup(event);
      }

      @Override
      public void mouseReleased(MouseEvent event) {
        maybeShowTradeHistoryRowPopup(event);
      }
    });

    JScrollPane filledOrdersGrid = new JScrollPane(filledOrdersTable);
    filledOrdersGrid.setOpaque(false);
    filledOrdersGrid.setBackground(new Color(0, 0, 0, 0));
    filledOrdersGrid.getViewport().setOpaque(false);
    filledOrdersGrid.getViewport().setBackground(new Color(0, 0, 0, 0));
    javax.swing.border.TitledBorder filledOrdersTitle = BorderFactory.createTitledBorder(
        BorderFactory.createLineBorder(TABLE_OUTER_BORDER_COLOR, 1, true),
        "Trade History"
    );
    filledOrdersTitle.setTitleFont(FontLoader.ui(Font.BOLD, 10f));
    filledOrdersTitle.setTitleColor(ThemeColors.color("NeuralArc.Section.titleForeground", new Color(78, 84, 94)));
    filledOrdersGrid.setBorder(BorderFactory.createCompoundBorder(
        new EmptyBorder(10, 0, 0, 0),
        filledOrdersTitle
    ));

    strategyTabs.setBorder(new EmptyBorder(0, 0, 0, 0));
    // The coordinator owns the two base tabs (All Stocks + Trade History) and inserts a
    // dynamic tab per active strategy workspace between them, re-parenting the shared grid.
    JComponent strategiesGridWrapper = wrapGridWithSearch(currentStrategiesSearchPanel, createStrategiesGridCenter(strategyGrid));
    // Per-tab figures row, pinned below the grid. The wrapper is re-parented into the
    // selected workspace tab, so these figures follow whichever workspace is being viewed.
    if (strategiesGridWrapper instanceof JPanel strategiesPanel) {
      strategiesPanel.add(createStrategiesBottomPanel(), BorderLayout.SOUTH);
    }
    JComponent historyGridWrapper = wrapGridWithSearch(tradeHistorySearchPanel, filledOrdersGrid);
    strategyWorkspaceTabs = new StrategyWorkspaceTabs(
        strategyTabs,
        strategiesGridWrapper,
        historyGridWrapper,
        workspaceService,
        () -> selectedViewMode,
        this::onWorkspaceTabSelected,
        this::currentStrategiesHeadingText,
        this::workspaceStrategiesHeadingText,
        this::tradeHistoryHeadingText
    );
    refreshNewStrategyButtonPresentation();
    installWorkspaceTabContextMenu();
    wireGridSearchFields();
    refreshGridSearchVisibility();

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
    statusStrategyCount.setToolTipText(TooltipStyler.text("Includes records shown in Current Strategies and Trade History tabs."));
    marketStatus.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
    marketStatus.setForeground(BOTTOM_STATUS_ACCENT);
    marketStatus.setVerticalAlignment(SwingConstants.CENTER);
    marketStatus.setBorder(new EmptyBorder(0, 0, 0, 12));
    streamStatus.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
    streamStatus.setForeground(BOTTOM_STATUS_ACCENT);
    streamStatus.setVerticalAlignment(SwingConstants.CENTER);
    streamStatus.setBorder(new EmptyBorder(0, 12, 0, 0));
    streamStatus.setHorizontalAlignment(SwingConstants.LEFT);
    marketValueStatus.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
    marketValueStatus.setForeground(BOTTOM_STATUS_MARKET_VALUE);
    marketValueStatus.setVerticalAlignment(SwingConstants.CENTER);
    marketValueStatus.setHorizontalAlignment(SwingConstants.LEFT);
    marketValueStatus.setBorder(new EmptyBorder(0, 0, 0, 0));
    availableFundsStatus.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
    availableFundsStatus.setForeground(BOTTOM_STATUS_MARKET_VALUE);
    availableFundsStatus.setVerticalAlignment(SwingConstants.CENTER);
    availableFundsStatus.setHorizontalAlignment(SwingConstants.LEFT);
    availableFundsStatus.setBorder(new EmptyBorder(0, 0, 0, 0));
    investedValueStatus.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
    investedValueStatus.setForeground(BOTTOM_STATUS_MARKET_VALUE);
    investedValueStatus.setVerticalAlignment(SwingConstants.CENTER);
    investedValueStatus.setHorizontalAlignment(SwingConstants.LEFT);
    investedValueStatus.setBorder(new EmptyBorder(0, 0, 0, 0));
    pendingBuyStatus.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
    pendingBuyStatus.setForeground(BOTTOM_STATUS_MARKET_VALUE);
    pendingBuyStatus.setVerticalAlignment(SwingConstants.CENTER);
    pendingBuyStatus.setHorizontalAlignment(SwingConstants.LEFT);
    pendingBuyStatus.setBorder(new EmptyBorder(0, 0, 0, 0));
    gainingPositionsStatus.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
    gainingPositionsStatus.setForeground(STATUS_TEXT_RUNNING);
    gainingPositionsStatus.setVerticalAlignment(SwingConstants.CENTER);
    gainingPositionsStatus.setHorizontalAlignment(SwingConstants.LEFT);
    gainingPositionsStatus.setBorder(new EmptyBorder(0, 0, 0, 0));
    losingPositionsStatus.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
    losingPositionsStatus.setForeground(STATUS_ERR);
    losingPositionsStatus.setVerticalAlignment(SwingConstants.CENTER);
    losingPositionsStatus.setHorizontalAlignment(SwingConstants.LEFT);
    losingPositionsStatus.setBorder(new EmptyBorder(0, 0, 0, 0));
    pendingSellStatus.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
    pendingSellStatus.setForeground(BOTTOM_STATUS_ACCENT);
    pendingSellStatus.setVerticalAlignment(SwingConstants.CENTER);
    pendingSellStatus.setHorizontalAlignment(SwingConstants.LEFT);
    pendingSellStatus.setBorder(new EmptyBorder(0, 0, 0, 0));
    cpuUsageStatus.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
    cpuUsageStatus.setForeground(BOTTOM_STATUS_ACCENT);
    cpuUsageStatus.setVerticalAlignment(SwingConstants.CENTER);
    cpuUsageStatus.setBorder(new EmptyBorder(0, 0, 0, 0));
    memoryUsageStatus.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
    memoryUsageStatus.setForeground(BOTTOM_STATUS_ACCENT);
    memoryUsageStatus.setVerticalAlignment(SwingConstants.CENTER);
    memoryUsageStatus.setBorder(new EmptyBorder(0, 0, 0, 0));
    compactStatusSummary.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
    compactStatusSummary.setForeground(BOTTOM_STATUS_ACCENT);
    compactStatusSummary.setVerticalAlignment(SwingConstants.CENTER);
    compactStatusSummary.setHorizontalAlignment(SwingConstants.LEFT);
    compactStatusSummary.setBorder(new EmptyBorder(0, 2, 0, 6));
    applyButtonIcon(statusDetailsButton, "icons/actions.svg", 14);
    styleStatusActionButton(statusDetailsButton);

    JButton faqsButton = new JButton("Faqs");
    applyButtonIcon(faqsButton, "icons/faqs.svg", 15);
    styleStatusActionButton(faqsButton);
    faqsButton.addActionListener(e -> runLoggedAction("Help & FAQ", () -> new HelpDialog(this).setVisible(true)));
    configureButtonShortcut(faqsButton, KeyEvent.VK_F,
        KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK),
        "footerFaqs");
    JButton updatesButton = new JButton("Check Updates");
    applyButtonIcon(updatesButton, "icons/check-for-updates.svg", 15);
    styleStatusActionButton(updatesButton);
    updatesButton.addActionListener(e -> {
      userActionLog.started("Check Updates");
      UpdateCheckSupport.checkForUpdates(this, updatesButton, userActionLog);
    });
    applyButtonIcon(legalDisclosureButton, "icons/legal-disclosure.svg", 15);
    styleStatusActionButton(legalDisclosureButton);
    legalDisclosureButton.addActionListener(e -> runLoggedAction("Legal Disclosure", () -> showLegalDisclosureDialog(false)));

    JButton submitFeatureButton = new JButton("Request New Feature");
    applyButtonIcon(submitFeatureButton, "icons/request-new-feature.svg", 15);
    styleStatusActionButton(submitFeatureButton);
    submitFeatureButton.addActionListener(e -> {
      userActionLog.started("Request New Feature");
      supportActionsController.openRequestNewFeatureDialog();
    });

    JButton contactUsButton = new JButton("Contact Us / Feedback");
    applyButtonIcon(contactUsButton, "icons/contact-us.svg", 15);
    styleStatusActionButton(contactUsButton);
    contactUsButton.addActionListener(e -> {
      userActionLog.started("Contact Us / Feedback");
      supportActionsController.openContactUsDialog();
    });

    applyButtonIcon(footerActionsButton, "icons/actions.svg", 15);
    styleStatusActionButton(footerActionsButton, true);
    footerActionsMenu.setBackground(new Color(46, 49, 60));
    footerActionsMenu.setOpaque(true);
    footerActionsMenu.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(new Color(70, 76, 90), 1, true),
        new EmptyBorder(4, 4, 4, 4)
    ));

    footerActionsMenu.add(createStatusMenuHeader("Support"));
    footerActionsMenu.add(createStatusMenuItem("Submit Bug", "icons/submit-bug.svg",
        this::openSubmitBugDialog));
    footerActionsMenu.add(createStatusMenuItem("Request New Feature", "icons/request-new-feature.svg",
        supportActionsController::openRequestNewFeatureDialog));
    footerActionsMenu.add(createStatusMenuItem("Contact Us / Feedback", "icons/contact-us.svg",
        supportActionsController::openContactUsDialog));
    footerActionsMenu.add(createStatusMenuSeparator());
    footerActionsMenu.add(createStatusMenuHeader("System"));
    footerActionsMenu.add(createStatusMenuItem("Check for Updates", "icons/check-for-updates.svg",
        () -> UpdateCheckSupport.checkForUpdates(this, footerActionsButton, userActionLog)));
    footerActionsMenu.add(createStatusMenuItem("Legal Disclosure", "icons/legal-disclosure.svg",
        () -> showLegalDisclosureDialog(false)));
    footerActionsMenu.add(createStatusMenuSeparator());
    footerActionsMenu.add(createStatusMenuItem("Uninstall NeuralArc", "icons/delete.svg",
        appUninstallController::confirmAndScheduleUninstall));
    footerActionsButton.addActionListener(e -> {
      if (updateAvailableNoticeActive) {
        clearUpdateAvailableNotice();
        userActionLog.started("Check Updates");
        UpdateCheckSupport.checkForUpdates(this, footerActionsButton, userActionLog);
        return;
      }
      userActionLog.started("Actions Menu");
      footerActionsMenu.show(footerActionsButton, 0, footerActionsButton.getHeight());
      userActionLog.completed("Actions Menu", "Menu opened.");
    });
    configureButtonShortcut(footerActionsButton, KeyEvent.VK_A,
        KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK),
        "footerActions");

    JLabel appLabel = new JLabel(AppMetadata.name() + "  " + AppMetadata.displayVersion() + " | Patent Pending™");
    appLabel.setFont(BASE_FONT.deriveFont(Font.PLAIN, 10f));

    appLabel.setForeground(new Color(160, 160, 170));
    appLabel.setVerticalAlignment(SwingConstants.CENTER);
    appLabel.setBorder(new EmptyBorder(0, 12, 0, 8));

    JPanel statusRight = new JPanel(new GridBagLayout());
    statusRight.setOpaque(false);
    GridBagConstraints rightGbc = new GridBagConstraints();
    rightGbc.gridy = 0;
    rightGbc.anchor = GridBagConstraints.WEST;
    rightGbc.insets = new java.awt.Insets(0, 0, 0, 10);
    rightGbc.gridx = 0;
    statusRight.add(appLabel, rightGbc);
    rightGbc.gridx = 1;
    statusRight.add(footerActionsButton, rightGbc);
    rightGbc.gridx = 2;
    rightGbc.insets = new java.awt.Insets(0, 0, 0, 0);
    statusRight.add(faqsButton, rightGbc);

    bottomStatusBars = new BottomStatusBars(
        BASE_FONT,
        BOTTOM_STATUS_ACCENT,
        PAPER_STATUS_BG,
        statusBar,
        marketStatus,
        streamStatus,
        pollingSummary,
        cpuUsageStatus,
        memoryUsageStatus,
        statusStrategyCount,
        availableFundsStatus,
        marketValueStatus,
        investedValueStatus,
        pendingBuyStatus,
        gainingPositionsStatus,
        losingPositionsStatus,
        pendingSellStatus,
        compactStatusSummary,
        statusDetailsButton,
        statusRight,
        statusBarPresenter,
        () -> streamReconnectAvailable,
        this::reconnectTradeStreamFromStatusBar
    );
    bottomStatusBars.applyModeBackground(PAPER_STATUS_BG);
    // ───────────────────────────────────────────────────────────────────────

    eventLog.setEditable(false);
    eventLog.setOpaque(false);
    eventLog.setBorder(new EmptyBorder(8, 8, 8, 8));
    eventLog.setBackground(new Color(0, 0, 0, 0));
    applyUiPolish();
    applyDataViewFonts();

    JScrollPane eventLogScrollPane = new JScrollPane(eventLog);
    eventLogScrollPane.setOpaque(false);
    eventLogScrollPane.setBorder(BorderFactory.createEmptyBorder());
    eventLogScrollPane.setBackground(new Color(0, 0, 0, 0));
    eventLogScrollPane.getViewport().setOpaque(false);
    eventLogScrollPane.getViewport().setBackground(new Color(0, 0, 0, 0));
    // Account Equity and Logs are separate sections side by side; the log has its own filter and clear.
    CollapsibleSectionPanel equitySection = new CollapsibleSectionPanel("Account Equity", accountEquityChart);
    JPanel logsContent = new JPanel(new BorderLayout(0, 4));
    logsContent.setOpaque(false);
    logsContent.add(createEventLogToolbar(), BorderLayout.NORTH);
    logsContent.add(eventLogScrollPane, BorderLayout.CENTER);
    CollapsibleSectionPanel logsSection = new CollapsibleSectionPanel("Logs", logsContent);
    JSplitPane logsColumns = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, equitySection, logsSection);
    logsColumns.setResizeWeight(0.4);
    logsColumns.setContinuousLayout(true);
    logsColumns.setDividerSize(6);
    logsColumns.setBorder(null);
    logsColumns.setOpaque(false);
    if (logsColumns.getUI() instanceof BasicSplitPaneUI logsColumnsUi) {
      logsColumnsUi.getDivider().setBorder(BorderFactory.createEmptyBorder());
      logsColumnsUi.getDivider().setBackground(
          ThemeColors.color("NeuralArc.SplitPane.divider", new Color(189, 198, 210)));
    }
    JPanel eventLogSection = new JPanel(new BorderLayout());
    eventLogSection.setOpaque(false);
    eventLogSection.add(logsColumns, BorderLayout.CENTER);
    accountEquitySampleTimer = new Timer(30_000, ignored -> {
      sampleAccountEquityAsync();
      recordWorkspaceValues();
    });
    accountEquitySampleTimer.setInitialDelay(5_000);
    accountEquitySampleTimer.start();

    // Put event log and strategy grid in a vertical split so both are always visible
    JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
        eventLogSection, strategyTabs);
    // Remember the expanded divider position so collapsing then expanding the Logs section
    // restores its previous height instead of leaving it stuck collapsed.
    final int[] expandedLogsDivider = {-1};
    // The top area shrinks only once both sections are collapsed; one open section keeps its height.
    java.beans.PropertyChangeListener topSectionsCollapsed = event -> {
      boolean nowCollapsed = equitySection.isCollapsed() && logsSection.isCollapsed();
      if (nowCollapsed == Boolean.TRUE.equals(eventLogSection.getClientProperty("collapsed"))) {
        return;
      }
      eventLogSection.putClientProperty("collapsed", nowCollapsed);
      if (nowCollapsed) {
        expandedLogsDivider[0] = splitPane.getDividerLocation();
        SwingUtilities.invokeLater(() -> {
          // Pin the top pane while collapsed so unrelated relayouts (e.g. toggling
          // another section) don't hand it space back.
          splitPane.setResizeWeight(0.0);
          splitPane.setDividerLocation(eventLogSection.getPreferredSize().height);
        });
      } else {
        SwingUtilities.invokeLater(() -> {
          splitPane.setResizeWeight(0.5);
          if (expandedLogsDivider[0] > 0) {
            splitPane.setDividerLocation(expandedLogsDivider[0]);
          } else {
            splitPane.resetToPreferredSizes();
          }
        });
      }
    };
    equitySection.addPropertyChangeListener(CollapsibleSectionPanel.COLLAPSED_PROPERTY, topSectionsCollapsed);
    logsSection.addPropertyChangeListener(CollapsibleSectionPanel.COLLAPSED_PROPERTY, topSectionsCollapsed);
    splitPane.setResizeWeight(0.5);
    splitPane.setDividerSize(6);
    splitPane.setBorder(null);
    splitPane.setOpaque(false);
    splitPane.setBackground(new Color(0, 0, 0, 0));
    if (splitPane.getUI() instanceof BasicSplitPaneUI splitPaneUi) {
      BasicSplitPaneDivider divider = splitPaneUi.getDivider();
      divider.setBorder(BorderFactory.createEmptyBorder());
      divider.setBackground(ThemeColors.color("NeuralArc.SplitPane.divider", new Color(189, 198, 210)));
    }

    CollapsibleSectionPanel positionSection = createDetailSection(positionSectionTitle, positionSummary);
    installCopyPopup(positionSection, positionSummary);
    CollapsibleSectionPanel rulesSection = createDetailSection(rulesSectionTitle, ruleState);

    JPanel detailSectionsPanel = new JPanel();
    detailSectionsPanel.setLayout(new BoxLayout(detailSectionsPanel, BoxLayout.Y_AXIS));
    detailSectionsPanel.setOpaque(false);
    positionSection.setAlignmentX(Component.LEFT_ALIGNMENT);
    rulesSection.setAlignmentX(Component.LEFT_ALIGNMENT);
    detailSectionsPanel.add(positionSection);
    detailSectionsPanel.add(Box.createVerticalStrut(8));
    detailSectionsPanel.add(rulesSection);

    // Two columns: Position and Rules Triggered on the left, the position's daily bars on the right.
    positionBarsColumn = new PositionBarsColumn(new PositionBarsColumn.Host() {
      @Override
      public PositionBarsColumn.BarsSource barsSource() {
        if (!connectionOk || runtimeApiKey.isBlank()) {
          return null;
        }
        HttpAlpacaMarketDataApi api = new HttpAlpacaMarketDataApi(runtimeApiKey, runtimeApiSecret);
        return api::getDailyBars;
      }

      @Override
      public void openFullChart(ManagedStrategy entry) {
        openStockChart(entry);
      }
    }, uiPollingExecutor, java.time.Clock.systemDefaultZone());
    CollapsibleSectionPanel positionBarsSection = new CollapsibleSectionPanel("Position Bars", positionBarsColumn);
    CollapsibleSectionPanel workspaceValueSection = new CollapsibleSectionPanel("Workspace Value", workspaceValueChart);
    JSplitPane chartColumns = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, positionBarsSection, workspaceValueSection);
    chartColumns.setResizeWeight(0.5);
    chartColumns.setContinuousLayout(true);
    chartColumns.setDividerSize(6);
    chartColumns.setBorder(null);
    chartColumns.setOpaque(false);
    if (chartColumns.getUI() instanceof BasicSplitPaneUI chartColumnsUi) {
      chartColumnsUi.getDivider().setBorder(BorderFactory.createEmptyBorder());
      chartColumnsUi.getDivider().setBackground(
          ThemeColors.color("NeuralArc.SplitPane.divider", new Color(189, 198, 210)));
    }
    // Three columns: Position and Rules Triggered, the position's bars, the workspace's value today.
    JSplitPane detailColumns = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, detailSectionsPanel, chartColumns);
    detailColumns.setResizeWeight(0.34);
    detailColumns.setContinuousLayout(true);
    detailColumns.setDividerSize(6);
    detailColumns.setBorder(null);
    detailColumns.setOpaque(false);
    if (detailColumns.getUI() instanceof BasicSplitPaneUI detailColumnsUi) {
      detailColumnsUi.getDivider().setBorder(BorderFactory.createEmptyBorder());
      detailColumnsUi.getDivider().setBackground(
          ThemeColors.color("NeuralArc.SplitPane.divider", new Color(189, 198, 210)));
    }

    JPanel statusPanel = new JPanel(new BorderLayout(0, 10));
    statusPanel.setOpaque(false);
    statusPanel.setBorder(new EmptyBorder(8, 0, 14, 0));
    statusPanel.add(detailColumns, BorderLayout.CENTER);

    add(headerPanel, BorderLayout.NORTH);
    add(splitPane, BorderLayout.CENTER);

    // Wrap status panels + status bar into one SOUTH panel
    JPanel southWrapper = new JPanel(new BorderLayout());
    southWrapper.setBorder(new EmptyBorder(8, 0, 0, 0));
    southWrapper.add(statusPanel, BorderLayout.CENTER);
    JPanel footerBars = composeFooterBars(bottomStatusBars.portfolioBarPanel(), bottomStatusBars.mainBarPanel());
    southWrapper.add(footerBars, BorderLayout.SOUTH);
    add(southWrapper, BorderLayout.SOUTH);

    wireEvents();
    updateLegalDisclosureUiState();
    settingsDialog.setConnectionVerifier(request -> runConnectionTest(
        request.brokerType(),
        request.applicationMode(),
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

      // Only the 250ms countdown-repaint timer pauses while minimized, purely to save CPU.
      // strategyPollingTimer (and the background StrategyPollingService it drives) keeps
      // running unconditionally — stop-loss/fill detection must never go stale just because
      // the window lost focus.
      @Override
      public void windowIconified(WindowEvent e) {
        pollingIndicatorTimer.stop();
      }

      @Override
      public void windowDeiconified(WindowEvent e) {
        pollingIndicatorTimer.start();
        strategyTable.repaint();
        filledOrdersTable.repaint();
      }
    });
    setExtendedState(getExtendedState() | JFrame.MAXIMIZED_BOTH);
    setLocationRelativeTo(null);
    startBackgroundUpdateAvailabilityCheck();
    SwingUtilities.invokeLater(portfolioCaptureRuns::restoreAll);
    applyViewModeTheme();
  }


  private JPanel createModeSwitchPanel() {
    JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    panel.setOpaque(false);
    ButtonGroup group = new ButtonGroup();
    configureModeToggle(paperViewButton, StrategyMode.PAPER, "Show and operate only on Alpaca Paper data.");
    configureModeToggle(liveViewButton, StrategyMode.LIVE, "Show and operate only on Alpaca Live data.");
    group.add(paperViewButton);
    group.add(liveViewButton);
    panel.add(paperViewButton);
    panel.add(liveViewButton);
    syncModeToggleSelection();
    return panel;
  }

  private void configureModeToggle(JToggleButton button, StrategyMode mode, String tooltip) {
    button.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
    button.setFocusPainted(false);
    button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    button.setToolTipText(TooltipStyler.text(tooltip));
    button.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(new Color(90, 94, 108), 1),
        new EmptyBorder(3, 10, 3, 10)
    ));
    button.addActionListener(event -> switchViewMode(mode));
  }

  private void switchViewMode(StrategyMode requestedMode) {
    if (updatingModeButtons) {
      return;
    }
    StrategyMode safeMode = requestedMode == null ? StrategyMode.PAPER : requestedMode;
    if (safeMode == selectedViewMode) {
      syncModeToggleSelection();
      return;
    }
    if (safeMode == StrategyMode.LIVE && !confirmLiveViewSwitch()) {
      syncModeToggleSelection();
      return;
    }
    selectedViewMode = safeMode;
    selectedStrategyId = null;
    applyAvailableFundsTextForMode(selectedApplicationMode());
    syncModeToggleSelection();
    applyViewModeTheme();
    ViewModeSwitchRefreshFlow.apply(
        this::syncStrategiesFromRepository,
        this::refreshStrategyTableData,
        this::updateSelectedStrategy,
        this::refreshPanels,
        this::updateStatusBar
    );
    // Schedules are mode-scoped too: a paper schedule must not keep firing while Live is on screen.
    applyHistoryReentrySchedule();
    // Workspaces are mode-scoped: rebuild the dynamic tabs for the newly selected mode.
    if (strategyWorkspaceTabs != null) {
      strategyWorkspaceTabs.rebuild();
    }
    updateGapRocketScheduleBadge(gapAndGoCoordinator.currentSchedule());
    refreshCapturePortfolioModeVisibility();
    updateHeaderModeStatus(currentBrokerType);
    rebindRuntimeToSelectedMode();
    userActionLog.completed("Mode Switch", "Viewing " + selectedViewMode.name() + " data.");
    log("[MODE] Switched app view to " + selectedViewMode.name() + ". Grids and actions are scoped to this mode.");
  }

  private ApplicationMode selectedApplicationMode() {
    return selectedViewMode == StrategyMode.LIVE ? ApplicationMode.LIVE : ApplicationMode.PAPER;
  }

  private String selectedModeLabel() {
    return selectedViewMode == StrategyMode.LIVE ? "Live" : "Paper";
  }

  private String savedApiKeyForSelectedMode() {
    return settingsDialog.savedApiKey(selectedApplicationMode());
  }

  private String savedApiSecretForSelectedMode() {
    return settingsDialog.savedApiSecret(selectedApplicationMode());
  }

  /**
   * Points the strategy poller, the order services and the trade stream at the selected mode. Only the selected mode is polled and streamed (Live keeps a companion poller while
   * Paper is viewed, so live stop-losses never go unwatched; Paper gets none while Live is viewed).
   */
  private void rebindRuntimeToSelectedMode() {
    refreshStrategyRuntimeServices(
        savedApiKeyForSelectedMode(),
        savedApiSecretForSelectedMode(),
        selectedApplicationMode()
    );
    restartTradingEventStreamForSelectedMode();
  }

  private void restartTradingEventStreamForSelectedMode() {
    String apiKey = savedApiKeyForSelectedMode();
    String apiSecret = savedApiSecretForSelectedMode();
    startTradingEventStreamIfConfigured(apiKey, apiSecret);
  }

  private boolean confirmLiveViewSwitch() {
    if (liveModeConfirmedThisSession) {
      return true;
    }
    if (!AppMetadata.liveTradingEnabled()) {
      JOptionPane.showMessageDialog(
          this,
          "Live trading is disabled in application configuration.",
          "Live Mode Disabled",
          JOptionPane.WARNING_MESSAGE
      );
      return false;
    }
    if (settingsDialog.savedApiKey(ApplicationMode.LIVE).isBlank()
        || settingsDialog.savedApiSecret(ApplicationMode.LIVE).isBlank()) {
      JOptionPane.showMessageDialog(
          this,
          "Live Alpaca credentials are required before switching to Live view.",
          "Live Credentials Required",
          JOptionPane.WARNING_MESSAGE
      );
      return false;
    }
    String message = "<html><body style='width:360px'>"
        + "<b>Switch to LIVE mode?</b><br><br>"
        + "All grids, portfolio totals, history, and actions will show and operate only on Live strategies. "
        + "Orders submitted while Live is selected can affect real funds."
        + "</body></html>";
    int choice = JOptionPane.showConfirmDialog(
        this,
        message,
        "Confirm Live Mode",
        JOptionPane.YES_NO_OPTION,
        JOptionPane.WARNING_MESSAGE
    );
    liveModeConfirmedThisSession = choice == JOptionPane.YES_OPTION;
    return liveModeConfirmedThisSession;
  }

  private void syncModeToggleSelection() {
    updatingModeButtons = true;
    paperViewButton.setSelected(selectedViewMode == StrategyMode.PAPER);
    liveViewButton.setSelected(selectedViewMode == StrategyMode.LIVE);
    updatingModeButtons = false;
    styleModeToggle(paperViewButton, selectedViewMode == StrategyMode.PAPER, false);
    styleModeToggle(liveViewButton, selectedViewMode == StrategyMode.LIVE, true);
  }

  private void styleModeToggle(JToggleButton button, boolean selected, boolean live) {
    if (selected) {
      button.setOpaque(true);
      button.setContentAreaFilled(true);
      button.setForeground(Color.WHITE);
      button.setBackground(live ? new Color(183, 28, 28) : new Color(25, 118, 210));
    } else {
      button.setOpaque(true);
      button.setContentAreaFilled(true);
      button.setForeground(new Color(205, 210, 220));
      button.setBackground(new Color(55, 58, 70));
    }
  }

  private void applyViewModeTheme() {
    boolean live = selectedViewMode == StrategyMode.LIVE;
    if (headerPanel != null) {
      headerPanel.setBackground(live ? LIVE_HEADER_BG : PAPER_HEADER_BG);
    }
    if (bottomStatusBars != null) {
      bottomStatusBars.applyModeBackground(live ? LIVE_STATUS_BG : PAPER_STATUS_BG);
    }
    styleModeToggle(paperViewButton, selectedViewMode == StrategyMode.PAPER, false);
    styleModeToggle(liveViewButton, selectedViewMode == StrategyMode.LIVE, true);
    applyStrategyGridColumnLayout();
    repaint();
  }

  private void applyStrategyGridColumnLayout() {
    if (strategyTable == null || strategyTable.getColumnModel().getColumnCount() <= StrategyGridLayoutPresenter.ACTIONS_COLUMN_INDEX) {
      return;
    }
    applyColumnWidth(
        strategyTable.getColumnModel().getColumn(StrategyGridLayoutPresenter.POLLING_COLUMN_INDEX),
        strategyGridLayoutPresenter.pollingColumnWidth()
    );
    applyFixedColumnWidth(
        strategyTable.getColumnModel().getColumn(StrategyGridLayoutPresenter.ACTIONS_COLUMN_INDEX),
        strategyGridLayoutPresenter.actionsColumnWidth(selectedViewMode == StrategyMode.PAPER)
    );
  }

  private void applyColumnWidth(TableColumn column, StrategyGridLayoutPresenter.ColumnWidth width) {
    column.setPreferredWidth(width.preferred());
    column.setMinWidth(width.minimum());
  }

  // The action buttons are drawn at fixed pixel zones, so the column must stay an exact
  // width the user cannot drag — otherwise the hit zones and rendered buttons drift apart.
  private void applyFixedColumnWidth(TableColumn column, StrategyGridLayoutPresenter.ColumnWidth width) {
    column.setMinWidth(width.minimum());
    column.setPreferredWidth(width.preferred());
    column.setMaxWidth(width.preferred());
    column.setResizable(false);
  }

  private void applyUiPolish() {
    applyFontRecursively(this);

    styleHeaderButton(addStrategyButton);
    styleHeaderButton(smartPicksButton, true);
    styleHeaderButton(refreshPortfolioButton);
    styleHeaderButton(riskDashboardButton);
    styleCompactHeaderButton(capturePortfolioButton);
    styleHeaderButton(portfolioActionsButton, true);
    styleHeaderButton(settingsButton);
    applyButtonIcon(addStrategyButton, "icons/add-stock-strategy.svg", 16);
    applyButtonIcon(smartPicksButton, "icons/smart-picks.svg", 16);
    applyButtonIcon(refreshPortfolioButton, "icons/refresh.svg", 16);
    applyButtonIcon(riskDashboardButton, "icons/portfolio.svg", 16);
    applyButtonIcon(capturePortfolioButton, "icons/portfolio.svg", 16);
    applyButtonIcon(portfolioActionsButton, "icons/portfolio.svg", 16);
    applyButtonIcon(settingsButton, "icons/settings.svg", 16);
    riskDashboardButton.setToolTipText(TooltipStyler.text(
        "Open the Strategy Risk Dashboard: exposure charts, open P&L, and risk advisories "
            + "(possible losers to protect, possible winners-in-losing to wait on, and cut-loss candidates).",
        340
    ));
    riskDashboardButton.addActionListener(e -> openRiskDashboard());
    refreshNewStrategyButtonPresentation();
    refreshPortfolioButton.setToolTipText(TooltipStyler.text(
        "Refetches Alpaca positions and quote data, updates matching Current Strategies, and recalculates grid P&L.",
        320
    ));
    capturePortfolioButton.setToolTipText(capturePortfolioDefaultTooltip());
    smartPicksButton.setToolTipText(TooltipStyler.text(
        "Open strategy picker: High Volatility Movers or Diversified Leaders (Top 20). Both run Auto Analyze with high-risk short-term review tabs.",
        320
    ));
    smartPicksButton.setEnabled(false);
  }

  private void applyDataViewFonts() {
    eventLog.setFont(FontLoader.ui(Font.PLAIN, 10f));
    strategyTable.setFont(FontLoader.ui(Font.PLAIN, 12f));
    strategyTable.getTableHeader().setFont(FontLoader.ui(Font.BOLD, 10f));
    strategyTable.getTableHeader().setOpaque(true);
    strategyTable.getTableHeader().setBackground(ThemeColors.color("NeuralArc.TableHeader.background", new Color(228, 233, 240)));
    strategyTable.getTableHeader().setForeground(ThemeColors.color("NeuralArc.TableHeader.foreground", new Color(82, 88, 98)));
    strategyTable.getTableHeader().setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(204, 210, 218)));
    filledOrdersTable.setFont(FontLoader.ui(Font.PLAIN, 12f));
    filledOrdersTable.getTableHeader().setFont(FontLoader.ui(Font.BOLD, 10f));
    filledOrdersTable.getTableHeader().setOpaque(true);
    filledOrdersTable.getTableHeader().setBackground(ThemeColors.color("NeuralArc.TableHeader.background", new Color(228, 233, 240)));
    filledOrdersTable.getTableHeader().setForeground(ThemeColors.color("NeuralArc.TableHeader.foreground", new Color(82, 88, 98)));
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

  private void setPortfolioRefreshButtonBusy(boolean busy) {
    refreshPortfolioButton.setEnabled(!busy);
    refreshPortfolioButton.setText(busy ? "Refreshing..." : "Refresh");
  }

  private CollapsibleSectionPanel createDetailSection(JLabel titleLabel, JComponent contentLabel) {
    titleLabel.setForeground(ThemeColors.color("NeuralArc.Section.titleForeground", new Color(78, 84, 94)));
    contentLabel.setForeground(ThemeColors.color("NeuralArc.Detail.foreground", new Color(35, 35, 45)));
    if (contentLabel instanceof JLabel label) {
      label.setVerticalAlignment(SwingConstants.TOP);
    }
    contentLabel.setBorder(new EmptyBorder(2, 0, 0, 0));

    JPanel contentPanel = new JPanel(new BorderLayout());
    contentPanel.setOpaque(false);
    contentPanel.add(contentLabel, BorderLayout.CENTER);
    return new CollapsibleSectionPanel(titleLabel.getText(), contentPanel);
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
  private static final Color DARK_BTN_BG = ThemeColors.color("NeuralArc.Button.background", new Color(60, 60, 90));
  private static final Color DARK_BTN_BORDER = ThemeColors.color("NeuralArc.Button.border", new Color(100, 100, 160));
  private static final Color DARK_BTN_BG_HOVER = ThemeColors.color("NeuralArc.Button.hoverBackground", new Color(80, 80, 118));
  private static final Color DARK_BTN_BORDER_HOVER = ThemeColors.color("NeuralArc.Button.hoverBorder", new Color(128, 128, 196));
  private static final Color DARK_BTN_BG_PRESSED = ThemeColors.color("NeuralArc.Button.pressedBackground", new Color(42, 42, 68));
  private static final Color DARK_BTN_BORDER_PRESSED = ThemeColors.color("NeuralArc.Button.pressedBorder", new Color(85, 85, 148));
  private static final Color DARK_BTN_FG = ThemeColors.color("NeuralArc.Button.foreground", new Color(230, 230, 255));

  private void styleHeaderButton(JButton button) {
    styleHeaderButton(button, false);
  }

  private void styleHeaderButton(JButton button, boolean dropdown) {
    button.setFocusPainted(false);
    button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
    button.setFont(FontLoader.ui(Font.BOLD, 11f));
    button.setForeground(DARK_BTN_FG);
    button.setBackground(DARK_BTN_BG);
    button.setOpaque(true);
    button.setContentAreaFilled(true);
    button.setRolloverEnabled(true);
    javax.swing.border.Border inner = dropdownAwareInner(dropdown, new EmptyBorder(4, 10, 4, dropdown ? 8 : 10));
    javax.swing.border.Border pressedInner = dropdownAwareInner(dropdown, new EmptyBorder(3, 9, 3, dropdown ? 7 : 9));
    button.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(DARK_BTN_BORDER, 1, true),
        inner
    ));
    button.setIconTextGap(6);
    installDarkButtonInteraction(button, inner, pressedInner);
  }

  /**
   * Inserts the dropdown chevron zone between the line border and the padding.
   */
  private static javax.swing.border.Border dropdownAwareInner(boolean dropdown, EmptyBorder padding) {
    return dropdown
        ? BorderFactory.createCompoundBorder(new DropdownChevronBorder(), padding)
        : padding;
  }

  private void styleCompactHeaderButton(JButton button) {
    button.setFocusPainted(false);
    button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
    button.setForeground(new Color(230, 230, 255));
    button.setBackground(DARK_BTN_BG);
    button.setOpaque(true);
    button.setContentAreaFilled(true);
    button.setRolloverEnabled(true);
    button.setFont(FontLoader.ui(Font.BOLD, 11f));
    button.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(DARK_BTN_BORDER, 1, true),
        new EmptyBorder(3, 8, 3, 8)
    ));
    button.setIconTextGap(6);
    installDarkButtonInteraction(button,
        new EmptyBorder(3, 8, 3, 8),
        new EmptyBorder(2, 7, 2, 7));
  }

  private void styleStatusActionButton(JButton button) {
    styleStatusActionButton(button, false);
  }

  private void styleStatusActionButton(JButton button, boolean dropdown) {
    button.setFocusPainted(false);
    button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
    button.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
    button.setForeground(DARK_BTN_FG);
    button.setBackground(DARK_BTN_BG);
    button.setOpaque(true);
    javax.swing.border.Border inner = dropdownAwareInner(dropdown, new EmptyBorder(3, 10, 3, dropdown ? 8 : 10));
    javax.swing.border.Border pressedInner = dropdownAwareInner(dropdown, new EmptyBorder(2, 9, 2, dropdown ? 7 : 9));
    button.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(DARK_BTN_BORDER, 1, true),
        inner
    ));
    button.setMargin(new java.awt.Insets(3, 10, 3, 10));
    button.setIconTextGap(6);
    installDarkButtonInteraction(button, inner, pressedInner);
  }

  private void installPremiumActionButtonStyle(JButton button) {
    button.setUI(new BasicButtonUI() {
      @Override
      public void paint(Graphics g, JComponent c) {
        JButton b = (JButton) c;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color top = b.getModel().isRollover() ? new Color(58, 132, 255) : new Color(42, 101, 225);
        Color bottom = b.getModel().isRollover() ? new Color(120, 84, 255) : new Color(91, 63, 204);
        if (portfolioCaptureRuns != null && portfolioCaptureRuns.monitoringActive(selectedCaptureScope())) {
          top = capturePortfolioPulseOn ? new Color(36, 140, 108) : CAPTURE_ACTIVE_BG;
          bottom = capturePortfolioPulseOn ? new Color(16, 128, 98) : new Color(24, 152, 118);
        }
        g2.setPaint(new java.awt.GradientPaint(0, 0, top, 0, c.getHeight(), bottom));
        g2.fillRoundRect(0, 0, c.getWidth() - 1, c.getHeight() - 1, 18, 18);
        g2.dispose();
        super.paint(g, c);
      }
    });
    button.setContentAreaFilled(false);
    button.setOpaque(false);
    button.setForeground(Color.WHITE);
    button.setBorder(new EmptyBorder(7, 13, 7, 13));
  }

  private JMenuItem createStatusMenuItem(String text, String iconPath, Runnable action) {
    JMenuItem item = new JMenuItem(text, SvgIconLoader.load(iconPath, 14));
    item.setFont(BASE_FONT.deriveFont(Font.PLAIN, 12f));
    item.setForeground(new Color(225, 228, 236));
    item.setBackground(new Color(46, 49, 60));
    item.setOpaque(true);
    item.setBorder(new EmptyBorder(8, 10, 8, 12));
    item.setIconTextGap(10);
    item.addActionListener(e -> {
      userActionLog.started(text);
      try {
        action.run();
      } catch (RuntimeException ex) {
        userActionLog.failed(text, ex.getMessage());
        throw ex;
      }
    });
    return item;
  }

  /**
   * Menu item that prints its one-line description under the label. The action log still records the plain label, not the rendered HTML.
   */
  private JMenuItem createDescribedStatusMenuItem(String label, String description, String iconPath, Runnable action) {
    JMenuItem item = createStatusMenuItem(label, iconPath, action);
    item.setText(PortfolioActionsMenu.itemHtml(label, description));
    return item;
  }

  private JMenu createStatusSubMenu(String label, String iconPath) {
    JMenu submenu = new JMenu(label);
    submenu.setIcon(SvgIconLoader.load(iconPath, 14));
    submenu.setFont(BASE_FONT.deriveFont(Font.PLAIN, 12f));
    submenu.setForeground(new Color(225, 228, 236));
    submenu.setBackground(new Color(46, 49, 60));
    submenu.setOpaque(true);
    submenu.setBorder(new EmptyBorder(8, 10, 8, 12));
    submenu.setIconTextGap(10);
    submenu.getPopupMenu().setBackground(new Color(46, 49, 60));
    submenu.getPopupMenu().setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(new Color(70, 76, 90), 1, true),
        new EmptyBorder(4, 4, 4, 4)
    ));
    return submenu;
  }

  private JMenuItem createStatusMenuHeader(String text) {
    JMenuItem header = new JMenuItem(text);
    header.setEnabled(false);
    header.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
    header.setForeground(new Color(155, 165, 184));
    header.setBackground(new Color(46, 49, 60));
    header.setOpaque(true);
    header.setBorder(new EmptyBorder(6, 10, 4, 12));
    return header;
  }

  private JMenuItem createStatusMenuSeparator() {
    JMenuItem separator = new JMenuItem();
    separator.setEnabled(false);
    separator.setOpaque(true);
    separator.setBackground(new Color(46, 49, 60));
    separator.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(70, 76, 90)));
    separator.setPreferredSize(new java.awt.Dimension(220, 3));
    return separator;
  }

  private void startBackgroundUpdateAvailabilityCheck() {
    if (!AppMetadata.updateCheckEnabled() || AppMetadata.githubLatestReleaseUrl().isBlank()) {
      return;
    }
    SwingWorker<GitHubReleaseUpdateService.UpdateCheckResult, Void> worker = new SwingWorker<>() {
      @Override
      protected GitHubReleaseUpdateService.UpdateCheckResult doInBackground() throws Exception {
        return new GitHubReleaseUpdateService(AppMetadata.githubLatestReleaseUrl())
            .checkForUpdates(AppMetadata.version());
      }

      @Override
      protected void done() {
        try {
          GitHubReleaseUpdateService.UpdateCheckResult result = get();
          if (result.updateAvailable()) {
            showUpdateAvailableNotice(result);
          }
        } catch (Exception ex) {
          log("[Update Check] Background update availability check failed: " + ex.getMessage());
        }
      }
    };
    worker.execute();
  }

  private void showUpdateAvailableNotice(GitHubReleaseUpdateService.UpdateCheckResult result) {
    updateAvailableNoticeActive = true;
    footerActionsButton.setText("Update Available");
    footerActionsButton.setToolTipText(TooltipStyler.text(
        "Newer version " + result.latestVersion() + " is available. Click to check updates.",
        300
    ));
    startUpdateAvailableFlash();
  }

  private void startUpdateAvailableFlash() {
    if (updateAvailableFlashTimer == null) {
      updateAvailableFlashTimer = new Timer(650, ignored -> applyUpdateAvailableFlash());
      updateAvailableFlashTimer.setInitialDelay(0);
    }
    updateAvailableFlashTimer.start();
  }

  private void applyUpdateAvailableFlash() {
    if (!updateAvailableNoticeActive) {
      clearUpdateAvailableNotice();
      return;
    }
    updateAvailableFlashOn = !updateAvailableFlashOn;
    Color background = updateAvailableFlashOn ? UPDATE_FLASH_BG : DARK_BTN_BG;
    Color border = updateAvailableFlashOn ? UPDATE_FLASH_BORDER : DARK_BTN_BORDER;
    footerActionsButton.setBackground(background);
    footerActionsButton.setForeground(Color.WHITE);
    footerActionsButton.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(border, 1, true),
        dropdownAwareInner(true, new EmptyBorder(3, 10, 3, 10))
    ));
  }

  private void clearUpdateAvailableNotice() {
    updateAvailableNoticeActive = false;
    updateAvailableFlashOn = false;
    if (updateAvailableFlashTimer != null) {
      updateAvailableFlashTimer.stop();
    }
    footerActionsButton.setText("Actions");
    footerActionsButton.setToolTipText(null);
    footerActionsButton.setForeground(new Color(220, 220, 255));
    footerActionsButton.setBackground(DARK_BTN_BG);
    footerActionsButton.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(DARK_BTN_BORDER, 1, true),
        dropdownAwareInner(true, new EmptyBorder(3, 10, 3, 10))
    ));
  }

  private void runLoggedAction(String actionName, Runnable action) {
    userActionLog.started(actionName);
    try {
      action.run();
      userActionLog.completed(actionName);
    } catch (RuntimeException ex) {
      userActionLog.failed(actionName, ex.getMessage());
      throw ex;
    }
  }

  /**
   * Attaches hover + press mouse feedback to a dark-background button. Guards against double-installation via a client property.
   *
   * @param normalInner  inner EmptyBorder for the normal/hover state
   * @param pressedInner inner EmptyBorder for the pressed state (1 px less each side to compensate for the thicker 2-px outer border)
   */
  private void installDarkButtonInteraction(JButton button,
      javax.swing.border.Border normalInner,
      javax.swing.border.Border pressedInner) {
    if (Boolean.TRUE.equals(button.getClientProperty("darkBtnInteractInstalled"))) {
      return;
    }
    button.addMouseListener(new MouseAdapter() {
      // While a button is flashing (e.g. active Capture/Liquidate monitoring), suppress hover
      // and press restyling so the flashing animation stays clean and consistent.
      private boolean flashing() {
        return !ButtonHoverPolicy.hoverEnabled(button);
      }

      @Override
      public void mouseEntered(MouseEvent e) {
        if (flashing()) {
          return;
        }
        if (button.isEnabled()) {
          button.setBackground(DARK_BTN_BG_HOVER);
          button.setBorder(BorderFactory.createCompoundBorder(
              BorderFactory.createLineBorder(DARK_BTN_BORDER_HOVER, 1, true),
              normalInner));
        }
      }

      @Override
      public void mouseExited(MouseEvent e) {
        if (flashing()) {
          return;
        }
        button.setBackground(DARK_BTN_BG);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(DARK_BTN_BORDER, 1, true),
            normalInner));
      }

      @Override
      public void mousePressed(MouseEvent e) {
        if (flashing()) {
          return;
        }
        if (button.isEnabled() && e.getButton() == MouseEvent.BUTTON1) {
          button.setBackground(DARK_BTN_BG_PRESSED);
          button.setBorder(BorderFactory.createCompoundBorder(
              BorderFactory.createLineBorder(DARK_BTN_BORDER_PRESSED, 2, true),
              pressedInner));
        }
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        if (flashing()) {
          return;
        }
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
   * Installs a right-click "Copy to Clipboard" popup on {@code panel} and its {@code contentLabel}.  The copied text is the current text of the label.
   */
  private void installCopyPopup(JPanel panel, javax.swing.text.JTextComponent contentLabel) {
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
      @Override
      public void mousePressed(MouseEvent e) {
        maybeShow(e);
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        maybeShow(e);
      }

      private void maybeShow(MouseEvent e) {
        if (e.isPopupTrigger()) {
          popup.show(e.getComponent(), e.getX(), e.getY());
        }
      }
    };
    panel.addMouseListener(handler);
    contentLabel.addMouseListener(handler);
  }

  private void openSubmitBugDialog() {
    SubmitBugDialog dialog = new SubmitBugDialog(
        this,
        settingsDialog.getUserEmail(),
        FeedbackEmailService.fromConfiguration()
    );
    if (dialog.showDialog()) {
      log("[Submit Bug] Sent and copied to " + settingsDialog.getUserEmail());
      userActionLog.completed("Submit Bug", "Bug report sent.");
    } else {
      userActionLog.canceled("Submit Bug");
    }
  }

  private void openPortfolioCaptureDialog() {
    if (selectedCapturePortfolioUiKey() == null) {
      return;
    }
    // Every mode + workspace owns its own run, so this dialog only ever sees and controls the
    // run of the tab it was opened from.
    PortfolioCaptureRuns.Scope scope = selectedCaptureScope();
    PortfolioCaptureController controller = portfolioCaptureRuns.controller(scope);
    userActionLog.started("Liquidate Portfolio");
    PortfolioCaptureDialog dialog = new PortfolioCaptureDialog(
        this,
        controller::currentSnapshot,
        config -> {
          userActionLog.started("Liquidate Portfolio Now");
          controller.executeNow(config);
        },
        config -> {
          userActionLog.started("Liquidate Portfolio Monitoring");
          controller.activateMonitoring(config);
          userActionLog.completed("Liquidate Portfolio Monitoring", "Monitoring activated.");
        },
        () -> {
          controller.emergencyStop();
          userActionLog.completed("Liquidate Portfolio Monitoring", "Monitoring deactivated.");
        },
        controller.monitoringActive()
    );
    boolean changed = dialog.showDialog();
    if (!changed) {
      userActionLog.canceled("Liquidate Portfolio");
    }
  }

  /**
   * The mode and workspace of the strategy tab being viewed.
   */
  private PortfolioCaptureRuns.Scope selectedCaptureScope() {
    return new PortfolioCaptureRuns.Scope(selectedViewMode, selectedWorkspaceId);
  }

  private PortfolioCaptureUiStateStore.Key captureUiKey(PortfolioCaptureRuns.Scope scope) {
    return capturePortfolioUiKey(scope.mode(), scope.workspaceId());
  }

  private void updateCapturePortfolioUi(PortfolioCaptureRuns.Scope scope, boolean active) {
    PortfolioCaptureUiStateStore.Key key = captureUiKey(scope);
    if (!active) {
      captureAutomationStates.remove(key);
    }
    String indicatorText = active ? captureIndicatorText(scope) : "";
    capturePortfolioUiStates.update(key, capturePortfolioUiStates.state(key)
        .withButton("Liquidate Portfolio", true)
        .withIndicator(indicatorText, active)
        .withPulse(active));
    applySelectedCapturePortfolioState();
    updateStopAllLiquidationsButton();
  }

  private void updateCapturePortfolioIndicator(PortfolioCaptureRuns.Scope scope) {
    PortfolioCaptureUiStateStore.Key key = captureUiKey(scope);
    String indicatorText = captureIndicatorText(scope);
    capturePortfolioUiStates.update(key, capturePortfolioUiStates.state(key)
        .withIndicator(indicatorText, portfolioCaptureRuns.monitoringActive(scope) && !indicatorText.isBlank()));
    if (key.equals(selectedCapturePortfolioUiKey())) {
      applySelectedCapturePortfolioState();
    }
  }

  /**
   * Builds the liquidation status line from the very snapshot the monitor evaluates its target against — open (unrealized) P&L of the rows that would be sold, over the capital
   * still at risk in them. Every figure on the line therefore shares one basis and predicts what the next tick will do. Reporting a different total here (the tab's
   * realized-inclusive P&L) against the calculator's own progress figure is what previously produced a meaningless 100% beside a losing P&L; banked realized P&L cannot be captured
   * again and so has no place on this line.
   */
  private String captureIndicatorText(PortfolioCaptureRuns.Scope scope) {
    PortfolioCaptureController controller = portfolioCaptureRuns.controller(scope);
    PortfolioCaptureConfig config = controller.activeConfig();
    if (config == null) {
      return "";
    }
    PortfolioCaptureSnapshot context = controller.previewSnapshot(config);
    if (config.mode() == PortfolioCaptureMode.PULLBACK_MONITORING) {
      return PortfolioCaptureIndicatorPresenter.pullbackMonitoringText(config, context.targetBasis().pnl(),
          context.targetBasis().investment(), controller.pullbackArmed(), controller.peakProfit());
    }
    return PortfolioCaptureIndicatorPresenter.targetMonitoringText(
        config, context.targetBasis().pnl(), context.targetBasis().investment());
  }

  private String captureIndicatorExplanation(PortfolioCaptureRuns.Scope scope) {
    PortfolioCaptureController controller = portfolioCaptureRuns.controller(scope);
    PortfolioCaptureConfig config = controller.activeConfig();
    if (config == null) {
      return "";
    }
    PortfolioCaptureSnapshot context = controller.previewSnapshot(config);
    if (config.mode() == PortfolioCaptureMode.PULLBACK_MONITORING) {
      return PortfolioCaptureIndicatorPresenter.pullbackMonitoringExplanation(config);
    }
    return PortfolioCaptureIndicatorPresenter.targetMonitoringExplanation(
        config, context.targetBasis().pnl(), context.targetBasis().investment());
  }

  /**
   * Enables Stop Liquidations only while at least one monitor is running in any mode or workspace.
   */
  private void updateStopAllLiquidationsButton() {
    List<PortfolioCaptureRuns.Scope> active = portfolioCaptureRuns.activeScopes();
    stopAllLiquidationsButton.setEnabled(!active.isEmpty());
    stopAllLiquidationsButton.setToolTipText(TooltipStyler.text(active.isEmpty()
            ? "No Liquidate Portfolio monitor is running in any workspace."
            : "Deactivates all " + active.size() + " Liquidate Portfolio monitor"
              + (active.size() == 1 ? "" : "s")
              + " across every workspace, in both Paper and Live mode. "
              + "Positions and open orders are not touched and nothing is sold.",
        320));
  }

  private void stopAllLiquidations() {
    userActionLog.started("Stop All Liquidations");
    int stopped = portfolioCaptureRuns.stopAll();
    updateStopAllLiquidationsButton();
    userActionLog.completed("Stop All Liquidations",
        stopped == 0 ? "No liquidation monitors were running." : "Deactivated " + stopped + " liquidation monitor(s).");
  }

  /**
   * Re-trims the cached status line after the slot it lives in changes width.
   */
  private void refitCapturePortfolioIndicator() {
    if (!capturePortfolioIndicator.isVisible() || capturePortfolioIndicatorFullText.isBlank()) {
      return;
    }
    capturePortfolioIndicator.setText(
        RainbowText.toHtml(fitCaptureIndicatorText(capturePortfolioIndicatorFullText)));
  }

  private void refreshCapturePortfolioModeVisibility() {
    applySelectedCapturePortfolioState();
  }

  private void clearCapturePortfolioIndicatorForMode() {
    capturePortfolioIndicatorFullText = "";
    capturePortfolioIndicator.setText("");
    capturePortfolioIndicator.setVisible(false);
    capturePortfolioIndicator.setForeground(CAPTURE_INDICATOR_IDLE_TEXT);
    capturePortfolioIndicator.setToolTipText(null);
    capturePortfolioButton.setText("Liquidate Portfolio");
    capturePortfolioButton.setEnabled(true);
    stopCapturePortfolioPulse();
  }

  private PortfolioCaptureUiStateStore.Key selectedCapturePortfolioUiKey() {
    if (strategyWorkspaceTabs != null && strategyWorkspaceTabs.isHistorySelected()) {
      return null;
    }
    String tabId = strategyWorkspaceTabs == null
        ? PortfolioCaptureUiStateStore.ALL_STOCKS_TAB_ID
        : strategyWorkspaceTabs.selectedStrategyTabId();
    return capturePortfolioUiStates.key(selectedViewMode, tabId);
  }

  private PortfolioCaptureUiStateStore.Key capturePortfolioUiKey(StrategyMode mode, String workspaceId) {
    String tabId = workspaceId == null || workspaceId.isBlank()
        ? PortfolioCaptureUiStateStore.ALL_STOCKS_TAB_ID
        : "workspace:" + workspaceId;
    return capturePortfolioUiStates.key(mode == null ? selectedViewMode : mode, tabId);
  }

  /**
   * Recompute the Liquidate Portfolio indicator P&L on the same refresh cycle as the status bar, so its P&L (the centralized context total) stays in lock-step with the top status
   * bar / tab summary instead of lagging at the slower monitoring-tick cadence.
   */
  private void refreshActiveCaptureIndicator() {
    for (PortfolioCaptureRuns.Scope scope : portfolioCaptureRuns.activeScopes()) {
      updateCapturePortfolioIndicator(scope);
    }
  }

  /**
   * Composes the whole status line fresh: the live target/P&L figures followed by the automation counters, both read from current state at render time.
   *
   * <p>The counters used to be appended into the stored indicator string by
   * {@link #updateCaptureAutomationState} and parsed back out of it here. Every monitoring tick and every status-bar refresh rewrote that stored string with the figures alone,
   * which silently dropped the counters again — so a counter appeared on an automation state change and vanished moments later. Composing at render time makes the counters as
   * stable as the conditions that produce them.
   */
  private String composeCaptureIndicatorText(String storedIndicatorText) {
    String live = captureIndicatorText(selectedCaptureScope());
    if (live.isBlank()) {
      return storedIndicatorText == null ? "" : storedIndicatorText;
    }
    return live + captureAutomationCounterText();
  }

  private void applySelectedCapturePortfolioState() {
    PortfolioCaptureUiStateStore.Key key = selectedCapturePortfolioUiKey();
    if (key == null) {
      clearCapturePortfolioIndicatorForMode();
      return;
    }
    PortfolioCaptureUiStateStore.State state = capturePortfolioUiStates.state(key);
    // The capture status line is shown only while monitoring is enabled for THIS strategy tab, and
    // rendered rainbow. The stored string is just the show/hide flag: the line itself — figures and
    // automation counters alike — is composed from live state at paint time, so no refresh can drop
    // part of it and the figures stay in lock-step with the tab summary / top status bar.
    boolean showIndicator = state.monitoringActive() && !state.indicatorText().isBlank();
    String indicatorText = showIndicator ? composeCaptureIndicatorText(state.indicatorText()) : "";
    capturePortfolioIndicatorFullText = indicatorText;
    capturePortfolioButton.setText(capturePortfolioButtonText(state, showIndicator));
    capturePortfolioButton.setEnabled(state.buttonEnabled());
    capturePortfolioIndicator.setVisible(showIndicator);
    // The line is trimmed to the width the layout granted it instead of overflowing its slot and
    // vanishing on a narrow window; the untrimmed line always stays available in the tooltip.
    capturePortfolioIndicator.setText(showIndicator
        ? RainbowText.toHtml(fitCaptureIndicatorText(indicatorText))
        : "");
    boolean pausedForClosedMarket = state.monitoringActive()
        && captureAutomationStates.get(key) == PortfolioCaptureAutomationState.PAUSED_MARKET_CLOSED;
    capturePortfolioIndicator.setForeground(state.monitoringActive() && !pausedForClosedMarket
        ? CAPTURE_INDICATOR_ACTIVE_TEXT
        : CAPTURE_INDICATOR_IDLE_TEXT);
    String activeTooltip = showIndicator ? captureIndicatorTooltip(indicatorText)
        : "Liquidate Portfolio monitoring is evaluating current portfolio P&L against the configured target.";
    if (pausedForClosedMarket) {
      activeTooltip = activeTooltip
          + "\n\nAutomation is paused because the market session is closed. It resumes "
          + "automatically when the configured regular or extended-hours session opens.";
    }
    capturePortfolioIndicator.setToolTipText(state.monitoringActive() ? TooltipStyler.text(activeTooltip, 420) : null);
    capturePortfolioButton.setToolTipText(state.monitoringActive()
        ? TooltipStyler.text(activeTooltip, 420)
        : capturePortfolioDefaultTooltip());
    if (state.pulseActive()) {
      startCapturePortfolioPulse();
    } else {
      stopCapturePortfolioPulse();
    }
  }

  /**
   * Trims the liquidation status line to the width its layout slot actually has. Segments are dropped from the right (they are ordered least-important-last) rather than letting
   * the line overflow and disappear behind the pinned action buttons on a narrow window.
   */
  private String fitCaptureIndicatorText(String indicatorText) {
    return StatusLineFitter.fit(
        indicatorText,
        capturePortfolioIndicator.getFontMetrics(capturePortfolioIndicator.getFont()),
        availableCaptureIndicatorWidth());
  }

  /**
   * Pixels the status line may use. A zero-height slot means the layout has not run yet and nothing should be trimmed; once it has, BorderLayout can legitimately grant the centre
   * slot zero (or negative) width when the search controls and the pinned buttons already fill the row, and the line then has to yield rather than overflow them.
   */
  private int availableCaptureIndicatorWidth() {
    if (capturePortfolioIndicatorPanel == null || capturePortfolioIndicatorPanel.getHeight() <= 0) {
      return StatusLineFitter.UNCONSTRAINED;
    }
    Insets insets = capturePortfolioIndicatorPanel.getInsets();
    // A few pixels of slack: the label renders as per-character HTML, which can measure marginally
    // wider than the plain string the fitter measures.
    int available = capturePortfolioIndicatorPanel.getWidth() - insets.left - insets.right - 6;
    return Math.max(available, 0);
  }

  /**
   * Full (untrimmed) status line plus a plain-language explanation of how its figures relate.
   */
  private String captureIndicatorTooltip(String fullIndicatorText) {
    StringBuilder tooltip = new StringBuilder(fullIndicatorText);
    String explanation = captureIndicatorExplanation(selectedCaptureScope());
    if (!explanation.isBlank()) {
      tooltip.append("\n\n").append(explanation);
    }
    if (!captureAutomationCounterText().isBlank()) {
      tooltip.append("\n\n").append(captureAutomationCounterTooltip());
    }
    return tooltip.toString();
  }

  private String capturePortfolioButtonText(PortfolioCaptureUiStateStore.State state, boolean showIndicator) {
    if (state.busy()) {
      return state.buttonText();
    }
    if (showIndicator) {
      return "Liquidation Monitor Active";
    }
    return state.buttonText();
  }

  private void updateCaptureAutomationState(PortfolioCaptureRuns.Scope scope, PortfolioCaptureAutomationState state) {
    SwingUtilities.invokeLater(() -> {
      PortfolioCaptureUiStateStore.Key key = captureUiKey(scope);
      boolean running = portfolioCaptureRuns.monitoringActive(scope);
      if (state == PortfolioCaptureAutomationState.STOPPED && !running) {
        captureAutomationStates.remove(key);
        updateStopAllLiquidationsButton();
        return;
      }
      captureAutomationStates.put(key, state);
      if (state == PortfolioCaptureAutomationState.PAUSED_MARKET_CLOSED) {
        capturePortfolioUiStates.update(key, capturePortfolioUiStates.state(key)
            .withButton("Liquidate Portfolio:Auto Paused [Closed Market]", true)
            .withPulse(false));
      } else if (running && state == PortfolioCaptureAutomationState.MONITORING) {
        capturePortfolioUiStates.update(key, capturePortfolioUiStates.state(key)
            .withButton("Liquidate Portfolio", true)
            .withPulse(true));
      }
      // Counters and the paused/active presentation are both derived inside apply..., so this is
      // the single place the chrome is written — nothing here can be clobbered by a later pass.
      applySelectedCapturePortfolioState();
      updateStopAllLiquidationsButton();
    });
  }

  /**
   * The automation counters that trail the status line, read from live state. Each segment appears exactly while its condition holds — Loops only for a continuous loop,
   * cancelled-order counts only when pending cleanup is enabled — so a segment does not flicker in and out between refreshes.
   *
   * <p>The counter describes the run the monitor is executing for the selected tab. Cumulative
   * cross-run liquidation totals deliberately do NOT belong here: the capture history is a single global log with no workspace or mode scope, so showing its total beside one
   * workspace's figures would mix in liquidations from every other workspace and from the opposite trading mode.
   */
  private String captureAutomationCounterText() {
    PortfolioCaptureController controller = portfolioCaptureRuns.controller(selectedCaptureScope());
    PortfolioCaptureConfig config = controller.activeConfig();
    StringBuilder text = new StringBuilder();
    if (config != null && config.continuousLoop()) {
      text.append(" | Loops ").append(controller.loopCount());
    }
    // Cancelled pending buys are deliberately not shown: cleanup is housekeeping the operator asked
    // for once in the config, and its running total says nothing about how the liquidation is going.
    return text.toString();
  }

  private String captureAutomationCounterTooltip() {
    return "Loops is the number of completed continuous liquidation/re-entry cycles.";
  }

  private String capturePortfolioDefaultTooltip() {
    return TooltipStyler.text(
        "Liquidate all portfolio profits/losses now, or automatically when the portfolio reaches a target profit.",
        340
    );
  }

  private int cancelPendingBaseBuysForAutomation(StrategyMode mode) {
    StrategyMode effectiveMode = mode == null ? selectedViewMode : mode;
    int canceled = 0;
    for (ManagedStrategy entry : new ArrayList<>(strategies)) {
      if (entry.strategy.mode() != effectiveMode) {
        continue;
      }
      StrategyService service = strategyServiceForMode(entry.strategy.mode());
      if (service == null) {
        continue;
      }
      StrategyService.LimitBuyCancelResult result = service.cancelPendingLimitBuys(entry.strategy.id());
      if (result.success()) {
        canceled += Math.max(0, result.canceledCount());
      }
    }
    syncStrategiesFromRepository();
    refreshStrategyTableData();
    updateStatusBar();
    refreshPanels();
    return canceled;
  }

  /**
   * Liquidation re-entry: runs the chosen Smart Picks strategy into its matching workspace, if there is one.
   */
  private String runSmartPicksAutomation(PortfolioCaptureConfig config) {
    SmartPicksWorkspaceKind kind = SmartPicksWorkspaceKind.forStrategy(config.reentrySmartPicksStrategy());
    String workspaceId = smartPicksWorkspaceId(kind, config.reentryMode());
    log("[Portfolio Liquidation] Auto re-entry started. mode=" + config.reentryMode()
        + " quantity=" + config.reentryQuantity()
        + " term=" + config.reentryRecommendationType()
        + " smartPicksStrategy=" + config.reentrySmartPicksStrategy()
        + " workspace=" + (workspaceId == null ? "All Stocks" : kind.title()));
    return runSmartPicks(config.reentrySmartPicksStrategy(), config.reentryMode(), config.reentryQuantity(),
        config.reentryRecommendationType(), workspaceId, true, "[Portfolio Liquidation]");
  }

  /**
   * The first active workspace of {@code kind} in {@code mode}, or null when none has been created.
   */
  private String smartPicksWorkspaceId(SmartPicksWorkspaceKind kind, StrategyMode mode) {
    return workspaceService.activeWorkspaces(mode).stream()
        .filter(workspace -> kind.code().equalsIgnoreCase(workspace.code()))
        .map(StrategyWorkspace::id)
        .findFirst()
        .orElse(null);
  }

  /**
   * Runs a Smart Picks strategy headlessly: fetch its live universe, analyze each stock, then either create and activate the picks in {@code workspaceId} ({@code place}) or only
   * report them. Blocking; call it off the EDT. Shared by the liquidation re-entry and the Smart Picks workspace schedules.
   */
  private String runSmartPicks(PortfolioCaptureSmartPicksStrategy strategy, StrategyMode mode, int quantity,
      RecommendationType term, String workspaceId, boolean place, String logTag) {
    ApplicationMode applicationMode = mode == StrategyMode.LIVE ? ApplicationMode.LIVE : ApplicationMode.PAPER;
    String apiKey = settingsDialog.savedApiKey(applicationMode);
    String apiSecret = settingsDialog.savedApiSecret(applicationMode);
    if (apiKey.isBlank() || apiSecret.isBlank()) {
      return "Skipped: Alpaca credentials are required.";
    }
    HttpAlpacaMarketDataApi marketDataApi = new HttpAlpacaMarketDataApi(apiKey, apiSecret);
    List<TrendingStock> stocks;
    try {
      stocks = SmartPicksUniverseLoader.load(strategy, apiKey, apiSecret, marketDataApi, logTag, this::log);
    } catch (Exception ex) {
      log(logTag + " Could not fetch Smart Picks stocks: " + ex.getMessage());
      return "Skipped: unable to fetch Smart Picks stocks.";
    }
    List<SmartPicksSimulationSelection> selections = new SmartPicksPortfolioAutomationService(marketDataApi, this::log)
        .analyzeSelections(stocks, term, quantity);
    if (!place) {
      return selections.isEmpty()
          ? "No picks qualified"
          : selections.size() + " pick(s): " + selections.stream()
              .map(selection -> selection.stock().symbol()).toList();
    }
    SmartPicksSimulationPlacementController controller = new SmartPicksSimulationPlacementController(new SmartPicksSimulationPlacementController.Gateway() {
      @Override
      public com.neuralarc.service.StrategyRepository repository() {
        return strategyRepository;
      }

      @Override
      public StrategyService.StrategyCreationResult createPaperStrategy(Strategy strategy) {
        return createStrategy(strategy, mode);
      }

      @Override
      public StrategyService.StrategyCreationResult createStrategy(Strategy strategy, StrategyMode targetMode) {
        StrategyService service = strategyServiceForMode(targetMode);
        if (service == null) {
          return StrategyService.StrategyCreationResult.failed("Strategy service is not configured for " + targetMode);
        }
        return service.createAndActivate(strategy);
      }

      @Override
      public boolean confirmReplaceWaitingPaperStrategy(String symbol) {
        return true;
      }

      @Override
      public boolean allowDuplicateSymbols() {
        return settingsDialog.appliedAllowDuplicateSymbolStrategies();
      }

      @Override
      public String targetWorkspaceId() {
        return workspaceId;
      }

      @Override
      public int defaultStrategyPollingSeconds() {
        return settingsDialog.appliedDefaultStrategyPollingSeconds();
      }

      @Override
      public boolean defaultRepeatCycleAfterProfitExitEnabled() {
        return settingsDialog.appliedDefaultRepeatCycleAfterProfitExitEnabled();
      }

      @Override
      public boolean defaultResubmitOnExpiryEnabled() {
        return settingsDialog.appliedDefaultResubmitOnExpiryEnabled();
      }

      @Override
      public void cancelAndDeletePaperStrategy(String strategyId) {
        strategyServiceForMode(mode).delete(strategyId);
      }

      @Override
      public void afterPlacement() {
        SwingUtilities.invokeLater(TradingFrame.this::onSmartPicksPlaced);
      }

      @Override
      public void log(String message) {
        TradingFrame.this.log(message);
      }
    }, mode);
    SmartPicksSimulationPlacementController.PlacementResult result = controller.place(selections);
    log(logTag + " Smart Picks generated positions. created=" + result.created()
        + " replaced=" + result.replaced() + " skipped=" + result.skipped());
    return controller.summaryMessage(result).replace('\n', ' ');
  }

  private void setCapturePortfolioBusy(PortfolioCaptureRuns.Scope scope, boolean busy) {
    PortfolioCaptureUiStateStore.Key key = captureUiKey(scope);
    capturePortfolioUiStates.update(key, capturePortfolioUiStates.state(key).withBusy(busy));
    applySelectedCapturePortfolioState();
  }

  private void startCapturePortfolioPulse() {
    if (capturePortfolioPulseTimer == null) {
      capturePortfolioPulseTimer = new Timer(550, ignored -> {
        capturePortfolioPulseOn = !capturePortfolioPulseOn;
        capturePortfolioButton.setBackground(capturePortfolioPulseOn ? CAPTURE_ACTIVE_BG : CAPTURE_ACTIVE_BG_ALT);
        capturePortfolioButton.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(capturePortfolioPulseOn ? CAPTURE_ACTIVE_BORDER : CAPTURE_ACTIVE_BORDER_ALT, 1, true),
            new EmptyBorder(5, 10, 5, 10)
        ));
      });
      capturePortfolioPulseTimer.setInitialDelay(0);
    }
    capturePortfolioButton.setRolloverEnabled(false);
    capturePortfolioButton.putClientProperty(ButtonHoverPolicy.FLASHING_PROPERTY, Boolean.TRUE);
    capturePortfolioPulseTimer.start();
  }

  private void stopCapturePortfolioPulse() {
    if (capturePortfolioPulseTimer != null) {
      capturePortfolioPulseTimer.stop();
    }
    capturePortfolioPulseOn = false;
    capturePortfolioButton.setBackground(DARK_BTN_BG);
    capturePortfolioButton.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(DARK_BTN_BORDER, 1, true),
        new EmptyBorder(5, 10, 5, 10)
    ));
    capturePortfolioButton.setRolloverEnabled(true);
    capturePortfolioButton.putClientProperty(ButtonHoverPolicy.FLASHING_PROPERTY, Boolean.FALSE);
  }

  private void showPortfolioCaptureSummary(PortfolioCaptureExecutionResult result, boolean targetTriggered) {
    if (targetTriggered) {
      JOptionPane.showMessageDialog(this,
          "Portfolio target reached. Liquidation executed successfully.",
          "Liquidate Portfolio",
          JOptionPane.INFORMATION_MESSAGE);
    }
    JOptionPane.showMessageDialog(this,
        "<html><body style='width:420px'>"
            + "<b>Portfolio Liquidation Summary</b><br><br>"
            + "Total Stocks Liquidated: " + result.capturedCount() + "<br>"
            + "Total Investment: $" + Monetary.round(result.totalInvestment()) + "<br>"
            + "Estimated Portfolio Value: $" + Monetary.round(result.estimatedPortfolioValue()) + "<br>"
            + "Actual Broker Execution Value: $" + Monetary.round(result.actualBrokerExecutionValue()) + "<br>"
            + "Estimated Profit/Loss: $" + Monetary.round(result.estimatedPnl()) + "<br>"
            + "Actual Profit/Loss: $" + Monetary.round(result.actualPnl()) + "<br>"
            + "Execution Variance: $" + Monetary.round(result.executionVariance()) + "<br>"
            + portfolioCaptureHistorySummaryHtml()
            + "Timestamp: " + result.timestamp() + "<br><br>"
            + (result.failures().isEmpty() ? "" : "<b>Failures:</b><br>" + String.join("<br>", result.failures()))
            + "</body></html>",
        "Portfolio Liquidation Summary",
        result.failures().isEmpty() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
    userActionLog.completed("Liquidate Portfolio", "Liquidated " + result.capturedCount() + " stock(s).");
  }

  private String portfolioCaptureHistorySummaryHtml() {
    PortfolioCaptureHistoryStore.Summary summary = portfolioCaptureRuns.captureHistorySummary();
    if (summary == null || summary.captureCount() == 0) {
      return "";
    }
    return "<br><b>Cumulative Liquidation History</b><br>"
        + "Liquidation Runs: " + summary.captureCount() + "<br>"
        + "Stocks Liquidated: " + summary.capturedStocks() + "<br>"
        + "Total Estimated P&L: $" + Monetary.round(summary.estimatedPnl()) + "<br>"
        + "Total Actual P&L: $" + Monetary.round(summary.actualPnl()) + "<br>"
        + "Total Broker Execution Value: $" + Monetary.round(summary.actualBrokerExecutionValue()) + "<br><br>";
  }

  private static Font createBaseFont() {
    return FontLoader.ui(Font.PLAIN, 12);
  }

  private void wireEvents() {
    addStrategyButton.addActionListener(e -> addStrategy());
    smartPicksButton.addActionListener(e -> showSmartPicksMenu());
    refreshPortfolioButton.addActionListener(e -> portfolioRefreshController.refresh(true));
    capturePortfolioButton.addActionListener(e -> openPortfolioCaptureDialog());
    portfolioActionsButton.addActionListener(e -> portfolioActionsController.showMenu(portfolioActionsButton));
    settingsButton.addActionListener(e -> openSettingsDialog());
    configureButtonShortcut(addStrategyButton, KeyEvent.VK_S,
        KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK),
        "addStockStrategy");
    configureButtonShortcut(smartPicksButton, KeyEvent.VK_L,
        KeyStroke.getKeyStroke(KeyEvent.VK_L, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK),
        "smartPicks");
    configureSmartPicksMenu();
    configureButtonShortcut(refreshPortfolioButton, KeyEvent.VK_R,
        KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK),
        "refreshPortfolio");
    configureButtonShortcut(capturePortfolioButton, KeyEvent.VK_C,
        KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK),
        "capturePortfolio");
    configureButtonShortcut(portfolioActionsButton, KeyEvent.VK_P,
        KeyStroke.getKeyStroke(KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK),
        "portfolioActions");
    configureButtonShortcut(settingsButton, KeyEvent.VK_T,
        KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK),
        "settings");
  }

  private void configureButtonShortcut(JButton button, int mnemonic, KeyStroke accelerator, String actionKey) {
    button.setMnemonic(mnemonic);
    int mnemonicIndex = mnemonicIndex(button.getText(), mnemonic);
    if (mnemonicIndex >= 0) {
      button.setDisplayedMnemonicIndex(mnemonicIndex);
    }
    if (accelerator == null) {
      return;
    }
    String key = "buttonShortcut." + actionKey;
    getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(accelerator, key);
    getRootPane().getActionMap().put(key, new AbstractAction() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent e) {
        if (button.isEnabled()) {
          button.doClick();
        }
      }
    });
  }

  private int mnemonicIndex(String text, int mnemonic) {
    if (text == null || text.isBlank()) {
      return -1;
    }
    char target = Character.toUpperCase((char) mnemonic);
    for (int i = 0; i < text.length(); i++) {
      if (Character.toUpperCase(text.charAt(i)) == target) {
        return i;
      }
    }
    return -1;
  }

  private void configureSmartPicksMenu() {
    smartPicksMenu.removeAll();
    smartPicksMenu.setBackground(new Color(46, 49, 60));
    smartPicksMenu.setOpaque(true);
    smartPicksMenu.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(new Color(70, 76, 90), 1, true),
        new EmptyBorder(4, 4, 4, 4)
    ));
    // One-click strategy-workspace creation: clicking a template creates the workspace and its
    // tab immediately (no restart) and selects it.
    smartPicksMenu.add(createStatusMenuHeader("New Strategy Workspace"));
    for (StrategyWorkspaceTemplate template : StrategyWorkspaceTemplate.catalog()) {
      JMenuItem templateItem = createStatusMenuItem(
          template.implemented() ? template.name() : template.name() + "  (Coming soon)",
          "icons/add-stock-strategy.svg",
          () -> createWorkspaceFromTemplate(template)
      );
      // Strategies without a dedicated scanner are advertised but disabled until implemented.
      templateItem.setEnabled(template.implemented());
      templateItem.setToolTipText(TooltipStyler.text(template.implemented()
          ? template.description()
          : template.description() + " — coming soon; this strategy is not implemented yet.", 360));
      smartPicksMenu.add(templateItem);
    }
  }

  /**
   * The Smart Picks strategies now live in their own workspaces, created from the "New Strategy Workspace" section like every other strategy; the menu no longer offers them as
   * one-off runs.
   */
  static List<String> smartPicksMenuLabels() {
    return StrategyWorkspaceTemplate.catalog().stream().map(StrategyWorkspaceTemplate::name).toList();
  }

  private void showSmartPicksMenu() {
    if (!smartPicksButton.isEnabled()) {
      return;
    }
    smartPicksMenu.show(smartPicksButton, 0, smartPicksButton.getHeight());
  }

  private void togglePauseResume(int viewRow) {
    strategyActionsController.togglePauseResume(viewRow);
  }

  private void sellStrategy(int viewRow) {
    strategyActionsController.sellPosition(viewRow);
  }

  private void sellStrategyAtMarketPlace(int viewRow) {
    strategyActionsController.sellPositionAtMarketPlace(viewRow);
  }

  private void buyMoreAtMarketPrice(int viewRow) {
    strategyActionsController.buyMoreAtMarketPrice(viewRow);
  }

  private void buyMoreAtLimitPrice(int viewRow) {
    strategyActionsController.buyMoreAtLimitPrice(viewRow);
  }

  private void repositionExpiredStrategy(int viewRow) {
    strategyActionsController.repositionExpiredStrategy(viewRow);
  }

  private void cancelPendingLimitBuyFromGrid(int viewRow) {
    strategyActionsController.cancelPendingLimitBuy(viewRow);
  }

  private void placePendingBaseBuyFromGrid(int viewRow) {
    int row = strategyTable.convertRowIndexToModel(viewRow);
    if (row < 0 || row >= strategies.size()) {
      return;
    }
    Strategy strategy = strategies.get(row).strategy;
    if (!PendingBaseBuyPlacementSupport.isPendingBaseBuyPlacement(strategy)) {
      return;
    }
    new SwingWorker<StrategyService.StrategyCreationResult, Void>() {
      @Override
      protected StrategyService.StrategyCreationResult doInBackground() {
        return placePendingBaseBuy(strategy);
      }

      @Override
      protected void done() {
        try {
          StrategyService.StrategyCreationResult result = get();
          syncStrategiesFromRepository();
          refreshStrategyTableData();
          applyCurrentStrategiesRowFilter();
          refreshWorkspaceSummary();
          updateStatusBar();
          if (result.success()) {
            log("[" + strategy.symbol() + "] Pending base limit buy placed. clientOrderId="
                + result.clientOrderId());
          } else {
            log("[" + strategy.symbol() + "] Failed to place pending base buy: " + result.error());
            JOptionPane.showMessageDialog(
                TradingFrame.this,
                "Failed to place pending base buy for " + strategy.symbol() + ": " + result.error(),
                "Place Pending Base Buy",
                JOptionPane.ERROR_MESSAGE
            );
          }
        } catch (Exception ex) {
          log("[" + strategy.symbol() + "] Failed to place pending base buy: " + ex.getMessage());
          JOptionPane.showMessageDialog(
              TradingFrame.this,
              "Failed to place pending base buy for " + strategy.symbol() + ": " + ex.getMessage(),
              "Place Pending Base Buy",
              JOptionPane.ERROR_MESSAGE
          );
        }
      }
    }.execute();
  }

  private void readjustLosingPendingBaseBuyFromGrid(int viewRow) {
    int row = strategyTable.convertRowIndexToModel(viewRow);
    if (row < 0 || row >= strategies.size()) {
      return;
    }
    ManagedStrategy entry = strategies.get(row);
    if (!isAmberPendingBaseBuy(entry)) {
      return;
    }
    new SwingWorker<StrategyService.StrategyCreationResult, Void>() {
      @Override
      protected StrategyService.StrategyCreationResult doInBackground() {
        return readjustLosingPendingBaseBuy(entry);
      }

      @Override
      protected void done() {
        try {
          StrategyService.StrategyCreationResult result = get();
          syncStrategiesFromRepository();
          refreshStrategyTableData();
          applyCurrentStrategiesRowFilter();
          refreshWorkspaceSummary();
          updateStatusBar();
          if (result.success()) {
            log("[" + entry.strategy.symbol() + "] Losing pending base-buy recommendation readjusted.");
          } else {
            log("[" + entry.strategy.symbol() + "] Failed to readjust pending base buy: " + result.error());
            JOptionPane.showMessageDialog(
                TradingFrame.this,
                "Failed to readjust pending base buy for " + entry.strategy.symbol() + ": " + result.error(),
                "Readjust Losing Pending Base Buy",
                JOptionPane.ERROR_MESSAGE
            );
          }
        } catch (Exception ex) {
          log("[" + entry.strategy.symbol() + "] Failed to readjust pending base buy: " + ex.getMessage());
          JOptionPane.showMessageDialog(
              TradingFrame.this,
              "Failed to readjust pending base buy for " + entry.strategy.symbol() + ": " + ex.getMessage(),
              "Readjust Losing Pending Base Buy",
              JOptionPane.ERROR_MESSAGE
          );
        }
      }
    }.execute();
  }

  private void repositionStockFromHistory(int viewRow) {
    if (strategyWorkspaceTabs == null || !strategyWorkspaceTabs.isHistorySelected()) {
      return;
    }
    int row = strategyTable.convertRowIndexToModel(viewRow);
    if (row < 0 || row >= strategies.size()) {
      return;
    }
    ManagedStrategy entry = strategies.get(row);
    if (!isHistoryRepositionEligible(entry)) {
      return;
    }
    repositionStockFromHistoryEntry(entry);
  }

  private void repositionStockFromHistoryEntry(ManagedStrategy entry) {
    String symbol = entry.strategy.symbol() == null ? "" : entry.strategy.symbol().trim().toUpperCase(Locale.ROOT);
    String actionName = "Reposition Stock " + symbol;
    userActionLog.started(actionName);
    if (!ensureLegalDisclosureAccepted()) {
      userActionLog.canceled(actionName);
      return;
    }
    String selectedApiKey = savedApiKeyForSelectedMode();
    String selectedApiSecret = savedApiSecretForSelectedMode();
    HttpAlpacaMarketDataApi marketDataApi = !selectedApiKey.isBlank() && !selectedApiSecret.isBlank()
        ? new HttpAlpacaMarketDataApi(selectedApiKey, selectedApiSecret)
        : null;
    int defaultPollingSeconds = settingsDialog.appliedDefaultStrategyPollingSeconds();
    boolean defaultRepeatCycle = settingsDialog.appliedDefaultRepeatCycleAfterProfitExitEnabled();
    boolean defaultResubmit = settingsDialog.appliedDefaultResubmitOnExpiryEnabled();
    StrategyConfig prefilledConfig = historyRepositionPrefilledConfig(
        entry,
        defaultPollingSeconds,
        defaultRepeatCycle,
        defaultResubmit
    );
    StrategyDialog dialog = new StrategyDialog(
        this,
        prefilledConfig,
        marketDataApi,
        autoAnalyzeResultStore,
        defaultPollingSeconds,
        defaultRepeatCycle,
        defaultResubmit,
        settingsDialog.appliedManualBuyTimeInForce()
    );
    StrategyConfig config = dialog.showDialog();
    if (config == null) {
      userActionLog.canceled(actionName);
      return;
    }
    StrategyMode targetMode = selectedViewMode;
    String targetWorkspaceId = selectedWorkspaceForNewStrategy();
    boolean allowDuplicateSymbols = settingsDialog.appliedAllowDuplicateSymbolStrategies();
    if (DuplicateSymbolPolicy.wouldBeDuplicate(
        config.symbol(),
        targetMode,
        strategyRepository.findAll(),
        allowDuplicateSymbols,
        targetWorkspaceId,
        ""
    )) {
      userActionLog.failed(actionName, "An active or paused strategy for " + config.symbol() + " already exists.");
      JOptionPane.showMessageDialog(
          this,
          duplicateSymbolAlertMessage(config.symbol(), targetWorkspaceId, allowDuplicateSymbols, true),
          "Duplicate Symbol",
          JOptionPane.WARNING_MESSAGE
      );
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
    NewStrategyWorkspaceAssignment.apply(strategy, targetWorkspaceId, workspaceService);
    strategy.setLastEvent("Repositioned from Trade History with base buy @$" + strategy.baseBuyLimitPrice().toPlainString() + ".");
    StrategyService modeAwareService = strategyServiceForMode(targetMode);
    if (modeAwareService == null) {
      userActionLog.failed(actionName, targetMode + " broker client is not configured.");
      JOptionPane.showMessageDialog(
          this,
          selectedModeLabel() + " Alpaca credentials are required before repositioning this strategy.",
          selectedModeLabel() + " Credentials Required",
          JOptionPane.WARNING_MESSAGE
      );
      return;
    }
    StrategyService.StrategyCreationResult creationResult = modeAwareService.createAndActivate(strategy);
    if (!creationResult.success()) {
      userActionLog.failed(actionName, creationResult.error());
      JOptionPane.showMessageDialog(
          this,
          "Failed to submit initial Alpaca limit buy order: " + creationResult.error(),
          "Reposition Failed",
          JOptionPane.ERROR_MESSAGE
      );
      log("[" + config.symbol() + "] History reposition failed during initial order placement: " + creationResult.error());
      return;
    }
    log("[" + config.symbol() + "] Repositioned from Trade History. base=$"
        + strategy.baseBuyLimitPrice().toPlainString()
        + ", clientOrderId=" + creationResult.clientOrderId());
    userActionLog.completed(actionName, config.symbol() + " " + selectedModeLabel() + " initial limit buy submitted.");
    JOptionPane.showMessageDialog(
        this,
        "Initial Alpaca limit buy submitted successfully.\nOrder ID: " + creationResult.alpacaOrderId(),
        "Strategy Repositioned",
        JOptionPane.INFORMATION_MESSAGE
    );
    ensureAnalyticsPublisher();
    syncStrategiesFromRepository();
    updateHeaderModeStatus(currentBrokerType);
    selectedStrategyId = strategy.id();
    refreshStrategyTableData();
    SwingUtilities.invokeLater(() -> selectAndRevealStrategy(strategy.id()));
    updateSelectedStrategy();
    refreshPanels();
  }

  private StrategyConfig historyRepositionPrefilledConfig(
      ManagedStrategy entry,
      int defaultPollingSeconds,
      boolean defaultRepeatCycle,
      boolean defaultResubmit
  ) {
    StrategyConfig source = entry.toConfig();
    BigDecimal baseBuyPrice = historyRepositionBaseBuyPrice(entry);
    int baseBuyQty = Math.max(1, source.baseBuyQty());
    return new StrategyConfig(
        source.symbol(),
        baseBuyPrice,
        baseBuyQty,
        source.stopLossEnabled(),
        source.stopLoss(),
        source.sellTriggerEnabled(),
        source.sellTriggerPrice(),
        source.lossBuyLevel1Price(),
        source.lossBuyLevel1Qty(),
        source.lossBuyLevel2Price(),
        source.lossBuyLevel2Qty(),
        source.lossBuyLevelsEnabled(),
        source.optionalLossExitEnabled(),
        source.optionalLossExitPrice(),
        Math.max(1, defaultPollingSeconds),
        selectedViewMode == StrategyMode.PAPER,
        source.alpacaTrailingStopEnabled(),
        source.profitHoldEnabled(),
        source.profitHoldType(),
        source.profitHoldPercent(),
        source.profitHoldAmount(),
        defaultRepeatCycle,
        source.profitControlMode(),
        source.automaticStopSellThresholdType(),
        source.automaticStopSellThreshold(),
        source.automaticStopSellTrailingType(),
        source.automaticStopSellTrailingValue(),
        defaultResubmit,
        source.baseBuyRepostReductionPercent(),
        source.timeInForce(),
        source.autoAdjustRisk()
    );
  }

  private BigDecimal historyRepositionBaseBuyPrice(ManagedStrategy entry) {
    Strategy strategy = entry.strategy;
    if (strategy != null && strategy.baseBuyLimitPrice() != null && strategy.baseBuyLimitPrice().signum() > 0) {
      return Monetary.round(strategy.baseBuyLimitPrice());
    }
    BigDecimal executed = entry.cachedBaseBuyExecutedPrice();
    if (executed != null && executed.signum() > 0) {
      return Monetary.round(executed);
    }
    BigDecimal lastPrice = entry.cachedPosition().getLastPrice();
    if (lastPrice != null && lastPrice.signum() > 0) {
      return Monetary.round(lastPrice);
    }
    return new BigDecimal("1.00");
  }

  private boolean rowCanRepositionFromHistory(int viewRow) {
    int row = strategyTable.convertRowIndexToModel(viewRow);
    if (row < 0 || row >= strategies.size()) {
      return false;
    }
    return isHistoryRepositionEligible(strategies.get(row));
  }

  private boolean isHistoryRepositionEligible(ManagedStrategy entry) {
    return strategyWorkspaceTabs != null
        && strategyWorkspaceTabs.isHistorySelected()
        && entry != null
        && entry.strategy != null
        && entry.strategy.symbol() != null
        && !entry.strategy.symbol().isBlank();
  }

  private boolean rowHasCancelablePendingLimitBuy(int viewRow) {
    int row = strategyTable.convertRowIndexToModel(viewRow);
    if (row < 0 || row >= strategies.size()) {
      return false;
    }
    Strategy strategy = strategies.get(row).strategy;
    return strategy != null
        && PendingBuyOrderGuard.hasCancelablePendingLimitBuy(
        strategyOrderRepository.findByStrategyId(strategy.id()));
  }

  private boolean rowHasPendingBaseBuyPlacement(int viewRow) {
    int row = strategyTable.convertRowIndexToModel(viewRow);
    if (row < 0 || row >= strategies.size()) {
      return false;
    }
    return PendingBaseBuyPlacementSupport.isPendingBaseBuyPlacement(strategies.get(row).strategy);
  }

  private boolean rowHasAmberPendingBaseBuy(int viewRow) {
    int row = strategyTable.convertRowIndexToModel(viewRow);
    if (row < 0 || row >= strategies.size()) {
      return false;
    }
    return isAmberPendingBaseBuy(strategies.get(row));
  }

  private boolean isAmberPendingBaseBuy(ManagedStrategy entry) {
    if (entry == null || entry.strategy == null
        || !PendingBaseBuyPlacementSupport.isPendingBaseBuyPlacement(entry.strategy)) {
      return false;
    }
    return OrbPendingBaseBuyRowStyler.priceDirection(entry.strategy, entry.cachedPosition())
        == OrbPendingBaseBuyRowStyler.PriceDirection.AMBER_LOSER;
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
    return sellPosition(strategy, SellSubmissionType.LIMIT);
  }

  private StrategyService.StrategyCreationResult sellPosition(Strategy strategy, SellSubmissionType submissionType) {
    return sellPosition(strategy, submissionType, StrategyService.SellExecutionSource.MANUAL_USER);
  }

  private StrategyService.StrategyCreationResult sellPosition(
      Strategy strategy,
      SellSubmissionType submissionType,
      StrategyService.SellExecutionSource executionSource
  ) {
    StrategyService modeAwareService = strategyServiceForMode(strategy.mode());
    if (modeAwareService == null) {
      return StrategyService.StrategyCreationResult.failed(
          "Broker client is not configured for " + strategy.mode().name() + " mode."
      );
    }
    return modeAwareService.closePosition(strategy.id(), submissionType, executionSource);
  }

  private Optional<Integer> chooseMarketBuyQuantity(Strategy strategy) {
    JSpinner quantitySpinner = new JSpinner(new SpinnerNumberModel(1, 1, 1_000_000, 1));
    String message = "<html><body style='width:360px'>"
        + "<b>Buy more shares of " + strategy.symbol() + " at market price</b><br><br>"
        + "Enter the quantity to buy. This submits an Alpaca market buy order; fill price can differ from the latest quote."
        + "<br><br>The strategy remains active and the order is recorded in trade history as a manual buy."
        + "</body></html>";
    Object[] content = {message, quantitySpinner};
    int choice = JOptionPane.showConfirmDialog(
        this,
        content,
        "Buy More — " + strategy.symbol(),
        JOptionPane.OK_CANCEL_OPTION,
        JOptionPane.WARNING_MESSAGE
    );
    if (choice != JOptionPane.OK_OPTION) {
      return Optional.empty();
    }
    Object value = quantitySpinner.getValue();
    if (value instanceof Number number) {
      return Optional.of(Math.max(1, number.intValue()));
    }
    return Optional.empty();
  }

  private Optional<AverageLosingPositionsSelection> chooseAverageLosingPositions(List<ManagedStrategy> scope) {
    String scopeLabel = (selectedWorkspaceId == null
        ? "All Stocks"
        : workspaceService.findById(selectedWorkspaceId).map(StrategyWorkspace::name).orElse("This Tab"))
        + " · " + selectedModeLabel();
    List<AverageDownCandidates.Candidate> candidates = AverageDownCandidates.collect(
        scope,
        PortfolioActionsSupport.BulkAction.AVERAGE_LOSING_POSITIONS::matches,
        workspaceId -> workspaceId == null || workspaceId.isBlank()
            ? "Unassigned"
            : workspaceService.findById(workspaceId).map(StrategyWorkspace::name).orElse("Unassigned"));
    return AverageLosingPositionsDialog.show(this, candidates, scopeLabel, settingsDialog.appliedManualBuyTimeInForce());
  }

  private Optional<BigDecimal> chooseSellProfitThresholdPercent(List<ManagedStrategy> targets) {
    return SellProfitThresholdDialog.show(this, targets);
  }

  private Optional<Strategy> updateStrategyForMode(Strategy strategy) {
    StrategyService modeAwareService = strategyServiceForMode(strategy.mode());
    if (modeAwareService == null) {
      return Optional.empty();
    }
    return modeAwareService.updateStrategy(strategy);
  }

  private StrategyService.StrategyCreationResult buyMoreAtMarket(Strategy strategy, int quantity) {
    StrategyService modeAwareService = strategyServiceForMode(strategy.mode());
    if (modeAwareService == null) {
      return StrategyService.StrategyCreationResult.failed(
          "Broker client is not configured for " + strategy.mode().name() + " mode."
      );
    }
    return modeAwareService.buyMoreAtMarket(strategy.id(), quantity);
  }

  private StrategyService.StrategyCreationResult buyMoreAtLimit(
      Strategy strategy,
      int quantity,
      BigDecimal limitPrice,
      boolean repositionAfterExpiry,
      TimeInForce timeInForce
  ) {
    StrategyService modeAwareService = strategyServiceForMode(strategy.mode());
    if (modeAwareService == null) {
      return StrategyService.StrategyCreationResult.failed(
          "Broker client is not configured for " + strategy.mode().name() + " mode."
      );
    }
    return modeAwareService.buyMoreAtLimit(strategy.id(), quantity, limitPrice, repositionAfterExpiry, timeInForce);
  }

  private StrategyService strategyServiceForMode(StrategyMode mode) {
    ApplicationMode applicationMode = mode == StrategyMode.LIVE ? ApplicationMode.LIVE : ApplicationMode.PAPER;
    HttpAlpacaClient client = alpacaClientForMode(applicationMode);
    return tradingRuntimeSupport.createStrategyService(client, mode);
  }

  /**
   * Adopts broker positions that no local strategy owns, in <em>both</em> modes.
   *
   * <p>Deliberately not the runtime {@code strategyService}: that field is bound to a single
   * application mode, and at startup it is wired before the default view is chosen. A live holding would then be checked against the paper account, find nothing, and stay
   * untracked for as long as the app ran - which is exactly how five held symbols sat in the reconciliation report while every refresh reported success.
   */
  private int adoptUnknownBrokerSymbols() {
    int adopted = 0;
    for (StrategyMode mode : List.of(StrategyMode.PAPER, StrategyMode.LIVE)) {
      if (ModeActivity.runsFor(mode, selectedViewMode)) {
        adopted += adoptUnknownBrokerSymbolsForMode(mode);
      }
    }
    return adopted;
  }

  private int adoptUnknownBrokerSymbolsForMode(StrategyMode mode) {
    StrategyService service = strategyServiceForMode(mode);
    if (service == null) {
      log("[Portfolio Refresh] Broker adoption skipped for " + mode.name()
          + ": no broker client is configured for that mode.");
      return 0;
    }
    // Built fresh per call, so the suppression list has to be attached or every deleted symbol
    // would look unsuppressed and be recreated.
    service.setRemoteSyncSuppressionRepository(remoteSyncSuppressionRepository);
    List<Strategy> created = service.syncRemoteStrategies();
    // Logged even at zero: this step was previously silent unless it adopted something, which is
    // why a mode-mismatched sync looked identical to having nothing to do.
    log("[Portfolio Refresh] Broker adoption checked " + mode.name() + ": adopted " + created.size()
        + (created.isEmpty()
        ? "."
        : " (" + created.stream().map(Strategy::symbol)
            .collect(java.util.stream.Collectors.joining(", ")) + ")."));
    return created.size();
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
    // Saved keys exist: verify paper and live off the EDT and only reopen Settings if the broker
    // actually rejects them. A slow or unreachable broker must not read as "credentials missing".
    startupCredentialCoordinator.verifySavedCredentials();
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
    userActionLog.started("Settings");
    ConnectionIdentity before = currentConnectionIdentity();
    settingsDialog.prepareForOpen();
    settingsDialog.setVisible(true);
    if (!settingsDialog.wasSavedDuringOpen()) {
      userActionLog.completed("Settings", "Closed without saving.");
      return;
    }
    portfolioEmailScheduler.setSettings(settingsDialog.portfolioEmailSettings());
    // Only a change of account, mode or broker needs a new session. Reconnecting for an email time
    // or a polling interval costs a stream restart and a full resync, and during market hours that
    // is a gap in the one thing that must not have gaps.
    if (connectionOk && currentConnectionIdentity().sameSessionAs(before)) {
      com.neuralarc.api.ApiRequestLogConfig.setVerboseJsonLogging(appSettingsService.loadVerboseApiJsonLoggingEnabled());
      refreshStrategyRuntimeServices(
          savedApiKeyForSelectedMode(), savedApiSecretForSelectedMode(), selectedApplicationMode());
      updateStatusBar();
      log("[SETTINGS] Saved. Credentials unchanged, so the broker connection and trade stream were left running.");
      userActionLog.completed("Settings", "Saved. Connection left connected.");
      return;
    }
    stopTradingEventStream();
    connectionOk = false;
    setStatus("Not connected — verify connection in Settings after changes.", STATUS_WARN);
    updateHeaderModeStatus(currentBrokerType);
    updateStatusBar();
    autoInitializeConnection();
    userActionLog.completed("Settings", "Saved. Connection refresh started.");
  }

  /** Which broker session the saved settings describe right now. */
  private ConnectionIdentity currentConnectionIdentity() {
    return ConnectionIdentity.of(
        settingsDialog.appliedBrokerType(),
        settingsDialog.appliedApplicationMode(),
        settingsDialog::savedApiKey,
        settingsDialog::savedApiSecret);
  }

  private void resetLocalTradingDataForAlpacaAccountChange() {
    log("[SETTINGS] Different Alpaca account selected. Clearing local strategy data before reconnect.");
    shutdownPollingServices();
    for (Strategy strategy : strategyRepository.findAll()) {
      strategyOrderRepository.deleteByStrategyId(strategy.id());
      strategyEventRepository.deleteByStrategyId(strategy.id());
      strategyRepository.deleteById(strategy.id());
    }
    strategyRepository.invalidateCache();
    strategyOrderRepository.invalidateCache();
    strategyEventRepository.invalidateCache();
    strategies.clear();
    filledOrderRows.clear();
    strategyTableModel.fireTableDataChanged();
    filledOrdersTableModel.fireTableDataChanged();
    refreshPanels();
    updateStatusBar();
    log("[SETTINGS] Local strategy data cleared. New Alpaca account data will sync after reconnect.");
  }

  private StrategyService.ArchiveResult deleteLocalTradeHistoryStrategy(String strategyId) {
    if (strategyId == null || strategyId.isBlank()) {
      return StrategyService.ArchiveResult.failed("Strategy id is missing");
    }
    Optional<Strategy> maybeStrategy = strategyRepository.findById(strategyId);
    if (maybeStrategy.isEmpty()) {
      return StrategyService.ArchiveResult.failed("Strategy not found");
    }
    Strategy strategy = maybeStrategy.get();
    if (strategy.status() != StrategyStatus.ARCHIVED
        && strategy.status() != StrategyStatus.COMPLETED
        && strategy.status() != StrategyStatus.FAILED
        && strategy.status() != StrategyStatus.STOPPED) {
      return StrategyService.ArchiveResult.failed("Only inactive trade history records can be deleted");
    }
    strategyOrderRepository.deleteByStrategyId(strategy.id());
    strategyEventRepository.deleteByStrategyId(strategy.id());
    strategyRepository.deleteById(strategy.id());
    suppressRemoteSyncFor(strategy);
    log("[PORTFOLIO] Deleted trade history record for " + strategy.symbol() + ".");
    return StrategyService.ArchiveResult.success(strategy.id());
  }

  /**
   * Deletes an archived past position that never bought or sold a share. Checked again here rather than trusting the menu's list, so a row that has since filled or placed an order
   * is never removed. No remote-sync suppression is recorded: the broker never held these, so there is nothing to keep out.
   */
  private StrategyService.ArchiveResult deleteArchivedPosition(String strategyId) {
    Optional<Strategy> maybeStrategy = strategyId == null ? Optional.empty() : strategyRepository.findById(strategyId);
    if (maybeStrategy.isEmpty()) {
      return StrategyService.ArchiveResult.failed("Strategy not found");
    }
    Strategy strategy = maybeStrategy.get();
    if (!ArchivedPositionCleanup.isCleanable(strategy, strategyOrderRepository.findByStrategyId(strategy.id()))) {
      return StrategyService.ArchiveResult.failed("It has a fill or a working order now; kept");
    }
    strategyOrderRepository.deleteByStrategyId(strategy.id());
    strategyEventRepository.deleteByStrategyId(strategy.id());
    strategyRepository.deleteById(strategy.id());
    return StrategyService.ArchiveResult.success(strategy.id());
  }

  private StrategyService.ArchiveResult deleteLocalPaperStrategy(String strategyId) {
    if (strategyId == null || strategyId.isBlank()) {
      return StrategyService.ArchiveResult.failed("Strategy id is missing");
    }
    Optional<Strategy> maybeStrategy = strategyRepository.findById(strategyId);
    if (maybeStrategy.isEmpty()) {
      return StrategyService.ArchiveResult.failed("Strategy not found");
    }
    Strategy strategy = maybeStrategy.get();
    if (strategy.mode() != StrategyMode.PAPER) {
      return StrategyService.ArchiveResult.failed("Refusing to delete non-PAPER strategy");
    }
    try {
      StrategyService paperService = strategyServiceForMode(StrategyMode.PAPER);
      if (paperService != null) {
        paperService.delete(strategy.id());
      } else {
        strategyOrderRepository.deleteByStrategyId(strategy.id());
        strategyEventRepository.deleteByStrategyId(strategy.id());
        strategyRepository.deleteById(strategy.id());
        suppressRemoteSyncFor(strategy);
      }
      log("[PORTFOLIO] Deleted PAPER mode entry for " + strategy.symbol() + ".");
      return StrategyService.ArchiveResult.success(strategy.id());
    } catch (Exception ex) {
      return StrategyService.ArchiveResult.failed(ex.getMessage());
    }
  }

  /**
   * Deletes a row cleaned off the grid: a recommendation that never placed its base buy, or one the operator cancelled and left waiting for a manual restart. Guarded again here
   * rather than trusting the filter, so a row that has since taken a position or placed an order is never removed.
   */
  private StrategyService.ArchiveResult deleteCleanableGridStrategy(String strategyId) {
    if (strategyId == null || strategyId.isBlank()) {
      return StrategyService.ArchiveResult.failed("Strategy id is missing");
    }
    Optional<Strategy> maybeStrategy = strategyRepository.findById(strategyId);
    if (maybeStrategy.isEmpty()) {
      return StrategyService.ArchiveResult.failed("Strategy not found");
    }
    Strategy strategy = maybeStrategy.get();
    boolean pendingPlacement = PendingBaseBuyPlacementSupport.isPendingBaseBuyPlacement(strategy);
    boolean cancelledByUser = strategy.status() == StrategyStatus.PAUSED
        && strategy.pauseReason() == PauseReason.MANUAL_LIMIT_BUY_CANCELED;
    if (!pendingPlacement && !cancelledByUser) {
      return StrategyService.ArchiveResult.failed(
          "Only pending recommendations and user-cancelled rows can be cleaned");
    }
    if (PortfolioActionMatchers.isPendingOrderState(strategy.currentState())) {
      return StrategyService.ArchiveResult.failed("A broker order is still working for this row");
    }
    if (loadPositionForStrategy(strategy).getTotalShares() != 0) {
      return StrategyService.ArchiveResult.failed("This row still holds shares");
    }
    strategyOrderRepository.deleteByStrategyId(strategy.id());
    strategyEventRepository.deleteByStrategyId(strategy.id());
    strategyRepository.deleteById(strategy.id());
    suppressRemoteSyncFor(strategy);
    log("[PORTFOLIO] Cleaned " + strategy.symbol() + " off the grid ("
        + (pendingPlacement ? "pending base buy" : "cancelled by user") + ").");
    return StrategyService.ArchiveResult.success(strategy.id());
  }

  private StrategyService.ArchiveResult deletePendingBaseBuyStrategy(String strategyId) {
    if (strategyId == null || strategyId.isBlank()) {
      return StrategyService.ArchiveResult.failed("Strategy id is missing");
    }
    Optional<Strategy> maybeStrategy = strategyRepository.findById(strategyId);
    if (maybeStrategy.isEmpty()) {
      return StrategyService.ArchiveResult.failed("Strategy not found");
    }
    Strategy strategy = maybeStrategy.get();
    if (!PendingBaseBuyPlacementSupport.isPendingBaseBuyPlacement(strategy)) {
      return StrategyService.ArchiveResult.failed("Only pending base-buy recommendations can be deleted");
    }
    strategyOrderRepository.deleteByStrategyId(strategy.id());
    strategyEventRepository.deleteByStrategyId(strategy.id());
    strategyRepository.deleteById(strategy.id());
    suppressRemoteSyncFor(strategy);
    log("[PORTFOLIO] Deleted pending base-buy recommendation for " + strategy.symbol() + ".");
    return StrategyService.ArchiveResult.success(strategy.id());
  }

  /**
   * Records a deletion made through the repositories directly (bypassing StrategyService) so the broker sync does not recreate the strategy from its still-open position or order.
   */
  private void suppressRemoteSyncFor(Strategy strategy) {
    if (strategy != null) {
      remoteSyncSuppressionRepository.suppress(strategy.symbol(), strategy.mode());
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
    shutdownPollingServices();
    configureCompanionLivePollingService();
    if (runtimeClient == null) {
      strategyService = null;
      log("[POLL][RUNTIME] Polling disabled for "
          + (mode == ApplicationMode.LIVE ? "LIVE" : "PAPER")
          + " mode because Alpaca credentials are not configured.");
      return;
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

          @Override
          public void onRulesAnalyzed(String strategyId, String symbol, List<StrategyEngine.RuleOutcome> outcomes) {
            SwingUtilities.invokeLater(() -> logRulesAnalyzed(symbol, outcomes));
          }
        },
        new TradeEmailNotificationService.EmailNotificationListener() {
          @Override
          public void onEmailSent(String eventType, String symbol, String recipientEmail, String subject) {
            SwingUtilities.invokeLater(() -> logEmailStatus(eventType, symbol, recipientEmail, "sent", null));
          }

          @Override
          public void onEmailFailed(String eventType, String symbol, String recipientEmail, String subject, String error) {
            SwingUtilities.invokeLater(() -> logEmailStatus(eventType, symbol, recipientEmail, "failed", error));
          }
        }
    );
    strategyService = runtimeServices.strategyService();
    strategyService.setRemoteSyncSuppressionRepository(remoteSyncSuppressionRepository);
    strategyPollingService = runtimeServices.strategyPollingService();
    strategyPollingService.setAutoCorrectionListener((strategyId, symbol, message) ->
        SwingUtilities.invokeLater(() -> notifyAutoCorrection(symbol, message)));
  }

  private void configureCompanionLivePollingService() {
    if (!shouldRunCompanionLivePolling(selectedViewMode)) {
      companionLivePollingService = null;
      return;
    }
    HttpAlpacaClient liveClient = alpacaClientForMode(ApplicationMode.LIVE);
    if (liveClient == null) {
      companionLivePollingService = null;
      return;
    }
    TradingRuntimeSupport.RuntimeServices runtimeServices = tradingRuntimeSupport.createRuntimeServices(
        liveClient,
        ApplicationMode.LIVE,
        StrategyPollingService.PollListener.NOOP
    );
    companionLivePollingService = runtimeServices.strategyPollingService();
  }

  private void shutdownPollingServices() {
    if (strategyPollingService != null) {
      strategyPollingService.shutdown();
      strategyPollingService = null;
    }
    if (companionLivePollingService != null) {
      companionLivePollingService.shutdown();
      companionLivePollingService = null;
    }
  }

  /**
   * An automatic safety correction changed a strategy — make it visible, but never block trading.
   */
  private void notifyAutoCorrection(String symbol, String message) {
    log("[AUTO-CORRECTION][" + symbol + "] " + message);
    toastNotifier.showWarning("Auto-correction applied — " + message);
    refreshStrategyTableContent();
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

  private SettingsDialog.ConnectionResult runConnectionTest(BrokerType brokerType, ApplicationMode mode, String apiKey, String apiSecret, boolean manualTrigger,
      boolean applyRuntimeChanges) {
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
    startAsyncLogUploadService();
    restoreStrategies();
  }

  private void startAsyncLogUploadService() {
    if (asyncLogUploadService != null || !AppMetadata.logUploadEnabled()
        || !settingsDialog.diagnosticLogSharingEnabled()) {
      return;
    }
    SpacesLogUploader.LogUploadConfig config = new SpacesLogUploader.LogUploadConfig(
        true,
        AppMetadata.logUploadSpacesEndpoint(),
        AppMetadata.logUploadSpacesRegion(),
        AppMetadata.logUploadSpacesBucket(),
        AppMetadata.logUploadSpacesAccessKey(),
        AppMetadata.logUploadSpacesSecretKey()
    );
    asyncLogUploadService = new AsyncLogUploadService(
        new LogArchiveService(rotatingLogWriter.logDirectory(), AppMetadata.logUploadArchiveDirectory()),
        new LogUploadStatusStore(AppMetadata.appDataDirectory().resolve("log-upload-status.json")),
        new SpacesLogUploader(config),
        identityService.generateUserId(settingsDialog.getUserEmail()),
        settingsDialog.getUserEmail(),
        AppMetadata.logUploadMarketCloseTime(),
        AppMetadata.logUploadMaxRetryCount(),
        AppMetadata.logUploadRetryBackoff(),
        this::log
    );
    asyncLogUploadService.start();
  }

  private void triggerPollingCycle() {
    detectAndHandleWakeFromSleep();
    boolean hasPrimaryPolling = strategyPollingService != null;
    boolean hasCompanionPolling = companionLivePollingService != null;
    if (!hasPrimaryPolling && !hasCompanionPolling) {
      return;
    }
    boolean runStrategyPolling = shouldRunStrategyPollingCycleNow();
    Set<String> brokerSnapshotStrategyIds = brokerSnapshotStrategyIdsDueForRefresh();
    if ((!runStrategyPolling && brokerSnapshotStrategyIds.isEmpty())
        || !pollingCycleInFlight.compareAndSet(false, true)) {
      return;
    }
    markBrokerSnapshotRefreshAttempt(brokerSnapshotStrategyIds);
    uiPollingExecutor.submit(() -> {
      try {
        int dueStrategies = runStrategyPolling && hasPrimaryPolling
            ? strategyPollingService.pollDueStrategies()
            : 0;
        if (runStrategyPolling && hasCompanionPolling) {
          companionLivePollingService.pollDueStrategies();
        }
        StrategyPollingService.MarketClosedAutoRepairSummary startupAutoRepairSummary = startupMarketClosedRepairAuditLogged
            || !runStrategyPolling
            || !hasPrimaryPolling
            ? new StrategyPollingService.MarketClosedAutoRepairSummary(List.of(), Map.of())
            : strategyPollingService.drainMarketClosedAutoRepairedStrategyIds();
        List<Strategy> stored = strategyRepository.findAll();
        List<Strategy> snapshotRefreshStrategies = strategiesForBrokerSnapshotRefresh(stored, brokerSnapshotStrategyIds);
        Map<String, Boolean> overnightEligibility = loadOvernightEligibilityForStrategies(stored);
        Map<String, Position> positionSnapshots = !snapshotRefreshStrategies.isEmpty()
            ? loadPositionSnapshotsForStrategies(snapshotRefreshStrategies, stored)
            : Map.of();
        Map<String, MarketBar> dailyBars = loadDailyBarSnapshots(stored);
        SwingUtilities.invokeLater(() -> {
          try {
            syncStrategies(stored);
            applyOvernightEligibilitySnapshots(overnightEligibility);
            applyPositionSnapshots(positionSnapshots);
            applyDailyBarSnapshots(dailyBars);
            if (dueStrategies > 0 && shouldRunBrokerBackedUiRefresh()) {
              refreshStrategyTableContent();
              refreshPanels();
            } else if (!positionSnapshots.isEmpty()) {
              refreshStrategyTableContent();
              refreshPanels();
            }
            logStartupMarketClosedRepairAudit(startupAutoRepairSummary);
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

  /**
   * Called at the top of every polling tick to detect a system-sleep gap. If the gap between ticks exceeds {@link #WAKE_GAP_DETECTION_MS} the system was likely suspended. We reset
   * the stream-reconnect backoff so the next retry fires at minimum delay rather than the current (potentially multi-minute) exponential ceiling. Must be called on the EDT.
   */
  private void detectAndHandleWakeFromSleep() {
    long now = System.currentTimeMillis();
    long lastTick = lastPollingTickMillis;
    lastPollingTickMillis = now;
    if (lastTick > 0 && now - lastTick > WAKE_GAP_DETECTION_MS) {
      long gapSeconds = (now - lastTick) / 1000;
      handleWakeFromSleep(gapSeconds);
    }
  }

  /**
   * Handles recovery after a detected system-sleep gap.
   * <ul>
   *   <li>Resets the stream reconnect backoff counter so the reconnect fires at
   *       minimum delay instead of the accumulated exponential delay.</li>
   *   <li>If the stream has already detected its error and flagged itself as
   *       reconnect-available, triggers an immediate reconnect attempt.</li>
   *   <li>Strategy polling recovers automatically: all active strategies will be
   *       "due" on the next cycle because their {@code lastPolledAt} timestamps are
   *       stale relative to the current time.</li>
   * </ul>
   * Must be called on the EDT.
   */
  private void handleWakeFromSleep(long gapSeconds) {
    log("[WAKE] System resumed after ~" + gapSeconds + "s gap. Resetting stream reconnect backoff.");
    resetTradeStreamReconnectBackoff("system wake after " + gapSeconds + "s");
    // If the stream has already flagged a connection error, reconnect immediately
    // instead of waiting for the (now-cancelled) backoff timer.
    if (streamReconnectAvailable) {
      attemptAutoTradeStreamReconnect();
    }
  }

  private void logStartupMarketClosedRepairAudit(StrategyPollingService.MarketClosedAutoRepairSummary summary) {
    if (startupMarketClosedRepairAuditLogged) {
      return;
    }
    startupMarketClosedRepairAuditLogged = true;
    if (summary == null || summary.isEmpty()) {
      return;
    }
    String categorySummary = summary.formatSummary();
    String idList = String.join(", ", summary.strategyIds());
    log("[STARTUP][MARKET_CLOSE_REPAIR] " + categorySummary + " | IDs: " + idList);
  }

  private boolean shouldRunStrategyPollingCycleNow() {
    if (!shouldSuppressBrokerBackedRefreshForClosedMarket()) {
      return true;
    }
    long now = System.currentTimeMillis();
    long closedMarketInterval = hasMarketClosedStateToReconcile()
        ? CLOSED_MARKET_RECONCILE_POLL_INTERVAL_MILLIS
        : CLOSED_MARKET_POLL_INTERVAL_MILLIS;
    if (now - lastClosedMarketPollingCycleAtMillis >= closedMarketInterval) {
      lastClosedMarketPollingCycleAtMillis = now;
      return true;
    }
    return false;
  }

  private boolean hasMarketClosedStateToReconcile() {
    for (ManagedStrategy entry : strategies) {
      if (entry == null || entry.strategy == null) {
        continue;
      }
      if (entry.strategy.status() == StrategyStatus.PAUSED
          && entry.strategy.pauseReason() == PauseReason.AUTO_MARKET_CLOSED) {
        return true;
      }
      if (entry.strategy.status() == StrategyStatus.ACTIVE
          && entry.strategy.pauseReason() == PauseReason.MANUAL_MARKET_CLOSED_OVERRIDE) {
        return true;
      }
    }
    return false;
  }

  private Set<String> brokerSnapshotStrategyIdsDueForRefresh() {
    List<Strategy> displayedStrategies = strategies.stream()
        .map(entry -> entry.strategy)
        .toList();
    logSnapshotIntervalIfChanged(BrokerSnapshotRefreshPolicy.resolveIntervalMillis(displayedStrategies));
    Set<String> dueStrategyIds = new HashSet<>();
    for (ManagedStrategy entry : strategies) {
      if (entry == null
          || entry.strategy == null
          || !includeInBrokerSnapshotRefreshForCurrentMode(entry.strategy)
          || !entry.shouldRefreshDisplayedPosition()) {
        continue;
      }
      dueStrategyIds.add(entry.strategy.id());
    }
    return dueStrategyIds;
  }

  private void markBrokerSnapshotRefreshAttempt(Set<String> dueStrategyIds) {
    if (dueStrategyIds == null || dueStrategyIds.isEmpty()) {
      return;
    }
    long now = System.currentTimeMillis();
    for (ManagedStrategy entry : strategies) {
      if (entry != null && entry.strategy != null && dueStrategyIds.contains(entry.strategy.id())) {
        entry.markDisplayedPositionRefreshAttempt(now);
      }
    }
  }

  private List<Strategy> strategiesForBrokerSnapshotRefresh(List<Strategy> stored, Set<String> dueStrategyIds) {
    if (stored == null || stored.isEmpty() || dueStrategyIds == null || dueStrategyIds.isEmpty()) {
      return List.of();
    }
    return stored.stream()
        .filter(strategy -> strategy != null && dueStrategyIds.contains(strategy.id()))
        .filter(this::includeInBrokerSnapshotRefreshForCurrentMode)
        .toList();
  }

  private void logSnapshotIntervalIfChanged(long refreshIntervalMillis) {
    if (refreshIntervalMillis <= 0L || refreshIntervalMillis == lastLoggedSnapshotIntervalMillis) {
      return;
    }
    lastLoggedSnapshotIntervalMillis = refreshIntervalMillis;
    log("[POLL][SNAPSHOT] interval=" + (refreshIntervalMillis / 1000L)
        + "s policy=min-with-floor(2s) eligibility=ACTIVE-only");
  }

  /**
   * Explains the TIF \u00b7 Days cell: what the time in force does, and when this row joined the grid.
   */
  private String strategyTimeInForceTooltip(Strategy strategy) {
    String timeInForce = strategy.timeInForce() == null ? "" : strategy.timeInForce().name();
    StringBuilder tooltip = new StringBuilder(switch (timeInForce) {
      case "GTC" -> "<b>GTC</b>: the order keeps working until it fills or you cancel it.";
      case "DAY" -> "<b>DAY</b>: the order is cancelled at the session close if it has not filled.";
      default -> "<b>Time in force</b>: not set for this strategy.";
    });
    if (strategy.createdAt() != null) {
      String added = strategy.createdAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate()
          .format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US));
      String age = StrategyTablePresenter.gridAge(strategy.createdAt(), java.time.LocalDate.now());
      tooltip.append("<br>Added ").append(escapeHtml(added)).append(", ")
          .append("today".equals(age) ? "on the grid since today" : escapeHtml(age) + " on the grid")
          .append(".");
    }
    return TooltipStyler.html(tooltip.toString(), 320);
  }

  private String strategyStockPriceTooltip(int viewRow) {
    if (viewRow < 0) {
      return null;
    }
    int modelRow = strategyTable.convertRowIndexToModel(viewRow);
    if (modelRow < 0 || modelRow >= strategies.size()) {
      return null;
    }
    ManagedStrategy entry = strategies.get(modelRow);
    String cacheKey = stockPriceTooltipCacheKey(entry.strategy);
    StockPriceTooltipSnapshot snapshot = stockPriceTooltipSnapshots.get(cacheKey);
    if (snapshot == null || snapshot.stale(STOCK_PRICE_TOOLTIP_TTL_MILLIS)) {
      scheduleStockPriceTooltipRefresh(entry);
    }
    if (snapshot == null) {
      snapshot = StockPriceTooltipSnapshot.loading(entry.strategy.symbol());
    }
    return snapshot.tooltipText();
  }

  private Color orbPendingBaseBuyForeground(int viewRow) {
    if (viewRow < 0) {
      return null;
    }
    int modelRow = strategyTable.convertRowIndexToModel(viewRow);
    if (modelRow < 0 || modelRow >= strategies.size()) {
      return null;
    }
    ManagedStrategy entry = strategies.get(modelRow);
    return OrbPendingBaseBuyRowStyler.foreground(entry.strategy, entry.cachedPosition());
  }

  private void scheduleStockPriceTooltipRefresh(ManagedStrategy entry) {
    if (entry == null || entry.strategy == null || entry.strategy.symbol() == null || entry.strategy.symbol().isBlank()) {
      return;
    }
    String cacheKey = stockPriceTooltipCacheKey(entry.strategy);
    if (!stockPriceTooltipRefreshesInFlight.add(cacheKey)) {
      return;
    }
    StrategyMode mode = entry.strategy.mode();
    String symbol = entry.strategy.symbol();
    BigDecimal fallbackCurrent = entry.cachedPosition().getLastPrice();
    uiPollingExecutor.submit(() -> {
      try {
        ApplicationMode applicationMode = mode == StrategyMode.LIVE ? ApplicationMode.LIVE : ApplicationMode.PAPER;
        String apiKey = settingsDialog.savedApiKey(applicationMode);
        String apiSecret = settingsDialog.savedApiSecret(applicationMode);
        StockPriceTooltipSnapshot snapshot;
        if (apiKey.isBlank() || apiSecret.isBlank()) {
          snapshot = StockPriceTooltipSnapshot.fromBars(symbol, List.of(), fallbackCurrent);
        } else {
          HttpAlpacaMarketDataApi marketDataApi = new HttpAlpacaMarketDataApi(apiKey, apiSecret);
          List<MarketBar> bars = marketDataApi.getIntradayBars(symbol, LocalDate.now(), LocalDate.now(), 5);
          snapshot = StockPriceTooltipSnapshot.fromBars(symbol, bars, fallbackCurrent);
        }
        stockPriceTooltipSnapshots.put(cacheKey, snapshot);
      } catch (Exception ex) {
        stockPriceTooltipSnapshots.put(cacheKey, StockPriceTooltipSnapshot.fromBars(symbol, List.of(), fallbackCurrent));
        log("[PRICE TOOLTIP] Failed to load intraday price details for " + symbol + ": " + ex.getMessage());
      } finally {
        stockPriceTooltipRefreshesInFlight.remove(cacheKey);
      }
    });
  }

  private String stockPriceTooltipCacheKey(Strategy strategy) {
    if (strategy == null) {
      return "";
    }
    String symbol = strategy.symbol() == null ? "" : strategy.symbol().trim().toUpperCase(Locale.ROOT);
    StrategyMode mode = strategy.mode() == null ? StrategyMode.PAPER : strategy.mode();
    return mode.name() + ":" + symbol;
  }

  private Map<String, Position> loadPositionSnapshotsForStrategies(List<Strategy> stored) {
    return loadPositionSnapshotsForStrategies(stored, stored);
  }

  /**
   * @param stored               strategies whose displayed position is being refreshed this tick.
   * @param allocationPopulation every strategy that could own part of a broker position. A symbol's single netted position is split across the local rows that hold it, so that
   *                             split has to see all of them — not just the refresh batch, in which a newly added row can look like the symbol's only owner.
   */
  private Map<String, Position> loadPositionSnapshotsForStrategies(
      List<Strategy> stored, List<Strategy> allocationPopulation) {
    if (stored == null || stored.isEmpty() || currentBrokerType != BrokerType.ALPACA) {
      return Map.of();
    }
    return BrokerSnapshotLoader.loadPositionSnapshots(
        stored,
        allocationPopulation,
        this::alpacaClientForMode,
        this::includeInBrokerSnapshotRefresh,
        this::cachedBrokerPositions,
        this::localShareClaim
    );
  }

  /**
   * Shares a strategy's own filled orders account for, used to split a shared broker position.
   */
  private int localShareClaim(Strategy strategy) {
    if (strategy == null || strategy.id() == null || strategy.id().isBlank()) {
      return 0;
    }
    return StrategyOrderFillSupport.netFilledShares(strategyOrderRepository.findByStrategyId(strategy.id()));
  }

  private List<AlpacaPositionData> cachedBrokerPositions(ApplicationMode mode, HttpAlpacaClient client) {
    if (mode == null || client == null) {
      return List.of();
    }
    long now = System.currentTimeMillis();
    BrokerPositionsCacheEntry cached = brokerPositionSnapshotCache.get(mode);
    if (cached != null && now - cached.loadedAtMillis() < BROKER_POSITION_SNAPSHOT_TTL_MILLIS) {
      return cached.positions();
    }
    List<AlpacaPositionData> positions = List.copyOf(client.getPositions());
    brokerPositionSnapshotCache.put(mode, new BrokerPositionsCacheEntry(positions, now));
    return positions;
  }

  private void invalidateBrokerPositionSnapshotCache(StrategyMode mode) {
    if (mode == null) {
      brokerPositionSnapshotCache.clear();
      return;
    }
    brokerPositionSnapshotCache.remove(mode == StrategyMode.LIVE ? ApplicationMode.LIVE : ApplicationMode.PAPER);
  }

  /**
   * One batched market-data call for every visible symbol's today-session bar, powering the Open / Today's Low / Today's High columns. Throttled by
   * {@link #DAILY_BAR_SNAPSHOT_TTL_MILLIS} and run off the EDT by the caller. Returns empty when unavailable so the grid shows "-" rather than a stale or fabricated price.
   */
  private Map<String, MarketBar> loadDailyBarSnapshots(List<Strategy> stored) {
    if (stored == null || stored.isEmpty() || currentBrokerType != BrokerType.ALPACA) {
      return Map.of();
    }
    long now = System.currentTimeMillis();
    if (now - lastDailyBarFetchAtMillis < DAILY_BAR_SNAPSHOT_TTL_MILLIS) {
      return Map.of();
    }
    HttpAlpacaClient client = alpacaClientForMode(selectedViewMode == StrategyMode.LIVE
        ? ApplicationMode.LIVE
        : ApplicationMode.PAPER);
    if (client == null) {
      return Map.of();
    }
    List<String> symbols = stored.stream()
        .filter(strategy -> strategy.symbol() != null && !strategy.symbol().isBlank())
        .map(strategy -> strategy.symbol().trim().toUpperCase(Locale.ROOT))
        .distinct()
        .toList();
    if (symbols.isEmpty()) {
      return Map.of();
    }
    try {
      Map<String, MarketBar> bars = client.getDailySnapshots(symbols);
      lastDailyBarFetchAtMillis = now;
      return bars;
    } catch (Exception ex) {
      log("Daily open/high/low fetch failed: " + ex.getMessage());
      return Map.of();
    }
  }

  private void applyDailyBarSnapshots(Map<String, MarketBar> bars) {
    if (bars == null || bars.isEmpty()) {
      return;
    }
    for (ManagedStrategy entry : strategies) {
      if (entry.strategy == null || entry.strategy.symbol() == null) {
        continue;
      }
      MarketBar bar = bars.get(entry.strategy.symbol().trim().toUpperCase(Locale.ROOT));
      if (bar != null) {
        entry.setCachedDailyBar(bar);
        // The grid already loads this bar for "Today's High"; the engine cannot see intraday peaks
        // between polls, so hand it the same number.
        if (bar != null) {
          com.neuralarc.service.SessionHighCache.shared().record(entry.strategy.symbol(),
              LocalDate.now(java.time.ZoneId.of("America/New_York")), bar.high());
        }
      }
    }
  }

  /** Broker shares this row cannot account for, for the grid and the status line. */
  private int untrackedSharesFor(Strategy strategy) {
    if (strategy == null) {
      return 0;
    }
    for (ManagedStrategy entry : strategies) {
      if (entry.strategy.id().equals(strategy.id())) {
        return entry.untrackedShares();
      }
    }
    return 0;
  }

  /**
   * Records how much of a refreshed broker position this strategy's own orders cannot explain, and
   * says so in the log the first time it changes — shares bought without a local order are worth an
   * operator's attention, not a silent line in a table.
   */
  private void applyUntrackedShares(ManagedStrategy entry, Position snapshot) {
    int untracked = UntrackedShares.count(snapshot.getTotalShares(), localShareClaim(entry.strategy));
    if (untracked != entry.untrackedShares()) {
      entry.setUntrackedShares(untracked);
      if (untracked > 0) {
        log("[Position] " + entry.strategy.symbol() + ": the broker holds " + snapshot.getTotalShares()
            + " shares but this strategy's orders account for " + (snapshot.getTotalShares() - untracked)
            + ". " + untracked + " untracked — check the broker's order history for this symbol.");
      }
    }
  }

  private void applyPositionSnapshots(Map<String, Position> snapshots) {
    if (snapshots == null || snapshots.isEmpty()) {
      return;
    }
    List<String> healedSymbols = new ArrayList<>();
    for (ManagedStrategy entry : strategies) {
      Position snapshot = snapshots.get(entry.strategy.id());
      if (snapshot == null) {
        continue;
      }
      entry.setCachedPosition(snapshot);
      applyUntrackedShares(entry, snapshot);
      if (snapshot.getTotalShares() > 0 && healFailedStrategyFromExposure(entry.strategy, true, false, "filled")) {
        healedSymbols.add(entry.strategy.symbol());
        entry.syncFrom(strategyRepository.findById(entry.strategy.id()).orElse(entry.strategy));
      }
    }
    if (!healedSymbols.isEmpty()) {
      log("[Portfolio Refresh] Recovered stale failed status for: " + String.join(", ", healedSymbols));
    }
    logExposureStateMismatches("portfolio-refresh");
  }

  private void reconcileFailedStrategiesWithBrokerExposure(List<Strategy> storedStrategies) {
    if (storedStrategies == null || storedStrategies.isEmpty()) {
      return;
    }
    Map<StrategyMode, Set<String>> positionSymbolsByMode = new LinkedHashMap<>();
    Map<StrategyMode, Set<String>> openOrderSymbolsByMode = new LinkedHashMap<>();
    Map<StrategyMode, Map<String, String>> openOrderStatusByModeAndSymbol = new LinkedHashMap<>();
    for (StrategyMode mode : StrategyMode.values()) {
      HttpAlpacaClient client = alpacaClientForStrategyMode(mode);
      if (client == null) {
        continue;
      }
      Set<String> positionSymbols = new HashSet<>();
      for (com.neuralarc.api.AlpacaPositionData position : client.getPositions()) {
        if (position != null && position.hasExposure() && position.symbol() != null && !position.symbol().isBlank()) {
          positionSymbols.add(position.symbol().toUpperCase(Locale.ROOT));
        }
      }
      Set<String> openOrderSymbols = new HashSet<>();
      Map<String, String> orderStatusBySymbol = new LinkedHashMap<>();
      for (com.neuralarc.api.AlpacaOrderData order : client.getOpenOrders()) {
        if (order == null || order.symbol() == null || order.symbol().isBlank()) {
          continue;
        }
        String symbol = order.symbol().toUpperCase(Locale.ROOT);
        openOrderSymbols.add(symbol);
        if (!orderStatusBySymbol.containsKey(symbol)) {
          orderStatusBySymbol.put(symbol, BrokerOrderStatusUtil.normalize(order.status()));
        }
      }
      positionSymbolsByMode.put(mode, positionSymbols);
      openOrderSymbolsByMode.put(mode, openOrderSymbols);
      openOrderStatusByModeAndSymbol.put(mode, orderStatusBySymbol);
    }

    List<String> healedSymbols = new ArrayList<>();
    for (Strategy strategy : storedStrategies) {
      if (strategy == null || strategy.status() != StrategyStatus.FAILED) {
        continue;
      }
      String symbol = strategy.symbol() == null ? "" : strategy.symbol().toUpperCase(Locale.ROOT);
      boolean hasPosition = positionSymbolsByMode.getOrDefault(strategy.mode(), Set.of()).contains(symbol);
      boolean hasOpenOrder = openOrderSymbolsByMode.getOrDefault(strategy.mode(), Set.of()).contains(symbol);
      String orderStatus = openOrderStatusByModeAndSymbol
          .getOrDefault(strategy.mode(), Map.of())
          .getOrDefault(symbol, "");
      if (healFailedStrategyFromExposure(strategy, hasPosition, hasOpenOrder, orderStatus)) {
        healedSymbols.add(strategy.symbol());
      }
    }
    if (!healedSymbols.isEmpty()) {
      log("[RESTORE] Recovered stale failed status from broker exposure for: " + String.join(", ", healedSymbols));
    }
  }

  private boolean healFailedStrategyFromExposure(
      Strategy strategy,
      boolean hasPosition,
      boolean hasOpenOrder,
      String brokerOrderStatus
  ) {
    if (!FailedStrategyExposureRecovery.recover(strategy, hasPosition, hasOpenOrder, brokerOrderStatus)) {
      return false;
    }
    strategyRepository.save(strategy);
    return true;
  }

  private void logExposureStateMismatches(String phase) {
    List<String> mismatches = new ArrayList<>();
    for (ManagedStrategy entry : strategies) {
      if (entry == null || entry.strategy == null || !ModeActivity.runsFor(entry.strategy.mode(), selectedViewMode)) {
        continue;
      }
      int pendingOrders = (int) strategyOrderRepository.findByStrategyId(entry.strategy.id()).stream()
          .filter(StrategyOrder::isPending)
          .count();
      int shares = entry.cachedPosition().getTotalShares();
      boolean hasExposure = shares > 0 || pendingOrders > 0 || isWaitingForFill(entry.strategy);
      boolean staleFailed = entry.strategy.status() == StrategyStatus.FAILED
          || entry.strategy.currentState() == StrategyLifecycleState.FAILED;
      if (!hasExposure || !staleFailed) {
        continue;
      }
      mismatches.add(entry.strategy.symbol()
          + " mode=" + entry.strategy.mode().name()
          + " status=" + entry.strategy.status().name()
          + " state=" + entry.strategy.currentState().name()
          + " latestOrderStatus=" + BrokerOrderStatusUtil.normalize(entry.strategy.latestOrderStatus())
          + " shares=" + shares
          + " pendingOrders=" + pendingOrders);
    }
    if (!mismatches.isEmpty()) {
      log("[STATE AUDIT][" + phase + "] Open exposure still marked failed/closed: " + String.join(" | ", mismatches));
    }
  }

  private void handleInvalidBrokerMissingStrategies(List<Strategy> invalidStrategies) {
    if (invalidStrategies == null || invalidStrategies.isEmpty()) {
      return;
    }
    List<Strategy> markedInvalid = new ArrayList<>();
    for (Strategy strategy : invalidStrategies) {
      if (strategy == null || strategy.id() == null || strategy.id().isBlank()) {
        continue;
      }
      ManagedStrategy managed = findStrategyById(strategy.id());
      boolean hasCachedExposure = managed != null && managed.cachedPosition().getTotalShares() > 0;
      boolean hasPendingLocalOrder = strategyOrderRepository.findByStrategyId(strategy.id()).stream().anyMatch(StrategyOrder::isPending);
      if (hasCachedExposure || hasPendingLocalOrder) {
        log("[Portfolio Refresh] Skipped invalid mark for " + strategy.symbol()
            + " because open exposure is still present (cached position or pending order).");
        continue;
      }
      Optional<Strategy> maybePersisted = strategyRepository.findById(strategy.id());
      if (maybePersisted.isEmpty()) {
        continue;
      }
      Strategy persisted = maybePersisted.get();
      persisted.setStatus(StrategyStatus.FAILED);
      persisted.setCurrentState(StrategyLifecycleState.FAILED);
      persisted.setLatestOrderStatus("invalid");
      persisted.setLastError("Invalid local strategy: no matching open broker order or broker position was found during portfolio refresh.");
      persisted.setLastEvent("Marked invalid during portfolio refresh; broker has no matching open order or position.");
      strategyRepository.save(persisted);
      markedInvalid.add(persisted);
      log("[Portfolio Refresh] Marked " + persisted.symbol()
          + " invalid because Alpaca has no matching open order or position.");
    }
    if (markedInvalid.isEmpty()) {
      return;
    }
    syncStrategiesFromRepository();
    promptToDeleteInvalidStrategies(markedInvalid);
  }

  private void promptToDeleteInvalidStrategies(List<Strategy> invalidStrategies) {
    if (invalidStrategies == null || invalidStrategies.isEmpty()) {
      return;
    }
    String symbols = invalidStrategies.stream()
        .map(Strategy::symbol)
        .filter(symbol -> symbol != null && !symbol.isBlank())
        .limit(8)
        .collect(Collectors.joining(", "));
    String ellipsis = invalidStrategies.size() > 8 ? ", ..." : "";
    int choice = JOptionPane.showConfirmDialog(
        this,
        "<html><body style='width:380px'>"
            + "<b>Delete invalid local strategy record(s)?</b><br><br>"
            + "These strategy records no longer match any open Alpaca order or broker position.<br><br>"
            + "Symbols: " + symbols + ellipsis + "<br><br>"
            + "Delete them locally now so they stop appearing as failed/invalid?"
            + "</body></html>",
        "Invalid Local Strategies",
        JOptionPane.YES_NO_OPTION,
        JOptionPane.WARNING_MESSAGE
    );
    if (choice != JOptionPane.YES_OPTION) {
      log("[Portfolio Refresh] User kept " + invalidStrategies.size()
          + " invalid local strategy record(s) for manual cleanup.");
      return;
    }
    int deleted = 0;
    List<String> failures = new ArrayList<>();
    for (Strategy strategy : invalidStrategies) {
      StrategyService.ArchiveResult result = deleteLocalTradeHistoryStrategy(strategy.id());
      if (result.success()) {
        deleted++;
      } else {
        failures.add(strategy.symbol() + ": " + result.error());
      }
    }
    syncStrategiesFromRepository();
    log("[Portfolio Refresh] Deleted " + deleted + " invalid local strategy record(s).");
    if (!failures.isEmpty()) {
      log("[Portfolio Refresh] Invalid cleanup failures: " + String.join(" | ", failures));
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
    List<Strategy> syncedRemoteStrategies = strategyService == null
        ? List.of()
        : strategyService.syncRemoteStrategies();
    storedStrategies = strategyRepository.findAll();
    reconcileFailedStrategiesWithBrokerExposure(storedStrategies);
    storedStrategies = strategyRepository.findAll();
    for (Strategy strategy : storedStrategies) {
      if (strategyService != null) {
        strategy = strategyService.recoverStaleRestartFailure(strategy.id()).orElse(strategy);
      }
      ManagedStrategy managed = new ManagedStrategy(strategy);
      resetPollingCountdown(managed);
      strategies.add(managed);
      log("[" + strategy.symbol() + "] Restored (" + strategy.status().name() + ").");
    }
    for (Strategy strategy : syncedRemoteStrategies) {
      log("[" + strategy.symbol() + "] Synced from Alpaca and resumed locally.");
    }
    applyStartupViewMode(storedStrategies);
    if (strategyWorkspaceTabs != null) {
      strategyWorkspaceTabs.rebuild();
    }
    if (storedStrategies.isEmpty()) {
      refreshPanels();
      updateStatusBar();
      maybePromptForDefaultStrategy();
      return;
    }
    refreshStrategyTableData();
    if (canSelectFirstRestoredRow(strategies.size(), strategyTable.getRowCount())) {
      strategyTable.setRowSelectionInterval(0, 0);
    } else {
      strategyTable.clearSelection();
    }
    updateSelectedStrategy();
    updateHeaderModeStatus(currentBrokerType);
    refreshPanels();
    updateStatusBar();
    logExposureStateMismatches("restore");
    reconcileOpenOrdersWithBrokerOnStartup();
  }

  /**
   * On startup, reconcile every locally stored pending order against the broker so an order that is still accepted/new/pending is never shown as filled after a restart. Broker
   * state wins. Runs on a background thread; the grid is re-synced on the EDT afterwards. Best-effort: if the broker is unreachable, local state is left untouched and later
   * polling/streaming will correct it.
   */
  private void reconcileOpenOrdersWithBrokerOnStartup() {
    if (!connectionOk) {
      return;
    }
    List<Strategy> pendingStrategies = new ArrayList<>();
    for (ManagedStrategy managed : strategies) {
      if (managed == null || managed.strategy == null) {
        continue;
      }
      boolean hasPending = strategyOrderRepository.findByStrategyId(managed.strategy.id())
          .stream().anyMatch(StrategyOrder::isPending);
      if (hasPending) {
        pendingStrategies.add(managed.strategy);
      }
    }
    if (pendingStrategies.isEmpty()) {
      return;
    }
    uiPollingExecutor.execute(() -> {
      for (Strategy strategy : pendingStrategies) {
        try {
          StrategyService service = strategyServiceForMode(strategy.mode());
          if (service != null) {
            service.refreshOrderStatusesFromBroker(strategy.id());
          }
        } catch (RuntimeException ex) {
          log("[RESTORE] Broker order-status refresh failed for " + strategy.symbol() + ": " + ex.getMessage());
        }
      }
      SwingUtilities.invokeLater(() -> {
        syncStrategiesFromRepository();
        refreshStrategyTableData();
        refreshPanels();
        updateStatusBar();
      });
    });
  }

  static boolean canSelectFirstRestoredRow(int strategyCount, int visibleRowCount) {
    return strategyCount > 0 && visibleRowCount > 0;
  }

  private void applyStartupViewMode(List<Strategy> storedStrategies) {
    StrategyMode startupMode = startupViewMode(storedStrategies);
    if (startupMode != selectedViewMode) {
      selectedViewMode = startupMode;
      selectedStrategyId = null;
      log("[MODE] Startup default view set to " + selectedViewMode.name()
          + " because live strategies " + (startupMode == StrategyMode.LIVE ? "exist." : "do not exist."));
      // The connection may already be up, bound to the Paper default: rebind it, as a manual switch
      // does. Without this a Live-view session kept polling Paper and listening to the Paper stream.
      if (connectionOk) {
        rebindRuntimeToSelectedMode();
      }
    }
    syncModeToggleSelection();
    applyViewModeTheme();
    applyAvailableFundsTextForMode(selectedApplicationMode());
  }

  static StrategyMode startupViewMode(List<Strategy> storedStrategies) {
    if (storedStrategies != null) {
      for (Strategy strategy : storedStrategies) {
        if (isStartupLiveStrategy(strategy)) {
          return StrategyMode.LIVE;
        }
      }
    }
    return StrategyMode.PAPER;
  }

  private static boolean isStartupLiveStrategy(Strategy strategy) {
    return strategy != null
        && strategy.mode() == StrategyMode.LIVE
        && strategy.status() != StrategyStatus.ARCHIVED
        && strategy.status() != StrategyStatus.STOPPED;
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
    userActionLog.started("Add New Stock Strategy");
    if (!ensureLegalDisclosureAccepted()) {
      userActionLog.canceled("Add New Stock Strategy");
      return;
    }
    if (strategyWorkspaceTabs != null && strategyWorkspaceTabs.isHistorySelected()) {
      userActionLog.failed("Add New Stock Strategy", "Select All Stocks or a strategy workspace tab first.");
      JOptionPane.showMessageDialog(
          this,
          "Switch to All Stocks or a strategy workspace tab before adding a new strategy.",
          "Strategy Tab Required",
          JOptionPane.WARNING_MESSAGE
      );
      return;
    }
    if (!connectionOk || tradingApi == null) {
      userActionLog.failed("Add New Stock Strategy", "Connection is required before adding a strategy.");
      JOptionPane.showMessageDialog(this, "Please complete Settings and verify the connection before adding a strategy.", "Connection Required", JOptionPane.WARNING_MESSAGE);
      return;
    }

    String selectedApiKey = savedApiKeyForSelectedMode();
    String selectedApiSecret = savedApiSecretForSelectedMode();
    HttpAlpacaMarketDataApi marketDataApi = !selectedApiKey.isBlank() && !selectedApiSecret.isBlank()
        ? new HttpAlpacaMarketDataApi(selectedApiKey, selectedApiSecret)
        : null;
    StrategyDialog dialog = new StrategyDialog(
        this,
        null,
        marketDataApi,
        autoAnalyzeResultStore,
        settingsDialog.appliedDefaultStrategyPollingSeconds(),
        settingsDialog.appliedDefaultRepeatCycleAfterProfitExitEnabled(),
        settingsDialog.appliedDefaultResubmitOnExpiryEnabled(),
        settingsDialog.appliedManualBuyTimeInForce()
    );
    StrategyConfig config = dialog.showDialog();
    if (config == null) {
      userActionLog.canceled("Add New Stock Strategy");
      return;
    }

    StrategyMode targetMode = selectedViewMode;
    String targetWorkspaceId = selectedWorkspaceForNewStrategy();
    boolean allowDuplicateSymbols = settingsDialog.appliedAllowDuplicateSymbolStrategies();
    if (DuplicateSymbolPolicy.wouldBeDuplicate(
        config.symbol(),
        targetMode,
        strategyRepository.findAll(),
        allowDuplicateSymbols,
        targetWorkspaceId,
        ""
    )) {
      userActionLog.failed("Add New Stock Strategy", "An active or paused strategy for " + config.symbol() + " already exists.");
      JOptionPane.showMessageDialog(
          this,
          duplicateSymbolAlertMessage(config.symbol(), targetWorkspaceId, allowDuplicateSymbols, true),
          "Duplicate Symbol",
          JOptionPane.WARNING_MESSAGE
      );
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
    NewStrategyWorkspaceAssignment.apply(strategy, targetWorkspaceId, workspaceService);
    StrategyService modeAwareService = strategyServiceForMode(targetMode);
    if (modeAwareService == null) {
      userActionLog.failed("Add New Stock Strategy", targetMode + " broker client is not configured.");
      JOptionPane.showMessageDialog(
          this,
          selectedModeLabel() + " Alpaca credentials are required before adding a " + selectedModeLabel() + " strategy.",
          selectedModeLabel() + " Credentials Required",
          JOptionPane.WARNING_MESSAGE
      );
      return;
    }
    StrategyService.StrategyCreationResult creationResult = modeAwareService.createAndActivate(strategy);
    if (!creationResult.success()) {
      JOptionPane.showMessageDialog(
          this,
          "Failed to submit initial Alpaca limit buy order: " + creationResult.error(),
          "Strategy Activation Failed",
          JOptionPane.ERROR_MESSAGE
      );
      log("[" + config.symbol() + "] Strategy failed during initial order placement: " + creationResult.error());
      userActionLog.failed("Add New Stock Strategy", creationResult.error());
      return;
    }
    log("[" + config.symbol() + "] Initial order submitted. rule=BASE_BUY, price=$"
        + strategy.baseBuyLimitPrice().toPlainString()
        + ", clientOrderId=" + creationResult.clientOrderId());
    userActionLog.completed("Add New Stock Strategy", config.symbol() + " " + selectedModeLabel() + " initial limit buy submitted.");
    JOptionPane.showMessageDialog(
        this,
        "Initial Alpaca limit buy submitted successfully.\nOrder ID: " + creationResult.alpacaOrderId(),
        "Strategy Activated",
        JOptionPane.INFORMATION_MESSAGE
    );

    ensureAnalyticsPublisher();
    syncStrategiesFromRepository();
    updateHeaderModeStatus(currentBrokerType);
    selectedStrategyId = strategy.id();
    refreshStrategyTableData();
    SwingUtilities.invokeLater(() -> selectAndRevealStrategy(strategy.id()));
    updateSelectedStrategy();
    refreshPanels();
  }

  private void openSmartPicksTrendingStocksDialog(SmartPicksTrendingStocksDialog.StrategyUniverse universe) {
    String actionName = switch (universe) {
      case DIVERSIFIED_TOP_20 -> "Smart Picks: Diversified Leaders (Top 20)";
      case WEEKEND_REBOUND -> "Smart Picks: Weekend Rebound";
      default -> "Smart Picks: High Volatility Movers";
    };
    userActionLog.started(actionName);
    log("[Smart Picks] Menu action clicked. source=" + universe);
    StrategyMode targetMode = selectedViewMode;
    String apiKey = savedApiKeyForSelectedMode();
    String apiSecret = savedApiSecretForSelectedMode();
    if (apiKey.isBlank() || apiSecret.isBlank()) {
      userActionLog.failed(actionName, "Alpaca credentials are required.");
      JOptionPane.showMessageDialog(
          this,
          "Please complete Settings with " + selectedModeLabel() + " Alpaca credentials before using Smart Picks.",
          "Alpaca Credentials Required",
          JOptionPane.WARNING_MESSAGE
      );
      return;
    }
    HttpAlpacaMarketDataApi marketDataApi = new HttpAlpacaMarketDataApi(apiKey, apiSecret);
    Consumer<SmartPicksSimulationSelection> reviewHandler = selection -> {
      StrategyRecommendation recommendation = switch (selection.selectedRecommendationType()) {
        case HIGH_RISK_SHORT_TERM -> selection.analysis().highRiskShortTermRecommendation();
        case LONG_TERM -> selection.analysis().longTermRecommendation();
        default -> selection.analysis().shortTermRecommendation();
      };
      if (recommendation == null || !recommendation.isApplicable()) {
        JOptionPane.showMessageDialog(TradingFrame.this,
            "The selected recommendation is not ready. Run Auto Analyze with a valid symbol first.",
            "Recommendation Not Ready",
            JOptionPane.WARNING_MESSAGE);
        return;
      }
      StrategyApplyService applyService = new StrategyApplyService();
      StrategyApplyService.AppliedStrategyValues values =
          applyService.applyRecommendationToCurrentStrategy(recommendation);
      BigDecimal currentPriceForGuard = selection.stock() == null ? null : selection.stock().latestPrice();
      if (currentPriceForGuard == null || currentPriceForGuard.compareTo(BigDecimal.ZERO) <= 0) {
        currentPriceForGuard = recommendation.currentPrice();
      }
      BigDecimal guardedBaseBuyPrice = SmartPicksSimulationPlacementController.adjustedSmartPicksPaperBaseBuyPrice(
          values.buyRulePrice(),
          currentPriceForGuard
      );
      int pollingSeconds = settingsDialog.appliedDefaultStrategyPollingSeconds();
      boolean repeatCycle = settingsDialog.appliedDefaultRepeatCycleAfterProfitExitEnabled();
      boolean resubmit = settingsDialog.appliedDefaultResubmitOnExpiryEnabled();
      StrategyConfig prefilledConfig = new StrategyConfig(
          selection.stock().symbol(),
          guardedBaseBuyPrice,
          Math.max(1, selection.buyQuantity()),
          values.enableStopLoss(),
          values.stopLossPrice(),
          true,
          values.sellRulePrice(),
          values.lossBuy1Price(),
          Math.max(1, selection.buyQuantity()),
          values.lossBuy2Price(),
          Math.max(1, selection.buyQuantity()),
          values.enableLossBuyLevels(),
          false,
          BigDecimal.ZERO,
          pollingSeconds,
          true,
          false,
          false,
          ProfitHoldType.PERCENT_TRAILING,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          repeatCycle,
          ProfitControlMode.SELL_TRIGGER,
          ThresholdType.FIXED_AMOUNT,
          BigDecimal.ZERO,
          TrailingType.PERCENTAGE,
          BigDecimal.ZERO,
          resubmit
      );
      StrategyDialog strategyDialog = new StrategyDialog(
          TradingFrame.this,
          prefilledConfig,
          marketDataApi,
          autoAnalyzeResultStore,
          pollingSeconds,
          repeatCycle,
          resubmit,
          settingsDialog.appliedManualBuyTimeInForce()
      );
      StrategyConfig config = strategyDialog.showDialog();
      if (config == null) {
        return;
      }
      boolean allowDuplicates = settingsDialog.appliedAllowDuplicateSymbolStrategies();
      String targetWorkspaceId = selectedWorkspaceForNewStrategy();
      if (DuplicateSymbolPolicy.wouldBeDuplicate(
          config.symbol(), targetMode, strategyRepository.findAll(), allowDuplicates, targetWorkspaceId, "")) {
        JOptionPane.showMessageDialog(TradingFrame.this,
            duplicateSymbolAlertMessage(config.symbol(), targetWorkspaceId, allowDuplicates, false),
            "Duplicate Symbol",
            JOptionPane.WARNING_MESSAGE);
        return;
      }
      Strategy strategy = Strategy.fromConfig(
          UUID.randomUUID().toString(),
          smartPicksStrategyName(selection, config.symbol(), targetMode),
          config,
          targetMode
      );
      NewStrategyWorkspaceAssignment.apply(strategy, targetWorkspaceId, workspaceService);
      strategy.setLastEvent(smartPicksEntrySourceEvent(selection, strategy.baseBuyLimitPrice(), targetMode));
      StrategyService service = strategyServiceForMode(targetMode);
      if (service == null) {
        JOptionPane.showMessageDialog(TradingFrame.this,
            selectedModeLabel() + " Alpaca credentials are required before starting this strategy.",
            selectedModeLabel() + " Credentials Required",
            JOptionPane.WARNING_MESSAGE);
        return;
      }
      StrategyService.StrategyCreationResult creationResult =
          service.createAndActivate(strategy);
      if (!creationResult.success()) {
        JOptionPane.showMessageDialog(TradingFrame.this,
            "Failed to start strategy: " + creationResult.error(),
            "Strategy Activation Failed",
            JOptionPane.ERROR_MESSAGE);
        return;
      }
      log("[Smart Picks] " + config.symbol() + " " + targetMode + " strategy created from review dialog.");
      userActionLog.completed(actionName + " Review", config.symbol() + " " + selectedModeLabel() + " strategy added.");
      syncStrategiesFromRepository();
      refreshStrategyTableData();
      updateStatusBar();
      refreshPanels();
      JOptionPane.showMessageDialog(TradingFrame.this,
          config.symbol() + " " + selectedModeLabel() + " strategy created successfully.",
          "Strategy Added",
          JOptionPane.INFORMATION_MESSAGE);
    };
    SmartPicksTrendingStocksDialog dialog = new SmartPicksTrendingStocksDialog(
        this,
        new TrendingStocksService(new HttpAlpacaScreenerClient(apiKey, apiSecret)),
        marketDataApi,
        this::placeSmartPicksSimulationStrategies,
        this::log,
        targetMode,
        universe
    );
    dialog.setReviewHandler(reviewHandler);
    dialog.setVisible(true);
  }

  private String smartPicksEntrySourceEvent(SmartPicksSimulationSelection selection, BigDecimal baseBuyLimitPrice, StrategyMode mode) {
    String stockReason = selection.stock().reason() == null || selection.stock().reason().isBlank()
        ? "smart-picks-simulation"
        : selection.stock().reason();
    String basePrice = baseBuyLimitPrice == null
        ? "-"
        : baseBuyLimitPrice.toPlainString();
    String modeLabel = mode == StrategyMode.LIVE ? "Alpaca Live" : "Alpaca Paper";
    return modeLabel + " mode from Smart Picks. Selected "
        + selection.selectedRecommendationType().name()
        + ". Source " + stockReason
        + ". Base limit buy $" + basePrice
        + ".";
  }

  private String smartPicksStrategyName(SmartPicksSimulationSelection selection, String symbol, StrategyMode mode) {
    return "SMART_PICKS_" + smartPicksSourceToken(selection) + ": " + symbol + " "
        + (mode == StrategyMode.LIVE ? "Live" : "Paper");
  }

  private String smartPicksSourceToken(SmartPicksSimulationSelection selection) {
    String reason = selection == null || selection.stock() == null || selection.stock().reason() == null
        ? ""
        : selection.stock().reason().toLowerCase(Locale.ROOT);
    if (reason.contains("gainer")) {
      return "GAINERS";
    }
    if (reason.contains("loser")) {
      return "LOSERS";
    }
    if (reason.contains("weekend rebound")) {
      return "WEEKEND_REBOUND";
    }
    return "REVIEWED";
  }

  private void placeSmartPicksSimulationStrategies(List<SmartPicksSimulationSelection> selections) {
    StrategyMode targetMode = selectedViewMode;
    SmartPicksSimulationPlacementController controller = new SmartPicksSimulationPlacementController(new SmartPicksSimulationPlacementController.Gateway() {
      @Override
      public com.neuralarc.service.StrategyRepository repository() {
        return strategyRepository;
      }

      @Override
      public StrategyService.StrategyCreationResult createPaperStrategy(Strategy strategy) {
        return createStrategy(strategy, targetMode);
      }

      @Override
      public StrategyService.StrategyCreationResult createStrategy(Strategy strategy, StrategyMode requestedMode) {
        StrategyService service = strategyServiceForMode(requestedMode);
        if (service == null) {
          return StrategyService.StrategyCreationResult.failed("Strategy service is not configured for " + requestedMode);
        }
        return service.createAndActivate(strategy);
      }

      @Override
      public boolean confirmReplaceWaitingPaperStrategy(String symbol) {
        int choice = JOptionPane.showConfirmDialog(
            TradingFrame.this,
            "A " + selectedModeLabel().toLowerCase(Locale.ROOT) + " strategy already exists for " + symbol
                + " with a limit buy waiting to fill.\n\nReplace it with the new one?",
            selectedModeLabel() + " Strategy Exists",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );
        return choice == JOptionPane.YES_OPTION;
      }

      @Override
      public boolean allowDuplicateSymbols() {
        return settingsDialog.appliedAllowDuplicateSymbolStrategies();
      }

      @Override
      public String targetWorkspaceId() {
        return selectedWorkspaceForNewStrategy();
      }

      @Override
      public int defaultStrategyPollingSeconds() {
        return settingsDialog.appliedDefaultStrategyPollingSeconds();
      }

      @Override
      public boolean defaultRepeatCycleAfterProfitExitEnabled() {
        return settingsDialog.appliedDefaultRepeatCycleAfterProfitExitEnabled();
      }

      @Override
      public boolean defaultResubmitOnExpiryEnabled() {
        return settingsDialog.appliedDefaultResubmitOnExpiryEnabled();
      }

      @Override
      public void cancelAndDeletePaperStrategy(String strategyId) {
        strategyServiceForMode(targetMode).delete(strategyId);
      }

      @Override
      public void afterPlacement() {
        onSmartPicksPlaced();
      }

      @Override
      public void log(String message) {
        TradingFrame.this.log(message);
      }
    }, targetMode);
    SmartPicksSimulationPlacementController.PlacementResult result = controller.place(selections);
    if (result.canceled()) {
      return;
    }
    String message = controller.summaryMessage(result);
    JOptionPane.showMessageDialog(this, message, "Smart Picks", JOptionPane.INFORMATION_MESSAGE);
    userActionLog.completed("Smart Picks", message.replace('\n', ' '));
  }

  private void editStrategy(int viewRow) {
    int row = strategyTable.convertRowIndexToModel(viewRow);
    if (row < 0 || row >= strategies.size()) {
      return;
    }

    ManagedStrategy entry = strategies.get(row);
    reviewAndSaveStrategy(entry, entry.toEditConfig(), "Edit Strategy " + entry.strategy.symbol());
  }

  /**
   * The grid's Auto Analyze action: the same editor as Edit, opened on its Auto Analyze tab.
   */
  private void autoAnalyzeStrategy(int viewRow) {
    int row = strategyTable.convertRowIndexToModel(viewRow);
    if (row < 0 || row >= strategies.size()) {
      return;
    }
    ManagedStrategy entry = strategies.get(row);
    reviewAndSaveStrategy(entry, entry.toEditConfig(), "Auto Analyze " + entry.strategy.symbol(), true);
  }

  /**
   * Opens the strategy editor seeded with {@code seed} and saves whatever comes back, so every path that rewrites a strategy is reviewed on the same screen first. Edit seeds it
   * with the strategy's current settings; Minimize Loss Impact seeds it with its plan. Returns the saved strategy, or empty when the operator cancelled or the save was refused.
   */
  private Optional<Strategy> reviewAndSaveStrategy(ManagedStrategy entry, StrategyConfig seed, String action) {
    return reviewAndSaveStrategy(entry, seed, action, false);
  }

  private Optional<Strategy> reviewAndSaveStrategy(ManagedStrategy entry, StrategyConfig seed, String action,
      boolean openOnAutoAnalyze) {
    userActionLog.started(action);
    HttpAlpacaMarketDataApi marketDataApi = connectionOk && !runtimeApiKey.isBlank()
        ? new HttpAlpacaMarketDataApi(runtimeApiKey, runtimeApiSecret) : null;
    StrategyDialog dialog = new StrategyDialog(this, seed, marketDataApi, autoAnalyzeResultStore);
    StrategyConfig updated = openOnAutoAnalyze ? dialog.showAutoAnalyze() : dialog.showDialog();
    if (updated == null) {
      userActionLog.canceled(action);
      return Optional.empty();
    }

    boolean allowDuplicateSymbols = settingsDialog.appliedAllowDuplicateSymbolStrategies();
    if (DuplicateSymbolPolicy.wouldBeDuplicate(
        updated.symbol(),
        entry.strategy.mode(),
        strategyRepository.findAll(),
        allowDuplicateSymbols,
        entry.strategy.workspaceId(),
        entry.strategy.id()
    )) {
      userActionLog.failed(action, "An active or paused strategy for " + updated.symbol() + " already exists.");
      JOptionPane.showMessageDialog(
          this,
          duplicateSymbolAlertMessage(updated.symbol(), entry.strategy.workspaceId(), allowDuplicateSymbols, false),
          "Duplicate Symbol",
          JOptionPane.WARNING_MESSAGE
      );
      return Optional.empty();
    }

    Strategy updatedStrategy = Strategy.fromConfig(entry.strategy.id(), entry.strategy.name(), updated, entry.strategy.mode());
    updatedStrategy.setWorkspaceId(entry.strategy.workspaceId());
    updatedStrategy.setStatus(entry.strategy.status());
    updatedStrategy.setCurrentState(entry.strategy.currentState());
    updatedStrategy.setLastPolledAt(entry.strategy.lastPolledAt());
    updatedStrategy.setLastEvent(entry.strategy.lastEvent());
    updatedStrategy.setLatestOrderStatus(entry.strategy.latestOrderStatus());
    updatedStrategy.setLatestAlpacaOrderId(entry.strategy.latestAlpacaOrderId());
    updatedStrategy.setLastError(entry.strategy.lastError());
    // Preserve the auto-adjust per-day progress across edits so changing config does not silently
    // restart the monitoring window or revert the day's already-applied adjustment.
    updatedStrategy.setAutoAdjustDayCount(entry.strategy.autoAdjustDayCount());
    updatedStrategy.setAutoAdjustLastAdjustedDate(entry.strategy.autoAdjustLastAdjustedDate());
    updatedStrategy.setAutoAdjustReferencePrice(entry.strategy.autoAdjustReferencePrice());
    StrategyService modeAwareService = strategyServiceForMode(entry.strategy.mode());
    if (modeAwareService == null) {
      userActionLog.failed(action, entry.strategy.mode() + " broker client is not configured.");
      JOptionPane.showMessageDialog(
          this,
          "Broker client is not configured for this strategy mode.",
          "Strategy Update Failed",
          JOptionPane.ERROR_MESSAGE
      );
      return Optional.empty();
    }
    Optional<Strategy> updatedResult = modeAwareService.updateStrategy(updatedStrategy);
    if (updatedResult.isEmpty()) {
      userActionLog.failed(action, "Strategy service rejected the update.");
      JOptionPane.showMessageDialog(
          this,
          "Failed to update strategy. Please review values and try again.",
          "Strategy Update Failed",
          JOptionPane.ERROR_MESSAGE
      );
      return Optional.empty();
    }
    entry.syncFrom(updatedResult.get());
    resetPollingCountdown(entry);
    updateHeaderModeStatus(currentBrokerType);
    refreshStrategyTableData();
    refreshEditedStrategyBrokerSnapshotAsync(updatedResult.get().id());
    refreshPanels();
    userActionLog.completed(action, "Strategy saved.");
    return updatedResult;
  }

  private void refreshEditedStrategyBrokerSnapshotAsync(String strategyId) {
    if (strategyId == null || strategyId.isBlank() || currentBrokerType != BrokerType.ALPACA) {
      return;
    }
    uiPollingExecutor.execute(() -> {
      Optional<Strategy> persisted = strategyRepository.findById(strategyId);
      if (persisted.isEmpty()) {
        return;
      }
      Map<String, Position> snapshots = loadPositionSnapshotsForStrategies(List.of(persisted.get()));
      if (snapshots.isEmpty()) {
        return;
      }
      SwingUtilities.invokeLater(() -> {
        Strategy latest = strategyRepository.findById(strategyId).orElse(persisted.get());
        ManagedStrategy managed = findStrategyById(strategyId);
        if (managed != null) {
          managed.syncFrom(latest);
        }
        applyPositionSnapshots(snapshots);
        refreshStrategyTableData();
        refreshPanels();
        updateStatusBar();
      });
    });
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
    refreshActiveCaptureIndicator();
    ManagedStrategy entry = selectedManagedStrategy();
    refreshPositionBarsColumn(entry);
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
    String currentRuleSummary = buildRuleTriggeredShortSummary(entry.strategy, entry, latestOrder, pendingOrder.orElse(null));
    ruleState.setText(ruleTriggeredHistoryPresenter.buildLabel(
        currentRuleSummary,
        strategyOrders,
        this::formatTimestampForDisplay
    ));
    ruleState.setToolTipText(TooltipStyler.html(
        ruleTriggeredHistoryPresenter.buildTooltip(
            buildRuleTriggeredSummary(entry.strategy, latestOrder, pendingOrder.orElse(null)),
            strategyOrders,
            this::formatTimestampForDisplay
        ),
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
    if (strategy.status() != StrategyStatus.ACTIVE) {
      return false;
    }
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
    String stateDisplay = displayStatusLabel(entry);

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

  private String displayStatusLabel(ManagedStrategy entry) {
    if (entry == null) {
      return "";
    }
    Position cachedPosition = entry.cachedPosition();
    boolean waitingForFill = isWaitingForFill(entry.strategy) && cachedPosition.getTotalShares() == 0;
    return strategyTablePresenter.displayStatusLabel(
        entry.strategy,
        cachedPosition,
        isStrategySessionSuppressed(entry.strategy),
        waitingForFill,
        entry.strategy.status() == StrategyStatus.FAILED && isQueueableSessionError(entry.strategy.lastError()),
        !connectionOk || connectionRetryPending,
        entry.cachedRealizedPnl(),
        entry.cachedLastSellPrice(),
        entry.cachedPendingManualBuy(),
        entry.cachedPendingLimitSell()
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

  private boolean isBrokerReachabilityTooltipReason(String normalizedOrderStatus, String reason) {
    if ("failed_transport".equals(normalizedOrderStatus) || "api_error".equals(normalizedOrderStatus)) {
      return true;
    }
    if (reason == null || reason.isBlank()) {
      return false;
    }
    String normalizedReason = reason.toLowerCase(Locale.ROOT);
    return normalizedReason.contains("unable to reach broker")
        || normalizedReason.contains("broker api error")
        || normalizedReason.contains("transport")
        || normalizedReason.contains("connection");
  }

  private boolean isExpiredOrderTooltipReason(String normalizedOrderStatus, String reason) {
    if (!"expired".equals(normalizedOrderStatus) || reason == null || reason.isBlank()) {
      return false;
    }
    return "alpaca order expired".equalsIgnoreCase(reason.trim());
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

  private String escapeHtml(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;");
  }

  /**
   * Repaints the top status-bar P&L totals: the All Stocks aggregate (forWorkspace(null)) over the exact same per-strategy accounts the tab summary uses, so both agree by
   * construction.
   *
   * <p>The realized half is the <em>trading day's</em> realized P&L — the sells filled during the
   * current local calendar day, the same trades Trade History lists for that day. It is taken from the sell records rather than the strategy rows, so profit stays counted after a
   * sold position is cleaned off the Current Strategies grid, and it resets on the next trading day.
   */
  private void updateUnrealizedSummaries() {
    WorkspaceAccountingInputs.Result inputs = buildAccountingInputs(selectedViewMode);
    WorkspaceAccounting.Snapshot total =
        WorkspaceAccounting.forWorkspace(null, inputs.accounts(), inputs.sells());
    String summary = " P&L (Unrealized/Realized Today): "
        + total.unrealized().toPlainString()
        + " / "
        + total.dailyRealized().toPlainString();
    String tooltip = TooltipStyler.text(
        "Unrealized is the open P&L of current positions. Realized Today is the profit banked by "
            + "sells filled today, from the same trades listed in Trade History for this day — "
            + "it keeps counting positions already cleaned off the Current Strategies grid, and "
            + "starts again at the next trading day. All-time realized P&L stays on the per-tab "
            + "summary below the grid.",
        360);
    if (selectedViewMode == StrategyMode.LIVE) {
      liveUnrealizedSummary.setText("LIVE" + summary);
      liveUnrealizedSummary.setToolTipText(tooltip);
    } else {
      paperUnrealizedSummary.setText("Paper" + summary);
      paperUnrealizedSummary.setToolTipText(tooltip);
    }
    applyHeaderTotalsVisibility();
  }

  private BigDecimal realizedPnlForStrategy(String strategyId) {
    return realizedPnlForOrders(strategyOrderRepository.findByStrategyId(strategyId));
  }

  private void refreshStrategyTradeSnapshots() {
    for (ManagedStrategy entry : strategies) {
      refreshStrategyTradeSnapshot(entry);
    }
  }

  private void refreshStrategyTradeSnapshot(ManagedStrategy entry) {
    if (entry == null || entry.strategy == null) {
      return;
    }
    List<StrategyOrder> orders = strategyOrderRepository.findByStrategyId(entry.strategy.id());
    BigDecimal lastSellPrice = latestFilledSellPrice(orders);
    BigDecimal realized = realizedPnlForOrders(orders);
    entry.setTradeSnapshot(lastSellPrice, realized, latestPendingLimitBuy(orders), latestPendingLimitSell(orders));
    entry.setCachedBaseBuyExecutedPrice(baseBuyExecutedPrice(orders));
  }

  /**
   * The price the base buy actually filled at, not the configured limit. Falls back to the most recent filled buy of any stage so a manually-entered position still shows what was
   * paid.
   */
  private BigDecimal baseBuyExecutedPrice(List<StrategyOrder> orders) {
    return orders.stream()
        .filter(order -> order.side() == StrategyOrderSide.BUY)
        .filter(order -> order.status() == StrategyOrderStatus.FILLED)
        .filter(order -> order.filledAveragePrice() != null
            && order.filledAveragePrice().compareTo(BigDecimal.ZERO) > 0)
        .sorted(Comparator.comparing(
                (StrategyOrder order) -> order.stage() == StrategyStage.BASE_BUY ? 0 : 1)
            .thenComparing(StrategyOrder::filledAt, Comparator.nullsLast(Comparator.naturalOrder())))
        .map(StrategyOrder::filledAveragePrice)
        .findFirst()
        .orElse(Monetary.zero());
  }

  private StrategyTablePresenter.PendingOrderSummary latestPendingLimitBuy(List<StrategyOrder> orders) {
    return orders.stream()
        .filter(order -> order.orderType() == StrategyOrderType.LIMIT)
        .filter(order -> order.side() == StrategyOrderSide.BUY)
        .filter(StrategyOrder::isPending)
        .max(Comparator
            .comparing(StrategyOrder::updatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(StrategyOrder::submittedAt, Comparator.nullsLast(Comparator.naturalOrder())))
        .map(order -> new StrategyTablePresenter.PendingOrderSummary(
            order.limitPrice(),
            order.requestedQuantity(),
            order.stage() == StrategyStage.MANUAL_BUY
        ))
        .orElse(null);
  }

  private StrategyTablePresenter.PendingOrderSummary latestPendingLimitSell(List<StrategyOrder> orders) {
    return orders.stream()
        .filter(order -> order.orderType() == StrategyOrderType.LIMIT)
        .filter(order -> order.side() == StrategyOrderSide.SELL)
        .filter(StrategyOrder::isPending)
        .max(Comparator
            .comparing(StrategyOrder::updatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(StrategyOrder::submittedAt, Comparator.nullsLast(Comparator.naturalOrder())))
        .map(order -> new StrategyTablePresenter.PendingOrderSummary(
            order.limitPrice(),
            order.requestedQuantity(),
            false,
            order.stage() == com.neuralarc.model.StrategyStage.TARGET_SELL))
        .orElse(null);
  }

  private BigDecimal latestFilledSellPrice(List<StrategyOrder> orders) {
    return orders.stream()
        .filter(order -> order.side() == StrategyOrderSide.SELL)
        .filter(order -> order.status() == StrategyOrderStatus.FILLED || order.status() == StrategyOrderStatus.PARTIALLY_FILLED)
        .filter(order -> StrategyOrderFillSupport.resolvedFilledQuantity(order).compareTo(BigDecimal.ZERO) > 0)
        .max(Comparator
            .comparing(StrategyOrder::filledAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(StrategyOrder::submittedAt, Comparator.nullsLast(Comparator.naturalOrder())))
        .map(StrategyOrderFillSupport::resolvedFillPrice)
        .orElse(BigDecimal.ZERO);
  }

  private BigDecimal realizedPnlForOrders(List<StrategyOrder> orders) {
    List<StrategyOrder> filledOrders = orders.stream()
        .filter(order -> order.status() == StrategyOrderStatus.FILLED || order.status() == StrategyOrderStatus.PARTIALLY_FILLED)
        .filter(order -> StrategyOrderFillSupport.resolvedFilledQuantity(order).compareTo(BigDecimal.ZERO) > 0)
        .sorted(Comparator
            .comparing(StrategyOrder::filledAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(StrategyOrder::submittedAt, Comparator.nullsLast(Comparator.naturalOrder())))
        .toList();

    BigDecimal positionQty = BigDecimal.ZERO;
    BigDecimal averageCost = BigDecimal.ZERO;
    BigDecimal realized = BigDecimal.ZERO;

    for (StrategyOrder order : filledOrders) {
      BigDecimal quantity = StrategyOrderFillSupport.resolvedFilledQuantity(order);
      BigDecimal fillPrice = StrategyOrderFillSupport.resolvedFillPrice(order);

      if (order.side() == StrategyOrderSide.BUY) {
        BigDecimal runningCost = averageCost.multiply(positionQty).add(fillPrice.multiply(quantity));
        positionQty = positionQty.add(quantity);
        if (positionQty.compareTo(BigDecimal.ZERO) > 0) {
          averageCost = runningCost.divide(positionQty, 8, java.math.RoundingMode.HALF_UP);
        }
        continue;
      }

      com.neuralarc.service.SellBasis.Result basis = com.neuralarc.service.SellBasis.of(
          fillPrice, quantity, positionQty, averageCost,
          com.neuralarc.service.SellBasis.brokerAverageEntry(order));
      if (basis.isEmpty()) {
        continue;
      }
      realized = realized.add(basis.realized());
      // Only the tracked shares leave the tracked position, however the sale was priced.
      positionQty = positionQty.subtract(quantity.min(positionQty.max(BigDecimal.ZERO)));
      if (positionQty.compareTo(BigDecimal.ZERO) <= 0) {
        positionQty = BigDecimal.ZERO;
        averageCost = BigDecimal.ZERO;
      }
    }

    return Monetary.round(realized);
  }

  private void applyHeaderTotalsVisibility() {
    paperUnrealizedSummary.setVisible(selectedViewMode == StrategyMode.PAPER);
    liveUnrealizedSummary.setVisible(selectedViewMode == StrategyMode.LIVE);
    headerTotalsSeparator.setVisible(false);
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

  private boolean selectAndRevealStrategy(String strategyId) {
    if (strategyId == null || strategyId.isBlank()) {
      return false;
    }
    selectedStrategyId = strategyId;
    restoreSelectedRow();
    int selectedViewRow = strategyTable.getSelectedRow();
    if (selectedViewRow < 0) {
      return false;
    }
    strategyTable.scrollRectToVisible(strategyTable.getCellRect(selectedViewRow, 0, true));
    return true;
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

  /**
   * Points the bars column at the selected row, or, with none selected, at the first losing position in grid order — the one most worth a look.
   */
  private void refreshPositionBarsColumn(ManagedStrategy selected) {
    if (positionBarsColumn == null) {
      return;
    }
    if (selected != null) {
      positionBarsColumn.show(selected, false);
      return;
    }
    positionBarsColumn.show(firstLosingPositionInGrid(), true);
  }

  private ManagedStrategy firstLosingPositionInGrid() {
    for (int viewRow = 0; viewRow < strategyTable.getRowCount(); viewRow++) {
      int modelRow = strategyTable.convertRowIndexToModel(viewRow);
      if (modelRow < 0 || modelRow >= strategies.size()) {
        continue;
      }
      ManagedStrategy candidate = strategies.get(modelRow);
      if (PositionBarsSelection.isLosing(candidate)) {
        return candidate;
      }
    }
    return null;
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
    refreshStrategyTradeSnapshots();
    strategyTableModel.fireTableDataChanged();
    if (strategyTable.getRowSorter() instanceof TableRowSorter<?> sorter) {
      sorter.allRowsChanged();
    }
    refreshFilledOrdersTableData();
    refreshGridSearchVisibility();
    refreshStrategyWorkspaceEmptyState();
    preservingSelection = false;
    SwingUtilities.invokeLater(this::restoreSelectedRow);
  }

  private void refreshStrategyTableRow(int modelRow) {
    if (modelRow < 0 || modelRow >= strategyTableModel.getRowCount()) {
      return;
    }
    rememberSelectedStrategy();
    preservingSelection = true;
    refreshStrategyTradeSnapshot(strategies.get(modelRow));
    fireStrategyTableRowUpdatedIfVisible(modelRow);
    preservingSelection = false;
    SwingUtilities.invokeLater(this::restoreSelectedRow);
  }

  private void refreshStrategyTableContent() {
    if (strategies.isEmpty()) {
      strategyTableModel.fireTableDataChanged();
      refreshFilledOrdersTableData();
      strategyTable.clearSelection();
      selectedStrategyId = null;
      refreshStrategyWorkspaceEmptyState();
      refreshPortfolioAnalytics();
      return;
    }
    // Row count can change between polls; full refresh keeps sorter/model indexes consistent.
    rememberSelectedStrategy();
    preservingSelection = true;
    refreshStrategyTradeSnapshots();
    strategyTableModel.fireTableDataChanged();
    refreshFilledOrdersTableData();
    refreshGridSearchVisibility();
    refreshStrategyWorkspaceEmptyState();
    preservingSelection = false;
    refreshPortfolioAnalytics();
    SwingUtilities.invokeLater(() -> {
      restoreSelectedRow();
      strategyTable.repaint();
      filledOrdersTable.repaint();
    });
  }

  private boolean includeInCurrentStrategiesTab(ManagedStrategy entry) {
    return entry != null && entry.strategy != null
        && entry.strategy.mode() == selectedViewMode
        && includeInStrategiesTab(entry);
  }

  /**
   * Whether a row belongs on the current (not history) grid of its own mode.
   */
  private boolean includeInStrategiesTab(ManagedStrategy entry) {
    if (entry == null || entry.strategy == null) {
      return false;
    }
    if (entry.strategy.status() == StrategyStatus.FAILED) {
      if (includeFailedStrategyInCurrentTab(entry.strategy)) {
        return true;
      }
      // Keep failed rows visible when there is still open broker exposure.
      return hasOpenExposure(entry);
    }
    // Shared with the broker-position allocator so unclaimed shares are never parked on a row
    // this method hides: one rule, so the two cannot drift apart.
    if (StrategyRowVisibility.hiddenFromCurrentTab(entry.strategy)) {
      return false;
    }
    if (entry.strategy.status() == StrategyStatus.PAUSED
        && (entry.strategy.pauseReason() == PauseReason.AUTO_MARKET_CLOSED
        || entry.strategy.pauseReason() == PauseReason.MANUAL_MARKET_CLOSED_OVERRIDE
        || entry.strategy.pauseReason() == PauseReason.SYSTEM_ERROR)) {
      return true;
    }
    if (entry.strategy.status() == StrategyStatus.PAUSED
        && (entry.strategy.pauseReason() == PauseReason.MANUAL_LIMIT_BUY_CANCELED
        || entry.strategy.pauseReason() == PauseReason.USER_PAUSED)) {
      return true;
    }
    if (includeScannerRecommendationInCurrentTab(entry.strategy)) {
      return true;
    }
    // Keep showing rows that still have live exposure on the broker side.
    return entry.strategy.status() == StrategyStatus.ACTIVE
        || isWaitingForFill(entry.strategy);
  }

  static boolean includeScannerRecommendationInCurrentTab(Strategy strategy) {
    return StrategyRecommendationMarkers.isScannerRecommendationRow(strategy);
  }

  static boolean matchesPortfolioActionScope(Strategy strategy, StrategyMode selectedMode, String selectedWorkspaceId) {
    if (strategy == null || selectedMode == null || strategy.mode() != selectedMode) {
      return false;
    }
    return selectedWorkspaceId == null || selectedWorkspaceId.equals(strategy.workspaceId());
  }

  static boolean includeFailedStrategyInCurrentTab(Strategy strategy) {
    if (strategy == null || strategy.status() != StrategyStatus.FAILED) {
      return false;
    }
    String latestOrderStatus = BrokerOrderStatusUtil.normalize(strategy.latestOrderStatus());
    return "invalid".equals(latestOrderStatus)
        || "expired".equals(latestOrderStatus);
  }

  static JPanel composeFooterBars(JPanel portfolioBarPanel, JPanel mainBarPanel) {
    JPanel footerBars = new JPanel(new BorderLayout());
    footerBars.setOpaque(false);
    footerBars.add(portfolioBarPanel, BorderLayout.NORTH);
    footerBars.add(mainBarPanel, BorderLayout.SOUTH);
    return footerBars;
  }

  private boolean hasOpenExposure(ManagedStrategy entry) {
    if (entry == null || entry.strategy == null) {
      return false;
    }
    if (entry.cachedPosition().getTotalShares() > 0) {
      return true;
    }
    if (isWaitingForFill(entry.strategy)) {
      return true;
    }
    List<StrategyOrder> orders = strategyOrderRepository.findByStrategyId(entry.strategy.id());
    return orders.stream().anyMatch(StrategyOrder::isPending);
  }

  private boolean includeInBrokerSnapshotRefresh(Strategy strategy) {
    return BrokerSnapshotRefreshPolicy.eligibleForBrokerSnapshot(strategy);
  }

  private boolean includeInBrokerSnapshotRefreshForCurrentMode(Strategy strategy) {
    return includeInBrokerSnapshotRefresh(strategy)
        && shouldPollPositionMode(strategy == null ? null : strategy.mode(), selectedViewMode);
  }

  static boolean shouldPollPositionMode(StrategyMode strategyMode, StrategyMode activeViewMode) {
    StrategyMode effectiveStrategyMode = strategyMode == null ? StrategyMode.PAPER : strategyMode;
    StrategyMode effectiveViewMode = activeViewMode == null ? StrategyMode.PAPER : activeViewMode;
    return effectiveStrategyMode == StrategyMode.LIVE || effectiveViewMode == StrategyMode.PAPER;
  }

  static boolean shouldRunCompanionLivePolling(StrategyMode activeViewMode) {
    return activeViewMode == null || activeViewMode == StrategyMode.PAPER;
  }

  private void refreshFilledOrdersTableData() {
    List<HistoryTablePresenter.HistorySource> sources = new ArrayList<>();
    for (ManagedStrategy entry : strategies) {
      if (entry.strategy.mode() != selectedViewMode) {
        continue;
      }
      List<StrategyOrder> orders = strategyOrderRepository.findByStrategyId(entry.strategy.id());
      sources.add(new HistoryTablePresenter.HistorySource(
          entry.strategy.symbol(),
          gridBrokerModeLabel(entry.strategy),
          displayStatusLabel(entry),
          entry.strategy.currentState() == null ? "-" : formatLifecycleStateForDisplay(entry.strategy.currentState()),
          entry.strategy.latestOrderStatus(),
          entry.strategy.lastPolledAt(),
          entry.strategy.status(),
          orders
      ));
    }
    filledOrderRows.clear();
    filledOrderRows.addAll(historyTablePresenter.buildRows(
        sources,
        this::formatTimestampForDisplay,
        tradeHistoryGroupBy,
        selectedTradeHistorySellFilter()
    ));
    filledOrdersTableModel.fireTableDataChanged();
    applyTradeHistoryRowFilter();
    refreshReenterHistoryButton();
    refreshTradeHistoryHeading();
    refreshGridSearchVisibility();
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
    entry.pollIntervalMillis = effectivePollingIntervalMillis(entry);
    if (entry.pollInFlight) {
      return;
    }
    if (shouldShowPollingIndicator(entry)) {
      Instant lastPolledAt = entry.strategy.lastPolledAt();
      PollingCountdownPolicy.Result result = PollingCountdownPolicy.resolve(
          entry.countdownActive,
          entry.nextPollDueAtMillis,
          entry.lastAppliedPolledAtEpochMilli,
          entry.lastAppliedPollIntervalMillis,
          lastPolledAt == null ? null : lastPolledAt.toEpochMilli(),
          entry.pollIntervalMillis,
          System.currentTimeMillis()
      );
      entry.countdownActive = true;
      entry.nextPollDueAtMillis = result.nextPollDueAtMillis();
      entry.lastAppliedPolledAtEpochMilli = result.lastAppliedPolledAtEpochMilli();
      entry.lastAppliedPollIntervalMillis = result.lastAppliedPollIntervalMillis();
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
    entry.pollIntervalMillis = effectivePollingIntervalMillis(entry);
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
    entry.lastValidationSuccessAtMillis = System.currentTimeMillis();
    if (shouldShowPollingIndicator(entry)) {
      markPollingCycleCompleted(entry);
    } else {
      stopPollingCountdown(entry);
    }
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
    if (modelRow < 0 || modelRow >= strategyTableModel.getRowCount()) {
      return;
    }
    fireStrategyTableRowUpdatedIfVisible(modelRow);
    if (selectedStrategyId != null && selectedStrategyId.equals(strategyId)) {
      refreshPanels();
    }
  }

  private void fireStrategyTableRowUpdatedIfVisible(int modelRow) {
    RowSorter<? extends javax.swing.table.TableModel> rowSorter = strategyTable.getRowSorter();
    if (rowSorter != null) {
      try {
        if (strategyTable.convertRowIndexToView(modelRow) < 0) {
          return;
        }
      } catch (IndexOutOfBoundsException ex) {
        return;
      }
    }
    strategyTableModel.fireTableRowsUpdated(modelRow, modelRow);
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
      return;
    }
    // Self-heal a countdown that is overdue by more than a full interval. That means no poll
    // completion re-anchored it for at least two cycles (worker backlog, a dropped listener
    // callback, or a poll that returned early without stamping lastPolledAt), which would
    // otherwise leave the row frozen on "Poll due" forever instead of showing a live counter.
    if (!entry.pollInFlight
        && System.currentTimeMillis() - entry.nextPollDueAtMillis > entry.pollIntervalMillis) {
      entry.countdownActive = true;
      entry.nextPollDueAtMillis = System.currentTimeMillis() + entry.pollIntervalMillis;
    }
  }

  private long effectivePollingIntervalMillis(ManagedStrategy entry) {
    if (entry == null || entry.strategy == null) {
      return 1_000L;
    }
    long configuredMillis = Math.max(1L, entry.strategy.pollingIntervalSeconds()) * 1_000L;
    if (strategyPollingService == null || entry.strategy.mode() != selectedViewMode) {
      return configuredMillis;
    }
    long effectiveSeconds = Math.max(1L, strategyPollingService.effectivePollingIntervalSeconds(entry.strategy));
    return effectiveSeconds * 1_000L;
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
    Instant now = Instant.now();
    if (!strategies.isEmpty()) {
      for (ManagedStrategy entry : strategies) {
        Strategy strategy = entry == null ? null : entry.strategy;
        if (strategy == null || strategy.status() != StrategyStatus.ACTIVE) {
          continue;
        }
        if (isStrategySessionOpen(strategy, settings, now)) {
          return false;
        }
      }
    }
    return !marketHoursService.isTradingSessionOpen(settings.extendedHoursTradingEnabled());
  }

  private boolean isAutoPausedForClosedMarket(ManagedStrategy entry) {
    if (entry == null || !isStrategySessionSuppressed(entry.strategy)) {
      return false;
    }
    if (entry.strategy.status() == StrategyStatus.ACTIVE) {
      return true;
    }
    return entry.strategy.status() == StrategyStatus.PAUSED
        && entry.strategy.pauseReason() == PauseReason.AUTO_MARKET_CLOSED;
  }

  private boolean isStrategySessionSuppressed(Strategy strategy) {
    if (strategy == null || tradingApi == null || currentBrokerType != BrokerType.ALPACA) {
      return false;
    }
    AppSettingsService.AppSettings settings = appSettingsService.load();
    if (!settings.autoPausePollingWhenMarketClosed()) {
      return false;
    }
    return !isStrategySessionOpen(strategy, settings, Instant.now());
  }

  private boolean isStrategySessionOpen(Strategy strategy, AppSettingsService.AppSettings settings, Instant now) {
    if (strategy == null) {
      return false;
    }
    boolean extendedEnabled = settings != null && settings.extendedHoursTradingEnabled();
    if (!extendedEnabled) {
      return marketHoursService.isTradingSessionOpen(now, false);
    }
    boolean overnightEligible = isOvernightEligibleCached(strategy);
    return marketHoursService.isTradingSessionOpen(now, true, overnightEligible);
  }

  private Map<String, Boolean> loadOvernightEligibilityForStrategies(List<Strategy> stored) {
    if (stored == null || stored.isEmpty() || currentBrokerType != BrokerType.ALPACA) {
      return Map.of();
    }
    AppSettingsService.AppSettings settings = appSettingsService.load();
    if (!settings.autoPausePollingWhenMarketClosed() || !settings.extendedHoursTradingEnabled()) {
      return Map.of();
    }
    return OvernightEligibilityLoader.load(stored, this::alpacaClientForStrategyMode);
  }

  private void applyOvernightEligibilitySnapshots(Map<String, Boolean> overnightEligibilityByStrategyId) {
    for (ManagedStrategy entry : strategies) {
      if (entry == null || entry.strategy == null) {
        continue;
      }
      if (overnightEligibilityByStrategyId != null && overnightEligibilityByStrategyId.containsKey(entry.strategy.id())) {
        entry.setOvernightEligible(overnightEligibilityByStrategyId.get(entry.strategy.id()));
      } else {
        entry.setOvernightEligible(null);
      }
    }
  }

  private boolean isOvernightEligibleCached(Strategy strategy) {
    if (strategy == null || strategy.id() == null) {
      return false;
    }
    ManagedStrategy managed = findStrategyById(strategy.id());
    return managed != null && Boolean.TRUE.equals(managed.overnightEligible());
  }

  private String appendSessionHint(String tooltip, ManagedStrategy strategy) {
    String base = tooltip == null || tooltip.isBlank() ? "Polling status" : tooltip;
    if (strategy == null) {
      return base;
    }
    String overnightHint = strategy.overnightEligible() == null
        ? "Overnight eligible: checking"
        : strategy.overnightEligible() ? "Overnight eligible: yes" : "Overnight eligible: no";
    return base + "\n" + overnightHint;
  }

  private String appendValidationHints(String tooltip, PositionValidationCellPresenter.ViewModel validationViewModel) {
    String base = tooltip == null || tooltip.isBlank() ? "Polling status" : tooltip;
    if (validationViewModel.lifecycleState() == PositionValidationCellPresenter.ValidationLifecycleState.WARNING_PAUSED) {
      return base + "\n" + validationViewModel.attemptLabel()
          + "\nUnable to reach the broker for the shared position/order snapshot. Click Refresh Now to retry.";
    }
    if (!validationViewModel.countdownText().isBlank()) {
      return base + "\n" + validationViewModel.countdownText() + "\n" + validationViewModel.attemptLabel();
    }
    return base;
  }

  private void setStatus(String message, Color color) {
    SwingUtilities.invokeLater(() -> {
      if (message != null && message.startsWith("FAILED")) {
        statusBar.setText("<html><b>FAILED</b> Retrying...</html>");
      } else {
        statusBar.setText(message == null || message.isBlank() ? "-" : message);
      }
      statusBar.setForeground(color == null ? BOTTOM_STATUS_ACCENT : color);
    });
  }

  private String connectionModeStatus(BrokerType brokerType) {
    return "Alpaca Mode: " + selectedModeLabel();
  }

  private String gridBrokerModeLabel(Strategy strategy) {
    if (strategy == null) {
      return "Alpaca";
    }
    return strategy.mode() == StrategyMode.LIVE ? "Alpaca Live" : "Alpaca Paper";
  }

  private boolean isMarketOpenForUi() {
    return currentMarketStatusViewModel().openForUi();
  }

  private MarketStatusPresenter.MarketStatusViewModel currentMarketStatusViewModel() {
    AppSettingsService.AppSettings settings = appSettingsService.load();
    boolean regularMarketOpen = marketHoursService.isRegularMarketHours();
    boolean tradingSessionOpen = marketHoursService.isTradingSessionOpen(settings.extendedHoursTradingEnabled());
    return marketStatusPresenter.present(
        settings,
        regularMarketOpen,
        tradingSessionOpen,
        Instant.now(),
        currentNextTradingSessionOpen(settings)
    );
  }

  private String nextTradingSessionOpenDisplay() {
    AppSettingsService.AppSettings settings = appSettingsService.load();
    return currentNextTradingSessionOpen(settings)
        .atZone(java.time.ZoneId.systemDefault())
        .format(NEXT_OPEN_FORMAT);
  }

  private Instant currentNextTradingSessionOpen(AppSettingsService.AppSettings settings) {
    boolean extendedHoursEnabled = settings != null && settings.extendedHoursTradingEnabled();
    return marketHoursService.nextMarketOpen(extendedHoursEnabled);
  }

  private JPanel createGridSearchPanel(String labelText, JTextField searchField) {
    JPanel panel = new JPanel(new BorderLayout(8, 0));
    panel.setOpaque(false);
    // Horizontal insets (14px) match the bottom status-bar padding so the search
    // field and the Liquidate Portfolio button sit at the same visual margin as the
    // rest of the chrome. Vertical insets match the header bar (6px top/bottom).
    panel.setBorder(new EmptyBorder(6, 14, 6, 14));
    JPanel searchControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    searchControls.setOpaque(false);
    JLabel label = new JLabel(labelText);
    label.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
    searchField.setToolTipText(TooltipStyler.text("Type to filter rows by stock symbol."));
    searchControls.add(label);
    searchControls.add(searchField);
    panel.add(searchControls, BorderLayout.WEST);
    if (searchField == currentStrategiesSearchField) {
      capturePortfolioIndicator.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
      capturePortfolioIndicator.setForeground(CAPTURE_INDICATOR_IDLE_TEXT);
      capturePortfolioIndicator.setHorizontalAlignment(SwingConstants.RIGHT);
      JPanel indicatorPanel = new JPanel(new BorderLayout());
      indicatorPanel.setOpaque(false);
      indicatorPanel.setBorder(new EmptyBorder(0, 12, 0, 8));
      indicatorPanel.add(capturePortfolioIndicator, BorderLayout.EAST);
      // The slot never claims width from the pinned buttons, so its preferred width may exceed
      // what it is granted; keep the minimum at zero so it shrinks instead of pushing them out.
      indicatorPanel.setMinimumSize(new Dimension(0, 0));
      indicatorPanel.addComponentListener(new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent event) {
          refitCapturePortfolioIndicator();
        }
      });
      capturePortfolioIndicatorPanel = indicatorPanel;
      panel.add(indicatorPanel, BorderLayout.CENTER);

      // Pin buttons in EAST so BorderLayout always grants them their full preferred
      // width. The variable-length liquidation status now lives in CENTER and can
      // shrink without disappearing behind the blinking Liquidate Portfolio button.
      JPanel captureControls = new JPanel(new GridBagLayout());
      captureControls.setOpaque(false);
      GridBagConstraints captureGbc = new GridBagConstraints();
      captureGbc.anchor = GridBagConstraints.CENTER;
      captureGbc.gridx = 0;
      captureGbc.insets = new Insets(0, 0, 0, 0);
      captureControls.add(capturePortfolioButton, captureGbc);
      captureGbc.gridx = 1;
      captureGbc.insets = new Insets(0, 8, 0, 0);
      captureControls.add(addStrategyButton, captureGbc);
      captureGbc.gridx = 2;
      captureGbc.insets = new Insets(0, 8, 0, 0);
      captureControls.add(portfolioActionsButton, captureGbc);
      panel.add(captureControls, BorderLayout.EAST);
    } else if (searchField == tradeHistorySearchField) {
      panel.add(createTradeHistoryGroupByPanel(), BorderLayout.CENTER);
      panel.add(createTradeHistoryFilterPanel(), BorderLayout.EAST);
    }
    panel.setVisible(false);
    return panel;
  }

  private void configureFilledOrdersColumnWidths() {
    // Set sensible starting widths, but leave columns resizable so operators can tune the
    // trade-history grid for their own screen and reading preference.
    setTableColumnWidth(0, 70, 52, 90);
    setTableColumnWidth(1, 108, 78, 150);
    setTableColumnWidth(3, 132, 96, 168);
    setTableColumnWidth(4, 64, 52, 78);
    setTableColumnWidth(5, 98, 78, 130);
    setTableColumnWidth(6, 50, 38, 62);
    setTableColumnWidth(7, 64, 52, 76);
    setTableColumnWidth(8, 64, 52, 76);
    setTableColumnWidth(10, 150, 108, 185);
    // Flexible text-heavy columns start wider and absorb remaining viewport width.
    setFlexibleTableColumnWidth(2, 190, 130);
    setFlexibleTableColumnWidth(9, 260, 190);
  }

  private void setTableColumnWidth(int columnIndex, int preferredWidth, int minWidth, int maxWidth) {
    if (columnIndex < 0 || columnIndex >= filledOrdersTable.getColumnModel().getColumnCount()) {
      return;
    }
    javax.swing.table.TableColumn column = filledOrdersTable.getColumnModel().getColumn(columnIndex);
    column.setPreferredWidth(preferredWidth);
    column.setMinWidth(minWidth);
    column.setMaxWidth(Integer.MAX_VALUE);
    column.setResizable(true);
  }

  /**
   * No max width, so the column can stretch and let the table fill its scroll pane.
   */
  private void setFlexibleTableColumnWidth(int columnIndex, int preferredWidth, int minWidth) {
    if (columnIndex < 0 || columnIndex >= filledOrdersTable.getColumnModel().getColumnCount()) {
      return;
    }
    javax.swing.table.TableColumn column = filledOrdersTable.getColumnModel().getColumn(columnIndex);
    column.setPreferredWidth(preferredWidth);
    column.setMinWidth(minWidth);
    column.setMaxWidth(Integer.MAX_VALUE);
    column.setResizable(true);
  }

  private void configureTradeHistorySorting() {
    if (filledOrdersSorter == null) {
      return;
    }
    boolean symbolSortable = tradeHistoryGroupBy == TradeHistoryGroupBy.SYMBOL;
    for (int column = 0; column < HistoryGridTableModel.COLUMNS.length; column++) {
      filledOrdersSorter.setSortable(column, symbolSortable && column == 0);
    }
    filledOrdersSorter.setSortKeys(List.of());
  }

  private JPanel createTradeHistoryGroupByPanel() {
    JPanel groupPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    groupPanel.setOpaque(false);
    styleTradeHistoryGroupByButton();
    groupPanel.add(tradeHistoryGroupByButton);
    reenterHistoryButton.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
    reenterHistoryButton.setFocusPainted(false);
    reenterHistoryButton.addActionListener(event -> reenterInactiveHistoryStocks());
    groupPanel.add(reenterHistoryButton);
    historyReentryScheduleButton.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
    historyReentryScheduleButton.setFocusPainted(false);
    historyReentryScheduleButton.addActionListener(event -> editHistoryReentrySchedule());
    groupPanel.add(historyReentryScheduleButton);
    refreshHistoryReentryScheduleButton();
    return groupPanel;
  }

  private JPanel createTradeHistoryFilterPanel() {
    JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    filterPanel.setOpaque(false);
    JLabel filterLabel = new JLabel("Sell filter:");
    filterLabel.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
    filterPanel.add(filterLabel);

    ButtonGroup filterGroup = new ButtonGroup();
    configureTradeHistoryFilterButton(profitableSellsFilterButton, filterGroup,
        "Show only symbols whose completed sell history is profitable.");
    configureTradeHistoryFilterButton(lossSellsFilterButton, filterGroup,
        "Show only symbols whose completed sell history closed at a loss.");
    configureTradeHistoryFilterButton(bothSellsFilterButton, filterGroup,
        "Show profitable and loss sell groups.");

    filterPanel.add(profitableSellsFilterButton);
    filterPanel.add(lossSellsFilterButton);
    filterPanel.add(bothSellsFilterButton);
    return filterPanel;
  }

  /**
   * Stocks in this mode's Trade History that no workspace is trading any more. A symbol whose entry is still waiting to fill, or whose shares are still held, is not one of them.
   */
  private List<Strategy> inactiveHistoryStocks() {
    return inactiveHistoryStocks(selectedViewMode);
  }

  private List<Strategy> inactiveHistoryStocks(StrategyMode mode) {
    return HistoryReentry.candidates(strategies.stream().map(entry -> entry.strategy).toList(),
        mode, strategyOrderRepository::findByStrategyId, heldSymbols());
  }

  /**
   * Loads the saved schedule for the selected mode and starts the ticker; called on startup and mode switches.
   */
  private void applyHistoryReentrySchedule() {
    com.neuralarc.model.HistoryReentrySchedule saved =
        historyReentryScheduleRepository.findByMode(selectedViewMode).orElse(null);
    historyReentryScheduleService.setSchedule(saved != null && saved.enabled() ? saved : null);
    historyReentryScheduleService.start();
    refreshHistoryReentryScheduleButton();
  }

  private void refreshHistoryReentryScheduleButton() {
    // Called while the toolbar is being built, before the repositories are opened: show the off state
    // until the saved schedule can actually be read.
    com.neuralarc.model.HistoryReentrySchedule saved = historyReentryScheduleRepository == null
        ? null
        : historyReentryScheduleRepository.findByMode(selectedViewMode).orElse(null);
    boolean on = saved != null && saved.enabled();
    historyReentryScheduleButton.setText(on ? "Auto Re-entry: On" : "Auto Re-entry: Off");
    historyReentryScheduleButton.setToolTipText(TooltipStyler.text(on
        ? "Scheduled re-entry is on — " + saved.summary() + ". Click to change or switch it off."
        : "Re-enter inactive stocks on a schedule, every week or fortnight, without opening the picker.", 380));
  }

  private void editHistoryReentrySchedule() {
    String action = "Schedule Re-entry Scan";
    userActionLog.started(action);
    com.neuralarc.model.HistoryReentrySchedule existing =
        historyReentryScheduleRepository.findByMode(selectedViewMode).orElse(null);
    Optional<com.neuralarc.model.HistoryReentrySchedule> saved =
        new HistoryReentryScheduleDialog(this, selectedViewMode, existing).showDialog();
    if (saved.isEmpty()) {
      userActionLog.canceled(action);
      return;
    }
    historyReentryScheduleRepository.save(saved.get());
    applyHistoryReentrySchedule();
    log("[History Re-entry] Schedule " + (saved.get().enabled() ? "on — " + saved.get().summary() : "off") + ".");
    userActionLog.completed(action, saved.get().enabled() ? saved.get().summary() : "Turned off.");
  }

  /**
   * The scheduled run: the same placement as the button, with the picker's choices taken from the schedule instead of an operator. Conservative by construction — one group,
   * capped, and skipped entirely when its mode is not the one this app is currently working for.
   */
  private void runScheduledHistoryReentry(com.neuralarc.model.HistoryReentrySchedule schedule) {
    String action = "Scheduled Re-entry";
    if (!ModeActivity.runsFor(schedule.mode(), selectedViewMode)) {
      log("[History Re-entry] Skipped the scheduled run: " + schedule.mode() + " is not the selected mode.");
      return;
    }
    if (!connectionOk) {
      log("[History Re-entry] Broker is not connected; the scheduled run will retry later today.");
      historyReentryScheduleService.deferToday();
      return;
    }
    List<HistoryReentryPicker.Pick> chosen = inactiveHistoryStocks(schedule.mode()).stream()
        .filter(source -> schedule.group().accepts(realizedPnlForSymbolHistory(source)))
        .limit(schedule.maxStocks())
        .map(source -> new HistoryReentryPicker.Pick(source, BigDecimal.ZERO))
        .toList();
    if (chosen.isEmpty()) {
      log("[History Re-entry] Scheduled run found nothing to re-enter.");
      historyReentryScheduleRepository.save(schedule.withLastRunDate(LocalDate.now()));
      applyHistoryReentrySchedule();
      return;
    }
    log("[History Re-entry] Scheduled run placing " + chosen.size() + " stock(s): " + schedule.summary() + ".");
    historyReentryScheduleRepository.save(schedule.withLastRunDate(LocalDate.now()));
    applyHistoryReentrySchedule();
    placeHistoryReentries(chosen, schedule.mode(), action, false);
  }

  /**
   * Symbols with shares on the books right now, whatever their strategy's status says.
   */
  private java.util.Set<String> heldSymbols() {
    java.util.Set<String> held = new java.util.HashSet<>();
    for (ManagedStrategy entry : List.copyOf(strategies)) {
      Position position = entry.cachedPosition();
      if (position != null && position.getTotalShares() > 0 && entry.strategy.symbol() != null) {
        held.add(entry.strategy.symbol().toUpperCase(java.util.Locale.ROOT));
      }
    }
    return held;
  }

  private void refreshReenterHistoryButton() {
    int count = inactiveHistoryStocks().size();
    reenterHistoryButton.setText("Re-enter Inactive Stocks (" + count + ")");
    reenterHistoryButton.setEnabled(count > 0);
    reenterHistoryButton.setToolTipText(TooltipStyler.text(count == 0
        ? "Every stock in Trade History is already active in a workspace."
        : count + " stock(s) in Trade History are not active in any workspace. Place them again at their"
          + " safe low (the lowest price traded this week) in a new workspace of their own.", 360));
  }

  /**
   * Places every stock in Trade History that no workspace trades any more again, at its safe low, in a new workspace created for this run so the re-entries are easy to track
   * together.
   */
  private void reenterInactiveHistoryStocks() {
    String action = "Re-enter Inactive Stocks";
    userActionLog.started(action);
    if (!connectionOk) {
      userActionLog.failed(action, "Broker is not connected.");
      JOptionPane.showMessageDialog(this, "Connect to Alpaca before re-entering stocks.", action, JOptionPane.WARNING_MESSAGE);
      return;
    }
    List<Strategy> sources = inactiveHistoryStocks();
    if (sources.isEmpty()) {
      userActionLog.skipped(action, "Nothing to re-enter.");
      JOptionPane.showMessageDialog(this, "Every stock in Trade History is already active in a workspace.",
          action, JOptionPane.INFORMATION_MESSAGE);
      return;
    }
    StrategyMode mode = selectedViewMode;
    String workspaceName = uniqueWorkspaceName(HistoryReentry.workspaceName(LocalDate.now()), mode);
    StrategyService service = strategyServiceForMode(mode);
    ApplicationMode applicationMode = mode == StrategyMode.LIVE ? ApplicationMode.LIVE : ApplicationMode.PAPER;
    // Checked before the picker opens: nobody should tick 30 stocks only to be told the broker is
    // not configured.
    if (service == null || settingsDialog.savedApiKey(applicationMode).isBlank()) {
      userActionLog.failed(action, mode + " broker client is not configured.");
      JOptionPane.showMessageDialog(this, "Alpaca credentials for this mode are required.", action, JOptionPane.WARNING_MESSAGE);
      return;
    }
    HttpAlpacaMarketDataApi marketData = new HttpAlpacaMarketDataApi(
        settingsDialog.savedApiKey(applicationMode), settingsDialog.savedApiSecret(applicationMode));
    Optional<List<HistoryReentryPicker.Pick>> picked = new HistoryReentryPicker(this, sources, mode, workspaceName,
        symbol -> historyReentryLevels(marketData, symbol), this::realizedPnlForSymbolHistory).showDialog();
    if (picked.isEmpty() || picked.get().isEmpty()) {
      userActionLog.canceled(action);
      return;
    }
    placeHistoryReentries(picked.get(), mode, action, true);
  }

  /**
   * Places one batch of re-entries into a dated workspace of its own, off the EDT. Shared by the picker and the schedule so both go in at the same price, with the same broker-held
   * check and the same log; {@code announce} is what separates an operator's run (a summary dialog) from a scheduled one (the event log).
   */
  private void placeHistoryReentries(List<HistoryReentryPicker.Pick> chosen, StrategyMode mode, String action,
      boolean announce) {
    ApplicationMode applicationMode = mode == StrategyMode.LIVE ? ApplicationMode.LIVE : ApplicationMode.PAPER;
    StrategyService service = strategyServiceForMode(mode);
    if (service == null || settingsDialog.savedApiKey(applicationMode).isBlank()) {
      userActionLog.failed(action, mode + " broker client is not configured.");
      log("[History Re-entry] " + mode + " broker client is not configured.");
      return;
    }
    HttpAlpacaMarketDataApi marketData = new HttpAlpacaMarketDataApi(
        settingsDialog.savedApiKey(applicationMode), settingsDialog.savedApiSecret(applicationMode));
    String workspaceName = uniqueWorkspaceName(HistoryReentry.workspaceName(LocalDate.now()), mode);
    StrategyWorkspace workspace = workspaceService.create(workspaceName, "COMEBACK", mode);
    HttpAlpacaClient broker = alpacaClientForMode(applicationMode);
    log("[History Re-entry] Re-entering " + chosen.size() + " stock(s) into workspace '" + workspaceName + "'.");
    new SwingWorker<List<String>, Void>() {
      @Override
      protected List<String> doInBackground() {
        List<String> outcomes = new ArrayList<>();
        LocalDate today = LocalDate.now(java.time.ZoneId.of("America/New_York"));
        for (HistoryReentryPicker.Pick pick : chosen) {
          Strategy source = pick.source();
          try {
            // No workspace tracks it, but the broker may still hold it (an untracked position):
            // buying again would stack shares on top of it.
            if (broker != null && broker.getPosition(source.symbol())
                .map(position -> position.quantity().signum() != 0).orElse(false)) {
              outcomes.add(source.symbol() + ": skipped, still held at the broker");
              continue;
            }
            // The price the operator ticked is the price that goes in; only a row whose
            // prices had not loaded yet is worked out here.
            BigDecimal safeLow = pick.entryPrice() != null && pick.entryPrice().signum() > 0
                ? pick.entryPrice()
                : HistoryReentry.safeLow(
                    marketData.getDailyBars(source.symbol(), today.minusDays(14), today), today);
            if (safeLow.signum() <= 0) {
              outcomes.add(source.symbol() + ": skipped, no recent prices");
              continue;
            }
            StrategyService.StrategyCreationResult result =
                service.createAndActivate(HistoryReentry.reentry(source, safeLow, workspace.id()));
            outcomes.add(source.symbol() + (result.success()
                ? ": placed at $" + safeLow.toPlainString()
                : ": failed, " + result.error()));
          } catch (Exception ex) {
            outcomes.add(source.symbol() + ": failed, " + ex.getMessage());
          }
        }
        return outcomes;
      }

      @Override
      protected void done() {
        List<String> outcomes;
        try {
          outcomes = get();
        } catch (Exception ex) {
          outcomes = List.of("Failed: " + ex.getMessage());
        }
        outcomes.forEach(outcome -> log("[History Re-entry] " + outcome));
        if (strategyWorkspaceTabs != null) {
          strategyWorkspaceTabs.rebuild();
        }
        onSmartPicksPlaced();
        refreshFilledOrdersTableData();
        long placed = outcomes.stream().filter(outcome -> outcome.contains(": placed")).count();
        userActionLog.completed(action, placed + " of " + outcomes.size() + " placed in " + workspaceName + ".");
        if (announce) {
          JOptionPane.showMessageDialog(TradingFrame.this, "<html>" + placed + " of " + outcomes.size()
                  + " stock(s) placed in <b>" + workspaceName + "</b>.<br><br>"
                  + String.join("<br>", outcomes) + "</html>",
              action, JOptionPane.INFORMATION_MESSAGE);
        } else {
          toastNotifier.show(new TradeEventToastFormatter.ToastMessage(
              placed + " of " + outcomes.size() + " stock(s) re-entered in " + workspaceName + ".",
              TradeEventToastFormatter.Severity.INFO));
        }
      }
    }.execute();
  }

  /**
   * One stock's re-entry price and two-week averages, read off its daily bars.
   */
  private HistoryReentry.Levels historyReentryLevels(HttpAlpacaMarketDataApi marketData, String symbol) throws Exception {
    LocalDate today = LocalDate.now(java.time.ZoneId.of("America/New_York"));
    return HistoryReentry.levels(marketData.getDailyBars(symbol, today.minusDays(21), today), today);
  }

  /**
   * What every closed trade on this symbol came to in this mode — the gain or loss the picker groups by.
   */
  private BigDecimal realizedPnlForSymbolHistory(Strategy source) {
    BigDecimal total = BigDecimal.ZERO;
    for (ManagedStrategy entry : List.copyOf(strategies)) {
      if (entry.strategy.mode() == source.mode()
          && entry.strategy.symbol() != null
          && entry.strategy.symbol().equalsIgnoreCase(source.symbol())) {
        total = total.add(realizedPnlForStrategy(entry.strategy.id()));
      }
    }
    return total;
  }

  /**
   * {@code base}, or "base (2)", "base (3)"… when a workspace of that name already exists in {@code mode}.
   */
  private String uniqueWorkspaceName(String base, StrategyMode mode) {
    java.util.Set<String> taken = workspaceService.activeWorkspaces(mode).stream()
        .map(StrategyWorkspace::name).collect(java.util.stream.Collectors.toSet());
    String name = base;
    for (int n = 2; taken.contains(name); n++) {
      name = base + " (" + n + ")";
    }
    return name;
  }

  private void styleTradeHistoryGroupByButton() {
    tradeHistoryGroupByButton.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
    tradeHistoryGroupByButton.setFocusPainted(false);
    tradeHistoryGroupByButton.setToolTipText(TooltipStyler.text("Choose how completed trade history rows are grouped."));
  }

  private void configureTradeHistoryFilterButton(JRadioButton button, ButtonGroup group, String tooltip) {
    button.setOpaque(false);
    button.setFont(BASE_FONT.deriveFont(Font.PLAIN, 11f));
    button.setToolTipText(TooltipStyler.text(tooltip));
    group.add(button);
  }

  private JComponent createStrategiesGridCenter(JComponent grid) {
    strategiesGridCardLayout = new CardLayout();
    strategiesGridCardPanel = new JPanel(strategiesGridCardLayout);
    strategiesGridCardPanel.setOpaque(false);
    strategiesGridCardPanel.add(grid, STRATEGIES_GRID_CARD);
    strategiesGridCardPanel.add(
        wrapEmptyStateWithScanHistory(new GapRocketPanel(this::openGapRocketAnalysisDialog, true),
            gapRocketScanHistoryPanel), GAP_ROCKET_EMPTY_CARD);
    strategiesGridCardPanel.add(
        wrapEmptyStateWithScanHistory(new OrbPanel(this::openOrbAnalysisDialog),
            orbScanHistoryPanel), ORB_EMPTY_CARD);
    strategiesGridCardPanel.add(
        wrapEmptyStateWithScanHistory(new DipHunterPanel(this::openDipHunterAnalysisDialog, true),
            dipHunterScanHistoryPanel), DIP_HUNTER_EMPTY_CARD);
    strategiesGridCardPanel.add(
        wrapEmptyStateWithScanHistory(new VwapPanel(this::openVwapAnalysisDialog, true),
            vwapScanHistoryPanel), VWAP_EMPTY_CARD);
    strategiesGridCardPanel.add(
        wrapEmptyStateWithScanHistory(new SwingPanel(this::openSwingAnalysisDialog, true),
            swingScanHistoryPanel), SWING_EMPTY_CARD);
    strategiesGridCardPanel.add(
        wrapEmptyStateWithScanHistory(new RangeRiderPanel(this::openRangeRiderAnalysisDialog, true),
            rangeRiderScanHistoryPanel), RANGE_RIDER_EMPTY_CARD);
    strategiesGridCardPanel.add(
        wrapEmptyStateWithScanHistory(new ProfitShieldPanel(this::openProfitShieldAnalysisDialog, true),
            profitShieldScanHistoryPanel), PROFIT_SHIELD_EMPTY_CARD);
    strategiesGridCardPanel.add(
        wrapEmptyStateWithScanHistory(new EarningsHunterPanel(this::openEarningsHunterAnalysisDialog, true),
            earningsHunterScanHistoryPanel), EARNINGS_HUNTER_EMPTY_CARD);
    strategiesGridCardPanel.add(
        wrapEmptyStateWithScanHistory(smartPicksWorkspaceView().emptyState(), smartPicksScanHistoryPanel),
        SMART_PICKS_EMPTY_CARD);
    strategiesGridCardLayout.show(strategiesGridCardPanel, STRATEGIES_GRID_CARD);
    return strategiesGridCardPanel;
  }

  /**
   * Stack a strategy's empty-state guidance panel above its recent-scan-history table. The history table hides itself when there is no history yet, so a first-time strategy shows
   * only the guidance and Analyze button. The whole stack is centered so it matches the standalone panels.
   */
  private JComponent wrapEmptyStateWithScanHistory(JComponent emptyState, ScanHistoryTablePanel historyPanel) {
    JPanel stack = new JPanel();
    stack.setOpaque(false);
    stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
    emptyState.setAlignmentX(Component.CENTER_ALIGNMENT);
    historyPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
    stack.add(emptyState);
    stack.add(historyPanel);
    JPanel center = new JPanel(new GridBagLayout());
    center.setOpaque(false);
    center.add(stack, new GridBagConstraints());
    return center;
  }

  private JPanel createStrategiesBottomPanel() {
    JPanel bottom = new JPanel(new BorderLayout());
    bottom.setOpaque(false);
    bottom.add(workspaceGridAnalyticsBar, BorderLayout.CENTER);
    gapRocketAnalyzeButton.setVisible(false);
    gapRocketAnalyzeButton.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
    gapRocketAnalyzeButton.setFocusPainted(false);
    gapRocketAnalyzeButton.setToolTipText(TooltipStyler.text(GapRocketPanel.EMPTY_STATE_TEXT, 420));
    gapRocketAnalyzeButton.addActionListener(event -> openGapRocketAnalysisDialog());
    gapRocketPlaceOrdersButton.setVisible(false);
    gapRocketPlaceOrdersButton.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
    gapRocketPlaceOrdersButton.setFocusPainted(false);
    gapRocketPlaceOrdersButton.setToolTipText(
        TooltipStyler.text("Submit Alpaca limit buy orders for all Gap Rocket rows still pending order placement. Uses each row's base buy price and current Paper/Live mode."));
    gapRocketPlaceOrdersButton.addActionListener(event -> placeAllGapRocketPendingLimitBuys());
    gapRocketScheduleStatusLabel.setVisible(false);
    gapRocketScheduleStatusLabel.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
    gapRocketCancelScheduleButton.setVisible(false);
    gapRocketCancelScheduleButton.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
    gapRocketCancelScheduleButton.setFocusPainted(false);
    gapRocketCancelScheduleButton.setToolTipText(TooltipStyler.text(
        "Cancel the autonomous premarket gap-and-go schedule for this workspace.", 320));
    gapRocketCancelScheduleButton.addActionListener(event -> gapAndGoCoordinator.cancelSchedule());
    orbAnalyzeButton.setVisible(false);
    orbAnalyzeButton.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
    orbAnalyzeButton.setFocusPainted(false);
    orbAnalyzeButton.setToolTipText(TooltipStyler.text(OrbPanel.EMPTY_STATE_TEXT, 420));
    orbAnalyzeButton.addActionListener(event -> openOrbAnalysisDialog());
    orbScheduleStatusLabel.setVisible(false);
    orbScheduleStatusLabel.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
    orbCancelScheduleButton.setVisible(false);
    orbCancelScheduleButton.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
    orbCancelScheduleButton.setFocusPainted(false);
    orbCancelScheduleButton.setToolTipText(TooltipStyler.text(
        "Cancel the autonomous post-range ORB schedule for this workspace.", 320));
    orbCancelScheduleButton.addActionListener(event -> orbCoordinator.cancelSchedule());
    dipHunterAnalyzeButton.setVisible(false);
    dipHunterAnalyzeButton.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
    dipHunterAnalyzeButton.setFocusPainted(false);
    dipHunterAnalyzeButton.setToolTipText(TooltipStyler.text(DipHunterPanel.EMPTY_STATE_TEXT, 420));
    dipHunterAnalyzeButton.addActionListener(event -> openDipHunterAnalysisDialog());
    dipHunterScheduleStatusLabel.setVisible(false);
    dipHunterScheduleStatusLabel.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
    dipHunterCancelScheduleButton.setVisible(false);
    dipHunterCancelScheduleButton.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
    dipHunterCancelScheduleButton.setFocusPainted(false);
    dipHunterCancelScheduleButton.setToolTipText(TooltipStyler.text(
        "Cancel the autonomous Dip Hunter schedule for this workspace.", 320));
    dipHunterCancelScheduleButton.addActionListener(event -> dipHunterCoordinator.cancelSchedule());
    vwapAnalyzeButton.setVisible(false);
    vwapAnalyzeButton.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
    vwapAnalyzeButton.setFocusPainted(false);
    vwapAnalyzeButton.setToolTipText(TooltipStyler.text(VwapPanel.EMPTY_STATE_TEXT, 420));
    vwapAnalyzeButton.addActionListener(event -> openVwapAnalysisDialog());
    vwapScheduleStatusLabel.setVisible(false);
    vwapScheduleStatusLabel.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
    vwapCancelScheduleButton.setVisible(false);
    vwapCancelScheduleButton.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
    vwapCancelScheduleButton.setFocusPainted(false);
    vwapCancelScheduleButton.setToolTipText(TooltipStyler.text(
        "Cancel the autonomous VWAP Desk schedule for this workspace.", 320));
    vwapCancelScheduleButton.addActionListener(event -> vwapCoordinator.cancelSchedule());
    swingAnalyzeButton.setVisible(false);
    swingAnalyzeButton.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
    swingAnalyzeButton.setFocusPainted(false);
    swingAnalyzeButton.setToolTipText(TooltipStyler.text(SwingPanel.EMPTY_STATE_TEXT, 420));
    swingAnalyzeButton.addActionListener(event -> openSwingAnalysisDialog());
    swingScheduleStatusLabel.setVisible(false);
    swingScheduleStatusLabel.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
    swingCancelScheduleButton.setVisible(false);
    swingCancelScheduleButton.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
    swingCancelScheduleButton.setFocusPainted(false);
    swingCancelScheduleButton.setToolTipText(TooltipStyler.text(
        "Cancel the autonomous Swing Vault schedule for this workspace.", 320));
    swingCancelScheduleButton.addActionListener(event -> swingCoordinator.cancelSchedule());
    rangeRiderAnalyzeButton.setVisible(false);
    rangeRiderAnalyzeButton.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
    rangeRiderAnalyzeButton.setFocusPainted(false);
    rangeRiderAnalyzeButton.setToolTipText(TooltipStyler.text(RangeRiderPanel.EMPTY_STATE_TEXT, 420));
    rangeRiderAnalyzeButton.addActionListener(event -> openRangeRiderAnalysisDialog());
    rangeRiderScheduleStatusLabel.setVisible(false);
    rangeRiderScheduleStatusLabel.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
    rangeRiderCancelScheduleButton.setVisible(false);
    rangeRiderCancelScheduleButton.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
    rangeRiderCancelScheduleButton.setFocusPainted(false);
    rangeRiderCancelScheduleButton.setToolTipText(TooltipStyler.text(
        "Cancel the autonomous Range Rider schedule for this workspace.", 320));
    rangeRiderCancelScheduleButton.addActionListener(event -> rangeRiderCoordinator.cancelSchedule());
    profitShieldAnalyzeButton.setVisible(false);
    profitShieldAnalyzeButton.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
    profitShieldAnalyzeButton.setFocusPainted(false);
    profitShieldAnalyzeButton.setToolTipText(TooltipStyler.text(ProfitShieldPanel.EMPTY_STATE_TEXT, 420));
    profitShieldAnalyzeButton.addActionListener(event -> openProfitShieldAnalysisDialog());
    profitShieldScheduleStatusLabel.setVisible(false);
    profitShieldScheduleStatusLabel.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
    profitShieldCancelScheduleButton.setVisible(false);
    profitShieldCancelScheduleButton.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
    profitShieldCancelScheduleButton.setFocusPainted(false);
    profitShieldCancelScheduleButton.setToolTipText(TooltipStyler.text(
        "Cancel the autonomous Profit Shield schedule for this workspace.", 320));
    profitShieldCancelScheduleButton.addActionListener(event -> profitShieldCoordinator.cancelSchedule());
    earningsHunterAnalyzeButton.setVisible(false);
    earningsHunterAnalyzeButton.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
    earningsHunterAnalyzeButton.setFocusPainted(false);
    earningsHunterAnalyzeButton.setToolTipText(TooltipStyler.text(EarningsHunterPanel.EMPTY_STATE_TEXT, 420));
    earningsHunterAnalyzeButton.addActionListener(event -> openEarningsHunterAnalysisDialog());
    JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 2));
    actions.setOpaque(false);
    actions.add(gapRocketScheduleStatusLabel);
    actions.add(gapRocketCancelScheduleButton);
    actions.add(gapRocketPlaceOrdersButton);
    actions.add(gapRocketAnalyzeButton);
    actions.add(orbScheduleStatusLabel);
    actions.add(orbCancelScheduleButton);
    actions.add(orbAnalyzeButton);
    actions.add(dipHunterScheduleStatusLabel);
    actions.add(dipHunterCancelScheduleButton);
    actions.add(dipHunterAnalyzeButton);
    actions.add(vwapScheduleStatusLabel);
    actions.add(vwapCancelScheduleButton);
    actions.add(vwapAnalyzeButton);
    actions.add(swingScheduleStatusLabel);
    actions.add(swingCancelScheduleButton);
    actions.add(swingAnalyzeButton);
    actions.add(rangeRiderScheduleStatusLabel);
    actions.add(rangeRiderCancelScheduleButton);
    actions.add(rangeRiderAnalyzeButton);
    actions.add(profitShieldScheduleStatusLabel);
    actions.add(profitShieldCancelScheduleButton);
    actions.add(profitShieldAnalyzeButton);
    actions.add(earningsHunterAnalyzeButton);
    smartPicksWorkspaceView().actionControls().forEach(actions::add);
    bottom.add(actions, BorderLayout.EAST);
    return bottom;
  }

  private void refreshNewStrategyButtonPresentation() {
    boolean historySelected = strategyWorkspaceTabs != null && strategyWorkspaceTabs.isHistorySelected();
    addStrategyButton.setVisible(!historySelected);
    portfolioActionsButton.setVisible(!historySelected);
    String targetLabel = selectedWorkspaceId == null
        ? "All Stocks"
        : workspaceService.findById(selectedWorkspaceId).map(StrategyWorkspace::name).orElse("This Tab");
    addStrategyButton.setText("New Strategy in " + abbreviateWorkspaceButtonLabel(targetLabel));
    addStrategyButton.setToolTipText(TooltipStyler.text(
        "Add a new stock strategy (symbol, entry, stop, target, and automation) directly into "
            + targetLabel + " for " + selectedModeLabel() + " mode.",
        360
    ));
    String portfolioScope = selectedWorkspaceId == null
        ? "all strategy workspaces in " + selectedModeLabel() + " mode"
        : "only " + targetLabel + " in " + selectedModeLabel() + " mode";
    portfolioActionsButton.setToolTipText(TooltipStyler.text(
        "Portfolio actions apply to " + portfolioScope + ".",
        340
    ));
  }

  private String selectedWorkspaceForNewStrategy() {
    if (strategyWorkspaceTabs != null && strategyWorkspaceTabs.isHistorySelected()) {
      return null;
    }
    return selectedWorkspaceId;
  }

  private String duplicateSymbolAlertMessage(
      String symbol,
      String workspaceId,
      boolean allowDuplicateSymbols,
      boolean suggestEdit
  ) {
    String normalizedSymbol = symbol == null || symbol.isBlank() ? "This symbol" : symbol.trim().toUpperCase(Locale.ROOT);
    if (!allowDuplicateSymbols) {
      return normalizedSymbol + " already has an active or paused strategy in this mode."
          + (suggestEdit ? " Use Edit on the grid row." : "");
    }
    String workspaceLabel = workspaceId == null || workspaceId.isBlank()
        ? "All Stocks"
        : workspaceService.findById(workspaceId).map(StrategyWorkspace::name).orElse("this workspace");
    return normalizedSymbol + " already has an active or paused strategy in " + workspaceLabel + ".\n\n"
        + "This setting allows the same symbol only across different workspaces."
        + (suggestEdit ? " Move one strategy to another workspace or use Edit on the existing row." : "");
  }

  private String abbreviateWorkspaceButtonLabel(String value) {
    if (value == null || value.isBlank()) {
      return "This Tab";
    }
    String trimmed = value.trim();
    return trimmed.length() <= 24 ? trimmed : trimmed.substring(0, 21) + "...";
  }

  private JComponent wrapGridWithSearch(JPanel searchPanel, JComponent grid) {
    JPanel wrapper = new JPanel(new BorderLayout());
    wrapper.setOpaque(false);
    wrapper.add(searchPanel, BorderLayout.NORTH);
    wrapper.add(grid, BorderLayout.CENTER);
    return wrapper;
  }

  private void wireGridSearchFields() {
    attachSearchListener(currentStrategiesSearchField, this::applyCurrentStrategiesRowFilter);
    attachSearchListener(tradeHistorySearchField, this::applyTradeHistoryRowFilter);
    tradeHistoryGroupByButton.addActionListener(event -> showTradeHistoryGroupByMenu());
    profitableSellsFilterButton.addActionListener(event -> refreshFilledOrdersTableData());
    lossSellsFilterButton.addActionListener(event -> refreshFilledOrdersTableData());
    bothSellsFilterButton.addActionListener(event -> refreshFilledOrdersTableData());
  }

  private void showTradeHistoryGroupByMenu() {
    JPopupMenu menu = new JPopupMenu();
    ButtonGroup group = new ButtonGroup();
    JRadioButtonMenuItem bySymbol = new JRadioButtonMenuItem("By Symbol", tradeHistoryGroupBy == TradeHistoryGroupBy.SYMBOL);
    JRadioButtonMenuItem byDate = new JRadioButtonMenuItem("By Date", tradeHistoryGroupBy == TradeHistoryGroupBy.DATE);
    group.add(bySymbol);
    group.add(byDate);
    bySymbol.addActionListener(event -> updateTradeHistoryGroupBy(TradeHistoryGroupBy.SYMBOL));
    byDate.addActionListener(event -> updateTradeHistoryGroupBy(TradeHistoryGroupBy.DATE));
    menu.add(bySymbol);
    menu.add(byDate);
    menu.show(tradeHistoryGroupByButton, 0, tradeHistoryGroupByButton.getHeight());
  }

  private void updateTradeHistoryGroupBy(TradeHistoryGroupBy groupBy) {
    if (groupBy == null || groupBy == tradeHistoryGroupBy) {
      return;
    }
    tradeHistoryGroupBy = groupBy;
    tradeHistoryGroupByButton.setText(groupBy == TradeHistoryGroupBy.DATE ? "Group By Menu: Date" : "Group By Menu: Symbol");
    configureTradeHistorySorting();
    refreshFilledOrdersTableData();
  }

  private void attachSearchListener(JTextField field, Runnable onChange) {
    field.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent e) {
        onChange.run();
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        onChange.run();
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
        onChange.run();
      }
    });
  }

  private void applyCurrentStrategiesRowFilter() {
    if (strategySorter == null) {
      return;
    }
    final String query = normalizeGridSearchQuery(currentStrategiesSearchField.getText());
    strategySorter.setRowFilter(new RowFilter<>() {
      @Override
      public boolean include(Entry<? extends StrategyGridTableModel, ? extends Integer> entry) {
        int modelRow = entry.getIdentifier();
        if (modelRow < 0 || modelRow >= strategies.size()) {
          return false;
        }
        ManagedStrategy managedStrategy = strategies.get(modelRow);
        if (!includeInCurrentStrategiesTab(managedStrategy)) {
          return false;
        }
        // Workspace tab: show only this workspace's strategies. All Stocks (null) shows all.
        if (selectedWorkspaceId != null
            && !selectedWorkspaceId.equals(managedStrategy.strategy.workspaceId())) {
          return false;
        }
        return query.isBlank() || matchesStockSymbol(managedStrategy.strategy.symbol(), query);
      }
    });
    refreshStrategyWorkspaceEmptyState();
    applySelectedCapturePortfolioState();
  }

  private void applyTradeHistoryRowFilter() {
    if (filledOrdersSorter == null) {
      return;
    }
    final String query = normalizeGridSearchQuery(tradeHistorySearchField.getText());
    final java.util.Set<String> matchedGroupKeys = new java.util.HashSet<>();
    if (!query.isBlank()) {
      for (HistoryTablePresenter.HistoryRow row : filledOrderRows) {
        if (row.style() == HistoryTablePresenter.HistoryRowStyle.SUBTOTAL) {
          continue;
        }
        if (matchesStockSymbol(row.symbol(), query)) {
          matchedGroupKeys.add(normalizeGridSearchQuery(row.groupKey()));
        }
      }
    }
    filledOrdersSorter.setRowFilter(new RowFilter<>() {
      @Override
      public boolean include(Entry<? extends HistoryGridTableModel, ? extends Integer> entry) {
        int modelRow = entry.getIdentifier();
        if (modelRow < 0 || modelRow >= filledOrderRows.size()) {
          return false;
        }
        HistoryTablePresenter.HistoryRow row = filledOrderRows.get(modelRow);
        if (query.isBlank()) {
          return true;
        }
        if (row.style() == HistoryTablePresenter.HistoryRowStyle.SUBTOTAL) {
          String normalizedGroupKey = normalizeGridSearchQuery(row.groupKey());
          return !normalizedGroupKey.equals("total") && matchedGroupKeys.contains(normalizedGroupKey);
        }
        return matchesStockSymbol(row.symbol(), query);
      }
    });
  }

  private TradeHistorySellFilter selectedTradeHistorySellFilter() {
    if (profitableSellsFilterButton.isSelected()) {
      return TradeHistorySellFilter.PROFITABLE_SELLS;
    }
    if (lossSellsFilterButton.isSelected()) {
      return TradeHistorySellFilter.LOSS_SELLS;
    }
    return TradeHistorySellFilter.BOTH;
  }

  private boolean matchesStockSymbol(String symbol, String normalizedQuery) {
    if (normalizedQuery == null || normalizedQuery.isBlank()) {
      return true;
    }
    if (symbol == null || symbol.isBlank()) {
      return false;
    }
    return symbol.toLowerCase(Locale.ROOT).contains(normalizedQuery);
  }

  private String normalizeGridSearchQuery(String text) {
    return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
  }

  private void refreshGridSearchVisibility() {
    boolean showCurrentStrategiesSearch = true;
    boolean hasTradeHistoryRows = tradeHistoryStockCount() > 0;

    if (!showCurrentStrategiesSearch && !currentStrategiesSearchField.getText().isBlank()) {
      currentStrategiesSearchField.setText("");
    }

    currentStrategiesSearchPanel.setVisible(showCurrentStrategiesSearch);
    tradeHistorySearchPanel.setVisible(hasTradeHistoryRows);
  }

  private long currentStrategiesStockCountInSelectedWorkspace() {
    return currentStrategiesStockCountInWorkspace(selectedWorkspaceId);
  }

  private long currentStrategiesStockCountInWorkspace(String workspaceId) {
    return strategies.stream()
        .filter(this::includeInCurrentStrategiesTab)
        .filter(entry -> workspaceId == null || workspaceId.equals(entry.strategy.workspaceId()))
        .count();
  }

  private long tradeHistoryStockCount() {
    return filledOrderRows.stream()
        .filter(row -> row.style() != HistoryTablePresenter.HistoryRowStyle.SUBTOTAL)
        .map(HistoryTablePresenter.HistoryRow::symbol)
        .filter(symbol -> symbol != null && !symbol.isBlank())
        .map(symbol -> symbol.toUpperCase(Locale.ROOT))
        .distinct()
        .count();
  }

  private void updateStatusBar() {
    long totalCurrentStrategies = strategies.stream().filter(this::includeInCurrentStrategiesTab).count();
    long running = strategies.stream()
        .filter(this::includeInCurrentStrategiesTab)
        .filter(s -> s.strategy.status() == StrategyStatus.ACTIVE)
        .count();
    long inactive = Math.max(0L, totalCurrentStrategies - running);
    MarketStatusPresenter.MarketStatusViewModel marketStatusViewModel = currentMarketStatusViewModel();
    String cpuText = formatCpuUsageText();
    String memoryText = formatMemoryUsageText();
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
            tradeHistoryStockCount(),
            connectionRetryPending,
            connectionOk,
            marketStatusViewModel.label(),
            marketStatusViewModel.tooltip(),
            marketStatusViewModel.openForUi(),
            availableFundsText,
            cpuText,
            memoryText
        )
    );
    SwingUtilities.invokeLater(() -> {
      refreshCurrentStrategiesHeading();
      statusStrategyCount.setText(statusBarViewModel.strategyCountText());
      pollingSummary.setText(statusBarViewModel.pollingText());
      pollingSummary.setForeground(statusToneColor(statusBarViewModel.pollingTone()));
      marketStatus.setText(statusBarViewModel.marketText());
      marketStatus.setForeground(statusToneColor(statusBarViewModel.marketTone()));
      marketStatus.setToolTipText(TooltipStyler.text(statusBarViewModel.marketTooltip()));
      availableFundsStatus.setText(statusBarViewModel.availableFundsText());
      refreshPortfolioAnalytics();
      cpuUsageStatus.setText(statusBarViewModel.cpuText());
      memoryUsageStatus.setText(statusBarViewModel.memoryText());
      statusBar.setText(statusBarViewModel.brokerText());
      statusBar.setForeground(statusToneColor(statusBarViewModel.brokerTone()));
      bottomStatusBars.updateCompactSummaryAndDetails(statusBarViewModel, availableFundsText);
      smartPicksButton.setEnabled(settingsDialog.hasRequiredSettings());
      refreshTradeHistoryHeading();
      refreshGridSearchVisibility();
      bottomStatusBars.updateLayoutMode();
      refreshAvailableFundsAsync();
    });
  }

  private void refreshAvailableFundsAsync() {
    ApplicationMode requestMode = selectedApplicationMode();
    if (!connectionOk || connectionRetryPending) {
      availableFundsText = availableFundsStatusState.clear(requestMode);
      applyAvailableFundsTextForMode(requestMode);
      return;
    }
    long now = System.currentTimeMillis();
    if (!availableFundsStatusState.shouldFetch(requestMode, now, AVAILABLE_FUNDS_REFRESH_INTERVAL_MILLIS)) {
      return;
    }
    HttpAlpacaClient client = alpacaClientForMode(requestMode);
    if (client == null) {
      availableFundsText = availableFundsStatusState.clear(requestMode);
      applyAvailableFundsTextForMode(requestMode);
      return;
    }
    if (!availableFundsFetchInFlight.compareAndSet(false, true)) {
      return;
    }
    availableFundsStatusState.markFetchStarted(requestMode, now);
    uiPollingExecutor.execute(() -> {
      String updatedText = null;
      try {
        updatedText = availableFundsStatusState.update(requestMode, client.getAvailableFunds());
      } finally {
        availableFundsFetchInFlight.set(false);
      }
      String textForUi = updatedText;
      SwingUtilities.invokeLater(() -> {
        if (selectedApplicationMode() == requestMode) {
          availableFundsText = textForUi;
          availableFundsStatus.setText(textForUi);
        }
        updateStatusBar();
      });
    });
  }

  private void applyAvailableFundsTextForMode(ApplicationMode mode) {
    availableFundsText = availableFundsStatusState.textFor(mode);
    availableFundsStatus.setText(availableFundsText);
  }

  private void refreshCurrentStrategiesHeading() {
    if (strategyTabs.getTabCount() == 0) {
      return;
    }
    if (strategyWorkspaceTabs == null) {
      strategyTabs.setTitleAt(0, currentStrategiesHeadingText());
      return;
    }
    strategyWorkspaceTabs.refreshStrategyTitles();
  }

  private void refreshTradeHistoryHeading() {
    if (strategyTabs.getTabCount() < 2) {
      return;
    }
    // Trade History is always the last tab (workspace tabs are inserted before it).
    strategyTabs.setTitleAt(strategyTabs.getTabCount() - 1, tradeHistoryHeadingText());
  }

  // Invoked by the workspace-tabs coordinator when the selected tab changes (null = All Stocks).
  private void onWorkspaceTabSelected(String workspaceId) {
    boolean workspaceChanged = !Objects.equals(selectedWorkspaceId, workspaceId);
    selectedWorkspaceId = workspaceId;
    refreshNewStrategyButtonPresentation();
    if (workspaceChanged && !currentStrategiesSearchField.getText().isBlank()) {
      currentStrategiesSearchField.setText("");
    }
    applyCurrentStrategiesRowFilter();
    refreshCurrentStrategiesHeading();
    refreshWorkspaceSummary();
    updateGapRocketScheduleBadge(gapAndGoCoordinator.currentSchedule());
    refreshStrategyWorkspaceEmptyState();
  }

  private void refreshStrategyWorkspaceEmptyState() {
    if (strategiesGridCardLayout == null || strategiesGridCardPanel == null) {
      return;
    }
    boolean selectedGapRocket = isSelectedGapRocketWorkspace();
    boolean selectedOrb = isSelectedOrbWorkspace();
    boolean selectedDipHunter = isSelectedDipHunterWorkspace();
    boolean selectedVwap = isSelectedVwapWorkspace();
    boolean selectedSwing = isSelectedSwingWorkspace();
    boolean selectedRangeRider = isSelectedRangeRiderWorkspace();
    boolean selectedProfitShield = isSelectedProfitShieldWorkspace();
    boolean selectedEarningsHunter = isSelectedEarningsHunterWorkspace();
    boolean showGapRocketEmptyState = selectedGapRocket && selectedWorkspaceStrategyCount() == 0;
    boolean showOrbEmptyState = selectedOrb && selectedWorkspaceStrategyCount() == 0;
    boolean showDipHunterEmptyState = selectedDipHunter && selectedWorkspaceStrategyCount() == 0;
    boolean showVwapEmptyState = selectedVwap && selectedWorkspaceStrategyCount() == 0;
    boolean showSwingEmptyState = selectedSwing && selectedWorkspaceStrategyCount() == 0;
    boolean showRangeRiderEmptyState = selectedRangeRider && selectedWorkspaceStrategyCount() == 0;
    boolean showProfitShieldEmptyState = selectedProfitShield && selectedWorkspaceStrategyCount() == 0;
    boolean showEarningsHunterEmptyState = selectedEarningsHunter && selectedWorkspaceStrategyCount() == 0;
    Optional<SmartPicksWorkspaceKind> smartPicksKind = selectedSmartPicksKind();
    boolean showSmartPicksEmptyState = smartPicksKind.isPresent() && selectedWorkspaceStrategyCount() == 0;
    smartPicksWorkspaceView().refresh(smartPicksKind, showSmartPicksEmptyState,
        smartPicksWorkspaceCoordinator == null ? Optional.empty() : smartPicksWorkspaceCoordinator.schedule(selectedWorkspaceId));
    gapRocketAnalyzeButton.setVisible(selectedGapRocket && !showGapRocketEmptyState);
    gapRocketPlaceOrdersButton.setVisible(selectedGapRocket && !showGapRocketEmptyState);
    orbAnalyzeButton.setVisible(selectedOrb && !showOrbEmptyState);
    dipHunterAnalyzeButton.setVisible(selectedDipHunter && !showDipHunterEmptyState);
    vwapAnalyzeButton.setVisible(selectedVwap && !showVwapEmptyState);
    swingAnalyzeButton.setVisible(selectedSwing && !showSwingEmptyState);
    rangeRiderAnalyzeButton.setVisible(selectedRangeRider && !showRangeRiderEmptyState);
    profitShieldAnalyzeButton.setVisible(selectedProfitShield && !showProfitShieldEmptyState);
    earningsHunterAnalyzeButton.setVisible(selectedEarningsHunter && !showEarningsHunterEmptyState);
    GapAndGoSchedule currentGapSchedule = gapAndGoCoordinator == null ? null : gapAndGoCoordinator.currentSchedule();
    boolean gapScheduled = currentGapSchedule != null && currentGapSchedule.enabled();
    gapRocketScheduleStatusLabel.setVisible(selectedGapRocket && gapScheduled);
    gapRocketCancelScheduleButton.setVisible(selectedGapRocket && gapScheduled);
    OrbSchedule currentOrbSchedule = orbCoordinator == null ? null : orbCoordinator.currentSchedule();
    boolean orbScheduled = currentOrbSchedule != null && currentOrbSchedule.enabled();
    orbScheduleStatusLabel.setVisible(selectedOrb && orbScheduled);
    orbCancelScheduleButton.setVisible(selectedOrb && orbScheduled);
    DipHunterSchedule currentDipSchedule = dipHunterCoordinator == null ? null : dipHunterCoordinator.currentSchedule();
    boolean dipScheduled = currentDipSchedule != null && currentDipSchedule.enabled();
    dipHunterScheduleStatusLabel.setVisible(selectedDipHunter && dipScheduled);
    dipHunterCancelScheduleButton.setVisible(selectedDipHunter && dipScheduled);
    VwapSchedule currentVwapSchedule = vwapCoordinator == null ? null : vwapCoordinator.currentSchedule();
    boolean vwapScheduled = currentVwapSchedule != null && currentVwapSchedule.enabled();
    vwapScheduleStatusLabel.setVisible(selectedVwap && vwapScheduled);
    vwapCancelScheduleButton.setVisible(selectedVwap && vwapScheduled);
    SwingSchedule currentSwingSchedule = swingCoordinator == null ? null : swingCoordinator.currentSchedule();
    boolean swingScheduled = currentSwingSchedule != null && currentSwingSchedule.enabled();
    swingScheduleStatusLabel.setVisible(selectedSwing && swingScheduled);
    swingCancelScheduleButton.setVisible(selectedSwing && swingScheduled);
    RangeRiderSchedule currentRangeRiderSchedule = rangeRiderCoordinator == null ? null : rangeRiderCoordinator.currentSchedule();
    boolean rangeRiderScheduled = currentRangeRiderSchedule != null && currentRangeRiderSchedule.enabled();
    rangeRiderScheduleStatusLabel.setVisible(selectedRangeRider && rangeRiderScheduled);
    rangeRiderCancelScheduleButton.setVisible(selectedRangeRider && rangeRiderScheduled);
    ProfitShieldSchedule currentProfitShieldSchedule = profitShieldCoordinator == null ? null : profitShieldCoordinator.currentSchedule();
    boolean profitShieldScheduled = currentProfitShieldSchedule != null && currentProfitShieldSchedule.enabled();
    profitShieldScheduleStatusLabel.setVisible(selectedProfitShield && profitShieldScheduled);
    profitShieldCancelScheduleButton.setVisible(selectedProfitShield && profitShieldScheduled);
    if (showGapRocketEmptyState) {
      refreshScanHistoryPanel(gapRocketScanHistoryPanel);
      strategiesGridCardLayout.show(strategiesGridCardPanel, GAP_ROCKET_EMPTY_CARD);
    } else if (showOrbEmptyState) {
      refreshScanHistoryPanel(orbScanHistoryPanel);
      strategiesGridCardLayout.show(strategiesGridCardPanel, ORB_EMPTY_CARD);
    } else if (showDipHunterEmptyState) {
      refreshScanHistoryPanel(dipHunterScanHistoryPanel);
      strategiesGridCardLayout.show(strategiesGridCardPanel, DIP_HUNTER_EMPTY_CARD);
    } else if (showVwapEmptyState) {
      refreshScanHistoryPanel(vwapScanHistoryPanel);
      strategiesGridCardLayout.show(strategiesGridCardPanel, VWAP_EMPTY_CARD);
    } else if (showSwingEmptyState) {
      refreshScanHistoryPanel(swingScanHistoryPanel);
      strategiesGridCardLayout.show(strategiesGridCardPanel, SWING_EMPTY_CARD);
    } else if (showRangeRiderEmptyState) {
      refreshScanHistoryPanel(rangeRiderScanHistoryPanel);
      strategiesGridCardLayout.show(strategiesGridCardPanel, RANGE_RIDER_EMPTY_CARD);
    } else if (showProfitShieldEmptyState) {
      refreshScanHistoryPanel(profitShieldScanHistoryPanel);
      strategiesGridCardLayout.show(strategiesGridCardPanel, PROFIT_SHIELD_EMPTY_CARD);
    } else if (showEarningsHunterEmptyState) {
      refreshScanHistoryPanel(earningsHunterScanHistoryPanel);
      strategiesGridCardLayout.show(strategiesGridCardPanel, EARNINGS_HUNTER_EMPTY_CARD);
    } else if (showSmartPicksEmptyState) {
      refreshScanHistoryPanel(smartPicksScanHistoryPanel);
      strategiesGridCardLayout.show(strategiesGridCardPanel, SMART_PICKS_EMPTY_CARD);
    } else {
      strategiesGridCardLayout.show(strategiesGridCardPanel, STRATEGIES_GRID_CARD);
    }
  }

  /**
   * Load the selected workspace's recent scan history into its empty-state table (EDT-safe cache read).
   */
  private void refreshScanHistoryPanel(ScanHistoryTablePanel panel) {
    if (panel == null) {
      return;
    }
    List<ScanHistoryEntry> entries = selectedWorkspaceId == null
        ? List.of()
        : scanHistoryRepository.findRecentByWorkspace(selectedWorkspaceId, ScanHistoryTablePanel.MAX_ROWS);
    panel.setEntries(entries);
  }

  private boolean isSelectedGapRocketWorkspace() {
    if (selectedWorkspaceId == null) {
      return false;
    }
    return workspaceService.findById(selectedWorkspaceId)
        .map(StrategyWorkspace::code)
        .map(GAP_ROCKET_WORKSPACE_CODE::equalsIgnoreCase)
        .orElse(false);
  }

  private boolean isSelectedOrbWorkspace() {
    if (selectedWorkspaceId == null) {
      return false;
    }
    return workspaceService.findById(selectedWorkspaceId)
        .map(StrategyWorkspace::code)
        .map(ORB_WORKSPACE_CODE::equalsIgnoreCase)
        .orElse(false);
  }

  private boolean isSelectedDipHunterWorkspace() {
    if (selectedWorkspaceId == null) {
      return false;
    }
    return workspaceService.findById(selectedWorkspaceId)
        .map(StrategyWorkspace::code)
        .map(DIP_HUNTER_WORKSPACE_CODE::equalsIgnoreCase)
        .orElse(false);
  }

  private boolean isSelectedVwapWorkspace() {
    if (selectedWorkspaceId == null) {
      return false;
    }
    return workspaceService.findById(selectedWorkspaceId)
        .map(StrategyWorkspace::code)
        .map(VWAP_WORKSPACE_CODE::equalsIgnoreCase)
        .orElse(false);
  }

  /**
   * Opens the read-only AI analyst. One dialog at a time: a second click brings the open one forward rather than starting a parallel run against the same key.
   */
  private void openAgentAnalyst() {
    String action = "Ask the AI Analyst";
    com.neuralarc.model.AgentSettings settings = appSettingsService.loadAgentSettings();
    if (!settings.ready()) {
      userActionLog.failed(action, "The AI analyst is off, or has no API key.");
      JOptionPane.showMessageDialog(this,
          "Add an Anthropic API key in Settings and switch on the AI analyst first.",
          "AI Analyst Not Configured", JOptionPane.INFORMATION_MESSAGE);
      return;
    }
    if (agentAnalystDialog != null && agentAnalystDialog.isShowing()) {
      agentAnalystDialog.toFront();
      return;
    }
    userActionLog.started(action);
    agentAnalystDialog = new AgentAnalystDialog(this, new AgentAnalystRunner(agentAnalystServices()));
    agentAnalystDialog.setVisible(true);
  }

  /**
   * What the analyst may read. Null services simply become tools the model is never offered.
   */
  private AgentAnalystRunner.Services agentAnalystServices() {
    boolean connected = connectionOk && !runtimeApiKey.isBlank();
    return new AgentAnalystRunner.Services() {
      @Override
      public com.neuralarc.api.TradingApi tradingApi() {
        return tradingApi;
      }

      @Override
      public com.neuralarc.api.AlpacaMarketDataApi marketDataApi() {
        return connected ? new HttpAlpacaMarketDataApi(runtimeApiKey, runtimeApiSecret) : null;
      }

      @Override
      public com.neuralarc.service.AlpacaNewsClient newsClient() {
        return connected
            ? new com.neuralarc.service.HttpAlpacaNewsClient(runtimeApiKey, runtimeApiSecret) : null;
      }

      @Override
      public com.neuralarc.service.MarketHoursService marketHours() {
        return marketHoursService;
      }

      @Override
      public com.neuralarc.agent.tools.PositionSnapshots positions() {
        return TradingFrame.this::agentPositionViews;
      }

      @Override
      public com.neuralarc.db.SqliteAgentToolCallRepository auditRepository() {
        return agentToolCallRepository;
      }

      @Override
      public com.neuralarc.model.AgentSettings settings() {
        return appSettingsService.loadAgentSettings();
      }
    };
  }

  /**
   * The open book as the analyst sees it, copied from the snapshots already on screen. Deliberately snapshot-only: asking a question must never set off a broker sweep.
   */
  private java.util.List<com.neuralarc.agent.tools.PositionSnapshots.PositionView> agentPositionViews() {
    java.util.List<com.neuralarc.agent.tools.PositionSnapshots.PositionView> views = new java.util.ArrayList<>();
    for (ManagedStrategy entry : List.copyOf(strategies)) {
      Position position = entry.cachedPosition();
      if (position == null || position.getTotalShares() <= 0) {
        continue;
      }
      String workspace = workspaceNameOrNull(entry.strategy.workspaceId());
      views.add(new com.neuralarc.agent.tools.PositionSnapshots.PositionView(
          entry.strategy.symbol(),
          entry.strategy.mode(),
          workspace == null ? "All Stocks" : workspace,
          String.valueOf(entry.strategy.status()),
          position.getTotalShares(),
          position.getAverageCost(),
          position.getLastPrice(),
          position.unrealizedPnl()));
    }
    return views;
  }

  private String workspaceNameOrNull(String workspaceId) {
    return workspaceService == null || workspaceId == null
        ? null
        : workspaceService.findById(workspaceId).map(StrategyWorkspace::name).orElse(null);
  }

  /**
   * The Smart Picks kind of the selected workspace, if it is one of the Smart Picks workspaces.
   */
  private Optional<SmartPicksWorkspaceKind> selectedSmartPicksKind() {
    if (selectedWorkspaceId == null) {
      return Optional.empty();
    }
    return workspaceService.findById(selectedWorkspaceId)
        .map(StrategyWorkspace::code)
        .flatMap(SmartPicksWorkspaceKind::fromCode);
  }

  private SmartPicksWorkspaceView smartPicksWorkspaceView() {
    if (smartPicksWorkspaceView == null) {
      smartPicksWorkspaceView = new SmartPicksWorkspaceView(BASE_FONT.deriveFont(Font.BOLD, 11f),
          this::analyzeSelectedSmartPicksWorkspace,
          this::scheduleSelectedSmartPicksWorkspace,
          this::cancelSelectedSmartPicksSchedule);
    }
    return smartPicksWorkspaceView;
  }

  /**
   * Opens the Smart Picks review for the selected workspace's strategy; placed picks land in this workspace.
   */
  private void analyzeSelectedSmartPicksWorkspace() {
    selectedSmartPicksKind().ifPresent(kind -> openSmartPicksTrendingStocksDialog(kind.universe()));
  }

  private void scheduleSelectedSmartPicksWorkspace() {
    Optional<SmartPicksWorkspaceKind> kind = selectedSmartPicksKind();
    if (kind.isEmpty()) {
      return;
    }
    SmartPicksSchedule existing = smartPicksWorkspaceCoordinator.schedule(selectedWorkspaceId).orElse(null);
    new SmartPicksScheduleDialog(this, kind.get(), selectedWorkspaceId, selectedViewMode, existing)
        .showDialog()
        .ifPresent(schedule -> {
          smartPicksWorkspaceCoordinator.save(schedule);
          userActionLog.completed("Schedule " + kind.get().title(), schedule.summary());
        });
  }

  private void cancelSelectedSmartPicksSchedule() {
    int choice = JOptionPane.showConfirmDialog(this, "Cancel this workspace's autonomous Smart Picks scan?",
        "Cancel Schedule", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
    if (choice == JOptionPane.YES_OPTION) {
      smartPicksWorkspaceCoordinator.cancelScheduleForWorkspace(selectedWorkspaceId);
    }
  }

  private boolean isSelectedSwingWorkspace() {
    if (selectedWorkspaceId == null) {
      return false;
    }
    return workspaceService.findById(selectedWorkspaceId)
        .map(StrategyWorkspace::code)
        .map(SWING_WORKSPACE_CODE::equalsIgnoreCase)
        .orElse(false);
  }

  private boolean isSelectedRangeRiderWorkspace() {
    if (selectedWorkspaceId == null) {
      return false;
    }
    return workspaceService.findById(selectedWorkspaceId)
        .map(StrategyWorkspace::code)
        .map(RANGE_RIDER_WORKSPACE_CODE::equalsIgnoreCase)
        .orElse(false);
  }

  private boolean isSelectedProfitShieldWorkspace() {
    if (selectedWorkspaceId == null) {
      return false;
    }
    return workspaceService.findById(selectedWorkspaceId)
        .map(StrategyWorkspace::code)
        .map(PROFIT_SHIELD_WORKSPACE_CODE::equalsIgnoreCase)
        .orElse(false);
  }

  private boolean isSelectedEarningsHunterWorkspace() {
    if (selectedWorkspaceId == null) {
      return false;
    }
    return workspaceService.findById(selectedWorkspaceId)
        .map(StrategyWorkspace::code)
        .map(EARNINGS_HUNTER_WORKSPACE_CODE::equalsIgnoreCase)
        .orElse(false);
  }

  private long selectedWorkspaceStrategyCount() {
    if (selectedWorkspaceId == null) {
      return 0;
    }
    return currentStrategiesStockCountInSelectedWorkspace();
  }


  private void placeAllGapRocketPendingLimitBuys() {
    if (selectedWorkspaceId == null || !isSelectedGapRocketWorkspace()) {
      return;
    }
    StrategyService service = strategyServiceForMode(selectedViewMode);
    if (service == null) {
      JOptionPane.showMessageDialog(this,
          selectedModeLabel() + " Alpaca credentials are required before placing Gap Rocket limit buys.",
          selectedModeLabel() + " Credentials Required",
          JOptionPane.WARNING_MESSAGE);
      return;
    }
    List<Strategy> pending = strategyRepository.findAll().stream()
        .filter(strategy -> strategy.mode() == selectedViewMode)
        .filter(strategy -> selectedWorkspaceId.equals(strategy.workspaceId()))
        .filter(this::isGapRocketPendingOrderPlacement)
        .toList();
    if (pending.isEmpty()) {
      JOptionPane.showMessageDialog(this,
          "No pending Gap Rocket limit buys to place.",
          "Gap Rocket Orders",
          JOptionPane.INFORMATION_MESSAGE);
      return;
    }
    placePendingLimitBuysInBatches(pending, service);
  }

  /**
   * Submits pending limit buys a few symbols at a time on a background thread. A workspace can hold hundreds of rows, and placing them in one uninterrupted sweep on the EDT froze
   * the window until the last order came back.
   */
  private void placePendingLimitBuysInBatches(List<Strategy> pending, StrategyService service) {
    gapRocketPlaceOrdersButton.setEnabled(false);
    log("[Gap Rocket] Placing " + pending.size() + " pending limit buy order(s) in batches of "
        + BulkPlacementRunner.DEFAULT_BATCH_SIZE + ".");
    new SwingWorker<BulkPlacementRunner.Progress, BulkPlacementRunner.Progress>() {
      @Override
      protected BulkPlacementRunner.Progress doInBackground() {
        return new BulkPlacementRunner().run(
            pending,
            strategy -> submitGapRocketPendingLimitBuy(service, strategy),
            this::publish
        );
      }

      @Override
      protected void process(List<BulkPlacementRunner.Progress> updates) {
        BulkPlacementRunner.Progress latest = updates.getLast();
        setStatus("Placing Gap Rocket limit buys: " + latest.completed() + " of " + latest.total()
            + " submitted...", STATUS_WARN);
        syncStrategiesFromRepository();
        refreshStrategyTableData();
        updateStatusBar();
      }

      @Override
      protected void done() {
        gapRocketPlaceOrdersButton.setEnabled(true);
        BulkPlacementRunner.Progress progress;
        try {
          progress = get();
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
          return;
        } catch (ExecutionException ex) {
          log("[Gap Rocket] Batch placement failed: " + ex.getCause().getMessage());
          JOptionPane.showMessageDialog(TradingFrame.this,
              "Failed to place Gap Rocket limit buys: " + ex.getCause().getMessage(),
              "Gap Rocket Orders",
              JOptionPane.ERROR_MESSAGE);
          return;
        }
        syncStrategiesFromRepository();
        refreshStrategyTableData();
        applyCurrentStrategiesRowFilter();
        refreshWorkspaceSummary();
        updateStatusBar();
        setStatus("Gap Rocket limit buys placed: " + progress.placed() + " of " + progress.total() + ".",
            progress.failed() > 0 ? STATUS_WARN : STATUS_OK);
        JOptionPane.showMessageDialog(TradingFrame.this,
            "Submitted " + progress.placed() + " Gap Rocket limit buy order"
                + (progress.placed() == 1 ? "" : "s") + "."
                + (progress.failed() > 0
                ? "\nSkipped " + progress.failed() + " row" + (progress.failed() == 1 ? "" : "s")
                  + " due to validation or broker errors." : "")
                + (progress.remaining() > 0
                ? "\nStopped with " + progress.remaining() + " row(s) not attempted." : ""),
            "Gap Rocket Orders",
            progress.failed() > 0 ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
      }
    }.execute();
  }

  /**
   * Places one pending Gap Rocket base limit buy. Runs on the batch worker thread.
   */
  private boolean submitGapRocketPendingLimitBuy(StrategyService service, Strategy strategy) {
    StrategyService.StrategyCreationResult result = service.createAndActivate(strategy);
    if (result.success()) {
      log("[Gap Rocket] Submitted base limit buy for " + strategy.symbol()
          + " @ $" + strategy.baseBuyLimitPrice().toPlainString()
          + ", clientOrderId=" + result.clientOrderId());
      return true;
    }
    log("[Gap Rocket] Failed to submit base limit buy for " + strategy.symbol() + ": " + result.error());
    return false;
  }

  private StrategyService.StrategyCreationResult placePendingBaseBuy(Strategy strategy) {
    if (strategy == null) {
      return StrategyService.StrategyCreationResult.failed("strategy is not available");
    }
    StrategyService service = strategyServiceForMode(strategy.mode());
    if (service == null) {
      return StrategyService.StrategyCreationResult.failed("broker client is not configured for "
          + strategy.mode().name() + " mode");
    }
    Strategy pending = strategyRepository.findById(strategy.id()).orElse(strategy);
    if (!PendingBaseBuyPlacementSupport.isPendingBaseBuyPlacement(pending)) {
      return StrategyService.StrategyCreationResult.failed("strategy is not pending base-buy placement");
    }
    BigDecimal originalLimit = pending.baseBuyLimitPrice();
    BigDecimal todayLow = loadTodayLow(pending);
    BigDecimal adjustedLimit = PendingBaseBuyPlacementSupport.adjustedBaseBuyLimit(originalLimit, todayLow);
    log("[" + pending.symbol() + "] Pending base-buy placement check: base=$"
        + Monetary.round(originalLimit).toPlainString()
        + ", todayLow=" + (todayLow.signum() > 0 ? "$" + Monetary.round(todayLow).toPlainString() : "unavailable")
        + ", submitLimit=$" + adjustedLimit.toPlainString() + ".");
    if (adjustedLimit.compareTo(Monetary.round(originalLimit)) != 0) {
      pending.setBaseBuyLimitPrice(adjustedLimit);
      pending.setLastEvent("Base buy adjusted before pending placement from $"
          + Monetary.round(originalLimit).toPlainString()
          + " to $" + adjustedLimit.toPlainString()
          + " because today's low is $" + Monetary.round(todayLow).toPlainString() + ".");
      strategyRepository.save(pending);
      log("[" + pending.symbol() + "] Pending base buy lowered from $"
          + Monetary.round(originalLimit).toPlainString()
          + " to $" + adjustedLimit.toPlainString()
          + " using today's low $" + Monetary.round(todayLow).toPlainString() + ".");
    }
    return service.createAndActivate(pending);
  }

  private StrategyService.StrategyCreationResult readjustLosingPendingBaseBuy(ManagedStrategy entry) {
    if (entry == null || entry.strategy == null) {
      return StrategyService.StrategyCreationResult.failed("strategy is not available");
    }
    Strategy strategy = entry.strategy;
    Strategy pending = strategyRepository.findById(strategy.id()).orElse(strategy);
    if (!PendingBaseBuyPlacementSupport.isPendingBaseBuyPlacement(pending)) {
      return StrategyService.StrategyCreationResult.failed("strategy is not pending base-buy placement");
    }
    if (!isAmberPendingBaseBuy(entry)) {
      return StrategyService.StrategyCreationResult.failed("strategy is not an amber pending base-buy row");
    }
    BigDecimal originalLimit = pending.baseBuyLimitPrice();
    BigDecimal todayLow = loadTodayLow(pending);
    BigDecimal adjustedLimit = PendingBaseBuyPlacementSupport.adjustedBaseBuyLimit(originalLimit, todayLow);
    BigDecimal cachedCurrentPrice = entry.cachedPosition() == null ? BigDecimal.ZERO : entry.cachedPosition().getLastPrice();
    if (cachedCurrentPrice != null && cachedCurrentPrice.signum() > 0
        && adjustedLimit.compareTo(cachedCurrentPrice) >= 0) {
      adjustedLimit = Monetary.round(cachedCurrentPrice.multiply(new BigDecimal("0.99")));
    }
    if (adjustedLimit.compareTo(BigDecimal.ZERO) <= 0) {
      return StrategyService.StrategyCreationResult.failed("unable to calculate a valid adjusted base-buy limit");
    }
    if (adjustedLimit.compareTo(Monetary.round(originalLimit)) >= 0) {
      return StrategyService.StrategyCreationResult.failed("base-buy limit is already adjusted and does not need lowering");
    }

    pending.setBaseBuyLimitPrice(adjustedLimit);
    String todayLowText = (todayLow != null && todayLow.signum() > 0)
        ? "$" + Monetary.round(todayLow).toPlainString()
        : "unavailable";
    String cachedPriceText = (cachedCurrentPrice != null && cachedCurrentPrice.signum() > 0)
        ? "$" + Monetary.round(cachedCurrentPrice).toPlainString()
        : "unavailable";
    pending.setLastEvent("Losing pending base buy readjusted from $"
        + Monetary.round(originalLimit).toPlainString()
        + " to $" + adjustedLimit.toPlainString()
        + " (todayLow=" + todayLowText + ", cachedPrice=" + cachedPriceText + ").");
    strategyRepository.save(pending);
    log("[" + pending.symbol() + "] Readjusted losing pending base buy from $"
        + Monetary.round(originalLimit).toPlainString()
        + " to $" + adjustedLimit.toPlainString()
        + " (todayLow=" + todayLowText + ", cachedPrice=" + cachedPriceText + ").");
    return StrategyService.StrategyCreationResult.success(pending.id(), "", "", "");
  }

  private BigDecimal loadTodayLow(Strategy strategy) {
    HttpAlpacaClient client = alpacaClientForStrategyMode(strategy.mode());
    if (client == null || strategy.symbol() == null || strategy.symbol().isBlank()) {
      return BigDecimal.ZERO;
    }
    LocalDate today = LocalDate.now();
    try {
      return client.getDailyBars(strategy.symbol(), today, today).stream()
          .map(MarketBar::low)
          .filter(low -> low != null && low.signum() > 0)
          .min(BigDecimal::compareTo)
          .orElse(BigDecimal.ZERO);
    } catch (Exception ex) {
      log("[" + strategy.symbol() + "] Unable to load today's low before pending base buy placement: "
          + ex.getMessage());
      return BigDecimal.ZERO;
    }
  }

  private boolean isGapRocketPendingOrderPlacement(Strategy strategy) {
    return GapAndGoCoordinator.isPendingOrderPlacement(strategy);
  }

  private void openGapRocketAnalysisDialog() {
    GapRocketConfig lastGapRocketConfig = lastGapRocketConfigs.get(selectedViewMode);
    GapRocketAnalysisDialog dialog = new GapRocketAnalysisDialog(this, selectedViewMode, lastGapRocketConfig);
    dialog.setLocationRelativeTo(this);
    dialog.setVisible(true);
    if (!dialog.accepted()) {
      return;
    }
    GapRocketConfig selectedModeConfig = dialog.config();
    lastGapRocketConfigs.put(selectedViewMode, selectedModeConfig);
    switch (dialog.runMode()) {
      case ANALYZE -> gapAndGoCoordinator.analyze(selectedModeConfig, false);
      case ANALYZE_AND_EXECUTE -> gapAndGoCoordinator.analyze(selectedModeConfig, true);
      case SCHEDULE -> gapAndGoCoordinator.scheduleOrCancel(selectedModeConfig);
    }
  }

  private void openOrbAnalysisDialog() {
    OrbConfig lastOrbConfig = lastOrbConfigs.get(selectedViewMode);
    OrbAnalysisDialog dialog = new OrbAnalysisDialog(this, selectedViewMode, lastOrbConfig);
    dialog.setLocationRelativeTo(this);
    dialog.setVisible(true);
    if (!dialog.accepted()) {
      return;
    }
    OrbConfig selectedModeConfig = dialog.config();
    lastOrbConfigs.put(selectedViewMode, selectedModeConfig);
    orbCoordinator.run(selectedModeConfig, dialog.runMode());
  }

  private void openDipHunterAnalysisDialog() {
    DipHunterConfig lastDipHunterConfig = lastDipHunterConfigs.get(selectedViewMode);
    DipHunterAnalysisDialog dialog = new DipHunterAnalysisDialog(this, selectedViewMode, lastDipHunterConfig);
    dialog.setLocationRelativeTo(this);
    dialog.setVisible(true);
    if (!dialog.accepted()) {
      return;
    }
    DipHunterConfig selectedModeConfig = dialog.config();
    lastDipHunterConfigs.put(selectedViewMode, selectedModeConfig);
    switch (dialog.runMode()) {
      case ANALYZE -> dipHunterCoordinator.analyze(selectedModeConfig, false);
      case ANALYZE_AND_EXECUTE -> dipHunterCoordinator.analyze(selectedModeConfig, true);
      case SCHEDULE -> dipHunterCoordinator.scheduleOrCancel(selectedModeConfig);
    }
  }

  private void openVwapAnalysisDialog() {
    VwapConfig lastVwapConfig = lastVwapConfigs.get(selectedViewMode);
    VwapAnalysisDialog dialog = new VwapAnalysisDialog(this, selectedViewMode, lastVwapConfig);
    dialog.setLocationRelativeTo(this);
    dialog.setVisible(true);
    if (!dialog.accepted()) {
      return;
    }
    VwapConfig selectedModeConfig = dialog.config();
    lastVwapConfigs.put(selectedViewMode, selectedModeConfig);
    switch (dialog.runMode()) {
      case ANALYZE -> vwapCoordinator.analyze(selectedModeConfig, false);
      case ANALYZE_AND_EXECUTE -> vwapCoordinator.analyze(selectedModeConfig, true);
      case SCHEDULE -> vwapCoordinator.scheduleOrCancel(selectedModeConfig);
    }
  }

  private void openSwingAnalysisDialog() {
    SwingConfig lastSwingConfig = lastSwingConfigs.get(selectedViewMode);
    SwingAnalysisDialog dialog = new SwingAnalysisDialog(this, selectedViewMode, lastSwingConfig);
    dialog.setLocationRelativeTo(this);
    dialog.setVisible(true);
    if (!dialog.accepted()) {
      return;
    }
    SwingConfig selectedModeConfig = dialog.config();
    lastSwingConfigs.put(selectedViewMode, selectedModeConfig);
    switch (dialog.runMode()) {
      case ANALYZE -> swingCoordinator.analyze(selectedModeConfig, false);
      case ANALYZE_AND_EXECUTE -> swingCoordinator.analyze(selectedModeConfig, true);
      case SCHEDULE -> swingCoordinator.scheduleOrCancel(selectedModeConfig);
    }
  }

  private void openRangeRiderAnalysisDialog() {
    RangeRiderConfig lastRangeRiderConfig = lastRangeRiderConfigs.get(selectedViewMode);
    RangeRiderAnalysisDialog dialog = new RangeRiderAnalysisDialog(this, selectedViewMode, lastRangeRiderConfig);
    dialog.setLocationRelativeTo(this);
    dialog.setVisible(true);
    if (!dialog.accepted()) {
      return;
    }
    RangeRiderConfig selectedModeConfig = dialog.config();
    lastRangeRiderConfigs.put(selectedViewMode, selectedModeConfig);
    switch (dialog.runMode()) {
      case ANALYZE -> rangeRiderCoordinator.analyze(selectedModeConfig, false);
      case ANALYZE_AND_EXECUTE -> rangeRiderCoordinator.analyze(selectedModeConfig, true);
      case SCHEDULE -> rangeRiderCoordinator.scheduleOrCancel(selectedModeConfig);
    }
  }

  private void openProfitShieldAnalysisDialog() {
    ProfitShieldConfig lastProfitShieldConfig = lastProfitShieldConfigs.get(selectedViewMode);
    ProfitShieldAnalysisDialog dialog = new ProfitShieldAnalysisDialog(this, selectedViewMode, lastProfitShieldConfig);
    dialog.setLocationRelativeTo(this);
    dialog.setVisible(true);
    if (!dialog.accepted()) {
      return;
    }
    ProfitShieldConfig selectedModeConfig = dialog.config();
    lastProfitShieldConfigs.put(selectedViewMode, selectedModeConfig);
    switch (dialog.runMode()) {
      case ANALYZE -> profitShieldCoordinator.analyze(selectedModeConfig, false);
      case ANALYZE_AND_EXECUTE -> profitShieldCoordinator.analyze(selectedModeConfig, true);
      case SCHEDULE -> profitShieldCoordinator.scheduleOrCancel(selectedModeConfig);
    }
  }

  private void openEarningsHunterAnalysisDialog() {
    EarningsHunterConfig lastConfig = lastEarningsHunterConfigs.get(selectedViewMode);
    EarningsHunterAnalysisDialog dialog = new EarningsHunterAnalysisDialog(this, selectedViewMode, lastConfig);
    dialog.setLocationRelativeTo(this);
    dialog.setVisible(true);
    if (!dialog.accepted()) {
      return;
    }
    EarningsHunterConfig selectedModeConfig = dialog.config();
    lastEarningsHunterConfigs.put(selectedViewMode, selectedModeConfig);
    switch (dialog.runMode()) {
      case ANALYZE -> earningsHunterCoordinator.analyze(selectedModeConfig, false);
      case ANALYZE_AND_EXECUTE -> earningsHunterCoordinator.analyze(selectedModeConfig, true);
    }
  }

  /**
   * Load any persisted schedules and start the autonomous schedulers.
   */
  public void startBackgroundSchedulers() {
    gapAndGoCoordinator.start();
    smartPicksWorkspaceCoordinator.start();
    applyHistoryReentrySchedule();
    orbCoordinator.start();
    dipHunterCoordinator.start();
    vwapCoordinator.start();
    swingCoordinator.start();
    rangeRiderCoordinator.start();
    profitShieldCoordinator.start();
    autoRiskAdjustmentService.start();
    portfolioEmailScheduler.setSettings(appSettingsService.loadPortfolioEmailSettings());
    portfolioEmailScheduler.start();
  }

  /**
   * Latest cached price for a strategy, used by the after-close Auto Adjust Risk &amp; Stop Loss runner. Reads the cached position snapshot only (no broker call); returns
   * {@code null} when no price is known yet so the adjuster safely skips that strategy for the day.
   */
  private BigDecimal latestPriceForAutoAdjust(Strategy strategy) {
    if (strategy == null) {
      return null;
    }
    ManagedStrategy entry = findStrategyById(strategy.id());
    if (entry == null) {
      return null;
    }
    BigDecimal price = entry.cachedPosition().getLastPrice();
    return price != null && price.signum() > 0 ? price : null;
  }

  /**
   * Refresh the grid after the coordinator applies gap-and-go recommendations (called on the EDT).
   */
  private void onGapRocketRecommendationsApplied(String workspaceId, String firstAddedStrategyId) {
    syncStrategiesFromRepository();
    refreshStrategyTableData();
    applyCurrentStrategiesRowFilter();
    refreshWorkspaceSummary();
    refreshStrategyWorkspaceEmptyState();
    updateStatusBar();
    if (firstAddedStrategyId != null && firstAddedStrategyId.length() > 0
        && workspaceId.equals(selectedWorkspaceId)) {
      SwingUtilities.invokeLater(() -> selectAndRevealStrategy(firstAddedStrategyId));
    }
  }

  /**
   * Refresh the grid after the coordinator applies ORB recommendations (called on the EDT).
   */
  private void onOrbRecommendationsApplied(String workspaceId, String firstAddedStrategyId) {
    syncStrategiesFromRepository();
    refreshStrategyTableData();
    applyCurrentStrategiesRowFilter();
    refreshWorkspaceSummary();
    refreshStrategyWorkspaceEmptyState();
    updateStatusBar();
    if (firstAddedStrategyId != null && firstAddedStrategyId.length() > 0
        && workspaceId.equals(selectedWorkspaceId)) {
      SwingUtilities.invokeLater(() -> selectAndRevealStrategy(firstAddedStrategyId));
    }
  }

  /**
   * Refresh the grid after the coordinator applies Dip Hunter recommendations (called on the EDT).
   */
  private void onDipHunterRecommendationsApplied(String workspaceId, String firstAddedStrategyId) {
    syncStrategiesFromRepository();
    refreshStrategyTableData();
    applyCurrentStrategiesRowFilter();
    refreshWorkspaceSummary();
    refreshStrategyWorkspaceEmptyState();
    updateStatusBar();
    if (firstAddedStrategyId != null && firstAddedStrategyId.length() > 0
        && workspaceId.equals(selectedWorkspaceId)) {
      SwingUtilities.invokeLater(() -> selectAndRevealStrategy(firstAddedStrategyId));
    }
  }

  /**
   * Refresh the grid after the coordinator applies VWAP Desk recommendations (called on the EDT).
   */
  private void onVwapRecommendationsApplied(String workspaceId, String firstAddedStrategyId) {
    syncStrategiesFromRepository();
    refreshStrategyTableData();
    applyCurrentStrategiesRowFilter();
    refreshWorkspaceSummary();
    refreshStrategyWorkspaceEmptyState();
    updateStatusBar();
    if (firstAddedStrategyId != null && firstAddedStrategyId.length() > 0
        && workspaceId.equals(selectedWorkspaceId)) {
      SwingUtilities.invokeLater(() -> selectAndRevealStrategy(firstAddedStrategyId));
    }
  }

  /** Refresh the grid after the coordinator applies Swing Vault recommendations (called on the EDT). */
  /**
   * Refreshes the grid after Smart Picks created rows — the same steps the other workspace types take. Without re-filtering and re-evaluating the empty state, a Smart Picks
   * workspace that was empty kept showing its Analyze card over the rows just placed into it, which only appeared under All Stocks.
   */
  private void onSmartPicksPlaced() {
    syncStrategiesFromRepository();
    refreshStrategyTableData();
    applyCurrentStrategiesRowFilter();
    refreshWorkspaceSummary();
    refreshStrategyWorkspaceEmptyState();
    updateStatusBar();
    refreshPanels();
  }

  private void onSwingRecommendationsApplied(String workspaceId, String firstAddedStrategyId) {
    syncStrategiesFromRepository();
    refreshStrategyTableData();
    applyCurrentStrategiesRowFilter();
    refreshWorkspaceSummary();
    refreshStrategyWorkspaceEmptyState();
    updateStatusBar();
    if (firstAddedStrategyId != null && firstAddedStrategyId.length() > 0
        && workspaceId.equals(selectedWorkspaceId)) {
      SwingUtilities.invokeLater(() -> selectAndRevealStrategy(firstAddedStrategyId));
    }
  }

  /**
   * Refresh the grid after the coordinator applies Range Rider recommendations (called on the EDT).
   */
  private void onRangeRiderRecommendationsApplied(String workspaceId, String firstAddedStrategyId) {
    syncStrategiesFromRepository();
    refreshStrategyTableData();
    applyCurrentStrategiesRowFilter();
    refreshWorkspaceSummary();
    refreshStrategyWorkspaceEmptyState();
    updateStatusBar();
    if (firstAddedStrategyId != null && firstAddedStrategyId.length() > 0
        && workspaceId.equals(selectedWorkspaceId)) {
      SwingUtilities.invokeLater(() -> selectAndRevealStrategy(firstAddedStrategyId));
    }
  }

  /**
   * Refresh the grid after the coordinator applies Profit Shield recommendations (called on the EDT).
   */
  private void onProfitShieldRecommendationsApplied(String workspaceId, String firstAddedStrategyId) {
    syncStrategiesFromRepository();
    refreshStrategyTableData();
    applyCurrentStrategiesRowFilter();
    refreshWorkspaceSummary();
    refreshStrategyWorkspaceEmptyState();
    updateStatusBar();
    if (firstAddedStrategyId != null && firstAddedStrategyId.length() > 0
        && workspaceId.equals(selectedWorkspaceId)) {
      SwingUtilities.invokeLater(() -> selectAndRevealStrategy(firstAddedStrategyId));
    }
  }

  /**
   * Reflect the current Profit Shield schedule on the action bar (badge + cancel button).
   */
  private void updateProfitShieldScheduleBadge(ProfitShieldSchedule schedule) {
    if (profitShieldScheduleStatusLabel == null) {
      return;
    }
    profitShieldScheduleStatusLabel.setText(schedule != null && schedule.enabled()
        ? "Scheduled: scan " + schedule.scanTimeEt() + " ET"
          + (schedule.executeAfterScan() ? " (auto-execute)" : "")
        : "");
    refreshStrategyWorkspaceEmptyState();
  }

  private void onEarningsHunterRecommendationsApplied(String workspaceId, String firstAddedStrategyId) {
    syncStrategiesFromRepository();
    refreshStrategyTableData();
    applyCurrentStrategiesRowFilter();
    refreshWorkspaceSummary();
    refreshStrategyWorkspaceEmptyState();
    updateStatusBar();
    if (firstAddedStrategyId != null && firstAddedStrategyId.length() > 0
        && workspaceId.equals(selectedWorkspaceId)) {
      SwingUtilities.invokeLater(() -> selectAndRevealStrategy(firstAddedStrategyId));
    }
  }

  /**
   * Reflect the current schedule on the Gap Rocket action bar (badge + cancel button).
   */
  private void updateGapRocketScheduleBadge(GapAndGoSchedule schedule) {
    if (gapRocketScheduleStatusLabel == null) {
      return;
    }
    gapRocketScheduleStatusLabel.setText(schedule != null && schedule.enabled()
        ? "Scheduled: scan " + schedule.scanTimeEt() + " ET"
          + (schedule.executeAfterScan() ? " (auto-execute)" : "")
        : "");
    refreshStrategyWorkspaceEmptyState();
  }

  /**
   * Reflect the current ORB schedule on the action bar (badge + cancel button).
   */
  private void updateOrbScheduleBadge(OrbSchedule schedule) {
    if (orbScheduleStatusLabel == null) {
      return;
    }
    orbScheduleStatusLabel.setText(schedule != null && schedule.enabled()
        ? "Scheduled: analysis " + schedule.rangeAnalysisTimeEt() + " ET"
          + (schedule.executeAfterRangeClose() ? " (auto-execute)" : "")
        : "");
    refreshStrategyWorkspaceEmptyState();
  }

  /**
   * Reflect the current Dip Hunter schedule on the action bar (badge + cancel button).
   */
  private void updateDipHunterScheduleBadge(DipHunterSchedule schedule) {
    if (dipHunterScheduleStatusLabel == null) {
      return;
    }
    dipHunterScheduleStatusLabel.setText(schedule != null && schedule.enabled()
        ? "Scheduled: scan " + schedule.scanTimeEt() + " ET"
          + (schedule.executeAfterScan() ? " (auto-execute)" : "")
        : "");
    refreshStrategyWorkspaceEmptyState();
  }

  /**
   * Reflect the current VWAP Desk schedule on the action bar (badge + cancel button).
   */
  private void updateVwapScheduleBadge(VwapSchedule schedule) {
    if (vwapScheduleStatusLabel == null) {
      return;
    }
    vwapScheduleStatusLabel.setText(schedule != null && schedule.enabled()
        ? "Scheduled: scan " + schedule.scanTimeEt() + " ET"
          + (schedule.executeAfterScan() ? " (auto-execute)" : "")
        : "");
    refreshStrategyWorkspaceEmptyState();
  }

  /**
   * Reflect the current Swing Vault schedule on the action bar (badge + cancel button).
   */
  private void updateSwingScheduleBadge(SwingSchedule schedule) {
    if (swingScheduleStatusLabel == null) {
      return;
    }
    swingScheduleStatusLabel.setText(schedule != null && schedule.enabled()
        ? "Scheduled: scan " + schedule.scanTimeEt() + " ET"
          + (schedule.executeAfterScan() ? " (auto-execute)" : "")
        : "");
    refreshStrategyWorkspaceEmptyState();
  }

  /**
   * Reflect the current Range Rider schedule on the action bar (badge + cancel button).
   */
  private void updateRangeRiderScheduleBadge(RangeRiderSchedule schedule) {
    if (rangeRiderScheduleStatusLabel == null) {
      return;
    }
    rangeRiderScheduleStatusLabel.setText(schedule != null && schedule.enabled()
        ? "Scheduled: scan " + schedule.scanTimeEt() + " ET"
          + (schedule.executeAfterScan() ? " (auto-execute)" : "")
        : "");
    refreshStrategyWorkspaceEmptyState();
  }

  /**
   * Bridges {@link GapAndGoCoordinator}'s needs to this frame without leaking the frame into it.
   */
  private final class GapAndGoCoordinatorUi implements GapAndGoCoordinator.Ui {

    @Override
    public String runtimeApiKey() {
      return runtimeApiKey;
    }

    @Override
    public String runtimeApiSecret() {
      return runtimeApiSecret;
    }

    @Override
    public boolean connectionOk() {
      return connectionOk;
    }

    @Override
    public String selectedModeLabel() {
      return TradingFrame.this.selectedModeLabel();
    }

    @Override
    public int defaultStrategyPollingSeconds() {
      return settingsDialog.appliedDefaultStrategyPollingSeconds();
    }

    @Override
    public String selectedWorkspaceId() {
      return selectedWorkspaceId;
    }

    @Override
    public boolean isGapRocketWorkspaceSelected() {
      return isSelectedGapRocketWorkspace();
    }

    @Override
    public boolean workspaceExists(String workspaceId) {
      return workspaceId != null && workspaceService.findById(workspaceId).isPresent();
    }

    @Override
    public void log(String message) {
      TradingFrame.this.log(message);
    }

    @Override
    public void setScanButtonsEnabled(boolean enabled) {
      gapRocketAnalyzeButton.setEnabled(enabled);
      gapRocketPlaceOrdersButton.setEnabled(enabled);
    }

    @Override
    public void onRecommendationsApplied(String workspaceId, String firstAddedStrategyId) {
      onGapRocketRecommendationsApplied(workspaceId, firstAddedStrategyId);
    }

    @Override
    public void onScheduleChanged(GapAndGoSchedule schedule) {
      updateGapRocketScheduleBadge(schedule);
    }

    @Override
    public java.awt.Component dialogParent() {
      return TradingFrame.this;
    }
  }

  /**
   * Bridges {@link OrbCoordinator}'s needs to this frame without leaking the frame into it.
   */
  private final class OrbCoordinatorUi implements OrbCoordinator.Ui {

    @Override
    public String runtimeApiKey() {
      return runtimeApiKey;
    }

    @Override
    public String runtimeApiSecret() {
      return runtimeApiSecret;
    }

    @Override
    public boolean connectionOk() {
      return connectionOk;
    }

    @Override
    public String selectedModeLabel() {
      return TradingFrame.this.selectedModeLabel();
    }

    @Override
    public int defaultStrategyPollingSeconds() {
      return settingsDialog.appliedDefaultStrategyPollingSeconds();
    }

    @Override
    public String selectedWorkspaceId() {
      return selectedWorkspaceId;
    }

    @Override
    public boolean isOrbWorkspaceSelected() {
      return isSelectedOrbWorkspace();
    }

    @Override
    public void log(String message) {
      TradingFrame.this.log(message);
    }

    @Override
    public void setAnalyzeButtonEnabled(boolean enabled) {
      orbAnalyzeButton.setEnabled(enabled);
    }

    @Override
    public void onRecommendationsApplied(String workspaceId, String firstAddedStrategyId) {
      onOrbRecommendationsApplied(workspaceId, firstAddedStrategyId);
    }

    @Override
    public void onScheduleChanged(OrbSchedule schedule) {
      updateOrbScheduleBadge(schedule);
    }

    @Override
    public java.awt.Component dialogParent() {
      return TradingFrame.this;
    }
  }

  /**
   * Bridges {@link DipHunterCoordinator}'s needs to this frame without leaking the frame into it.
   */
  private final class DipHunterCoordinatorUi implements DipHunterCoordinator.Ui {

    @Override
    public String runtimeApiKey() {
      return runtimeApiKey;
    }

    @Override
    public String runtimeApiSecret() {
      return runtimeApiSecret;
    }

    @Override
    public boolean connectionOk() {
      return connectionOk;
    }

    @Override
    public String selectedModeLabel() {
      return TradingFrame.this.selectedModeLabel();
    }

    @Override
    public int defaultStrategyPollingSeconds() {
      return settingsDialog.appliedDefaultStrategyPollingSeconds();
    }

    @Override
    public String selectedWorkspaceId() {
      return selectedWorkspaceId;
    }

    @Override
    public boolean isDipHunterWorkspaceSelected() {
      return isSelectedDipHunterWorkspace();
    }

    @Override
    public void log(String message) {
      TradingFrame.this.log(message);
    }

    @Override
    public void setScanButtonsEnabled(boolean enabled) {
      dipHunterAnalyzeButton.setEnabled(enabled);
    }

    @Override
    public void onRecommendationsApplied(String workspaceId, String firstAddedStrategyId) {
      onDipHunterRecommendationsApplied(workspaceId, firstAddedStrategyId);
    }

    @Override
    public void onScheduleChanged(DipHunterSchedule schedule) {
      updateDipHunterScheduleBadge(schedule);
    }

    @Override
    public java.awt.Component dialogParent() {
      return TradingFrame.this;
    }
  }

  /**
   * Bridges {@link VwapCoordinator}'s needs to this frame without leaking the frame into it.
   */
  private final class VwapCoordinatorUi implements VwapCoordinator.Ui {

    @Override
    public String runtimeApiKey() {
      return runtimeApiKey;
    }

    @Override
    public String runtimeApiSecret() {
      return runtimeApiSecret;
    }

    @Override
    public boolean connectionOk() {
      return connectionOk;
    }

    @Override
    public String selectedModeLabel() {
      return TradingFrame.this.selectedModeLabel();
    }

    @Override
    public int defaultStrategyPollingSeconds() {
      return settingsDialog.appliedDefaultStrategyPollingSeconds();
    }

    @Override
    public String selectedWorkspaceId() {
      return selectedWorkspaceId;
    }

    @Override
    public boolean isVwapWorkspaceSelected() {
      return isSelectedVwapWorkspace();
    }

    @Override
    public void log(String message) {
      TradingFrame.this.log(message);
    }

    @Override
    public void setScanButtonsEnabled(boolean enabled) {
      vwapAnalyzeButton.setEnabled(enabled);
    }

    @Override
    public void onRecommendationsApplied(String workspaceId, String firstAddedStrategyId) {
      onVwapRecommendationsApplied(workspaceId, firstAddedStrategyId);
    }

    @Override
    public void onScheduleChanged(VwapSchedule schedule) {
      updateVwapScheduleBadge(schedule);
    }

    @Override
    public java.awt.Component dialogParent() {
      return TradingFrame.this;
    }
  }

  /**
   * Bridges {@link SwingCoordinator}'s needs to this frame without leaking the frame into it.
   */
  private final class SwingCoordinatorUi implements SwingCoordinator.Ui {

    @Override
    public String runtimeApiKey() {
      return runtimeApiKey;
    }

    @Override
    public String runtimeApiSecret() {
      return runtimeApiSecret;
    }

    @Override
    public boolean connectionOk() {
      return connectionOk;
    }

    @Override
    public String selectedModeLabel() {
      return TradingFrame.this.selectedModeLabel();
    }

    @Override
    public int defaultStrategyPollingSeconds() {
      return settingsDialog.appliedDefaultStrategyPollingSeconds();
    }

    @Override
    public String selectedWorkspaceId() {
      return selectedWorkspaceId;
    }

    @Override
    public boolean isSwingWorkspaceSelected() {
      return isSelectedSwingWorkspace();
    }

    @Override
    public void log(String message) {
      TradingFrame.this.log(message);
    }

    @Override
    public void setScanButtonsEnabled(boolean enabled) {
      swingAnalyzeButton.setEnabled(enabled);
    }

    @Override
    public void onRecommendationsApplied(String workspaceId, String firstAddedStrategyId) {
      onSwingRecommendationsApplied(workspaceId, firstAddedStrategyId);
    }

    @Override
    public void onScheduleChanged(SwingSchedule schedule) {
      updateSwingScheduleBadge(schedule);
    }

    @Override
    public java.awt.Component dialogParent() {
      return TradingFrame.this;
    }
  }

  /**
   * Bridges {@link RangeRiderCoordinator}'s needs to this frame without leaking the frame into it.
   */
  private final class RangeRiderCoordinatorUi implements RangeRiderCoordinator.Ui {

    @Override
    public String runtimeApiKey() {
      return runtimeApiKey;
    }

    @Override
    public String runtimeApiSecret() {
      return runtimeApiSecret;
    }

    @Override
    public boolean connectionOk() {
      return connectionOk;
    }

    @Override
    public String selectedModeLabel() {
      return TradingFrame.this.selectedModeLabel();
    }

    @Override
    public int defaultStrategyPollingSeconds() {
      return settingsDialog.appliedDefaultStrategyPollingSeconds();
    }

    @Override
    public String selectedWorkspaceId() {
      return selectedWorkspaceId;
    }

    @Override
    public boolean isRangeRiderWorkspaceSelected() {
      return isSelectedRangeRiderWorkspace();
    }

    @Override
    public void log(String message) {
      TradingFrame.this.log(message);
    }

    @Override
    public void setScanButtonsEnabled(boolean enabled) {
      rangeRiderAnalyzeButton.setEnabled(enabled);
    }

    @Override
    public void onRecommendationsApplied(String workspaceId, String firstAddedStrategyId) {
      onRangeRiderRecommendationsApplied(workspaceId, firstAddedStrategyId);
    }

    @Override
    public void onScheduleChanged(RangeRiderSchedule schedule) {
      updateRangeRiderScheduleBadge(schedule);
    }

    @Override
    public java.awt.Component dialogParent() {
      return TradingFrame.this;
    }
  }

  /**
   * Bridges {@link ProfitShieldCoordinator}'s needs to this frame without leaking the frame into it.
   */
  private final class ProfitShieldCoordinatorUi implements ProfitShieldCoordinator.Ui {

    @Override
    public String runtimeApiKey() {
      return runtimeApiKey;
    }

    @Override
    public String runtimeApiSecret() {
      return runtimeApiSecret;
    }

    @Override
    public boolean connectionOk() {
      return connectionOk;
    }

    @Override
    public String selectedModeLabel() {
      return TradingFrame.this.selectedModeLabel();
    }

    @Override
    public int defaultStrategyPollingSeconds() {
      return settingsDialog.appliedDefaultStrategyPollingSeconds();
    }

    @Override
    public String selectedWorkspaceId() {
      return selectedWorkspaceId;
    }

    @Override
    public boolean isProfitShieldWorkspaceSelected() {
      return isSelectedProfitShieldWorkspace();
    }

    @Override
    public void log(String message) {
      TradingFrame.this.log(message);
    }

    @Override
    public void setScanButtonsEnabled(boolean enabled) {
      profitShieldAnalyzeButton.setEnabled(enabled);
    }

    @Override
    public void onRecommendationsApplied(String workspaceId, String firstAddedStrategyId) {
      onProfitShieldRecommendationsApplied(workspaceId, firstAddedStrategyId);
    }

    @Override
    public void onScheduleChanged(ProfitShieldSchedule schedule) {
      updateProfitShieldScheduleBadge(schedule);
    }

    @Override
    public java.awt.Component dialogParent() {
      return TradingFrame.this;
    }
  }

  private final class EarningsHunterCoordinatorUi implements EarningsHunterCoordinator.Ui {

    @Override
    public String runtimeApiKey() {
      return runtimeApiKey;
    }

    @Override
    public String runtimeApiSecret() {
      return runtimeApiSecret;
    }

    @Override
    public boolean connectionOk() {
      return connectionOk;
    }

    @Override
    public String selectedModeLabel() {
      return TradingFrame.this.selectedModeLabel();
    }

    @Override
    public int defaultStrategyPollingSeconds() {
      return settingsDialog.appliedDefaultStrategyPollingSeconds();
    }

    @Override
    public String selectedWorkspaceId() {
      return selectedWorkspaceId;
    }

    @Override
    public boolean isEarningsHunterWorkspaceSelected() {
      return isSelectedEarningsHunterWorkspace();
    }

    @Override
    public void log(String message) {
      TradingFrame.this.log(message);
    }

    @Override
    public void setScanButtonsEnabled(boolean enabled) {
      earningsHunterAnalyzeButton.setEnabled(enabled);
    }

    @Override
    public void onRecommendationsApplied(String workspaceId, String firstAddedStrategyId) {
      onEarningsHunterRecommendationsApplied(workspaceId, firstAddedStrategyId);
    }

    @Override
    public java.awt.Component dialogParent() {
      return TradingFrame.this;
    }
  }

  // Builds the per-tab P&L summary for the selected workspace (or All Stocks) from cached
  // snapshots — no broker calls — and renders it in the summary row below the grid.

  /**
   * Refreshes the footer under the grid for the selected tab: its P&L and its portfolio figures in one row. Runs on every tab switch, so a new tab shows its own figures at once.
   */
  private void refreshWorkspaceSummary() {
    String label = selectedScopeLabel();
    WorkspaceAccounting.Snapshot snapshot = computeWorkspaceSnapshot(selectedWorkspaceId);
    java.util.List<PortfolioScopePresenter.Figure> figures =
        new java.util.ArrayList<>(workspaceSummaryPresenter.figures(label, snapshot));
    figures.addAll(portfolioScopePresenter.present(label, portfolioMetrics(selectedWorkspaceId)).gridFigures());
    workspaceGridAnalyticsBar.apply(label, workspaceSummaryPresenter.titleTooltip(label, snapshot), figures);
  }

  /**
   * The selected grid's name: a workspace, or All Stocks.
   */
  private String selectedScopeLabel() {
    return selectedWorkspaceId == null
        ? "All Stocks"
        : workspaceService.findById(selectedWorkspaceId).map(StrategyWorkspace::name).orElse("Workspace");
  }

  /**
   * Recalculates the figures from the rows as they are now: every workspace for the bottom status bar, and the selected tab for the footer under its grid. Runs with every
   * status-bar update and every grid refresh, so fills, orders and price moves reach both at once.
   */
  private void refreshPortfolioAnalytics() {
    if (bottomStatusBars != null) {
      bottomStatusBars.applyPortfolioScope(
          portfolioScopePresenter.present(PortfolioScopePresenter.ALL_WORKSPACES, portfolioMetrics(null)));
    }
    refreshWorkspaceSummary();
    refreshAccountEquityChart();
    refreshWorkspaceValueChart();
  }

  /**
   * Records this minute's holdings value of every workspace, in both modes, from the cached grid rows.
   */
  private void recordWorkspaceValues() {
    if (workspaceValueRecorder == null) {
      return;
    }
    for (StrategyMode mode : StrategyMode.values()) {
      if (!ModeActivity.runsFor(mode, selectedViewMode)) {
        continue;
      }
      for (StrategyWorkspace workspace : workspaceService.activeWorkspaces(mode)) {
        workspaceValueRecorder.record(mode, workspace.id(), portfolioMetrics(mode, workspace.id()).marketValue());
      }
    }
    refreshWorkspaceValueChart();
  }

  /**
   * The selected workspace's line, or the first workspace's while All Stocks is selected.
   */
  private void refreshWorkspaceValueChart() {
    if (workspaceValueRecorder == null) {
      return;
    }
    String modeLabel = selectedViewMode == StrategyMode.LIVE ? "Live" : "Paper";
    Optional<StrategyWorkspace> workspace = selectedWorkspaceId == null
        ? workspaceService.activeWorkspaces(selectedViewMode).stream().findFirst()
        : workspaceService.findById(selectedWorkspaceId);
    if (workspace.isEmpty()) {
      workspaceValueChart.setSamples(List.of(), "No workspaces in " + modeLabel + " yet");
      return;
    }
    String id = workspace.get().id();
    workspaceValueChart.setSamples(workspaceValueRecorder.today(selectedViewMode, id),
        workspace.get().name() + " · " + modeLabel,
        workspaceValueRecorder.baselineLabel(selectedViewMode, id));
  }

  /**
   * Reads Alpaca account equity for both modes off the EDT and records this minute's point, so switching modes shows a complete day for each. A mode without a connected client is
   * skipped.
   */
  private void sampleAccountEquityAsync() {
    if (accountEquityRecorder == null || !connectionOk || connectionRetryPending
        || !accountEquityFetchInFlight.compareAndSet(false, true)) {
      return;
    }
    Map<StrategyMode, HttpAlpacaClient> clients = new EnumMap<>(StrategyMode.class);
    for (StrategyMode mode : StrategyMode.values()) {
      if (!ModeActivity.runsFor(mode, selectedViewMode)) {
        continue;
      }
      HttpAlpacaClient client = alpacaClientForMode(mode == StrategyMode.LIVE ? ApplicationMode.LIVE : ApplicationMode.PAPER);
      if (client != null) {
        clients.put(mode, client);
      }
    }
    uiPollingExecutor.execute(() -> {
      try {
        clients.forEach((mode, client) -> client.getAccountEquity().ifPresent(
            equity -> accountEquityRecorder.record(mode, equity.equity(), equity.lastEquity())));
      } finally {
        accountEquityFetchInFlight.set(false);
        SwingUtilities.invokeLater(this::refreshAccountEquityChart);
      }
    });
  }

  private void refreshAccountEquityChart() {
    if (accountEquityRecorder == null) {
      return;
    }
    accountEquityChart.setSamples(accountEquityRecorder.today(selectedViewMode),
        selectedViewMode == StrategyMode.LIVE ? "Live account" : "Paper account");
  }

  /**
   * Totals the current-mode grid rows of one workspace, or of every workspace when the id is null.
   */
  private SystemMetricsPresenter.PortfolioScopeMetrics portfolioMetrics(String workspaceId) {
    return portfolioMetrics(selectedViewMode, workspaceId);
  }

  /**
   * Totals one mode's grid rows, for one workspace or every workspace when the id is null.
   */
  private SystemMetricsPresenter.PortfolioScopeMetrics portfolioMetrics(StrategyMode mode, String workspaceId) {
    List<ManagedStrategy> rows = strategies.stream()
        .filter(entry -> entry.strategy != null && entry.strategy.mode() == mode)
        .filter(this::includeInStrategiesTab)
        .filter(entry -> matchesPortfolioActionScope(entry.strategy, mode, workspaceId))
        .toList();
    return systemMetricsPresenter.computePortfolioScopeMetrics(rows, strategyOrderRepository::findByStrategyId);
  }

  // Opens the read-only risk dashboard: builds strategy-level risk analytics from cached
  // snapshots on the EDT, fetches Alpaca positions off-EDT for reconciliation, then renders.

  /**
   * Emails the portfolio snapshot for the viewed mode: every workspace's totals, each workspace, the Risk Dashboard's analysis and the broker reconciliation. Built on the EDT from
   * the cached rows; the Alpaca comparison and the send run in the background.
   */
  private void emailPortfolioSnapshot(String occasion, String typedEmail) {
    PortfolioSnapshotAssembler.RiskInputs inputs = riskInputs();
    HttpAlpacaClient client = alpacaClientForMode(selectedApplicationMode());
    portfolioSnapshotEmailService.send(PortfolioSnapshotAssembler.assemble(
            occasion, java.time.ZonedDateTime.now(java.time.ZoneId.of("America/New_York")), selectedModeLabel(),
            availableFundsText, workspaceService.activeWorkspaces(selectedViewMode),
            this::portfolioMetrics, this::computeWorkspaceSnapshot, inputs), typedEmail,
        () -> PortfolioSnapshotAssembler.brokerCheck(inputs.localPositions(), client));
  }

  /**
   * The Risk Dashboard's inputs for the viewed mode; the portfolio snapshot email uses the same.
   */
  private PortfolioSnapshotAssembler.RiskInputs riskInputs() {
    java.util.List<com.neuralarc.analytics.RiskAnalytics.Holding> holdings = new java.util.ArrayList<>();
    java.util.List<com.neuralarc.analytics.RiskAnalytics.PositionInput> positionInputs = new java.util.ArrayList<>();
    java.util.List<ReconciliationService.SymbolPosition> localPositions = new java.util.ArrayList<>();
    for (ManagedStrategy entry : strategies) {
      if (entry.strategy.mode() != selectedViewMode) {
        continue;
      }
      Position position = GapRocketDisplaySupport.suppressBrokerPosition(entry.strategy)
          ? new Position(entry.strategy.symbol())
          : entry.cachedPosition();
      String workspaceLabel = entry.strategy.workspaceId() == null
          ? "Unassigned"
          : workspaceService.findById(entry.strategy.workspaceId()).map(StrategyWorkspace::name).orElse("Unassigned");
      BigDecimal totalPnl = position.unrealizedPnl().add(realizedPnlForStrategy(entry.strategy.id()));
      holdings.add(new com.neuralarc.analytics.RiskAnalytics.Holding(
          entry.strategy.symbol(), workspaceLabel, position.marketValue(), totalPnl));
      if (position.getTotalShares() != 0) {
        localPositions.add(new ReconciliationService.SymbolPosition(
            entry.strategy.symbol(),
            BigDecimal.valueOf(position.getTotalShares()),
            position.getAverageCost()));
        positionInputs.add(new com.neuralarc.analytics.RiskAnalytics.PositionInput(
            entry.strategy.symbol(), workspaceLabel,
            BigDecimal.valueOf(position.getTotalShares()), position.getAverageCost(),
            position.getLastPrice(), entry.strategy.stopLossPrice(), entry.strategy.targetSellPrice()));
      }
    }
    return new PortfolioSnapshotAssembler.RiskInputs(holdings, positionInputs, localPositions);
  }

  private void openRiskDashboard() {
    PortfolioSnapshotAssembler.RiskInputs inputs = riskInputs();
    java.util.List<ReconciliationService.SymbolPosition> localPositions = inputs.localPositions();
    com.neuralarc.analytics.RiskAnalytics.Report riskReport = com.neuralarc.analytics.RiskAnalytics.analyze(inputs.holdings());
    java.util.List<com.neuralarc.analytics.RiskAnalytics.PositionRisk> positionRisks =
        com.neuralarc.analytics.RiskAnalytics.classify(inputs.positions());
    HttpAlpacaClient client = alpacaClientForMode(selectedApplicationMode());
    String modeLabel = selectedModeLabel();

    new SwingWorker<java.util.List<ReconciliationService.SymbolPosition>, Void>() {
      @Override
      protected java.util.List<ReconciliationService.SymbolPosition> doInBackground() {
        return PortfolioSnapshotAssembler.brokerPositions(client);
      }

      @Override
      protected void done() {
        ReconciliationService.Report reconciliation;
        try {
          reconciliation = new ReconciliationService().reconcile(localPositions, get());
        } catch (Exception ex) {
          reconciliation = new ReconciliationService().reconcile(localPositions, java.util.List.of());
          log("[RISK] Could not fetch broker positions for reconciliation: " + ex.getMessage());
        }
        RiskDashboardPanel panel = new RiskDashboardPanel(modeLabel, riskReport, positionRisks, reconciliation,
            LossHarvesting.analyze(lossHarvestingPositions(), realizedGainsThisYear(), java.time.LocalDate.now()));
        new RiskDashboardDialog(TradingFrame.this, panel).setVisible(true);
      }
    }.execute();
  }

  private WorkspaceAccounting.Snapshot computeWorkspaceSnapshot(String workspaceId) {
    WorkspaceAccountingInputs.Result inputs = buildAccountingInputs(selectedViewMode);
    return WorkspaceAccounting.forWorkspace(workspaceId, inputs.accounts(), inputs.sells());
  }

  private record BrokerPositionsCacheEntry(List<AlpacaPositionData> positions, long loadedAtMillis) {

  }

  /**
   * Builds the canonical per-strategy accounting inputs for a mode — the single source of truth shared by the top status bar (All Stocks aggregate), every tab summary, and Capture
   * Portfolio. Open P&L is taken from {@link StrategyOpenPnlCalculator} (Gap-Rocket suppression + zero-cost/ price guards) and realized from the replayed fills, so every consumer
   * sees identical numbers.
   */
  private WorkspaceAccountingInputs.Result buildAccountingInputs(StrategyMode mode) {
    java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.systemDefault());
    java.util.List<WorkspaceAccountingInputs.StrategyInput> inputs = new java.util.ArrayList<>();
    for (ManagedStrategy entry : strategies) {
      if (entry == null || entry.strategy == null || entry.strategy.mode() != mode) {
        continue;
      }
      String entryWorkspaceId = entry.strategy.workspaceId();
      java.util.Optional<StrategyOpenPnlCalculator.Row> openRow = openPnlCalculator.openRow(entry);
      inputs.add(new WorkspaceAccountingInputs.StrategyInput(
          entryWorkspaceId,
          includeInCurrentStrategiesTab(entry),
          realizedSellsForStrategy(
              entryWorkspaceId, strategyOrderRepository.findByStrategyId(entry.strategy.id()), today),
          openRow.map(StrategyOpenPnlCalculator.Row::shares).orElse(0),
          openRow.map(StrategyOpenPnlCalculator.Row::unrealizedPnl).orElse(BigDecimal.ZERO),
          openRow.map(StrategyOpenPnlCalculator.Row::marketValue).orElse(BigDecimal.ZERO),
          entry.strategy.estimatedTotalCapital()));
    }
    return WorkspaceAccountingInputs.build(inputs);
  }

  // Reconstructs realized P&L per individual sell (one RealizedSell per filled sell), replaying
  // fills to track average cost — mirrors realizedPnlForOrders but keeps each trade for win rate.
  private java.util.List<WorkspaceAccounting.RealizedSell> realizedSellsForStrategy(
      String workspaceId, List<StrategyOrder> orders, java.time.LocalDate today) {
    List<StrategyOrder> filledOrders = orders.stream()
        .filter(order -> order.status() == StrategyOrderStatus.FILLED || order.status() == StrategyOrderStatus.PARTIALLY_FILLED)
        .filter(order -> StrategyOrderFillSupport.resolvedFilledQuantity(order).compareTo(BigDecimal.ZERO) > 0)
        .sorted(Comparator
            .comparing(StrategyOrder::filledAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(StrategyOrder::submittedAt, Comparator.nullsLast(Comparator.naturalOrder())))
        .toList();

    java.util.List<WorkspaceAccounting.RealizedSell> result = new java.util.ArrayList<>();
    BigDecimal positionQty = BigDecimal.ZERO;
    BigDecimal averageCost = BigDecimal.ZERO;
    for (StrategyOrder order : filledOrders) {
      BigDecimal quantity = StrategyOrderFillSupport.resolvedFilledQuantity(order);
      BigDecimal fillPrice = StrategyOrderFillSupport.resolvedFillPrice(order);
      if (order.side() == StrategyOrderSide.BUY) {
        BigDecimal runningCost = averageCost.multiply(positionQty).add(fillPrice.multiply(quantity));
        positionQty = positionQty.add(quantity);
        if (positionQty.compareTo(BigDecimal.ZERO) > 0) {
          averageCost = runningCost.divide(positionQty, 8, java.math.RoundingMode.HALF_UP);
        }
        continue;
      }
      com.neuralarc.service.SellBasis.Result basis = com.neuralarc.service.SellBasis.of(
          fillPrice, quantity, positionQty, averageCost,
          com.neuralarc.service.SellBasis.brokerAverageEntry(order));
      if (basis.isEmpty()) {
        continue;
      }
      BigDecimal realized = Monetary.round(basis.realized());
      java.time.Instant when = order.filledAt() != null ? order.filledAt() : order.submittedAt();
      boolean isToday = when != null
          && java.time.LocalDate.ofInstant(when, java.time.ZoneId.systemDefault()).equals(today);
      result.add(new WorkspaceAccounting.RealizedSell(workspaceId, realized, isToday));
      // Only the tracked shares leave the tracked position, however the sale was priced.
      positionQty = positionQty.subtract(quantity.min(positionQty.max(BigDecimal.ZERO)));
      if (positionQty.compareTo(BigDecimal.ZERO) <= 0) {
        positionQty = BigDecimal.ZERO;
        averageCost = BigDecimal.ZERO;
      }
    }
    return result;
  }

  /**
   * Right-click a workspace tab to rename it or archive it (archive removes the tab, keeps records).
   */
  private void installWorkspaceTabContextMenu() {
    strategyTabs.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent event) {
        maybeShow(event);
      }

      @Override
      public void mouseReleased(MouseEvent event) {
        maybeShow(event);
      }

      private void maybeShow(MouseEvent event) {
        if (!event.isPopupTrigger()) {
          return;
        }
        int tabIndex = strategyTabs.indexAtLocation(event.getX(), event.getY());
        String workspaceId = strategyWorkspaceTabs.workspaceIdAt(tabIndex);
        if (workspaceId == null) {
          return; // All Stocks / Trade History are not editable.
        }
        showWorkspaceTabMenu(event, workspaceId, strategyTabs.getTitleAt(tabIndex));
      }
    });
  }

  private void showWorkspaceTabMenu(MouseEvent event, String workspaceId, String currentName) {
    JPopupMenu menu = new JPopupMenu();
    JMenuItem rename = new JMenuItem("Rename Strategy");
    rename.setFont(BASE_FONT.deriveFont(Font.PLAIN, 12f));
    rename.addActionListener(e -> {
      String newName = JOptionPane.showInputDialog(this, "Rename strategy workspace:", currentName);
      if (newName != null && !newName.isBlank()) {
        strategyWorkspaceTabs.renameWorkspace(workspaceId, newName.trim());
        userActionLog.completed("Rename Workspace", newName.trim());
      }
    });
    JMenuItem delete = new JMenuItem("Delete Strategy");
    delete.setFont(BASE_FONT.deriveFont(Font.PLAIN, 12f));
    delete.addActionListener(e -> deleteWorkspaceTab(workspaceId, currentName));
    menu.add(rename);
    menu.add(delete);
    menu.show(event.getComponent(), event.getX(), event.getY());
  }

  // Delete a workspace only when the currently visible grid is empty. Hidden history/archived
  // records are moved back to All Stocks/Trade History first so they do not block deleting an
  // apparently empty workspace tab.
  private void deleteWorkspaceTab(String workspaceId, String currentName) {
    List<Strategy> assignedStrategies = workspaceService.strategiesIn(workspaceId);
    long visibleStrategyCount = assignedStrategies.stream()
        .map(this::managedStrategyFor)
        .filter(this::includeInCurrentStrategiesTab)
        .count();
    if (visibleStrategyCount > 0) {
      JOptionPane.showMessageDialog(this,
          "Can't delete \"" + currentName + "\": it still owns " + visibleStrategyCount
              + " visible stock" + (visibleStrategyCount == 1 ? "" : "s")
              + ". Move or remove the visible rows first.",
          "Delete Strategy Workspace", JOptionPane.WARNING_MESSAGE);
      return;
    }
    int hiddenStrategyCount = assignedStrategies.size();
    int choice = JOptionPane.showConfirmDialog(this,
        "Delete the empty strategy workspace \"" + currentName + "\"?"
            + (hiddenStrategyCount > 0
            ? "\n\n" + hiddenStrategyCount + " hidden history/archived record"
              + (hiddenStrategyCount == 1 ? " is" : "s are")
              + " assigned to this workspace and will be moved back to All Stocks/Trade History."
            : ""),
        "Delete Strategy Workspace", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
    if (choice != JOptionPane.YES_OPTION) {
      return;
    }
    if (hiddenStrategyCount > 0) {
      for (Strategy strategy : assignedStrategies) {
        strategy.setWorkspaceId(null);
        strategyRepository.save(strategy);
      }
    }
    WorkspaceService.DeleteResult result = strategyWorkspaceTabs.deleteWorkspace(workspaceId);
    if (result == WorkspaceService.DeleteResult.DELETED) {
      // A deleted workspace's Gap Rocket schedule must not keep scanning (and auto-executing) for it.
      gapAndGoCoordinator.cancelScheduleForWorkspace(workspaceId);
      smartPicksWorkspaceCoordinator.cancelScheduleForWorkspace(workspaceId);
      log("[WORKSPACE] Deleted empty strategy workspace '" + currentName + "'.");
      userActionLog.completed("Delete Workspace", currentName);
    }
  }

  private ManagedStrategy managedStrategyFor(Strategy strategy) {
    if (strategy == null) {
      return null;
    }
    return strategies.stream()
        .filter(entry -> entry.strategy.id().equals(strategy.id()))
        .findFirst()
        .orElseGet(() -> new ManagedStrategy(strategy));
  }

  private void createWorkspaceFromTemplate(StrategyWorkspaceTemplate template) {
    String name = template.name();
    if (template.isCustom()) {
      name = JOptionPane.showInputDialog(this, "Name your strategy workspace:", "Custom Strategy");
      if (name == null || name.isBlank()) {
        userActionLog.canceled("Create Workspace");
        return;
      }
      name = name.trim();
    }
    StrategyWorkspace workspace = strategyWorkspaceTabs.createOrSelect(name, template.isCustom() ? null : template.code());
    log("[WORKSPACE] Opened strategy workspace '" + workspace.name() + "' (" + workspace.code() + ") in "
        + selectedModeLabel() + " mode.");
    userActionLog.completed("Open Workspace", workspace.name());
    refreshStrategyWorkspaceEmptyState();
  }

  // Context-menu action: move the strategy in the clicked row into a workspace (or back to
  // All Stocks when workspaceId is null), then refresh the grid, filter, and tab counts.
  private void assignStrategyRowToWorkspace(String workspaceId, int viewRow) {
    if (viewRow < 0) {
      return;
    }
    int modelRow = strategyTable.convertRowIndexToModel(viewRow);
    if (modelRow < 0 || modelRow >= strategies.size()) {
      return;
    }
    ManagedStrategy entry = strategies.get(modelRow);
    if (!workspaceService.assignStrategy(entry.strategy.id(), workspaceId)) {
      return;
    }
    syncStrategiesFromRepository();
    refreshStrategyTableData();
    applyCurrentStrategiesRowFilter();
    refreshCurrentStrategiesHeading();
    String workspaceName = workspaceId == null
        ? "All Stocks"
        : workspaceService.findById(workspaceId).map(StrategyWorkspace::name).orElse("workspace");
    log("[" + entry.strategy.symbol() + "] Moved to " + workspaceName + ".");
    userActionLog.completed("Move to Workspace", entry.strategy.symbol() + " -> " + workspaceName);
  }

  private String currentStrategiesHeadingText() {
    // Renamed from "Current Strategies" to "All Stocks" for the strategy-workspaces feature:
    // this tab always shows every strategy in the active mode regardless of workspace
    // assignment, preserving the existing stock-centric view for current installations.
    long currentCount = strategies.stream().filter(this::includeInCurrentStrategiesTab).count();
    return "All Stocks - " + selectedModeLabel() + " (" + currentCount + ")";
  }

  private String workspaceStrategiesHeadingText(StrategyWorkspace workspace) {
    if (workspace == null) {
      return "Workspace [ 0 ]";
    }
    long count = currentStrategiesStockCountInWorkspace(workspace.id());
    return workspace.name() + " [ " + count + " ]";
  }

  private String tradeHistoryHeadingText() {
    long historyCount = tradeHistoryStockCount();
    return "Trade History - " + selectedModeLabel() + " (" + historyCount + ")";
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


  private int compareNumericCells(Object left, Object right) {
    BigDecimal leftValue = sortableNumericValue(left);
    BigDecimal rightValue = sortableNumericValue(right);
    if (leftValue == null && rightValue == null) {
      return 0;
    }
    if (leftValue == null) {
      return 1;
    }
    if (rightValue == null) {
      return -1;
    }
    return leftValue.compareTo(rightValue);
  }

  private String formatCpuUsageText() {
    return systemMetricsPresenter.formatCpuUsageText();
  }

  private String formatMemoryUsageText() {
    return systemMetricsPresenter.formatMemoryUsageText();
  }

  private void ensureAnalyticsPublisher() {
    if (analyticsPublisher == null) {
      boolean analyticsAllowed = AppMetadata.analyticsEnabled();
      TelemetryConfig telemetryConfig = new TelemetryConfig(
          analyticsAllowed,
          settingsDialog.getEndpoint(),
          null,
          AppMetadata.displayVersion()
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
    if (updateAvailableFlashTimer != null) {
      updateAvailableFlashTimer.stop();
    }
    if (capturePortfolioPulseTimer != null) {
      capturePortfolioPulseTimer.stop();
    }
    bottomStatusBars.shutdown();
    portfolioCaptureRuns.shutdown();
    smartPicksWorkspaceCoordinator.shutdown();
    if (accountEquitySampleTimer != null) {
      accountEquitySampleTimer.stop();
    }
    portfolioEmailScheduler.stop();
    strategyPollingTimer.stop();
    uiPollingExecutor.shutdownNow();
    shutdownPollingServices();
    if (asyncLogUploadService != null) {
      asyncLogUploadService.close();
      asyncLogUploadService = null;
    }
    flushLogsToFile();
    if (analyticsPublisher != null) {
      analyticsPublisher.publish(new AnalyticsEvent("APP_EXIT"));
      analyticsPublisher.shutdown();
    }
  }

  /**
   * The red header-button look shared by KILL SWITCH and Stop Liquidations.
   */
  private void styleHeaderDangerButton(JButton button) {
    button.setFocusPainted(false);
    button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
    button.setFont(FontLoader.ui(Font.BOLD, 11f));
    button.setForeground(Color.WHITE);
    button.setBackground(new Color(180, 20, 20));
    button.setOpaque(true);
    button.setContentAreaFilled(true);
    javax.swing.border.Border inner = new EmptyBorder(4, 10, 4, 10);
    javax.swing.border.Border pressedInner = new EmptyBorder(3, 9, 3, 9);
    button.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(new Color(120, 10, 10), 1, true),
        inner
    ));
    button.setMargin(new java.awt.Insets(4, 10, 4, 10));
    button.addMouseListener(new MouseAdapter() {
      private static final Color BASE_BG = new Color(180, 20, 20);
      private static final Color BASE_BORDER = new Color(120, 10, 10);
      private static final Color HOVER_BG = new Color(210, 32, 32);
      private static final Color HOVER_BORDER = new Color(148, 15, 15);
      private static final Color PRESS_BG = new Color(148, 14, 14);
      private static final Color PRESS_BORDER = new Color(95, 6, 6);

      @Override
      public void mouseEntered(MouseEvent e) {
        if (button.isEnabled()) {
          button.setBackground(HOVER_BG);
          button.setBorder(BorderFactory.createCompoundBorder(
              BorderFactory.createLineBorder(HOVER_BORDER, 1, true),
              inner));
        }
      }

      @Override
      public void mouseExited(MouseEvent e) {
        button.setBackground(BASE_BG);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BASE_BORDER, 1, true),
            inner));
      }

      @Override
      public void mousePressed(MouseEvent e) {
        if (button.isEnabled() && e.getButton() == MouseEvent.BUTTON1) {
          button.setBackground(PRESS_BG);
          button.setBorder(BorderFactory.createCompoundBorder(
              BorderFactory.createLineBorder(PRESS_BORDER, 2, true),
              pressedInner));
        }
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        if (button.contains(e.getPoint()) && button.isEnabled()) {
          button.setBackground(HOVER_BG);
          button.setBorder(BorderFactory.createCompoundBorder(
              BorderFactory.createLineBorder(HOVER_BORDER, 1, true),
              inner));
        } else {
          button.setBackground(BASE_BG);
          button.setBorder(BorderFactory.createCompoundBorder(
              BorderFactory.createLineBorder(BASE_BORDER, 1, true),
              inner));
        }
      }
    });
  }

  private void killAllStrategies() {
    killSwitchController.activate();
  }

  private void updateHeaderModeStatus(BrokerType brokerType) {
    BrokerType effectiveBroker = brokerType == null ? BrokerType.ALPACA : brokerType;
    headerStatus.setText(connectionModeStatus(effectiveBroker));
    boolean blinkLiveAlpaca = effectiveBroker == BrokerType.ALPACA && selectedViewMode == StrategyMode.LIVE;
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
    if (selectedViewMode != StrategyMode.LIVE) {
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
    if (isGapRocketWorkspaceStrategy(strategy) && !hasFilledBuyOrder(strategy.id())) {
      return new Position(strategy.symbol());
    }
    // Allocate against every stored strategy: on its own this row would look like the symbol's
    // only owner and take the whole broker position.
    return loadPositionSnapshotsForStrategies(List.of(strategy), strategyRepository.findAll())
        .getOrDefault(strategy.id(), new Position(strategy.symbol()));
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

  private void tradeLog(String message) {
    String timestamp = formatLogTimestamp();
    SwingUtilities.invokeLater(() -> {
      String logEntry = "[" + timestamp + "] " + message + System.lineSeparator();
      appendLogEntry(logEntry);
      rotatingLogWriter.append(RotatingLogWriter.LogType.TRADE, logEntry);
    });
  }

  private void logEmailStatus(String eventType, String symbol, String recipientEmail, String status, String error) {
    String maskedRecipient = identityService.maskEmail(recipientEmail);
    String detail = error == null || error.isBlank() ? "" : " | error=" + error;
    log("[EMAIL][" + safeLogToken(symbol) + "] type=" + safeLogToken(eventType)
        + " status=" + safeLogToken(status)
        + " recipient=" + maskedRecipient
        + detail);
  }

  private void logRulesAnalyzed(String symbol, List<StrategyEngine.RuleOutcome> outcomes) {
    if (outcomes == null || outcomes.isEmpty()) {
      return;
    }
    String summary = outcomes.stream()
        .map(StrategyEngine.RuleOutcome::toString)
        .collect(Collectors.joining(" | "));
    log("[RULES][" + safeLogToken(symbol) + "] analyzed=" + summary);
  }

  private String safeLogToken(String value) {
    return value == null || value.isBlank() ? "-" : value.trim();
  }

  /**
   * The log's filter field (applied on Enter) and a clear icon, in one row above the log.
   */
  private JComponent createEventLogToolbar() {
    JTextField filterField = new JTextField(22);
    filterField.setFont(BASE_FONT.deriveFont(Font.PLAIN, 11f));
    filterField.putClientProperty("JTextField.placeholderText", "Filter logs, then press Enter (e.g. Position)");
    filterField.putClientProperty("JTextField.showClearButton", true);
    filterField.setToolTipText(TooltipStyler.text("Shows only log lines containing this text. Press Enter to apply;"
        + " clear the field and press Enter to show everything again.", 320));
    filterField.addActionListener(event -> eventLogView.setFilter(filterField.getText()));
    // The field's own clear button empties the text: show everything again straight away.
    filterField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
      @Override
      public void insertUpdate(javax.swing.event.DocumentEvent e) {
      }

      @Override
      public void changedUpdate(javax.swing.event.DocumentEvent e) {
      }

      @Override
      public void removeUpdate(javax.swing.event.DocumentEvent e) {
        if (filterField.getText().isEmpty() && !eventLogView.filter().isEmpty()) {
          SwingUtilities.invokeLater(() -> eventLogView.setFilter(""));
        }
      }
    });
    JButton clear = new JButton(SvgIconLoader.load("icons/delete.svg", 14));
    clear.setFocusPainted(false);
    clear.setToolTipText(TooltipStyler.text("Clear the log on screen. The log files on disk are kept.", 280));
    clear.getAccessibleContext().setAccessibleName("Clear logs");
    clear.putClientProperty("JButton.buttonType", "toolBarButton");
    clear.addActionListener(event -> eventLogView.clear());
    JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
    toolbar.setOpaque(false);
    toolbar.add(filterField);
    toolbar.add(clear);
    return toolbar;
  }

  private void appendLogEntry(String logEntry) {
    eventLogView.append(logEntry);
  }

  private Color logEntryColor(String logEntry, int shownIndex) {
    return switch (EventLogSeverity.tone(logEntry)) {
      case FAILURE -> LOG_LINE_FAILURE;
      case WARNING -> LOG_LINE_WARNING;
      case SUCCESS -> LOG_LINE_SUCCESS;
      case PROCESSING -> LOG_LINE_PROCESSING;
      case INFO -> (shownIndex % 2 == 0) ? LOG_LINE_EVEN : LOG_LINE_ODD;
    };
  }

  private boolean isFailureLogEntry(String logEntry) {
    return EventLogSeverity.isFailure(logEntry);
  }

  private void flushLogsToFile() {
    try {
      if (!pendingLogWrites.isEmpty()) {
        rotatingLogWriter.append(RotatingLogWriter.LogType.APP, pendingLogWrites.toString());
      }
      rotatingLogWriter.flush();
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
          ),
          tradeHistoryGroupBy == TradeHistoryGroupBy.SYMBOL
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

  private Border tableCellBorder(int top, int left, int bottom, int right) {
    return BorderFactory.createEmptyBorder(top, left, bottom, right);
  }


  private final class StatusRowRenderer extends DefaultTableCellRenderer {

    @Override
    public Component getTableCellRendererComponent(
        JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      setOpaque(true);
      int modelRow = table.convertRowIndexToModel(row);
      if (modelRow >= 0 && modelRow < strategies.size()) {
        boolean paused = strategies.get(modelRow).isPaused();
        if (isSelected) {
          setBackground(TABLE_SELECTION_BG);
          setForeground(TABLE_SELECTION_FG);
        } else {
          setBackground(row % 2 == 0 ? TABLE_ROW_BG_EVEN : TABLE_ROW_BG_ODD);
          if (column == StrategyGridLayoutPresenter.STATUS_COLUMN_INDEX) {
            String latestOrderStatus = BrokerOrderStatusUtil.normalize(strategies.get(modelRow).strategy.latestOrderStatus());
            if ("rejected".equals(latestOrderStatus)) {
              setForeground(STATUS_ERR);
              setFont(getFont().deriveFont(Font.BOLD));
            } else if (strategies.get(modelRow).strategy.status() == StrategyStatus.ARCHIVED) {
              setForeground(ThemeColors.color("NeuralArc.statusArchived", new Color(108, 117, 125)));
            } else if (strategies.get(modelRow).strategy.pauseReason() == PauseReason.SYSTEM_ERROR) {
              setForeground(STATUS_ERR);
            } else {
              setForeground(paused ? STATUS_TEXT_PAUSED : STATUS_TEXT_RUNNING);
            }
          } else if (column == STRATEGY_SHARES_COLUMN
              && strategies.get(modelRow).untrackedShares() > 0) {
            // The broker holds shares this row's own orders never bought: colour it so a quantity
            // that disagrees with the timeline is noticed rather than read as normal.
            setForeground(STATUS_TEXT_PAUSED);
            setFont(getFont().deriveFont(Font.BOLD));
          } else if (column == STRATEGY_STOCK_PRICE_COLUMN) {
            // Green when current price is above today's open; amber when below.
            BigDecimal lastPrice = strategies.get(modelRow).cachedPosition().getLastPrice();
            com.neuralarc.model.MarketBar bar = strategies.get(modelRow).cachedDailyBar();
            BigDecimal openPrice = bar == null ? null : bar.open();
            if (lastPrice != null && lastPrice.compareTo(BigDecimal.ZERO) > 0
                && openPrice != null && openPrice.compareTo(BigDecimal.ZERO) > 0) {
              int cmp = lastPrice.compareTo(openPrice);
              setForeground(cmp > 0 ? STATUS_TEXT_RUNNING : cmp < 0 ? STATUS_TEXT_PAUSED : table.getForeground());
            } else {
              setForeground(table.getForeground());
            }
          } else if (column == 1) {
            Object pnlValue = table.getModel().getValueAt(modelRow, STRATEGY_PNL_COLUMN);
            setForeground(PnlCellStyleSupport.foregroundFor(pnlValue, table.getForeground()));
          } else if (column == 13) {
            setForeground(entrySourceTextColor(value, table.getForeground()));
          } else {
            setForeground(table.getForeground());
          }
        }
      }
      if (!(column == StrategyGridLayoutPresenter.STATUS_COLUMN_INDEX
          && modelRow >= 0
          && modelRow < strategies.size()
          && "rejected".equals(BrokerOrderStatusUtil.normalize(strategies.get(modelRow).strategy.latestOrderStatus())))) {
        setFont(getFont().deriveFont(Font.PLAIN));
      }
      setToolTipText(null);
      if (column == STRATEGY_SHARES_COLUMN && modelRow >= 0 && modelRow < strategies.size()) {
        ManagedStrategy sharesRow = strategies.get(modelRow);
        if (sharesRow.untrackedShares() > 0) {
          setToolTipText(TooltipStyler.text(UntrackedShares.tooltip(sharesRow.strategy.symbol(),
              sharesRow.cachedPosition().getTotalShares(), sharesRow.untrackedShares()), 360));
        }
      }
      setHorizontalAlignment(alignmentForColumn(column));
      // Border is managed by prepareRenderer for selected rows (accent stripe on col 0);
      // for unselected rows set the standard inset border here.
      if (!isSelected) {
        setBorder(tableCellBorder(0, 10, 0, 10));
      }
      return this;
    }

    private int alignmentForColumn(int column) {
      // Numeric price and P&L columns are right-aligned; text columns are left-aligned.
      return switch (column) {
        case 2, 3, 4, 5, 6, 7, 8, 9 -> RIGHT;
        default -> LEFT;
      };
    }

    private Color entrySourceTextColor(Object value, Color fallback) {
      if (value == null) {
        return fallback;
      }
      String source = String.valueOf(value).toLowerCase(Locale.ROOT);
      if (source.contains("gainer")) {
        return ThemeColors.color("NeuralArc.entryGainers", new Color(34, 139, 34));
      }
      if (source.contains("loser")) {
        return ThemeColors.color("NeuralArc.entryLosers", new Color(210, 130, 20));
      }
      return fallback;
    }
  }

  private final class UnrealizedPnLRenderer extends DefaultTableCellRenderer {

    @Override
    public Component getTableCellRendererComponent(
        JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      setOpaque(true);
      if (isSelected) {
        setBackground(TABLE_SELECTION_BG);
        setForeground(TABLE_SELECTION_FG);
      } else {
        setBackground(row % 2 == 0 ? TABLE_ROW_BG_EVEN : TABLE_ROW_BG_ODD);
        setForeground(PnlCellStyleSupport.foregroundFor(value, table.getForeground()));
        setBorder(tableCellBorder(0, 10, 0, 10));
      }
      setHorizontalAlignment(SwingConstants.LEFT);
      return this;
    }
  }

  private final class PollingBarRenderer extends JPanel implements TableCellRenderer {

    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final JLabel countdownLabel = new JLabel();
    private final JLabel refreshNowLabel = new JLabel("Refresh Now");

    private PollingBarRenderer() {
      super(new BorderLayout());
      setOpaque(true);
      setBorder(new EmptyBorder(4, 8, 4, 8));
      countdownLabel.setOpaque(false);
      countdownLabel.setFont(FontLoader.ui(Font.PLAIN, 10f));
      countdownLabel.setHorizontalAlignment(SwingConstants.LEFT);
      countdownLabel.setBorder(new EmptyBorder(0, 8, 0, 0));
      refreshNowLabel.setOpaque(false);
      refreshNowLabel.setFont(FontLoader.ui(Font.BOLD, 10f));
      refreshNowLabel.setHorizontalAlignment(SwingConstants.RIGHT);
      refreshNowLabel.setBorder(new EmptyBorder(0, 4, 0, 0));
      refreshNowLabel.setForeground(new Color(66, 133, 244));
      refreshNowLabel.setPreferredSize(new Dimension(PositionValidationHotspotLayout.REFRESH_NOW_WIDTH, 0));
      refreshNowLabel.setVisible(false);
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
      add(refreshNowLabel, BorderLayout.EAST);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      int modelRow = table.convertRowIndexToModel(row);
      ManagedStrategy strategy = strategies.get(modelRow);
      ensurePollingCountdownScheduled(strategy);
      long nowMillis = System.currentTimeMillis();
      PositionValidationCellPresenter.ViewModel validationViewModel = positionValidationCellPresenter.present(
          new PositionValidationCellPresenter.State(
              new PollingCellPresenter.PollingCellState(
                  strategy.strategy.status(),
                  isWaitingForFill(strategy.strategy),
                  strategy.isPaused(),
                  strategy.pauseLabel(),
                  strategy.pauseTooltip(),
                  strategy.pollInFlight,
                  isAutoPausedForClosedMarket(strategy),
                  strategy.pollIntervalMillis,
                  strategy.nextPollDueAtMillis,
                  isSelected
              ),
              strategyPollingService.isValidationWarningPaused(),
              strategyPollingService.activeValidationAttempt(),
              strategyPollingService.maxValidationAttemptsBeforePause(),
              strategy.lastValidationSuccessAtMillis
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
          nowMillis
      );
      PollingCellPresenter.PollingCellViewModel viewModel = validationViewModel.base();

      // Apply alternating row background colors
      if (isSelected) {
        setBackground(TABLE_SELECTION_BG);
      } else {
        setBackground(row % 2 == 0 ? TABLE_ROW_BG_EVEN : TABLE_ROW_BG_ODD);
      }
      setBorder(tableCellBorder(5, 10, 5, 10));
      boolean warningPaused = validationViewModel.lifecycleState() == PositionValidationCellPresenter.ValidationLifecycleState.WARNING_PAUSED;
      progressBar.setValue(warningPaused ? 0 : viewModel.progress());
      progressBar.setBackground(viewModel.trackBackground());
      progressBar.setForeground(warningPaused ? STATUS_TEXT_PAUSED : viewModel.progressForeground());
      countdownLabel.setForeground(warningPaused ? STATUS_TEXT_PAUSED : viewModel.labelForeground());
      countdownLabel.setText(warningPaused ? "Unable to validate" : viewModel.labelText());
      refreshNowLabel.setVisible(validationViewModel.showRefreshNowAffordance());
      String tooltipText = TooltipStyler.text(
          appendValidationHints(appendSessionHint(viewModel.tooltip(), strategy), validationViewModel));
      setToolTipText(tooltipText);
      progressBar.setToolTipText(tooltipText);
      countdownLabel.setToolTipText(tooltipText);
      refreshNowLabel.setToolTipText(tooltipText);
      return this;
    }
  }

  private final class ActionsRenderer extends JPanel implements TableCellRenderer {

    private final JButton chartButton = new JButton();
    private final JButton analyzeButton = new JButton();
    private final JButton editButton = new JButton();
    private final JButton toggleButton = new JButton();
    private final JButton sellButton = new JButton();
    private final JButton promoteButton = new JButton();
    private final JButton deleteButton = new JButton();

    private ActionsRenderer() {
      super(new FlowLayout(FlowLayout.CENTER, StrategyGridActionLayout.BUTTON_GAP, 0));
      setOpaque(true);
      setBorder(new EmptyBorder(5, 0, 0, 0));
      applyButtonIcon(chartButton, "icons/chart.svg", 13);
      applyButtonIcon(analyzeButton, "icons/actions.svg", 13);
      applyButtonIcon(editButton, "icons/edit.svg", 12);
      applyButtonIcon(toggleButton, "icons/pause.svg", 13);
      applyButtonIcon(sellButton, "icons/sell-position.svg", 13);
      applyButtonIcon(promoteButton, "icons/add-stock-strategy.svg", 13);
      applyButtonIcon(deleteButton, "icons/delete.svg", 13);
      styleIconOnlyActionButton(chartButton, new Color(38, 124, 128));
      styleIconOnlyActionButton(analyzeButton, new Color(92, 84, 150));
      styleIconOnlyActionButton(editButton, new Color(82, 101, 132));
      styleIconOnlyActionButton(toggleButton, new Color(180, 122, 42));
      styleIconOnlyActionButton(sellButton, new Color(71, 85, 105));
      styleIconOnlyActionButton(promoteButton, new Color(37, 99, 235));
      styleIconOnlyActionButton(deleteButton, new Color(148, 62, 78));
      add(chartButton);
      add(analyzeButton);
      add(editButton);
      add(toggleButton);
      add(sellButton);
      add(promoteButton);
      add(deleteButton);
      setActionButtonSize(chartButton, StrategyGridActionLayout.ICON_BUTTON_WIDTH);
      setActionButtonSize(analyzeButton, StrategyGridActionLayout.ICON_BUTTON_WIDTH);
      setActionButtonSize(editButton, StrategyGridActionLayout.ICON_BUTTON_WIDTH);
      setActionButtonSize(toggleButton, StrategyGridActionLayout.ICON_BUTTON_WIDTH);
      setActionButtonSize(sellButton, StrategyGridActionLayout.ICON_BUTTON_WIDTH);
      setActionButtonSize(promoteButton, StrategyGridActionLayout.PROMOTE_BUTTON_WIDTH);
      setActionButtonSize(deleteButton, StrategyGridActionLayout.ICON_BUTTON_WIDTH);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      int modelRow = table.convertRowIndexToModel(row);
      ManagedStrategy strategy = strategies.get(modelRow);
      StrategyActionsPresenter.StrategyActionsViewModel actionsViewModel = actionViewModelFor(strategy);
      removeAll();
      add(chartButton);
      add(analyzeButton);
      add(editButton);
      add(toggleButton);
      add(sellButton);
      if (actionsViewModel.promoteVisible()) {
        add(promoteButton);
      }
      add(deleteButton);
      toggleButton.setText("");
      String toggleIconPath = actionsViewModel.toggleIconPath();
      String currentToggleIconPath = (String) toggleButton.getClientProperty("toggleIconPath");
      if (currentToggleIconPath == null || !currentToggleIconPath.equals(toggleIconPath)) {
        applyButtonIcon(toggleButton, toggleIconPath, 13);
        toggleButton.putClientProperty("toggleIconPath", toggleIconPath);
      }
      styleIconOnlyActionButton(toggleButton, actionsViewModel.toggleColor());
      toggleButton.setEnabled(actionsViewModel.toggleEnabled());
      sellButton.setEnabled(actionsViewModel.sellEnabled());
      styleIconOnlyActionButton(sellButton, actionsViewModel.sellColor());
      promoteButton.setEnabled(actionsViewModel.promoteEnabled());
      promoteButton.setVisible(actionsViewModel.promoteVisible());
      styleIconOnlyActionButton(promoteButton, actionsViewModel.promoteColor());
      chartButton.setToolTipText(TooltipStyler.text(CHART_ACTION_TOOLTIP));
      analyzeButton.setToolTipText(TooltipStyler.text(ANALYZE_ACTION_TOOLTIP));
      editButton.setToolTipText(TooltipStyler.text("Edit strategy rules, limits, and settings."));
      toggleButton.setToolTipText(actionsViewModel.toggleEnabled()
          ? TooltipStyler.text("Run the shown action for this strategy: " + actionsViewModel.toggleText() + ".")
          : TooltipStyler.text("This action is currently unavailable for this strategy state."));
      sellButton.setToolTipText(actionsViewModel.sellEnabled()
          ? TooltipStyler.text("Sell the open position for this strategy.")
          : TooltipStyler.text("Sell is available only when Alpaca shows an open position for this strategy."));
      promoteButton.setToolTipText(actionsViewModel.promoteEnabled()
          ? TooltipStyler.text("Promote this PAPER strategy to LIVE.")
          : TooltipStyler.text("Promote is available only for eligible PAPER strategies."));
      deleteButton.setToolTipText(TooltipStyler.text("Delete this strategy from Current Strategies."));
      setBackground(selectionAwareRowColor(isSelected, row));
      setBorder(tableCellBorder(5, 0, 0, 0));
      return this;
    }
  }

  private void setActionButtonSize(JButton button, int width) {
    Dimension size = new Dimension(width, StrategyGridActionLayout.BUTTON_HEIGHT);
    button.setPreferredSize(size);
    button.setMinimumSize(size);
    button.setMaximumSize(size);
  }

  private StrategyActionsPresenter.StrategyActionsViewModel actionViewModelFor(ManagedStrategy strategy) {
    return strategyActionsPresenter.present(
        new StrategyActionsPresenter.StrategyActionsState(
            strategy.strategy.status(),
            strategy.strategy.pauseReason() == PauseReason.MANUAL_LIMIT_BUY_CANCELED
                || strategy.strategy.pauseReason() == PauseReason.USER_PAUSED,
            strategy.isPauseResumeBusy(),
            strategy.pauseResumeBusyText(),
            strategy.strategy.mode() == StrategyMode.PAPER,
            strategy.cachedPosition().getTotalShares() > 0,
            isMarketOpenForUi(),
            strategy.strategy.latestOrderStatus()
        )
    );
  }

  private String actionTooltipForHover(int viewRow, int mouseX) {
    int modelRow = strategyTable.convertRowIndexToModel(viewRow);
    if (modelRow < 0 || modelRow >= strategies.size()) {
      return null;
    }
    ManagedStrategy strategy = strategies.get(modelRow);
    StrategyActionsPresenter.StrategyActionsViewModel actionsViewModel = actionViewModelFor(strategy);
    return switch (actionAtMousePoint(viewRow, mouseX)) {
      case CHART -> TooltipStyler.text(CHART_ACTION_TOOLTIP);
      case ANALYZE -> TooltipStyler.text(ANALYZE_ACTION_TOOLTIP);
      case EDIT -> TooltipStyler.text("Edit strategy rules, limits, and settings.");
      case TOGGLE -> actionsViewModel.toggleEnabled()
          ? TooltipStyler.text("Run the shown action for this strategy: " + actionsViewModel.toggleText() + ".")
          : TooltipStyler.text("This action is currently unavailable for this strategy state.");
      case SELL -> actionsViewModel.sellEnabled()
          ? TooltipStyler.text("Sell the open position for this strategy.")
          : TooltipStyler.text("Sell is available only when Alpaca shows an open position for this strategy.");
      case PROMOTE -> actionsViewModel.promoteEnabled()
          ? TooltipStyler.text("Promote this PAPER strategy to LIVE.")
          : TooltipStyler.text("Promote is available only for eligible PAPER strategies.");
      case DELETE -> TooltipStyler.text("Delete this strategy from Current Strategies.");
      case NONE -> null;
    };
  }

  private StrategyGridActionLayout.Action actionAtMousePoint(int viewRow, int mouseX) {
    int modelRow = strategyTable.convertRowIndexToModel(viewRow);
    if (modelRow < 0 || modelRow >= strategies.size()) {
      return StrategyGridActionLayout.Action.NONE;
    }
    ManagedStrategy strategy = strategies.get(modelRow);
    boolean promoteVisible = actionViewModelFor(strategy).promoteVisible();
    java.awt.Rectangle cellRect = strategyTable.getCellRect(viewRow, StrategyGridLayoutPresenter.ACTIONS_COLUMN_INDEX, false);
    int xInCell = mouseX - cellRect.x;
    return StrategyGridActionLayout.actionAt(cellRect.width, xInCell, promoteVisible);
  }

  private boolean refreshNowHotspotAtMousePoint(int viewRow, int mouseX) {
    int modelRow = strategyTable.convertRowIndexToModel(viewRow);
    if (modelRow < 0 || modelRow >= strategies.size()) {
      return false;
    }
    boolean showRefreshNow = strategyPollingService.isValidationWarningPaused();
    java.awt.Rectangle cellRect = strategyTable.getCellRect(viewRow, StrategyGridLayoutPresenter.POLLING_COLUMN_INDEX, false);
    int xInCell = mouseX - cellRect.x;
    return PositionValidationHotspotLayout.isRefreshNowHotspot(cellRect.width, xInCell, showRefreshNow);
  }

  private Color selectionAwareRowColor(boolean selected, int row) {
    if (selected) {
      return TABLE_SELECTION_BG;
    }
    return row % 2 == 0 ? TABLE_ROW_BG_EVEN : TABLE_ROW_BG_ODD;
  }

  private static final String ANALYZE_ACTION_TOOLTIP =
      "Auto Analyze this stock: opens its strategy on the Auto Analyze tab and runs the analysis,"
          + " so you can apply a recommendation and save.";
  private static final String CHART_ACTION_TOOLTIP =
      "Open the price chart: moving averages, RSI, volume, MACD and this strategy's own levels, "
          + "with a plain-language guide to each part.";

  /**
   * Only a row holding shares that are under water has a loss worth working down.
   */
  private boolean rowHasLosingPosition(int viewRow) {
    if (viewRow < 0 || viewRow >= strategyTable.getRowCount()) {
      return false;
    }
    Position position = strategies.get(strategyTable.convertRowIndexToModel(viewRow)).cachedPosition();
    return position.getTotalShares() > 0
        && position.getLastPrice().compareTo(BigDecimal.ZERO) > 0
        && position.unrealizedPnl().signum() < 0;
  }

  /**
   * Plans a way out of a losing position: a measured add low enough to pull the average cost down, then an exit the last month of prices supports. Bars are read off the EDT, and
   * nothing reaches the broker until the operator presses Review and Execute and submits the ticket that opens.
   */
  private void minimizeLossImpact(int viewRow) {
    if (!rowHasLosingPosition(viewRow)) {
      return;
    }
    if (!connectionOk || runtimeApiKey.isBlank()) {
      JOptionPane.showMessageDialog(this,
          selectedModeLabel() + " Alpaca credentials are required to read the last month of prices.",
          "Minimize Loss Impact", JOptionPane.WARNING_MESSAGE);
      return;
    }
    ManagedStrategy entry = strategies.get(strategyTable.convertRowIndexToModel(viewRow));
    Strategy strategy = entry.strategy;
    Position position = entry.cachedPosition();
    int shares = position.getTotalShares();
    BigDecimal averageCost = position.getAverageCost();
    BigDecimal currentPrice = position.getLastPrice();
    new SwingWorker<LossRecoveryPlan.Plan, Void>() {
      @Override
      protected LossRecoveryPlan.Plan doInBackground() throws Exception {
        java.time.LocalDate end = java.time.LocalDate.now();
        java.util.List<com.neuralarc.model.MarketBar> bars =
            new HttpAlpacaMarketDataApi(runtimeApiKey, runtimeApiSecret)
                .getDailyBars(strategy.symbol(), end.minusDays(LOSS_RECOVERY_LOOKBACK_DAYS), end);
        // Price the add at today's low — a price the market has actually paid today, so it can fill.
        return LossRecoveryPlan.forPosition(shares, averageCost, currentPrice, bars, BigDecimal.ZERO,
            com.neuralarc.analytics.RecentLow.sessionLow(bars, end));
      }

      @Override
      protected void done() {
        LossRecoveryPlan.Plan plan;
        try {
          plan = get();
        } catch (Exception ex) {
          log("[" + strategy.symbol() + "] Minimize loss impact failed: " + ex.getMessage());
          JOptionPane.showMessageDialog(TradingFrame.this,
              "Could not read prices for " + strategy.symbol() + ": " + ex.getMessage(),
              "Minimize Loss Impact", JOptionPane.ERROR_MESSAGE);
          return;
        }
        LossRecoveryDialog dialog = new LossRecoveryDialog(TradingFrame.this, strategy.symbol(), shares,
            averageCost, currentPrice, plan);
        dialog.setVisible(true);
        if (dialog.wasExecuted() && plan.feasible()) {
          executeLossRecoveryPlan(entry, plan);
        }
      }
    }.execute();
  }

  /**
   * Opens the plan's buy as an order ticket to review: the shares it worked out, priced at today's low, with the time in force saved in Settings. Every field can still be changed,
   * and nothing reaches the broker until the operator submits it there.
   */
  private void executeLossRecoveryPlan(ManagedStrategy entry, LossRecoveryPlan.Plan plan) {
    Strategy strategy = entry.strategy;
    String action = "Minimize Loss Impact " + strategy.symbol();
    int suggestedShares = Math.max(1, plan.addShares());
    Optional<ManualLimitBuySelection> selection = ManualLimitBuyDialog.show(
        this,
        strategy,
        entry.cachedPosition().getLastPrice(),
        settingsDialog.appliedManualBuyTimeInForce(),
        suggestedShares,
        plan.addLimitPrice(),
        lossRecoveryTicketIntro(strategy, plan, suggestedShares)
    );
    if (selection.isEmpty()) {
      userActionLog.canceled(action);
      return;
    }

    ManualLimitBuySelection order = selection.get();
    userActionLog.started(action);
    new SwingWorker<StrategyService.StrategyCreationResult, Void>() {
      @Override
      protected StrategyService.StrategyCreationResult doInBackground() {
        return buyMoreAtLimit(strategy, order.quantity(), order.limitPrice(),
            order.repositionAfterExpiry(), order.timeInForce());
      }

      @Override
      protected void done() {
        StrategyService.StrategyCreationResult result;
        try {
          result = get();
        } catch (Exception ex) {
          result = StrategyService.StrategyCreationResult.failed(ex.getMessage());
        }
        if (result.success()) {
          log("[" + strategy.symbol() + "] Loss recovery buy submitted: " + order.quantity()
              + " at " + order.limitPrice().toPlainString() + " (" + order.timeInForce()
              + "). The plan's exit at " + plan.exitPrice().toPlainString() + " was not placed.");
          userActionLog.completed(action, "Limit buy submitted for " + order.quantity() + " share(s).");
        } else {
          userActionLog.failed(action, result.error());
          JOptionPane.showMessageDialog(TradingFrame.this,
              "Could not submit the buy for " + strategy.symbol() + ": " + result.error(),
              "Minimize Loss Impact", JOptionPane.ERROR_MESSAGE);
        }
        syncStrategiesFromRepository();
        refreshStrategyTableData();
        applyCurrentStrategiesRowFilter();
        updateStatusBar();
      }
    }.execute();
  }

  /**
   * Opening line of the loss-recovery ticket: what this buy is for, and what it deliberately leaves out.
   */
  private String lossRecoveryTicketIntro(Strategy strategy, LossRecoveryPlan.Plan plan, int shares) {
    return "<html><body style='width:380px'>"
        + "<b>Minimize loss impact on " + strategy.symbol() + "</b><br><br>"
        + "Buy " + shares + " share(s) at <b>$" + plan.addLimitPrice().toPlainString()
        + "</b> — today's low — bringing the average cost to $"
        + plan.newAverageCost().toPlainString() + ".<br><br>"
        + "The plan's exit at $" + plan.exitPrice().toPlainString()
        + " is <b>not</b> placed here; only the buy below is submitted."
        + "<br><br><span style='color:#667085'>Every field can be changed before submitting. "
        + "A GTC order never expires, so auto reposition only applies to a DAY order.</span>"
        + "</body></html>";
  }

  /**
   * Open losing positions in the viewed mode, with how long each has been held.
   */
  private java.util.List<LossHarvesting.Position> lossHarvestingPositions() {
    java.util.List<LossHarvesting.Position> positions = new java.util.ArrayList<>();
    for (ManagedStrategy entry : strategies) {
      if (entry.strategy.mode() != selectedViewMode) {
        continue;
      }
      Position position = entry.cachedPosition();
      if (position.getTotalShares() <= 0 || position.unrealizedPnl().signum() >= 0) {
        continue;
      }
      String workspaceLabel = entry.strategy.workspaceId() == null
          ? "Unassigned"
          : workspaceService.findById(entry.strategy.workspaceId())
              .map(StrategyWorkspace::name).orElse("Unassigned");
      double percent = position.totalInvested().signum() > 0
          ? position.unrealizedPnl().multiply(BigDecimal.valueOf(100))
          .divide(position.totalInvested(), 2, java.math.RoundingMode.HALF_UP).doubleValue()
          : 0.0;
      // A strategy armed to buy back would trigger the wash-sale rule on a harvested loss.
      boolean repurchaseArmed = entry.strategy.lossBuyLevelsEnabled() || entry.strategy.restartAfterExitEnabled();
      positions.add(new LossHarvesting.Position(entry.strategy.symbol(), workspaceLabel,
          position.getTotalShares(), position.unrealizedPnl(), percent,
          heldDays(entry.strategy.id()), repurchaseArmed));
    }
    return positions;
  }

  /**
   * Days since this strategy's first buy filled, or 0 when no fill is recorded.
   */
  private long heldDays(String strategyId) {
    java.time.Instant first = null;
    for (StrategyOrder order : strategyOrderRepository.findByStrategyId(strategyId)) {
      if (order.side() != StrategyOrderSide.BUY
          || StrategyOrderFillSupport.resolvedFilledQuantity(order).signum() <= 0) {
        continue;
      }
      java.time.Instant when = order.filledAt() != null ? order.filledAt() : order.submittedAt();
      if (when != null && (first == null || when.isBefore(first))) {
        first = when;
      }
    }
    return first == null ? 0L : java.time.temporal.ChronoUnit.DAYS.between(
        java.time.LocalDate.ofInstant(first, java.time.ZoneId.systemDefault()), java.time.LocalDate.now());
  }

  /**
   * Profit booked by sells that filled this calendar year, across the viewed mode.
   */
  private BigDecimal realizedGainsThisYear() {
    int year = java.time.LocalDate.now().getYear();
    BigDecimal realized = BigDecimal.ZERO;
    for (ManagedStrategy entry : strategies) {
      if (entry.strategy.mode() != selectedViewMode) {
        continue;
      }
      realized = realized.add(LossHarvesting.realizedInYear(fillsForStrategy(entry.strategy.id()), year));
    }
    return Monetary.round(realized);
  }

  private java.util.List<LossHarvesting.Fill> fillsForStrategy(String strategyId) {
    java.util.List<LossHarvesting.Fill> fills = new java.util.ArrayList<>();
    for (StrategyOrder order : strategyOrderRepository.findByStrategyId(strategyId)) {
      if (order.status() != StrategyOrderStatus.FILLED && order.status() != StrategyOrderStatus.PARTIALLY_FILLED) {
        continue;
      }
      BigDecimal quantity = StrategyOrderFillSupport.resolvedFilledQuantity(order);
      java.time.Instant when = order.filledAt() != null ? order.filledAt() : order.submittedAt();
      if (quantity.signum() <= 0 || when == null) {
        continue;
      }
      fills.add(new LossHarvesting.Fill(order.side() == StrategyOrderSide.BUY, quantity,
          StrategyOrderFillSupport.resolvedFillPrice(order),
          java.time.LocalDate.ofInstant(when, java.time.ZoneId.systemDefault())));
    }
    return fills;
  }

  /**
   * Opens the stock chart for a grid row, with that strategy's levels drawn on it.
   */
  private void openStockChart(int viewRow) {
    if (viewRow < 0 || viewRow >= strategyTable.getRowCount()) {
      return;
    }
    openStockChart(strategies.get(strategyTable.convertRowIndexToModel(viewRow)));
  }

  private void openStockChart(ManagedStrategy entry) {
    if (entry == null || entry.strategy == null) {
      return;
    }
    String workspaceId = entry.strategy.workspaceId();
    String workspace = workspaceId == null || workspaceId.isBlank()
        ? "All Stocks"
        : workspaceService.findById(workspaceId).map(StrategyWorkspace::name).orElse("All Stocks");
    String context = workspace + "  ·  " + (entry.strategy.mode() == StrategyMode.LIVE ? "Live" : "Paper");
    StockChartOpener.open(this, entry, context,
        () -> connectionOk && !runtimeApiKey.isBlank()
            ? new HttpAlpacaMarketDataApi(runtimeApiKey, runtimeApiSecret)
            : null);
  }

  private boolean maybeShowStrategyGridCopyPopup(MouseEvent event) {
    if (!event.isPopupTrigger() && event.getButton() != MouseEvent.BUTTON3) {
      return false;
    }
    int viewRow = strategyTable.rowAtPoint(event.getPoint());
    int viewCol = strategyTable.columnAtPoint(event.getPoint());
    if (viewRow < 0 || viewCol < 0) {
      return true;
    }
    // Keep a multi-row selection when the click lands inside it; the menu reads the selection.
    if (!strategyTable.isRowSelected(viewRow)) {
      strategyTable.setRowSelectionInterval(viewRow, viewRow);
    }
    strategyTable.setColumnSelectionInterval(viewCol, viewCol);
    new StrategyGridContextMenu(
        strategyTable,
        BASE_FONT.deriveFont(Font.PLAIN, 12f),
        this::strategyGridRowText,
        this::copyTextToClipboard,
        this::buyMoreAtMarketPrice,
        this::buyMoreAtLimitPrice,
        this::sellStrategyAtMarketPlace,
        this::repositionExpiredStrategy,
        this::cancelPendingLimitBuyFromGrid,
        this::rowHasCancelablePendingLimitBuy,
        this::placePendingBaseBuyFromGrid,
        this::rowHasPendingBaseBuyPlacement,
        this::readjustLosingPendingBaseBuyFromGrid,
        this::rowHasAmberPendingBaseBuy,
        this::repositionStockFromHistory,
        this::rowCanRepositionFromHistory,
        () -> strategyWorkspaceTabs != null && strategyWorkspaceTabs.isHistorySelected(),
        () -> workspaceService.activeWorkspaces(selectedViewMode),
        this::assignStrategyRowToWorkspace,
        this::openStockChart,
        this::minimizeLossImpact,
        this::rowHasLosingPosition
    ).show(event);
    return true;
  }

  private void maybeShowTradeHistoryRowPopup(MouseEvent event) {
    if (!event.isPopupTrigger() && event.getButton() != MouseEvent.BUTTON3) {
      return;
    }
    int viewRow = filledOrdersTable.rowAtPoint(event.getPoint());
    int viewCol = filledOrdersTable.columnAtPoint(event.getPoint());
    if (viewRow < 0 || viewCol < 0) {
      return;
    }
    filledOrdersTable.setRowSelectionInterval(viewRow, viewRow);
    String symbol = historySymbolAtViewRow(viewRow);
    ManagedStrategy target = symbol == null ? null : historyRepositionTarget(symbol);
    JPopupMenu popup = new JPopupMenu();
    JMenu positionMenu = new JMenu("Position");
    positionMenu.setFont(BASE_FONT.deriveFont(Font.PLAIN, 12f));
    JMenuItem reposition = new JMenuItem("Reposition Stock");
    reposition.setFont(BASE_FONT.deriveFont(Font.PLAIN, 12f));
    reposition.setEnabled(target != null);
    reposition.addActionListener(e -> {
      if (target != null) {
        repositionStockFromHistoryEntry(target);
      }
    });
    positionMenu.add(reposition);
    popup.add(positionMenu);
    popup.show(event.getComponent(), event.getX(), event.getY());
  }

  private String tradeHistoryCellTooltip(MouseEvent event) {
    if (event == null) {
      return null;
    }
    int viewRow = filledOrdersTable.rowAtPoint(event.getPoint());
    int viewCol = filledOrdersTable.columnAtPoint(event.getPoint());
    if (viewRow < 0 || viewCol < 0) {
      return null;
    }
    int modelRow = filledOrdersTable.convertRowIndexToModel(viewRow);
    if (modelRow < 0 || modelRow >= filledOrderRows.size()) {
      return null;
    }
    Object value = filledOrdersTable.getValueAt(viewRow, viewCol);
    String text = value == null ? "" : String.valueOf(value).trim();
    if (text.isBlank() || "-".equals(text)) {
      return null;
    }
    if (viewCol == 9) {
      return TooltipStyler.text(text, 420);
    }
    HistoryTablePresenter.HistoryRow row = filledOrderRows.get(modelRow);
    if (row.style() == HistoryTablePresenter.HistoryRowStyle.SUBTOTAL && (viewCol == 3 || viewCol == 10)) {
      return TooltipStyler.text(text, 420);
    }
    return null;
  }

  private String historySymbolAtViewRow(int viewRow) {
    int modelRow = filledOrdersTable.convertRowIndexToModel(viewRow);
    if (modelRow < 0 || modelRow >= filledOrderRows.size()) {
      return null;
    }
    HistoryTablePresenter.HistoryRow row = filledOrderRows.get(modelRow);
    if (row.style() == HistoryTablePresenter.HistoryRowStyle.SUBTOTAL) {
      return null;
    }
    String symbol = row.symbol();
    if (symbol == null || symbol.isBlank()) {
      return null;
    }
    return symbol.trim().toUpperCase(Locale.ROOT);
  }

  private ManagedStrategy historyRepositionTarget(String symbol) {
    if (symbol == null || symbol.isBlank()) {
      return null;
    }
    return strategies.stream()
        .filter(entry -> entry != null && entry.strategy != null)
        .filter(entry -> entry.strategy.mode() == selectedViewMode)
        .filter(entry -> symbol.equalsIgnoreCase(entry.strategy.symbol()))
        .filter(this::isHistoryRepositionEligible)
        .sorted(Comparator.comparing(
            (ManagedStrategy entry) -> entry.strategy.updatedAt(),
            Comparator.nullsLast(Comparator.reverseOrder())))
        .findFirst()
        .orElse(null);
  }

  private void maybeShowStrategyHeaderCopyPopup(MouseEvent event) {
    if (!event.isPopupTrigger() && event.getButton() != MouseEvent.BUTTON3) {
      return;
    }
    int viewCol = strategyTable.getTableHeader().columnAtPoint(event.getPoint());
    if (viewCol < 0) {
      return;
    }
    String columnName = strategyTable.getColumnName(viewCol);
    JPopupMenu popup = new JPopupMenu();
    JMenuItem copyColumn = new JMenuItem("Copy Column Name to Clipboard");
    copyColumn.setFont(BASE_FONT.deriveFont(Font.PLAIN, 12f));
    copyColumn.addActionListener(e -> copyTextToClipboard(columnName));
    popup.add(copyColumn);
    popup.show(event.getComponent(), event.getX(), event.getY());
  }

  private String strategyGridRowText(int viewRow) {
    StringBuilder row = new StringBuilder();
    for (int col = 0; col < strategyTable.getColumnCount(); col++) {
      if (!row.isEmpty()) {
        row.append(" | ");
      }
      Object value = strategyTable.getValueAt(viewRow, col);
      row.append(strategyTable.getColumnName(col)).append(": ").append(value == null ? "" : value);
    }
    return row.toString();
  }

  private void copyTextToClipboard(String text) {
    Toolkit.getDefaultToolkit()
        .getSystemClipboard()
        .setContents(new StringSelection(text == null ? "" : text), null);
  }

  private BigDecimal sortableNumericValue(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof BigDecimal number) {
      return number;
    }
    String text = String.valueOf(value).trim();
    if (text.isBlank() || "-".equals(text)) {
      return null;
    }
    text = text.replace(",", "");
    if (text.endsWith("%")) {
      text = text.substring(0, text.length() - 1).trim();
    }
    if (text.startsWith("+")) {
      text = text.substring(1).trim();
    }
    try {
      return new BigDecimal(text);
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private int compareHistoryNumericCells(Object left, Object right) {
    BigDecimal leftValue = sortableHistoryNumericValue(left);
    BigDecimal rightValue = sortableHistoryNumericValue(right);
    if (leftValue == null && rightValue == null) {
      return 0;
    }
    if (leftValue == null) {
      return 1;
    }
    if (rightValue == null) {
      return -1;
    }
    return leftValue.compareTo(rightValue);
  }

  private BigDecimal sortableHistoryNumericValue(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof BigDecimal number) {
      return number;
    }
    String text = String.valueOf(value).trim();
    if (text.isBlank() || "-".equals(text)) {
      return null;
    }
    // History total/subtotal rows can include suffix text, e.g.:
    // "100.00 (+ve: 200.00 / -ve: -100.00)". Sort by the leading total.
    int firstSpace = text.indexOf(' ');
    String numericPart = firstSpace > 0 ? text.substring(0, firstSpace) : text;
    numericPart = numericPart.replace(",", "");
    try {
      return new BigDecimal(numericPart);
    } catch (NumberFormatException ignored) {
      return null;
    }
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
    button.putClientProperty("actionButtonHover", actionButtonHoverColor(background));
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

  private void styleCompactActionButton(JButton button, Color background) {
    styleActionButton(button, background);
    button.setFont(BASE_FONT.deriveFont(Font.BOLD, 10f));
    button.setMargin(new java.awt.Insets(3, 4, 3, 4));
    button.setIconTextGap(3);
    button.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(background.darker(), 1, true),
        new EmptyBorder(1, 3, 1, 3)
    ));
  }

  private void styleIconOnlyActionButton(JButton button, Color background) {
    styleCompactActionButton(button, background);
    button.setMargin(new java.awt.Insets(2, 2, 2, 2));
    button.setIconTextGap(0);
    button.setHorizontalTextPosition(SwingConstants.CENTER);
    button.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(background.darker(), 1, true),
        new EmptyBorder(2, 2, 2, 2)
    ));
  }

  private void updateActionButtonColor(JButton button, Color background) {
    button.setBackground(background);
    button.setForeground(button.isEnabled() ? Color.WHITE : new Color(248, 250, 252));
    button.putClientProperty("JButton.disabledText", new Color(248, 250, 252));
  }

  private Color actionButtonHoverColor(Color background) {
    int red = Math.min(255, background.getRed() + 18);
    int green = Math.min(255, background.getGreen() + 18);
    int blue = Math.min(255, background.getBlue() + 18);
    return new Color(red, green, blue);
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
        selectedViewMode == StrategyMode.LIVE
    );
  }

  private void stopTradingEventStream() {
    streamReconnectAvailable = false;
    cancelTradeStreamReconnectRetry();
    tradeStreamLifecycleCoordinator.stop();
  }

  private boolean isGapRocketWorkspaceStrategy(Strategy strategy) {
    if (strategy == null || strategy.workspaceId() == null) {
      return false;
    }
    return workspaceService.findById(strategy.workspaceId())
        .map(StrategyWorkspace::code)
        .map(GAP_ROCKET_WORKSPACE_CODE::equalsIgnoreCase)
        .orElse(false);
  }

  private boolean hasFilledBuyOrder(String strategyId) {
    if (strategyId == null || strategyId.isBlank()) {
      return false;
    }
    return strategyOrderRepository.findByStrategyId(strategyId).stream()
        .filter(order -> order.side() == StrategyOrderSide.BUY)
        .filter(order -> order.status() == StrategyOrderStatus.FILLED
            || order.status() == StrategyOrderStatus.PARTIALLY_FILLED)
        .anyMatch(order -> order.filledQuantity().compareTo(BigDecimal.ZERO) > 0);
  }


  private void refreshDisplayedPositionFromStream(String strategyId) {
    if (tradingApi == null || strategyId == null || strategyId.isBlank()) {
      return;
    }
    ManagedStrategy entry = findStrategyById(strategyId);
    if (entry == null) {
      return;
    }
    // Keep stream updates scoped to the matched strategy when duplicate symbols exist.
    invalidateBrokerPositionSnapshotCache(entry.strategy.mode());
    entry.setCachedPosition(loadPositionForStrategy(entry.strategy));
    // Stream events come from the live account. If the user is currently viewing the
    // other mode (e.g. Paper while a live order filled), there is nothing relevant to
    // repaint – skip the EDT refresh entirely. The next poll cycle will pick up the
    // updated state for the visible mode.
    if (entry.strategy.mode() != selectedViewMode) {
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
      boolean error = normalized.equalsIgnoreCase("error");
      streamReconnectAvailable = error;
      if (error) {
        streamStatus.setText("<html><b>error</b> "
            + "<span style='color:#2F80ED; text-decoration:underline;'>Reconnect</span></html>");
        streamStatus.setToolTipText(TooltipStyler.text("Click Reconnect to open the Alpaca trade stream WebSocket again."));
        streamStatus.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      } else {
        streamStatus.setText(normalized);
        streamStatus.setToolTipText(null);
        streamStatus.setCursor(Cursor.getDefaultCursor());
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.contains("authorized") || lower.contains("listening") || lower.contains("trade update")) {
          showStreamReconnectFailureDialog = false;
          resetTradeStreamReconnectBackoff("stream status=" + normalized);
          syncStrategiesAfterTradeStreamRecovery(normalized);
        }
      }
      streamStatus.setForeground(color == null ? BOTTOM_STATUS_ACCENT : color);
    });
  }

  private void onTradeStreamError(String message) {
    lastStreamErrorMessage = message == null || message.isBlank()
        ? "Unknown trade stream error."
        : message;
    streamRecoverySyncPending = true;
    scheduleTradeStreamReconnectRetry();
    if (!showStreamReconnectFailureDialog) {
      return;
    }
    showStreamReconnectFailureDialog = false;
    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
        this,
        "Trade stream reconnect failed.\n\n" + lastStreamErrorMessage,
        "Trade Stream Error",
        JOptionPane.ERROR_MESSAGE
    ));
  }

  private void reconnectTradeStreamFromStatusBar() {
    if (!AppMetadata.alpacaTradingEventsWebSocketEnabled()) {
      JOptionPane.showMessageDialog(this,
          "Trade stream WebSocket is disabled in app properties.",
          "Trade Stream Disabled",
          JOptionPane.WARNING_MESSAGE);
      return;
    }
    StreamCredentials credentials = streamCredentials();
    if (!credentials.isConfigured()) {
      JOptionPane.showMessageDialog(this,
          "Trade stream reconnect needs saved Alpaca credentials. Open Settings, verify the connection, and save.",
          "Missing Stream Credentials",
          JOptionPane.WARNING_MESSAGE);
      return;
    }
    showStreamReconnectFailureDialog = true;
    updateStreamStatus("connecting", STATUS_WARN);
    tradeLog("[STREAM] Manual reconnect requested from status bar.");
    cancelTradeStreamReconnectRetry();
    startTradingEventStreamIfConfigured(credentials.apiKey(), credentials.apiSecret());
  }

  private void syncStrategiesAfterTradeStreamRecovery(String status) {
    if (!streamRecoverySyncPending) {
      return;
    }
    streamRecoverySyncPending = false;
    tradeLog("[STREAM] Reconnected with status=" + status + ". Syncing strategies after missed stream window.");
    uiPollingExecutor.submit(() -> {
      try {
        if (strategyService != null) {
          strategyService.syncRemoteStrategies();
        }
        if (strategyPollingService != null) {
          int submitted = strategyPollingService.pollStrategiesAsync(strategyRepository.findAll().stream()
              .filter(strategy -> strategy.status() == StrategyStatus.ACTIVE || strategy.status() == StrategyStatus.PAUSED)
              .map(Strategy::id)
              .toList());
          tradeLog("[STREAM] Submitted " + submitted + " strategy refresh poll(s) after reconnect.");
        }
        List<Strategy> stored = strategyRepository.findAll();
        List<Strategy> snapshotsEligibleStrategies = stored.stream()
            .filter(this::includeInBrokerSnapshotRefreshForCurrentMode)
            .toList();
        Map<String, Position> snapshots = !snapshotsEligibleStrategies.isEmpty()
            ? loadPositionSnapshotsForStrategies(snapshotsEligibleStrategies)
            : Map.of();
        SwingUtilities.invokeLater(() -> {
          syncStrategies(stored);
          applyPositionSnapshots(snapshots);
          refreshStrategyTableContent();
          refreshPanels();
          updateStatusBar();
          tradeLog("[STREAM] Strategy sync after reconnect completed.");
        });
      } catch (Exception ex) {
        streamRecoverySyncPending = true;
        tradeLog("[STREAM] Strategy sync after reconnect failed: " + ex.getMessage());
      }
    });
  }

  private void scheduleTradeStreamReconnectRetry() {
    if (!AppMetadata.alpacaTradingEventsWebSocketEnabled()) {
      return;
    }
    resetTradeStreamReconnectBackoffAfterSixAm();
    if (streamReconnectRetryTimer != null && streamReconnectRetryTimer.isRunning()) {
      return;
    }
    int delay = nextTradeStreamReconnectDelayMillis();
    streamReconnectAttempt++;
    streamReconnectRetryTimer = new Timer(delay, ignored -> attemptAutoTradeStreamReconnect());
    streamReconnectRetryTimer.setRepeats(false);
    streamReconnectRetryTimer.start();
    tradeLog("[STREAM] Auto reconnect scheduled in " + (delay / 1000) + "s. attempt=" + streamReconnectAttempt);
  }

  private int nextTradeStreamReconnectDelayMillis() {
    long multiplier = 1L << Math.min(streamReconnectAttempt, 8);
    long delay = STREAM_RECONNECT_BASE_DELAY_MILLIS * multiplier;
    return (int) Math.min(delay, STREAM_RECONNECT_MAX_DELAY_MILLIS);
  }

  private void attemptAutoTradeStreamReconnect() {
    streamReconnectRetryTimer = null;
    resetTradeStreamReconnectBackoffAfterSixAm();
    if (!streamReconnectAvailable) {
      return;
    }
    StreamCredentials credentials = streamCredentials();
    if (!credentials.isConfigured()) {
      tradeLog("[STREAM] Auto reconnect skipped: saved Alpaca credentials are missing.");
      scheduleTradeStreamReconnectRetry();
      return;
    }
    updateStreamStatus("connecting", STATUS_WARN);
    tradeLog("[STREAM] Auto reconnect attempt " + streamReconnectAttempt + " started.");
    startTradingEventStreamIfConfigured(credentials.apiKey(), credentials.apiSecret());
  }

  private StreamCredentials streamCredentials() {
    ApplicationMode mode = selectedApplicationMode();
    String apiKey = settingsDialog.savedApiKey(mode);
    String apiSecret = settingsDialog.savedApiSecret(mode);
    return new StreamCredentials(apiKey, apiSecret);
  }

  private void cancelTradeStreamReconnectRetry() {
    if (streamReconnectRetryTimer != null) {
      streamReconnectRetryTimer.stop();
      streamReconnectRetryTimer = null;
    }
  }

  private void resetTradeStreamReconnectBackoff(String reason) {
    cancelTradeStreamReconnectRetry();
    if (streamReconnectAttempt > 0) {
      tradeLog("[STREAM] Auto reconnect backoff reset: " + reason + ".");
    }
    streamReconnectAttempt = 0;
  }

  private void resetTradeStreamReconnectBackoffAfterSixAm() {
    ZonedDateTime now = ZonedDateTime.now();
    if (now.getHour() < STREAM_RECONNECT_RESET_HOUR) {
      return;
    }
    LocalDate today = now.toLocalDate();
    if (today.equals(lastStreamBackoffResetDate)) {
      return;
    }
    lastStreamBackoffResetDate = today;
    resetTradeStreamReconnectBackoff("daily 6 AM reset");
  }

  private record StreamCredentials(String apiKey, String apiSecret) {

    boolean isConfigured() {
      return apiKey != null && !apiKey.isBlank() && apiSecret != null && !apiSecret.isBlank();
    }
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

}
