package com.neuralarc.profitshield;

import com.neuralarc.model.StrategyMode;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.math.BigDecimal;

public final class ProfitShieldAnalysisDialog extends JDialog {
    public static final String TITLE = "Analyze Profit Shield Stocks - Defensive book that protects gains";
    private final JTextField lookbackSessions = new JTextField("126", 8);
    private final JTextField maxVolatility = new JTextField("3", 8);
    private final JTextField maxDrawdown = new JTextField("20", 8);
    private final JTextField maxDistanceFromHigh = new JTextField("12", 8);
    private final JTextField minAvgVolume = new JTextField("300000", 8);
    private final JTextField minPrice = new JTextField("5", 8);
    private final JTextField maxPrice = new JTextField("", 8);
    private final JComboBox<ProfitShieldConfig.TrendFilter> trendFilter =
            new JComboBox<>(ProfitShieldConfig.TrendFilter.values());
    private final JTextField entryDiscount = new JTextField("1", 8);
    private final JTextField protectiveStop = new JTextField("3", 8);
    private final JTextField target = new JTextField("6", 8);
    private final JTextField maxStocks = new JTextField("10", 8);
    private final JTextArea candidateSymbols = new JTextArea(3, 24);
    private final StrategyMode mode;
    private boolean accepted;
    private RunMode runMode = RunMode.ANALYZE;

    /** How the operator chose to run the Profit Shield scan from the consolidated control. */
    public enum RunMode { ANALYZE, ANALYZE_AND_EXECUTE, SCHEDULE }

    public ProfitShieldAnalysisDialog(Window owner, StrategyMode mode, ProfitShieldConfig existing) {
        super(owner, TITLE, ModalityType.APPLICATION_MODAL);
        com.neuralarc.ui.DialogCloseActions.bindEscapeToClose(this);
        this.mode = mode == null ? StrategyMode.PAPER : mode;
        apply(existing == null ? ProfitShieldConfig.defaults(this.mode) : existing);
        setContentPane(build());
        pack();
    }

    public boolean accepted() { return accepted; }

    public RunMode runMode() { return runMode; }

    public ProfitShieldConfig config() {
        return new ProfitShieldConfig(
                Integer.parseInt(lookbackSessions.getText().trim()),
                new BigDecimal(maxVolatility.getText().trim()),
                new BigDecimal(maxDrawdown.getText().trim()),
                new BigDecimal(maxDistanceFromHigh.getText().trim()),
                Long.parseLong(minAvgVolume.getText().trim()),
                new BigDecimal(minPrice.getText().trim()),
                maxPrice.getText().isBlank() ? null : new BigDecimal(maxPrice.getText().trim()),
                (ProfitShieldConfig.TrendFilter) trendFilter.getSelectedItem(),
                new BigDecimal(entryDiscount.getText().trim()),
                new BigDecimal(protectiveStop.getText().trim()),
                new BigDecimal(target.getText().trim()),
                Integer.parseInt(maxStocks.getText().trim()),
                mode,
                ProfitShieldLiveScanner.parseSymbols(candidateSymbols.getText()));
    }

