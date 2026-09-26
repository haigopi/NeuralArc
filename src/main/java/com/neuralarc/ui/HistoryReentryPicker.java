package com.neuralarc.ui;

import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.util.FontLoader;
import com.neuralarc.util.Monetary;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.JTable;
import javax.swing.SwingWorker;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.JTableHeader;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Pick which inactive Trade History stocks to re-enter. Nothing is ticked to start with, so placing
 * dozens of live orders always takes a deliberate choice; the place button says exactly how many.
 *
 * <p>Each row shows the price the stock would actually go in at, beside the two-week range it has
 * been trading in, so the operator can see whether an entry is a genuine pullback or just today's
 * price. Those prices are read one stock at a time on a background thread and fill in as they
 * arrive: the dialog is usable immediately, and a stock whose prices have not loaded yet can still
 * be ticked — its entry price is then worked out at placement.
 */
final class HistoryReentryPicker extends JDialog {
    /** Reads one stock's levels; called off the EDT, once per stock. */
    interface LevelsLoader {
        HistoryReentry.Levels load(String symbol) throws Exception;
    }

    /** A ticked stock and the entry price the operator saw; a zero price means "work it out now". */
    record Pick(Strategy source, BigDecimal entryPrice) {
    }

    /** How the stock's own history ended, which is what the groups separate. */
    enum Outcome {
        GAIN("Closed in profit"),
        LOSS("Closed at a loss"),
        /** Nothing closed, or its orders were cleaned away: counting this as a gain was a lie. */
        UNKNOWN("No closed result");

        private final String label;

