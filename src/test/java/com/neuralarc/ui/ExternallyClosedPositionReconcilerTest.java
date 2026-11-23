package com.neuralarc.ui;

import com.neuralarc.model.ProfitHoldType;
import com.neuralarc.model.StopLossType;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternallyClosedPositionReconcilerTest {
    private static final Instant NOW = Instant.parse("2026-11-22T18:00:00Z");
    private static final Instant LONG_AGO = NOW.minus(3, ChronoUnit.DAYS);

    @Test
    void completesAnActiveRowWhoseSharesLeftTheBroker() {
        // The SSTI case: bought, the sell never filled, and the broker no longer holds the symbol.
        Strategy strategy = active(StrategyLifecycleState.BASE_BUY_FILLED);
        List<StrategyOrder> orders = List.of(buy(strategy, StrategyOrderStatus.FILLED, "10", LONG_AGO));

        boolean changed = ExternallyClosedPositionReconciler.reconcile(strategy, orders, Set.of(), Set.of(), NOW);

        assertTrue(changed);
        assertEquals(StrategyStatus.COMPLETED, strategy.status());
        assertEquals(StrategyLifecycleState.COMPLETED, strategy.currentState());
        assertTrue(strategy.lastEvent().contains("sold outside this strategy"));
    }

    @Test
    void leavesTheRowAloneWhileTheBrokerStillHoldsTheSymbol() {
        Strategy strategy = active(StrategyLifecycleState.BASE_BUY_FILLED);
        List<StrategyOrder> orders = List.of(buy(strategy, StrategyOrderStatus.FILLED, "10", LONG_AGO));

        assertFalse(ExternallyClosedPositionReconciler.reconcile(strategy, orders, Set.of("AAPL"), Set.of(), NOW));
        assertEquals(StrategyStatus.ACTIVE, strategy.status());
    }

    @Test
    void leavesTheRowAloneWhileTheBrokerHasAnOpenOrderInTheSymbol() {
        Strategy strategy = active(StrategyLifecycleState.STOP_LOSS_ACTIVE);
        List<StrategyOrder> orders = List.of(buy(strategy, StrategyOrderStatus.FILLED, "10", LONG_AGO));

        assertFalse(ExternallyClosedPositionReconciler.reconcile(strategy, orders, Set.of(), Set.of("AAPL"), NOW));
        assertEquals(StrategyStatus.ACTIVE, strategy.status());
    }

    @Test
    void leavesTheRowAloneWhenItsOwnOrderIsStillWorking() {
        Strategy strategy = active(StrategyLifecycleState.BASE_BUY_FILLED);
        List<StrategyOrder> orders = List.of(
                buy(strategy, StrategyOrderStatus.FILLED, "10", LONG_AGO),
                sell(strategy, StrategyOrderStatus.SUBMITTED, "0", LONG_AGO));

        assertFalse(ExternallyClosedPositionReconciler.reconcile(strategy, orders, Set.of(), Set.of(), NOW));
    }

    @Test
    void leavesTheRowAloneWhenItsOwnSellsAlreadyAccountForEveryShare() {
        Strategy strategy = active(StrategyLifecycleState.BASE_BUY_FILLED);
        List<StrategyOrder> orders = List.of(
                buy(strategy, StrategyOrderStatus.FILLED, "10", LONG_AGO),
                sell(strategy, StrategyOrderStatus.FILLED, "10", LONG_AGO));

        assertFalse(ExternallyClosedPositionReconciler.reconcile(strategy, orders, Set.of(), Set.of(), NOW),
                "the engine completes this row from its own filled exit");
    }

    @Test
    void waitsOutTheSettleGraceAfterAFreshFill() {
        Strategy strategy = active(StrategyLifecycleState.BASE_BUY_FILLED);
        Instant justNow = NOW.minus(30, ChronoUnit.SECONDS);
        List<StrategyOrder> orders = List.of(buy(strategy, StrategyOrderStatus.FILLED, "10", justNow));

        assertFalse(ExternallyClosedPositionReconciler.reconcile(strategy, orders, Set.of(), Set.of(), NOW),
                "a fresh fill can reach the orders list before the positions list");
        assertEquals(StrategyStatus.ACTIVE, strategy.status());
    }

    @Test
    void neverTouchesRowsThatAreNotActiveOrNotHoldingShares() {
        List<StrategyLifecycleState> notHolding = List.of(
                StrategyLifecycleState.BASE_BUY_PLACED, StrategyLifecycleState.QUEUED_FOR_OPEN,
                StrategyLifecycleState.SELL_PLACED, StrategyLifecycleState.COMPLETED);
        for (StrategyLifecycleState state : notHolding) {
            Strategy strategy = active(state);
            List<StrategyOrder> orders = List.of(buy(strategy, StrategyOrderStatus.FILLED, "10", LONG_AGO));
            assertFalse(ExternallyClosedPositionReconciler.reconcile(strategy, orders, Set.of(), Set.of(), NOW), state.name());
        }
        Strategy paused = strategy(StrategyStatus.PAUSED, StrategyLifecycleState.BASE_BUY_FILLED, "AAPL");
        assertFalse(ExternallyClosedPositionReconciler.reconcile(paused,
                List.of(buy(paused, StrategyOrderStatus.FILLED, "10", LONG_AGO)), Set.of(), Set.of(), NOW),
                "an operator's pause is never undone or closed");
    }

    @Test
    void doesNothingWithoutOrdersOrBrokerData() {
        Strategy strategy = active(StrategyLifecycleState.BASE_BUY_FILLED);

        assertFalse(ExternallyClosedPositionReconciler.reconcile(strategy, List.of(), Set.of(), Set.of(), NOW));
        assertFalse(ExternallyClosedPositionReconciler.reconcile(strategy,
                List.of(buy(strategy, StrategyOrderStatus.FILLED, "10", LONG_AGO)), null, Set.of(), NOW));
    }

    private Strategy active(StrategyLifecycleState state) {
        return strategy(StrategyStatus.ACTIVE, state, "AAPL");
    }

    private StrategyOrder buy(Strategy strategy, StrategyOrderStatus status, String filled, Instant at) {
        return order(strategy, StrategyStage.BASE_BUY, StrategyOrderSide.BUY, status, filled, at);
    }

    private StrategyOrder sell(Strategy strategy, StrategyOrderStatus status, String filled, Instant at) {
        return order(strategy, StrategyStage.TARGET_SELL, StrategyOrderSide.SELL, status, filled, at);
    }

    private StrategyOrder order(Strategy strategy, StrategyStage stage, StrategyOrderSide side,
                                StrategyOrderStatus status, String filled, Instant at) {
        return new StrategyOrder(
                UUID.randomUUID().toString(), strategy.id(), stage, "alpaca-" + UUID.randomUUID(), "client-" + UUID.randomUUID(),
                strategy.symbol(), side, StrategyOrderType.LIMIT, new BigDecimal("8.00"), BigDecimal.ZERO,
                new BigDecimal("10"), new BigDecimal(filled), new BigDecimal("8.00"), status, at, at, at, "{}");
    }

    private Strategy strategy(StrategyStatus status, StrategyLifecycleState state, String symbol) {
        return new Strategy(
                UUID.randomUUID().toString(),
                symbol + " Strategy",
                symbol,
                StrategyMode.PAPER,
                status,
                state,
                new BigDecimal("100.00"),
                10,
                new BigDecimal("95.00"),
                5,
                new BigDecimal("90.00"),
                5,
                true,
                StopLossType.FIXED_PRICE,
                new BigDecimal("85.00"),
                BigDecimal.ZERO,
                false,
                BigDecimal.ZERO,
                true,
                new BigDecimal("120.00"),
                new BigDecimal("100.00"),
                true,
                false,
                ProfitHoldType.PERCENT_TRAILING,
                new BigDecimal("5.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                false,
                30,
                new BigDecimal("10000.00"),
                5,
                Instant.now(),
                Instant.now()
        );
    }
}
