package com.neuralarc.ui;

import com.neuralarc.model.RecommendationType;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.util.FontLoader;
import com.neuralarc.util.Monetary;

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
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
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
    private static final String DISCLAIMER = "<html><body style='width:520px'>"
            + "<b>Disclaimer:</b><br>"
            + "All portfolio capture actions execute using live market orders at the marketplace.<br>"
            + "Final execution prices depend on real-time market conditions, bid/ask spread, liquidity, slippage, and broker execution timing.<br>"
            + "Exact captured profit/loss numbers shown in the application may slightly vary from broker-reported final values.<br>"
            + "During volatile market conditions, executed values may differ from displayed estimated values.<br>"
            + "By proceeding, the user acknowledges and accepts potential execution variances."
            + "</body></html>";

    private final Function<PortfolioCaptureConfig, PortfolioCaptureSnapshot> snapshotSupplier;
    private final Consumer<PortfolioCaptureConfig> captureNowHandler;
    private final Consumer<PortfolioCaptureConfig> activateHandler;
    private final Runnable deactivateHandler;
    private final boolean monitoringActive;
    private final Timer refreshTimer;

    private final JRadioButton captureNow = new JRadioButton("Capture Now", true);
    private final JRadioButton captureTarget = new JRadioButton("Capture When Target Is Reached");
    private final JRadioButton percentTarget = new JRadioButton("Profit Percent", true);
    private final JRadioButton amountTarget = new JRadioButton("Profit Amount");
    private final JTextField percentField = new JTextField("5", 8);
    private final JTextField amountField = new JTextField("500", 8);
    private final JCheckBox includeLosses = new JCheckBox("Include losses while calculating net portfolio profit/loss", true);
    private final JTextField intervalField = new JTextField("1", 5);
    private final JCheckBox activeOnly = new JCheckBox("Include only active strategies", true);
    private final JRadioButton executeOnce = new JRadioButton("Execute Once And Stop", true);
    private final JRadioButton reenterOnce = new JRadioButton("Execute Capture Then Re-Enter Once");
    private final JRadioButton continuousLoop = new JRadioButton("Continuous Automated Loop");
    private final JRadioButton paperMode = new JRadioButton("Paper Trading Mode", true);
    private final JRadioButton liveMode = new JRadioButton("Live Trading Mode");
    private final JSpinner reentryQuantity = new JSpinner(new SpinnerNumberModel(1, 1, 100000, 1));
    private final JComboBox<RecommendationType> reentryTerm = new JComboBox<>(RecommendationType.values());
    private final JCheckBox autoCleanPending = new JCheckBox("Auto clean pending positions before every new capture cycle");
    private final JCheckBox acknowledgement = new JCheckBox("I understand that market execution prices and final broker values may vary.");
    private final JButton saveButton = new JButton("Save");
    private final JButton deactivateButton = new JButton("Deactivate Monitoring");
    private final JLabel investmentValue = new JLabel("-");
    private final JLabel marketValue = new JLabel("-");
    private final JLabel pnlValue = new JLabel("-");
    private final JLabel pnlPercentValue = new JLabel("-");
    private final JLabel progressValue = new JLabel("-");

    private boolean executed;

    PortfolioCaptureDialog(
            Frame owner,
            Function<PortfolioCaptureConfig, PortfolioCaptureSnapshot> snapshotSupplier,
            Consumer<PortfolioCaptureConfig> captureNowHandler,
            Consumer<PortfolioCaptureConfig> activateHandler,
            Runnable deactivateHandler,
            boolean monitoringActive
    ) {
        super(owner, "Capture Portfolio", true);
        this.snapshotSupplier = snapshotSupplier;
        this.captureNowHandler = captureNowHandler;
        this.activateHandler = activateHandler;
        this.deactivateHandler = deactivateHandler;
        this.monitoringActive = monitoringActive;
        this.refreshTimer = new Timer(1000, ignored -> refreshMetrics());
        buildUi();
        refreshMetrics();
        updateEnabledState();
        pack();
        setMinimumSize(getPreferredSize());
        setLocationRelativeTo(owner);
    }

    boolean showDialog() {
        refreshTimer.start();
        setVisible(true);
        refreshTimer.stop();
        return executed;
    }

    private void buildUi() {
        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBorder(new EmptyBorder(14, 16, 14, 16));
        content.add(modeSection(), BorderLayout.NORTH);

        JPanel center = new JPanel(new GridBagLayout());
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
        content.add(center, BorderLayout.CENTER);
        content.add(buttonBar(), BorderLayout.SOUTH);
        setContentPane(content);

        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(captureNow);
        modeGroup.add(captureTarget);
        ButtonGroup targetGroup = new ButtonGroup();
        targetGroup.add(percentTarget);
        targetGroup.add(amountTarget);
        ButtonGroup flowGroup = new ButtonGroup();
        flowGroup.add(executeOnce);
        flowGroup.add(reenterOnce);
        flowGroup.add(continuousLoop);
        ButtonGroup modeGroup2 = new ButtonGroup();
        modeGroup2.add(paperMode);
        modeGroup2.add(liveMode);
        captureNow.addActionListener(e -> updateEnabledState());
        captureTarget.addActionListener(e -> updateEnabledState());
        percentTarget.addActionListener(e -> updateEnabledState());
        amountTarget.addActionListener(e -> updateEnabledState());
        executeOnce.addActionListener(e -> updateEnabledState());
        reenterOnce.addActionListener(e -> updateEnabledState());
        continuousLoop.addActionListener(e -> updateEnabledState());
        acknowledgement.addActionListener(e -> updateEnabledState());
    }

    private JPanel modeSection() {
        JPanel panel = section("Capture Mode");
        addRadioWithDescription(panel, captureNow,
                "Capture the portfolio as it is at the current market price.", 0);
        addRadioWithDescription(panel, captureTarget,
                "Automatically monitor and capture the portfolio once the configured target is reached.", 2);
        return panel;
    }

    private JPanel metricsSection() {
        JPanel panel = section("Portfolio Metrics");
        addMetric(panel, "Total Investment", investmentValue, 0);
        addMetric(panel, "Current Market Value", marketValue, 1);
        addMetric(panel, "Current Unrealized Profit/Loss", pnlValue, 2);
        addMetric(panel, "Profit/Loss Percent", pnlPercentValue, 3);
        addMetric(panel, "Current Target Progress", progressValue, 4);
        return panel;
    }

    private JPanel targetSection() {
        JPanel panel = section("Target Monitoring");
        GridBagConstraints gbc = baseGbc(0);
        panel.add(percentTarget, gbc);
        gbc.gridx = 1;
        panel.add(percentField, gbc);
        gbc.gridx = 0;
        gbc.gridy++;
        panel.add(amountTarget, gbc);
        gbc.gridx = 1;
        panel.add(amountField, gbc);
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        panel.add(includeLosses, gbc);
        gbc.gridy++;
        panel.add(description("Monitoring executes market orders automatically once the configured portfolio target is reached."), gbc);
        gbc.gridy++;
        panel.add(advancedPanel(), gbc);
        return panel;
    }

    private JPanel advancedPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        panel.setOpaque(false);
        panel.add(new JLabel("Monitoring Interval"));
        panel.add(intervalField);
        panel.add(new JLabel("seconds"));
        panel.add(activeOnly);
        return panel;
    }

    private JPanel postAutomationSection() {
        JPanel panel = section("Post-Capture Automation");
        addRadioWithDescription(panel, executeOnce,
                "Capture executes once and monitoring stops afterward.", 0);
        addRadioWithDescription(panel, reenterOnce,
                "Capture executes, I Am Feeling Lucky runs once, then monitoring stops.", 2);
        addRadioWithDescription(panel, continuousLoop,
                "Capture executes, I Am Feeling Lucky runs automatically, monitoring restarts, and the cycle repeats.", 4);
        GridBagConstraints gbc = baseGbc(6);
        gbc.gridwidth = 2;
        panel.add(reentryOptionsPanel(), gbc);
        gbc.gridy++;
        panel.add(autoCleanPending, gbc);
        gbc.gridy++;
        panel.add(description("Pending base buy limits will be automatically cancelled before every automated capture cycle."), gbc);
        return panel;
    }

    private JPanel reentryOptionsPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        panel.setOpaque(false);
        panel.add(paperMode);
        panel.add(liveMode);
        panel.add(new JLabel("Quantity"));
        panel.add(reentryQuantity);
        panel.add(new JLabel("Term"));
        panel.add(reentryTerm);
        return panel;
    }

    private JPanel disclaimerSection() {
        JPanel panel = section("Execution Disclaimer");
        GridBagConstraints gbc = baseGbc(0);
        gbc.gridwidth = 2;
        panel.add(description(DISCLAIMER), gbc);
        gbc.gridy++;
        panel.add(acknowledgement, gbc);
        return panel;
    }

    private JPanel buttonBar() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
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
            JOptionPane.showMessageDialog(this, "No eligible portfolio positions are available to capture.",
                    "Capture Portfolio", JOptionPane.WARNING_MESSAGE);
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
                    "Confirm Portfolio Capture",
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
                    "Capture Portfolio", JOptionPane.WARNING_MESSAGE);
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
                    "Capture Portfolio", JOptionPane.WARNING_MESSAGE);
            return Optional.empty();
        }
        if (target.compareTo(BigDecimal.ZERO) <= 0) {
            JOptionPane.showMessageDialog(this, "Target value must be greater than zero.",
                    "Capture Portfolio", JOptionPane.WARNING_MESSAGE);
            return Optional.empty();
        }
        int interval;
        try {
            interval = Integer.parseInt(intervalField.getText().trim());
        } catch (NumberFormatException ex) {
            interval = 1;
        }
        if (interval <= 0) {
            JOptionPane.showMessageDialog(this, "Monitoring interval must be greater than zero.",
                    "Capture Portfolio", JOptionPane.WARNING_MESSAGE);
            return Optional.empty();
        }
        return Optional.of(new PortfolioCaptureConfig(
                PortfolioCaptureMode.TARGET_MONITORING,
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
                autoCleanPending.isSelected()
        ));
    }

    private void updateEnabledState() {
        boolean targetMode = captureTarget.isSelected();
        boolean reentryEnabled = targetMode && (reenterOnce.isSelected() || continuousLoop.isSelected());
        percentTarget.setEnabled(targetMode);
        amountTarget.setEnabled(targetMode);
        percentField.setEnabled(targetMode && percentTarget.isSelected());
        amountField.setEnabled(targetMode && amountTarget.isSelected());
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
        autoCleanPending.setEnabled(targetMode);
        saveButton.setText(targetMode ? "Activate Monitoring" : "Capture Now");
        saveButton.setEnabled(acknowledgement.isSelected());
    }

    private void refreshMetrics() {
        PortfolioCaptureSnapshot snapshot = snapshotSupplier.apply(previewConfig());
        investmentValue.setText(money(snapshot.totalInvestment()));
        marketValue.setText(money(snapshot.marketValue()));
        pnlValue.setText(money(snapshot.unrealizedPnl()));
        pnlValue.setForeground(snapshot.unrealizedPnl().compareTo(BigDecimal.ZERO) < 0
                ? new Color(183, 28, 28) : new Color(27, 94, 32));
        pnlPercentValue.setText(Monetary.round(snapshot.profitLossPercent()) + "%");
        progressValue.setText(Monetary.round(snapshot.targetProgressPercent()) + "%");
    }

    private PortfolioCaptureConfig previewConfig() {
        if (!captureTarget.isSelected()) {
            return PortfolioCaptureConfig.captureNow();
        }
        BigDecimal target;
        try {
            target = new BigDecimal(percentTarget.isSelected() ? percentField.getText().trim() : amountField.getText().trim());
        } catch (NumberFormatException ex) {
            target = BigDecimal.ZERO;
        }
        return new PortfolioCaptureConfig(
                PortfolioCaptureMode.TARGET_MONITORING,
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
                autoCleanPending.isSelected()
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
                autoCleanPending.isSelected()
        );
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
            return 1;
        }
    }

    private JPanel section(String title) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(title),
                new EmptyBorder(8, 10, 10, 10)
        ));
        return panel;
    }

    private void addRadioWithDescription(JPanel panel, JRadioButton radio, String text, int row) {
        GridBagConstraints gbc = baseGbc(row);
        gbc.gridwidth = 2;
        panel.add(radio, gbc);
        gbc.gridy++;
        panel.add(description(text), gbc);
    }

    private void addMetric(JPanel panel, String label, JLabel value, int row) {
        GridBagConstraints gbc = baseGbc(row);
        JLabel name = new JLabel(label);
        name.setFont(FontLoader.ui(Font.BOLD, 12f));
        panel.add(name, gbc);
        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.EAST;
        value.setHorizontalAlignment(SwingConstants.RIGHT);
        panel.add(value, gbc);
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
        JLabel label = new JLabel(text);
        label.setForeground(MUTED);
        label.setFont(FontLoader.ui(Font.PLAIN, 11f));
        return label;
    }

    private String money(BigDecimal value) {
        return "$" + Monetary.round(value);
    }
}
