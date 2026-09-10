package com.neuralarc.ui;

import com.neuralarc.analytics.WorkspaceAccounting;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceAccountingInputsTest {
    @Test
    void bankedProfitSurvivesAfterASoldPositionLeavesTheGrid() {
        WorkspaceAccountingInputs.Result result = WorkspaceAccountingInputs.build(List.of(
                open("orb", 10, "-34.43", "208.58", "243.01", List.of()),
                closed("orb", List.of(sell("orb", "128.40", true)))
        ));
        WorkspaceAccounting.Snapshot snapshot =
                WorkspaceAccounting.forWorkspace("orb", result.accounts(), result.sells());

        assertEquals(new BigDecimal("128.40"), snapshot.realized());
        assertEquals(new BigDecimal("128.40"), snapshot.dailyRealized());
        assertEquals(new BigDecimal("-34.43"), snapshot.unrealized());
    }

    @Test
    void aClosedStrategyContributesRealizedOnlyAndNoOpenExposure() {
        WorkspaceAccountingInputs.Result result = WorkspaceAccountingInputs.build(List.of(
                closed("orb", List.of(sell("orb", "50.00", true), sell("orb", "-10.00", false)))
        ));
        WorkspaceAccounting.Snapshot snapshot =
                WorkspaceAccounting.forWorkspace("orb", result.accounts(), result.sells());

        assertEquals(new BigDecimal("40.00"), snapshot.realized());
        assertEquals(new BigDecimal("50.00"), snapshot.dailyRealized(), "only today's sell counts");
        assertEquals(new BigDecimal("0.00"), snapshot.unrealized());
        assertEquals(new BigDecimal("0.00"), snapshot.capitalAllocated(),
                "a closed strategy holds no capital");
        assertEquals(0, snapshot.openPositions());
    }

    @Test
    void closedStrategiesWithNoRealizedHistoryAreDroppedEntirely() {
        WorkspaceAccountingInputs.Result result = WorkspaceAccountingInputs.build(List.of(
                closed("orb", List.of()),
                closed("orb", null)
        ));

        assertTrue(result.accounts().isEmpty());
        assertTrue(result.sells().isEmpty());
    }

    @Test
    void openStrategiesKeepTheirSharesCapitalAndMarketValue() {
        WorkspaceAccountingInputs.Result result = WorkspaceAccountingInputs.build(List.of(
                open("orb", 10, "100.00", "1200.00", "1500.00", List.of(sell("orb", "50.00", true)))
        ));
        WorkspaceAccounting.Snapshot snapshot =
                WorkspaceAccounting.forWorkspace("orb", result.accounts(), result.sells());

        assertEquals(new BigDecimal("100.00"), snapshot.unrealized());
        assertEquals(new BigDecimal("50.00"), snapshot.realized());
        assertEquals(new BigDecimal("1500.00"), snapshot.capitalAllocated());
        assertEquals(1, snapshot.openPositions());
    }

    @Test
    void realizedIsSummedPerStrategyFromItsOwnSells() {
        WorkspaceAccountingInputs.Result result = WorkspaceAccountingInputs.build(List.of(
                closed("orb", List.of(sell("orb", "10.00", true), sell("orb", "15.50", true)))
        ));

        assertEquals(1, result.accounts().size());
        assertEquals(new BigDecimal("25.50"), result.accounts().getFirst().realizedPnl());
        assertEquals(2, result.sells().size());
    }

    @Test
    void handlesNullInput() {
        WorkspaceAccountingInputs.Result result = WorkspaceAccountingInputs.build(null);

        assertTrue(result.accounts().isEmpty());
        assertTrue(result.sells().isEmpty());
    }

    private static WorkspaceAccountingInputs.StrategyInput open(
            String workspaceId, int shares, String unrealized, String marketValue, String capital,
            List<WorkspaceAccounting.RealizedSell> sells) {
        return new WorkspaceAccountingInputs.StrategyInput(
                workspaceId, true, sells, shares,
                new BigDecimal(unrealized), new BigDecimal(marketValue), new BigDecimal(capital));
    }

    private static WorkspaceAccountingInputs.StrategyInput closed(
            String workspaceId, List<WorkspaceAccounting.RealizedSell> sells) {
        // Values a caller might still pass for a closed row; the builder must ignore all of them.
        return new WorkspaceAccountingInputs.StrategyInput(
                workspaceId, false, sells, 7,
                new BigDecimal("99.00"), new BigDecimal("99.00"), new BigDecimal("99.00"));
    }

    private static WorkspaceAccounting.RealizedSell sell(String workspaceId, String pnl, boolean today) {
        return new WorkspaceAccounting.RealizedSell(workspaceId, new BigDecimal(pnl), today);
    }
}
