package com.neuralarc.ui;

import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyOrderSide;
import com.neuralarc.model.StrategyStage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrokerFillReconcileTableModelTest {
    private static BrokerFillReconciler.Proposal fix(String symbol, String delta) {
        return new BrokerFillReconciler.Proposal(BrokerFillReconciler.Kind.LINK, symbol + "-id", symbol, StrategyMode.LIVE,
                StrategyOrderSide.BUY, new BigDecimal(delta).abs(), new BigDecimal("10.50"), Instant.parse("2026-10-01T14:30:00Z"),
                "b-" + symbol, "", "filled", StrategyStage.MANUAL_BUY, null, new BigDecimal(delta), "No local record.");
    }

    private static BrokerFillReconciler.Proposal review(String symbol) {
        return new BrokerFillReconciler.Proposal(BrokerFillReconciler.Kind.REVIEW, symbol + "-id", symbol, StrategyMode.LIVE,
                null, BigDecimal.ZERO, BigDecimal.ZERO, null, null, null, "", null, null, BigDecimal.ZERO, "Bought elsewhere.");
    }

    private static BrokerFillReconcileTableModel model() {
        return new BrokerFillReconcileTableModel(List.of(fix("RDW", "4"), fix("ASTS", "2"), review("PN")));
    }

    @Test
    void fixesStartTickedAndReviewRowsNever() {
        BrokerFillReconcileTableModel model = model();

        assertEquals(2, model.selectedCount());
        assertEquals(2, model.fixableCount());
        assertEquals(1, model.reviewCount());
        assertFalse(model.isCellEditable(2, BrokerFillReconcileTableModel.INCLUDE));
    }

    @Test
    void aReviewRowCannotBeTickedEvenBySelectAll() {
        BrokerFillReconcileTableModel model = model();
        model.setValueAt(true, 2, BrokerFillReconcileTableModel.INCLUDE);
        model.selectAll();

        assertEquals(2, model.selectedCount());
    }

    @Test
    void untickingAFixLeavesItOutOfTheSelection() {
        BrokerFillReconcileTableModel model = model();

        model.setValueAt(false, 0, BrokerFillReconcileTableModel.INCLUDE);

        assertEquals(List.of("ASTS"), model.selected().stream().map(BrokerFillReconciler.Proposal::symbol).toList());
        assertEquals(0, new BigDecimal("2").compareTo(model.selectedShareDelta()));
    }

    @Test
    void cellsReadInTheOperatorsTerms() {
        BrokerFillReconcileTableModel model = model();

        assertEquals("Add missing order", model.getValueAt(0, 2));
        assertEquals("BUY", model.getValueAt(0, 3));
        assertEquals("$10.50", model.getValueAt(0, 5));
        assertEquals("+4 sh", model.getValueAt(0, 7));
        assertEquals("Needs review", model.getValueAt(2, 2));
        assertEquals("–", model.getValueAt(2, 7));
    }

    @Test
    void clearSelectionUntickEverything() {
        BrokerFillReconcileTableModel model = model();
        model.clearSelection();

        assertEquals(0, model.selectedCount());
        assertTrue(model.selected().isEmpty());
    }
}
