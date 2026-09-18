package com.neuralarc.ui;

import com.neuralarc.model.RecommendationType;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.util.FontLoader;
import com.neuralarc.util.Monetary;
import com.neuralarc.util.ThemeColors;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

final class PortfolioCaptureDialog extends JDialog {
    private static final Color MUTED = new Color(120, 124, 135);
    private static final Font UI_FONT = FontLoader.ui(Font.PLAIN, 11f);
    private static final Font UI_BOLD_FONT = FontLoader.ui(Font.BOLD, 11f);
    private static final Font DESCRIPTION_FONT = FontLoader.ui(Font.PLAIN, 10f);
    /** Help text wraps at this width so no single sentence can push the dialog wider than its window. */
    private static final int TEXT_WIDTH = 500;
    private static final String DISCLAIMER = "<html><body style='width:"
            + CompactFormLayout.cssWidth(500) + "px'>"
            + "<b>Disclaimer:</b><br>"
            + "All portfolio liquidation actions execute using live market orders at the marketplace.<br>"
            + "Final execution prices depend on real-time market conditions, bid/ask spread, liquidity, slippage, and broker execution timing.<br>"
            + "Exact liquidated profit/loss numbers shown in the application may slightly vary from broker-reported final values.<br>"
            + "During volatile market conditions, executed values may differ from displayed estimated values.<br>"
            + "By proceeding, the user acknowledges and accepts potential execution variances."
            + "</body></html>";

    private final Function<PortfolioCaptureConfig, PortfolioCaptureSnapshot> snapshotSupplier;
    private final Consumer<PortfolioCaptureConfig> captureNowHandler;
    private final Consumer<PortfolioCaptureConfig> activateHandler;
    private final Runnable deactivateHandler;
    private final boolean monitoringActive;
    private final Timer refreshTimer;

    private final JRadioButton captureNow = new JRadioButton("Liquidate Now", true);
    private final JRadioButton captureTarget = new JRadioButton("Liquidate At Target");
    private final JRadioButton capturePullback = new JRadioButton("Wait for Minimum, Then Liquidate on Pullback");
    private final JRadioButton percentTarget = new JRadioButton("Profit Percent", true);
    private final JRadioButton amountTarget = new JRadioButton("Profit Amount");
    private final JTextField percentField = new JTextField("5", 8);
    private final JTextField amountField = new JTextField("500", 8);
    private final JRadioButton percentPullback = new JRadioButton("Pullback Percent", true);
    private final JRadioButton amountPullback = new JRadioButton("Pullback Amount");
    private final JTextField pullbackPercentField = new JTextField("10", 8);
    private final JTextField pullbackAmountField = new JTextField("100", 8);
    private final JCheckBox includeLosses = new JCheckBox("Include losses in net P/L", true);
    private final JTextField intervalField = new JTextField("45", 5);
    private final JCheckBox activeOnly = new JCheckBox("Active strategies only", true);
    private final JRadioButton executeOnce = new JRadioButton("Execute once", true);
    private final JRadioButton reenterOnce = new JRadioButton("Liquidate, then re-enter once");
    private final JRadioButton continuousLoop = new JRadioButton("Continuous Automated Loop");
    private final JRadioButton paperMode = new JRadioButton("Paper mode", true);
    private final JRadioButton liveMode = new JRadioButton("Live mode");
    private final JSpinner reentryQuantity = new JSpinner(new SpinnerNumberModel(1, 1, 100000, 1));
    private final JComboBox<RecommendationType> reentryTerm = new JComboBox<>(RecommendationType.values());
    private final JComboBox<PortfolioCaptureSmartPicksStrategy> reentrySmartPicksStrategy =
            new JComboBox<>(PortfolioCaptureSmartPicksStrategy.values());
    private final JCheckBox autoCleanPending = new JCheckBox("Auto-clean pending base buys before each cycle");
    private final JCheckBox acknowledgement = new JCheckBox("I understand execution prices may vary.");
    private final JButton saveButton = new JButton("Save");
    private final JButton deactivateButton = new JButton("Deactivate Monitoring");
    private final JLabel investmentValue = new JLabel("-");
    private final JLabel marketValue = new JLabel("-");
    private final JLabel pnlValue = new JLabel("-");
    private final JLabel pnlPercentValue = new JLabel("-");
    private final JLabel progressValue = new JLabel("-");
    private final JLabel realizedPnlValue = new JLabel("-");

