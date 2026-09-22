package com.neuralarc.ui;

import com.neuralarc.model.MarketBar;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyOrder;
import com.neuralarc.model.StrategyOrderSide;
import com.neuralarc.model.StrategyOrderStatus;
import com.neuralarc.model.StrategyOrderType;
import com.neuralarc.model.StrategyStage;
import com.neuralarc.model.StrategyStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static com.neuralarc.ui.AverageDownTestEntries.position;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryReentryTest {
    @Test
    void onlyTradedStocksThatNoWorkspaceTradesAnyMoreAreCandidates() {
        Strategy nioOld = strategy("nio-1", "NIO", StrategyStatus.COMPLETED, "2026-09-01T00:00:00Z");
        Strategy nioNew = strategy("nio-2", "NIO", StrategyStatus.ARCHIVED, "2026-09-10T00:00:00Z");
        Strategy aaplDone = strategy("aapl-1", "AAPL", StrategyStatus.COMPLETED, "2026-09-05T00:00:00Z");
        Strategy aaplLive = strategy("aapl-2", "AAPL", StrategyStatus.ACTIVE, "2026-09-12T00:00:00Z");
        Strategy neverTraded = strategy("dtss", "DTSS", StrategyStatus.ARCHIVED, "2026-09-12T00:00:00Z");
        Strategy pendingPlacement = strategy("msft-2", "MSFT", StrategyStatus.CREATED, "2026-09-12T00:00:00Z");
        Strategy msftDone = strategy("msft-1", "MSFT", StrategyStatus.COMPLETED, "2026-09-02T00:00:00Z");
        Map<String, List<StrategyOrder>> orders = Map.of(
                "nio-1", List.of(filled()), "nio-2", List.of(filled()), "aapl-1", List.of(filled()), "msft-1", List.of(filled()));

        List<Strategy> candidates = HistoryReentry.candidates(
                List.of(nioOld, nioNew, aaplDone, aaplLive, neverTraded, pendingPlacement, msftDone),
                StrategyMode.PAPER, id -> orders.getOrDefault(id, List.of()));

        assertEquals(List.of("nio-2"), candidates.stream().map(Strategy::id).toList(),
                "AAPL and MSFT are still live somewhere; DTSS never traded; NIO uses its latest plan");
    }

    @Test
    void theSafeLowIsTheLowestPriceTradedThisWeek() {
        LocalDate today = LocalDate.of(2026, 9, 22);
        List<MarketBar> bars = List.of(bar("2026-09-16", "10.00"), bar("2026-09-17", "9.40"), bar("2026-09-18", "9.80"),
                bar("2026-09-21", "9.60"), bar("2026-09-22", "9.90"));

        assertEquals(new BigDecimal("9.40"), HistoryReentry.safeLow(bars, today));
    }

    @Test
    void theReentryKeepsThePlanButRescalesItsPriceLevelsToTheNewEntry() {
        Strategy source = strategy("nio-2", "NIO", StrategyStatus.COMPLETED, "2026-09-10T00:00:00Z");
        source.setBaseBuyLimitPrice(new BigDecimal("10.00"));
        source.setAutomatedStopLossEnabled(true);
        source.setStopLossPrice(new BigDecimal("9.00"));   // -10%
        source.setTargetSellEnabled(true);
        source.setTargetSellPrice(new BigDecimal("12.00")); // +20%

        Strategy reentry = HistoryReentry.reentry(source, new BigDecimal("5.00"), "comeback-ws");

        assertEquals(new BigDecimal("5.00"), reentry.baseBuyLimitPrice());
        assertEquals(new BigDecimal("4.50"), reentry.stopLossPrice(), "still 10% below the entry, not a stale $9 stop");
        assertEquals(new BigDecimal("6.00"), reentry.targetSellPrice());
        assertEquals("comeback-ws", reentry.workspaceId());
        assertEquals(StrategyStatus.CREATED, reentry.status());
        assertTrue(reentry.name().startsWith(HistoryReentry.NAME_PREFIX));
    }

    @Test
    void theWorkspaceIsNamedForTheDay() {
        assertEquals("Comeback Picks · Sep 22", HistoryReentry.workspaceName(LocalDate.of(2026, 9, 22)));
    }

    private static Strategy strategy(String id, String symbol, StrategyStatus status, String updatedAt) {
        Strategy strategy = position(symbol, 0, "0", "10").strategy;
        Strategy copy = new Strategy(id, strategy.name(), symbol, StrategyMode.PAPER, status, StrategyLifecycleState.COMPLETED,
                new BigDecimal("10.00"), 1, BigDecimal.ZERO, 0, BigDecimal.ZERO, 0, false,
                strategy.stopLossType(), BigDecimal.ZERO, BigDecimal.ZERO, false, BigDecimal.ZERO, false,
                BigDecimal.ZERO, BigDecimal.ZERO, false, false, strategy.profitHoldType(), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, false, 1, new BigDecimal("10.00"), 30,
                Instant.parse("2026-09-01T00:00:00Z"), Instant.parse(updatedAt));
        return copy;
    }

    private static StrategyOrder filled() {
        return new StrategyOrder("o", "s", StrategyStage.BASE_BUY, "ord", "c", "X", StrategyOrderSide.BUY,
                StrategyOrderType.LIMIT, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.TEN,
                StrategyOrderStatus.FILLED, Instant.now(), Instant.now(), Instant.now(), "{}");
    }

    private static MarketBar bar(String day, String low) {
        return new MarketBar("NIO", day + "T04:00:00Z", new BigDecimal("10"), new BigDecimal("10.5"),
                new BigDecimal(low), new BigDecimal("10"), new BigDecimal("1000"));
    }
}
