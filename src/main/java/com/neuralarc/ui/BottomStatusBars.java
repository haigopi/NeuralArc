package com.neuralarc.ui;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;

final class BottomStatusBars {
    private static final int COMPACT_THRESHOLD_PX = 1280;
    private static final String STATUS_CARD_FULL = "full";
    private static final String STATUS_CARD_COMPACT = "compact";
    private static final String STATUS_SEPARATOR = " • ";
    // The compact one-line summary uses plain spacing instead of a glyph separator.
    private static final String COMPACT_SEPARATOR = "   ";
    private static final ZoneId MARKET_TIME_ZONE = ZoneId.of("America/New_York");
    private static final DateTimeFormatter MARKET_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("EEE, MMM d h:mm a 'EST'", Locale.US);
    private static final Color ITEM_LABEL_COLOR = new Color(126, 132, 146);
    private static final int ITEM_TOOLTIP_WIDTH = 420;
    // Records, Funds, Market Value and Invested vs Upcoming stay on the first row when the bar wraps.
    private static final int PORTFOLIO_FIRST_ROW_ITEMS = 4;
    private static final int PORTFOLIO_RIGHT_GAP = 16;

    /** Each value label's caption, so both explain the figure on hover. */
    private final Map<JLabel, JLabel> captions = new HashMap<>();
    private final List<JPanel> portfolioItems = new ArrayList<>();
    private final JPanel portfolioRows = new JPanel();
    private boolean portfolioTwoRows;
    private final JPanel networkStatusRight;

    private final Font baseFont;
    private final Color accentColor;
    private final JLabel statusBar;
    private final JLabel marketStatus;
    private final JLabel streamStatus;
    private final JLabel pollingSummary;
    private final JLabel cpuUsageStatus;
    private final JLabel memoryUsageStatus;
    private final JLabel statusStrategyCount;
    private final JLabel availableFundsStatus;
    private final JLabel marketValueStatus;
    private final JLabel investedValueStatus;
    private final JLabel pendingBuyStatus;
    private final JLabel gainingPositionsStatus;
    private final JLabel losingPositionsStatus;
    private final JLabel pendingSellStatus;
    private final JLabel compactStatusSummary;
    private final JButton statusDetailsButton;
    private final NetworkConnectionStatusIndicator networkConnectionStatus;
    private final JLabel marketTimeStatus = new JLabel();
    private final Timer marketTimeTimer;

    private final JPanel statusBarPanel;
    private final JPanel portfolioStatusBarPanel;
    private final JPanel statusLeftCards;
    private boolean compactStatusMode;

