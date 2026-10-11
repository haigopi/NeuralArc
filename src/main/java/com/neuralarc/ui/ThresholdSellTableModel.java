package com.neuralarc.ui;

import com.neuralarc.util.Monetary;

import javax.swing.table.AbstractTableModel;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Rows of the threshold-sell review: the positions the current rule would sell, each with a
 * checkbox. Every row starts ticked; the operator unticks the ones to keep.
 *
 * <p>Unticking is remembered by position, not by row, so editing the rule afterwards re-filters the
 * list without quietly re-ticking a stock the operator already chose to keep.
 */
final class ThresholdSellTableModel extends AbstractTableModel {
    static final int INCLUDE = 0;

    private final List<ThresholdSellCandidates.Candidate> all;
    private final boolean showWorkspace;
    private final List<String> columns = new ArrayList<>();
    private final Set<String> excluded = new LinkedHashSet<>();
    private List<ThresholdSellCandidates.Candidate> rows = List.of();
    private Runnable onChange = () -> { };

    ThresholdSellTableModel(List<ThresholdSellCandidates.Candidate> candidates, ThresholdSellRule rule) {
        this.all = candidates == null ? List.of() : List.copyOf(candidates);
        this.showWorkspace = all.stream()
                .map(ThresholdSellCandidates.Candidate::workspaceLabel)
                .distinct()
                .count() > 1;
        columns.add("");
        columns.add("Symbol");
        if (showWorkspace) {
            columns.add("Workspace");
        }
        columns.addAll(List.of("Shares", "Avg Entry", "Last", "P&L", "Proceeds"));
        applyRule(rule);
    }

    void setOnChange(Runnable onChange) {
        this.onChange = onChange == null ? () -> { } : onChange;
    }

    /** Re-filters the list to what {@code rule} would sell. */
    void setRule(ThresholdSellRule rule) {
        applyRule(rule);
        fireTableDataChanged();
        onChange.run();
    }

    private void applyRule(ThresholdSellRule rule) {
        rows = all.stream().filter(candidate -> rule.matches(candidate.position())).toList();
    }

    void selectAll() {
        rows.forEach(row -> excluded.remove(row.strategyId()));
        fireTableDataChanged();
        onChange.run();
    }

    void clearSelection() {
        rows.forEach(row -> excluded.add(row.strategyId()));
        fireTableDataChanged();
        onChange.run();
    }

    ThresholdSellCandidates.Candidate candidateAt(int row) {
        return rows.get(row);
    }

    Set<String> selectedIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (ThresholdSellCandidates.Candidate row : rows) {
            if (isTicked(row)) {
                ids.add(row.strategyId());
            }
        }
        return Set.copyOf(ids);
    }

    int selectedCount() {
        return (int) rows.stream().filter(this::isTicked).count();
    }

    /** Positions the current rule matches, ticked or not. */
    int matchCount() {
        return rows.size();
    }

    /** Every sellable position, whether or not the rule matches it. */
    int totalCount() {
        return all.size();
    }

    boolean showsWorkspace() {
        return showWorkspace;
    }

    /** What the ticked positions are worth at their last prices. */
    BigDecimal estimatedProceeds() {
        return Monetary.round(rows.stream()
                .filter(this::isTicked)
                .map(ThresholdSellCandidates.Candidate::estimatedProceeds)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    /** The P&amp;L the ticked positions would lock in at their last prices. */
    BigDecimal estimatedPnl() {
        return Monetary.round(rows.stream()
                .filter(this::isTicked)
                .map(ThresholdSellCandidates.Candidate::unrealizedPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private boolean isTicked(ThresholdSellCandidates.Candidate row) {
        return !excluded.contains(row.strategyId());
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
        return column == INCLUDE;
    }

    @Override
    public void setValueAt(Object value, int row, int column) {
        if (column != INCLUDE) {
            return;
        }
        String id = rows.get(row).strategyId();
        if (Boolean.TRUE.equals(value)) {
            excluded.remove(id);
        } else {
            excluded.add(id);
        }
        fireTableRowsUpdated(row, row);
        onChange.run();
    }

    @Override
    public Object getValueAt(int rowIndex, int column) {
        ThresholdSellCandidates.Candidate row = rows.get(rowIndex);
        return switch (columns.get(column)) {
            case "" -> isTicked(row);
            case "Symbol" -> row.symbol();
            case "Workspace" -> row.workspaceLabel();
            case "Shares" -> String.valueOf(row.shares());
            case "Avg Entry" -> money(row.averageCost());
            case "Last" -> money(row.lastPrice());
            case "P&L" -> signedMoney(row.unrealizedPnl()) + " (" + signedPercent(row.pnlPercent()) + ")";
            case "Proceeds" -> money(row.estimatedProceeds());
            default -> "";
        };
    }

    private static String money(BigDecimal value) {
        return "$" + Monetary.round(value).toPlainString();
    }

    private static String signedMoney(BigDecimal value) {
        BigDecimal rounded = Monetary.round(value);
        return (rounded.signum() < 0 ? "-$" : "+$") + rounded.abs().toPlainString();
    }

    private static String signedPercent(BigDecimal value) {
        return (value.signum() < 0 ? "" : "+") + value.toPlainString() + "%";
    }
}
