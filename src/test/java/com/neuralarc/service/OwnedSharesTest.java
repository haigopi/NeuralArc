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

import static org.junit.jupiter.api.Assertions.assertEquals;

class OwnedSharesTest {
    @Test
    void filledBuysMinusFilledSellsAndPartialFillsCount() {
        assertEquals(7, OwnedShares.net(List.of(
                order(StrategyOrderSide.BUY, StrategyOrderStatus.FILLED, "10", "10"),
                order(StrategyOrderSide.BUY, StrategyOrderStatus.PARTIALLY_FILLED, "5", "2"),
                order(StrategyOrderSide.SELL, StrategyOrderStatus.FILLED, "5", "5"),
                order(StrategyOrderSide.BUY, StrategyOrderStatus.SUBMITTED, "9", "0"))));
    }

    @Test
    void neverBelowZeroAndEmptyIsZero() {
        assertEquals(0, OwnedShares.net(List.of(order(StrategyOrderSide.SELL, StrategyOrderStatus.FILLED, "5", "5"))));
        assertEquals(0, OwnedShares.net(List.of()));
        assertEquals(0, OwnedShares.net(null));
    }

    private static StrategyOrder order(StrategyOrderSide side, StrategyOrderStatus status, String requested, String filled) {
        return new StrategyOrder("o" + System.nanoTime(), "s1", StrategyStage.BASE_BUY, "a", "c", "AAPL", side,
                StrategyOrderType.LIMIT, BigDecimal.TEN, BigDecimal.ZERO, new BigDecimal(requested), new BigDecimal(filled),
                BigDecimal.TEN, status, Instant.now(), Instant.now(), null, "{}");
    }
}
