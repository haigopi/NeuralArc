package com.neuralarc.service;

import com.neuralarc.api.AlpacaPositionData;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ManagedStopLossEvaluatorTest {
    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private AlpacaPositionData position(String quantity, String avgEntry) {
        return new AlpacaPositionData("MOVE", bd(quantity), bd(avgEntry), bd("0"), "{}");
    }

    @Test
    void sellsWhenPriceFallsToALegitimateStopBelowCost() {
        Strategy strategy = strategy(true, bd("13.00"));

        ManagedStopLossEvaluator.Decision decision = ManagedStopLossEvaluator.decide(
                strategy, position("1", "15.12"), bd("12.90"), List.of());

        assertEquals(ManagedStopLossEvaluator.Action.SELL, decision.action());
    }

    @Test
    void autoCorrectsInsteadOfSellingWhenTheStopSitsAboveCostAndPrice() {
        Strategy strategy = strategy(true, bd("15.55"));

        ManagedStopLossEvaluator.Decision decision = ManagedStopLossEvaluator.decide(
                strategy, position("1", "15.12"), bd("12.54"), List.of());

        assertEquals(ManagedStopLossEvaluator.Action.AUTO_CORRECT, decision.action(),
                "a stop above the cost basis must never liquidate the position");
        assertEquals(bd("15.55"), decision.threshold());
    }

    @Test
    void doesNothingWhilePriceStaysAboveTheStop() {
        Strategy strategy = strategy(true, bd("13.00"));

        ManagedStopLossEvaluator.Decision decision = ManagedStopLossEvaluator.decide(
                strategy, position("1", "15.12"), bd("14.00"), List.of());

        assertEquals(ManagedStopLossEvaluator.Action.NOT_SATISFIED, decision.action());
    }

    @Test
    void skipsWhenStopLossIsDisabled() {
        Strategy strategy = strategy(false, bd("13.00"));

        ManagedStopLossEvaluator.Decision decision = ManagedStopLossEvaluator.decide(
                strategy, position("1", "15.12"), bd("12.00"), List.of());

        assertEquals(ManagedStopLossEvaluator.Action.SKIP, decision.action());
    }

    @Test
    void skipsWhenAStopLossOrderIsAlreadyWorking() {
        Strategy strategy = strategy(true, bd("13.00"));
        StrategyOrder pendingStop = new StrategyOrder(
                UUID.randomUUID().toString(), strategy.id(), StrategyStage.STOP_LOSS,
                "ord-1", "client-1", "MOVE", StrategyOrderSide.SELL, StrategyOrderType.LIMIT,
                bd("13.00"), BigDecimal.ZERO, bd("1"), BigDecimal.ZERO, BigDecimal.ZERO,
                StrategyOrderStatus.SUBMITTED, Instant.now(), Instant.now(), null, "{}");

        ManagedStopLossEvaluator.Decision decision = ManagedStopLossEvaluator.decide(
                strategy, position("1", "15.12"), bd("12.00"), List.of(pendingStop));

        assertEquals(ManagedStopLossEvaluator.Action.SKIP, decision.action());
    }

    private Strategy strategy(boolean stopLossEnabled, BigDecimal stopLossPrice) {
        return new Strategy(
                UUID.randomUUID().toString(), "MOVE Strategy", "MOVE", StrategyMode.PAPER,
                StrategyStatus.ACTIVE, StrategyLifecycleState.BASE_BUY_FILLED,
                bd("15.12"), 1,
                BigDecimal.ZERO, 0,
                BigDecimal.ZERO, 0,
                stopLossEnabled, StopLossType.FIXED_PRICE, stopLossPrice, BigDecimal.ZERO,
                false, BigDecimal.ZERO,
                false, BigDecimal.ZERO, BigDecimal.ZERO, false,
                false, ProfitHoldType.PERCENT_TRAILING, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                false, 10, bd("1000.00"), 60, Instant.now(), Instant.now()
        );
    }
}
