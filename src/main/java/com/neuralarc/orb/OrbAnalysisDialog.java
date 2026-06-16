package com.neuralarc.orb;

import com.neuralarc.model.StrategyMode;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

public final class OrbAnalysisDialog extends JDialog {
    public static final String TITLE = "Analyze ORB Stocks - Captures Opening Range from Live Alpaca Data";
    private final JComboBox<Integer> rangeMinutes = new JComboBox<>(new Integer[]{5, 15, 30});
    private final JTextArea candidateSymbols = new JTextArea(3, 24);
    private final JTextField minPrice = new JTextField("1", 8);
    private final JTextField maxPrice = new JTextField("", 8);
    private final JTextField minRelVolume = new JTextField("1.5", 8);
    private final JTextField minRange = new JTextField("0.20", 8);
    private final JTextField entryBuffer = new JTextField("0.10", 8);
    private final JComboBox<OrbConfig.StopMode> stopMode = new JComboBox<>(OrbConfig.StopMode.values());
    private final JTextField risk = new JTextField("1.00", 8);
    private final JTextField target = new JTextField("3.00", 8);
    private final JTextField maxStocks = new JTextField("10", 8);
    private final JTextField latestEntry = new JTextField("11:00", 8);
    private final JCheckBox autoDiscover = new JCheckBox("Auto-discover with Alpaca when symbols are blank", true);
    private final StrategyMode mode;
    private boolean accepted;
    private OrbRunMode runMode = OrbRunMode.ANALYZE_NOW;

    public OrbAnalysisDialog(Window owner, StrategyMode mode, OrbConfig existing) {
        super(owner, TITLE, ModalityType.APPLICATION_MODAL);
        this.mode = mode == null ? StrategyMode.PAPER : mode;
        apply(existing == null ? OrbConfig.defaults(this.mode) : existing);
        setContentPane(build());
        pack();
    }

    public boolean accepted() { return accepted; }
    public OrbRunMode runMode() { return runMode; }

    public OrbConfig config() {
        return new OrbConfig((Integer) rangeMinutes.getSelectedItem(), new BigDecimal(entryBuffer.getText()),
                (OrbConfig.StopMode) stopMode.getSelectedItem(), new BigDecimal(risk.getText()), new BigDecimal(target.getText()),
                Integer.parseInt(maxStocks.getText()), new BigDecimal(minPrice.getText()),
                maxPrice.getText().isBlank() ? null : new BigDecimal(maxPrice.getText()),
                new BigDecimal(minRelVolume.getText()), new BigDecimal(minRange.getText()), LocalTime.parse(latestEntry.getText()),
                parseSymbols(candidateSymbols.getText()), autoDiscover.isSelected(), runMode == OrbRunMode.SCHEDULE, mode);
    }

    public static List<String> parseSymbols(String text) {
        if (text == null || text.isBlank()) return List.of();
        return Arrays.stream(text.split("[\\s,;]+"))
                .map(String::trim).filter(s -> !s.isBlank()).map(String::toUpperCase).distinct().toList();
    }