    private boolean executed;

    PortfolioCaptureDialog(
            Frame owner,
            Function<PortfolioCaptureConfig, PortfolioCaptureSnapshot> snapshotSupplier,
            Consumer<PortfolioCaptureConfig> captureNowHandler,
            Consumer<PortfolioCaptureConfig> activateHandler,
            Runnable deactivateHandler,
            boolean monitoringActive
    ) {
        super(owner, "Liquidate Portfolio", true);
        DialogCloseActions.bindEscapeToClose(this);
        this.snapshotSupplier = snapshotSupplier;
        this.captureNowHandler = captureNowHandler;
        this.activateHandler = activateHandler;
        this.deactivateHandler = deactivateHandler;
        this.monitoringActive = monitoringActive;
        this.refreshTimer = new Timer(1000, ignored -> refreshMetrics());
        buildUi();
        applyCompactFonts(getContentPane());
        refreshMetrics();
        updateEnabledState();
        // The content is built to a fixed reading width (wrapped help text, natural-width fields), so the
        // packed width fits it exactly; growTall then only adds height.
        DialogSizing.packAndFitTall(this, 580, 480);
        setLocationRelativeTo(owner);
    }

    boolean showDialog() {
        refreshTimer.start();
        setVisible(true);
        refreshTimer.stop();
        return executed;
    }

    private void buildUi() {
        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(10, 12, 10, 12));
        content.add(modeSection(), BorderLayout.NORTH);

