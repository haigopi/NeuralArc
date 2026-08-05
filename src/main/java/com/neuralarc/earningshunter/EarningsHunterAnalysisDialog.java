package com.neuralarc.earningshunter;

import com.neuralarc.model.StrategyMode;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.math.BigDecimal;

public final class EarningsHunterAnalysisDialog extends JDialog {
    public static final String TITLE = "Analyze Earnings Hunter Stocks - Scans live earnings catalysts";
    private final JTextField windowDays = new JTextField("7", 8);
    private final JTextField minAvgVolume = new JTextField("100000", 8);
    private final JTextField minPrice = new JTextField("0.5", 8);
    private final JTextField minRelVolume = new JTextField("0.5", 8);
    private final JTextField maxPrice = new JTextField("", 8);
    private final JTextField minNewsScore = new JTextField("50", 8);
    private final JTextField entryDiscount = new JTextField("2", 8);
    private final JTextField stop = new JTextField("5", 8);
    private final JTextField target = new JTextField("10", 8);
    private final JTextField maxStocks = new JTextField("10", 8);
    private final JTextArea candidateSymbols = new JTextArea(3, 24);
    private final StrategyMode mode;
    private boolean accepted;
    private RunMode runMode = RunMode.ANALYZE;

    public enum RunMode { ANALYZE, ANALYZE_AND_EXECUTE }

    public EarningsHunterAnalysisDialog(Window owner, StrategyMode mode, EarningsHunterConfig existing) {
        super(owner, TITLE, ModalityType.APPLICATION_MODAL);
        com.neuralarc.ui.DialogCloseActions.bindEscapeToClose(this);
        this.mode = mode == null ? StrategyMode.PAPER : mode;
        apply(existing == null ? EarningsHunterConfig.defaults(this.mode) : existing);
        setContentPane(build());
        pack();
    }

    public boolean accepted() { return accepted; }
    public RunMode runMode() { return runMode; }

    public EarningsHunterConfig config() {
        return new EarningsHunterConfig(Integer.parseInt(windowDays.getText()), Long.parseLong(minAvgVolume.getText()),
                new BigDecimal(minPrice.getText()), new BigDecimal(minRelVolume.getText()),
                maxPrice.getText().isBlank() ? null : new BigDecimal(maxPrice.getText()),
                new BigDecimal(minNewsScore.getText()), new BigDecimal(entryDiscount.getText()),
                new BigDecimal(stop.getText()), new BigDecimal(target.getText()),
                Integer.parseInt(maxStocks.getText()), mode, EarningsHunterLiveScanner.parseSymbols(candidateSymbols.getText()));
    }

    private JPanel build() {
        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        int row = 0;
        row = addField(fields, row, "Earnings News Window (days)", windowDays,
                "Only include symbols with Alpaca news containing earnings terms within this many calendar days. Default: 7.");
        row = addField(fields, row, "Minimum Average Volume", minAvgVolume,
                "Require enough average daily shares traded for liquidity. Default is 100,000 shares.");
        row = addField(fields, row, "Minimum Stock Price", minPrice,
                "Reject very low-priced symbols before scoring.");
        row = addField(fields, row, "Minimum Relative Volume", minRelVolume,
                "Require the current session volume pace to be above normal. This confirms the market is reacting to the earnings catalyst.");
        row = addField(fields, row, "Maximum Stock Price", maxPrice,
                "Optional cap for high-priced stocks. Leave blank to allow any price above the minimum.");
        row = addField(fields, row, "Minimum News Score", minNewsScore,
                "Scores the strength of earnings-related articles and price reaction. Raise it for stricter filtering; lower it to see more candidates.");
        row = addField(fields, row, "Entry Discount Below Current Price %", entryDiscount,
                "Safety cap for the entry. The scanner also computes support from the lowest daily lows across roughly 1, 3, and 6 months, then chooses the lower of that support-gravity entry or this flat discount.");
        row = addField(fields, row, "Stop Loss %", stop,
                "Planning risk from the discounted entry price for the strategy row.");
        row = addField(fields, row, "Target Profit %", target,
                "Planning target above the discounted entry price for the strategy row.");
        row = addField(fields, row, "Max Stocks to Add", maxStocks,
                "Limit how many qualifying recommendations are added after scoring.");
        row = addField(fields, row, "Candidate Symbols (optional)", new JScrollPane(candidateSymbols),
                "Leave blank to auto-discover active stocks from Alpaca screener, then filter them by live earnings news. Or enter symbols manually.");
        addField(fields, row, "Mode", new JLabel(mode.name()), "Uses the current NeuralArc mode automatically.");
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
        JButton run = new JButton("Run Earnings Hunter  ▾");
        cancel.addActionListener(event -> dispose());
        JPopupMenu menu = new JPopupMenu();
        menu.add(runItem("Analyze now", RunMode.ANALYZE));
        menu.add(runItem("Analyze & Execute now", RunMode.ANALYZE_AND_EXECUTE));
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

    private void apply(EarningsHunterConfig c) {
        windowDays.setText(String.valueOf(c.earningsWindowDays()));
        minAvgVolume.setText(String.valueOf(c.minimumAverageVolume()));
        minPrice.setText(c.minimumStockPrice().toPlainString());
        minRelVolume.setText(c.minimumRelativeVolume().toPlainString());
        maxPrice.setText(c.maximumStockPrice() == null ? "" : c.maximumStockPrice().toPlainString());
        minNewsScore.setText(c.minimumNewsScore().toPlainString());
        entryDiscount.setText(c.entryDiscountPercent().toPlainString());
        stop.setText(c.stopLossPercent().toPlainString());
        target.setText(c.targetProfitPercent().toPlainString());
        maxStocks.setText(String.valueOf(c.maxStocksToAdd()));
        candidateSymbols.setText(String.join(", ", c.candidateSymbols()));
    }
}