        Outcome(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    private final List<Strategy> sources;
    private final List<BigDecimal> realized = new ArrayList<>();
    private final boolean[] picked;
    private final Map<String, HistoryReentry.Levels> levels = new HashMap<>();
    /** Prices typed over the calculated one, by symbol; these are what gets placed. */
    private final Map<String, BigDecimal> overrides = new HashMap<>();
    private final List<Integer> visible = new ArrayList<>();
    private final JButton place = new JButton();
    private final JLabel status = new JLabel(" ");
    private final JToggleButton showGains = new JToggleButton();
    private final JToggleButton showLosses = new JToggleButton();
    private final PickModel model = new PickModel();
    private SwingWorker<Void, Object[]> loader;
    private List<Pick> result;

    HistoryReentryPicker(Component parent, List<Strategy> sources, StrategyMode mode, String workspaceName,
                         LevelsLoader levelsLoader, java.util.function.Function<Strategy, BigDecimal> realizedPnl) {
        super(parent == null ? null : javax.swing.SwingUtilities.getWindowAncestor(parent),
                "Re-enter Inactive Stocks", ModalityType.APPLICATION_MODAL);
        DialogCloseActions.bindEscapeToClose(this);
        java.util.function.Function<Strategy, BigDecimal> pnl =
                realizedPnl == null ? source -> BigDecimal.ZERO : realizedPnl;
        // Gains first, then losses: the two groups are what the operator is choosing between, and a
        // stock that lost money last time deserves a second look before it is ticked again.
        this.sources = sources.stream()
                .sorted(java.util.Comparator
                        .comparing((Strategy source) -> switch (outcomeOf(value(pnl.apply(source)))) {
                            case GAIN -> 0;
                            case LOSS -> 1;
                            case UNKNOWN -> 2;
                        })
                        .thenComparing(Strategy::symbol))
                .toList();
        this.sources.forEach(source -> realized.add(value(pnl.apply(source))));
        this.picked = new boolean[this.sources.size()];

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBorder(BorderFactory.createEmptyBorder(12, 14, 10, 14));
        JLabel intro = new JLabel(CompactFormLayout.wrapped("Stocks in " + (mode == StrategyMode.LIVE ? "Live" : "Paper")
                + " Trade History that no workspace is trading any more. Tick the ones to place again, into a new"
                + " workspace: " + workspaceName + ". A stock the broker still holds is skipped."
                + (mode == StrategyMode.LIVE ? " This places LIVE limit buy orders." : ""), 620));
        intro.setFont(FontLoader.ui(Font.PLAIN, 11f));
        JLabel method = new JLabel(richText("<b>Re-entry price</b> is editable — type over it to place at your own"
                + " number. Left alone it is the lowest price the stock has"
                + " actually traded over the last " + com.neuralarc.analytics.RecentLow.WEEK_SESSIONS + " sessions or"
                + " the last " + HistoryReentry.TWO_WEEK_SESSIONS + ", whichever is lower — a price the stock has"
                + " really printed, so the limit buy can fill, and never above what it traded at a few days ago. The rest of the old plan comes with it: stop loss, target and loss-buy levels"
                + " are rescaled by the same ratio as the entry (new entry ÷ old entry), so the plan keeps its"
                + " percentages instead of carrying stale prices. <b>2-Wk Avg Low</b> and <b>2-Wk Avg High</b> are the"
                + " mean of each session's low and high over the last " + HistoryReentry.TWO_WEEK_SESSIONS
                + " sessions: an entry well under the average low is a real dip, one close to the average high is not."
                + " <b>Past Result</b> is what this stock's own closed trades came to, and the two buttons below split"
                + " the list into the stocks that made money and the ones that lost it."));
        method.setFont(FontLoader.ui(Font.PLAIN, 10.5f));
        JPanel heading = new JPanel(new BorderLayout(0, 8));
        heading.add(intro, BorderLayout.NORTH);
        heading.add(method, BorderLayout.CENTER);
        content.add(heading, BorderLayout.NORTH);

        JTable table = new JTable(model);
        table.setRowHeight(22);
        table.getColumnModel().getColumn(0).setMaxWidth(60);
        table.getColumnModel().getColumn(1).setPreferredWidth(80);
        table.getColumnModel().getColumn(2).setPreferredWidth(130);
        table.getColumnModel().getColumn(3).setPreferredWidth(110);
        table.getColumnModel().getColumn(4).setPreferredWidth(110);
        table.getColumnModel().getColumn(5).setPreferredWidth(110);
        table.getColumnModel().getColumn(6).setPreferredWidth(110);
        table.getColumnModel().getColumn(7).setPreferredWidth(90);
        JTableHeader header = table.getTableHeader();
        header.setToolTipText(TooltipStyler.text("Re-entry price: the lowest price traded over the last "
                + com.neuralarc.analytics.RecentLow.WEEK_SESSIONS + " sessions. 2-week averages: the mean session low"
                + " and high over the last " + HistoryReentry.TWO_WEEK_SESSIONS + " sessions.", 360));
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(760, Math.min(440, 40 + sources.size() * 22)));
        content.add(scroll, BorderLayout.CENTER);

        DialogButtonStyles.apply(showGains);
        DialogButtonStyles.apply(showLosses);
        showGains.addActionListener(event -> refreshVisibleRows());
        showLosses.addActionListener(event -> refreshVisibleRows());
        showGains.setSelected(true);
        showLosses.setSelected(true);
        showGains.setToolTipText(TooltipStyler.text("Show the stocks whose own trade history closed in profit.", 300));
        showLosses.setToolTipText(TooltipStyler.text("Show the stocks whose own trade history closed at a loss.", 300));
        JButton all = new JButton("Select All Shown");
        DialogButtonStyles.apply(all, "icons/apply.svg");
        all.setToolTipText(TooltipStyler.text("Ticks every stock currently listed, which is one group when the other"
                + " is hidden.", 320));
        all.addActionListener(event -> setAll(true));
        JButton none = new JButton("Select None");
        DialogButtonStyles.apply(none, "icons/close.svg");
        none.addActionListener(event -> setAll(false));
        JButton cancel = new JButton("Cancel");
        DialogButtonStyles.apply(cancel, "icons/close.svg");
        cancel.addActionListener(event -> dispose());
        DialogButtonStyles.apply(place, "icons/submit.svg");
        place.addActionListener(event -> {
            result = selected();
            dispose();
        });
        status.setFont(FontLoader.ui(Font.PLAIN, 10.5f));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.add(showGains);
        left.add(showLosses);
        left.add(all);
        left.add(none);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.add(cancel);
        right.add(place);
        JPanel buttons = new JPanel(new BorderLayout());
        buttons.add(left, BorderLayout.WEST);
        buttons.add(right, BorderLayout.EAST);
        JPanel south = new JPanel(new BorderLayout(0, 4));
        south.add(status, BorderLayout.NORTH);
        south.add(buttons, BorderLayout.CENTER);
        content.add(south, BorderLayout.SOUTH);
        setContentPane(content);
        refreshVisibleRows();
        refreshPlaceButton();
        startLoading(levelsLoader);
        pack();
        setLocationRelativeTo(parent);
    }

    /** Wrapped HTML that keeps its own markup, for text with emphasis the escaping wrapper would show. */
    private static String richText(String html) {
        return "<html><body style='width:" + CompactFormLayout.cssWidth(620) + "px'>" + html + "</body></html>";
    }

