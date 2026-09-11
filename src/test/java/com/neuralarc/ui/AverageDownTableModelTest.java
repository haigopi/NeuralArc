package com.neuralarc.ui;

import com.neuralarc.model.TimeInForce;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static com.neuralarc.ui.AverageDownTestEntries.inWorkspace;
import static com.neuralarc.ui.AverageDownTestEntries.position;
import static com.neuralarc.ui.AverageDownTestEntries.withWorkingSell;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AverageDownTableModelTest {
    @Test
    void startsWithEverySelectablePositionTickedAndLockedOnesLeftOut() {
        AverageDownTableModel model = model(limitPlan());

        assertEquals(2, model.selectedCount());
        assertEquals(2, model.selectableCount());
        assertEquals(1, model.lockedCount());
        int locked = rowOf(model, "RKTO");
        assertEquals(Boolean.FALSE, model.getValueAt(locked, AverageDownTableModel.INCLUDE));
        assertFalse(model.isCellEditable(locked, AverageDownTableModel.INCLUDE));
    }

    @Test
    void untickingARowExcludesItAndNotifiesTheDialog() {
        AverageDownTableModel model = model(limitPlan());
        AtomicInteger changes = new AtomicInteger();
        model.setOnChange(changes::incrementAndGet);

        model.setValueAt(false, rowOf(model, "KLC"), AverageDownTableModel.INCLUDE);

        assertEquals(Set.of("CURI-id"), model.selectedIds());
        assertEquals(1, changes.get());
    }

    @Test
    void aLockedRowCanNeverBeTicked() {
        AverageDownTableModel model = model(limitPlan());

        model.setValueAt(true, rowOf(model, "RKTO"), AverageDownTableModel.INCLUDE);
        model.selectAll();

        assertFalse(model.selectedIds().contains("RKTO-id"));
    }

    @Test
    void selectAllAndClearChangeTheWholeList() {
        AverageDownTableModel model = model(limitPlan());

        model.clearSelection();
        assertEquals(0, model.selectedCount());
        model.selectAll();
        assertEquals(2, model.selectedCount());
    }

    @Test
    void previewsTheOrderEachRowWillSubmit() {
        AverageDownTableModel model = model(limitPlan());
        int klc = rowOf(model, "KLC");

        assertEquals("4", model.getValueAt(klc, column(model, "Buy Qty")));
        assertEquals("$2.55", model.getValueAt(klc, column(model, "Buy At")), "2.58 less a 1% pullback");
        assertEquals("–", model.getValueAt(rowOf(model, "RKTO"), column(model, "Buy At")),
                "a locked row previews no order");

        model.setPlan(new AverageLosingPositionsSelection(
                AverageLosingPositionsSelection.OrderType.MARKET,
                AverageLosingPositionsSelection.QuantityMode.FIXED_INPUT_QUANTITY, 2, BigDecimal.ZERO));

        assertEquals("2", model.getValueAt(klc, column(model, "Buy Qty")));
        assertEquals("Market", model.getValueAt(klc, column(model, "Buy At")));
    }

    @Test
    void estimatesTheCostOfTheTickedOrders() {
        AverageDownTableModel model = model(limitPlan());
        // KLC: 4 x 2.55 = 10.20; CURI: 2 x 2.63 = 5.26 (2.66 less 1%, rounded).
        assertEquals(0, new BigDecimal("15.46").compareTo(model.estimatedCost()));

        model.setValueAt(false, rowOf(model, "CURI"), AverageDownTableModel.INCLUDE);
        assertEquals(0, new BigDecimal("10.20").compareTo(model.estimatedCost()));
    }

    @Test
    void showsTheWorkspaceColumnOnlyWhenPositionsSpanWorkspaces() {
        assertFalse(model(limitPlan()).showsWorkspace());

        AverageDownTableModel spanning = new AverageDownTableModel(AverageDownCandidates.collect(List.of(
                inWorkspace(position("KLC", 4, "2.66", "2.58"), "orb"),
                position("CURI", 2, "3.53", "2.66")
        ), entry -> true, id -> id == null ? "Unassigned" : "ORB Engine"), limitPlan());

        assertTrue(spanning.showsWorkspace());
        assertEquals("Workspace", spanning.getColumnName(2));
    }

    @Test
    void countsTickedLimitBuysThatHaveNoMarketPrice() {
        AverageDownTableModel model = new AverageDownTableModel(AverageDownCandidates.collect(List.of(
                position("KLC", 4, "2.66", "2.58")
        ), entry -> true, id -> ""), limitPlan());
        assertEquals(0, model.unpricedSelectedCount());
    }

    private static AverageDownTableModel model(AverageLosingPositionsSelection plan) {
        return new AverageDownTableModel(AverageDownCandidates.collect(List.of(
                position("KLC", 4, "2.66", "2.58"),
                withWorkingSell(position("RKTO", 1, "0.85", "0.69")),
                position("CURI", 2, "3.53", "2.66")
        ), PortfolioActionsSupport.BulkAction.AVERAGE_LOSING_POSITIONS::matches, id -> ""), plan);
    }

    private static AverageLosingPositionsSelection limitPlan() {
        return new AverageLosingPositionsSelection(
                AverageLosingPositionsSelection.OrderType.LIMIT_BELOW_MARKET,
                AverageLosingPositionsSelection.QuantityMode.CURRENT_POSITION_QUANTITY,
                1, BigDecimal.ONE, TimeInForce.DAY);
    }

    private static int rowOf(AverageDownTableModel model, String symbol) {
        for (int row = 0; row < model.getRowCount(); row++) {
            if (symbol.equals(model.candidateAt(row).symbol())) {
                return row;
            }
        }
        throw new AssertionError("no row for " + symbol);
    }

    private static int column(AverageDownTableModel model, String name) {
        for (int column = 0; column < model.getColumnCount(); column++) {
            if (name.equals(model.getColumnName(column))) {
                return column;
            }
        }
        throw new AssertionError("no column " + name);
    }
}
