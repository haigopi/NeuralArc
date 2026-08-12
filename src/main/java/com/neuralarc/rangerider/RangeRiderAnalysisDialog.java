package com.neuralarc.rangerider;

import com.neuralarc.model.StrategyMode;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.math.BigDecimal;

public final class RangeRiderAnalysisDialog extends JDialog {
    public static final String TITLE = "Analyze Range Rider Stocks - Executes 9:45 AM ET to 3:30 PM ET";
    private final JTextField lookbackSessions = new JTextField("15", 8);
    private final JTextField minRange = new JTextField("1", 8);
    private final JTextField maxRange = new JTextField("12", 8);
    private final JTextField minFillRate = new JTextField("50", 8);
    private final JTextField minAvgVolume = new JTextField("1000000", 8);
    private final JTextField minPrice = new JTextField("10", 8);
    private final JTextField maxPrice = new JTextField("", 8);
    private final JTextArea candidateSymbols = new JTextArea(3, 24);
    private final JTextField targetCapture = new JTextField("50", 8);
    private final JTextField minExpectedGain = new JTextField("0.5", 8);
    private final JTextField stop = new JTextField("2", 8);
    private final JTextField maxStocks = new JTextField("10", 8);
    private final JComboBox<RangeRiderConfig.ExecutionFrequency> frequency =
            new JComboBox<>(RangeRiderConfig.ExecutionFrequency.values());
    private final StrategyMode mode;
    private boolean accepted;
    private RunMode runMode = RunMode.ANALYZE;

    /** How the operator chose to run the Range Rider scan from the consolidated control. */
    public enum RunMode { ANALYZE, ANALYZE_AND_EXECUTE, SCHEDULE }

    public RangeRiderAnalysisDialog(Window owner, StrategyMode mode, RangeRiderConfig existing) {
        super(owner, TITLE, ModalityType.APPLICATION_MODAL);
        com.neuralarc.ui.DialogCloseActions.bindEscapeToClose(this);
        this.mode = mode == null ? StrategyMode.PAPER : mode;
        apply(existing == null ? RangeRiderConfig.defaults(this.mode) : existing);
        setContentPane(build());
        pack();
    }

    public boolean accepted() { return accepted; }

    public RunMode runMode() { return runMode; }

    public RangeRiderConfig config() {
        return new RangeRiderConfig(Integer.parseInt(lookbackSessions.getText().trim()),
                new BigDecimal(minRange.getText().trim()), new BigDecimal(maxRange.getText().trim()),
                new BigDecimal(minFillRate.getText().trim()), Long.parseLong(minAvgVolume.getText().trim()),
                new BigDecimal(minPrice.getText().trim()),
                maxPrice.getText().isBlank() ? null : new BigDecimal(maxPrice.getText().trim()),
                new BigDecimal(targetCapture.getText().trim()), new BigDecimal(minExpectedGain.getText().trim()),
                new BigDecimal(stop.getText().trim()), Integer.parseInt(maxStocks.getText().trim()),
                (RangeRiderConfig.ExecutionFrequency) frequency.getSelectedItem(), mode,
                RangeRiderLiveScanner.parseSymbols(candidateSymbols.getText()));
    }

    private JPanel build() {
        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        int row = 0;
        row = addField(fields, row, "Lookback Sessions", lookbackSessions,
                "How many completed trading sessions to average. Default is 15, which is about the last three weeks. Today's unfinished session is never included.");
        row = addField(fields, row, "Minimum Average Daily Range %", minRange,
                "Require the stock to travel at least this far between its average low and average high on a typical day. Most heavily traded large caps sit between 1% and 3%.");
        row = addField(fields, row, "Maximum Average Daily Range %", maxRange,
                "Reject stocks whose typical day is wider than this — those moves are news-driven rather than a repeatable daily range.");
        row = addField(fields, row, "Minimum Same-Day Fill Rate %", minFillRate,
                "Require the planned buy and the planned sell to have both been reached on at least this share of the lookback sessions, each measured against that session's own open. Daily bars cannot prove the low came before the high, so this is an optimistic upper bound.");
        row = addField(fields, row, "Minimum Average Volume", minAvgVolume,
                "Require enough average daily shares traded to ensure liquidity. Default is 1,000,000 shares, which keeps the scan on heavily traded names.");
        row = addField(fields, row, "Minimum Stock Price", minPrice,
                "Reject low-priced stocks below this value before scoring.");
        row = addField(fields, row, "Maximum Stock Price", maxPrice,
                "Optional cap for high-priced stocks. Leave blank to allow any price above the minimum.");
        row = addField(fields, row, "Candidate Symbols (optional)", new JScrollPane(candidateSymbols),
                "Leave blank to auto-discover the most actively traded stocks from the Alpaca screener. Or enter specific live "
                        + "tickers, separated by commas or spaces, to scan only those. NeuralArc does not use hardcoded stock candidates.");
        row = addField(fields, row, "Range Capture %", targetCapture,
                "What share of the typical daily dip and rally the plan aims to take. This is the strategy's main trade-off: "
                        + "aiming for the whole move only completes on unusually wide, perfectly timed days, while taking half of it "
                        + "gives up size per trade but completes far more often. 50 is the default.");
        row = addField(fields, row, "Minimum Expected Gain %", minExpectedGain,
                "Skip stocks whose planned buy-to-sell round trip is worth less than this, so a quiet day is not traded for less than it costs in spread and commission.");
        row = addField(fields, row, "Range Rider Stop Loss %", stop,
                "Planned risk from the entry price. NeuralArc uses this for strategy-level planning instead of broker blended position accounting. The same-day target is the planned sell price.");
        row = addField(fields, row, "Max Stocks to Add", maxStocks,
                "Limit how many qualifying recommendations are added to the Range Rider grid after scoring.");
        row = addField(fields, row, "Execution Frequency", frequency,
                "How often this scanner should run when scheduled. The daily plan does not change intraday; re-scanning only picks up stocks that have newly traded down into their planned entry.");
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
        JButton run = new JButton("Run Range Rider  ▾");
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

    private void apply(RangeRiderConfig c) {
        lookbackSessions.setText(String.valueOf(c.lookbackSessions()));
        minRange.setText(c.minimumAverageRangePercent().toPlainString());
        maxRange.setText(c.maximumAverageRangePercent().toPlainString());
        minFillRate.setText(c.minimumSameDayFillRatePercent().toPlainString());
        minAvgVolume.setText(String.valueOf(c.minimumAverageVolume()));
        minPrice.setText(c.minimumStockPrice().toPlainString());
        maxPrice.setText(c.maximumStockPrice() == null ? "" : c.maximumStockPrice().toPlainString());
        candidateSymbols.setText(String.join(", ", c.candidateSymbols()));
        targetCapture.setText(c.targetCapturePercent().toPlainString());
        minExpectedGain.setText(c.minimumExpectedGainPercent().toPlainString());
        stop.setText(c.stopLossPercent().toPlainString());
        maxStocks.setText(String.valueOf(c.maxStocksToAdd()));
        frequency.setSelectedItem(c.executionFrequency());
    }
}
