package com.neuralarc.service;

import com.neuralarc.model.StrategyOrder;
import com.neuralarc.model.StrategyOrderSide;
import com.neuralarc.model.StrategyOrderStatus;
import com.neuralarc.model.StrategyOrderType;
import com.neuralarc.model.StrategyStage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SellBasisTest {
    @Test
    void aSellWithinTheTrackedPositionIsPricedAtTheTrackedCost() {
        SellBasis.Result result = SellBasis.of(new BigDecimal("240.80"), new BigDecimal("18"),
                new BigDecimal("18"), new BigDecimal("226.83166667"), BigDecimal.ZERO);

        assertFalse(result.brokerReconciled());
        assertFalse(result.hasUntrackedShares());
        assertEquals(new BigDecimal("18"), result.quantity());
        assertEquals(0, result.realized().setScale(2, java.math.RoundingMode.HALF_UP)
                .compareTo(new BigDecimal("251.43")), "the balanced sell keeps the figure it always had");
    }

    @Test
    void aSellBeyondTheTrackedPositionIsPricedAtTheBrokersEntry() {
        // The NBIS case: 11 shares sold, 1 tracked at 228.59, broker holding all 11 at 231.00.
        SellBasis.Result result = SellBasis.of(new BigDecimal("209.90"), new BigDecimal("11"),
                new BigDecimal("1"), new BigDecimal("228.59"), new BigDecimal("231.00"));

        assertTrue(result.brokerReconciled());
        assertEquals(new BigDecimal("11"), result.quantity(), "every share sold is accounted for");
        assertEquals(new BigDecimal("231.00"), result.averageCost());
        assertEquals(0, result.realized().compareTo(new BigDecimal("-232.10")), result.realized().toPlainString());
        assertEquals(new BigDecimal("10"), result.untrackedShares());
    }

    @Test
    void withoutABrokerEntryTheOldClampedFigureIsKept() {
        // A sell recorded before the broker entry was captured must not gain an invented basis.
        SellBasis.Result result = SellBasis.of(new BigDecimal("209.90"), new BigDecimal("11"),
                new BigDecimal("1"), new BigDecimal("228.59"), BigDecimal.ZERO);

        assertFalse(result.brokerReconciled());
        assertEquals(new BigDecimal("1"), result.quantity());
        assertEquals(0, result.realized().compareTo(new BigDecimal("-18.69")), result.realized().toPlainString());
        assertTrue(result.hasUntrackedShares(), "the shortfall is still reported even when it cannot be priced");
        assertEquals(new BigDecimal("10"), result.untrackedShares());
    }

    @Test
    void aSellWithNothingTrackedAndNoBrokerEntryBooksNothing() {
        SellBasis.Result result = SellBasis.of(new BigDecimal("209.90"), new BigDecimal("11"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        assertTrue(result.isEmpty());
        assertEquals(0, result.realized().compareTo(BigDecimal.ZERO));
        assertEquals(new BigDecimal("11"), result.untrackedShares());
    }

    @Test
    void aSellOfNothingBooksNothing() {
        SellBasis.Result result = SellBasis.of(new BigDecimal("209.90"), BigDecimal.ZERO,
                new BigDecimal("5"), new BigDecimal("100.00"), new BigDecimal("101.00"));

        assertTrue(result.isEmpty());
        assertEquals(0, result.realized().compareTo(BigDecimal.ZERO));
    }

    @Test
    void nullsAreTreatedAsAbsentRatherThanThrowing() {
        SellBasis.Result result = SellBasis.of(null, null, null, null, null);

        assertTrue(result.isEmpty());
        assertEquals(0, result.realized().compareTo(BigDecimal.ZERO));
    }

    @Test
    void theBrokerEntryIsReadBackFromTheOrdersOwnMetadata() {
        StrategyOrder order = sell("{\"" + StrategyService.BROKER_AVERAGE_ENTRY_JSON_KEY + "\":\"231.00\"}");

        assertEquals(new BigDecimal("231.00"), SellBasis.brokerAverageEntry(order));
    }

    @Test
    void anOrderWithoutUsableMetadataReportsNoBrokerEntry() {
        assertEquals(BigDecimal.ZERO, SellBasis.brokerAverageEntry(sell("{}")));
        assertEquals(BigDecimal.ZERO, SellBasis.brokerAverageEntry(sell("not json at all")));
        assertEquals(BigDecimal.ZERO, SellBasis.brokerAverageEntry(sell("")));
        assertEquals(BigDecimal.ZERO, SellBasis.brokerAverageEntry(null));
    }

    private static StrategyOrder sell(String rawJson) {
        return new StrategyOrder(
                "order-1", "strategy-1", StrategyStage.MANUAL_EXIT, "alpaca-1", "client-1", "NBIS",
                StrategyOrderSide.SELL, StrategyOrderType.LIMIT, new BigDecimal("209.90"), BigDecimal.ZERO,
                new BigDecimal("11"), new BigDecimal("11"), new BigDecimal("209.90"),
                StrategyOrderStatus.FILLED, Instant.now(), Instant.now(), Instant.now(), rawJson);
    }
}