        JPanel center = CompactFormLayout.widthTrackingPanel(new GridBagLayout());
        center.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        gbc.insets = new Insets(0, 0, 10, 0);
        center.add(metricsSection(), gbc);
        gbc.gridy++;
        center.add(targetSection(), gbc);
        gbc.gridy++;
        center.add(postAutomationSection(), gbc);
        gbc.gridy++;
        center.add(disclaimerSection(), gbc);
        JScrollPane centerScroll = new JScrollPane(center);
        centerScroll.setBorder(BorderFactory.createEmptyBorder());
        centerScroll.setOpaque(false);
        centerScroll.getViewport().setOpaque(false);
        // The view tracks the viewport width, so this pane only ever scrolls vertically.
        centerScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        centerScroll.getVerticalScrollBar().setUnitIncrement(16);
        centerScroll.setPreferredSize(DialogSizing.preferredViewportSize(center, 540, 360, 720, 560));
        content.add(centerScroll, BorderLayout.CENTER);
        content.add(buttonBar(), BorderLayout.SOUTH);
        setContentPane(content);

        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(captureNow);
        modeGroup.add(captureTarget);
        modeGroup.add(capturePullback);
        ButtonGroup targetGroup = new ButtonGroup();
        targetGroup.add(percentTarget);
        targetGroup.add(amountTarget);
        ButtonGroup pullbackGroup = new ButtonGroup();
        pullbackGroup.add(percentPullback);
        pullbackGroup.add(amountPullback);
        ButtonGroup flowGroup = new ButtonGroup();
        flowGroup.add(executeOnce);
        flowGroup.add(reenterOnce);
        flowGroup.add(continuousLoop);
        ButtonGroup modeGroup2 = new ButtonGroup();
        modeGroup2.add(paperMode);
        modeGroup2.add(liveMode);
        captureNow.addActionListener(e -> updateEnabledState());
        captureTarget.addActionListener(e -> updateEnabledState());
        capturePullback.addActionListener(e -> updateEnabledState());
        percentTarget.addActionListener(e -> updateEnabledState());
        amountTarget.addActionListener(e -> updateEnabledState());
        percentPullback.addActionListener(e -> updateEnabledState());
        amountPullback.addActionListener(e -> updateEnabledState());
        executeOnce.addActionListener(e -> updateEnabledState());
        reenterOnce.addActionListener(e -> updateEnabledState());
        continuousLoop.addActionListener(e -> updateEnabledState());
        acknowledgement.addActionListener(e -> updateEnabledState());
        setBackground(getOwner() == null ? getBackground() : getOwner().getBackground());
    }

    private JPanel modeSection() {
        JPanel panel = section("Liquidation Mode");
        addRadioWithDescription(panel, captureNow,
                "Liquidate the portfolio as it is at the current market price.", 0);
        addRadioWithDescription(panel, captureTarget,
                "Automatically monitor and liquidate the portfolio once the configured target is reached.", 2);
        addRadioWithDescription(panel, capturePullback,
                "Wait for the minimum profit target, track the highest profit, then liquidate after the configured pullback.", 4);
        return panel;
    }

    private JPanel metricsSection() {
        JPanel panel = section("Portfolio Metrics");
        // Two label/value pairs per row, each value right-aligned beside its own label rather than
        // stranded at the far edge of the dialog.
        JPanel grid = CompactFormLayout.form();
        addMetric(grid, "Total Investment", investmentValue, 0, 0);
        addMetric(grid, "Market Value", marketValue, 0, 1);
        addMetric(grid, "Unrealized P/L", pnlValue, 1, 0);
        addMetric(grid, "Unrealized P/L %", pnlPercentValue, 1, 1);
        addMetric(grid, "Realized P/L (Banked)", realizedPnlValue, 2, 0);
        addMetric(grid, "Target Progress", progressValue, 2, 1);
        GridBagConstraints filler = baseGbc(0);
        filler.gridx = 4;
        grid.add(javax.swing.Box.createHorizontalStrut(0), filler);
        panel.add(grid, baseGbc(0));
        addDescription(panel,
                "Targets are measured on unrealized profit only — the P&L this liquidation would "
                        + "actually realize. Realized profit from trades already closed is shown for "
                        + "reference and cannot trigger a liquidation.", 1);
        return panel;
    }

    private JPanel targetSection() {
        JPanel panel = section("Target Monitoring");
        JPanel targets = CompactFormLayout.form();
        CompactFormLayout.addRow(targets, 0, percentTarget, percentField, unit("%"));
        CompactFormLayout.addRow(targets, 1, amountTarget, amountField, unit("USD"));
        panel.add(targets, baseGbc(0));
        panel.add(includeLosses, baseGbc(1));
        panel.add(pullbackPanel(), baseGbc(2));
        panel.add(description("Target mode liquidates at the minimum. Pullback mode arms at the minimum and liquidates only after profit falls from its subsequent peak."), baseGbc(3));
        JPanel interval = CompactFormLayout.form();
        CompactFormLayout.addRow(interval, 0, fieldLabel("Monitoring interval"), intervalField, unit("seconds"));
        panel.add(interval, baseGbc(4));
        panel.add(activeOnly, baseGbc(5));
        return panel;
    }

    private JPanel pullbackPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(ThemeColors.color("NeuralArc.Section.border", new Color(190, 194, 202))),
                "Pullback After Minimum");
        border.setTitleFont(UI_BOLD_FONT);
        border.setTitleColor(ThemeColors.color("NeuralArc.Section.titleForeground", new Color(70, 75, 85)));
        panel.setBorder(BorderFactory.createCompoundBorder(border, new EmptyBorder(2, 6, 4, 6)));
        JPanel rows = CompactFormLayout.form();
        CompactFormLayout.addRow(rows, 0, percentPullback, pullbackPercentField, unit("% from peak profit"));
        CompactFormLayout.addRow(rows, 1, amountPullback, pullbackAmountField, unit("USD from peak profit"));
        panel.add(rows, baseGbc(0));
        return panel;
    }

    private JPanel postAutomationSection() {
        JPanel panel = section("Post-Liquidation Automation");
        addRadioWithDescription(panel, executeOnce,
                "Liquidation executes once and monitoring stops afterward.", 0);
        addRadioWithDescription(panel, reenterOnce,
                "Liquidation executes, Smart Picks runs once, then monitoring stops.", 2);
        addRadioWithDescription(panel, continuousLoop,
                "Liquidation executes, Smart Picks runs automatically, monitoring restarts, and the cycle repeats.", 4);
        panel.add(reentryOptionsPanel(), baseGbc(6));
        panel.add(autoCleanPending, baseGbc(7));
        panel.add(description("Pending base buy limits will be automatically cancelled before every automated liquidation cycle."), baseGbc(8));
        return panel;
    }

    private JPanel reentryOptionsPanel() {
        JPanel tradingMode = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tradingMode.setOpaque(false);
        tradingMode.add(paperMode);
        tradingMode.add(javax.swing.Box.createHorizontalStrut(16));
        tradingMode.add(liveMode);
        JPanel form = CompactFormLayout.form();
        CompactFormLayout.addRow(form, 0, fieldLabel("Re-entry mode"), tradingMode, null);
        CompactFormLayout.addRow(form, 1, fieldLabel("Smart Picks strategy"), reentrySmartPicksStrategy, null);
        // The unit rides with the spinner: the field column is as wide as the strategy dropdown.
        JPanel quantity = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        quantity.setOpaque(false);
        quantity.add(reentryQuantity);
        quantity.add(javax.swing.Box.createHorizontalStrut(6));
        quantity.add(unit("shares per pick"));
        CompactFormLayout.addRow(form, 2, fieldLabel("Quantity"), quantity, null);
        CompactFormLayout.addRow(form, 3, fieldLabel("Term"), reentryTerm, null);
        return form;
    }

    private JPanel disclaimerSection() {
        JPanel panel = section("Execution Disclaimer");
        panel.add(description(DISCLAIMER), baseGbc(0));
        panel.add(acknowledgement, baseGbc(1));
        return panel;
    }

    private JPanel buttonBar() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        panel.setOpaque(false);
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dispose());
        deactivateButton.setVisible(monitoringActive);
        deactivateButton.addActionListener(e -> {
            deactivateHandler.run();
            executed = true;
            dispose();
        });
        saveButton.addActionListener(e -> onSave());
        panel.add(deactivateButton);
        panel.add(cancel);
        panel.add(saveButton);
        return panel;
    }

    private void onSave() {
        Optional<PortfolioCaptureConfig> maybeConfig = readConfig();
        if (maybeConfig.isEmpty()) {
            return;
        }
        PortfolioCaptureConfig config = maybeConfig.get();
        int eligibleCount = snapshotSupplier.apply(config).eligibleCount();
        if (config.mode() == PortfolioCaptureMode.CAPTURE_NOW && eligibleCount <= 0) {
            JOptionPane.showMessageDialog(this, "No eligible portfolio positions are available to liquidate.",
                    "Liquidate Portfolio", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (config.mode() == PortfolioCaptureMode.CAPTURE_NOW) {
            int choice = JOptionPane.showConfirmDialog(
                    this,
                    "<html><body style='width:520px'>"
                            + "This action will execute market orders for the entire eligible portfolio immediately.<br>"
                            + "Final broker execution values may vary slightly from estimated values shown in the application.<br><br>"
                            + DISCLAIMER
                            + "</body></html>",
                    "Confirm Portfolio Liquidation",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
            captureNowHandler.accept(config);
        } else {
            activateHandler.accept(config);
        }
        executed = true;
        dispose();
    }

    private Optional<PortfolioCaptureConfig> readConfig() {
        if (!acknowledgement.isSelected()) {
            JOptionPane.showMessageDialog(this, "Acknowledge the execution disclaimer before continuing.",
                    "Liquidate Portfolio", JOptionPane.WARNING_MESSAGE);
            return Optional.empty();
        }
        if (captureNow.isSelected()) {
            return Optional.of(withAutomation(PortfolioCaptureConfig.captureNow()));
        }
        BigDecimal target;
        try {
            target = new BigDecimal(percentTarget.isSelected() ? percentField.getText().trim() : amountField.getText().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Enter a valid positive target value.",
                    "Liquidate Portfolio", JOptionPane.WARNING_MESSAGE);
            return Optional.empty();
        }
        if (target.compareTo(BigDecimal.ZERO) <= 0) {
            JOptionPane.showMessageDialog(this, "Target value must be greater than zero.",
                    "Liquidate Portfolio", JOptionPane.WARNING_MESSAGE);
            return Optional.empty();
        }
        BigDecimal pullback = BigDecimal.ZERO;
        if (capturePullback.isSelected()) {
            try {
                pullback = new BigDecimal(percentPullback.isSelected()
                        ? pullbackPercentField.getText().trim()
                        : pullbackAmountField.getText().trim());
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Enter a valid positive pullback value.",
                        "Liquidate Portfolio", JOptionPane.WARNING_MESSAGE);
                return Optional.empty();
            }
            if (pullback.compareTo(BigDecimal.ZERO) <= 0
                    || (percentPullback.isSelected() && pullback.compareTo(BigDecimal.valueOf(100)) >= 0)) {
                JOptionPane.showMessageDialog(this, "Pullback must be greater than zero and percentages must be below 100.",
                        "Liquidate Portfolio", JOptionPane.WARNING_MESSAGE);
                return Optional.empty();
            }
        }
        int interval;
        try {
            interval = Integer.parseInt(intervalField.getText().trim());
        } catch (NumberFormatException ex) {
            interval = 45;
        }
        if (interval <= 0) {
            JOptionPane.showMessageDialog(this, "Monitoring interval must be greater than zero.",
                    "Liquidate Portfolio", JOptionPane.WARNING_MESSAGE);
            return Optional.empty();
        }
        return Optional.of(new PortfolioCaptureConfig(
                capturePullback.isSelected()
                        ? PortfolioCaptureMode.PULLBACK_MONITORING
                        : PortfolioCaptureMode.TARGET_MONITORING,
                percentTarget.isSelected() ? PortfolioCaptureTargetType.PROFIT_PERCENT : PortfolioCaptureTargetType.PROFIT_AMOUNT,
                target,
                includeLosses.isSelected(),
                interval,
                executeOnce.isSelected(),
                activeOnly.isSelected(),
                selectedExecutionFlow(),
                paperMode.isSelected() ? StrategyMode.PAPER : StrategyMode.LIVE,
                (Integer) reentryQuantity.getValue(),
                (RecommendationType) reentryTerm.getSelectedItem(),
                selectedSmartPicksStrategy(),
                autoCleanPending.isSelected(),
                percentPullback.isSelected()
                        ? PortfolioCapturePullbackType.PERCENT_FROM_PEAK
                        : PortfolioCapturePullbackType.AMOUNT_FROM_PEAK,
                pullback
        ));
    }

    private void updateEnabledState() {
        boolean targetMode = captureTarget.isSelected() || capturePullback.isSelected();
        boolean pullbackMode = capturePullback.isSelected();
        boolean reentryEnabled = targetMode && (reenterOnce.isSelected() || continuousLoop.isSelected());
        percentTarget.setEnabled(targetMode);
        amountTarget.setEnabled(targetMode);
        percentField.setEnabled(targetMode && percentTarget.isSelected());
        amountField.setEnabled(targetMode && amountTarget.isSelected());
        percentPullback.setEnabled(pullbackMode);
        amountPullback.setEnabled(pullbackMode);
        pullbackPercentField.setEnabled(pullbackMode && percentPullback.isSelected());
        pullbackAmountField.setEnabled(pullbackMode && amountPullback.isSelected());
        includeLosses.setEnabled(targetMode);
        intervalField.setEnabled(targetMode);
        activeOnly.setEnabled(targetMode);
        executeOnce.setEnabled(targetMode);
        reenterOnce.setEnabled(targetMode);
        continuousLoop.setEnabled(targetMode);
        paperMode.setEnabled(reentryEnabled);
        liveMode.setEnabled(reentryEnabled);
        reentryQuantity.setEnabled(reentryEnabled);
        reentryTerm.setEnabled(reentryEnabled);
        reentrySmartPicksStrategy.setEnabled(reentryEnabled);
        autoCleanPending.setEnabled(targetMode);
        saveButton.setText(targetMode ? "Activate Monitoring" : "Liquidate Now");
        saveButton.setEnabled(acknowledgement.isSelected());
    }

    private void refreshMetrics() {
        PortfolioCaptureSnapshot snapshot = snapshotSupplier.apply(previewConfig());
        investmentValue.setText(money(snapshot.totalInvestment()));
        marketValue.setText(money(snapshot.marketValue()));
        realizedPnlValue.setText(money(snapshot.realizedPnl()));
        pnlValue.setText(money(snapshot.unrealizedPnl()));
        pnlValue.setForeground(snapshot.unrealizedPnl().compareTo(BigDecimal.ZERO) < 0
                ? ThemeColors.color("NeuralArc.pnlNegative", new Color(183, 28, 28))
                : ThemeColors.color("NeuralArc.pnlPositive", new Color(27, 94, 32)));
        pnlPercentValue.setText(Monetary.round(snapshot.profitLossPercent()) + "%");
        progressValue.setText(Monetary.round(snapshot.targetProgressPercent()) + "%");
    }

    private PortfolioCaptureConfig previewConfig() {
        if (captureNow.isSelected()) {
            return PortfolioCaptureConfig.captureNow();
        }
        BigDecimal target;
        try {
            target = new BigDecimal(percentTarget.isSelected() ? percentField.getText().trim() : amountField.getText().trim());
        } catch (NumberFormatException ex) {
            target = BigDecimal.ZERO;
        }
        return new PortfolioCaptureConfig(
                capturePullback.isSelected()
                        ? PortfolioCaptureMode.PULLBACK_MONITORING
                        : PortfolioCaptureMode.TARGET_MONITORING,
                percentTarget.isSelected() ? PortfolioCaptureTargetType.PROFIT_PERCENT : PortfolioCaptureTargetType.PROFIT_AMOUNT,
                target,
                includeLosses.isSelected(),
                parseInterval(),
                executeOnce.isSelected(),
                activeOnly.isSelected(),
                selectedExecutionFlow(),
                paperMode.isSelected() ? StrategyMode.PAPER : StrategyMode.LIVE,
                (Integer) reentryQuantity.getValue(),
                (RecommendationType) reentryTerm.getSelectedItem(),
                selectedSmartPicksStrategy(),
                autoCleanPending.isSelected(),
                percentPullback.isSelected()
                        ? PortfolioCapturePullbackType.PERCENT_FROM_PEAK
                        : PortfolioCapturePullbackType.AMOUNT_FROM_PEAK,
                previewPullbackValue()
        );
    }

    private PortfolioCaptureConfig withAutomation(PortfolioCaptureConfig base) {
        return new PortfolioCaptureConfig(
                base.mode(),
                base.targetType(),
                base.targetValue(),
                base.includeLosses(),
                base.monitoringIntervalSeconds(),
                true,
                base.includeOnlyActiveStrategies(),
                selectedExecutionFlow(),
                paperMode.isSelected() ? StrategyMode.PAPER : StrategyMode.LIVE,
                (Integer) reentryQuantity.getValue(),
                (RecommendationType) reentryTerm.getSelectedItem(),
                selectedSmartPicksStrategy(),
                autoCleanPending.isSelected(),
                base.pullbackType(),
                base.pullbackValue()
        );
    }

    private BigDecimal previewPullbackValue() {
        try {
            return new BigDecimal(percentPullback.isSelected()
                    ? pullbackPercentField.getText().trim()
                    : pullbackAmountField.getText().trim());
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private PortfolioCaptureSmartPicksStrategy selectedSmartPicksStrategy() {
        Object selected = reentrySmartPicksStrategy.getSelectedItem();
        return selected instanceof PortfolioCaptureSmartPicksStrategy strategy
                ? strategy
                : PortfolioCaptureSmartPicksStrategy.VOLATILE;
    }

    private PortfolioCaptureExecutionFlow selectedExecutionFlow() {
        if (continuousLoop.isSelected()) {
            return PortfolioCaptureExecutionFlow.CONTINUOUS_AUTOMATED_LOOP;
        }
        if (reenterOnce.isSelected()) {
            return PortfolioCaptureExecutionFlow.CAPTURE_THEN_REENTER_ONCE;
        }
        return PortfolioCaptureExecutionFlow.EXECUTE_ONCE_AND_STOP;
    }

    private int parseInterval() {
        try {
            return Math.max(1, Integer.parseInt(intervalField.getText().trim()));
        } catch (NumberFormatException ex) {
            return 45;
        }
    }

    private JPanel section(String title) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        TitledBorder titleBorder = BorderFactory.createTitledBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, ThemeColors.color("NeuralArc.Section.border", new Color(190, 194, 202))),
                title
        );
        titleBorder.setTitleFont(UI_BOLD_FONT);
        titleBorder.setTitleColor(ThemeColors.color("NeuralArc.Section.titleForeground", new Color(70, 75, 85)));
        panel.setBorder(BorderFactory.createCompoundBorder(
                titleBorder,
                new EmptyBorder(5, 6, 7, 6)
        ));
        return panel;
    }

    private void addRadioWithDescription(JPanel panel, JRadioButton radio, String text, int row) {
        panel.add(radio, baseGbc(row));
        GridBagConstraints gbc = baseGbc(row + 1);
        // Indent the help text under the radio's label rather than under its circle.
        gbc.insets = new Insets(0, 24, 4, 8);
        panel.add(description(text), gbc);
    }

    private void addDescription(JPanel panel, String text, int row) {
        panel.add(description(text), baseGbc(row));
    }

    private void addMetric(JPanel grid, String label, JLabel value, int row, int pair) {
        int column = pair * 2;
        GridBagConstraints name = new GridBagConstraints();
        name.gridx = column;
        name.gridy = row;
        name.anchor = GridBagConstraints.WEST;
        name.insets = new Insets(2, pair == 0 ? 0 : 28, 2, 10);
        grid.add(fieldLabel(label), name);
        GridBagConstraints amount = new GridBagConstraints();
        amount.gridx = column + 1;
        amount.gridy = row;
        amount.anchor = GridBagConstraints.EAST;
        amount.fill = GridBagConstraints.HORIZONTAL;
        // Room for a six-figure dollar amount, so the columns do not shift as values refresh.
        amount.ipadx = 36;
        amount.insets = new Insets(2, 0, 2, 0);
        value.setHorizontalAlignment(SwingConstants.RIGHT);
        grid.add(value, amount);
    }

    private GridBagConstraints baseGbc(int row) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        gbc.insets = new Insets(3, 0, 3, 8);
        return gbc;
    }

    private JLabel description(String text) {
        JLabel label = new JLabel(text.startsWith("<html>") ? text : CompactFormLayout.wrapped(text, TEXT_WIDTH));
        label.setForeground(MUTED);
        label.setFont(DESCRIPTION_FONT);
        return label;
    }

    private JLabel unit(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(MUTED);
        return label;
    }

    private JLabel fieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(UI_BOLD_FONT);
        return label;
    }

    private void applyCompactFonts(Component component) {
        if (component == null) {
            return;
        }
        if (component instanceof JLabel label && label.getFont() != DESCRIPTION_FONT) {
            label.setFont(UI_FONT);
        } else if (!(component instanceof JLabel)) {
            component.setFont(UI_FONT);
        }
        if (component instanceof JSpinner spinner) {
            spinner.setPreferredSize(new Dimension(72, 24));
        }
        if (component instanceof JTextField field) {
            Dimension preferred = field.getPreferredSize();
            field.setPreferredSize(new Dimension(Math.min(92, preferred.width), 24));
        }
        if (component instanceof JComboBox<?> comboBox) {
            comboBox.setPreferredSize(new Dimension(Math.min(180, comboBox.getPreferredSize().width), 24));
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                applyCompactFonts(child);
            }
        }
    }

    private String money(BigDecimal value) {
        return "$" + Monetary.round(value);
    }
}
