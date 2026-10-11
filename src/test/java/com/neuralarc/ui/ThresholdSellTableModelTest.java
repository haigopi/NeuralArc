package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static com.neuralarc.ui.AverageDownTestEntries.inWorkspace;
import static com.neuralarc.ui.AverageDownTestEntries.position;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThresholdSellTableModelTest {
    private static final ThresholdSellRule PROFIT_OVER_5 = new ThresholdSellRule(ThresholdSellRule.Side.PROFIT,
            ThresholdSellRule.Comparison.MORE_THAN, ThresholdSellRule.Unit.PERCENT, new BigDecimal("5"));
    private static final ThresholdSellRule PROFIT_OVER_0 = new ThresholdSellRule(ThresholdSellRule.Side.PROFIT,
            ThresholdSellRule.Comparison.MORE_THAN, ThresholdSellRule.Unit.PERCENT, BigDecimal.ZERO);

    @Test
    void listsOnlyTheMatchingPositionsAllTickedByDefault() {
        ThresholdSellTableModel model = model(PROFIT_OVER_5);

        assertEquals(2, model.getRowCount());
        assertEquals(2, model.selectedCount());
        assertEquals(3, model.totalCount());
        assertEquals(Set.of("BIG-id", "MID-id"), model.selectedIds());
    }

    @Test
    void untickingARowKeepsItAndNotifiesTheDialog() {
        ThresholdSellTableModel model = model(PROFIT_OVER_5);
        AtomicInteger changes = new AtomicInteger();
        model.setOnChange(changes::incrementAndGet);

        model.setValueAt(false, rowOf(model, "BIG"), ThresholdSellTableModel.INCLUDE);

        assertEquals(Set.of("MID-id"), model.selectedIds());
        assertEquals(1, changes.get());
    }

    @Test
    void aKeptPositionStaysKeptWhenTheRuleWidens() {
        ThresholdSellTableModel model = model(PROFIT_OVER_5);
        model.setValueAt(false, rowOf(model, "BIG"), ThresholdSellTableModel.INCLUDE);

        model.setRule(PROFIT_OVER_0);

        assertEquals(3, model.getRowCount());
        assertEquals(Set.of("MID-id", "SMALL-id"), model.selectedIds(), "BIG was kept, so widening the rule does not re-tick it");
    }

    @Test
    void aRowThatNoLongerMatchesIsNeitherListedNorSold() {
        ThresholdSellTableModel model = model(PROFIT_OVER_0);

        model.setRule(PROFIT_OVER_5);

        assertEquals(2, model.getRowCount());
        assertTrue(!model.selectedIds().contains("SMALL-id"));
    }

    @Test
    void selectAllAndClearActOnTheListedRows() {
        ThresholdSellTableModel model = model(PROFIT_OVER_5);

        model.clearSelection();
        assertEquals(0, model.selectedCount());
        model.selectAll();
        assertEquals(2, model.selectedCount());
    }

    @Test
    void totalsCoverOnlyTheTickedRows() {
        ThresholdSellTableModel model = model(PROFIT_OVER_5);
        // BIG: 10 sh, +$40, worth $140. MID: 10 sh, +$10, worth $110.
        assertEquals(0, new BigDecimal("250.00").compareTo(model.estimatedProceeds()));
        assertEquals(0, new BigDecimal("50.00").compareTo(model.estimatedPnl()));

        model.setValueAt(false, rowOf(model, "BIG"), ThresholdSellTableModel.INCLUDE);

        assertEquals(0, new BigDecimal("110.00").compareTo(model.estimatedProceeds()));
        assertEquals(0, new BigDecimal("10.00").compareTo(model.estimatedPnl()));
    }

    @Test
    void showsTheWorkspaceColumnOnlyWhenPositionsSpanMoreThanOne() {
        assertEquals(false, model(PROFIT_OVER_5).showsWorkspace());
        List<ThresholdSellCandidates.Candidate> mixed = ThresholdSellCandidates.collect(List.of(
                inWorkspace(position("A", 1, "10", "12"), "orb"),
                position("B", 1, "10", "12")), id -> id == null ? "Unassigned" : "ORB Engine");
        assertTrue(new ThresholdSellTableModel(mixed, PROFIT_OVER_5).showsWorkspace());
    }

    @Test
    void pnlCellShowsDollarsAndPercentWithASign() {
        ThresholdSellTableModel model = model(PROFIT_OVER_5);
        int pnl = model.findColumn("P&L");

        assertEquals("+$40.00 (+40.00%)", model.getValueAt(rowOf(model, "BIG"), pnl));
    }

    private static ThresholdSellTableModel model(ThresholdSellRule rule) {
        return new ThresholdSellTableModel(ThresholdSellCandidatesTest.sample(), rule);
    }

    private static int rowOf(ThresholdSellTableModel model, String symbol) {
        for (int row = 0; row < model.getRowCount(); row++) {
            if (model.candidateAt(row).symbol().equals(symbol)) {
                return row;
            }
        }
        throw new AssertionError("no row for " + symbol);
    }
}
