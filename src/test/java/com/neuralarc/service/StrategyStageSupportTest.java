package com.neuralarc.service;

import com.neuralarc.model.StrategyOrder;
import com.neuralarc.model.StrategyOrderSide;
import com.neuralarc.model.StrategyOrderStatus;
import com.neuralarc.model.StrategyOrderType;
import com.neuralarc.model.StrategyStage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategyStageSupportTest {
    @Test
    void anEntryWithOnlyAWorkingOrderIsAwaitingItsFirstFill() {
        List<StrategyOrder> orders = List.of(order(StrategyStage.BASE_BUY, StrategyOrderStatus.SUBMITTED, "0"));

        assertTrue(StrategyStageSupport.awaitingFirstEntryFill(orders));
        assertFalse(StrategyStageSupport.hasEntryFill(orders));
    }

    @Test
    void aFilledOrPartlyFilledEntryOrderMeansTheEntryHoldsShares() {
        assertFalse(StrategyStageSupport.awaitingFirstEntryFill(
                List.of(order(StrategyStage.BASE_BUY, StrategyOrderStatus.FILLED, "10"))));
        assertFalse(StrategyStageSupport.awaitingFirstEntryFill(List.of(
                order(StrategyStage.BASE_BUY, StrategyOrderStatus.PARTIALLY_FILLED, "3"),
                order(StrategyStage.BASE_BUY, StrategyOrderStatus.PARTIALLY_FILLED, "3"))));
    }

    @Test
    void aPartialFillOfZeroSharesIsNotAFill() {
        assertTrue(StrategyStageSupport.awaitingFirstEntryFill(
                List.of(order(StrategyStage.BASE_BUY, StrategyOrderStatus.PARTIALLY_FILLED, "0"))));
    }

    @Test
    void anEntryWithNoOrdersAtAllIsNotAwaitingAFill() {
        assertFalse(StrategyStageSupport.awaitingFirstEntryFill(List.of()));
        assertFalse(StrategyStageSupport.awaitingFirstEntryFill(
                List.of(order(StrategyStage.TARGET_SELL, StrategyOrderStatus.SUBMITTED, "0"))),
                "a working sell is not a working entry");
    }

    private static StrategyOrder order(StrategyStage stage, StrategyOrderStatus status, String filled) {
        StrategyOrderSide side = StrategyStage.TARGET_SELL == stage ? StrategyOrderSide.SELL : StrategyOrderSide.BUY;
        return new StrategyOrder("o" + System.nanoTime(), "s1", stage, "a1", "c1", "AAPL", side,
                StrategyOrderType.LIMIT, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN, new BigDecimal(filled),
                BigDecimal.ZERO, status, Instant.now(), Instant.now(), null, "{}");
    }
}
