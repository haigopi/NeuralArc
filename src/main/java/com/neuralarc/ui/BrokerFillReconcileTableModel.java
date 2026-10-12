package com.neuralarc.ui;

import com.neuralarc.util.Monetary;

import javax.swing.table.AbstractTableModel;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Rows of the reconcile review: one per proposed fix, ticked by default, plus rows that only explain
 * why nothing can be fixed. Rows that cannot be applied are listed but can never be ticked.
 */
final class BrokerFillReconcileTableModel extends AbstractTableModel {
    static final int INCLUDE = 0;
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("MMM d HH:mm").withZone(ZoneId.systemDefault());
    private static final List<String> COLUMNS =
            List.of("", "Symbol", "Fix", "Side", "Shares", "Price", "When", "Effect", "Why");

    private final List<BrokerFillReconciler.Proposal> rows;
    private final Set<String> checked = new LinkedHashSet<>();
    private Runnable onChange = () -> { };

    BrokerFillReconcileTableModel(List<BrokerFillReconciler.Proposal> proposals) {
        this.rows = proposals == null ? List.of() : List.copyOf(proposals);
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).applicable()) {
                checked.add(key(i));
            }
        }
    }

    private static String key(int row) {
        return Integer.toString(row);
    }

    void setOnChange(Runnable onChange) {
        this.onChange = onChange == null ? () -> { } : onChange;
    }

    void selectAll() {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).applicable()) {
                checked.add(key(i));
            }
        }
        fireTableDataChanged();
        onChange.run();
    }

    void clearSelection() {
        checked.clear();
        fireTableDataChanged();
        onChange.run();
    }

    BrokerFillReconciler.Proposal proposalAt(int row) {
        return rows.get(row);
    }

    List<BrokerFillReconciler.Proposal> selected() {
        List<BrokerFillReconciler.Proposal> chosen = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            if (checked.contains(key(i))) {
                chosen.add(rows.get(i));
            }
        }
        return chosen;
    }

    int selectedCount() {
        return checked.size();
    }

    int fixableCount() {
        return (int) rows.stream().filter(BrokerFillReconciler.Proposal::applicable).count();
    }

    int reviewCount() {
        return rows.size() - fixableCount();
    }

    /** Net shares the ticked fixes add to the strategies' records (a sell counts negative). */
    BigDecimal selectedShareDelta() {
        return selected().stream()
                .map(BrokerFillReconciler.Proposal::shareDelta)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.size();
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS.get(column);
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return column == INCLUDE ? Boolean.class : String.class;
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return column == INCLUDE && rows.get(row).applicable();
    }

    @Override
    public void setValueAt(Object value, int row, int column) {
        if (!isCellEditable(row, column)) {
            return;
        }
        if (Boolean.TRUE.equals(value)) {
            checked.add(key(row));
        } else {
            checked.remove(key(row));
        }
        fireTableRowsUpdated(row, row);
        onChange.run();
    }

    @Override
    public Object getValueAt(int rowIndex, int column) {
        BrokerFillReconciler.Proposal row = rows.get(rowIndex);
        boolean fill = row.side() != null;
        return switch (column) {
            case 0 -> checked.contains(key(rowIndex));
            case 1 -> row.symbol();
            case 2 -> switch (row.kind()) {
                case LINK -> "Add missing order";
                case SYNC -> "Correct order";
                case REVIEW -> "Needs review";
            };
            case 3 -> fill ? row.side().name() : "–";
            case 4 -> fill ? row.filledQuantity().stripTrailingZeros().toPlainString() : "–";
            case 5 -> fill && row.averagePrice().signum() > 0 ? "$" + Monetary.round(row.averagePrice()).toPlainString() : "–";
            case 6 -> row.filledAt() == null ? "–" : WHEN.format(row.filledAt());
            case 7 -> row.applicable() ? signed(row.shareDelta()) + " sh" : "–";
            case 8 -> row.note();
            default -> "";
        };
    }

    private static String signed(BigDecimal value) {
        String text = value.stripTrailingZeros().toPlainString();
        return value.signum() > 0 ? "+" + text : text;
    }
}
