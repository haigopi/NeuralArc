package com.neuralarc.swing;

import com.neuralarc.model.StrategyMode;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.math.BigDecimal;

public final class SwingAnalysisDialog extends JDialog {
    public static final String TITLE = "Analyze Swing Vault Stocks - Scans daily setups during the regular session";
    private final JTextField minPullback = new JTextField("3", 8);
    private final JTextField maxPullback = new JTextField("15", 8);
    private final JTextField minAvgVolume = new JTextField("500000", 8);
    private final JTextField minPrice = new JTextField("5", 8);
    private final JTextField minRelVolume = new JTextField("0.8", 8);
    private final JTextField maxPrice = new JTextField("", 8);
    private final JTextArea candidateSymbols = new JTextArea(3, 24);
    private final JComboBox<SwingConfig.TrendFilter> trend = new JComboBox<>(SwingConfig.TrendFilter.values());
    private final JTextField stop = new JTextField("6", 8);
    private final JTextField target = new JTextField("12", 8);
    private final JTextField maxStocks = new JTextField("10", 8);
    private final JComboBox<SwingConfig.ExecutionFrequency> frequency = new JComboBox<>(SwingConfig.ExecutionFrequency.values());
    private final StrategyMode mode;
    private boolean accepted;
    private RunMode runMode = RunMode.ANALYZE;

    /** How the operator chose to run the Swing Vault scan from the consolidated control. */
    public enum RunMode { ANALYZE, ANALYZE_AND_EXECUTE, SCHEDULE }

    public SwingAnalysisDialog(Window owner, StrategyMode mode, SwingConfig existing) {
        super(owner, TITLE, ModalityType.APPLICATION_MODAL);
        com.neuralarc.ui.DialogCloseActions.bindEscapeToClose(this);
        this.mode = mode == null ? StrategyMode.PAPER : mode;
        apply(existing == null ? SwingConfig.defaults(this.mode) : existing);
        setContentPane(build());
        pack();
    }

    public boolean accepted() { return accepted; }

    public RunMode runMode() { return runMode; }

    public SwingConfig config() {
        return new SwingConfig(new BigDecimal(minPullback.getText()), new BigDecimal(maxPullback.getText()),
                Long.parseLong(minAvgVolume.getText()), new BigDecimal(minPrice.getText()),
                new BigDecimal(minRelVolume.getText()), maxPrice.getText().isBlank() ? null : new BigDecimal(maxPrice.getText()),
                (SwingConfig.TrendFilter) trend.getSelectedItem(), new BigDecimal(stop.getText()),
                new BigDecimal(target.getText()), Integer.parseInt(maxStocks.getText()),
                (SwingConfig.ExecutionFrequency) frequency.getSelectedItem(), mode,
                SwingLiveScanner.parseSymbols(candidateSymbols.getText()));
    }

    private JPanel build() {
        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        int row = 0;
        row = addField(fields, row, "Minimum Pullback % from recent high", minPullback,
                "Only include stocks that have pulled back at least this far from their recent swing high. Example: 3 means a 3% or deeper dip.");
        row = addField(fields, row, "Maximum Pullback % from recent high", maxPullback,
                "Reject deeper drops to avoid broken trends. Example: 15 means skip anything more than 15% off the recent high.");
        row = addField(fields, row, "Minimum Average Volume", minAvgVolume,
                "Require enough average daily shares traded to ensure liquidity. Default is 500,000 shares.");
        row = addField(fields, row, "Minimum Stock Price", minPrice,
                "Reject very low-priced stocks below this value before scoring.");
        row = addField(fields, row, "Minimum Relative Volume", minRelVolume,
                "Require recent activity to be at least this multiple of normal. Healthy swing pullbacks often come on lighter volume, so the default is 0.8.");
        row = addField(fields, row, "Maximum Stock Price", maxPrice,
                "Optional cap for high-priced stocks. Leave blank to allow any price above the minimum.");
        row = addField(fields, row, "Candidate Symbols (optional)", new JScrollPane(candidateSymbols),
                "Leave blank to auto-discover the day's pulled-back leaders from the Alpaca screener. Or enter specific live "
                        + "tickers, separated by commas or spaces, to scan only those. NeuralArc does not use hardcoded stock candidates.");
        row = addField(fields, row, "Trend Filter", trend,
                "Confirm the daily uptrend is intact. Above MA 50 & 200 requires both; Stacked Uptrend requires price above the 20-, 50-, and 200-day stack. Disabled skips this check.");
        row = addField(fields, row, "Swing Vault Stop Loss %", stop,
                "Planned risk from the entry price, sized below support. NeuralArc uses this for strategy-level planning instead of broker blended position accounting.");
        row = addField(fields, row, "Target Profit %", target,
                "Fallback profit target from the entry price when no higher recent swing high is available. When a higher recent high exists, that high is used as the target.");
        row = addField(fields, row, "Max Stocks to Add", maxStocks,
                "Limit how many qualifying recommendations are added to the Swing Vault grid after scoring.");
        row = addField(fields, row, "Execution Frequency", frequency,
                "How often this scanner should run when scheduled. Manual means it only runs when you click Analyze; scheduled runs scan once per trading day.");
        addField(fields, row, "Mode", new JLabel(mode.name()),
                "Uses the current NeuralArc mode automatically. Paper and Live candidates remain isolated.");

        JPanel wrapper = new JPanel(new BorderLayout(8, 8));
        wrapper.setBorder(new EmptyBorder(12, 12, 12, 12));
        wrapper.add(fields, BorderLayout.CENTER);
        wrapper.add(buttons(), BorderLayout.SOUTH);
        return wrapper;
    }