    /** The ticked stocks with the prices shown, or empty when cancelled. */
    Optional<List<Pick>> showDialog() {
        setVisible(true);
        return Optional.ofNullable(result);
    }

    List<Pick> selected() {
        List<Pick> chosen = new ArrayList<>();
        for (int i = 0; i < sources.size(); i++) {
            if (picked[i]) {
                chosen.add(new Pick(sources.get(i), entryPriceFor(sources.get(i))));
            }
        }
        return chosen;
    }

    void setPicked(int row, boolean value) {
        picked[row] = value;
        refreshPlaceButton();
    }

    JButton placeButton() {
        return place;
    }

    String statusText() {
        return status.getText();
    }

    /** Applies one stock's loaded levels, as the background loader does. */
    void applyLevels(String symbol, HistoryReentry.Levels loaded) {
        levels.put(symbol, loaded == null ? HistoryReentry.Levels.unknown() : loaded);
        for (int row = 0; row < visible.size(); row++) {
            if (sources.get(visible.get(row)).symbol().equals(symbol)) {
                model.fireTableRowsUpdated(row, row);
            }
        }
        refreshStatus();
    }

    Object valueAt(int row, int column) {
        return model.getValueAt(row, column);
    }

    String columnName(int column) {
        return model.getColumnName(column);
    }

    int columnCount() {
        return model.getColumnCount();
    }

    @Override
    public void dispose() {
        if (loader != null) {
            loader.cancel(true);
        }
        super.dispose();
    }

    private BigDecimal entryPriceFor(Strategy source) {
        BigDecimal typed = overrides.get(source.symbol());
        if (typed != null && typed.signum() > 0) {
            return typed;
        }
        HistoryReentry.Levels loaded = levels.get(source.symbol());
        return loaded == null || !loaded.known() ? BigDecimal.ZERO : loaded.entryPrice();
    }

    /** Sets a typed-in re-entry price; a blank or unreadable entry restores the calculated one. */
    void setEntryPriceOverride(String symbol, String typed) {
        String cleaned = typed == null ? "" : typed.replace("$", "").replace(",", "").trim();
        if (cleaned.isEmpty()) {
            overrides.remove(symbol);
            return;
        }
        try {
            BigDecimal price = new BigDecimal(cleaned);
            if (price.signum() > 0) {
                overrides.put(symbol, Monetary.round(price));
            }
        } catch (NumberFormatException ignored) {
            // Leave the calculated price in place rather than placing an order at a price nobody meant.
        }
    }

    boolean hasOverride(String symbol) {
        return overrides.containsKey(symbol);
    }

    /**
     * Reads the levels one stock at a time on a background thread. Sequential on purpose: this runs
     * against the same rate limit as everything else, and the rows fill in as they arrive, so nothing
     * is gained by asking for dozens at once.
     */
    private void startLoading(LevelsLoader levelsLoader) {
        if (levelsLoader == null || sources.isEmpty()) {
            status.setText(" ");
            return;
        }
        refreshStatus();
        loader = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                for (Strategy source : sources) {
                    if (isCancelled()) {
                        return null;
                    }
                    HistoryReentry.Levels loaded;
                    try {
                        loaded = levelsLoader.load(source.symbol());
                    } catch (Exception ex) {
                        loaded = HistoryReentry.Levels.unknown();
                    }
                    publish(new Object[]{source.symbol(), loaded});
                }
                return null;
            }

