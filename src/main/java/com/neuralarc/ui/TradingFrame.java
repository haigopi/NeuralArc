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
import com.neuralarc.db.SqliteWorkspaceRepository;
import com.neuralarc.gaprocket.GapRocketAnalysisDialog;
import com.neuralarc.gaprocket.GapRocketConfig;
import com.neuralarc.gaprocket.GapRocketPanel;
import com.neuralarc.orb.OrbAnalysisDialog;
import com.neuralarc.orb.OrbConfig;
import com.neuralarc.orb.OrbPanel;
import com.neuralarc.diphunter.DipHunterAnalysisDialog;
import com.neuralarc.diphunter.DipHunterConfig;
import com.neuralarc.diphunter.DipHunterPanel;
import com.neuralarc.vwap.VwapAnalysisDialog;
import com.neuralarc.vwap.VwapConfig;
import com.neuralarc.vwap.VwapPanel;
import com.neuralarc.swing.SwingAnalysisDialog;
import com.neuralarc.swing.SwingConfig;
import com.neuralarc.swing.SwingPanel;
import com.neuralarc.model.GapAndGoSchedule;
import com.neuralarc.model.OrbSchedule;
import com.neuralarc.model.DipHunterSchedule;
import com.neuralarc.model.VwapSchedule;
import com.neuralarc.model.SwingSchedule;
import com.neuralarc.service.AutoAnalyzeResultStore;
import com.neuralarc.service.AutoRiskAdjustmentService;
import com.neuralarc.service.FeedbackEmailService;
import com.neuralarc.service.GitHubReleaseUpdateService;
import com.neuralarc.service.MarketHoursService;
import com.neuralarc.service.PendingBuyOrderGuard;
import com.neuralarc.service.OnboardingStateStore;
import com.neuralarc.service.AppSettingsService;
import com.neuralarc.service.AsyncLogUploadService;
import com.neuralarc.service.LogArchiveService;
import com.neuralarc.service.LogUploadStatusStore;
import com.neuralarc.service.StrategyApplyService;
import com.neuralarc.service.StrategyPollingService;
import com.neuralarc.service.ReconciliationService;
import com.neuralarc.service.StrategyService;
import com.neuralarc.service.WorkspaceService;
import com.neuralarc.model.StrategyWorkspace;
import com.neuralarc.model.StrategyWorkspaceTemplate;
import com.neuralarc.service.StrategyEngine;
import com.neuralarc.service.TrendingStocksService;
import com.neuralarc.service.HttpAlpacaScreenerClient;
import com.neuralarc.service.WeekendReboundScoreService;
import com.neuralarc.service.RotatingLogWriter;
import com.neuralarc.service.SpacesLogUploader;
import com.neuralarc.service.TradeEmailNotificationService;
import com.neuralarc.service.UserIdentityService;
import com.neuralarc.util.AppMetadata;
import com.neuralarc.util.BrokerOrderStatusUtil;
import com.neuralarc.util.FontLoader;
import com.neuralarc.util.Monetary;
import com.neuralarc.util.SvgIconLoader;
import com.neuralarc.util.ThemeColors;
import org.json.JSONArray;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.RowSorter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;
import javax.swing.plaf.basic.BasicProgressBarUI;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
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
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.math.BigDecimal;
import java.lang.management.ManagementFactory;
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
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class TradingFrame extends JFrame {
    private static final Font BASE_FONT = createBaseFont();
    private static final int OUTER_PADDING = 16;
    private static final DateTimeFormatter LOG_DATE_FORMAT = DateTimeFormatter.ofPattern("EEE, MMM");
    private static final DateTimeFormatter LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a");
    private static final DateTimeFormatter RULE_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("EEE, MMM d yyyy, h:mm a");
    private static final DateTimeFormatter NEXT_OPEN_FORMAT = DateTimeFormatter.ofPattern("EEE, MMM d yyyy h:mm a z");
    private static final int GRID_SEARCH_MIN_STOCK_COUNT = 9;
    private static final String SMART_PICKS_MENU_VOLATILE = "High Volatility Movers";
    private static final String SMART_PICKS_MENU_DIVERSIFIED = "Diversified Leaders (Top 20)";
    private static final String SMART_PICKS_MENU_WEEKEND_REBOUND = "Weekend Rebound";

    private final JLabel positionSummary = new JLabel("Position: -");
    private final JLabel ruleState = new JLabel("Rules: -");
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
    private final JLabel baseBuyPendingStatus = new JLabel("0.00");
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
    private static final Color TABLE_SELECTION_BG       = ThemeColors.color("NeuralArc.Table.selectionBackground", new Color(201, 220, 252));
    private static final Color TABLE_SELECTION_FG       = ThemeColors.color("NeuralArc.Table.selectionForeground", new Color(10,  35, 100));
    private static final Color TABLE_SELECTION_BORDER   = ThemeColors.color("NeuralArc.Table.selectionBorder", new Color(66, 133, 244)); // left accent stripe on selected row
    private static final Color TABLE_SELECTION_BAR_BG   = ThemeColors.color("NeuralArc.Table.selectionBarBackground", new Color(170, 198, 245)); // progress-bar unfilled on selected row
    private static final Color TABLE_ROW_BG_EVEN        = ThemeColors.color("NeuralArc.Table.rowBackgroundEven", new Color(245, 247, 250));
    private static final Color TABLE_ROW_BG_ODD         = ThemeColors.color("NeuralArc.Table.rowBackgroundOdd", new Color(239, 243, 248));
    private static final Color TABLE_OUTER_BORDER_COLOR = ThemeColors.color("NeuralArc.Table.outerBorder", new Color(232, 236, 242));
    private static final Color PNL_POSITIVE_FG        = PnlCellStyleSupport.POSITIVE;
    private static final Color PNL_NEGATIVE_FG        = PnlCellStyleSupport.NEGATIVE;
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
    private static final Color HISTORY_SUBTOTAL_BG  = ThemeColors.color("NeuralArc.History.subtotalBackground", new Color(215, 225, 240));
    private static final Color HISTORY_SUBTOTAL_FG  = ThemeColors.color("NeuralArc.History.subtotalForeground", new Color(28, 48, 80));
    private static final Color HISTORY_GROUP_BORDER = ThemeColors.color("NeuralArc.History.groupBorder", new Color(173, 181, 189));
    private static final Color LOG_LINE_EVEN = ThemeColors.color("NeuralArc.Log.lineEven", new Color(63, 72, 82));
    private static final Color LOG_LINE_ODD = ThemeColors.color("NeuralArc.Log.lineOdd", new Color(110, 118, 128));
    private static final Color LOG_LINE_FAILURE = ThemeColors.color("NeuralArc.Log.failure", new Color(183, 28, 28));
    private static final int MAX_EVENT_LOG_LINES = 1500;
    private static final long CLOSED_MARKET_RECONCILE_POLL_INTERVAL_MILLIS = 60L * 1000L;
    private static final long CLOSED_MARKET_POLL_INTERVAL_MILLIS = 10L * 60L * 1000L;
    private static final int STREAM_RECONNECT_BASE_DELAY_MILLIS = 2 * 60 * 1000;
    private static final int STREAM_RECONNECT_MAX_DELAY_MILLIS = 30 * 60 * 1000;
    private static final int STREAM_RECONNECT_RESET_HOUR = 6;
    private static final int STRATEGY_STOCK_PRICE_COLUMN = 3;
    private static final long STOCK_PRICE_TOOLTIP_TTL_MILLIS = 30_000L;
    /** Gap between polling ticks that indicates the system was suspended (slept). */
    private static final long WAKE_GAP_DETECTION_MS = 30_000L;
    private static final Color HEADER_STATUS_DEFAULT = new Color(220, 220, 255);
    private static final Color HEADER_STATUS_LIVE_ALERT = new Color(255, 82, 82);
    private static final Color HEADER_STATUS_LIVE_ALERT_DIM = new Color(255, 205, 210);
    private static final Color HEADER_STATUS_LIVE_ACTIVE = new Color(46, 125, 50);
    private static final Color HEADER_STATUS_LIVE_ACTIVE_DIM = Color.WHITE;
    private final JTextPane eventLog = new JTextPane();
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
    private PortfolioCaptureUiStateStore.Key activeCapturePortfolioUiKey;
    private PortfolioCaptureConfig capturePortfolioConfigForUi;
    private StrategyMode capturePortfolioModeForUi;

    private final UserIdentityService identityService = new UserIdentityService();
    private final UserActionLogSupport userActionLog = new UserActionLogSupport(this::log);
    private final AppUninstallController appUninstallController;
    private final SupportActionsController supportActionsController;
    private final HistoryTablePresenter historyTablePresenter = new HistoryTablePresenter();
    private final HistoryRowStyler historyRowStyler = new HistoryRowStyler();
    private final Map<String, StockPriceTooltipSnapshot> stockPriceTooltipSnapshots = new ConcurrentHashMap<>();
    private final Set<String> stockPriceTooltipRefreshesInFlight = ConcurrentHashMap.newKeySet();
    private final MarketStatusPresenter marketStatusPresenter = new MarketStatusPresenter();
    private final PollingCellPresenter pollingCellPresenter = new PollingCellPresenter();
    private final StatusBarPresenter statusBarPresenter = new StatusBarPresenter();
    private final StrategyActionsPresenter strategyActionsPresenter = new StrategyActionsPresenter();
    private final StrategyGridLayoutPresenter strategyGridLayoutPresenter = new StrategyGridLayoutPresenter();
    private final RuleTriggeredHistoryPresenter ruleTriggeredHistoryPresenter = new RuleTriggeredHistoryPresenter();
    private final StrategyTablePresenter strategyTablePresenter = new StrategyTablePresenter();
    private final StrategyOpenPnlCalculator openPnlCalculator = new StrategyOpenPnlCalculator();
    private final SystemMetricsPresenter systemMetricsPresenter = new SystemMetricsPresenter();
    private final KillSwitchController killSwitchController;
    private final JButton refreshPortfolioButton = new JButton("Refresh");
    private final JButton capturePortfolioButton = new JButton("Liquidate Portfolio");
    private final JLabel capturePortfolioIndicator = new JLabel("");
    private final JButton footerActionsButton = new JButton("Actions");
    private final JPopupMenu footerActionsMenu = new JPopupMenu();
    private final PortfolioRefreshController portfolioRefreshController;
    private final PortfolioActionsController portfolioActionsController;
    private final PortfolioCaptureController portfolioCaptureController;
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
            if (viewCol != 6) {
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
            // Left accent stripe: a 3-px blue bar on column 0 of selected rows.
            if (c instanceof JComponent jc) {
                StrategyGridSelectionStyler.applySelectionBorder(jc, rowSelected, column, TABLE_SELECTION_BORDER);
            }
            return c;
        }
    };
    private final JTable filledOrdersTable = new JTable(filledOrdersTableModel);
    private static final String GAP_ROCKET_WORKSPACE_CODE = "GAPROCKET";
    private static final String ORB_WORKSPACE_CODE = "ORB";
    private static final String DIP_HUNTER_WORKSPACE_CODE = "DIP";
    private static final String VWAP_WORKSPACE_CODE = "VWAP";
    private static final String SWING_WORKSPACE_CODE = "SWING";
    private static final String STRATEGIES_GRID_CARD = "strategiesGrid";
    private static final String GAP_ROCKET_EMPTY_CARD = "gapRocketEmpty";
    private static final String ORB_EMPTY_CARD = "orbEmpty";
    private static final String DIP_HUNTER_EMPTY_CARD = "dipHunterEmpty";
    private static final String VWAP_EMPTY_CARD = "vwapEmpty";
    private static final String SWING_EMPTY_CARD = "swingEmpty";
    private final JTabbedPane strategyTabs = new JTabbedPane();
    private final JTextField currentStrategiesSearchField = new JTextField(24);
    private final JTextField tradeHistorySearchField = new JTextField(24);
    private final JRadioButton profitableSellsFilterButton = new JRadioButton("Profitable Sells");
    private final JRadioButton lossSellsFilterButton = new JRadioButton("Loss Sells");
    private final JRadioButton bothSellsFilterButton = new JRadioButton("Both", true);
    private final JButton tradeHistoryGroupByButton = new JButton("Group By Menu: Symbol");
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
    private JPanel headerPanel;
    private final EnumMap<StrategyMode, GapRocketConfig> lastGapRocketConfigs = new EnumMap<>(StrategyMode.class);
    private final EnumMap<StrategyMode, OrbConfig> lastOrbConfigs = new EnumMap<>(StrategyMode.class);
    private final EnumMap<StrategyMode, DipHunterConfig> lastDipHunterConfigs = new EnumMap<>(StrategyMode.class);
    private final EnumMap<StrategyMode, VwapConfig> lastVwapConfigs = new EnumMap<>(StrategyMode.class);
    private final EnumMap<StrategyMode, SwingConfig> lastSwingConfigs = new EnumMap<>(StrategyMode.class);
    private CardLayout strategiesGridCardLayout;
    private JPanel strategiesGridCardPanel;
    private BottomStatusBars bottomStatusBars;
    private TableRowSorter<StrategyGridTableModel> strategySorter;
    private TableRowSorter<HistoryGridTableModel> filledOrdersSorter;
    private TradeHistoryGroupBy tradeHistoryGroupBy = TradeHistoryGroupBy.SYMBOL;
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
    private final GapAndGoCoordinator gapAndGoCoordinator;
    private final OrbCoordinator orbCoordinator;
    private final DipHunterCoordinator dipHunterCoordinator;
    private final VwapCoordinator vwapCoordinator;
    private final SwingCoordinator swingCoordinator;
    private final AutoRiskAdjustmentService autoRiskAdjustmentService;
    private final WorkspaceService workspaceService;
    // Dynamic strategy-workspace tabs; null workspace = the All Stocks view.
    private StrategyWorkspaceTabs strategyWorkspaceTabs;
    private String selectedWorkspaceId;
    private final WorkspaceSummaryPresenter workspaceSummaryPresenter = new WorkspaceSummaryPresenter();
    private final JLabel workspaceSummaryLabel = new JLabel(" ");
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
    private final AvailableFundsStatusState availableFundsStatusState = new AvailableFundsStatusState();
    private volatile String availableFundsText = "Funds Available: -";
    private final AtomicBoolean availableFundsFetchInFlight = new AtomicBoolean(false);
    private static final long AVAILABLE_FUNDS_REFRESH_INTERVAL_MILLIS = 30000L;
    private volatile long lastBatchGridPriceRefreshAtMillis;
    private volatile boolean batchGridPriceRefreshRequestedFromStream;
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
    /** Wall-clock millis of the last polling tick. Used to detect system-sleep gaps. EDT-only. */
    private long lastPollingTickMillis;
    private final ConnectionLifecycleCoordinator connectionLifecycleCoordinator;
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
        gapAndGoCoordinator = new GapAndGoCoordinator(
                new GapAndGoCoordinatorUi(), appDatabase, strategyRepository,
                appSettingsService, marketHoursService, uiPollingExecutor);
        orbCoordinator = new OrbCoordinator(new OrbCoordinatorUi(), appDatabase, strategyRepository,
                appSettingsService, marketHoursService, uiPollingExecutor);
        dipHunterCoordinator = new DipHunterCoordinator(new DipHunterCoordinatorUi(), appDatabase, strategyRepository,
                appSettingsService, marketHoursService, uiPollingExecutor);
        vwapCoordinator = new VwapCoordinator(new VwapCoordinatorUi(), appDatabase, strategyRepository,
                appSettingsService, marketHoursService, uiPollingExecutor);
        swingCoordinator = new SwingCoordinator(new SwingCoordinatorUi(), appDatabase, strategyRepository,
                appSettingsService, marketHoursService, uiPollingExecutor);
        autoRiskAdjustmentService = new AutoRiskAdjustmentService(strategyRepository, marketHoursService,
                java.time.Clock.systemUTC(), this::latestPriceForAutoAdjust, this::log);
        workspaceService = new WorkspaceService(workspaceRepository, strategyRepository);
        portfolioRefreshController = new PortfolioRefreshController(
                strategyRepository,
                strategyOrderRepository,
                uiPollingExecutor,
                new PortfolioRefreshController.Gateway() {
                    @Override public boolean isConnected() { return connectionOk; }
                    @Override public BrokerType brokerType() { return currentBrokerType; }
                    @Override public HttpAlpacaClient alpacaClientForMode(ApplicationMode mode) { return TradingFrame.this.alpacaClientForMode(mode); }
                    @Override public void onRefreshStarted() { setPortfolioRefreshButtonBusy(true); }
                    @Override public void onRefreshFinished() { setPortfolioRefreshButtonBusy(false); }
                    @Override public void syncStrategies(List<Strategy> strategies) { TradingFrame.this.syncStrategies(strategies); }
                    @Override public void applyPositionSnapshots(Map<String, Position> snapshots) { TradingFrame.this.applyPositionSnapshots(snapshots); }
                    @Override
                    public void handleInvalidBrokerMissingStrategies(List<Strategy> invalidStrategies) {
                        TradingFrame.this.handleInvalidBrokerMissingStrategies(invalidStrategies);
                    }
                    @Override public void refreshStrategyTableContent() { TradingFrame.this.refreshStrategyTableContent(); }
                    @Override public void refreshPanels() { TradingFrame.this.refreshPanels(); }
                    @Override public void updateStatusBar() { TradingFrame.this.updateStatusBar(); }
                    @Override public void log(String message) { TradingFrame.this.log(message); }
                    @Override public void showConnectionRequired() {
                        JOptionPane.showMessageDialog(
                                TradingFrame.this,
                                "Connect to Alpaca before refreshing the portfolio.",
                                "Portfolio Refresh",
                                JOptionPane.WARNING_MESSAGE
                        );
                    }
                    @Override public void showRefreshFailed(String message) {
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
            @Override public List<ManagedStrategy> strategies() { return strategies; }
            @Override public List<ManagedStrategy> currentStrategies() {
                return strategies.stream()
                        .filter(TradingFrame.this::includeInCurrentStrategiesTab)
                        .toList();
            }
            @Override public StrategyService strategyService() { return strategyService; }
            @Override public StrategyService strategyServiceForMode(StrategyMode mode) { return TradingFrame.this.strategyServiceForMode(mode); }
            @Override public StrategyMode selectedViewMode() { return selectedViewMode; }
            @Override public StrategyService.ArchiveResult archiveStrategy(String strategyId, String reason) {
                if (strategyService == null) {
                    return StrategyService.ArchiveResult.failed("strategy service is not configured");
                }
                return strategyService.archiveStrategy(strategyId, reason);
            }
            @Override public StrategyService.ArchiveResult deleteLocalTradeHistoryStrategy(String strategyId) {
                return TradingFrame.this.deleteLocalTradeHistoryStrategy(strategyId);
            }
            @Override public StrategyService.ArchiveResult deleteLocalPaperStrategy(String strategyId) {
                return TradingFrame.this.deleteLocalPaperStrategy(strategyId);
            }
            @Override
            public StrategyService.StrategyCreationResult sellPosition(
                    Strategy strategy,
                    SellSubmissionType submissionType,
                    StrategyService.SellExecutionSource executionSource
            ) {
                return TradingFrame.this.sellPosition(strategy, submissionType, executionSource);
            }
            @Override public JMenuItem createMenuItem(String text, String iconPath, Runnable action) { return TradingFrame.this.createStatusMenuItem(text, iconPath, action); }
            @Override public int confirm(Object message, String title, int optionType, int messageType) {
                return JOptionPane.showConfirmDialog(TradingFrame.this, message, title, optionType, messageType);
            }
            @Override public void showMessage(Object message, String title, int messageType) {
                JOptionPane.showMessageDialog(TradingFrame.this, message, title, messageType);
            }
            @Override public void syncStrategiesFromRepository() { TradingFrame.this.syncStrategiesFromRepository(); }
            @Override public void refreshStrategyTableData() { TradingFrame.this.refreshStrategyTableData(); }
            @Override public void updateSelectedStrategy() { TradingFrame.this.updateSelectedStrategy(); }
            @Override public void refreshPanels() { TradingFrame.this.refreshPanels(); }
            @Override public void updateStatusBar() { TradingFrame.this.updateStatusBar(); }
            @Override public void log(String message) { TradingFrame.this.log(message); }
            @Override public void actionStarted(String actionName) { userActionLog.started(actionName); }
            @Override public void actionCompleted(String actionName, String detail) { userActionLog.completed(actionName, detail); }
            @Override public void actionSkipped(String actionName, String reason) { userActionLog.skipped(actionName, reason); }
            @Override public void actionCanceled(String actionName) { userActionLog.canceled(actionName); }
            @Override public void actionFailed(String actionName, String reason) { userActionLog.failed(actionName, reason); }
        });
        portfolioCaptureController = new PortfolioCaptureController(
                new PortfolioCaptureController.Gateway() {
                    @Override public List<ManagedStrategy> strategies() {
                        return strategies;
                    }
                    @Override public StrategyMode selectedViewMode() { return selectedViewMode; }
                    @Override public String selectedWorkspaceId() { return selectedWorkspaceId; }
                    @Override public BigDecimal realizedPnlForStrategy(String strategyId) { return TradingFrame.this.realizedPnlForStrategy(strategyId); }
                    @Override
                    public StrategyService.StrategyCreationResult sellPosition(
                            ManagedStrategy entry,
                            SellSubmissionType submissionType,
                            StrategyService.SellExecutionSource executionSource
                    ) {
                        return TradingFrame.this.sellPosition(entry.strategy, submissionType, executionSource);
                    }
                    @Override public int cancelPendingBaseBuys(StrategyMode mode) { return TradingFrame.this.cancelPendingBaseBuysForAutomation(mode); }
                    @Override public String runSmartPicksAutomation(PortfolioCaptureConfig config) { return TradingFrame.this.runSmartPicksAutomation(config); }
                    @Override public boolean tradingSessionOpen() { return TradingFrame.this.isMarketOpenForUi(); }
                    @Override public String nextTradingSessionOpenDisplay() { return TradingFrame.this.nextTradingSessionOpenDisplay(); }
                    @Override
                    public void onMonitoringChanged(boolean active, PortfolioCaptureSnapshot snapshot, PortfolioCaptureConfig config,
                                                    StrategyMode mode, String workspaceId) {
                        TradingFrame.this.updateCapturePortfolioUi(active, snapshot, config, mode, workspaceId);
                    }
                    @Override
                    public void onSnapshotUpdated(PortfolioCaptureSnapshot snapshot, PortfolioCaptureConfig config) {
                        TradingFrame.this.updateCapturePortfolioIndicator(snapshot, config);
                    }
                    @Override
                    public void onAutomationStateChanged(PortfolioCaptureAutomationState state, int loopCount, int pendingCanceled) {
                        TradingFrame.this.updateCaptureAutomationState(state, loopCount, pendingCanceled);
                    }
                    @Override public void onExecutionStarted() { setCapturePortfolioBusy(true); }
                    @Override
                    public void onExecutionFinished(PortfolioCaptureExecutionResult result, boolean targetTriggered) {
                        setCapturePortfolioBusy(false);
                        syncStrategiesFromRepository();
                        refreshStrategyTableContent();
                        refreshPanels();
                        updateStatusBar();
                        showPortfolioCaptureSummary(result, targetTriggered);
                    }
                    @Override public void log(String message) { TradingFrame.this.log(message); }
                },
                new PortfolioCaptureCalculator(),
                new PortfolioCaptureStateStore(AppMetadata.appDataDirectory().resolve("portfolio-capture-state.json")),
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
                    @Override public Strategy strategy() { return entry.strategy; }
                    @Override public boolean isPaused() { return entry.isPaused(); }
                    @Override public boolean isPauseResumeBusy() { return entry.isPauseResumeBusy(); }
                    @Override public void setPauseResumeBusy(boolean value) { entry.setPauseResumeBusy(value); }
                    @Override public void setPauseResumeBusyText(String value) { entry.setPauseResumeBusyText(value); }
                    @Override public void syncFrom(Strategy strategy) { entry.syncFrom(strategy); }
                };
            }

            @Override public StrategyService strategyService() { return strategyService; }
            @Override public StrategyService liveStrategyService() { return strategyServiceForMode(StrategyMode.LIVE); }
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
            public StrategyService.StrategyCreationResult sellPosition(Strategy strategy, SellSubmissionType submissionType) {
                return TradingFrame.this.sellPosition(strategy, submissionType);
            }
            @Override
            public Optional<Integer> chooseMarketBuyQuantity(Strategy strategy) {
                return TradingFrame.this.chooseMarketBuyQuantity(strategy);
            }
            @Override
            public Optional<ManualLimitBuySelection> chooseLimitBuy(Strategy strategy, BigDecimal currentPrice) {
                return ManualLimitBuyDialog.show(TradingFrame.this, strategy, currentPrice);
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
            public StrategyService.StrategyCreationResult buyMoreAtLimit(Strategy strategy, int quantity, BigDecimal limitPrice) {
                return TradingFrame.this.buyMoreAtLimit(strategy, quantity, limitPrice);
            }
            @Override public StrategyService.StrategyCreationResult repositionExpiredStrategy(String strategyId) {
                StrategyService service = strategyRepository.findById(strategyId)
                        .map(strategy -> strategyServiceForMode(strategy.mode()))
                        .orElse(strategyService);
                if (service == null) {
                    return StrategyService.StrategyCreationResult.failed("strategy service is not configured");
                }
                return service.repositionExpiredStrategy(strategyId);
            }
            @Override public StrategyService.LimitBuyCancelResult cancelPendingLimitBuys(Strategy strategy) {
                StrategyService service = strategyServiceForMode(strategy.mode());
                if (service == null) {
                    return StrategyService.LimitBuyCancelResult.failed(
                            "Broker client is not configured for " + strategy.mode().name() + " mode.");
                }
                return service.cancelPendingLimitBuys(strategy.id());
            }
            @Override public boolean hasCancelablePendingLimitBuy(Strategy strategy) {
                return strategy != null
                        && PendingBuyOrderGuard.hasCancelablePendingLimitBuy(
                                strategyOrderRepository.findByStrategyId(strategy.id()));
            }
            @Override public void excludeFromPortfolioCaptureIfRunning(String strategyId) {
                portfolioCaptureController.excludeStrategyFromActiveCapture(strategyId);
            }
            @Override public BigDecimal realizedPnlForStrategy(String strategyId) { return TradingFrame.this.realizedPnlForStrategy(strategyId); }
            @Override public String closePaperAccountState(Strategy strategy) { return TradingFrame.this.closePaperAccountState(strategy); }
            @Override public void updateHeaderModeStatus(BrokerType brokerType) { TradingFrame.this.updateHeaderModeStatus(brokerType); }
            @Override public BrokerType currentBrokerType() { return currentBrokerType; }
            @Override public boolean hasBrokerPositionAccess() { return currentBrokerType == BrokerType.ALPACA || tradingApi != null; }
            @Override public boolean marketOpenForUi() { return TradingFrame.this.isMarketOpenForUi(); }
            @Override public void setSelectedStrategyId(String strategyId) { selectedStrategyId = strategyId; }
            @Override public String selectedStrategyId() { return selectedStrategyId; }
            @Override public void removeStrategyAt(int modelRow) { strategies.remove(modelRow); }
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
        legalDisclosureAccepted = legalDisclosureController.loadAccepted();
        refreshStrategyRuntimeServices(
                settingsDialog.savedApiKey(selectedApplicationMode()),
                settingsDialog.savedApiSecret(selectedApplicationMode()),
                selectedApplicationMode()
        );
        settingsDialog.setStrategyExportHandler(this::exportStrategiesToFile);
        settingsDialog.setStrategyImportHandler(this::importStrategiesFromFile);
        settingsDialog.setAlpacaAccountChangedHandler(this::resetLocalTradingDataForAlpacaAccountChange);
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
        headerControlsPanel.add(portfolioActionsButton);
        headerControlsPanel.add(settingsButton);

        JButton killSwitchButton = new JButton("KILL SWITCH");
        applyButtonIcon(killSwitchButton, "icons/kill-switch.svg", 15);
        killSwitchButton.setToolTipText(TooltipStyler.text(
                "Pauses active strategies, cancels open Alpaca orders for their symbols, stops polling countdowns, and saves local state. "
                        + "It does not automatically liquidate positions.",
                320
        ));
        killSwitchButton.setFocusPainted(false);
        killSwitchButton.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        killSwitchButton.setFont(FontLoader.ui(Font.BOLD, 11f));
        killSwitchButton.setForeground(Color.WHITE);
        killSwitchButton.setBackground(new Color(180, 20, 20));
        killSwitchButton.setOpaque(true);
        killSwitchButton.setContentAreaFilled(true);
        javax.swing.border.Border killSwitchInner = new EmptyBorder(4, 10, 4, 10);
        javax.swing.border.Border killSwitchPressedInner = new EmptyBorder(3, 9, 3, 9);
        killSwitchButton.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(120, 10, 10), 1, true),
                killSwitchInner
        ));
        killSwitchButton.setMargin(new java.awt.Insets(4, 10, 4, 10));
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
                            killSwitchInner));
                }
            }
            @Override public void mouseExited(MouseEvent e) {
                killSwitchButton.setBackground(BASE_BG);
                killSwitchButton.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(BASE_BORDER, 1, true),
                        killSwitchInner));
            }
            @Override public void mousePressed(MouseEvent e) {
                if (killSwitchButton.isEnabled() && e.getButton() == MouseEvent.BUTTON1) {
                    killSwitchButton.setBackground(PRESS_BG);
                    killSwitchButton.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(PRESS_BORDER, 2, true),
                            killSwitchPressedInner));
                }
            }
            @Override public void mouseReleased(MouseEvent e) {
                if (killSwitchButton.contains(e.getPoint()) && killSwitchButton.isEnabled()) {
                    killSwitchButton.setBackground(HOVER_BG);
                    killSwitchButton.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(HOVER_BORDER, 1, true),
                            killSwitchInner));
                } else {
                    killSwitchButton.setBackground(BASE_BG);
                    killSwitchButton.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(BASE_BORDER, 1, true),
                            killSwitchInner));
                }
            }
        });
        killSwitchButton.addActionListener(e -> killAllStrategies());
        configureButtonShortcut(killSwitchButton, KeyEvent.VK_K,
                KeyStroke.getKeyStroke(KeyEvent.VK_K, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK),
                "killSwitch");
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
        strategyTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        strategyTable.setSelectionBackground(TABLE_SELECTION_BG);
        strategyTable.setSelectionForeground(TABLE_SELECTION_FG);
        strategyTable.setRowMargin(0);
        strategyTable.setShowGrid(false);
        strategyTable.setIntercellSpacing(new Dimension(0, 0));
        StatusRowRenderer statusRowRenderer = new StatusRowRenderer();
        strategyTable.setDefaultRenderer(Object.class, statusRowRenderer);
        strategyTable.setDefaultRenderer(Number.class, statusRowRenderer);
        strategyTable.getColumnModel().getColumn(5).setCellRenderer(new UnrealizedPnLRenderer());
        strategyTable.getColumnModel().getColumn(StrategyGridLayoutPresenter.POLLING_COLUMN_INDEX).setCellRenderer(new PollingBarRenderer());
        strategyTable.getColumnModel().getColumn(StrategyGridLayoutPresenter.ACTIONS_COLUMN_INDEX).setCellRenderer(new ActionsRenderer());
        // Preferred widths express the desired layout when there is room; minimums are kept
        // low so the table can shrink to fit a narrow window instead of overflowing and
        // clipping the right-hand Actions column. Symbol holds a short ticker, so it stays
        // tight and the spare preferred width goes to Status, Polling, and Entry/Exit Source.
        strategyTable.getColumnModel().getColumn(0).setPreferredWidth(72);
        strategyTable.getColumnModel().getColumn(0).setMinWidth(50);
        strategyTable.getColumnModel().getColumn(1).setPreferredWidth(92);
        strategyTable.getColumnModel().getColumn(1).setMinWidth(54);
        strategyTable.getColumnModel().getColumn(6).setPreferredWidth(340);
        strategyTable.getColumnModel().getColumn(6).setMinWidth(120);
        strategyTable.getColumnModel().getColumn(8).setPreferredWidth(95);
        strategyTable.getColumnModel().getColumn(8).setMinWidth(70);
        strategyTable.getColumnModel().getColumn(9).setPreferredWidth(210);
        strategyTable.getColumnModel().getColumn(9).setMinWidth(110);
        strategyTable.getColumnModel().getColumn(10).setPreferredWidth(180);
        strategyTable.getColumnModel().getColumn(10).setMinWidth(100);
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

                // Dispatch the action buttons via the same fixed-width zones used by the renderer.
                // Use invokeLater so the action runs AFTER ALL mousePressed handlers
                // (ours + BasicTableUI) have finished — this is critical because:
                //   • BasicTableUI fires its own mousePressed AFTER ours (LIFO order).
                //   • Without deferral, dialogs opened here block BasicTableUI from
                //     ever running, leaving the table in a broken state on first click.
                if (e.getButton() != java.awt.event.MouseEvent.BUTTON1) return;
                if (viewRow < 0 || viewRow >= strategies.size()
                        || viewCol != StrategyGridLayoutPresenter.ACTIONS_COLUMN_INDEX) return;
                ManagedStrategy clickedStrategy = strategies.get(strategyTable.convertRowIndexToModel(viewRow));
                boolean promoteVisible = actionViewModelFor(clickedStrategy).promoteVisible();
                java.awt.Rectangle cellRect = strategyTable.getCellRect(viewRow, viewCol, false);
                int xInCell  = e.getX() - cellRect.x;
                StrategyGridActionLayout.Action action = StrategyGridActionLayout.actionAt(cellRect.width, xInCell, promoteVisible);
                if (action == StrategyGridActionLayout.Action.NONE) {
                    return;
                }
                final int capturedRow     = viewRow;
                final StrategyGridActionLayout.Action capturedAction = action;
                SwingUtilities.invokeLater(() -> {
                    switch (capturedAction) {
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
                if (viewRow >= 0 && viewCol == StrategyGridLayoutPresenter.ACTIONS_COLUMN_INDEX
                        && actionAtMousePoint(viewRow, e.getX()) != StrategyGridActionLayout.Action.NONE) {
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
        strategySorter.setComparator(2, (left, right) -> compareNumericCells(left, right));
        strategySorter.setComparator(3, (left, right) -> compareNumericCells(left, right));
        strategySorter.setComparator(4, (left, right) -> compareNumericCells(left, right));
        strategySorter.setComparator(5, (left, right) -> {
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
        });
        strategySorter.setSortable(StrategyGridLayoutPresenter.POLLING_COLUMN_INDEX, false); // Polling countdown bar column — not sortable
        strategySorter.setSortable(StrategyGridLayoutPresenter.ACTIONS_COLUMN_INDEX, false); // Actions button column — not sortable
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
        // Per-tab P&L summary row, pinned below the grid. The wrapper is re-parented into the
        // selected workspace tab, so this summary follows whichever workspace is being viewed.
        workspaceSummaryLabel.setFont(BASE_FONT.deriveFont(Font.PLAIN, 11f));
        workspaceSummaryLabel.setForeground(DARK_BTN_FG);
        workspaceSummaryLabel.setBorder(new EmptyBorder(6, 14, 4, 14));
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
        baseBuyPendingStatus.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
        baseBuyPendingStatus.setForeground(BOTTOM_STATUS_MARKET_VALUE);
        baseBuyPendingStatus.setVerticalAlignment(SwingConstants.CENTER);
        baseBuyPendingStatus.setHorizontalAlignment(SwingConstants.LEFT);
        baseBuyPendingStatus.setBorder(new EmptyBorder(0, 0, 0, 0));
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
                baseBuyPendingStatus,
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
        CollapsibleSectionPanel eventLogSection = new CollapsibleSectionPanel("Logs", eventLogScrollPane);

        // Put event log and strategy grid in a vertical split so both are always visible
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                eventLogSection, strategyTabs);
        // Remember the expanded divider position so collapsing then expanding the Logs section
        // restores its previous height instead of leaving it stuck collapsed.
        final int[] expandedLogsDivider = { -1 };
        eventLogSection.addPropertyChangeListener(CollapsibleSectionPanel.COLLAPSED_PROPERTY, event -> {
            boolean nowCollapsed = Boolean.TRUE.equals(event.getNewValue());
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
        });
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
        });
        setExtendedState(getExtendedState() | JFrame.MAXIMIZED_BOTH);
        setLocationRelativeTo(null);
        startBackgroundUpdateAvailabilityCheck();
        SwingUtilities.invokeLater(portfolioCaptureController::restoreIfNeeded);
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
        // Workspaces are mode-scoped: rebuild the dynamic tabs for the newly selected mode.
        if (strategyWorkspaceTabs != null) {
            strategyWorkspaceTabs.rebuild();
        }
        updateGapRocketScheduleBadge(gapAndGoCoordinator.currentSchedule());
        refreshCapturePortfolioModeVisibility();
        updateHeaderModeStatus(currentBrokerType);
        refreshStrategyRuntimeServices(
                savedApiKeyForSelectedMode(),
                savedApiSecretForSelectedMode(),
                selectedApplicationMode()
        );
        restartTradingEventStreamForSelectedMode();
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
        portfolioActionsButton.setToolTipText(TooltipStyler.text(
                "Portfolio actions: bulk operations across your strategies and positions.", 320));
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

    private CollapsibleSectionPanel createDetailSection(JLabel titleLabel, JLabel contentLabel) {
        titleLabel.setForeground(ThemeColors.color("NeuralArc.Section.titleForeground", new Color(78, 84, 94)));
        contentLabel.setForeground(ThemeColors.color("NeuralArc.Detail.foreground", new Color(35, 35, 45)));
        contentLabel.setVerticalAlignment(SwingConstants.TOP);
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
    private static final Color DARK_BTN_BG           = ThemeColors.color("NeuralArc.Button.background", new Color(60,  60,  90));
    private static final Color DARK_BTN_BORDER        = ThemeColors.color("NeuralArc.Button.border", new Color(100, 100, 160));
    private static final Color DARK_BTN_BG_HOVER      = ThemeColors.color("NeuralArc.Button.hoverBackground", new Color(80,  80,  118));
    private static final Color DARK_BTN_BORDER_HOVER  = ThemeColors.color("NeuralArc.Button.hoverBorder", new Color(128, 128, 196));
    private static final Color DARK_BTN_BG_PRESSED    = ThemeColors.color("NeuralArc.Button.pressedBackground", new Color(42,  42,  68));
    private static final Color DARK_BTN_BORDER_PRESSED= ThemeColors.color("NeuralArc.Button.pressedBorder", new Color(85,  85,  148));
    private static final Color DARK_BTN_FG            = ThemeColors.color("NeuralArc.Button.foreground", new Color(230, 230, 255));

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

    /** Inserts the dropdown chevron zone between the line border and the padding. */
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
                if (portfolioCaptureController != null && portfolioCaptureController.monitoringActive()) {
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
     * Attaches hover + press mouse feedback to a dark-background button.
     * Guards against double-installation via a client property.
     *
     * @param normalInner  inner EmptyBorder for the normal/hover state
     * @param pressedInner inner EmptyBorder for the pressed state (1 px less each
     *                     side to compensate for the thicker 2-px outer border)
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
        activeCapturePortfolioUiKey = selectedCapturePortfolioUiKey();
        userActionLog.started("Liquidate Portfolio");
        PortfolioCaptureDialog dialog = new PortfolioCaptureDialog(
                this,
                portfolioCaptureController::currentSnapshot,
                config -> {
                    userActionLog.started("Liquidate Portfolio Now");
                    portfolioCaptureController.executeNow(config);
                },
                config -> {
                    userActionLog.started("Liquidate Portfolio Monitoring");
                    portfolioCaptureController.activateMonitoring(config);
                    userActionLog.completed("Liquidate Portfolio Monitoring", "Monitoring activated.");
                },
                () -> {
                    portfolioCaptureController.emergencyStop();
                    userActionLog.completed("Liquidate Portfolio Monitoring", "Monitoring deactivated.");
                },
                portfolioCaptureController.monitoringActive()
        );
        boolean changed = dialog.showDialog();
        if (!changed) {
            userActionLog.canceled("Liquidate Portfolio");
        }
    }

    private void updateCapturePortfolioUi(boolean active, PortfolioCaptureSnapshot snapshot, PortfolioCaptureConfig config,
                                          StrategyMode mode, String workspaceId) {
        PortfolioCaptureUiStateStore.Key contextKey = capturePortfolioUiKey(mode, workspaceId);
        PortfolioCaptureUiStateStore.Key key = active
                ? contextKey
                : activeCapturePortfolioUiKey == null ? contextKey : activeCapturePortfolioUiKey;
        activeCapturePortfolioUiKey = active ? key : null;
        capturePortfolioConfigForUi = active ? config : null;
        capturePortfolioModeForUi = active && key != null ? key.mode() : null;
        if (key != null) {
            capturePortfolioUiStates.update(key, capturePortfolioUiStates.state(key)
                    .withButton("Liquidate Portfolio", true)
                    .withIndicator(active ? captureIndicatorText(snapshot, config) : "", active)
                    .withPulse(active));
        }
        applySelectedCapturePortfolioState();
        updateCapturePortfolioIndicator(snapshot, config);
    }

    private void updateCapturePortfolioIndicator(PortfolioCaptureSnapshot snapshot, PortfolioCaptureConfig config) {
        PortfolioCaptureUiStateStore.Key key = activeCapturePortfolioUiKey == null
                ? selectedCapturePortfolioUiKey()
                : activeCapturePortfolioUiKey;
        String indicatorText = captureIndicatorText(snapshot, config);
        if (key != null) {
            capturePortfolioUiStates.update(key, capturePortfolioUiStates.state(key)
                    .withIndicator(indicatorText, !indicatorText.isBlank()));
        }
        if (key != null && key.equals(selectedCapturePortfolioUiKey())) {
            capturePortfolioConfigForUi = config;
            applySelectedCapturePortfolioState();
        }
    }

    private String captureIndicatorText(PortfolioCaptureSnapshot snapshot, PortfolioCaptureConfig config) {
        if (snapshot == null || config == null || config.mode() != PortfolioCaptureMode.TARGET_MONITORING) {
            return "";
        }
        String targetLabel = config.targetType() == PortfolioCaptureTargetType.PROFIT_PERCENT
                ? Monetary.round(config.targetValue()) + "%"
                : "$" + Monetary.round(config.targetValue());
        // Show the same P&L as this tab's bottom summary / the status bar (All Stocks), from the
        // single centralized source — not the capture calculator's eligible-subset total.
        BigDecimal contextPnl = computeWorkspaceSnapshot(selectedWorkspaceId).total();
        return "Monitoring Active | P&L $" + Monetary.round(contextPnl)
                + " | Target " + targetLabel
                + " | Progress " + Monetary.round(snapshot.targetProgressPercent()) + "%";
    }

    private void refreshCapturePortfolioModeVisibility() {
        applySelectedCapturePortfolioState();
    }

    private void clearCapturePortfolioIndicatorForMode() {
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
     * Recompute the Liquidate Portfolio indicator P&L on the same refresh cycle as the status bar,
     * so its P&L (the centralized context total) stays in lock-step with the top status bar / tab
     * summary instead of lagging at the slower monitoring-tick cadence.
     */
    private void refreshActiveCaptureIndicator() {
        if (portfolioCaptureController.monitoringActive()) {
            updateCapturePortfolioIndicator(
                    portfolioCaptureController.lastSnapshot(),
                    portfolioCaptureController.activeConfig());
        }
    }

    /**
     * Replace the P&L amount in a stored capture-indicator string with the live centralized context
     * total (computeWorkspaceSnapshot for the selected tab), so the displayed capture P&L always
     * matches the top status bar / tab summary. Target/progress/automation counters are preserved.
     */
    private String withLiveCapturePnl(String storedIndicatorText) {
        if (storedIndicatorText == null || storedIndicatorText.isBlank()) {
            return storedIndicatorText == null ? "" : storedIndicatorText;
        }
        if (!storedIndicatorText.matches(".*P&L \\$-?[0-9.]+.*")) {
            return storedIndicatorText;
        }
        String livePnl = Monetary.round(computeWorkspaceSnapshot(selectedWorkspaceId).total()).toPlainString();
        return storedIndicatorText.replaceFirst(
                "P&L \\$-?[0-9.]+",
                java.util.regex.Matcher.quoteReplacement("P&L $" + livePnl));
    }

    private void applySelectedCapturePortfolioState() {
        PortfolioCaptureUiStateStore.Key key = selectedCapturePortfolioUiKey();
        if (key == null) {
            clearCapturePortfolioIndicatorForMode();
            return;
        }
        PortfolioCaptureUiStateStore.State state = capturePortfolioUiStates.state(key);
        capturePortfolioButton.setText(state.buttonText());
        capturePortfolioButton.setEnabled(state.buttonEnabled());
        // The capture P&L text is shown only while monitoring is enabled for THIS strategy tab,
        // and rendered rainbow. The P&L amount is re-derived live from the single centralized source
        // at paint time so it stays in lock-step with the top status bar / tab summary (the stored
        // string can be stale, e.g. computed before broker positions loaded).
        boolean showIndicator = state.monitoringActive() && !state.indicatorText().isBlank();
        String indicatorText = showIndicator ? withLiveCapturePnl(state.indicatorText()) : "";
        capturePortfolioIndicator.setVisible(showIndicator);
        capturePortfolioIndicator.setText(showIndicator ? RainbowText.toHtml(indicatorText) : "");
        capturePortfolioIndicator.setForeground(state.monitoringActive() ? CAPTURE_INDICATOR_ACTIVE_TEXT : CAPTURE_INDICATOR_IDLE_TEXT);
        capturePortfolioIndicator.setToolTipText(state.monitoringActive()
                ? TooltipStyler.text("Liquidate Portfolio monitoring is evaluating current portfolio P&L against the configured target.", 320)
                : null);
        if (state.pulseActive()) {
            startCapturePortfolioPulse();
        } else {
            stopCapturePortfolioPulse();
        }
    }

    private void updateCaptureAutomationState(PortfolioCaptureAutomationState state, int loopCount, int pendingCanceled) {
        SwingUtilities.invokeLater(() -> {
            if (state == PortfolioCaptureAutomationState.STOPPED && !portfolioCaptureController.monitoringActive()) {
                return;
            }
            if (state == PortfolioCaptureAutomationState.PAUSED_MARKET_CLOSED) {
                stopCapturePortfolioPulse();
                if (activeCapturePortfolioUiKey != null) {
                    capturePortfolioUiStates.update(activeCapturePortfolioUiKey, capturePortfolioUiStates.state(activeCapturePortfolioUiKey)
                            .withButton("Liquidate Portfolio:Auto Paused [Closed Market]", true)
                            .withPulse(false));
                }
                applySelectedCapturePortfolioState();
                capturePortfolioIndicator.setForeground(CAPTURE_INDICATOR_IDLE_TEXT);
                capturePortfolioIndicator.setToolTipText(TooltipStyler.text(
                        "Liquidate Portfolio automation is configured but paused because the market session is closed. "
                                + "It resumes automatically when the configured regular or extended-hours session opens.",
                        360
                ));
            } else if (portfolioCaptureController.monitoringActive()
                    && state == PortfolioCaptureAutomationState.MONITORING) {
                if (activeCapturePortfolioUiKey != null) {
                    capturePortfolioUiStates.update(activeCapturePortfolioUiKey, capturePortfolioUiStates.state(activeCapturePortfolioUiKey)
                            .withButton("Liquidate Portfolio", true)
                            .withPulse(true));
                }
                applySelectedCapturePortfolioState();
                capturePortfolioButton.setToolTipText(capturePortfolioDefaultTooltip());
                capturePortfolioIndicator.setForeground(CAPTURE_INDICATOR_ACTIVE_TEXT);
            }
            String suffix = captureAutomationCounterText(loopCount, pendingCanceled);
            String current = activeCapturePortfolioUiKey == null ? capturePortfolioIndicator.getText() : capturePortfolioUiStates.state(activeCapturePortfolioUiKey).indicatorText();
            String nextIndicator = (current == null || current.isBlank() ? "Monitoring Active" : stripCaptureAutomationCounters(current)) + suffix;
            if (activeCapturePortfolioUiKey != null) {
                capturePortfolioUiStates.update(activeCapturePortfolioUiKey, capturePortfolioUiStates.state(activeCapturePortfolioUiKey)
                        .withIndicator(nextIndicator, true));
            }
            applySelectedCapturePortfolioState();
            if (!suffix.isBlank()) {
                capturePortfolioIndicator.setToolTipText(TooltipStyler.text(captureAutomationCounterTooltip(), 380));
            }
        });
    }

    private String captureAutomationCounterText(int loopCount, int pendingCanceled) {
        PortfolioCaptureConfig config = capturePortfolioConfigForUi;
        PortfolioCaptureHistoryStore.Summary summary = portfolioCaptureController.captureHistorySummary();
        StringBuilder text = new StringBuilder();
        if (config != null && config.continuousLoop()) {
            text.append(" | Loops ").append(loopCount);
        }
        if (summary != null && summary.captureCount() > 0) {
            text.append(" | Liquidation Total P&L $").append(Monetary.round(summary.actualPnl()));
        }
        if (config != null && config.autoCleanPendingBeforeCycle()) {
            text.append(" | Pending Buy Orders Cancelled ").append(pendingCanceled);
        }
        return text.toString();
    }

    private String captureAutomationCounterTooltip() {
        PortfolioCaptureHistoryStore.Summary summary = portfolioCaptureController.captureHistorySummary();
        String history = summary == null || summary.captureCount() == 0
                ? ""
                : " Total liquidation P&L is cumulative across completed portfolio liquidations. Runs="
                + summary.captureCount()
                + ", stocks liquidated="
                + summary.capturedStocks()
                + ", estimated P&L=$"
                + Monetary.round(summary.estimatedPnl())
                + ", actual P&L=$"
                + Monetary.round(summary.actualPnl())
                + ".";
        return "Loops is the number of completed continuous liquidation/re-entry cycles. "
                + "Pending Buy Orders Cancelled is the number of pending base buy limit orders automatically cancelled "
                + "by Liquidate Portfolio cleanup before liquidation or re-entry."
                + history;
    }

    private String stripCaptureAutomationCounters(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        int marker = firstMarkerIndex(
                text,
                " | Loops ",
                " | Pending Buy Orders Cancelled ",
                " | Pending Cancelled ",
                " | Liquidation Total P&L ",
                " | State "
        );
        return marker < 0 ? text : text.substring(0, marker);
    }

    private int firstMarkerIndex(String text, String... markers) {
        int first = -1;
        for (String marker : markers) {
            int index = text.indexOf(marker);
            if (index >= 0 && (first < 0 || index < first)) {
                first = index;
            }
        }
        return first;
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

    private String runSmartPicksAutomation(PortfolioCaptureConfig config) {
        ApplicationMode mode = config.reentryMode() == StrategyMode.LIVE ? ApplicationMode.LIVE : ApplicationMode.PAPER;
        String apiKey = settingsDialog.savedApiKey(mode);
        String apiSecret = settingsDialog.savedApiSecret(mode);
        if (apiKey.isBlank() || apiSecret.isBlank()) {
            return "Skipped: Alpaca credentials are required.";
        }
        log("[Portfolio Liquidation] Auto re-entry started. mode=" + config.reentryMode()
                + " quantity=" + config.reentryQuantity()
                + " term=" + config.reentryRecommendationType()
                + " smartPicksStrategy=" + config.reentrySmartPicksStrategy());
        HttpAlpacaMarketDataApi marketDataApi = new HttpAlpacaMarketDataApi(apiKey, apiSecret);
        List<TrendingStock> stocks = new ArrayList<>();
        try {
            stocks.addAll(portfolioCaptureSmartPicksStocks(config, apiKey, apiSecret, marketDataApi));
        } catch (Exception ex) {
            log("[Portfolio Liquidation] Auto re-entry failed to fetch Smart Picks stocks: " + ex.getMessage());
            return "Skipped: unable to fetch Smart Picks stocks.";
        }
        List<SmartPicksSimulationSelection> selections = new SmartPicksPortfolioAutomationService(marketDataApi, this::log)
                .analyzeSelections(stocks, config.reentryRecommendationType(), config.reentryQuantity());
        SmartPicksSimulationPlacementController controller = new SmartPicksSimulationPlacementController(new SmartPicksSimulationPlacementController.Gateway() {
            @Override public com.neuralarc.service.StrategyRepository repository() { return strategyRepository; }
            @Override public StrategyService.StrategyCreationResult createPaperStrategy(Strategy strategy) {
                return createStrategy(strategy, config.reentryMode());
            }
            @Override public StrategyService.StrategyCreationResult createStrategy(Strategy strategy, StrategyMode targetMode) {
                StrategyService service = strategyServiceForMode(targetMode);
                if (service == null) {
                    return StrategyService.StrategyCreationResult.failed("Strategy service is not configured for " + targetMode);
                }
                return service.createAndActivate(strategy);
            }
            @Override public boolean confirmReplaceWaitingPaperStrategy(String symbol) { return true; }
            @Override public boolean allowDuplicateSymbols() { return settingsDialog.appliedAllowDuplicateSymbolStrategies(); }
            @Override public String targetWorkspaceId() { return null; }
            @Override public int defaultStrategyPollingSeconds() { return settingsDialog.appliedDefaultStrategyPollingSeconds(); }
            @Override public boolean defaultRepeatCycleAfterProfitExitEnabled() { return settingsDialog.appliedDefaultRepeatCycleAfterProfitExitEnabled(); }
            @Override public boolean defaultResubmitOnExpiryEnabled() { return settingsDialog.appliedDefaultResubmitOnExpiryEnabled(); }
            @Override public void cancelAndDeletePaperStrategy(String strategyId) { strategyServiceForMode(config.reentryMode()).delete(strategyId); }
            @Override public void afterPlacement() {
                syncStrategiesFromRepository();
                refreshStrategyTableData();
                updateStatusBar();
                refreshPanels();
            }
            @Override public void log(String message) { TradingFrame.this.log(message); }
        }, config.reentryMode());
        SmartPicksSimulationPlacementController.PlacementResult result = controller.place(selections);
        log("[Portfolio Liquidation] Auto re-entry generated positions. created=" + result.created()
                + " replaced=" + result.replaced() + " skipped=" + result.skipped());
        return controller.summaryMessage(result).replace('\n', ' ');
    }

    private List<TrendingStock> portfolioCaptureSmartPicksStocks(
            PortfolioCaptureConfig config,
            String apiKey,
            String apiSecret,
            HttpAlpacaMarketDataApi marketDataApi
    ) throws Exception {
        if (config.reentrySmartPicksStrategy() == PortfolioCaptureSmartPicksStrategy.DIVERSIFIED_TOP_20) {
            List<TrendingStock> stocks = SmartPicksTrendingStocksDialog.diversifiedTop20Stocks(
                    symbol -> latestPriceForPortfolioCaptureAutomation(symbol, marketDataApi)
            );
            log("[Portfolio Liquidation] Auto re-entry using Diversified Leaders (Top 20). symbols="
                    + stocks.stream().map(TrendingStock::symbol).toList());
            return stocks;
        }
        if (config.reentrySmartPicksStrategy() == PortfolioCaptureSmartPicksStrategy.WEEKEND_REBOUND) {
            TrendingStocksService trendingService = new TrendingStocksService(new HttpAlpacaScreenerClient(apiKey, apiSecret));
            List<TrendingStock> stocks = new WeekendReboundScoreService().topStocks(trendingService, marketDataApi, 20);
            log("[Portfolio Liquidation] Auto re-entry using Weekend Rebound. symbols="
                    + stocks.stream().map(TrendingStock::symbol).toList());
            return stocks;
        }
        TrendingStockGroups groups = new TrendingStocksService(new HttpAlpacaScreenerClient(apiKey, apiSecret)).topGainersAndLosers(10);
        List<TrendingStock> stocks = new ArrayList<>();
        stocks.addAll(groups.gainers());
        stocks.addAll(groups.losers());
        log("[Portfolio Liquidation] Auto re-entry using High Volatility Movers. gainers="
                + groups.gainers().stream().map(TrendingStock::symbol).toList()
                + " losers=" + groups.losers().stream().map(TrendingStock::symbol).toList());
        return stocks;
    }

    private BigDecimal latestPriceForPortfolioCaptureAutomation(String symbol, HttpAlpacaMarketDataApi marketDataApi) {
        try {
            List<MarketBar> bars = marketDataApi.getIntradayBars(
                    symbol,
                    LocalDate.now().minusDays(5),
                    LocalDate.now(),
                    15
            );
            if (bars == null || bars.isEmpty()) {
                return BigDecimal.ZERO;
            }
            return bars.get(bars.size() - 1).close();
        } catch (Exception ex) {
            log("[Portfolio Liquidation] Price fetch fallback used for " + symbol + ": " + ex.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private void setCapturePortfolioBusy(boolean busy) {
        PortfolioCaptureUiStateStore.Key key = activeCapturePortfolioUiKey == null
                ? selectedCapturePortfolioUiKey()
                : activeCapturePortfolioUiKey;
        if (key != null) {
            capturePortfolioUiStates.update(key, capturePortfolioUiStates.state(key).withBusy(busy));
        }
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
        PortfolioCaptureHistoryStore.Summary summary = portfolioCaptureController.captureHistorySummary();
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
        smartPicksMenu.add(createStatusMenuHeader("Smart Picks"));
        smartPicksMenu.add(createStatusMenuItem(
                SMART_PICKS_MENU_VOLATILE,
                "icons/smart-picks.svg",
                () -> openSmartPicksTrendingStocksDialog(SmartPicksTrendingStocksDialog.StrategyUniverse.VOLATILE)
        ));
        smartPicksMenu.add(createStatusMenuItem(
                SMART_PICKS_MENU_DIVERSIFIED,
                "icons/portfolio.svg",
                () -> openSmartPicksTrendingStocksDialog(SmartPicksTrendingStocksDialog.StrategyUniverse.DIVERSIFIED_TOP_20)
        ));
        smartPicksMenu.add(createStatusMenuItem(
                SMART_PICKS_MENU_WEEKEND_REBOUND,
                "icons/smart-picks.svg",
                () -> openSmartPicksTrendingStocksDialog(SmartPicksTrendingStocksDialog.StrategyUniverse.WEEKEND_REBOUND)
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

    static List<String> smartPicksMenuLabels() {
        return List.of(SMART_PICKS_MENU_VOLATILE, SMART_PICKS_MENU_DIVERSIFIED, SMART_PICKS_MENU_WEEKEND_REBOUND);
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

    private StrategyService.StrategyCreationResult buyMoreAtMarket(Strategy strategy, int quantity) {
        StrategyService modeAwareService = strategyServiceForMode(strategy.mode());
        if (modeAwareService == null) {
            return StrategyService.StrategyCreationResult.failed(
                    "Broker client is not configured for " + strategy.mode().name() + " mode."
            );
        }
        return modeAwareService.buyMoreAtMarket(strategy.id(), quantity);
    }

    private StrategyService.StrategyCreationResult buyMoreAtLimit(Strategy strategy, int quantity, BigDecimal limitPrice) {
        StrategyService modeAwareService = strategyServiceForMode(strategy.mode());
        if (modeAwareService == null) {
            return StrategyService.StrategyCreationResult.failed(
                    "Broker client is not configured for " + strategy.mode().name() + " mode."
            );
        }
        return modeAwareService.buyMoreAtLimit(strategy.id(), quantity, limitPrice);
    }

    private StrategyService strategyServiceForMode(StrategyMode mode) {
        ApplicationMode applicationMode = mode == StrategyMode.LIVE ? ApplicationMode.LIVE : ApplicationMode.PAPER;
        HttpAlpacaClient client = alpacaClientForMode(applicationMode);
        return tradingRuntimeSupport.createStrategyService(client, mode);
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
        userActionLog.started("Settings");
        stopTradingEventStream();
        settingsDialog.prepareForOpen();
        settingsDialog.setVisible(true);
        if (settingsDialog.wasSavedDuringOpen()) {
            connectionOk = false;
            setStatus("Not connected — verify connection in Settings after changes.", STATUS_WARN);
            updateHeaderModeStatus(currentBrokerType);
            updateStatusBar();
            autoInitializeConnection();
            userActionLog.completed("Settings", "Saved. Connection refresh started.");
        } else {
            userActionLog.completed("Settings", "Closed without saving.");
        }
    }

    private void resetLocalTradingDataForAlpacaAccountChange() {
        log("[SETTINGS] Different Alpaca account selected. Clearing local strategy data before reconnect.");
        if (strategyPollingService != null) {
            strategyPollingService.shutdown();
        }
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
        log("[PORTFOLIO] Deleted trade history record for " + strategy.symbol() + ".");
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
            }
            log("[PORTFOLIO] Deleted PAPER mode entry for " + strategy.symbol() + ".");
            return StrategyService.ArchiveResult.success(strategy.id());
        } catch (Exception ex) {
            return StrategyService.ArchiveResult.failed(ex.getMessage());
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
        if (strategyPollingService != null) {
            strategyPollingService.shutdown();
            strategyPollingService = null;
        }
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
        if (strategyPollingService == null || !shouldRunPollingCycleNow() || !pollingCycleInFlight.compareAndSet(false, true)) {
            return;
        }
         uiPollingExecutor.submit(() -> {
            try {
                int dueStrategies = strategyPollingService.pollDueStrategies();
                StrategyPollingService.MarketClosedAutoRepairSummary startupAutoRepairSummary = startupMarketClosedRepairAuditLogged
                        ? new StrategyPollingService.MarketClosedAutoRepairSummary(List.of(), Map.of())
                        : strategyPollingService.drainMarketClosedAutoRepairedStrategyIds();
                List<Strategy> stored = strategyRepository.findAll();
                Map<String, Boolean> overnightEligibility = loadOvernightEligibilityForStrategies(stored);
                boolean intervalRefreshDue = shouldRunBatchGridPriceRefresh(stored);
                boolean refreshBrokerSnapshots = hasStrategiesNeedingBrokerSnapshots(stored)
                        && (dueStrategies > 0 || intervalRefreshDue);
                Map<String, Position> positionSnapshots = refreshBrokerSnapshots
                        ? loadPositionSnapshotsForStrategies(stored)
                        : Map.of();
                SwingUtilities.invokeLater(() -> {
                    try {
                        syncStrategies(stored);
                        applyOvernightEligibilitySnapshots(overnightEligibility);
                        applyPositionSnapshots(positionSnapshots);
                        if ((dueStrategies > 0 || !positionSnapshots.isEmpty()) && shouldRunBrokerBackedUiRefresh()) {
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
     * Called at the top of every polling tick to detect a system-sleep gap.
     * If the gap between ticks exceeds {@link #WAKE_GAP_DETECTION_MS} the system was likely
     * suspended. We reset the stream-reconnect backoff so the next retry fires at minimum
     * delay rather than the current (potentially multi-minute) exponential ceiling.
     * Must be called on the EDT.
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

    private boolean shouldRunPollingCycleNow() {
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
            snapshot = StockPriceTooltipSnapshot.fromBars(
                    entry.strategy.symbol(),
                    List.of(),
                    entry.cachedPosition().getLastPrice()
            );
        }
        return snapshot.tooltipText();
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
        if (stored == null || stored.isEmpty() || currentBrokerType != BrokerType.ALPACA) {
            return Map.of();
        }
        return BrokerSnapshotLoader.loadPositionSnapshots(stored, this::alpacaClientForMode, this::includeInBrokerSnapshotRefresh);
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
                if (position != null && position.exists() && position.symbol() != null && !position.symbol().isBlank()) {
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
            if (entry == null || entry.strategy == null) {
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
     * On startup, reconcile every locally stored pending order against the broker so an order that is
     * still accepted/new/pending is never shown as filled after a restart. Broker state wins. Runs on
     * a background thread; the grid is re-synced on the EDT afterwards. Best-effort: if the broker is
     * unreachable, local state is left untouched and later polling/streaming will correct it.
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
                settingsDialog.appliedDefaultResubmitOnExpiryEnabled()
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
                    true,
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
                    resubmit
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
            @Override public com.neuralarc.service.StrategyRepository repository() { return strategyRepository; }
            @Override public StrategyService.StrategyCreationResult createPaperStrategy(Strategy strategy) {
                return createStrategy(strategy, targetMode);
            }
            @Override public StrategyService.StrategyCreationResult createStrategy(Strategy strategy, StrategyMode requestedMode) {
                StrategyService service = strategyServiceForMode(requestedMode);
                if (service == null) {
                    return StrategyService.StrategyCreationResult.failed("Strategy service is not configured for " + requestedMode);
                }
                return service.createAndActivate(strategy);
            }
            @Override public boolean confirmReplaceWaitingPaperStrategy(String symbol) {
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
            @Override public boolean allowDuplicateSymbols() { return settingsDialog.appliedAllowDuplicateSymbolStrategies(); }
            @Override public String targetWorkspaceId() { return selectedWorkspaceForNewStrategy(); }
            @Override public int defaultStrategyPollingSeconds() { return settingsDialog.appliedDefaultStrategyPollingSeconds(); }
            @Override public boolean defaultRepeatCycleAfterProfitExitEnabled() { return settingsDialog.appliedDefaultRepeatCycleAfterProfitExitEnabled(); }
            @Override public boolean defaultResubmitOnExpiryEnabled() { return settingsDialog.appliedDefaultResubmitOnExpiryEnabled(); }
            @Override public void cancelAndDeletePaperStrategy(String strategyId) { strategyServiceForMode(targetMode).delete(strategyId); }
            @Override public void afterPlacement() {
                syncStrategiesFromRepository();
                refreshStrategyTableData();
                updateStatusBar();
                refreshPanels();
            }
            @Override public void log(String message) { TradingFrame.this.log(message); }
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
        userActionLog.started("Edit Strategy " + entry.strategy.symbol());
        HttpAlpacaMarketDataApi marketDataApi = connectionOk && !runtimeApiKey.isBlank()
                ? new HttpAlpacaMarketDataApi(runtimeApiKey, runtimeApiSecret) : null;
        StrategyDialog dialog = new StrategyDialog(this, entry.toConfig(), marketDataApi, autoAnalyzeResultStore);
        StrategyConfig updated = dialog.showDialog();
        if (updated == null) {
            userActionLog.canceled("Edit Strategy " + entry.strategy.symbol());
            return;
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
            userActionLog.failed("Edit Strategy " + entry.strategy.symbol(), "An active or paused strategy for " + updated.symbol() + " already exists.");
            JOptionPane.showMessageDialog(
                    this,
                    duplicateSymbolAlertMessage(updated.symbol(), entry.strategy.workspaceId(), allowDuplicateSymbols, false),
                    "Duplicate Symbol",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
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
            userActionLog.failed("Edit Strategy " + entry.strategy.symbol(), entry.strategy.mode() + " broker client is not configured.");
            JOptionPane.showMessageDialog(
                    this,
                    "Broker client is not configured for this strategy mode.",
                    "Strategy Update Failed",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }
        Optional<Strategy> updatedResult = modeAwareService.updateStrategy(updatedStrategy);
        if (updatedResult.isEmpty()) {
            userActionLog.failed("Edit Strategy " + entry.strategy.symbol(), "Strategy service rejected the update.");
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
        refreshEditedStrategyBrokerSnapshotAsync(updatedResult.get().id());
        refreshPanels();
        userActionLog.completed("Edit Strategy " + updatedResult.get().symbol(), "Strategy saved.");
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
        return strategyTablePresenter.displayStatusLabel(
                entry.strategy,
                entry.cachedPosition(),
                isStrategySessionSuppressed(entry.strategy),
                isWaitingForFill(entry.strategy),
                entry.strategy.status() == StrategyStatus.FAILED && isQueueableSessionError(entry.strategy.lastError()),
                !connectionOk || connectionRetryPending,
                entry.cachedRealizedPnl(),
                entry.cachedPendingManualBuy()
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

    private void updateUnrealizedSummaries() {
        // Single source of truth: the top status-bar total is the All Stocks aggregate
        // (forWorkspace(null)) over the exact same per-strategy accounts the tab summary uses.
        AccountingInputs inputs = buildAccountingInputs(selectedViewMode);
        WorkspaceAccounting.Snapshot total =
                WorkspaceAccounting.forWorkspace(null, inputs.accounts(), inputs.sells());
        if (selectedViewMode == StrategyMode.LIVE) {
            liveUnrealizedSummary.setText("LIVE P&L (Unrealized/Realized): "
                    + total.unrealized().toPlainString()
                    + " / "
                    + total.realized().toPlainString());
        } else {
            paperUnrealizedSummary.setText("Paper P&L (Unrealized/Realized): "
                    + total.unrealized().toPlainString()
                    + " / "
                    + total.realized().toPlainString());
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
        entry.setTradeSnapshot(lastSellPrice, realized, latestPendingManualBuy(orders));
    }

    private StrategyTablePresenter.PendingOrderSummary latestPendingManualBuy(List<StrategyOrder> orders) {
        return orders.stream()
                .filter(order -> order.stage() == StrategyStage.MANUAL_BUY)
                .filter(order -> order.side() == StrategyOrderSide.BUY)
                .filter(StrategyOrder::isPending)
                .max(Comparator
                        .comparing(StrategyOrder::updatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(StrategyOrder::submittedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(order -> new StrategyTablePresenter.PendingOrderSummary(order.limitPrice(), order.requestedQuantity()))
                .orElse(null);
    }

    private BigDecimal latestFilledSellPrice(List<StrategyOrder> orders) {
        return orders.stream()
                .filter(order -> order.side() == StrategyOrderSide.SELL)
                .filter(order -> order.status() == StrategyOrderStatus.FILLED || order.status() == StrategyOrderStatus.PARTIALLY_FILLED)
                .filter(order -> order.filledQuantity().compareTo(BigDecimal.ZERO) > 0)
                .max(Comparator
                        .comparing(StrategyOrder::filledAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(StrategyOrder::submittedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(order -> order.filledAveragePrice().compareTo(BigDecimal.ZERO) > 0
                        ? order.filledAveragePrice()
                        : order.limitPrice())
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal realizedPnlForOrders(List<StrategyOrder> orders) {
        List<StrategyOrder> filledOrders = orders.stream()
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
        rememberSelectedStrategy();
        preservingSelection = true;
        if (modelRow >= 0 && modelRow < strategies.size()) {
            refreshStrategyTradeSnapshot(strategies.get(modelRow));
        }
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
            refreshStrategyWorkspaceEmptyState();
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
        if (entry.strategy.mode() != selectedViewMode) {
            return false;
        }
        if (entry.strategy.status() == StrategyStatus.FAILED) {
            if (includeFailedStrategyInCurrentTab(entry.strategy)) {
                return true;
            }
            // Keep failed rows visible when there is still open broker exposure.
            return hasOpenExposure(entry);
        }
        if (entry.strategy.status() == StrategyStatus.ARCHIVED || entry.strategy.status() == StrategyStatus.STOPPED) {
            return false;
        }
        if (entry.strategy.status() == StrategyStatus.COMPLETED) {
            return !entry.strategy.restartAfterExitEnabled();
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
        if (isGapRocketRecommendationRow(entry.strategy)) {
            return true;
        }
        // Keep showing rows that still have live exposure on the broker side.
        return entry.strategy.status() == StrategyStatus.ACTIVE
                || isWaitingForFill(entry.strategy);
    }

    private boolean isGapRocketRecommendationRow(Strategy strategy) {
        if (strategy == null || strategy.latestOrderStatus() == null) {
            return false;
        }
        return strategy.latestOrderStatus().startsWith("GAP_ROCKET_");
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
            // Pin the button in EAST so BorderLayout always grants it its full preferred
            // width — it must never be clipped at the toolbar edge. The status indicator
            // lives in the flexible CENTER region so its variable-length text can shrink
            // (or clip) without ever squeezing the button.
            capturePortfolioIndicator.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
            capturePortfolioIndicator.setForeground(CAPTURE_INDICATOR_IDLE_TEXT);
            JPanel captureControls = new JPanel(new GridBagLayout());
            captureControls.setOpaque(false);
            GridBagConstraints captureGbc = new GridBagConstraints();
            captureGbc.anchor = GridBagConstraints.CENTER;
            // P&L indicator sits immediately to the LEFT of the Capture/Liquidate button.
            captureGbc.gridx = 0;
            captureGbc.insets = new Insets(0, 0, 0, 8);
            captureControls.add(capturePortfolioIndicator, captureGbc);
            captureGbc.gridx = 1;
            captureGbc.insets = new Insets(0, 0, 0, 0);
            captureControls.add(capturePortfolioButton, captureGbc);
            captureGbc.gridx = 2;
            captureGbc.insets = new Insets(0, 8, 0, 0);
            captureControls.add(addStrategyButton, captureGbc);
            panel.add(captureControls, BorderLayout.EAST);
        } else if (searchField == tradeHistorySearchField) {
            panel.add(createTradeHistoryGroupByPanel(), BorderLayout.CENTER);
            panel.add(createTradeHistoryFilterPanel(), BorderLayout.EAST);
        }
        panel.setVisible(false);
        return panel;
    }

    private void configureFilledOrdersColumnWidths() {
        setTableColumnWidth(0, 70, 52, 90);
        setTableColumnWidth(1, 88, 68, 110);
        setTableColumnWidth(2, 96, 76, 120);
        setFlexibleTableColumnWidth(3, 260, 180, Integer.MAX_VALUE);
        setTableColumnWidth(4, 48, 40, 58);
        setTableColumnWidth(5, 88, 70, 112);
        setTableColumnWidth(6, 44, 34, 54);
        setTableColumnWidth(7, 64, 52, 76);
        setTableColumnWidth(8, 64, 52, 76);
        setFlexibleTableColumnWidth(9, 240, 170, Integer.MAX_VALUE);
        setTableColumnWidth(10, 200, 170, 240);
    }

    private void setTableColumnWidth(int columnIndex, int preferredWidth, int minWidth, int maxWidth) {
        if (columnIndex < 0 || columnIndex >= filledOrdersTable.getColumnModel().getColumnCount()) {
            return;
        }
        javax.swing.table.TableColumn column = filledOrdersTable.getColumnModel().getColumn(columnIndex);
        column.setPreferredWidth(preferredWidth);
        column.setMinWidth(minWidth);
        column.setMaxWidth(maxWidth);
    }

    private void setFlexibleTableColumnWidth(int columnIndex, int preferredWidth, int minWidth, int maxWidth) {
        if (columnIndex < 0 || columnIndex >= filledOrdersTable.getColumnModel().getColumnCount()) {
            return;
        }
        javax.swing.table.TableColumn column = filledOrdersTable.getColumnModel().getColumn(columnIndex);
        column.setPreferredWidth(preferredWidth);
        column.setMinWidth(minWidth);
        column.setMaxWidth(maxWidth);
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
        strategiesGridCardPanel.add(new GapRocketPanel(this::openGapRocketAnalysisDialog, true), GAP_ROCKET_EMPTY_CARD);
        strategiesGridCardPanel.add(new OrbPanel(this::openOrbAnalysisDialog), ORB_EMPTY_CARD);
        strategiesGridCardPanel.add(new DipHunterPanel(this::openDipHunterAnalysisDialog, true), DIP_HUNTER_EMPTY_CARD);
        strategiesGridCardPanel.add(new VwapPanel(this::openVwapAnalysisDialog, true), VWAP_EMPTY_CARD);
        strategiesGridCardPanel.add(new SwingPanel(this::openSwingAnalysisDialog, true), SWING_EMPTY_CARD);
        strategiesGridCardLayout.show(strategiesGridCardPanel, STRATEGIES_GRID_CARD);
        return strategiesGridCardPanel;
    }

    private JPanel createStrategiesBottomPanel() {
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.add(workspaceSummaryLabel, BorderLayout.CENTER);
        gapRocketAnalyzeButton.setVisible(false);
        gapRocketAnalyzeButton.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
        gapRocketAnalyzeButton.setFocusPainted(false);
        gapRocketAnalyzeButton.setToolTipText(TooltipStyler.text(GapRocketPanel.EMPTY_STATE_TEXT, 420));
        gapRocketAnalyzeButton.addActionListener(event -> openGapRocketAnalysisDialog());
        gapRocketPlaceOrdersButton.setVisible(false);
        gapRocketPlaceOrdersButton.setFont(BASE_FONT.deriveFont(Font.BOLD, 11f));
        gapRocketPlaceOrdersButton.setFocusPainted(false);
        gapRocketPlaceOrdersButton.setToolTipText(TooltipStyler.text("Submit Alpaca limit buy orders for all Gap Rocket rows still pending order placement. Uses each row's base buy price and current Paper/Live mode."));
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
        bottom.add(actions, BorderLayout.EAST);
        return bottom;
    }

    private void refreshNewStrategyButtonPresentation() {
        boolean historySelected = strategyWorkspaceTabs != null && strategyWorkspaceTabs.isHistorySelected();
        addStrategyButton.setVisible(!historySelected);
        String targetLabel = selectedWorkspaceId == null
                ? "All Stocks"
                : workspaceService.findById(selectedWorkspaceId).map(StrategyWorkspace::name).orElse("This Tab");
        addStrategyButton.setText("New Strategy in " + abbreviateWorkspaceButtonLabel(targetLabel));
        addStrategyButton.setToolTipText(TooltipStyler.text(
                "Add a new stock strategy (symbol, entry, stop, target, and automation) directly into "
                        + targetLabel + " for " + selectedModeLabel() + " mode.",
                360
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
        String marketValueText = formatMarketValueText();
        String investedValueText = formatInvestedValueText();
        String baseBuyPendingTotalText = formatBaseBuyPendingTotalText();
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
                        marketValueText,
                        investedValueText,
                        availableFundsText,
                        baseBuyPendingTotalText,
                        cpuText,
                        memoryText
                )
        );
        SwingUtilities.invokeLater(() -> {
            refreshCurrentStrategiesHeading();
            refreshWorkspaceSummary();
            statusStrategyCount.setText(statusBarViewModel.strategyCountText());
            pollingSummary.setText(statusBarViewModel.pollingText());
            pollingSummary.setForeground(statusToneColor(statusBarViewModel.pollingTone()));
            marketStatus.setText(statusBarViewModel.marketText());
            marketStatus.setForeground(statusToneColor(statusBarViewModel.marketTone()));
            marketStatus.setToolTipText(TooltipStyler.text(statusBarViewModel.marketTooltip()));
            availableFundsStatus.setText(statusBarViewModel.availableFundsText());
            marketValueStatus.setText(statusBarViewModel.marketValueText());
            investedValueStatus.setText(statusBarViewModel.investedValueText());
            baseBuyPendingStatus.setText(statusBarViewModel.baseBuyPendingText());
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
        boolean showGapRocketEmptyState = selectedGapRocket && selectedWorkspaceStrategyCount() == 0;
        boolean showOrbEmptyState = selectedOrb && selectedWorkspaceStrategyCount() == 0;
        boolean showDipHunterEmptyState = selectedDipHunter && selectedWorkspaceStrategyCount() == 0;
        boolean showVwapEmptyState = selectedVwap && selectedWorkspaceStrategyCount() == 0;
        boolean showSwingEmptyState = selectedSwing && selectedWorkspaceStrategyCount() == 0;
        gapRocketAnalyzeButton.setVisible(selectedGapRocket && !showGapRocketEmptyState);
        gapRocketPlaceOrdersButton.setVisible(selectedGapRocket && !showGapRocketEmptyState);
        orbAnalyzeButton.setVisible(selectedOrb && !showOrbEmptyState);
        dipHunterAnalyzeButton.setVisible(selectedDipHunter && !showDipHunterEmptyState);
        vwapAnalyzeButton.setVisible(selectedVwap && !showVwapEmptyState);
        swingAnalyzeButton.setVisible(selectedSwing && !showSwingEmptyState);
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
        if (showGapRocketEmptyState) {
            strategiesGridCardLayout.show(strategiesGridCardPanel, GAP_ROCKET_EMPTY_CARD);
        } else if (showOrbEmptyState) {
            strategiesGridCardLayout.show(strategiesGridCardPanel, ORB_EMPTY_CARD);
        } else if (showDipHunterEmptyState) {
            strategiesGridCardLayout.show(strategiesGridCardPanel, DIP_HUNTER_EMPTY_CARD);
        } else if (showVwapEmptyState) {
            strategiesGridCardLayout.show(strategiesGridCardPanel, VWAP_EMPTY_CARD);
        } else if (showSwingEmptyState) {
            strategiesGridCardLayout.show(strategiesGridCardPanel, SWING_EMPTY_CARD);
        } else {
            strategiesGridCardLayout.show(strategiesGridCardPanel, STRATEGIES_GRID_CARD);
        }
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

    private boolean isSelectedSwingWorkspace() {
        if (selectedWorkspaceId == null) {
            return false;
        }
        return workspaceService.findById(selectedWorkspaceId)
                .map(StrategyWorkspace::code)
                .map(SWING_WORKSPACE_CODE::equalsIgnoreCase)
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
        int submitted = 0;
        int skipped = 0;
        List<Strategy> pending = strategyRepository.findAll().stream()
                .filter(strategy -> strategy.mode() == selectedViewMode)
                .filter(strategy -> selectedWorkspaceId.equals(strategy.workspaceId()))
                .filter(this::isGapRocketPendingOrderPlacement)
                .toList();
        for (Strategy strategy : pending) {
            StrategyService.StrategyCreationResult result = service.createAndActivate(strategy);
            if (result.success()) {
                submitted++;
                log("[Gap Rocket] Submitted base limit buy for " + strategy.symbol()
                        + " @ $" + strategy.baseBuyLimitPrice().toPlainString()
                        + ", clientOrderId=" + result.clientOrderId());
            } else {
                skipped++;
                log("[Gap Rocket] Failed to submit base limit buy for " + strategy.symbol() + ": " + result.error());
            }
        }
        syncStrategiesFromRepository();
        refreshStrategyTableData();
        applyCurrentStrategiesRowFilter();
        refreshWorkspaceSummary();
        updateStatusBar();
        JOptionPane.showMessageDialog(this,
                "Submitted " + submitted + " Gap Rocket limit buy order" + (submitted == 1 ? "" : "s") + "."
                        + (skipped > 0 ? "\nSkipped " + skipped + " row" + (skipped == 1 ? "" : "s") + " due to validation or broker errors." : ""),
                "Gap Rocket Orders",
                skipped > 0 ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
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

    /** Load any persisted schedules and start the autonomous schedulers. */
    public void startBackgroundSchedulers() {
        gapAndGoCoordinator.start();
        orbCoordinator.start();
        dipHunterCoordinator.start();
        vwapCoordinator.start();
        swingCoordinator.start();
        autoRiskAdjustmentService.start();
    }

    /**
     * Latest cached price for a strategy, used by the after-close Auto Adjust Risk &amp; Stop Loss
     * runner. Reads the cached position snapshot only (no broker call); returns {@code null} when no
     * price is known yet so the adjuster safely skips that strategy for the day.
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

    /** Refresh the grid after the coordinator applies gap-and-go recommendations (called on the EDT). */
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

    /** Refresh the grid after the coordinator applies ORB recommendations (called on the EDT). */
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

    /** Refresh the grid after the coordinator applies Dip Hunter recommendations (called on the EDT). */
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

    /** Refresh the grid after the coordinator applies VWAP Desk recommendations (called on the EDT). */
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

    /** Reflect the current schedule on the Gap Rocket action bar (badge + cancel button). */
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

    /** Reflect the current ORB schedule on the action bar (badge + cancel button). */
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

    /** Reflect the current Dip Hunter schedule on the action bar (badge + cancel button). */
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

    /** Reflect the current VWAP Desk schedule on the action bar (badge + cancel button). */
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

    /** Reflect the current Swing Vault schedule on the action bar (badge + cancel button). */
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

    /** Bridges {@link GapAndGoCoordinator}'s needs to this frame without leaking the frame into it. */
    private final class GapAndGoCoordinatorUi implements GapAndGoCoordinator.Ui {
        @Override public String runtimeApiKey() { return runtimeApiKey; }
        @Override public String runtimeApiSecret() { return runtimeApiSecret; }
        @Override public boolean connectionOk() { return connectionOk; }
        @Override public String selectedModeLabel() { return TradingFrame.this.selectedModeLabel(); }
        @Override public int defaultStrategyPollingSeconds() { return settingsDialog.appliedDefaultStrategyPollingSeconds(); }
        @Override public String selectedWorkspaceId() { return selectedWorkspaceId; }
        @Override public boolean isGapRocketWorkspaceSelected() { return isSelectedGapRocketWorkspace(); }
        @Override public void log(String message) { TradingFrame.this.log(message); }
        @Override public void setScanButtonsEnabled(boolean enabled) {
            gapRocketAnalyzeButton.setEnabled(enabled);
            gapRocketPlaceOrdersButton.setEnabled(enabled);
        }
        @Override public void onRecommendationsApplied(String workspaceId, String firstAddedStrategyId) {
            onGapRocketRecommendationsApplied(workspaceId, firstAddedStrategyId);
        }
        @Override public void onScheduleChanged(GapAndGoSchedule schedule) { updateGapRocketScheduleBadge(schedule); }
        @Override public java.awt.Component dialogParent() { return TradingFrame.this; }
    }

    /** Bridges {@link OrbCoordinator}'s needs to this frame without leaking the frame into it. */
    private final class OrbCoordinatorUi implements OrbCoordinator.Ui {
        @Override public String runtimeApiKey() { return runtimeApiKey; }
        @Override public String runtimeApiSecret() { return runtimeApiSecret; }
        @Override public boolean connectionOk() { return connectionOk; }
        @Override public String selectedModeLabel() { return TradingFrame.this.selectedModeLabel(); }
        @Override public int defaultStrategyPollingSeconds() { return settingsDialog.appliedDefaultStrategyPollingSeconds(); }
        @Override public String selectedWorkspaceId() { return selectedWorkspaceId; }
        @Override public boolean isOrbWorkspaceSelected() { return isSelectedOrbWorkspace(); }
        @Override public void log(String message) { TradingFrame.this.log(message); }
        @Override public void setAnalyzeButtonEnabled(boolean enabled) { orbAnalyzeButton.setEnabled(enabled); }
        @Override public void onRecommendationsApplied(String workspaceId, String firstAddedStrategyId) {
            onOrbRecommendationsApplied(workspaceId, firstAddedStrategyId);
        }
        @Override public void onScheduleChanged(OrbSchedule schedule) { updateOrbScheduleBadge(schedule); }
        @Override public java.awt.Component dialogParent() { return TradingFrame.this; }
    }

    /** Bridges {@link DipHunterCoordinator}'s needs to this frame without leaking the frame into it. */
    private final class DipHunterCoordinatorUi implements DipHunterCoordinator.Ui {
        @Override public String runtimeApiKey() { return runtimeApiKey; }
        @Override public String runtimeApiSecret() { return runtimeApiSecret; }
        @Override public boolean connectionOk() { return connectionOk; }
        @Override public String selectedModeLabel() { return TradingFrame.this.selectedModeLabel(); }
        @Override public int defaultStrategyPollingSeconds() { return settingsDialog.appliedDefaultStrategyPollingSeconds(); }
        @Override public String selectedWorkspaceId() { return selectedWorkspaceId; }
        @Override public boolean isDipHunterWorkspaceSelected() { return isSelectedDipHunterWorkspace(); }
        @Override public void log(String message) { TradingFrame.this.log(message); }
        @Override public void setScanButtonsEnabled(boolean enabled) { dipHunterAnalyzeButton.setEnabled(enabled); }
        @Override public void onRecommendationsApplied(String workspaceId, String firstAddedStrategyId) {
            onDipHunterRecommendationsApplied(workspaceId, firstAddedStrategyId);
        }
        @Override public void onScheduleChanged(DipHunterSchedule schedule) { updateDipHunterScheduleBadge(schedule); }
        @Override public java.awt.Component dialogParent() { return TradingFrame.this; }
    }

    /** Bridges {@link VwapCoordinator}'s needs to this frame without leaking the frame into it. */
    private final class VwapCoordinatorUi implements VwapCoordinator.Ui {
        @Override public String runtimeApiKey() { return runtimeApiKey; }
        @Override public String runtimeApiSecret() { return runtimeApiSecret; }
        @Override public boolean connectionOk() { return connectionOk; }
        @Override public String selectedModeLabel() { return TradingFrame.this.selectedModeLabel(); }
        @Override public int defaultStrategyPollingSeconds() { return settingsDialog.appliedDefaultStrategyPollingSeconds(); }
        @Override public String selectedWorkspaceId() { return selectedWorkspaceId; }
        @Override public boolean isVwapWorkspaceSelected() { return isSelectedVwapWorkspace(); }
        @Override public void log(String message) { TradingFrame.this.log(message); }
        @Override public void setScanButtonsEnabled(boolean enabled) { vwapAnalyzeButton.setEnabled(enabled); }
        @Override public void onRecommendationsApplied(String workspaceId, String firstAddedStrategyId) {
            onVwapRecommendationsApplied(workspaceId, firstAddedStrategyId);
        }
        @Override public void onScheduleChanged(VwapSchedule schedule) { updateVwapScheduleBadge(schedule); }
        @Override public java.awt.Component dialogParent() { return TradingFrame.this; }
    }

    /** Bridges {@link SwingCoordinator}'s needs to this frame without leaking the frame into it. */
    private final class SwingCoordinatorUi implements SwingCoordinator.Ui {
        @Override public String runtimeApiKey() { return runtimeApiKey; }
        @Override public String runtimeApiSecret() { return runtimeApiSecret; }
        @Override public boolean connectionOk() { return connectionOk; }
        @Override public String selectedModeLabel() { return TradingFrame.this.selectedModeLabel(); }
        @Override public int defaultStrategyPollingSeconds() { return settingsDialog.appliedDefaultStrategyPollingSeconds(); }
        @Override public String selectedWorkspaceId() { return selectedWorkspaceId; }
        @Override public boolean isSwingWorkspaceSelected() { return isSelectedSwingWorkspace(); }
        @Override public void log(String message) { TradingFrame.this.log(message); }
        @Override public void setScanButtonsEnabled(boolean enabled) { swingAnalyzeButton.setEnabled(enabled); }
        @Override public void onRecommendationsApplied(String workspaceId, String firstAddedStrategyId) {
            onSwingRecommendationsApplied(workspaceId, firstAddedStrategyId);
        }
        @Override public void onScheduleChanged(SwingSchedule schedule) { updateSwingScheduleBadge(schedule); }
        @Override public java.awt.Component dialogParent() { return TradingFrame.this; }
    }

    // Builds the per-tab P&L summary for the selected workspace (or All Stocks) from cached
    // snapshots — no broker calls — and renders it in the summary row below the grid.
    private void refreshWorkspaceSummary() {
        if (workspaceSummaryLabel == null) {
            return;
        }
        WorkspaceAccounting.Snapshot snapshot = computeWorkspaceSnapshot(selectedWorkspaceId);
        String label = selectedWorkspaceId == null
                ? "All Stocks"
                : workspaceService.findById(selectedWorkspaceId).map(StrategyWorkspace::name).orElse("Workspace");
        workspaceSummaryLabel.setText(workspaceSummaryPresenter.summaryLine(label, snapshot));
    }

    // Opens the read-only risk dashboard: builds strategy-level risk analytics from cached
    // snapshots on the EDT, fetches Alpaca positions off-EDT for reconciliation, then renders.
    private void openRiskDashboard() {
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
            if (position.getTotalShares() > 0) {
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
        com.neuralarc.analytics.RiskAnalytics.Report riskReport = com.neuralarc.analytics.RiskAnalytics.analyze(holdings);
        java.util.List<com.neuralarc.analytics.RiskAnalytics.PositionRisk> positionRisks =
                com.neuralarc.analytics.RiskAnalytics.classify(positionInputs);
        HttpAlpacaClient client = alpacaClientForMode(selectedApplicationMode());
        String modeLabel = selectedModeLabel();

        new SwingWorker<java.util.List<ReconciliationService.SymbolPosition>, Void>() {
            @Override
            protected java.util.List<ReconciliationService.SymbolPosition> doInBackground() {
                java.util.List<ReconciliationService.SymbolPosition> broker = new java.util.ArrayList<>();
                if (client != null) {
                    for (com.neuralarc.api.AlpacaPositionData position : client.getPositions()) {
                        if (position.exists()) {
                            broker.add(new ReconciliationService.SymbolPosition(
                                    position.symbol(), position.quantity(), position.avgEntryPrice()));
                        }
                    }
                }
                return broker;
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
                RiskDashboardPanel panel = new RiskDashboardPanel(modeLabel, riskReport, positionRisks, reconciliation);
                new RiskDashboardDialog(TradingFrame.this, panel).setVisible(true);
            }
        }.execute();
    }

    private WorkspaceAccounting.Snapshot computeWorkspaceSnapshot(String workspaceId) {
        AccountingInputs inputs = buildAccountingInputs(selectedViewMode);
        return WorkspaceAccounting.forWorkspace(workspaceId, inputs.accounts(), inputs.sells());
    }

    /** Bundle of per-strategy accounts + realized sells for a mode — the single P&L input set. */
    private record AccountingInputs(
            java.util.List<WorkspaceAccounting.StrategyAccount> accounts,
            java.util.List<WorkspaceAccounting.RealizedSell> sells
    ) {
    }

    /**
     * Builds the canonical per-strategy accounting inputs for a mode — the single source of truth
     * shared by the top status bar (All Stocks aggregate), every tab summary, and Capture Portfolio.
     * Open P&L is taken from {@link StrategyOpenPnlCalculator} (Gap-Rocket suppression + zero-cost/
     * price guards) and realized from the replayed fills, so every consumer sees identical numbers.
     */
    private AccountingInputs buildAccountingInputs(StrategyMode mode) {
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.systemDefault());
        java.util.List<WorkspaceAccounting.StrategyAccount> accounts = new java.util.ArrayList<>();
        java.util.List<WorkspaceAccounting.RealizedSell> sells = new java.util.ArrayList<>();
        for (ManagedStrategy entry : strategies) {
            if (entry == null || entry.strategy == null || entry.strategy.mode() != mode) {
                continue;
            }
            if (!includeInCurrentStrategiesTab(entry)) {
                continue;
            }
            String entryWorkspaceId = entry.strategy.workspaceId();
            java.util.List<WorkspaceAccounting.RealizedSell> strategySells =
                    realizedSellsForStrategy(entryWorkspaceId, strategyOrderRepository.findByStrategyId(entry.strategy.id()), today);
            sells.addAll(strategySells);
            BigDecimal realized = BigDecimal.ZERO;
            for (WorkspaceAccounting.RealizedSell sell : strategySells) {
                realized = realized.add(sell.realizedPnl());
            }
            java.util.Optional<StrategyOpenPnlCalculator.Row> openRow = openPnlCalculator.openRow(entry);
            int shares = openRow.map(StrategyOpenPnlCalculator.Row::shares).orElse(0);
            BigDecimal unrealized = openRow.map(StrategyOpenPnlCalculator.Row::unrealizedPnl).orElse(BigDecimal.ZERO);
            BigDecimal marketValue = openRow.map(StrategyOpenPnlCalculator.Row::marketValue).orElse(BigDecimal.ZERO);
            accounts.add(new WorkspaceAccounting.StrategyAccount(
                    entryWorkspaceId, shares, unrealized, realized, marketValue, entry.strategy.estimatedTotalCapital()));
        }
        return new AccountingInputs(accounts, sells);
    }

    // Reconstructs realized P&L per individual sell (one RealizedSell per filled sell), replaying
    // fills to track average cost — mirrors realizedPnlForOrders but keeps each trade for win rate.
    private java.util.List<WorkspaceAccounting.RealizedSell> realizedSellsForStrategy(
            String workspaceId, List<StrategyOrder> orders, java.time.LocalDate today) {
        List<StrategyOrder> filledOrders = orders.stream()
                .filter(order -> order.status() == StrategyOrderStatus.FILLED || order.status() == StrategyOrderStatus.PARTIALLY_FILLED)
                .filter(order -> order.filledQuantity().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator
                        .comparing(StrategyOrder::filledAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(StrategyOrder::submittedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        java.util.List<WorkspaceAccounting.RealizedSell> result = new java.util.ArrayList<>();
        BigDecimal positionQty = BigDecimal.ZERO;
        BigDecimal averageCost = BigDecimal.ZERO;
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
            BigDecimal realized = Monetary.round(fillPrice.subtract(averageCost).multiply(sellQty));
            java.time.Instant when = order.filledAt() != null ? order.filledAt() : order.submittedAt();
            boolean isToday = when != null
                    && java.time.LocalDate.ofInstant(when, java.time.ZoneId.systemDefault()).equals(today);
            result.add(new WorkspaceAccounting.RealizedSell(workspaceId, realized, isToday));
            positionQty = positionQty.subtract(sellQty);
            if (positionQty.compareTo(BigDecimal.ZERO) == 0) {
                averageCost = BigDecimal.ZERO;
            }
        }
        return result;
    }

    /** Right-click a workspace tab to rename it or archive it (archive removes the tab, keeps records). */
    private void installWorkspaceTabContextMenu() {
        strategyTabs.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) { maybeShow(event); }
            @Override public void mouseReleased(MouseEvent event) { maybeShow(event); }

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
            return "Workspace - " + selectedModeLabel() + " (0)";
        }
        long count = currentStrategiesStockCountInWorkspace(workspace.id());
        return workspace.name() + " - " + selectedModeLabel() + " (" + count + ")";
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


    private String formatMarketValueText() {
        return systemMetricsPresenter.formatMarketValueText(strategies, selectedViewMode);
    }

    private String formatInvestedValueText() {
        return systemMetricsPresenter.formatInvestedValueText(strategies, selectedViewMode);
    }

    private String formatBaseBuyPendingTotalText() {
        return systemMetricsPresenter.formatBaseBuyPendingTotalText(
                strategies,
                strategyOrderRepository::findByStrategyId,
                selectedViewMode
        );
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
        portfolioCaptureController.shutdown();
        strategyPollingTimer.stop();
        uiPollingExecutor.shutdownNow();
        if (strategyPollingService != null) {
            strategyPollingService.shutdown();
        }
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
        HttpAlpacaClient client = alpacaClientForStrategyMode(strategy.mode());
        if (client == null) {
            return new Position(strategy.symbol());
        }
        Optional<com.neuralarc.api.AlpacaPositionData> remote = client.getPosition(strategy.symbol());
        BigDecimal latestPrice = remote.isPresent() && remote.get().exists()
                ? BigDecimal.ZERO
                : client.getLatestPrice(strategy.symbol());
        return BrokerSnapshotLoader.buildPositionSnapshot(strategy.symbol(), remote.orElse(null), latestPrice);
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

    private void appendLogEntry(String logEntry) {
        StyledDocument document = eventLog.getStyledDocument();
        SimpleAttributeSet attributes = new SimpleAttributeSet();
        StyleConstants.setForeground(attributes, logEntryColor(logEntry));
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

    private Color logEntryColor(String logEntry) {
        return isFailureLogEntry(logEntry)
                ? LOG_LINE_FAILURE
                : (logLineCount % 2 == 0) ? LOG_LINE_EVEN : LOG_LINE_ODD;
    }

    private boolean isFailureLogEntry(String logEntry) {
        if (logEntry == null) {
            return false;
        }
        String normalized = logEntry.toLowerCase();
        return normalized.contains(" failed.")
                || normalized.contains(" failed:")
                || normalized.contains(" buy failed")
                || normalized.contains(" rejected");
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
                    if (column == 6) {
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
                    } else if (column == 1) {
                        Object pnlValue = table.getModel().getValueAt(modelRow, 5);
                        setForeground(PnlCellStyleSupport.foregroundFor(pnlValue, table.getForeground()));
                    } else if (column == 9) {
                        setForeground(entrySourceTextColor(value, table.getForeground()));
                    } else {
                        setForeground(table.getForeground());
                    }
                }
            }
            if (!(column == 6
                    && modelRow >= 0
                    && modelRow < strategies.size()
                    && "rejected".equals(BrokerOrderStatusUtil.normalize(strategies.get(modelRow).strategy.latestOrderStatus())))) {
                setFont(getFont().deriveFont(Font.PLAIN));
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
            return switch (column) {
                case 6 -> RIGHT;
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
                            isAutoPausedForClosedMarket(strategy),
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

            // Apply alternating row background colors
            if (isSelected) {
                setBackground(TABLE_SELECTION_BG);
            } else {
                setBackground(row % 2 == 0 ? TABLE_ROW_BG_EVEN : TABLE_ROW_BG_ODD);
            }
            setBorder(tableCellBorder(5, 10, 5, 10));
            progressBar.setValue(viewModel.progress());
            progressBar.setBackground(viewModel.trackBackground());
            progressBar.setForeground(viewModel.progressForeground());
            countdownLabel.setForeground(viewModel.labelForeground());
            countdownLabel.setText(viewModel.labelText());
            String tooltipText = TooltipStyler.text(appendSessionHint(viewModel.tooltip(), strategy));
            setToolTipText(tooltipText);
            progressBar.setToolTipText(tooltipText);
            countdownLabel.setToolTipText(tooltipText);
            return this;
        }
    }

    private final class ActionsRenderer extends JPanel implements TableCellRenderer {
        private final JButton editButton = new JButton();
        private final JButton toggleButton = new JButton();
        private final JButton sellButton = new JButton();
        private final JButton promoteButton = new JButton();
        private final JButton deleteButton = new JButton();

        private ActionsRenderer() {
            super(new FlowLayout(FlowLayout.CENTER, StrategyGridActionLayout.BUTTON_GAP, 0));
            setOpaque(true);
            setBorder(new EmptyBorder(5, 0, 0, 0));
            applyButtonIcon(editButton, "icons/edit.svg", 12);
            applyButtonIcon(toggleButton, "icons/pause.svg", 13);
            applyButtonIcon(sellButton, "icons/sell-position.svg", 13);
            applyButtonIcon(promoteButton, "icons/add-stock-strategy.svg", 13);
            applyButtonIcon(deleteButton, "icons/delete.svg", 13);
            styleIconOnlyActionButton(editButton, new Color(82, 101, 132));
            styleIconOnlyActionButton(toggleButton, new Color(180, 122, 42));
            styleIconOnlyActionButton(sellButton, new Color(71, 85, 105));
            styleIconOnlyActionButton(promoteButton, new Color(37, 99, 235));
            styleIconOnlyActionButton(deleteButton, new Color(148, 62, 78));
            add(editButton);
            add(toggleButton);
            add(sellButton);
            add(promoteButton);
            add(deleteButton);
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


    private Color selectionAwareRowColor(boolean selected, int row) {
        if (selected) {
            return TABLE_SELECTION_BG;
        }
        return row % 2 == 0 ? TABLE_ROW_BG_EVEN : TABLE_ROW_BG_ODD;
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
        strategyTable.setRowSelectionInterval(viewRow, viewRow);
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
                () -> workspaceService.activeWorkspaces(selectedViewMode),
                this::assignStrategyRowToWorkspace
        ).show(event);
        return true;
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
                Map<String, Position> snapshots = hasStrategiesNeedingBrokerSnapshots(stored)
                        ? loadPositionSnapshotsForStrategies(stored)
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
