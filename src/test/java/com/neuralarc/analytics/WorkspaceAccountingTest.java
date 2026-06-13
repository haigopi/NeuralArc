package com.neuralarc.analytics;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkspaceAccountingTest {
    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private WorkspaceAccounting.StrategyAccount account(String workspaceId, int shares, String unrealized, String realized, String marketValue) {
        return new WorkspaceAccounting.StrategyAccount(workspaceId, shares, bd(unrealized), bd(realized), bd(marketValue));
    }

    @Test
    void aggregatesOnlyTheTargetWorkspace() {
        List<WorkspaceAccounting.StrategyAccount> accounts = List.of(
                account("orb", 10, "100.00", "50.00", "1200.00"),   // same symbol, ORB
                account("vwap", 5, "40.00", "20.00", "620.00"),     // same symbol, VWAP
                account("orb", 0, "0.00", "30.00", "0.00")          // ORB, closed (no open shares)
        );
        WorkspaceAccounting.Snapshot orb = WorkspaceAccounting.forWorkspace("orb", accounts, List.of());

        assertEquals(bd("80.00"), orb.realized());          // 50 + 30
        assertEquals(bd("100.00"), orb.unrealized());       // only the open ORB account
        assertEquals(bd("180.00"), orb.total());
        assertEquals(bd("1200.00"), orb.capitalAllocated());
        assertEquals(1, orb.openPositions());               // one ORB account with open shares
    }

    @Test
    void allStocksAggregatesEveryWorkspace() {
        List<WorkspaceAccounting.StrategyAccount> accounts = List.of(
                account("orb", 10, "100.00", "50.00", "1200.00"),
                account("vwap", 5, "40.00", "20.00", "620.00"),
                account(null, 3, "10.00", "5.00", "300.00")        // unassigned
        );
        WorkspaceAccounting.Snapshot all = WorkspaceAccounting.forWorkspace(null, accounts, List.of());

        assertEquals(bd("75.00"), all.realized());          // 50 + 20 + 5
        assertEquals(bd("150.00"), all.unrealized());       // 100 + 40 + 10
        assertEquals(3, all.openPositions());
        assertEquals(bd("2120.00"), all.capitalAllocated());
    }

    @Test
    void winRateAndDailyRealizedComeFromSellTrades() {
        List<WorkspaceAccounting.RealizedSell> sells = List.of(
                new WorkspaceAccounting.RealizedSell("orb", bd("40.00"), true),   // win, today
                new WorkspaceAccounting.RealizedSell("orb", bd("-15.00"), true),  // loss, today
                new WorkspaceAccounting.RealizedSell("orb", bd("25.00"), false),  // win, earlier
                new WorkspaceAccounting.RealizedSell("vwap", bd("99.00"), true)   // different workspace
        );
        WorkspaceAccounting.Snapshot orb = WorkspaceAccounting.forWorkspace("orb", List.of(), sells);

        assertEquals(3, orb.closedTrades());                          // ORB sells only
        assertEquals(66.7, Math.round(orb.winRatePercent() * 10) / 10.0, 0.05); // 2 of 3 profitable
        assertEquals(bd("25.00"), orb.dailyRealized());              // 40 - 15 today
    }

    @Test
    void emptyWorkspaceProducesZeroes() {
        WorkspaceAccounting.Snapshot snapshot = WorkspaceAccounting.forWorkspace("empty", List.of(), List.of());
        assertEquals(bd("0.00"), snapshot.total());
        assertEquals(0, snapshot.openPositions());
        assertEquals(0, snapshot.closedTrades());
        assertEquals(0.0, snapshot.winRatePercent(), 0.001);
    }
}