            @Override
            protected void process(List<Object[]> loaded) {
                for (Object[] row : loaded) {
                    applyLevels((String) row[0], (HistoryReentry.Levels) row[1]);
                }
            }
        };
        loader.execute();
    }

    private void refreshStatus() {
        int known = 0;
        for (Strategy source : sources) {
            HistoryReentry.Levels loaded = levels.get(source.symbol());
            if (loaded != null && loaded.known()) {
                known++;
            }
        }
        status.setText(known == sources.size()
                ? "Prices loaded for all " + sources.size() + " stocks."
                : "Loading prices… " + known + " of " + sources.size() + ".");
    }

    /** Ticks or clears only the rows currently listed, so a hidden group is never placed by accident. */
    private void setAll(boolean value) {
        for (int index : visible) {
            picked[index] = value;
        }
        model.fireTableDataChanged();
        refreshPlaceButton();
    }

    /** Which group a stock belongs to: anything that did not end down is treated as a gain. */
    static Outcome outcomeOf(BigDecimal realizedPnl) {
        if (realizedPnl == null || realizedPnl.signum() == 0) {
            return Outcome.UNKNOWN;
        }
        return realizedPnl.signum() < 0 ? Outcome.LOSS : Outcome.GAIN;
    }

    Outcome outcomeAt(int row) {
        return outcomeOf(realized.get(row));
    }

    int shownRowCount() {
        return visible.size();
    }

    javax.swing.JToggleButton gainsToggle() {
        return showGains;
    }

    javax.swing.JToggleButton lossesToggle() {
        return showLosses;
    }

    /**
     * Rebuilds the listed rows from the two group toggles. A hidden row keeps whatever tick it had:
     * hiding a group is a way to work through one at a time, not a way to clear a choice.
     */
    private void refreshVisibleRows() {
        visible.clear();
        int gains = 0;
        int losses = 0;
        int unknown = 0;
        for (int index = 0; index < sources.size(); index++) {
            Outcome outcome = outcomeAt(index);
            boolean shown = switch (outcome) {
                case GAIN -> {
                    gains++;
                    yield showGains.isSelected();
                }
                case LOSS -> {
                    losses++;
                    yield showLosses.isSelected();
                }
                // A stock with no closed result belongs to neither group, so it is listed whenever
                // either is: hiding it behind both filters would lose it entirely.
                case UNKNOWN -> {
                    unknown++;
                    yield showGains.isSelected() || showLosses.isSelected();
                }
            };
            if (shown) {
                visible.add(index);
            }
        }
        showGains.setText(Outcome.GAIN.label() + " (" + gains + ")");
        showLosses.setText(Outcome.LOSS.label() + " (" + losses + ")"
                + (unknown > 0 ? " · " + unknown + " with no closed result" : ""));
        model.fireTableDataChanged();
        refreshPlaceButton();
    }

    private static BigDecimal value(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private void refreshPlaceButton() {
        int count = selected().size();
        place.setText("Place " + count + " Selected");
        place.setEnabled(count > 0);
    }

    private final class PickModel extends AbstractTableModel {
        private final String[] columns = {"Place", "Symbol", "Past Result", "Re-entry Price", "2-Wk Avg Low",
                "2-Wk Avg High", "Last Entry Price", "Last Shares"};

        @Override public int getRowCount() { return visible.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int column) { return columns[column]; }
        @Override public Class<?> getColumnClass(int column) {
            return column == 0 ? Boolean.class : column == 7 ? Integer.class : String.class;
        }
        @Override public boolean isCellEditable(int row, int column) { return column == 0 || column == 3; }

        @Override
        public Object getValueAt(int row, int column) {
            int index = visible.get(row);
            Strategy source = sources.get(index);
            HistoryReentry.Levels loaded = levels.get(source.symbol());
            return switch (column) {
                case 0 -> picked[index];
                case 1 -> source.symbol();
                case 2 -> pastResult(index);
                case 3 -> {
                    BigDecimal typed = overrides.get(source.symbol());
                    yield typed != null ? "$" + typed.toPlainString() : money(loaded == null ? null : loaded.entryPrice());
                }
                case 4 -> money(loaded == null ? null : loaded.averageLow());
                case 5 -> money(loaded == null ? null : loaded.averageHigh());
                case 6 -> "$" + Monetary.round(source.baseBuyLimitPrice()).toPlainString();
                default -> source.baseBuyQuantity();
            };
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            int index = visible.get(row);
            if (column == 0 && value instanceof Boolean tick) {
                setPicked(index, tick);
                fireTableRowsUpdated(row, row);
                return;
            }
            if (column == 3) {
                setEntryPriceOverride(sources.get(index).symbol(), value == null ? "" : value.toString());
                fireTableRowsUpdated(row, row);
            }
        }

        /** "+$182.40 gain" / "-$96.10 loss" — the realized result of this stock's own past trades. */
        private String pastResult(int index) {
            BigDecimal pnl = realized.get(index);
            if (outcomeOf(pnl) == Outcome.UNKNOWN) {
                return "no closed result";
            }
            String amount = Monetary.round(pnl.abs()).toPlainString();
            return (pnl.signum() < 0 ? "-$" + amount + " loss" : "+$" + amount + " gain");
        }

        /** "…" while a price is still loading, "—" when the stock has no recent prices at all. */
        private String money(BigDecimal value) {
            if (value == null) {
                return "…";
            }
            return value.signum() <= 0 ? "—" : "$" + Monetary.round(value).toPlainString();
        }
    }
}
