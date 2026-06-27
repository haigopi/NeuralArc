package com.neuralarc.vwap;

import com.neuralarc.model.StrategyMode;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.math.BigDecimal;

public final class VwapAnalysisDialog extends JDialog {
    public static final String TITLE = "Analyze VWAP Desk Stocks - Executes 10:00 AM ET to 3:30 PM ET";
    private final JTextField minDiscount = new JTextField("1", 8);
    private final JTextField maxDiscount = new JTextField("8", 8);
    private final JTextField minAvgVolume = new JTextField("500000", 8);
    private final JTextField minPrice = new JTextField("5", 8);
    private final JTextField minRelVolume = new JTextField("1.0", 8);
    private final JTextField maxPrice = new JTextField("", 8);
    private final JTextArea candidateSymbols = new JTextArea(3, 24);
    private final JComboBox<VwapConfig.TrendFilter> trend = new JComboBox<>(VwapConfig.TrendFilter.values());
    private final JTextField stop = new JTextField("4", 8);
    private final JTextField maxStocks = new JTextField("10", 8);
    private final JComboBox<VwapConfig.ExecutionFrequency> frequency = new JComboBox<>(VwapConfig.ExecutionFrequency.values());
    private final StrategyMode mode;
    private boolean accepted;
    private RunMode runMode = RunMode.ANALYZE;

    /** How the operator chose to run the VWAP Desk scan from the consolidated control. */
    public enum RunMode { ANALYZE, ANALYZE_AND_EXECUTE, SCHEDULE }

    public VwapAnalysisDialog(Window owner, StrategyMode mode, VwapConfig existing) {
        super(owner, TITLE, ModalityType.APPLICATION_MODAL);
        com.neuralarc.ui.DialogCloseActions.bindEscapeToClose(this);
        this.mode = mode == null ? StrategyMode.PAPER : mode;
        apply(existing == null ? VwapConfig.defaults(this.mode) : existing);
        setContentPane(build());
        pack();
    }

    public boolean accepted() { return accepted; }

    public RunMode runMode() { return runMode; }

    public VwapConfig config() {
        return new VwapConfig(new BigDecimal(minDiscount.getText()), new BigDecimal(maxDiscount.getText()),
                Long.parseLong(minAvgVolume.getText()), new BigDecimal(minPrice.getText()),
                new BigDecimal(minRelVolume.getText()), maxPrice.getText().isBlank() ? null : new BigDecimal(maxPrice.getText()),
                (VwapConfig.TrendFilter) trend.getSelectedItem(), new BigDecimal(stop.getText()),
                Integer.parseInt(maxStocks.getText()), (VwapConfig.ExecutionFrequency) frequency.getSelectedItem(), mode,
                VwapLiveScanner.parseSymbols(candidateSymbols.getText()));
    }

    private JPanel build() {
        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        int row = 0;
        row = addField(fields, row, "Minimum Discount % below VWAP", minDiscount,
                "Only include stocks trading at least this far below their intraday VWAP. Example: 1 means -1% or more under VWAP.");
        row = addField(fields, row, "Maximum Discount % below VWAP", maxDiscount,
                "Reject stretches deeper than this to avoid outright breakdowns. Example: 8 means skip anything more than 8% under VWAP.");
        row = addField(fields, row, "Minimum Average Volume", minAvgVolume,
                "Require enough average daily shares traded to ensure liquidity. Default is 500,000 shares.");
        row = addField(fields, row, "Minimum Stock Price", minPrice,
                "Reject very low-priced stocks below this value before scoring.");
        row = addField(fields, row, "Minimum Relative Volume", minRelVolume,
                "Require today's activity to be at least this multiple of normal so the reversion has participation. Example: 1.0 means typical volume or higher.");
        row = addField(fields, row, "Maximum Stock Price", maxPrice,
                "Optional cap for high-priced stocks. Leave blank to allow any price above the minimum.");
        row = addField(fields, row, "Candidate Symbols (optional)", new JScrollPane(candidateSymbols),
                "Leave blank to auto-discover the day's discounted leaders from the Alpaca screener. Or enter specific live "
                        + "tickers, separated by commas or spaces, to scan only those. NeuralArc does not use hardcoded stock candidates.");
        row = addField(fields, row, "Trend Filter", trend,
                "Confirm the name is still strong by requiring price above its 50-day, 200-day, or either moving average. Disabled skips this check.");
        row = addField(fields, row, "VWAP Desk Stop Loss %", stop,
                "Planned risk from the entry price. NeuralArc uses this for strategy-level planning instead of broker blended position accounting. The reversion target is VWAP itself.");
        row = addField(fields, row, "Max Stocks to Add", maxStocks,
                "Limit how many qualifying recommendations are added to the VWAP Desk grid after scoring.");
        row = addField(fields, row, "Execution Frequency", frequency,
                "How often this scanner should run when scheduled. Manual means it only runs when you click Analyze.");
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
        JButton run = new JButton("Run VWAP Desk  ▾");
        cancel.addActionListener(event -> dispose());

        JPopupMenu menu = new JPopupMenu();
        menu.add(runItem("Analyze now", RunMode.ANALYZE, true));
        menu.add(runItem("Analyze & Execute now", RunMode.ANALYZE_AND_EXECUTE, true));
        JMenuItem schedule = runItem("Schedule (10:00 ET, autonomous)", RunMode.SCHEDULE, true);
        schedule.setToolTipText("Run this scan automatically at 10:00 ET on trading days. NeuralArc must be running at that time.");
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

    private void apply(VwapConfig c) {
        minDiscount.setText(c.minimumDiscountPercent().toPlainString());
        maxDiscount.setText(c.maximumDiscountPercent().toPlainString());
        minAvgVolume.setText(String.valueOf(c.minimumAverageVolume()));
        minPrice.setText(c.minimumStockPrice().toPlainString());
        minRelVolume.setText(c.minimumRelativeVolume().toPlainString());
        maxPrice.setText(c.maximumStockPrice() == null ? "" : c.maximumStockPrice().toPlainString());
        candidateSymbols.setText(String.join(", ", c.candidateSymbols()));
        trend.setSelectedItem(c.trendFilter());
        stop.setText(c.stopLossPercent().toPlainString());
        maxStocks.setText(String.valueOf(c.maxStocksToAdd()));
        frequency.setSelectedItem(c.executionFrequency());
    }
}
