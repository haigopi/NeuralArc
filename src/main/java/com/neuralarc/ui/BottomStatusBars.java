package com.neuralarc.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.BooleanSupplier;

final class BottomStatusBars {
    private static final int COMPACT_THRESHOLD_PX = 1280;
    private static final String STATUS_CARD_FULL = "full";
    private static final String STATUS_CARD_COMPACT = "compact";
    private static final String COMPACT_SEPARATOR = "   ";
    private static final Color ITEM_LABEL_COLOR = new Color(126, 132, 146);

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
    private final JLabel baseBuyPendingStatus;
    private final JLabel compactStatusSummary;
    private final JButton statusDetailsButton;
    private final NetworkConnectionStatusIndicator networkConnectionStatus;

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
            JLabel baseBuyPendingStatus,
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
        this.baseBuyPendingStatus = baseBuyPendingStatus;
        this.compactStatusSummary = compactStatusSummary;
        this.statusDetailsButton = statusDetailsButton;
        this.networkConnectionStatus = new NetworkConnectionStatusIndicator(statusBarPresenter);

        forceLeftAlignment();

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
        this.portfolioStatusBarPanel.add(buildPortfolioLeft(), BorderLayout.WEST);
        this.portfolioStatusBarPanel.add(buildNetworkStatusRight(), BorderLayout.EAST);

        applyModeBackground(initialBackground);
        updateLayoutMode();
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

    void shutdown() {
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
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        int column = 0;
        column = addStatusItem(panel, column, "Records", statusStrategyCount);
        column = addStatusItem(panel, column, "Funds", availableFundsStatus);
        column = addStatusItem(panel, column, "Market Value", marketValueStatus);
        column = addStatusItem(panel, column, "Invested", investedValueStatus);
        addStatusItem(panel, column, "Pending Buys", baseBuyPendingStatus);
        return panel;
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
        gbc.insets = new java.awt.Insets(0, 0, 0, 0);
        panel.add(networkConnectionStatus.component(), gbc);
        return panel;
    }

    private int addStatusItem(JPanel statusPanel, int column, String labelText, JLabel valueLabel) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = column;
        constraints.gridy = 0;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new java.awt.Insets(0, column == 0 ? 0 : 10, 0, 0);
        statusPanel.add(createStatusItem(labelText, valueLabel), constraints);
        return column + 1;
    }

    private JPanel createStatusItem(String labelText, JLabel valueLabel) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));

        JLabel label = new JLabel(labelText);
        label.setFont(baseFont.deriveFont(Font.PLAIN, 9f));
        label.setForeground(ITEM_LABEL_COLOR);
        label.setHorizontalAlignment(SwingConstants.LEFT);

        valueLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
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

    private String compactStatusSummaryText(StatusBarPresenter.StatusBarViewModel model, String availableFundsText) {
        String broker = stripHtmlTags(model.brokerText());
        String market = model.marketText();
        String funds = model.availableFundsText() == null || model.availableFundsText().isBlank()
                ? "-"
                : model.availableFundsText();
        return "Broker " + broker + COMPACT_SEPARATOR + "Market " + market + COMPACT_SEPARATOR + "Funds " + funds
                + COMPACT_SEPARATOR + "Pending " + model.baseBuyPendingText();
    }

    private String statusBarDetailsHtml(StatusBarPresenter.StatusBarViewModel model) {
        return "<b>Broker</b>: " + escapeHtml(stripHtmlTags(model.brokerText()))
                + "<br><b>Market</b>: " + escapeHtml(model.marketText())
                + "<br><b>Records</b>: " + escapeHtml(model.strategyCountText())
                + "<br><b>Polling</b>: " + escapeHtml(model.pollingText())
                + "<br><b>Trade Stream</b>: " + escapeHtml(stripHtmlTags(streamStatus.getText()))
                + "<br><b>Funds</b>: " + escapeHtml(model.availableFundsText())
                + "<br><b>Market Value</b>: " + escapeHtml(model.marketValueText())
                + "<br><b>Invested Value</b>: " + escapeHtml(model.investedValueText())
                + "<br><b>Pending Buys</b>: " + escapeHtml(model.baseBuyPendingText())
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
        baseBuyPendingStatus.setHorizontalAlignment(SwingConstants.LEFT);
        compactStatusSummary.setHorizontalAlignment(SwingConstants.LEFT);
        statusBar.setForeground(accentColor);
    }
}
