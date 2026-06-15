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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingBuyOrderGuardTest {

    private static StrategyOrder order(StrategyOrderSide side, StrategyOrderType type, StrategyOrderStatus status) {
        return new StrategyOrder(
                UUID.randomUUID().toString(), "strat-1", StrategyStage.MANUAL_BUY, "ord-1", "client-1", "META",
                side, type, new BigDecimal("100.00"), BigDecimal.ZERO, new BigDecimal("1"), BigDecimal.ZERO,
                BigDecimal.ZERO, status, Instant.now(), Instant.now(), null, "{}");
    }

    @Test
    void acceptsOpenLimitBuyStatuses() {
        assertTrue(PendingBuyOrderGuard.isCancelablePendingLimitBuy(
                order(StrategyOrderSide.BUY, StrategyOrderType.LIMIT, StrategyOrderStatus.SUBMITTED)));
        assertTrue(PendingBuyOrderGuard.isCancelablePendingLimitBuy(
                order(StrategyOrderSide.BUY, StrategyOrderType.LIMIT, StrategyOrderStatus.PENDING)));
        assertTrue(PendingBuyOrderGuard.isCancelablePendingLimitBuy(
                order(StrategyOrderSide.BUY, StrategyOrderType.LIMIT, StrategyOrderStatus.PARTIALLY_FILLED)));
    }

    @Test
    void rejectsTerminalStatuses() {
        for (StrategyOrderStatus terminal : List.of(StrategyOrderStatus.FILLED, StrategyOrderStatus.CANCELED,
                StrategyOrderStatus.REJECTED, StrategyOrderStatus.FAILED)) {
            assertFalse(PendingBuyOrderGuard.isCancelablePendingLimitBuy(
                            order(StrategyOrderSide.BUY, StrategyOrderType.LIMIT, terminal)),
                    "should not allow canceling a " + terminal + " order");
        }
    }

    @Test
    void rejectsSellOrdersAndNonLimitOrders() {
        assertFalse(PendingBuyOrderGuard.isCancelablePendingLimitBuy(
                order(StrategyOrderSide.SELL, StrategyOrderType.LIMIT, StrategyOrderStatus.SUBMITTED)));
        assertFalse(PendingBuyOrderGuard.isCancelablePendingLimitBuy(
                order(StrategyOrderSide.BUY, StrategyOrderType.MARKET, StrategyOrderStatus.SUBMITTED)));
        assertFalse(PendingBuyOrderGuard.isCancelablePendingLimitBuy(null));
    }

    @Test
    void hasCancelableScansTheList() {
        List<StrategyOrder> orders = List.of(
                order(StrategyOrderSide.BUY, StrategyOrderType.LIMIT, StrategyOrderStatus.FILLED),
                order(StrategyOrderSide.BUY, StrategyOrderType.LIMIT, StrategyOrderStatus.SUBMITTED));
        assertTrue(PendingBuyOrderGuard.hasCancelablePendingLimitBuy(orders));

        List<StrategyOrder> onlyFilled = List.of(
                order(StrategyOrderSide.BUY, StrategyOrderType.LIMIT, StrategyOrderStatus.FILLED));
        assertFalse(PendingBuyOrderGuard.hasCancelablePendingLimitBuy(onlyFilled));
        assertFalse(PendingBuyOrderGuard.hasCancelablePendingLimitBuy(List.of()));
        assertFalse(PendingBuyOrderGuard.hasCancelablePendingLimitBuy(null));
    }
}