    private JPanel build() {
        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        int row = 0;
        row = addField(fields, row, "Drawdown Lookback Sessions", lookbackSessions,
                "How many completed sessions the drawdown, distance-from-high, and resilience checks look back over. Default is 126, about six months. Today's unfinished session is never included.");
        row = addField(fields, row, "Maximum Daily Volatility %", maxVolatility,
                "Caps the 14-day average true range as a percent of price. A defensive book wants quiet names, so the default of 3% rejects anything that routinely swings harder than that in a day.");
        row = addField(fields, row, "Maximum Drawdown %", maxDrawdown,
                "Reject names whose deepest peak-to-trough decline over the lookback was worse than this. This is the core protection filter: it is how much the stock has historically given back.");
        row = addField(fields, row, "Maximum Distance Below High %", maxDistanceFromHigh,
                "Require the stock to still trade within this much of its own lookback high. Keeps the book in names that are holding up rather than ones still repairing damage.");
        row = addField(fields, row, "Minimum Average Volume", minAvgVolume,
                "Require enough average daily shares traded that a protective stop can actually fill. Default is 300,000 shares.");
        row = addField(fields, row, "Minimum Stock Price", minPrice,
                "Reject low-priced stocks before scoring. Defensive positions default to $5 and above.");
        row = addField(fields, row, "Maximum Stock Price", maxPrice,
                "Optional cap for high-priced stocks. Leave blank to allow any price above the minimum.");
        row = addField(fields, row, "Long-Term Trend Filter", trendFilter,
                "Which moving averages the price must hold. ABOVE_MA_50_AND_200 is the default: gains are only sheltered in names whose larger trend is still intact.");
        row = addField(fields, row, "Entry Discount Below Current Price %", entryDiscount,
                "How far below the current price the planned limit buy sits. Kept small by default so a slow, quiet name is not waited on forever.");
        row = addField(fields, row, "Protective Stop %", protectiveStop,
                "The widest stop allowed below the planned entry. When the 50-day average or the recent session low sits nearer than this, the stop is tightened to just under that shelf instead — but never closer than half this value.");
        row = addField(fields, row, "Target Profit %", target,
                "Planning target above the entry price. Deliberately modest: this book is sized to keep gains, not to chase them.");
        row = addField(fields, row, "Max Stocks to Add", maxStocks,
                "Limit how many qualifying recommendations are added to the Profit Shield grid after scoring.");
        row = addField(fields, row, "Candidate Symbols (optional)", new JScrollPane(candidateSymbols),
                "Leave blank to auto-discover liquid, actively traded stocks from the Alpaca screener and let the defensive filters decide. Or enter specific live tickers, separated by commas or spaces. NeuralArc does not use hardcoded stock candidates.");
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
        styleField(component);
        panel.add(label, constraints(0, row, GridBagConstraints.NORTHWEST, 0));
        panel.add(component, constraints(1, row, GridBagConstraints.WEST, 1));
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
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = x;
        c.gridy = y;
        c.anchor = anchor;
        c.weightx = weightx;
        c.insets = new Insets(3, 4, 3, 8);
        c.fill = x == 1 ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE;
        return c;
    }

    private JPanel buttons() {
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancel = new JButton("Cancel");
        JButton run = new JButton("Run Profit Shield  ▾");
        cancel.addActionListener(event -> dispose());
        JPopupMenu menu = new JPopupMenu();
        menu.add(runItem("Analyze now", RunMode.ANALYZE));
        menu.add(runItem("Analyze & Execute now", RunMode.ANALYZE_AND_EXECUTE));
        JMenuItem schedule = runItem("Schedule (9:45 ET, autonomous)", RunMode.SCHEDULE);
        schedule.setToolTipText("Run this scan automatically once per trading day at 9:45 ET. NeuralArc must be running at that time.");
        menu.add(schedule);
        run.addActionListener(event -> menu.show(run, 0, run.getHeight()));
        buttons.add(cancel);
        buttons.add(run);
        return buttons;
    }

    private JMenuItem runItem(String label, RunMode mode) {
        JMenuItem item = new JMenuItem(label);
        item.addActionListener(event -> { accepted = true; runMode = mode; dispose(); });
        return item;
    }

    private void apply(ProfitShieldConfig c) {
        lookbackSessions.setText(String.valueOf(c.drawdownLookbackSessions()));
        maxVolatility.setText(c.maximumDailyVolatilityPercent().toPlainString());
        maxDrawdown.setText(c.maximumDrawdownPercent().toPlainString());
        maxDistanceFromHigh.setText(c.maximumDistanceFromHighPercent().toPlainString());
        minAvgVolume.setText(String.valueOf(c.minimumAverageVolume()));
        minPrice.setText(c.minimumStockPrice().toPlainString());
        maxPrice.setText(c.maximumStockPrice() == null ? "" : c.maximumStockPrice().toPlainString());
        trendFilter.setSelectedItem(c.trendFilter());
        entryDiscount.setText(c.entryDiscountPercent().toPlainString());
        protectiveStop.setText(c.protectiveStopPercent().toPlainString());
        target.setText(c.targetProfitPercent().toPlainString());
        maxStocks.setText(String.valueOf(c.maxStocksToAdd()));
        candidateSymbols.setText(String.join(", ", c.candidateSymbols()));
    }
}
