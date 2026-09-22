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
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Pick which inactive Trade History stocks to re-enter. Nothing is ticked to start with, so placing
 * dozens of live orders always takes a deliberate choice; the place button says exactly how many.
 */
final class HistoryReentryPicker extends JDialog {
    private final List<Strategy> sources;
    private final boolean[] picked;
    private final JButton place = new JButton();
    private List<Strategy> result;

    HistoryReentryPicker(Component parent, List<Strategy> sources, StrategyMode mode, String workspaceName) {
        super(parent == null ? null : javax.swing.SwingUtilities.getWindowAncestor(parent),
                "Re-enter Inactive Stocks", ModalityType.APPLICATION_MODAL);
        DialogCloseActions.bindEscapeToClose(this);
        this.sources = List.copyOf(sources);
        this.picked = new boolean[sources.size()];

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBorder(BorderFactory.createEmptyBorder(12, 14, 10, 14));
        JLabel intro = new JLabel(CompactFormLayout.wrapped("Stocks in " + (mode == StrategyMode.LIVE ? "Live" : "Paper")
                + " Trade History that no workspace is trading any more. Tick the ones to place again. Each goes in at"
                + " its safe low (the lowest price it traded this week), reusing its last plan with the stop, target"
                + " and loss levels rescaled to the new entry, into a new workspace: " + workspaceName + ". A stock the"
                + " broker still holds is skipped." + (mode == StrategyMode.LIVE ? " This places LIVE limit buy orders." : ""), 520));
        intro.setFont(FontLoader.ui(Font.PLAIN, 11f));
        content.add(intro, BorderLayout.NORTH);

        PickModel model = new PickModel();
        JTable table = new JTable(model);
        table.setRowHeight(22);
        table.getColumnModel().getColumn(0).setMaxWidth(60);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(560, Math.min(440, 40 + sources.size() * 22)));
        content.add(scroll, BorderLayout.CENTER);

        JButton all = new JButton("Select All");
        all.addActionListener(event -> setAll(true, model));
        JButton none = new JButton("Select None");
        none.addActionListener(event -> setAll(false, model));
        JButton cancel = new JButton("Cancel");
        DialogButtonStyles.apply(cancel, "icons/close.svg");
        cancel.addActionListener(event -> dispose());
        DialogButtonStyles.apply(place, "icons/submit.svg");
        place.addActionListener(event -> {
            result = selected();
            dispose();
        });
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.add(all);
        left.add(none);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.add(cancel);
        right.add(place);
        JPanel south = new JPanel(new BorderLayout());
        south.add(left, BorderLayout.WEST);
        south.add(right, BorderLayout.EAST);
        content.add(south, BorderLayout.SOUTH);
        setContentPane(content);
        refreshPlaceButton();
        pack();
        setLocationRelativeTo(parent);
    }

    /** The ticked stocks, or empty when cancelled. */
    Optional<List<Strategy>> showDialog() {
        setVisible(true);
        return Optional.ofNullable(result);
    }

    List<Strategy> selected() {
        List<Strategy> chosen = new ArrayList<>();
        for (int i = 0; i < sources.size(); i++) {
            if (picked[i]) {
                chosen.add(sources.get(i));
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

    private void setAll(boolean value, PickModel model) {
        java.util.Arrays.fill(picked, value);
        model.fireTableDataChanged();
        refreshPlaceButton();
    }

    private void refreshPlaceButton() {
        int count = selected().size();
        place.setText("Place " + count + " Selected");
        place.setEnabled(count > 0);
    }

    private final class PickModel extends AbstractTableModel {
        private final String[] columns = {"Place", "Symbol", "Last Entry Price", "Last Shares"};

        @Override public int getRowCount() { return sources.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int column) { return columns[column]; }
        @Override public Class<?> getColumnClass(int column) {
            return column == 0 ? Boolean.class : column == 3 ? Integer.class : String.class;
        }
        @Override public boolean isCellEditable(int row, int column) { return column == 0; }

        @Override
        public Object getValueAt(int row, int column) {
            Strategy source = sources.get(row);
            return switch (column) {
                case 0 -> picked[row];
                case 1 -> source.symbol();
                case 2 -> "$" + Monetary.round(source.baseBuyLimitPrice()).toPlainString();
                default -> source.baseBuyQuantity();
            };
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            if (column == 0 && value instanceof Boolean tick) {
                setPicked(row, tick);
                fireTableRowsUpdated(row, row);
            }
        }
    }
}