    private int addField(JPanel panel, int row, String labelText, JComponent component, String description) {
        JLabel label = new JLabel(labelText);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
        JLabel help = new JLabel("<html><div style='width:360px;color:#6f7785;'>" + description + "</div></html>");
        help.setFont(help.getFont().deriveFont(Font.PLAIN, 10f));
        GridBagConstraints labelConstraints = constraints(0, row, GridBagConstraints.NORTHWEST, 0);
        GridBagConstraints fieldConstraints = constraints(1, row, GridBagConstraints.WEST, 1);
        styleField(component);
        panel.add(label, labelConstraints);
        panel.add(component, fieldConstraints);
        row++;
        GridBagConstraints helpConstraints = constraints(1, row, GridBagConstraints.WEST, 1);
        helpConstraints.insets = new Insets(0, 8, 8, 0);
        panel.add(help, helpConstraints);
        return row + 1;
    }

    private void styleField(JComponent component) {
        Font compact = component.getFont().deriveFont(Font.PLAIN, 10f);
        component.setFont(compact);
        if (component instanceof JComboBox<?> comboBox) {
            comboBox.setFont(compact);
        }
        if (component instanceof JScrollPane scrollPane) {
            scrollPane.getViewport().getView().setFont(compact);
        }
    }

    private GridBagConstraints constraints(int x, int y, int anchor, double weightx) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = x;
        constraints.gridy = y;
        constraints.anchor = anchor;
        constraints.weightx = weightx;
        constraints.insets = new Insets(3, 4, 3, 8);
        constraints.fill = x == 1 ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE;
        return constraints;
    }

    private JPanel buttons() {
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancel = new JButton("Cancel");
        JButton run = new JButton("Run Swing Vault  ▾");
        cancel.addActionListener(event -> dispose());

        JPopupMenu menu = new JPopupMenu();
        menu.add(runItem("Analyze now", RunMode.ANALYZE, true));
        menu.add(runItem("Analyze & Execute now", RunMode.ANALYZE_AND_EXECUTE, true));
        JMenuItem schedule = runItem("Schedule (9:45 ET, autonomous)", RunMode.SCHEDULE, true);
        schedule.setToolTipText("Run this scan automatically at 9:45 ET on trading days. NeuralArc must be running at that time.");
        menu.add(schedule);
        run.addActionListener(event -> menu.show(run, 0, run.getHeight()));

        buttons.add(cancel);
        buttons.add(run);
        return buttons;
    }

    private JMenuItem runItem(String label, RunMode mode, boolean enabled) {
        JMenuItem item = new JMenuItem(label);
        item.setEnabled(enabled);
        item.addActionListener(event -> { accepted = true; runMode = mode; dispose(); });
        return item;
    }

    private void apply(SwingConfig c) {
        minPullback.setText(c.minimumPullbackPercent().toPlainString());
        maxPullback.setText(c.maximumPullbackPercent().toPlainString());
        minAvgVolume.setText(String.valueOf(c.minimumAverageVolume()));
        minPrice.setText(c.minimumStockPrice().toPlainString());
        minRelVolume.setText(c.minimumRelativeVolume().toPlainString());
        maxPrice.setText(c.maximumStockPrice() == null ? "" : c.maximumStockPrice().toPlainString());
        candidateSymbols.setText(String.join(", ", c.candidateSymbols()));
        trend.setSelectedItem(c.trendFilter());
        stop.setText(c.stopLossPercent().toPlainString());
        target.setText(c.targetProfitPercent().toPlainString());
        maxStocks.setText(String.valueOf(c.maxStocksToAdd()));
        frequency.setSelectedItem(c.executionFrequency());
    }
}
