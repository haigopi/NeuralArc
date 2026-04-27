package com.neuralarc.ui;

import com.neuralarc.analytics.*;
import com.neuralarc.api.HttpAlpacaClient;
import com.neuralarc.api.AlpacaTradeUpdateEvent;
import com.neuralarc.api.AlpacaTradingWebSocketClient;
import com.neuralarc.api.TradingApi;
import com.neuralarc.api.TradingApiFactory;
import com.neuralarc.model.*;
import com.neuralarc.service.FileStrategyExecutionEventRepository;
import com.neuralarc.service.FileStrategyOrderRepository;
import com.neuralarc.service.FileStrategyRepository;
import com.neuralarc.service.StrategyPollingService;
import com.neuralarc.service.StrategyService;
import com.neuralarc.service.StrategyValidator;
import com.neuralarc.service.UserIdentityService;
import com.neuralarc.util.AppMetadata;
import com.neuralarc.util.FontLoader;
import com.neuralarc.util.SvgIconLoader;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
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
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class TradingFrame extends JFrame {
    private static final Font BASE_FONT = createBaseFont();
    private static final int OUTER_PADDING = 16;
    private static final DateTimeFormatter LOG_DATE_FORMAT = DateTimeFormatter.ofPattern("EEE, MMM");
    private static final DateTimeFormatter LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a");
    private static final DateTimeFormatter RULE_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("EEE, MMM d yyyy, h:mm a");

    private final JLabel positionSummary = new JLabel("Position: -");
    private final JLabel ruleState = new JLabel("Rules: -");
    private final JLabel paperUnrealizedSummary = new JLabel("Paper Unrealized P&L Total: -");
    private final JLabel headerTotalsSeparator = new JLabel("|");
    private final JLabel liveUnrealizedSummary = new JLabel("Live Unrealized P&L Total: -");
    private final JLabel positionSectionTitle = new JLabel("Position");
    private final JLabel rulesSectionTitle = new JLabel("Rules Triggered");
    private final JLabel statusBar = new JLabel(" ● Not connected");
    private final JLabel statusStrategyCount = new JLabel("");
    private final JLabel streamStatus = new JLabel("Stream: idle");
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
    private final Timer liveModeBlinkTimer;
    private final Timer logFlushTimer;
    private final Timer pollingIndicatorTimer;
    private final Timer strategyPollingTimer;
    private final Path appLogFile = AppMetadata.appDataDirectory().resolve("app.log");
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
    private AlpacaTradingWebSocketClient tradingWebSocketClient;
    private long lastBrokerBackedUiRefreshAtMillis;
    private String runtimeApiKey = "";
    private String runtimeApiSecret = "";

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
                AppMetadata.appDataDirectory().resolve("strategies-v2.json")
        );
        strategyOrderRepository = new FileStrategyOrderRepository(
                AppMetadata.appDataDirectory().resolve("strategy-orders.json")
        );
        strategyEventRepository = new FileStrategyExecutionEventRepository(
                AppMetadata.appDataDirectory().resolve("strategy-events.json")
        );
        refreshStrategyRuntimeServices(settingsDialog.getApiKey(), settingsDialog.getApiSecret());
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
        strategyTable.getColumnModel().getColumn(9).setPreferredWidth(270);
        strategyTable.getColumnModel().getColumn(9).setMinWidth(270);

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

                // Dispatch the action button (column 9 only) via zone thirds.
                // Use invokeLater so the action runs AFTER ALL mousePressed handlers
                // (ours + BasicTableUI) have finished — this is critical because:
                //   • BasicTableUI fires its own mousePressed AFTER ours (LIFO order).
                //   • Without deferral, dialogs opened here block BasicTableUI from
                //     ever running, leaving the table in a broken state on first click.
                if (e.getButton() != java.awt.event.MouseEvent.BUTTON1) return;
                if (viewRow < 0 || viewRow >= strategies.size() || viewCol != 9) return;
                java.awt.Rectangle cellRect = strategyTable.getCellRect(viewRow, viewCol, false);
                int xInCell  = e.getX() - cellRect.x;
                int section  = cellRect.width / 3;
                final int capturedRow     = viewRow;
                final int capturedX       = xInCell;
                final int capturedSection = section;
                SwingUtilities.invokeLater(() -> {
                    if (capturedX < capturedSection) {
                        editStrategy(capturedRow);
                    } else if (capturedX < capturedSection * 2) {
                        togglePauseResume(capturedRow);
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
        statusBar.setForeground(new Color(200, 100, 100));
        statusBar.setVerticalAlignment(SwingConstants.CENTER);
        statusBar.setBorder(new EmptyBorder(0, 6, 0, 16));

        statusStrategyCount.setFont(BASE_FONT.deriveFont(Font.ITALIC, 12f));
        statusStrategyCount.setForeground(new Color(150, 150, 160));
        statusStrategyCount.setVerticalAlignment(SwingConstants.CENTER);
        statusStrategyCount.setBorder(new EmptyBorder(0, 0, 0, 12));
        streamStatus.setFont(BASE_FONT.deriveFont(Font.PLAIN, 11f));
        streamStatus.setForeground(new Color(150, 150, 160));
        streamStatus.setVerticalAlignment(SwingConstants.CENTER);
        streamStatus.setBorder(new EmptyBorder(0, 12, 0, 0));

        JButton faqsButton = new JButton("Faqs");
        applyButtonIcon(faqsButton, "icons/faqs.svg", 15);
        styleStatusActionButton(faqsButton);
        faqsButton.addActionListener(e -> new HelpDialog(this).setVisible(true));

        JButton submitFeatureButton = new JButton("Request New Feature");
        applyButtonIcon(submitFeatureButton, "icons/request-new-feature.svg", 15);
        styleStatusActionButton(submitFeatureButton);
        submitFeatureButton.addActionListener(e -> openFeedbackDialog("Request New Feature"));

        JButton contactUsButton = new JButton("Contact Us");
        applyButtonIcon(contactUsButton, "icons/contact-us.svg", 15);
        styleStatusActionButton(contactUsButton);
        contactUsButton.addActionListener(e -> openFeedbackDialog("Contact Us"));

        JLabel appLabel = new JLabel(AppMetadata.name() + "  " + AppMetadata.version() + " | Patent Pending™");
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
        leftGbc.insets = new java.awt.Insets(0, 0, 0, 0);
        statusLeft.add(streamStatus, leftGbc);

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
        settingsDialog.setConnectionVerifier(request -> runConnectionTest(request.brokerType(), request.apiKey(), request.apiSecret(), true));
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
        stopTradingEventStream();
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

    private void refreshStrategyRuntimeServices(String apiKey, String apiSecret) {
        runtimeApiKey = apiKey == null ? "" : apiKey.trim();
        runtimeApiSecret = apiSecret == null ? "" : apiSecret;
        ApplicationMode mode = settingsDialog.applicationMode();
        HttpAlpacaClient alpacaClient = new HttpAlpacaClient(
                runtimeApiKey,
                runtimeApiSecret,
                AppMetadata.alpacaTradingBaseUrl(mode),
                AppMetadata.alpacaDataUrl()
        );
        strategyService = new StrategyService(
                strategyRepository,
                strategyOrderRepository,
                strategyEventRepository,
                alpacaClient,
                new StrategyValidator(),
                AppMetadata.liveTradingEnabled(),
                mode == ApplicationMode.LIVE ? StrategyMode.LIVE : StrategyMode.PAPER
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
            refreshStrategyRuntimeServices(apiKey, apiSecret);
            startTradingEventStreamIfConfigured(apiKey, apiSecret);
            setStatus("Connected — broker " + brokerType.name() + " ready.", STATUS_OK);
            updateHeaderModeStatus(brokerType);
            settingsDialog.markConnectionStatus(true, "Connected to " + brokerType.name());
            updateStatusBar();
            initPersistenceAndRestore();
            return new SettingsDialog.ConnectionResult(true, "Connected to " + brokerType.name());
        } else {
            stopTradingEventStream();
            setStatus("Connection failed — check API credentials in Settings.", STATUS_ERR);
            updateHeaderModeStatus(brokerType);
            settingsDialog.markConnectionStatus(false, "Connection failed");
            updateStatusBar();
            return new SettingsDialog.ConnectionResult(false, "Connection failed");
        }
    }

    private void initPersistenceAndRestore() {
        ensureAnalyticsPublisher();
        restoreStrategies();
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
        boolean symbolExistsInRepository = strategyRepository.findAll().stream()
                .anyMatch(existing -> existing.symbol().equalsIgnoreCase(config.symbol()));
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
        selectedStrategySymbol = config.symbol();
        restoreSelectedRow();
        int selectedModelRow = strategyTable.getSelectedRow() >= 0
                ? strategyTable.convertRowIndexToModel(strategyTable.getSelectedRow())
                : strategies.size() - 1;
        int addedViewRow = selectedModelRow >= 0 ? strategyTable.convertRowIndexToView(selectedModelRow) : -1;
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
        StrategyDialog dialog = new StrategyDialog(this, entry.toConfig());
        StrategyConfig updated = dialog.showDialog();
        if (updated == null) {
            return;
        }

        ManagedStrategy duplicate = findStrategy(updated.symbol());
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
        strategyService.updateStrategy(updatedStrategy);
        entry.syncFrom(updatedStrategy);
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
        boolean wasPaused = entry.isPaused();
        String strategyId = entry.strategy.id();
        String symbol = entry.strategy.symbol();

        if (wasPaused) {
            strategyService.resume(strategyId);
            log("Strategy resumed for symbol " + symbol);
        } else {
            strategyService.pause(strategyId);
            stopPollingCountdown(entry);
            log("Strategy paused for symbol " + symbol);
            if (analyticsPublisher != null) {
                analyticsPublisher.publish(new AnalyticsEvent("STRATEGY_PAUSED").put("symbol", symbol));
            }
        }

        // After pause/resume, immediately fetch the updated strategy from repository
        // and sync it to the entry. This ensures the UI sees the state change immediately
        // on the first click, rather than waiting for the disk read to settle.
        strategyRepository.findById(strategyId).ifPresent(updatedStrategy -> {
            entry.syncFrom(updatedStrategy);
            if (wasPaused) {
                startPollingCountdown(entry);
            } else {
                resetPollingCountdown(entry);
            }
        });

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
        String statusLabel = entry.strategy.status().name();
        String modeLabel = entry.strategy.mode() == StrategyMode.PAPER ? "Paper Trading" : "Live Trading";
        String positionNote;
        if (tradingApi != null) {
            int shares = tradingApi.getPosition(entry.strategy.symbol()).getTotalShares();
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

        strategyService.delete(entry.strategy.id());
        strategies.remove(row);
        log("Deleted strategy for symbol " + entry.strategy.symbol());
        if (analyticsPublisher != null) {
            analyticsPublisher.publish(new AnalyticsEvent("STRATEGY_DELETED").put("symbol", entry.strategy.symbol()));
        }

        if (strategies.isEmpty()) {
            selectedStrategySymbol = null;
        } else {
            int nextModelRow = Math.min(row, strategies.size() - 1);
            selectedStrategySymbol = strategies.get(nextModelRow).strategy.symbol();
        }

        updateHeaderModeStatus(currentBrokerType);
        refreshStrategyTableData();
        if (selectedStrategySymbol != null) {
            restoreSelectedRow();
        } else {
            strategyTable.clearSelection();
        }
        updateStatusBar();
        refreshPanels();
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
                positionSummary.setText("[" + entry.strategy.symbol() + "]: Waiting Fill — order submitted to Alpaca (status: " + orderStatus + ")");
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
        String stateDisplay = formatLifecycleStateForDisplay(state);

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

    private String formatTimestampForDisplay(Instant timestamp) {
        ZonedDateTime zdt = timestamp.atZone(java.time.ZoneId.systemDefault());
        return zdt.format(RULE_TIMESTAMP_FORMAT);
    }

    private String buildRuleTriggeredSummary(Strategy strategy, StrategyOrder latestOrder, StrategyOrder pendingOrder) {
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
                + "<br><b>Buy Limit 1:</b> <= " + strategy.buyLimit1Price().toPlainString() + " x " + strategy.buyLimit1Quantity()
                + "<br><b>Buy Limit 2:</b> <= " + strategy.buyLimit2Price().toPlainString() + " x " + strategy.buyLimit2Quantity()
                + "<br><b>Stop Loss:</b> " + stopLossValue
                + "<br><b>Target Sell:</b> >= " + strategy.targetSellPrice().toPlainString()
                + "<br><b>Profit Hold:</b> " + profitHoldValue;
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
        if (strategies.isEmpty() || tradingApi == null) {
            paperUnrealizedSummary.setText("Paper Unrealized P&L Total: -");
            liveUnrealizedSummary.setText("Live Unrealized P&L Total: -");
            applyHeaderTotalsVisibility();
            return;
        }

        BigDecimal paperTotal = BigDecimal.ZERO;
        BigDecimal liveTotal = BigDecimal.ZERO;
        for (ManagedStrategy strategy : strategies) {
            Position position = displayedPosition(strategy);
            BigDecimal unrealized = position.unrealizedPnl();
            if (strategy.strategy.mode() == StrategyMode.PAPER) {
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

    private boolean updateSelectedStrategy() {
        int viewRow = strategyTable.getSelectedRow();
        if (viewRow < 0) {
            return false;
        }
        int modelRow = strategyTable.convertRowIndexToModel(viewRow);
        if (modelRow >= 0 && modelRow < strategies.size()) {
            String newSymbol = strategies.get(modelRow).strategy.symbol();
            boolean changed = selectedStrategySymbol == null || !selectedStrategySymbol.equalsIgnoreCase(newSymbol);
            selectedStrategySymbol = newSymbol;
            return changed;
        }
        return false;
    }

    private void restoreSelectedRow() {
        if (selectedStrategySymbol == null || strategies.isEmpty()) {
            return;
        }
        preservingSelection = true;
        int modelRow = -1;
        for (int i = 0; i < strategies.size(); i++) {
            if (strategies.get(i).strategy.symbol().equalsIgnoreCase(selectedStrategySymbol)) {
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
            selectedStrategySymbol = strategies.get(modelRow).strategy.symbol();
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
            strategyTableModel.fireTableDataChanged();
            strategyTable.clearSelection();
            selectedStrategySymbol = null;
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
        for (ManagedStrategy strategy : strategies) {
            if (strategy.strategy.symbol().equalsIgnoreCase(symbol)) {
                return strategy;
            }
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
        if (entry.strategy.status() == StrategyStatus.ACTIVE) {
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
        if (entry.strategy.status() != StrategyStatus.ACTIVE || entry.pollIntervalMillis <= 0L) {
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
        if (entry.strategy.status() != StrategyStatus.ACTIVE || entry.pollIntervalMillis <= 0L) {
            return 0L;
        }
        if (entry.nextPollDueAtMillis <= 0L) {
            entry.countdownActive = true;
            entry.nextPollDueAtMillis = System.currentTimeMillis() + entry.pollIntervalMillis;
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
        long running = strategies.stream().filter(s -> s.strategy.status() == StrategyStatus.ACTIVE).count();
        long paused  = strategies.stream().filter(s ->  s.strategy.status() == StrategyStatus.PAUSED).count();
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
        return strategies.stream().anyMatch(s -> s.strategy.mode() == StrategyMode.LIVE);
    }

    private Position displayedPosition(ManagedStrategy entry) {
        if (tradingApi == null) {
            return entry.cachedPosition();
        }
        if (entry.strategy.status() != StrategyStatus.ACTIVE) {
            return entry.cachedPosition();
        }
        if (!entry.shouldRefreshDisplayedPosition()) {
            return entry.cachedPosition();
        }
        Position latest = tradingApi.getPosition(entry.strategy.symbol());
        entry.setCachedPosition(latest);
        return latest;
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
                case 1 -> entry.strategy.status().name();
                case 2 -> "-";
                case 3 -> "-";
                case 4 -> "-";
                case 5 -> "-";
                case 6 -> "-";
                case 7 -> entry.strategy.pollingIntervalSeconds();
                case 8 -> gridBrokerModeLabel();
                case 9 -> entry.strategy.status().name();
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

        private StrategyConfig toConfig() {
            return new StrategyConfig(
                    strategy.symbol(),
                    strategy.baseBuyLimitPrice(),
                    strategy.baseBuyQuantity(),
                    strategy.stopLossPrice(),
                    strategy.targetSellPrice(),
                    strategy.buyLimit1Price(),
                    strategy.buyLimit1Quantity(),
                    strategy.buyLimit2Price(),
                    strategy.buyLimit2Quantity(),
                    strategy.pollingIntervalSeconds(),
                    strategy.mode() == StrategyMode.PAPER,
                    strategy.profitHoldEnabled(),
                    strategy.profitHoldType(),
                    strategy.profitHoldPercent(),
                    strategy.profitHoldAmount()
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
            long totalSeconds = Math.max(1L, strategy.strategy.pollingIntervalSeconds());

            setBackground(selectionAwareRowColor(isSelected, table));
            progressBar.setValue(progress);
            progressBar.setBackground(isSelected ? TABLE_SELECTION_BAR_BG : new Color(232, 236, 242));
            progressBar.setForeground(strategy.isPaused() ? STATUS_TEXT_PAUSED : new Color(94, 53, 177));
            countdownLabel.setForeground(isSelected ? TABLE_SELECTION_FG : table.getForeground());
            countdownLabel.setText(strategy.isPaused() ? "Paused" : secondsRemaining + "s / " + totalSeconds + "s");
            String tooltipText = TooltipStyler.text(strategy.isPaused()
                    ? "Polling paused"
                    : secondsRemaining + " seconds remaining out of " + totalSeconds + " seconds");
            setToolTipText(tooltipText);
            progressBar.setToolTipText(tooltipText);
            countdownLabel.setToolTipText(tooltipText);
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
            applyButtonIcon(editButton, "icons/edit.svg", 13);
            applyButtonIcon(toggleButton, "icons/pause.svg", 13);
            applyButtonIcon(deleteButton, "icons/delete.svg", 13);
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
            boolean paused = strategies.get(modelRow).isPaused();
            toggleButton.setText(paused ? "Resume" : "Pause");
            styleActionButton(toggleButton, paused ? new Color(46, 125, 50) : new Color(198, 40, 40));
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
                settingsDialog.applicationMode() == ApplicationMode.LIVE
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
            streamStatus.setText("Stream: " + status);
            if (color != null) {
                streamStatus.setForeground(color);
            }
        });
    }
}
