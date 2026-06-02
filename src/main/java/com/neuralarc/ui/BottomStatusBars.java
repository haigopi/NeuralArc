package com.neuralarc.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
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
    private static final String GROUPED_STATUS_SEPARATOR = ".";
    private static final String COMPACT_SEPARATOR = " . ";

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
    private final JLabel compactStatusSummary;
    private final JButton statusDetailsButton;

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
            JLabel compactStatusSummary,
            JButton statusDetailsButton,
            JPanel statusRight,
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
        this.compactStatusSummary = compactStatusSummary;
        this.statusDetailsButton = statusDetailsButton;

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

    private JPanel buildMainStatusLeft() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        int column = 0;
        column = addStatusSegment(panel, gbc, column, createStatusSegment(statusBar), true);
        column = addStatusSegment(panel, gbc, column, createStatusSegment(marketStatus), true);
        column = addStatusSegment(panel, gbc, column, createStatusSegment(streamStatus), true);
        column = addStatusSegment(panel, gbc, column, createCpuMemorySegment(), true);
        addStatusSegment(panel, gbc, column, createStatusSegment(pollingSummary), false);
        return panel;
    }

    private JPanel buildPortfolioLeft() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        int column = 0;
        column = addStatusSegment(panel, gbc, column, createStatusSegment(statusStrategyCount), true);
        addStatusSegment(panel, gbc, column, createPortfolioValueSegment(), false);
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

    private JPanel createStatusSegment(JComponent component) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        panel.add(component, BorderLayout.CENTER);
        return panel;
    }

    private int addStatusSegment(
            JPanel statusPanel,
            GridBagConstraints constraints,
            int column,
            JComponent segment,
            boolean separatorAfter
    ) {
        constraints.gridx = column;
        constraints.insets = new java.awt.Insets(0, 0, 0, 0);
        statusPanel.add(segment, constraints);
        if (!separatorAfter) {
            return column + 1;
        }
        constraints.gridx = column + 1;
        statusPanel.add(createStatusSeparator(), constraints);
        return column + 2;
    }

    private JLabel createStatusSeparator() {
        JLabel separator = new JLabel(GROUPED_STATUS_SEPARATOR);
        separator.setFont(baseFont.deriveFont(Font.BOLD, 11f));
        separator.setForeground(new Color(92, 92, 108));
        separator.setHorizontalAlignment(SwingConstants.CENTER);
        separator.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        return separator;
    }

    private JPanel createPortfolioValueSegment() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridx = 0;
        gbc.insets = new java.awt.Insets(0, 0, 0, 6);
        panel.add(availableFundsStatus, gbc);
        gbc.gridx = 1;
        panel.add(createInlineStatusSeparator(), gbc);
        gbc.gridx = 2;
        panel.add(marketValueStatus, gbc);
        gbc.gridx = 3;
        panel.add(createInlineStatusSeparator(), gbc);
        gbc.gridx = 4;
        gbc.insets = new java.awt.Insets(0, 0, 0, 0);
        panel.add(investedValueStatus, gbc);
        return panel;
    }

    private JPanel createCpuMemorySegment() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridx = 0;
        gbc.insets = new java.awt.Insets(0, 0, 0, 5);
        panel.add(cpuUsageStatus, gbc);
        gbc.gridx = 1;
        panel.add(createInlineStatusSeparator(), gbc);
        gbc.gridx = 2;
        gbc.insets = new java.awt.Insets(0, 0, 0, 0);
        panel.add(memoryUsageStatus, gbc);
        return panel;
    }

    private JLabel createInlineStatusSeparator() {
        JLabel separator = new JLabel(GROUPED_STATUS_SEPARATOR);
        separator.setFont(baseFont.deriveFont(Font.BOLD, 11f));
        separator.setForeground(new Color(95, 95, 110));
        return separator;
    }

    private String compactStatusSummaryText(StatusBarPresenter.StatusBarViewModel model, String availableFundsText) {
        String broker = stripHtmlTags(model.brokerText());
        String market = model.marketText();
        String funds = availableFundsText == null || availableFundsText.isBlank()
                ? "Funds: -"
                : availableFundsText.replace("Funds Available:", "Funds");
        return broker + COMPACT_SEPARATOR + market + COMPACT_SEPARATOR + funds;
    }

    private String statusBarDetailsHtml(StatusBarPresenter.StatusBarViewModel model) {
        return "<b>Broker</b>: " + escapeHtml(stripHtmlTags(model.brokerText()))
                + "<br><b>Market</b>: " + escapeHtml(model.marketText())
                + "<br><b>Records</b>: " + escapeHtml(model.strategyCountText())
                + "<br><b>Polling</b>: " + escapeHtml(model.pollingText())
                + "<br><b>Trade Stream</b>: " + escapeHtml(stripHtmlTags(streamStatus.getText()))
                + "<br><b>Funds</b>: " + escapeHtml(stripHtmlTags(availableFundsStatus.getText()))
                + "<br><b>Market Value</b>: " + escapeHtml(stripHtmlTags(marketValueStatus.getText()))
                + "<br><b>Invested Value</b>: " + escapeHtml(stripHtmlTags(investedValueStatus.getText()))
                + "<br><b>CPU</b>: " + escapeHtml(stripHtmlTags(cpuUsageStatus.getText()))
                + "<br><b>Memory</b>: " + escapeHtml(stripHtmlTags(memoryUsageStatus.getText()));
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
        compactStatusSummary.setHorizontalAlignment(SwingConstants.LEFT);
        statusBar.setForeground(accentColor);
    }
}

