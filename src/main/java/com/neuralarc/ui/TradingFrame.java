package com.neuralarc.ui;

import com.neuralarc.analytics.*;
import com.neuralarc.api.HttpAlpacaClient;
import com.neuralarc.api.AlpacaTradeUpdateEvent;
import com.neuralarc.api.AlpacaTradingWebSocketClient;
import com.neuralarc.api.TradingApi;
import com.neuralarc.api.TradingApiFactory;
import com.neuralarc.model.*;
import com.neuralarc.api.HttpAlpacaMarketDataApi;
import com.neuralarc.service.AutoAnalyzeResultStore;
import com.neuralarc.service.FileStrategyExecutionEventRepository;
import com.neuralarc.service.FileStrategyOrderRepository;
import com.neuralarc.service.FileStrategyRepository;
import com.neuralarc.service.FeedbackEmailService;
import com.neuralarc.service.MarketHoursService;
import com.neuralarc.service.OnboardingStateStore;
import com.neuralarc.service.PersistentAggregatePnlStore;
import com.neuralarc.service.AppSettingsService;
import com.neuralarc.service.StrategyPollingService;
import com.neuralarc.service.StrategyService;
import com.neuralarc.service.StrategyValidator;
import com.neuralarc.service.UserIdentityService;
import com.neuralarc.util.AppMetadata;
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
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

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
    private final JLabel marketStatus = new JLabel("Market: Unknown");
    private final JLabel streamStatus = new JLabel("Trade Stream: idle");
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
    private static final Color LOG_LINE_EVEN = new Color(63, 72, 82);
    private static final Color LOG_LINE_ODD = new Color(110, 118, 128);
    private static final Color HEADER_STATUS_DEFAULT = new Color(220, 220, 255);
    private static final Color HEADER_STATUS_LIVE_ALERT = new Color(255, 82, 82);
    private static final Color HEADER_STATUS_LIVE_ALERT_DIM = new Color(255, 205, 210);
    private static final Color HEADER_STATUS_LIVE_ACTIVE = new Color(46, 125, 50);
    private static final Color HEADER_STATUS_LIVE_ACTIVE_DIM = Color.WHITE;
    private final JTextPane eventLog = new JTextPane();
    private final JButton addStrategyButton = new JButton("Add New Stock Strategy");
    private final JButton settingsButton = new JButton("Settings");
    private final JButton legalDisclosureButton = new JButton("Legal Disclosure");
    private final Timer liveModeBlinkTimer;
    private final Timer logFlushTimer;
    private final Timer pollingIndicatorTimer;
    private final Timer strategyPollingTimer;
    private final AppSettingsService appSettingsService = new AppSettingsService();
    private final MarketHoursService marketHoursService = new MarketHoursService();
    private final Path appLogFile = AppMetadata.appDataDirectory().resolve("app.log");
    private final Path strategiesFilePath = AppMetadata.appDataDirectory().resolve("strategies-v2.json");
    private static final Path LEGAL_DISCLOSURE_FILE = AppMetadata.appDataDirectory().resolve("legal-disclosure.properties");
    private static final String LEGAL_DISCLOSURE_TEXT = String.join(System.lineSeparator(),
         "LEGAL DISCLOSURE AND USER RESPONSIBILITY AGREEMENT\n"
             + "\n"
             + "Last Updated: [Insert Date]\n"
             + "\n"
             + "This Legal Disclosure and User Responsibility Agreement (\"Agreement\") governs your use of the NeuralArc software application (\"NeuralArc\", \"Application\", or \"Software\"). By installing, accessing, or using NeuralArc, you acknowledge that you have read, understood, and agree to be bound by the terms set forth below.\n"
             + "\n"
             + "1. SERVICE FEE\n"
             + "NeuralArc may apply a service fee equal to twenty percent (20%) of net profit realized from each completed sell transaction executed through the Application.\n"
             + "\n"
             + "- No service fee shall be applied to transactions resulting in a loss.\n"
             + "- Profit calculations are based on data available to the Application and may not include taxes, brokerage fees, or third-party costs.\n"
             + "- NeuralArc reserves the right to modify its fee structure with reasonable notice.\n"
             + "\n"
             + "2. NO INVESTMENT ADVICE\n"
             + "NeuralArc is a software tool for trade execution and strategy automation only.\n"
             + "\n"
             + "- NeuralArc does not provide financial, investment, legal, or tax advice.\n"
             + "- All trading decisions are made solely by you.\n"
             + "- You are fully responsible for evaluating risks and outcomes of your strategies.\n"
             + "\n"
             + "3. ASSUMPTION OF RISK AND LOSS LIABILITY\n"
             + "You acknowledge and agree that:\n"
             + "\n"
             + "- Trading securities involves substantial risk, including total loss of capital.\n"
             + "- NeuralArc is not responsible for any losses, including but not limited to:\n"
             + "  - Market volatility\n"
             + "  - Slippage\n"
             + "  - Partial or missed order fills\n"
             + "  - Delayed execution\n"
             + "  - Connectivity failures\n"
             + "  - System errors or downtime\n"
             + "- NeuralArc does not guarantee profitability or performance.\n"
             + "\n"
             + "4. NO FIDUCIARY RELATIONSHIP\n"
             + "Use of NeuralArc does not create any fiduciary, advisory, or agency relationship between you and NeuralArc or its operators.\n"
             + "\n"
             + "- NeuralArc does not act in your best interest in a fiduciary capacity.\n"
             + "- You retain full control and responsibility for all actions taken.\n"
             + "\n"
             + "5. ALPACA ACCOUNT INTEGRATION\n"
             + "NeuralArc integrates with third-party brokerage services including Alpaca Markets.\n"
             + "\n"
             + "- Your brokerage account remains under your sole ownership and control.\n"
             + "- NeuralArc does not hold or custody funds or securities.\n"
             + "- API credentials are stored locally and used only to execute your instructions.\n"
             + "- You are responsible for securing your API keys and permissions.\n"
             + "\n"
             + "6. THIRD-PARTY SERVICES DISCLAIMER\n"
             + "NeuralArc depends on third-party services, including brokerage APIs and market data providers.\n"
             + "\n"
             + "- NeuralArc is not responsible for failures, inaccuracies, or interruptions from third-party services.\n"
             + "- Changes to third-party APIs may affect functionality.\n"
             + "\n"
             + "7. DATA STORAGE AND PRIVACY\n"
             + "- All strategy and application data is stored locally on your device by default.\n"
             + "- NeuralArc does not upload or store your strategies in the cloud unless explicitly enabled in future features.\n"
             + "- Optional telemetry, if enabled, is limited to system performance and operational metrics.\n"
             + "\n"
             + "8. DATA SECURITY DISCLAIMER\n"
             + "- NeuralArc does not guarantee protection against unauthorized access to your device.\n"
             + "- NeuralArc is not responsible for:\n"
             + "  - Data loss\n"
             + "  - Device compromise\n"
             + "  - Malware or external attacks\n"
             + "- You are responsible for maintaining device security and safe usage practices.\n"
             + "\n"
             + "9. BACKUP AND DATA INTEGRITY\n"
             + "- You are solely responsible for backing up your data.\n"
             + "- NeuralArc recommends regular export of strategies to avoid data loss.\n"
             + "- NeuralArc is not responsible for recovery of lost or corrupted data.\n"
             + "\n"
             + "10. AVAILABILITY AND SYSTEM RELIABILITY\n"
             + "- NeuralArc is provided \"as-is\" and \"as-available\".\n"
             + "- The Application may experience interruptions, delays, or errors.\n"
             + "- Continuous or error-free operation is not guaranteed.\n"
             + "\n"
             + "11. LIMITATION OF LIABILITY\n"
             + "To the fullest extent permitted by law:\n"
             + "\n"
             + "- NeuralArc and its developers shall not be liable for any damages, including:\n"
             + "  - Direct or indirect financial loss\n"
             + "  - Loss of profits\n"
             + "  - Loss of data\n"
             + "  - Loss of opportunity\n"
             + "- This applies regardless of cause, including negligence.\n"
             + "\n"
             + "12. INDEMNIFICATION\n"
             + "You agree to indemnify and hold harmless NeuralArc, its developers, and affiliates from any claims, damages, or liabilities arising from:\n"
             + "\n"
             + "- Your use of the Application\n"
             + "- Your trading activities\n"
             + "- Violation of this Agreement\n"
             + "\n"
             + "13. TAX RESPONSIBILITY DISCLAIMER\n"
             + "- You are solely responsible for reporting and paying any taxes related to your trading activities.\n"
             + "- NeuralArc does not provide tax reporting or guidance.\n"
             + "\n"
             + "14. OPEN SOURCE AND SOFTWARE LICENSE (IF APPLICABLE)\n"
             + "- Portions of NeuralArc may include open-source components governed by their respective licenses.\n"
             + "- You agree to comply with all applicable third-party license terms.\n"
             + "- NeuralArc itself may be distributed under a separate license, if provided.\n"
             + "\n"
             + "15. MODIFICATIONS AND UPDATES\n"
             + "- NeuralArc may update or modify this Agreement at any time.\n"
             + "- Continued use of the Application constitutes acceptance of updated terms.\n"
             + "\n"
             + "16. TERMINATION\n"
             + "- NeuralArc reserves the right to suspend or terminate access for misuse, violations, or security risks.\n"
             + "- You may discontinue use at any time.\n"
             + "\n"
             + "17. GOVERNING LAW\n"
             + "This Agreement shall be governed by applicable laws of the jurisdiction in which the Application operator resides, without regard to conflict of law principles.\n"
             + "\n"
             + "18. USER RESPONSIBILITY\n"
             + "You acknowledge that:\n"
             + "\n"
             + "- You are solely responsible for all trades executed through NeuralArc.\n"
             + "- You understand the risks associated with automated trading.\n"
             + "- You accept full responsibility for outcomes, including financial losses.\n"
             + "\n"
             + "19. ACKNOWLEDGMENT AND ACCEPTANCE\n"
             + "By selecting \"Accept\", installing, or using NeuralArc, you:\n"
             + "\n"
             + "- Confirm that you have read and understood this Agreement\n"
             + "- Accept all terms and conditions\n"
             + "- Agree to use the Application at your own risk");
    private boolean legalDisclosureAccepted;
    private final StringBuilder pendingLogWrites = new StringBuilder();

    private final UserIdentityService identityService = new UserIdentityService();
    private final List<ManagedStrategy> strategies = new ArrayList<>();
    private final StrategyTableModel strategyTableModel = new StrategyTableModel();
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

    private TradingApi tradingApi;
    private AnalyticsPublisher analyticsPublisher;
    private final SettingsDialog settingsDialog;
    private final FileStrategyRepository strategyRepository;
    private final FileStrategyOrderRepository strategyOrderRepository;
    private final FileStrategyExecutionEventRepository strategyEventRepository;
    private final PersistentAggregatePnlStore aggregatePnlStore;
    private boolean connectionOk;
    private boolean appLaunchedPublished;
    private String selectedStrategyId;
    private BrokerType currentBrokerType = BrokerType.ALPACA;
    private boolean preservingSelection;
    private Color liveBlinkPrimary = HEADER_STATUS_DEFAULT;
    private Color liveBlinkSecondary = HEADER_STATUS_DEFAULT;
    private boolean liveBlinkPrimaryActive;
    private int logLineCount;
    private boolean promptedDefaultStrategyDialog;
    private StrategyService strategyService;
    private StrategyPollingService strategyPollingService;
    private AlpacaTradingWebSocketClient tradingWebSocketClient;
    private long lastBrokerBackedUiRefreshAtMillis;
    private String runtimeApiKey = "";
    private String runtimeApiSecret = "";
    private final AutoAnalyzeResultStore autoAnalyzeResultStore = new AutoAnalyzeResultStore();
    private final OnboardingStateStore onboardingStateStore = new OnboardingStateStore();

    public TradingFrame() {
        liveModeBlinkTimer = new Timer(500, _ -> toggleLiveHeaderBlink());
        liveModeBlinkTimer.setInitialDelay(0);
        logFlushTimer = new Timer(10000, _ -> flushLogsToFile());
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
                strategiesFilePath
        );
        strategyOrderRepository = new FileStrategyOrderRepository(
                AppMetadata.appDataDirectory().resolve("strategy-orders.json")
        );
        strategyEventRepository = new FileStrategyExecutionEventRepository(
                AppMetadata.appDataDirectory().resolve("strategy-events.json")
        );
        aggregatePnlStore = new PersistentAggregatePnlStore(
                AppMetadata.appDataDirectory().resolve("aggregate-pnl.json")
        );
        legalDisclosureAccepted = loadLegalDisclosureAcceptance();
        refreshStrategyRuntimeServices(
                settingsDialog.savedApiKey(settingsDialog.appliedApplicationMode()),
                settingsDialog.savedApiSecret(settingsDialog.appliedApplicationMode()),
                settingsDialog.appliedApplicationMode()
        );
        settingsDialog.setStrategyExportHandler(this::exportStrategiesToFile);
        settingsDialog.setStrategyImportHandler(this::importStrategiesFromFile);
        strategyPollingTimer = new Timer(1000, e -> {
            strategyPollingService.pollDueStrategies();
            syncStrategiesFromRepository();
            if (shouldRunBrokerBackedUiRefresh()) {
                refreshStrategyTableContent();
                refreshPanels();
            }
            updateStatusBar();
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
        strategyTable.getColumnModel().getColumn(9).setPreferredWidth(380);
        strategyTable.getColumnModel().getColumn(9).setMinWidth(360);

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

                // Dispatch the action buttons (column 9 only) via four equal zones.
                // Use invokeLater so the action runs AFTER ALL mousePressed handlers
                // (ours + BasicTableUI) have finished — this is critical because:
                //   • BasicTableUI fires its own mousePressed AFTER ours (LIFO order).
                //   • Without deferral, dialogs opened here block BasicTableUI from
                //     ever running, leaving the table in a broken state on first click.
                if (e.getButton() != java.awt.event.MouseEvent.BUTTON1) return;
                if (viewRow < 0 || viewRow >= strategies.size() || viewCol != 9) return;
                java.awt.Rectangle cellRect = strategyTable.getCellRect(viewRow, viewCol, false);
                int xInCell  = e.getX() - cellRect.x;
                int section  = Math.max(1, cellRect.width / 4);
                final int capturedRow     = viewRow;
                final int capturedX       = xInCell;
                final int capturedSection = section;
                SwingUtilities.invokeLater(() -> {
                    if (capturedX < capturedSection) {
                        editStrategy(capturedRow);
                    } else if (capturedX < capturedSection * 2) {
                        togglePauseResume(capturedRow);
                    } else if (capturedX < capturedSection * 3) {
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
        TableRowSorter<StrategyTableModel> sorter = new TableRowSorter<>(strategyTableModel);
        sorter.setSortable(7, false); // Polling countdown bar column — not sortable
        sorter.setSortable(9, false); // Actions button column — not sortable
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


        // ── Status bar ─────────────────────────────────────────────────────────
        // ── Status bar ─────────────────────────────────────────────────────────
        statusBar.setFont(BASE_FONT.deriveFont(10f));
        statusBar.setForeground(BOTTOM_STATUS_ACCENT);
        statusBar.setVerticalAlignment(SwingConstants.CENTER);
        statusBar.setBorder(new EmptyBorder(0, 6, 0, 16));

        statusStrategyCount.setFont(BASE_FONT.deriveFont(Font.PLAIN, 12f));
        statusStrategyCount.setForeground(new Color(150, 150, 160));
        statusStrategyCount.setVerticalAlignment(SwingConstants.CENTER);
        statusStrategyCount.setBorder(new EmptyBorder(0, 0, 0, 12));
        marketStatus.setFont(BASE_FONT.deriveFont(Font.PLAIN, 11f));
        marketStatus.setForeground(BOTTOM_STATUS_ACCENT);
        marketStatus.setVerticalAlignment(SwingConstants.CENTER);
        marketStatus.setBorder(new EmptyBorder(0, 0, 0, 12));
        streamStatus.setFont(BASE_FONT.deriveFont(Font.PLAIN, 11f));
        streamStatus.setForeground(BOTTOM_STATUS_ACCENT);
        streamStatus.setVerticalAlignment(SwingConstants.CENTER);
        streamStatus.setBorder(new EmptyBorder(0, 12, 0, 0));
        cpuUsageStatus.setFont(BASE_FONT.deriveFont(Font.PLAIN, 11f));
        cpuUsageStatus.setForeground(BOTTOM_STATUS_ACCENT);
        cpuUsageStatus.setVerticalAlignment(SwingConstants.CENTER);
        cpuUsageStatus.setBorder(new EmptyBorder(0, 12, 0, 0));
        memoryUsageStatus.setFont(BASE_FONT.deriveFont(Font.PLAIN, 11f));
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
        statusLeft.add(marketStatus, leftGbc);
        leftGbc.gridx = 3;
        leftGbc.insets = new java.awt.Insets(0, 0, 0, 8);
        statusLeft.add(streamStatus, leftGbc);
        leftGbc.gridx = 4;
        leftGbc.insets = new java.awt.Insets(0, 0, 0, 8);
        statusLeft.add(cpuUsageStatus, leftGbc);
        leftGbc.gridx = 5;
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
                eventLogScrollPane, strategyGrid);
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
                false
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
        styleHeaderButton(settingsButton);
        applyButtonIcon(addStrategyButton, "icons/add-stock-strategy.svg", 16);
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
        settingsButton.addActionListener(e -> openSettingsDialog());
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
        ApplicationMode mode = settingsDialog.appliedApplicationMode();
        String apiKey = settingsDialog.savedApiKey(mode);
        String apiSecret = settingsDialog.savedApiSecret(mode);
        if (apiKey.isBlank() || apiSecret.isBlank()) {
            return false;
        }
        SettingsDialog.ConnectionResult result = runConnectionTest(
                settingsDialog.appliedBrokerType(),
                mode,
                apiKey,
                apiSecret,
                false,
                true
        );
        return result.connected();
    }

    private void refreshStrategyRuntimeServices(String apiKey, String apiSecret, ApplicationMode mode) {
        runtimeApiKey = apiKey == null ? "" : apiKey.trim();
        runtimeApiSecret = apiSecret == null ? "" : apiSecret;
        HttpAlpacaClient alpacaClient = new HttpAlpacaClient(
                runtimeApiKey,
                runtimeApiSecret,
                AppMetadata.alpacaTradingBaseUrl(mode),
                AppMetadata.alpacaDataUrl(),
                settingsDialog.appliedExtendedHoursTradingEnabled()
        );
        strategyService = new StrategyService(
                strategyRepository,
                strategyOrderRepository,
                strategyEventRepository,
                alpacaClient,
                new StrategyValidator(),
                AppMetadata.liveTradingEnabled(),
                mode == ApplicationMode.LIVE ? StrategyMode.LIVE : StrategyMode.PAPER,
                appSettingsService,
                marketHoursService
        );
        strategyPollingService = new StrategyPollingService(
                strategyRepository,
                strategyOrderRepository,
                strategyEventRepository,
                alpacaClient,
                appSettingsService,
                marketHoursService
        );
    }

    private SettingsDialog.ConnectionResult runConnectionTest(BrokerType brokerType, ApplicationMode mode, String apiKey, String apiSecret, boolean manualTrigger, boolean applyRuntimeChanges) {
        if (brokerType == null) {
            log("Connection test: FAILED (broker not set in Settings)");
            updateHeaderModeStatus(null);
            headerStatus.setText("Status: broker not configured");
            return new SettingsDialog.ConnectionResult(false, "Broker not configured");
        }
        if (mode == ApplicationMode.LIVE && !AppMetadata.liveTradingEnabled()) {
            String message = "LIVE mode is disabled. Set trading.live.enabled=true in app.properties.";
            settingsDialog.markConnectionStatus(false, message);
            setStatus(message, STATUS_ERR);
            return new SettingsDialog.ConnectionResult(false, message);
        }

        TradingApi candidateApi = TradingApiFactory.create(brokerType, mode);
        candidateApi.authenticate(apiKey, apiSecret);
        boolean connected = candidateApi.testConnection();
        log((manualTrigger ? "Connection test: " : "Auto connection test: ") + (connected ? "SUCCESS" : "FAILED"));
        if (connected) {
            settingsDialog.markConnectionStatus(true, "Connected to " + brokerType.name() + " (" + mode.name() + ")");
            if (applyRuntimeChanges) {
                tradingApi = candidateApi;
                currentBrokerType = brokerType;
                connectionOk = true;
                refreshStrategyRuntimeServices(apiKey, apiSecret, mode);
                startTradingEventStreamIfConfigured(apiKey, apiSecret);
                setStatus("Connected — broker " + brokerType.name() + " ready.", STATUS_OK);
                updateHeaderModeStatus(brokerType);
                updateStatusBar();
                initPersistenceAndRestore();
            }
            if (!applyRuntimeChanges) {
                return new SettingsDialog.ConnectionResult(true, "Connected to " + brokerType.name() + " (" + mode.name() + ")");
            }
            settingsDialog.markConnectionStatus(true, "Connected to " + brokerType.name());
            return new SettingsDialog.ConnectionResult(true, "Connected to " + brokerType.name());
        } else {
            if (applyRuntimeChanges) {
                stopTradingEventStream();
                connectionOk = false;
                setStatus("Connection failed — check API credentials in Settings.", STATUS_ERR);
                updateHeaderModeStatus(brokerType);
                updateStatusBar();
            }
            settingsDialog.markConnectionStatus(false, "Connection failed");
            return new SettingsDialog.ConnectionResult(false, "Connection failed");
        }
    }

    private void initPersistenceAndRestore() {
        ensureAnalyticsPublisher();
        restoreStrategies();
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
            String content = Files.exists(strategiesFilePath) ? Files.readString(strategiesFilePath) : "[]";
            JSONArray parsed = new JSONArray(content.isBlank() ? "[]" : content);
            Files.writeString(targetPath, parsed.toString(2));
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
            Files.createDirectories(strategiesFilePath.getParent());
            Files.writeString(strategiesFilePath, parsed.toString());
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

    private void togglePauseResume(int viewRow) {
        int row = strategyTable.convertRowIndexToModel(viewRow);
        if (row < 0 || row >= strategies.size()) {
            return;
        }

        ManagedStrategy entry = strategies.get(row);
        if (entry.strategy.status() == StrategyStatus.ARCHIVED) {
            return;
        }
        if (entry.isPauseResumeBusy()) {
            return;
        }
        boolean wasPaused = entry.isPaused();
        String strategyId = entry.strategy.id();
        String symbol = entry.strategy.symbol();
        entry.setPauseResumeBusy(true);
        entry.setPauseResumeBusyText(wasPaused ? "Resuming..." : "Pausing...");
        refreshStrategyTableRow(row);

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                if (wasPaused) {
                    strategyService.resume(strategyId);
                } else {
                    strategyService.pause(strategyId);
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    if (wasPaused) {
                        log("Strategy resumed for symbol " + symbol);
                    } else {
                        stopPollingCountdown(entry);
                        log("Strategy paused for symbol " + symbol);
                        if (analyticsPublisher != null) {
                            analyticsPublisher.publish(new AnalyticsEvent("STRATEGY_PAUSED").put("symbol", symbol));
                        }
                    }

                    strategyRepository.findById(strategyId).ifPresent(updatedStrategy -> {
                        entry.syncFrom(updatedStrategy);
                        if (wasPaused) {
                            startPollingCountdown(entry);
                        } else {
                            resetPollingCountdown(entry);
                        }
                    });
                } catch (Exception ex) {
                    log("Pause/Resume failed for symbol " + symbol + ": " + ex.getMessage());
                } finally {
                    entry.setPauseResumeBusy(false);
                    entry.setPauseResumeBusyText("");
                    refreshStrategyTableRow(row);
                    updateStatusBar();
                    refreshPanels();
                }
            }
        };
        worker.execute();
    }

    private void previewLivePromotion(int viewRow) {
        int row = strategyTable.convertRowIndexToModel(viewRow);
        if (row < 0 || row >= strategies.size()) {
            return;
        }

        ManagedStrategy entry = strategies.get(row);
        if (entry.strategy.mode() != StrategyMode.PAPER || entry.strategy.status() == StrategyStatus.ARCHIVED) {
            return;
        }

        StrategyService.LivePromotionPreview preview = strategyService.previewLivePromotion(entry.strategy.id());
        Position paperPosition = loadPositionForStrategy(entry.strategy);
        String realizedPnl = Monetary.round(realizedPnlForStrategy(entry.strategy.id())).toPlainString();
        String unrealizedPnl = Monetary.round(paperPosition.unrealizedPnl()).toPlainString();
        LivePromotionDialog dialog = new LivePromotionDialog(this, preview, realizedPnl, unrealizedPnl);
        if (!dialog.showDialog()) {
            return;
        }

        StrategyService.LivePromotionResult result = strategyService.promotePaperStrategyToLive(entry.strategy.id());
        if (!result.success()) {
            JOptionPane.showMessageDialog(
                    this,
                    result.error(),
                    "Live Promotion Failed",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        String cleanupSummary = "";
        if (dialog.shouldClosePaperPositions()) {
            cleanupSummary = closePaperAccountState(entry.strategy);
        }

        syncStrategiesFromRepository();
        refreshStrategyTableData();
        selectedStrategyId = result.liveStrategyId();
        restoreSelectedRow();
        updateSelectedStrategy();
        refreshPanels();
        updateStatusBar();
        log("[" + entry.strategy.symbol() + "] Promoted paper strategy to LIVE and archived the paper copy.");
        JOptionPane.showMessageDialog(
                this,
                "LIVE strategy created successfully.\nPaper strategy archived locally.\nLive Order ID: " + result.alpacaOrderId(),
                "Promotion Complete",
                JOptionPane.INFORMATION_MESSAGE
        );
        if (!cleanupSummary.isBlank()) {
            log("[" + entry.strategy.symbol() + "] " + cleanupSummary);
            JOptionPane.showMessageDialog(
                    this,
                    cleanupSummary,
                    "Paper Cleanup",
                    cleanupSummary.contains("skipped") ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE
            );
        }
    }

    private void deleteStrategy(int viewRow) {
        int row = strategyTable.convertRowIndexToModel(viewRow);
        if (row < 0 || row >= strategies.size()) {
            return;
        }

        ManagedStrategy entry = strategies.get(row);
        String statusLabel = entry.strategy.status().name();
        String modeLabel = entry.strategy.mode() == StrategyMode.PAPER ? "Paper Trading" : "Live Trading";
        String positionNote;
        if (currentBrokerType == BrokerType.ALPACA || tradingApi != null) {
            int shares = loadPositionForStrategy(entry.strategy).getTotalShares();
            positionNote = shares > 0
                    ? "• Open position: " + shares + " share(s) held — these will NOT be automatically sold."
                    : "• No open position.";
        } else {
            positionNote = "• Position data not available (broker not connected).";
        }
        String message = "<html><body style='width:340px'>"
                + "<b>Permanently delete the \"" + entry.strategy.symbol() + "\" strategy?</b><br><br>"
                + "• Status: " + statusLabel + "<br>"
                + "• Mode: " + modeLabel + "<br>"
                + positionNote + "<br><br>"
                + "This will immediately stop polling and permanently remove the strategy from saved data.<br>"
                + "This action <b>cannot be undone</b>."
                + "</body></html>";
        int choice = JOptionPane.showConfirmDialog(
                this,
                message,
                "Delete Strategy — " + entry.strategy.symbol(),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }

        BigDecimal realizedAtDeletion = realizedPnlForStrategy(entry.strategy.id());
        aggregatePnlStore.addArchivedRealized(entry.strategy.mode(), realizedAtDeletion);
        strategyService.delete(entry.strategy.id());
        strategies.remove(row);
        log("Deleted strategy for symbol " + entry.strategy.symbol());
        if (analyticsPublisher != null) {
            analyticsPublisher.publish(new AnalyticsEvent("STRATEGY_DELETED").put("symbol", entry.strategy.symbol()));
        }

        if (strategies.isEmpty()) {
            selectedStrategyId = null;
        } else {
            int nextModelRow = Math.min(row, strategies.size() - 1);
            selectedStrategyId = strategies.get(nextModelRow).strategy.id();
        }

        updateHeaderModeStatus(currentBrokerType);
        refreshStrategyTableData();
        if (selectedStrategyId != null) {
            restoreSelectedRow();
        } else {
            strategyTable.clearSelection();
        }
        updateStatusBar();
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
            String normalized = latestOrderStatus.trim().toUpperCase();
            if ("SUBMITTED".equals(normalized) || "PENDING".equals(normalized) || "PARTIALLY_FILLED".equals(normalized)) {
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
        if (state == null) {
            return "";
        }
        return switch (state) {
            case CREATED -> "Created";
            case VALIDATED -> "Validated";
            case BASE_BUY_PLACED -> "Limit Base Buy Placed";
            case BASE_BUY_PARTIALLY_FILLED -> "Limit Base Buy Partially Filled";
            case BASE_BUY_FILLED -> "Base Buy Filled";
            case BUY_LIMIT_1_PLACED -> "Limit Buy 1 Placed";
            case BUY_LIMIT_1_PARTIALLY_FILLED -> "Limit Buy 1 Partially Filled";
            case BUY_LIMIT_1_FILLED -> "Buy Limit 1 Filled";
            case BUY_LIMIT_2_PLACED -> "Limit Buy 2 Placed";
            case BUY_LIMIT_2_PARTIALLY_FILLED -> "Limit Buy 2 Partially Filled";
            case BUY_LIMIT_2_FILLED -> "Buy Limit 2 Filled";
            case STOP_LOSS_ACTIVE -> "Stop Loss Active";
            case PROFIT_HOLD_ACTIVE -> "Profit Hold Active";
            case SELL_PLACED -> "Limit Sell Placed";
            case SELL_PARTIALLY_FILLED -> "Limit Sell Partially Filled";
            case COMPLETED -> "Completed";
            case PAUSED -> "Paused";
            case FAILED -> "Failed";
            case STOPPED -> "Stopped";
        };
    }

    private String displayStatusLabel(Strategy strategy) {
        if (strategy == null) {
            return "";
        }
        if (strategy.status() == StrategyStatus.ARCHIVED) {
            return "Archived";
        }
        if (strategy.status() == StrategyStatus.ACTIVE && strategy.pauseReason() == PauseReason.MANUAL_MARKET_CLOSED_OVERRIDE) {
            return formatLifecycleStateForDisplay(strategy.currentState()) + " (Market Closed)";
        }
        if (strategy.status() == StrategyStatus.PAUSED && strategy.pauseReason() == PauseReason.AUTO_MARKET_CLOSED) {
            return "Auto Paused (Market Closed)";
        }
        if (strategy.status() == StrategyStatus.PAUSED && strategy.pauseReason() == PauseReason.USER_PAUSED) {
            return "Paused";
        }
        if (strategy.status() == StrategyStatus.PAUSED && strategy.pauseReason() == PauseReason.SYSTEM_ERROR) {
            return "Paused (System Error)";
        }
        return formatLifecycleStateForDisplay(strategy.currentState());
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
        Position position = displayedPosition(entry);
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
                    : displayedPosition(strategy).unrealizedPnl();
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
        if (viewRow >= 0 && strategyTable.getSelectedRow() != viewRow) {
            strategyTable.setRowSelectionInterval(viewRow, viewRow);
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
            strategyTable.clearSelection();
            selectedStrategyId = null;
            return;
        }
        // Row count can change between polls; full refresh keeps sorter/model indexes consistent.
        rememberSelectedStrategy();
        preservingSelection = true;
        strategyTableModel.fireTableDataChanged();
        preservingSelection = false;
        SwingUtilities.invokeLater(() -> {
            restoreSelectedRow();
            strategyTable.repaint();
        });
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
        if (shouldShowPollingIndicator(entry)) {
            entry.countdownActive = true;
            long baseTime = entry.strategy.lastPolledAt() == null
                    ? System.currentTimeMillis()
                    : entry.strategy.lastPolledAt().toEpochMilli();
            entry.nextPollDueAtMillis = baseTime + entry.pollIntervalMillis;
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

    private int pollingProgressPercent(ManagedStrategy entry) {
        if (!shouldShowPollingIndicator(entry) || entry.pollIntervalMillis <= 0L) {
            return 0;
        }
        if (entry.nextPollDueAtMillis <= 0L) {
            entry.countdownActive = true;
            entry.nextPollDueAtMillis = System.currentTimeMillis() + entry.pollIntervalMillis;
        }
        long remainingMillis = Math.max(0L, entry.nextPollDueAtMillis - System.currentTimeMillis());
        int progress = (int) Math.min(100L, Math.round((remainingMillis * 100.0d) / entry.pollIntervalMillis));
        if (remainingMillis > 0L && progress == 0) {
            return 1;
        }
        return progress;
    }

    private long pollingSecondsRemaining(ManagedStrategy entry) {
        if (!shouldShowPollingIndicator(entry) || entry.pollIntervalMillis <= 0L) {
            return 0L;
        }
        if (entry.nextPollDueAtMillis <= 0L) {
            entry.countdownActive = true;
            entry.nextPollDueAtMillis = System.currentTimeMillis() + entry.pollIntervalMillis;
        }
        long remainingMillis = Math.max(0L, entry.nextPollDueAtMillis - System.currentTimeMillis());
        return (long) Math.ceil(remainingMillis / 1000.0d);
    }

    private boolean shouldShowPollingIndicator(ManagedStrategy entry) {
        return entry != null
                && (entry.strategy.status() == StrategyStatus.ACTIVE || isWaitingForFill(entry.strategy));
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
            statusBar.setText("Broker: " + message);
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
        boolean marketOpen = marketHoursService.isTradingSessionOpen(settings.extendedHoursTradingEnabled());
        String cpuText = formatCpuUsageText();
        String memoryText = formatMemoryUsageText();
        SwingUtilities.invokeLater(() -> {
            statusStrategyCount.setText("Strategies: Active " + running + " | Inactive " + inactive);
            marketStatus.setText("Market: " + (marketOpen ? "Open" : "Closed"));
            marketStatus.setForeground(marketOpen ? STATUS_OK : STATUS_WARN);
            cpuUsageStatus.setText(cpuText);
            memoryUsageStatus.setText(memoryText);
            if (!connectionOk) {
                statusBar.setText("Broker: Not connected");
                statusBar.setForeground(STATUS_ERR);
            } else if (running > 0) {
                statusBar.setText("Broker: Connected");
                statusBar.setForeground(STATUS_OK);
            } else if (inactive > 0) {
                statusBar.setText("Broker: Connected (No active strategies)");
                statusBar.setForeground(STATUS_WARN);
            } else {
                statusBar.setText("Broker: Connected (No strategies)");
                statusBar.setForeground(STATUS_WARN);
            }
        });
    }

    private String formatCpuUsageText() {
        try {
            com.sun.management.OperatingSystemMXBean osBean =
                    ManagementFactory.getPlatformMXBean(com.sun.management.OperatingSystemMXBean.class);
            if (osBean == null) {
                return "CPU: -";
            }
            double processCpuLoad = osBean.getProcessCpuLoad();
            if (processCpuLoad < 0.0) {
                return "CPU: -";
            }
            return String.format(Locale.US, "CPU: %.1f%%", processCpuLoad * 100.0d);
        } catch (Exception ex) {
            return "CPU: -";
        }
    }

    private String formatMemoryUsageText() {
        Runtime runtime = Runtime.getRuntime();
        long usedBytes = runtime.totalMemory() - runtime.freeMemory();
        long usedMb = usedBytes / (1024L * 1024L);
        return "Memory: " + usedMb + " MB";
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
        logFlushTimer.stop();
        pollingIndicatorTimer.stop();
        strategyPollingTimer.stop();
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
        if (tradingApi == null) {
            return entry.cachedPosition();
        }
        if (shouldSuppressBrokerBackedRefreshForClosedMarket()) {
            return entry.cachedPosition();
        }
        if (!entry.shouldRefreshDisplayedPosition()) {
            return entry.cachedPosition();
        }
        if (entry.strategy.status() != StrategyStatus.ACTIVE) {
            if (isWaitingForFill(entry.strategy)) {
                Position latest = entry.cachedPosition();
                BigDecimal latestPrice = latestPriceForStrategy(entry.strategy);
                if (latestPrice.compareTo(BigDecimal.ZERO) > 0) {
                    latest.setLastPrice(latestPrice);
                    entry.setCachedPosition(latest);
                }
                return latest;
            }
            return entry.cachedPosition();
        }
        Position latest = loadPositionForStrategy(entry.strategy);
        if (latest.getTotalShares() == 0 && isWaitingForFill(entry.strategy)) {
            BigDecimal latestPrice = latestPriceForStrategy(entry.strategy);
            if (latestPrice.compareTo(BigDecimal.ZERO) > 0) {
                latest.setLastPrice(latestPrice);
            }
        }
        entry.setCachedPosition(latest);
        return latest;
    }

    private Position loadPositionForStrategy(Strategy strategy) {
        if (strategy == null) {
            return new Position("");
        }
        if (currentBrokerType != BrokerType.ALPACA) {
            return tradingApi == null ? new Position(strategy.symbol()) : tradingApi.getPosition(strategy.symbol());
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

    private BigDecimal latestPriceForStrategy(Strategy strategy) {
        if (strategy == null) {
            return BigDecimal.ZERO;
        }
        if (currentBrokerType != BrokerType.ALPACA) {
            return tradingApi == null ? BigDecimal.ZERO : tradingApi.getLatestPrice(strategy.symbol());
        }
        HttpAlpacaClient client = alpacaClientForStrategyMode(strategy.mode());
        return client == null ? BigDecimal.ZERO : client.getLatestPrice(strategy.symbol());
    }

    private HttpAlpacaClient alpacaClientForStrategyMode(StrategyMode mode) {
        ApplicationMode applicationMode = mode == StrategyMode.LIVE ? ApplicationMode.LIVE : ApplicationMode.PAPER;
        String apiKey = settingsDialog.savedApiKey(applicationMode);
        String apiSecret = settingsDialog.savedApiSecret(applicationMode);
        if (apiKey.isBlank() || apiSecret.isBlank()) {
            if (applicationMode == settingsDialog.appliedApplicationMode() && !runtimeApiKey.isBlank() && !runtimeApiSecret.isBlank()) {
                apiKey = runtimeApiKey;
                apiSecret = runtimeApiSecret;
            } else {
                return null;
            }
        }
        return new HttpAlpacaClient(
                apiKey,
                apiSecret,
                AppMetadata.alpacaTradingBaseUrl(applicationMode),
                AppMetadata.alpacaDataUrl(),
                settingsDialog.appliedExtendedHoursTradingEnabled()
        );
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
                Position p = displayedPosition(entry);
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
                case 0 -> entry.strategy.symbol();
                case 1 -> displayStatusLabel(entry.strategy);
                case 2 -> "-";
                case 3 -> "-";
                case 4 -> "-";
                case 5 -> "-";
                case 6 -> "-";
                case 7 -> entry.strategy.pollingIntervalSeconds();
                case 8 -> gridBrokerModeLabel(entry.strategy);
                case 9 -> displayStatusLabel(entry.strategy);
                default -> "";
            };
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false; // Actions are handled via table MouseListener; no cell editor needed.
        }
    }

    private static final class ManagedStrategy {
        private Strategy strategy;
        private Position cachedPosition;
        private volatile long lastDisplayedPositionFetchAtMillis;
        private volatile long pollIntervalMillis;
        private volatile long nextPollDueAtMillis;
        private volatile boolean countdownActive;
        private volatile boolean pauseResumeBusy;
        private volatile String pauseResumeBusyText = "";

        private ManagedStrategy(Strategy strategy) {
            this.strategy = strategy;
            this.cachedPosition = new Position(strategy.symbol());
        }

        private void syncFrom(Strategy strategy) {
            this.strategy = strategy;
        }

        private boolean isPaused() {
            return strategy.status() == StrategyStatus.PAUSED;
        }

        private String pauseLabel() {
            if (strategy.pauseReason() == PauseReason.AUTO_MARKET_CLOSED) {
                return "Market Closed";
            }
            if (strategy.pauseReason() == PauseReason.SYSTEM_ERROR) {
                return "System Error";
            }
            return "Paused";
        }

        private String pauseTooltip() {
            if (strategy.pauseReason() == PauseReason.AUTO_MARKET_CLOSED) {
                return "Polling auto-paused because the market is closed";
            }
            if (strategy.pauseReason() == PauseReason.SYSTEM_ERROR) {
                return "Polling paused because of a system error";
            }
            return "Polling paused by the user";
        }

        private boolean isPauseResumeBusy() {
            return pauseResumeBusy;
        }

        private void setPauseResumeBusy(boolean pauseResumeBusy) {
            this.pauseResumeBusy = pauseResumeBusy;
        }

        private String pauseResumeBusyText() {
            return pauseResumeBusyText;
        }

        private void setPauseResumeBusyText(String pauseResumeBusyText) {
            this.pauseResumeBusyText = pauseResumeBusyText == null ? "" : pauseResumeBusyText;
        }

        private StrategyConfig toConfig() {
            return new StrategyConfig(
                    strategy.symbol(),
                    strategy.baseBuyLimitPrice(),
                    strategy.baseBuyQuantity(),
                    strategy.automatedStopLossEnabled(),
                    strategy.stopLossPrice(),
                    strategy.targetSellPrice(),
                    strategy.buyLimit1Price(),
                    strategy.buyLimit1Quantity(),
                    strategy.buyLimit2Price(),
                    strategy.buyLimit2Quantity(),
                    strategy.lossBuyLevelsEnabled(),
                    strategy.optionalLossExitEnabled(),
                    strategy.optionalLossExitPrice(),
                    strategy.pollingIntervalSeconds(),
                    strategy.mode() == StrategyMode.PAPER,
                    strategy.profitHoldEnabled(),
                    strategy.profitHoldType(),
                    strategy.profitHoldPercent(),
                    strategy.profitHoldAmount(),
                    strategy.restartAfterExitEnabled()
            );
        }

        private Position cachedPosition() {
            return cachedPosition.copy();
        }

        private void setCachedPosition(Position position) {
            this.cachedPosition = position == null ? new Position(strategy.symbol()) : position.copy();
            this.lastDisplayedPositionFetchAtMillis = System.currentTimeMillis();
        }

        private boolean shouldRefreshDisplayedPosition() {
            long refreshIntervalMillis = Math.max(1L, strategy.pollingIntervalSeconds()) * 1000L;
            return lastDisplayedPositionFetchAtMillis == 0L
                    || System.currentTimeMillis() - lastDisplayedPositionFetchAtMillis >= refreshIntervalMillis;
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
            progressBar.setOpaque(false);
            progressBar.setBorder(BorderFactory.createEmptyBorder());
            progressBar.setStringPainted(false);
            // Fixed thin size — the wrapper panel enforces this height.
            progressBar.setPreferredSize(new Dimension(88, 6));
            progressBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 6));
            progressBar.setComponentOrientation(java.awt.ComponentOrientation.RIGHT_TO_LEFT);
            progressBar.setForeground(new Color(94, 53, 177));
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
            barWrapper.setPreferredSize(new Dimension(90, 0)); // fixed width, height from parent
            barWrapper.add(progressBar, new GridBagConstraints());
            add(barWrapper, BorderLayout.WEST);
            add(countdownLabel, BorderLayout.CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            int modelRow = table.convertRowIndexToModel(row);
            ManagedStrategy strategy = strategies.get(modelRow);
            int progress = pollingProgressPercent(strategy);
            long secondsRemaining = pollingSecondsRemaining(strategy);
            long totalSeconds = Math.max(1L, strategy.strategy.pollingIntervalSeconds());
            boolean showPollingProgress = shouldShowPollingIndicator(strategy);

            // Match the row background so no separate cell box is visible
            Color rowBg = selectionAwareRowColor(isSelected, table);
            setBackground(rowBg);

            progressBar.setValue(progress);
            // Track: transparent on selected row, very subtle on normal rows
            progressBar.setBackground(isSelected
                    ? new Color(TABLE_SELECTION_BAR_BG.getRed(), TABLE_SELECTION_BAR_BG.getGreen(),
                                TABLE_SELECTION_BAR_BG.getBlue(), 60)
                    : new Color(0, 0, 0, 0)); // fully transparent track on normal rows
            progressBar.setForeground(!showPollingProgress && strategy.isPaused()
                    ? STATUS_TEXT_PAUSED
                    : isSelected ? new Color(60, 30, 140) : new Color(94, 53, 177));
            countdownLabel.setForeground(isSelected ? TABLE_SELECTION_FG : table.getForeground());
            boolean closedMarketPaused = isAutoPausedForClosedMarket(strategy) && shouldSuppressBrokerBackedRefreshForClosedMarket();
            countdownLabel.setText(closedMarketPaused
                    ? "Market Closed"
                    : showPollingProgress
                    ? secondsRemaining + "s / " + totalSeconds + "s"
                    : strategy.isPaused()
                    ? strategy.pauseLabel()
                    : "Idle");
            String tooltipText = TooltipStyler.text(closedMarketPaused
                    ? "Polling is paused because the market is closed. Alpaca refresh calls are suppressed until the next trading session opens."
                    : showPollingProgress
                    ? secondsRemaining + " seconds remaining out of " + totalSeconds + " seconds"
                    : strategy.isPaused()
                    ? strategy.pauseTooltip()
                    : "Polling is idle for this strategy");
            if (closedMarketPaused) {
                progressBar.setValue(0);
                progressBar.setForeground(STATUS_TEXT_PAUSED);
            }
            setToolTipText(tooltipText);
            progressBar.setToolTipText(tooltipText);
            countdownLabel.setToolTipText(tooltipText);
            return this;
        }
    }

    private final class ActionsRenderer extends JPanel implements TableCellRenderer {
        private final JButton editButton = new JButton("Edit");
        private final JButton toggleButton = new JButton();
        private final JButton promoteButton = new JButton("Live");
        private final JButton deleteButton = new JButton("Delete");

        private ActionsRenderer() {
            super(new GridLayout(1, 4, 6, 0));
            setOpaque(true);
            applyButtonIcon(editButton, "icons/edit.svg", 13);
            applyButtonIcon(toggleButton, "icons/pause.svg", 13);
            applyButtonIcon(promoteButton, "icons/add-stock-strategy.svg", 13);
            applyButtonIcon(deleteButton, "icons/delete.svg", 13);
            styleActionButton(editButton, new Color(63, 81, 181));
            styleActionButton(toggleButton, new Color(198, 40, 40));
            styleActionButton(promoteButton, new Color(25, 118, 210));
            styleActionButton(deleteButton, new Color(156, 39, 39));
            add(editButton);
            add(toggleButton);
            add(promoteButton);
            add(deleteButton);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            int modelRow = table.convertRowIndexToModel(row);
            ManagedStrategy strategy = strategies.get(modelRow);
            boolean paused = strategy.isPaused();
            boolean busy = strategy.isPauseResumeBusy();
            boolean archived = strategy.strategy.status() == StrategyStatus.ARCHIVED;
            boolean canPromote = strategy.strategy.mode() == StrategyMode.PAPER && !archived;
            toggleButton.setText(archived ? "Archived" : busy ? strategy.pauseResumeBusyText() : paused ? "Resume" : "Pause");
            styleActionButton(toggleButton, archived ? new Color(120, 144, 156)
                    : busy ? new Color(120, 144, 156)
                    : paused ? new Color(46, 125, 50) : new Color(198, 40, 40));
            toggleButton.setEnabled(!archived);
            promoteButton.setEnabled(canPromote);
            styleActionButton(promoteButton, canPromote ? new Color(25, 118, 210) : new Color(120, 144, 156));
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
        stopTradingEventStream();
        if (!AppMetadata.alpacaTradingEventsWebSocketEnabled()) {
            updateStreamStatus("disabled", new Color(150, 150, 160));
            return;
        }
        String streamUrl = AppMetadata.alpacaTradingEventsWebSocketUrl(
                settingsDialog.appliedApplicationMode() == ApplicationMode.LIVE
        );
        tradingWebSocketClient = new AlpacaTradingWebSocketClient(
                streamUrl,
                apiKey,
                apiSecret
        );
        if (!tradingWebSocketClient.isConfigured()) {
            updateStreamStatus("not configured", STATUS_WARN);
            return;
        }
        updateStreamStatus("connecting", new Color(180, 100, 0));
        tradingWebSocketClient.start(this::handleTradingStreamEvent,
                status -> {
                    log("[STREAM] " + status);
                    String normalized = status == null ? "" : status.toLowerCase();
                    if (normalized.contains("authorized")) {
                        updateStreamStatus("authorized", STATUS_OK);
                    } else if (normalized.contains("listening")) {
                        updateStreamStatus("listening", STATUS_OK);
                    } else if (normalized.contains("connected")) {
                        updateStreamStatus("connected", new Color(180, 100, 0));
                    } else {
                        updateStreamStatus(status, new Color(150, 150, 160));
                    }
                },
                ex -> {
                    log("[STREAM] Trade event stream error: " + ex.getMessage());
                    updateStreamStatus("error", STATUS_ERR);
                });
        log("[STREAM] Connected trading WebSocket.");
    }

    private void stopTradingEventStream() {
        if (tradingWebSocketClient == null) {
            return;
        }
        tradingWebSocketClient.stop();
        tradingWebSocketClient = null;
        updateStreamStatus("idle", new Color(150, 150, 160));
    }

    private void handleTradingStreamEvent(AlpacaTradeUpdateEvent event) {
        if (event == null || strategyPollingService == null) {
            return;
        }
        updateStreamStatus("trade update", new Color(46, 125, 50));
        log("[STREAM] Trade update received: event=" + event.eventType()
                + " orderId=" + event.orderData().orderId()
                + " clientOrderId=" + event.orderData().clientOrderId());
        strategyPollingService.onTradeUpdate(event);
        refreshDisplayedPositionFromStream(event.orderData().symbol());
        SwingUtilities.invokeLater(() -> {
            syncStrategiesFromRepository();
            refreshStrategyTableContent();
            refreshPanels();
            updateStatusBar();
        });
    }

    private void refreshDisplayedPositionFromStream(String symbol) {
        if (tradingApi == null || symbol == null || symbol.isBlank()) {
            return;
        }
        ManagedStrategy entry = findStrategy(symbol);
        if (entry == null) {
            return;
        }
        Position latest = tradingApi.getPosition(symbol);
        entry.setCachedPosition(latest);
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
        JDialog dialog = new JDialog(this, "Legal Disclosure", true);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.getRootPane().setBorder(new EmptyBorder(10, 10, 10, 10));

        JTextArea disclosureArea = new JTextArea(LEGAL_DISCLOSURE_TEXT);
        disclosureArea.setEditable(false);
        disclosureArea.setLineWrap(true);
        disclosureArea.setWrapStyleWord(true);
        disclosureArea.setCaretPosition(0);
        disclosureArea.setFont(FontLoader.ui(Font.PLAIN, 12f));
        JScrollPane disclosureScroll = new JScrollPane(disclosureArea);
        disclosureScroll.setPreferredSize(new Dimension(760, 440));

        JCheckBox acceptCheck = new JCheckBox("I have read and accept this legal disclosure.", legalDisclosureAccepted);
        boolean requiresScrollGate = requireAcceptance && !legalDisclosureAccepted;
        acceptCheck.setEnabled(!requiresScrollGate);
        JLabel scrollHint = new JLabel("Scroll to end to enable acceptance.");
        scrollHint.setFont(FontLoader.ui(Font.PLAIN, 11f));
        scrollHint.setForeground(new Color(180, 160, 110));
        scrollHint.setVisible(requiresScrollGate);

        JButton acceptButton = new JButton(requireAcceptance ? "Accept and Continue" : "Save Acceptance");
        DialogButtonStyles.apply(acceptButton, "icons/verify.svg");
        JButton closeButton = new JButton(requireAcceptance ? "Decline" : "Close");
        DialogButtonStyles.apply(closeButton, "icons/close.svg");

        final boolean[] accepted = new boolean[]{legalDisclosureAccepted};
        acceptButton.setEnabled(acceptCheck.isSelected());
        acceptCheck.addActionListener(e -> acceptButton.setEnabled(acceptCheck.isSelected()));

        if (requiresScrollGate) {
            JScrollBar verticalBar = disclosureScroll.getVerticalScrollBar();
            verticalBar.addAdjustmentListener(e -> {
                boolean atBottom = isScrolledToBottom(verticalBar);
                acceptCheck.setEnabled(atBottom);
                scrollHint.setVisible(!atBottom);
                if (!atBottom) {
                    acceptCheck.setSelected(false);
                    acceptButton.setEnabled(false);
                }
            });
            SwingUtilities.invokeLater(() -> {
                boolean atBottom = isScrolledToBottom(verticalBar);
                acceptCheck.setEnabled(atBottom);
                scrollHint.setVisible(!atBottom);
            });
        }

        acceptButton.addActionListener(e -> {
            legalDisclosureAccepted = acceptCheck.isSelected();
            saveLegalDisclosureAcceptance(legalDisclosureAccepted);
            updateLegalDisclosureUiState();
            accepted[0] = legalDisclosureAccepted;
            dialog.dispose();
        });
        closeButton.addActionListener(e -> {
            accepted[0] = legalDisclosureAccepted;
            dialog.dispose();
        });

        JPanel footer = new JPanel(new BorderLayout());
        footer.setBorder(new EmptyBorder(12, 4, 8, 4));
        JPanel footerLeft = new JPanel();
        footerLeft.setLayout(new BoxLayout(footerLeft, BoxLayout.Y_AXIS));
        footerLeft.setOpaque(false);
        acceptCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        scrollHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        footerLeft.add(acceptCheck);
        footerLeft.add(Box.createVerticalStrut(4));
        footerLeft.add(scrollHint);
        footer.add(footerLeft, BorderLayout.WEST);
        JPanel footerActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        footerActions.setBorder(new EmptyBorder(6, 0, 6, 0));
        footerActions.add(acceptButton);
        footerActions.add(closeButton);
        footer.add(footerActions, BorderLayout.EAST);

        dialog.add(disclosureScroll, BorderLayout.CENTER);
        dialog.add(footer, BorderLayout.SOUTH);
        dialog.setMinimumSize(new Dimension(790, 590));
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);

        return accepted[0];
    }

    private boolean isScrolledToBottom(JScrollBar bar) {
        int extent = bar.getModel().getExtent();
        int max = bar.getMaximum();
        int value = bar.getValue();
        return value + extent >= max;
    }

    private boolean loadLegalDisclosureAcceptance() {
        if (!Files.exists(LEGAL_DISCLOSURE_FILE)) {
            return false;
        }
        Properties properties = new Properties();
        try (var input = Files.newInputStream(LEGAL_DISCLOSURE_FILE)) {
            properties.load(input);
            return Boolean.parseBoolean(properties.getProperty("accepted", "false"));
        } catch (Exception ignored) {
            return false;
        }
    }

    private void saveLegalDisclosureAcceptance(boolean accepted) {
        Properties properties = new Properties();
        properties.setProperty("accepted", String.valueOf(accepted));
        properties.setProperty("acceptedAt", accepted ? Instant.now().toString() : "");
        try {
            Files.createDirectories(LEGAL_DISCLOSURE_FILE.getParent());
            try (var output = Files.newOutputStream(LEGAL_DISCLOSURE_FILE)) {
                properties.store(output, "NeuralArc legal disclosure acceptance");
            }
        } catch (Exception ignored) {
            // Keep app running even if acceptance state cannot be persisted.
        }
    }

    private void updateLegalDisclosureUiState() {
        legalDisclosureButton.setForeground(legalDisclosureAccepted
                ? new Color(220, 255, 220)
                : new Color(255, 235, 190));
    }
}