    private JPanel build() {
        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        int row = 0;
        row = addField(fields, row, "Opening Range Duration", rangeMinutes,
                "How many minutes after 9:30 AM ET to capture for the ORB high/low before planning entries.");
        row = addField(fields, row, "Candidate Symbols (optional)", new JScrollPane(candidateSymbols),
                "Leave blank to auto-discover live Alpaca movers and most-active stocks. Manual symbols always win over discovery.");
        row = addField(fields, row, "Auto Discovery", autoDiscover,
                "Uses Alpaca live screeners only. If credentials or data are unavailable, NeuralArc shows an empty state instead of demo rows.");
        row = addField(fields, row, "Minimum Stock Price", minPrice, "Reject candidates below this latest screener price when Alpaca supplies it.");
        row = addField(fields, row, "Maximum Stock Price", maxPrice, "Optional upper price cap. Leave blank for no cap.");
        row = addField(fields, row, "Minimum Relative Volume", minRelVolume, "Prefer names trading above normal activity. AI context can add rationale but does not replace live-data filters.");
        row = addField(fields, row, "Minimum Range %", minRange, "Reject opening ranges that are too narrow to plan a practical breakout.");
        row = addField(fields, row, "Entry Buffer %", entryBuffer, "Adds a small buffer above the range high for the planned long breakout entry.");
        row = addField(fields, row, "Stop Mode", stopMode, "Range Low is the v1 default; other modes are persisted in config for future expansion.");
        row = addField(fields, row, "Risk %", risk, "Strategy-level risk metadata for the ORB setup.");
        row = addField(fields, row, "Take Profit %", target, "Planned reward target from the entry price.");
        row = addField(fields, row, "Max Stocks to Add", maxStocks, "Limit how many qualifying ORB recommendations are added to the grid.");
        row = addField(fields, row, "Latest Entry Time ET", latestEntry, "Morning cutoff for planned ORB entries. Use HH:mm, for example 11:00.");
        addField(fields, row, "Mode", new JLabel(mode.name()), "Uses the currently selected NeuralArc mode; Paper and Live ORB grids stay isolated.");

        JPanel wrapper = new JPanel(new BorderLayout(8, 8));
        wrapper.setBorder(new EmptyBorder(12, 12, 12, 12));
        wrapper.add(fields, BorderLayout.CENTER);
        wrapper.add(buttons(), BorderLayout.SOUTH);
        return wrapper;
    }

    private int addField(JPanel panel, int row, String labelText, JComponent component, String description) {
        JLabel label = new JLabel(labelText);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
        JLabel help = new JLabel("<html><div style='width:380px;color:#6f7785;'>" + description + "</div></html>");
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
        if (component instanceof JComboBox<?> comboBox) comboBox.setFont(compact);
        if (component instanceof JCheckBox checkBox) checkBox.setOpaque(false);
        if (component instanceof JScrollPane scrollPane) scrollPane.getViewport().getView().setFont(compact);
    }

    private GridBagConstraints constraints(int x, int y, int anchor, double weightx) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = x; constraints.gridy = y; constraints.anchor = anchor; constraints.weightx = weightx;
        constraints.insets = new Insets(3, 4, 3, 8);
        constraints.fill = x == 1 ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE;
        return constraints;
    }

    private JPanel buttons() {
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancel = new JButton("Cancel");
        JButton run = new JButton("Run ORB Engine  ▾");
        cancel.addActionListener(event -> dispose());
        JPopupMenu menu = new JPopupMenu();
        menu.add(runItem("Analyze now", OrbRunMode.ANALYZE_NOW, true));
        menu.add(runItem("Analyze & Arm now", OrbRunMode.ANALYZE_AND_ARM_NOW, true));
        JMenuItem schedule = runItem("Schedule open", OrbRunMode.SCHEDULE, false);
        schedule.setToolTipText("ORB scheduling is planned for a later phase; use Analyze now while NeuralArc is open.");
        menu.add(schedule);
        run.addActionListener(event -> menu.show(run, 0, run.getHeight()));
        buttons.add(cancel); buttons.add(run);
        return buttons;
    }

    private JMenuItem runItem(String label, OrbRunMode mode, boolean enabled) {
        JMenuItem item = new JMenuItem(label);
        item.setEnabled(enabled);
        item.addActionListener(event -> { accepted = true; runMode = mode; dispose(); });
        return item;
    }

    private void apply(OrbConfig c) {
        rangeMinutes.setSelectedItem(c.rangeDurationMinutes()); candidateSymbols.setText(String.join(", ", c.candidateSymbols()));
        minPrice.setText(c.minimumPrice().toPlainString()); maxPrice.setText(c.maximumPrice() == null ? "" : c.maximumPrice().toPlainString());
        minRelVolume.setText(c.minimumRelativeVolume().toPlainString()); minRange.setText(c.minimumRangePercent().toPlainString());
        entryBuffer.setText(c.entryBufferPercent().toPlainString()); stopMode.setSelectedItem(c.stopMode()); risk.setText(c.riskPercent().toPlainString());
        target.setText(c.takeProfitPercent().toPlainString()); maxStocks.setText(String.valueOf(c.maxStocksToAdd())); latestEntry.setText(c.latestEntryTimeEt().toString());
        autoDiscover.setSelected(c.autoDiscoverEnabled());
    }
}