    BottomStatusBars(
            Font baseFont,
            Color accentColor,
            Color initialBackground,
            JLabel statusBar,
            JLabel marketStatus,
            JLabel streamStatus,
            JLabel pollingSummary,
            JLabel cpuUsageStatus,
            JLabel memoryUsageStatus,
            JLabel statusStrategyCount,
            JLabel availableFundsStatus,
            JLabel marketValueStatus,
            JLabel investedValueStatus,
            JLabel pendingBuyStatus,
            JLabel gainingPositionsStatus,
            JLabel losingPositionsStatus,
            JLabel pendingSellStatus,
            JLabel compactStatusSummary,
            JButton statusDetailsButton,
            JPanel statusRight,
            StatusBarPresenter statusBarPresenter,
            BooleanSupplier streamReconnectAvailable,
            Runnable reconnectTradeStreamAction
    ) {
        this.baseFont = baseFont;
        this.accentColor = accentColor;
        this.statusBar = statusBar;
        this.marketStatus = marketStatus;
        this.streamStatus = streamStatus;
        this.pollingSummary = pollingSummary;
        this.cpuUsageStatus = cpuUsageStatus;
        this.memoryUsageStatus = memoryUsageStatus;
        this.statusStrategyCount = statusStrategyCount;
        this.availableFundsStatus = availableFundsStatus;
        this.marketValueStatus = marketValueStatus;
        this.investedValueStatus = investedValueStatus;
        this.pendingBuyStatus = pendingBuyStatus;
        this.gainingPositionsStatus = gainingPositionsStatus;
        this.losingPositionsStatus = losingPositionsStatus;
        this.pendingSellStatus = pendingSellStatus;
        this.compactStatusSummary = compactStatusSummary;
        this.statusDetailsButton = statusDetailsButton;
        this.networkConnectionStatus = new NetworkConnectionStatusIndicator(statusBarPresenter);
        this.marketTimeTimer = new Timer(30_000, ignored -> updateMarketTimeStatus());

        forceLeftAlignment();
        configureMarketTimeStatus();

        this.streamStatus.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (streamReconnectAvailable.getAsBoolean()) {
                    reconnectTradeStreamAction.run();
                }
            }
        });
        this.statusDetailsButton.addActionListener(e -> showStatusDetailsPopup(statusDetailsButton));

        JPanel statusLeft = buildMainStatusLeft();
        JPanel compactStatusLeft = buildCompactStatusLeft();

        this.statusLeftCards = new JPanel(new CardLayout());
        this.statusLeftCards.setOpaque(false);
        this.statusLeftCards.add(statusLeft, STATUS_CARD_FULL);
        this.statusLeftCards.add(compactStatusLeft, STATUS_CARD_COMPACT);

        this.statusBarPanel = new JPanel(new BorderLayout());
        this.statusBarPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(200, 200, 210)),
                BorderFactory.createEmptyBorder(4, 14, 4, 14)
        ));
        this.statusBarPanel.add(this.statusLeftCards, BorderLayout.WEST);
        this.statusBarPanel.add(statusRight, BorderLayout.EAST);
        this.statusBarPanel.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                updateLayoutMode();
            }
        });

        this.portfolioStatusBarPanel = new JPanel(new BorderLayout());
        this.portfolioStatusBarPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(76, 76, 90)),
                BorderFactory.createEmptyBorder(2, 14, 2, 14)
        ));
        this.networkStatusRight = buildNetworkStatusRight();
        this.portfolioStatusBarPanel.add(buildPortfolioLeft(), BorderLayout.WEST);
        this.portfolioStatusBarPanel.add(networkStatusRight, BorderLayout.EAST);
        this.portfolioStatusBarPanel.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                updatePortfolioLayout();
            }
        });

        applyModeBackground(initialBackground);
        updateLayoutMode();
        updateMarketTimeStatus();
        marketTimeTimer.start();
    }

    JPanel mainBarPanel() {
        return statusBarPanel;
    }

    JPanel portfolioBarPanel() {
        return portfolioStatusBarPanel;
    }

    void applyModeBackground(Color background) {
        statusBarPanel.setBackground(background);
        portfolioStatusBarPanel.setBackground(background);
    }

    void updateLayoutMode() {
        updatePortfolioLayout();
        boolean compact = statusBarPanel.getWidth() > 0 && statusBarPanel.getWidth() < COMPACT_THRESHOLD_PX;
        if (compactStatusMode == compact) {
            return;
        }
        compactStatusMode = compact;
        CardLayout layout = (CardLayout) statusLeftCards.getLayout();
        layout.show(statusLeftCards, compact ? STATUS_CARD_COMPACT : STATUS_CARD_FULL);
        statusLeftCards.revalidate();
        statusLeftCards.repaint();
    }

    void updateCompactSummaryAndDetails(StatusBarPresenter.StatusBarViewModel model, String availableFundsText) {
        compactStatusSummary.setText(compactStatusSummaryText(model, availableFundsText));
        String detailsTooltip = TooltipStyler.html(statusBarDetailsHtml(model), 520);
        compactStatusSummary.setToolTipText(detailsTooltip);
        statusDetailsButton.setToolTipText(detailsTooltip);
    }

    /** Shows the selected grid's portfolio figures; hovering a figure or its caption explains it. */
    void applyPortfolioScope(PortfolioScopePresenter.PortfolioScopeView view) {
        applyItem(marketValueStatus, view.marketValue());
        applyItem(investedValueStatus, view.investedVsUpcoming());
        applyItem(gainingPositionsStatus, view.gaining());
        applyItem(losingPositionsStatus, view.losing());
        applyItem(pendingBuyStatus, view.pendingBuy());
        applyItem(pendingSellStatus, view.pendingSell());
        updatePortfolioLayout();
    }

    private void applyItem(JLabel valueLabel, PortfolioScopePresenter.Item item) {
        valueLabel.setText(item.text());
        String tooltip = TooltipStyler.html(item.tooltipHtml(), ITEM_TOOLTIP_WIDTH);
        valueLabel.setToolTipText(tooltip);
        JLabel caption = captions.get(valueLabel);
        if (caption != null) {
            caption.setToolTipText(tooltip);
        }
    }

    void shutdown() {
        marketTimeTimer.stop();
        networkConnectionStatus.shutdown();
    }

    private JPanel buildMainStatusLeft() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        int column = 0;
        column = addStatusItem(panel, column, "Broker", statusBar);
        column = addStatusItem(panel, column, "Market", marketStatus);
        column = addStatusItem(panel, column, "Stream", streamStatus);
        column = addStatusItem(panel, column, "Polling", pollingSummary);
        column = addStatusItem(panel, column, "CPU", cpuUsageStatus);
        addStatusItem(panel, column, "Memory", memoryUsageStatus);
        return panel;
    }

    private JPanel buildPortfolioLeft() {
        portfolioRows.setOpaque(false);
        portfolioRows.setLayout(new BoxLayout(portfolioRows, BoxLayout.Y_AXIS));
        portfolioItems.add(createStatusItem("Records", statusStrategyCount));
        portfolioItems.add(createStatusItem("Funds", availableFundsStatus));
        portfolioItems.add(createStatusItem("Market Value", marketValueStatus));
        portfolioItems.add(createStatusItem("Invested vs Upcoming", investedValueStatus));
        portfolioItems.add(createStatusItem("Gaining", gainingPositionsStatus));
        portfolioItems.add(createStatusItem("Losing", losingPositionsStatus));
        portfolioItems.add(createStatusItem("Pending Buy", pendingBuyStatus));
        portfolioItems.add(createStatusItem("Pending Sell", pendingSellStatus));
        arrangePortfolioItems(false);
        return portfolioRows;
    }

    /**
     * Lays the portfolio figures out on one row, or on two when the window is too narrow for one:
     * the money figures on the first row and the position counts on the second, so none is cut off.
     */
    private void arrangePortfolioItems(boolean twoRows) {
        if (portfolioRows.getComponentCount() > 0 && portfolioTwoRows == twoRows) {
            return;
        }
        portfolioTwoRows = twoRows;
        portfolioRows.removeAll();
        int split = twoRows ? PORTFOLIO_FIRST_ROW_ITEMS : portfolioItems.size();
        portfolioRows.add(portfolioRow(portfolioItems.subList(0, split)));
        if (twoRows) {
            portfolioRows.add(portfolioRow(portfolioItems.subList(split, portfolioItems.size())));
        }
        portfolioRows.revalidate();
        portfolioRows.repaint();
    }

    private JPanel portfolioRow(List<JPanel> items) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                row.add(portfolioSeparator());
            }
            row.add(items.get(i));
        }
        return row;
    }

    private void updatePortfolioLayout() {
        int available = portfolioStatusBarPanel.getWidth();
        arrangePortfolioItems(available > 0 && available < portfolioSingleRowWidth());
    }

    /** The width the figures need on one row, whichever arrangement is showing now. */
    private int portfolioSingleRowWidth() {
        int width = (portfolioItems.size() - 1) * portfolioSeparator().getPreferredSize().width;
        for (JPanel item : portfolioItems) {
            width += item.getPreferredSize().width;
        }
        Insets insets = portfolioStatusBarPanel.getInsets();
        return width + insets.left + insets.right + networkStatusRight.getPreferredSize().width + PORTFOLIO_RIGHT_GAP;
    }

    private JLabel portfolioSeparator() {
        JLabel separator = new JLabel(STATUS_SEPARATOR.trim());
        separator.setFont(baseFont.deriveFont(Font.PLAIN, 11f));
        separator.setForeground(ITEM_LABEL_COLOR);
        separator.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 8));
        return separator;
    }

    private JPanel buildCompactStatusLeft() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridx = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(compactStatusSummary, gbc);
        gbc.gridx = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(statusDetailsButton, gbc);
        return panel;
    }

    private JPanel buildNetworkStatusRight() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.gridx = 0;
        gbc.insets = new java.awt.Insets(0, 0, 0, 8);
        panel.add(marketTimeStatus, gbc);
        gbc.gridx = 1;
        gbc.insets = new java.awt.Insets(0, 0, 0, 0);
        panel.add(networkConnectionStatus.component(), gbc);
        return panel;
    }

    private int addStatusItem(JPanel statusPanel, int column, String labelText, JLabel valueLabel) {
        if (column > 0) {
            JLabel separator = new JLabel(STATUS_SEPARATOR.trim());
            separator.setFont(baseFont.deriveFont(Font.PLAIN, 11f));
            separator.setForeground(ITEM_LABEL_COLOR);
            GridBagConstraints separatorConstraints = new GridBagConstraints();
            separatorConstraints.gridx = column;
            separatorConstraints.gridy = 0;
            separatorConstraints.anchor = GridBagConstraints.CENTER;
            separatorConstraints.insets = new java.awt.Insets(0, 10, 0, 8);
            statusPanel.add(separator, separatorConstraints);
            column++;
        }
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = column;
        constraints.gridy = 0;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new java.awt.Insets(0, 0, 0, 0);
        statusPanel.add(createStatusItem(labelText, valueLabel), constraints);
        return column + 1;
    }

    private JPanel createStatusItem(String labelText, JLabel valueLabel) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));

        JLabel label = new JLabel(labelText);
        label.setFont(baseFont.deriveFont(Font.PLAIN, 11f));
        label.setForeground(ITEM_LABEL_COLOR);
        label.setHorizontalAlignment(SwingConstants.LEFT);

        valueLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        captions.put(valueLabel, label);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridx = 0;
        gbc.insets = new java.awt.Insets(0, 0, 0, 4);
        panel.add(label, gbc);
        gbc.gridx = 1;
        gbc.insets = new java.awt.Insets(0, 0, 0, 0);
        panel.add(valueLabel, gbc);
        return panel;
    }

    private void configureMarketTimeStatus() {
        marketTimeStatus.setFont(baseFont.deriveFont(Font.PLAIN, 11f));
        marketTimeStatus.setForeground(ITEM_LABEL_COLOR);
        marketTimeStatus.setHorizontalAlignment(SwingConstants.RIGHT);
        marketTimeStatus.setToolTipText(TooltipStyler.text("Eastern stock market time."));
        marketTimeStatus.getAccessibleContext().setAccessibleName("Eastern stock market time");
    }

    private void updateMarketTimeStatus() {
        marketTimeStatus.setText(ZonedDateTime.now(MARKET_TIME_ZONE).format(MARKET_TIME_FORMATTER));
    }

    private String compactStatusSummaryText(StatusBarPresenter.StatusBarViewModel model, String availableFundsText) {
        String broker = stripHtmlTags(model.brokerText());
        String market = model.marketText();
        String funds = model.availableFundsText() == null || model.availableFundsText().isBlank()
                ? "-"
                : model.availableFundsText();
        return "Broker " + broker + COMPACT_SEPARATOR + "Market " + market + COMPACT_SEPARATOR + "Funds " + funds
                + COMPACT_SEPARATOR + "Upcoming " + model.portfolioScope().upcomingText();
    }

    private String statusBarDetailsHtml(StatusBarPresenter.StatusBarViewModel model) {
        PortfolioScopePresenter.PortfolioScopeView scope = model.portfolioScope();
        return "<b>Broker</b>: " + escapeHtml(stripHtmlTags(model.brokerText()))
                + "<br><b>Market</b>: " + escapeHtml(model.marketText())
                + "<br><b>Records</b>: " + escapeHtml(model.strategyCountText())
                + "<br><b>Polling</b>: " + escapeHtml(model.pollingText())
                + "<br><b>Trade Stream</b>: " + escapeHtml(stripHtmlTags(streamStatus.getText()))
                + "<br><b>Funds</b>: " + escapeHtml(model.availableFundsText())
                + "<br><b>Totals for</b>: " + escapeHtml(scope.scopeLabel())
                + "<br><b>Market Value</b>: " + escapeHtml(scope.marketValue().text())
                + "<br><b>Invested vs Upcoming</b>: " + escapeHtml(scope.investedVsUpcoming().text())
                + "<br><b>Gaining</b>: " + escapeHtml(scope.gaining().text())
                + "<br><b>Losing</b>: " + escapeHtml(scope.losing().text())
                + "<br><b>Pending Buy</b>: " + escapeHtml(scope.pendingBuy().text())
                + "<br><b>Pending Sell</b>: " + escapeHtml(scope.pendingSell().text())
                + "<br><b>CPU</b>: " + escapeHtml(model.cpuText())
                + "<br><b>Memory</b>: " + escapeHtml(model.memoryText());
    }

    private String stripHtmlTags(String text) {
        if (text == null || text.isBlank()) {
            return "-";
        }
        return text.replaceAll("<[^>]*>", "").replace("&nbsp;", " ").trim();
    }

    private void showStatusDetailsPopup(Component anchor) {
        String details = compactStatusSummary.getToolTipText();
        if (details == null || details.isBlank()) {
            details = TooltipStyler.html(
                    "<b>Broker</b>: " + escapeHtml(stripHtmlTags(statusBar.getText()))
                            + "<br><b>Market</b>: " + escapeHtml(stripHtmlTags(marketStatus.getText())),
                    520
            );
        }
        JPopupMenu popup = new JPopupMenu();
        popup.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(70, 76, 90), 1, true),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)
        ));
        JLabel content = new JLabel(details);
        content.setFont(baseFont.deriveFont(Font.BOLD, 11f));
        popup.add(content);
        popup.show(anchor, Math.max(0, anchor.getWidth() - 360), anchor.getHeight());
    }

    private String escapeHtml(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private void forceLeftAlignment() {
        statusBar.setHorizontalAlignment(SwingConstants.LEFT);
        marketStatus.setHorizontalAlignment(SwingConstants.LEFT);
        streamStatus.setHorizontalAlignment(SwingConstants.LEFT);
        pollingSummary.setHorizontalAlignment(SwingConstants.LEFT);
        cpuUsageStatus.setHorizontalAlignment(SwingConstants.LEFT);
        memoryUsageStatus.setHorizontalAlignment(SwingConstants.LEFT);
        statusStrategyCount.setHorizontalAlignment(SwingConstants.LEFT);
        availableFundsStatus.setHorizontalAlignment(SwingConstants.LEFT);
        marketValueStatus.setHorizontalAlignment(SwingConstants.LEFT);
        investedValueStatus.setHorizontalAlignment(SwingConstants.LEFT);
        pendingBuyStatus.setHorizontalAlignment(SwingConstants.LEFT);
        gainingPositionsStatus.setHorizontalAlignment(SwingConstants.LEFT);
        losingPositionsStatus.setHorizontalAlignment(SwingConstants.LEFT);
        pendingSellStatus.setHorizontalAlignment(SwingConstants.LEFT);
        compactStatusSummary.setHorizontalAlignment(SwingConstants.LEFT);
        statusBar.setForeground(accentColor);
    }
}
