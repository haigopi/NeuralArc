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

class SellQuantityGuardTest {
    @Test
    void aSecondFullSizeSellOnOneShareIsRefused() {
        List<StrategyOrder> orders = List.of(sell(StrategyStage.STOP_LOSS, "1", "0", StrategyOrderStatus.PENDING));

        assertEquals(0, SellQuantityGuard.allowed(1, BigDecimal.ONE, orders), "the share is already being sold");
    }

    @Test
    void aSellIsCappedAtWhatIsLeftUncommitted() {
        List<StrategyOrder> orders = List.of(sell(StrategyStage.TARGET_SELL, "6", "2", StrategyOrderStatus.PARTIALLY_FILLED));

        assertEquals(6, SellQuantityGuard.allowed(10, new BigDecimal("10"), orders), "10 held, 4 still to fill");
    }

    @Test
    void finishedOrdersAndBuysDoNotCount() {
        List<StrategyOrder> orders = List.of(
                sell(StrategyStage.TARGET_SELL, "5", "0", StrategyOrderStatus.CANCELED),
                sell(StrategyStage.STOP_LOSS, "5", "5", StrategyOrderStatus.FILLED));

        assertEquals(5, SellQuantityGuard.allowed(5, new BigDecimal("5"), orders));
    }

    @Test
    void nothingIsSoldFromAFlatOrShortPosition() {
        assertEquals(0, SellQuantityGuard.allowed(1, BigDecimal.ZERO, List.of()));
        assertEquals(0, SellQuantityGuard.allowed(1, new BigDecimal("-1"), List.of()), "MRVL's short");
        assertEquals(0, SellQuantityGuard.allowed(1, null, List.of()), "an unreadable position is not a licence to sell");
    }

    private static StrategyOrder sell(StrategyStage stage, String requested, String filled, StrategyOrderStatus status) {
        return new StrategyOrder("id-" + stage + status, "s1", stage, "ord", "client", "MRVL",
                StrategyOrderSide.SELL, StrategyOrderType.LIMIT, new BigDecimal("201.15"), BigDecimal.ZERO,
                new BigDecimal(requested), new BigDecimal(filled), BigDecimal.ZERO, status,
                Instant.now(), Instant.now(), null, "{}");
    }
}
