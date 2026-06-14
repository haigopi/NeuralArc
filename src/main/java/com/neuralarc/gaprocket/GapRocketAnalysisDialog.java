package com.neuralarc.gaprocket;

import com.neuralarc.model.StrategyMode;

import javax.swing.*;
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
        JPanel panel = new JPanel(new GridLayout(0, 2, 8, 6));
        panel.add(new JLabel("Minimum Premarket Gap %")); panel.add(minGap);
        panel.add(new JLabel("Minimum Premarket Volume")); panel.add(minVolume);
        panel.add(new JLabel("Minimum Stock Price")); panel.add(minPrice);
        panel.add(new JLabel("Minimum Relative Volume")); panel.add(minRelVolume);
        panel.add(new JLabel("Maximum Stock Price")); panel.add(maxPrice);
        panel.add(catalystRequired); panel.add(new JLabel("Earnings, FDA/biotech, analyst, contract, breaking news"));
        panel.add(new JLabel("Market Trend Filter")); panel.add(trend);
        panel.add(new JLabel("Entry Style")); panel.add(entry);
        panel.add(new JLabel("Opening Range Duration")); panel.add(range);
        panel.add(new JLabel("Stop Loss %")); panel.add(stop);
        panel.add(new JLabel("Take Profit %")); panel.add(target);
        panel.add(new JLabel("Max Stocks to Add")); panel.add(maxStocks);
        panel.add(new JLabel("Execution Frequency")); panel.add(frequency);
        panel.add(new JLabel("Mode")); panel.add(new JLabel(mode.name()));
        JPanel wrapper = new JPanel(new BorderLayout(8, 8));
        wrapper.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        wrapper.add(panel, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancel = new JButton("Cancel");
        JButton analyze = new JButton("Analyze");
        JButton analyzeAndExecute = new JButton("Analyze & Execute");
        cancel.addActionListener(event -> dispose());
        analyze.addActionListener(event -> { accepted = true; executeRequested = false; dispose(); });
        analyzeAndExecute.addActionListener(event -> { accepted = true; executeRequested = true; dispose(); });
        buttons.add(cancel); buttons.add(analyze); buttons.add(analyzeAndExecute);
        wrapper.add(buttons, BorderLayout.SOUTH);
        return wrapper;
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
