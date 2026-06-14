package com.neuralarc.gaprocket;

import com.neuralarc.model.StrategyMode;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.math.BigDecimal;

public final class GapRocketAnalysisDialog extends JDialog {
    public static final String TITLE = "Analyze Gap-and-Go Stocks - Executes 9:45 AM ET to 11:00 AM ET";
    private final JTextField minGap = new JTextField("5", 8);
    private final JTextField minVolume = new JTextField("1000000", 8);
    private final JTextField minPrice = new JTextField("5", 8);
    private final JTextField minRelVolume = new JTextField("2", 8);
    private final JTextField maxPrice = new JTextField("", 8);
    private final JCheckBox catalystRequired = new JCheckBox("News Catalyst Required", true);
    private final JComboBox<GapRocketConfig.MarketTrendFilter> trend = new JComboBox<>(GapRocketConfig.MarketTrendFilter.values());
    private final JComboBox<GapRocketConfig.EntryStyle> entry = new JComboBox<>(GapRocketConfig.EntryStyle.values());
    private final JComboBox<GapRocketConfig.OpeningRangeDuration> range = new JComboBox<>(GapRocketConfig.OpeningRangeDuration.values());
    private final JTextField stop = new JTextField("1", 8);
    private final JTextField target = new JTextField("2", 8);
    private final JTextField maxStocks = new JTextField("10", 8);
    private final JComboBox<GapRocketConfig.ExecutionFrequency> frequency = new JComboBox<>(GapRocketConfig.ExecutionFrequency.values());
    private final StrategyMode mode;
    private boolean accepted;
    private boolean executeRequested;

    public GapRocketAnalysisDialog(Window owner, StrategyMode mode, GapRocketConfig existing) {
        super(owner, TITLE, ModalityType.APPLICATION_MODAL);
        this.mode = mode == null ? StrategyMode.PAPER : mode;
        apply(existing == null ? GapRocketConfig.defaults(this.mode) : existing);
        setContentPane(build());
        pack();
    }

    public boolean accepted() { return accepted; }

    public boolean executeRequested() { return executeRequested; }

    public GapRocketConfig config() {
        return new GapRocketConfig(new BigDecimal(minGap.getText()), Long.parseLong(minVolume.getText()), new BigDecimal(minPrice.getText()),
                new BigDecimal(minRelVolume.getText()), maxPrice.getText().isBlank() ? null : new BigDecimal(maxPrice.getText()),
                catalystRequired.isSelected(), null, (GapRocketConfig.MarketTrendFilter) trend.getSelectedItem(),
                (GapRocketConfig.EntryStyle) entry.getSelectedItem(), (GapRocketConfig.OpeningRangeDuration) range.getSelectedItem(),
                new BigDecimal(stop.getText()), new BigDecimal(target.getText()), Integer.parseInt(maxStocks.getText()),
                (GapRocketConfig.ExecutionFrequency) frequency.getSelectedItem(), mode);
    }

    private JPanel build() {
        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        int row = 0;
        row = addField(fields, row, "Minimum Premarket Gap %", minGap,
                "Only include stocks already above the previous close by at least this percent before the open. Example: 5 means +5% or higher.");
        row = addField(fields, row, "Minimum Premarket Volume", minVolume,
                "Require enough premarket shares traded to avoid thin, hard-to-fill movers. Default is 1,000,000 shares.");
        row = addField(fields, row, "Minimum Stock Price", minPrice,
                "Reject very low-priced stocks below this value before scoring.");
        row = addField(fields, row, "Minimum Relative Volume", minRelVolume,
                "Require current activity to be meaningfully higher than normal. Example: 2 means at least 2x typical volume.");
        row = addField(fields, row, "Maximum Stock Price", maxPrice,
                "Optional cap for high-priced stocks. Leave blank to allow any price above the minimum.");
        row = addField(fields, row, "News Catalyst Required", catalystRequired,
                "When enabled, candidates need a reason for the gap such as earnings, FDA/biotech news, analyst upgrades, contracts, partnerships, or breaking news.");
        row = addField(fields, row, "Market Trend Filter", trend,
                "Require SPY, QQQ, or either index to be green so long ideas align with the morning market tone. Disabled skips this check.");
        row = addField(fields, row, "Entry Style", entry,
                "Choose how the strategy waits after the open: opening-range breakout, breakout retest, VWAP pullback, or manual review only.");
        row = addField(fields, row, "Opening Range Duration", range,
                "How long after 9:30 AM ET to build the opening high/low before evaluating breakout or pullback entries.");
        row = addField(fields, row, "Gap-and-Go Stop Loss %", stop,
                "Planned risk from the entry price. NeuralArc uses this for strategy-level planning instead of broker blended position accounting.");
        row = addField(fields, row, "Gap-and-Go Take Profit %", target,
                "Planned reward target from the entry price for this Gap-and-Go setup.");
        row = addField(fields, row, "Max Stocks to Add", maxStocks,
                "Limit how many qualifying recommendations are added to the Gap Rocket grid after scoring.");
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
        panel.add(label, labelConstraints);
        panel.add(component, fieldConstraints);
        row++;
        GridBagConstraints helpConstraints = constraints(1, row, GridBagConstraints.WEST, 1);
        helpConstraints.insets = new Insets(0, 8, 8, 0);
        panel.add(help, helpConstraints);
        return row + 1;
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
        JButton analyze = new JButton("Analyze");
        JButton analyzeAndExecute = new JButton("Analyze & Execute");
        cancel.addActionListener(event -> dispose());
        analyze.addActionListener(event -> { accepted = true; executeRequested = false; dispose(); });
        analyzeAndExecute.addActionListener(event -> { accepted = true; executeRequested = true; dispose(); });
        buttons.add(cancel);
        buttons.add(analyze);
        buttons.add(analyzeAndExecute);
        return buttons;
    }

    private void apply(GapRocketConfig c) {
        minGap.setText(c.minimumPremarketGapPercent().toPlainString()); minVolume.setText(String.valueOf(c.minimumPremarketVolume()));
        minPrice.setText(c.minimumStockPrice().toPlainString()); minRelVolume.setText(c.minimumRelativeVolume().toPlainString());
        maxPrice.setText(c.maximumStockPrice() == null ? "" : c.maximumStockPrice().toPlainString()); catalystRequired.setSelected(c.newsCatalystRequired());
        trend.setSelectedItem(c.marketTrendFilter()); entry.setSelectedItem(c.entryStyle()); range.setSelectedItem(c.openingRangeDuration());
        stop.setText(c.stopLossPercent().toPlainString()); target.setText(c.takeProfitPercent().toPlainString()); maxStocks.setText(String.valueOf(c.maxStocksToAdd()));
        frequency.setSelectedItem(c.executionFrequency());
    }
}
