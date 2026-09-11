package com.neuralarc.ui;

import com.neuralarc.util.Monetary;

import javax.swing.table.AbstractTableModel;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Rows of the average-down review: one per losing position, with a checkbox to include it and the
 * order the current plan would submit for it. Locked rows are listed but can never be ticked.
 */
final class AverageDownTableModel extends AbstractTableModel {
    static final int INCLUDE = 0;

    private final List<AverageDownCandidates.Candidate> rows;
    private final boolean showWorkspace;
    private final List<String> columns = new ArrayList<>();
    private final Set<String> checked = new LinkedHashSet<>();
    private AverageLosingPositionsSelection plan;
    private Runnable onChange = () -> { };

    AverageDownTableModel(List<AverageDownCandidates.Candidate> rows, AverageLosingPositionsSelection plan) {
        this.rows = rows == null ? List.of() : List.copyOf(rows);
        this.plan = plan;
        this.showWorkspace = this.rows.stream()
                .map(AverageDownCandidates.Candidate::workspaceLabel)
                .distinct()
                .count() > 1;
        columns.add("");
        columns.add("Symbol");
        if (showWorkspace) {
            columns.add("Workspace");
        }
        columns.addAll(List.of("Shares", "Avg Entry", "Last", "P&L", "Buy Qty", "Buy At", "Note"));
        for (AverageDownCandidates.Candidate row : this.rows) {
            if (row.selectable()) {
                checked.add(row.strategyId());
            }
        }
    }

    void setOnChange(Runnable onChange) {
        this.onChange = onChange == null ? () -> { } : onChange;
    }

    /** Re-projects every row's order after the operator changes how or how much to buy. */
    void setPlan(AverageLosingPositionsSelection plan) {
        this.plan = plan;
        fireTableDataChanged();
        onChange.run();
    }

    void selectAll() {
        for (AverageDownCandidates.Candidate row : rows) {
            if (row.selectable()) {
                checked.add(row.strategyId());
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

    AverageDownCandidates.Candidate candidateAt(int row) {
        return rows.get(row);
    }

    Set<String> selectedIds() {
        return Set.copyOf(checked);
    }

    int selectedCount() {
        return checked.size();
    }

    int selectableCount() {
        return (int) rows.stream().filter(AverageDownCandidates.Candidate::selectable).count();
    }

    int lockedCount() {
        return rows.size() - selectableCount();
    }

    boolean showsWorkspace() {
        return showWorkspace;
    }

    /** Estimated cost of every ticked order under the current plan. */
    BigDecimal estimatedCost() {
        BigDecimal total = BigDecimal.ZERO;
        for (AverageDownCandidates.Candidate row : rows) {
            if (!checked.contains(row.strategyId())) {
                continue;
            }
            BigDecimal price = plan.estimatedPriceFor(row.position());
            total = total.add(price.multiply(BigDecimal.valueOf(plan.quantityFor(row.position()))));
        }
        return Monetary.round(total);
    }

    /** Ticked rows the current plan cannot price (a limit buy with no cached market price). */
    int unpricedSelectedCount() {
        if (plan.orderType() == AverageLosingPositionsSelection.OrderType.MARKET) {
            return 0;
        }
        return (int) rows.stream()
                .filter(row -> checked.contains(row.strategyId()))
                .filter(row -> plan.limitPriceFor(row.position()).signum() <= 0)
                .count();
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return columns.size();
    }

    @Override
    public String getColumnName(int column) {
        return columns.get(column);
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return column == INCLUDE ? Boolean.class : String.class;
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return column == INCLUDE && rows.get(row).selectable();
    }

    @Override
    public void setValueAt(Object value, int row, int column) {
        if (!isCellEditable(row, column)) {
            return;
        }
        String id = rows.get(row).strategyId();
        if (Boolean.TRUE.equals(value)) {
            checked.add(id);
        } else {
            checked.remove(id);
        }
        fireTableRowsUpdated(row, row);
        onChange.run();
    }

    @Override
    public Object getValueAt(int rowIndex, int column) {
        AverageDownCandidates.Candidate row = rows.get(rowIndex);
        String name = columns.get(column);
        return switch (name) {
            case "" -> checked.contains(row.strategyId());
            case "Symbol" -> row.symbol();
            case "Workspace" -> row.workspaceLabel();
            case "Shares" -> String.valueOf(row.shares());
            case "Avg Entry" -> money(row.averageCost());
            case "Last" -> money(row.lastPrice());
            case "P&L" -> signedMoney(row.unrealizedPnl()) + " (" + row.pnlPercent().toPlainString() + "%)";
            case "Buy Qty" -> row.selectable() ? String.valueOf(plan.quantityFor(row.position())) : "–";
            case "Buy At" -> row.selectable() ? buyAt(row) : "–";
            case "Note" -> row.lockReason();
            default -> "";
        };
    }

    private String buyAt(AverageDownCandidates.Candidate row) {
        if (plan.orderType() == AverageLosingPositionsSelection.OrderType.MARKET) {
            return "Market";
        }
        BigDecimal limit = plan.limitPriceFor(row.position());
        return limit.signum() > 0 ? money(limit) : "No price";
    }

    private static String money(BigDecimal value) {
        return "$" + Monetary.round(value).toPlainString();
    }

    private static String signedMoney(BigDecimal value) {
        BigDecimal rounded = Monetary.round(value);
        return (rounded.signum() < 0 ? "-$" : "$") + rounded.abs().toPlainString();
    }
}
