package com.neuralarc.ui;

import com.neuralarc.model.StrategyOrder;
import com.neuralarc.model.StrategyOrderSide;
import com.neuralarc.model.StrategyOrderStatus;
import com.neuralarc.model.StrategyOrderType;
import com.neuralarc.model.StrategyStage;
import com.neuralarc.model.StrategyStatus;
import com.neuralarc.model.Strategy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static com.neuralarc.ui.AverageDownTestEntries.position;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchivedPositionCleanupTest {
    @Test
    void anArchivedEntryThatWasCancelledBeforeFillingIsCleanable() {
        assertTrue(ArchivedPositionCleanup.isCleanable(archived(),
                List.of(order(StrategyOrderStatus.CANCELED, "0"), order(StrategyOrderStatus.FAILED, "0"))));
        assertTrue(ArchivedPositionCleanup.isCleanable(archived(), List.of()), "never even placed an order");
    }

    @Test
    void anythingWithAFillIsTradeHistoryAndIsKept() {
        assertFalse(ArchivedPositionCleanup.isCleanable(archived(), List.of(order(StrategyOrderStatus.FILLED, "5"))));
        assertFalse(ArchivedPositionCleanup.isCleanable(archived(), List.of(order(StrategyOrderStatus.PARTIALLY_FILLED, "2"))));
        assertFalse(ArchivedPositionCleanup.isCleanable(archived(), List.of(order(StrategyOrderStatus.CANCELED, "3"))),
                "a cancel after a partial fill still bought shares");
    }

    @Test
    void anOrderStillWorkingAtTheBrokerKeepsItsStrategy() {
        assertFalse(ArchivedPositionCleanup.isCleanable(archived(), List.of(order(StrategyOrderStatus.SUBMITTED, "0"))));
    }

    @Test
    void rowsShowingInTheGridsAreNeverCleanable() {
        for (StrategyStatus status : List.of(StrategyStatus.ACTIVE, StrategyStatus.PAUSED, StrategyStatus.CREATED,
                StrategyStatus.FAILED, StrategyStatus.COMPLETED)) {
            Strategy strategy = position("MSFT", 0, "0", "400").strategy;
            strategy.setStatus(status);
            assertFalse(ArchivedPositionCleanup.isCleanable(strategy, List.of()), status.name());
        }
    }

    private static Strategy archived() {
        Strategy strategy = position("DTSS", 0, "0", "0.92").strategy;
        strategy.setStatus(StrategyStatus.ARCHIVED);
        return strategy;
    }

    private static StrategyOrder order(StrategyOrderStatus status, String filled) {
        return new StrategyOrder("o-" + status + filled, "s1", StrategyStage.BASE_BUY, "ord", "client", "DTSS",
                StrategyOrderSide.BUY, StrategyOrderType.LIMIT, new BigDecimal("0.55"), BigDecimal.ZERO,
                new BigDecimal("5"), new BigDecimal(filled), BigDecimal.ZERO, status,
                Instant.now(), Instant.now(), null, "{}");
    }
}
