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
import com.neuralarc.model.TimeInForce;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TargetSellPricingTest {
    private static final Instant PLACED = Instant.parse("2026-09-10T14:00:00Z");

    @Test
    void anAverageDownKeepsTheSameMarginOverTheNewAverage() {
        // 10 @ $8.00 with the target at $10.00 (25% over cost); buy 10 more @ $6.00 -> 20 @ $7.00.
        StrategyOrder target = sell(StrategyStage.TARGET_SELL, "10", "10.00", TimeInForce.GTC, StrategyOrderStatus.SUBMITTED, PLACED);
        StrategyOrder averageDown = buy("10", "6.00", PLACED.plusSeconds(600));

        TargetSellPricing.Replacement replacement = TargetSellPricing.forReplacement(
                strategy("10.00"), target, position("20", "7.00"), List.of(target, averageDown));

        assertEquals(0, new BigDecimal("8.75").compareTo(replacement.limitPrice()), "10.00 x 7.00 / 8.00");
        assertTrue(replacement.repriced());
        assertEquals(TimeInForce.GTC, replacement.timeInForce(), "a GTC target stays GTC");
    }

    @Test
    void neverPricesAtTheMarketWhenNoAverageDownExplainsTheChange() {
        // The share count changed at the broker with no buy of the strategy's own behind it.
        StrategyOrder target = sell(StrategyStage.TARGET_SELL, "10", "10.00", TimeInForce.DAY, StrategyOrderStatus.SUBMITTED, PLACED);

        TargetSellPricing.Replacement replacement = TargetSellPricing.forReplacement(
                strategy("10.00"), target, position("15", "8.00"), List.of(target));

        assertEquals(0, new BigDecimal("10.00").compareTo(replacement.limitPrice()), "the earlier limit, unchanged");
        assertFalse(replacement.repriced());
    }

    @Test
    void buysFilledBeforeTheTargetWasPlacedDoNotCountAsAnAverageDown() {
        StrategyOrder earlierBuy = buy("10", "8.00", PLACED.minusSeconds(600));
        StrategyOrder target = sell(StrategyStage.TARGET_SELL, "10", "10.00", TimeInForce.DAY, StrategyOrderStatus.SUBMITTED, PLACED);

        TargetSellPricing.Replacement replacement = TargetSellPricing.forReplacement(
                strategy("10.00"), target, position("10", "8.00"), List.of(earlierBuy, target));

        assertFalse(replacement.repriced());
    }

    @Test
    void aLimitAtOrBelowTheOldCostIsADeliberateLossExitAndIsNotRescaled() {
        StrategyOrder lossExit = sell(StrategyStage.TARGET_SELL, "10", "7.50", TimeInForce.DAY, StrategyOrderStatus.SUBMITTED, PLACED);
        StrategyOrder averageDown = buy("10", "6.00", PLACED.plusSeconds(60));

        TargetSellPricing.Replacement replacement = TargetSellPricing.forReplacement(
                strategy("7.50"), lossExit, position("20", "7.00"), List.of(lossExit, averageDown));

        assertEquals(0, new BigDecimal("7.50").compareTo(replacement.limitPrice()));
        assertFalse(replacement.repriced());
    }

    @Test
    void restoringWithNoEarlierOrderUsesTheStrategysTarget() {
        TargetSellPricing.Replacement replacement = TargetSellPricing.forReplacement(
                strategy("12.40"), null, position("10", "8.00"), List.of());

        assertEquals(0, new BigDecimal("12.40").compareTo(replacement.limitPrice()));
        assertEquals(TimeInForce.DAY, replacement.timeInForce());
    }

    @Test
    void findsTheMostRecentExitThatWentAwayWithoutFilling() {
        StrategyOrder filledEarlier = sell(StrategyStage.TARGET_SELL, "5", "9.00", TimeInForce.DAY, StrategyOrderStatus.FILLED, PLACED.minusSeconds(900));
        StrategyOrder expired = sell(StrategyStage.TARGET_SELL, "10", "10.00", TimeInForce.DAY, StrategyOrderStatus.EXPIRED, PLACED);
        StrategyOrder filledLater = sell(StrategyStage.TARGET_SELL, "1", "11.00", TimeInForce.DAY, StrategyOrderStatus.FILLED, PLACED.plusSeconds(900));

        assertEquals(expired, TargetSellPricing.latestUnfilledBrokerManagedExit(List.of(filledEarlier, expired, filledLater)));
        assertNull(TargetSellPricing.latestUnfilledBrokerManagedExit(List.of(filledEarlier)));
    }

    private static Strategy strategy(String targetPrice) {
        return new Strategy(
                UUID.randomUUID().toString(), "s", "AAPL", StrategyMode.PAPER, StrategyStatus.ACTIVE,
                StrategyLifecycleState.SELL_PLACED,
                new BigDecimal("8.00"), 10,
                new BigDecimal("6.00"), 5,
                new BigDecimal("5.00"), 5,
                true, StopLossType.FIXED_PRICE, new BigDecimal("7.00"), BigDecimal.ZERO,
                false, BigDecimal.ZERO,
                true, new BigDecimal(targetPrice), new BigDecimal("100.00"), true,
                false, ProfitHoldType.PERCENT_TRAILING, new BigDecimal("10.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                false, 25, new BigDecimal("300.00"), 2, Instant.now(), Instant.now()
        );
    }

    private static AlpacaPositionData position(String quantity, String averageEntry) {
        return new AlpacaPositionData("AAPL", new BigDecimal(quantity), new BigDecimal(averageEntry), new BigDecimal("6.50"), "{}");
    }

    private static StrategyOrder sell(StrategyStage stage, String quantity, String limit, TimeInForce timeInForce,
                                     StrategyOrderStatus status, Instant submittedAt) {
        return new StrategyOrder(UUID.randomUUID().toString(), "s1", stage, "ord-" + UUID.randomUUID(), "cid",
                "AAPL", StrategyOrderSide.SELL, StrategyOrderType.LIMIT, new BigDecimal(limit), BigDecimal.ZERO,
                new BigDecimal(quantity), BigDecimal.ZERO, BigDecimal.ZERO, status, submittedAt, submittedAt,
                null, "{}", timeInForce);
    }

    private static StrategyOrder buy(String quantity, String price, Instant filledAt) {
        return new StrategyOrder(UUID.randomUUID().toString(), "s1", StrategyStage.MANUAL_BUY, "ord-" + UUID.randomUUID(), "cid",
                "AAPL", StrategyOrderSide.BUY, StrategyOrderType.LIMIT, new BigDecimal(price), BigDecimal.ZERO,
                new BigDecimal(quantity), new BigDecimal(quantity), new BigDecimal(price), StrategyOrderStatus.FILLED,
                filledAt, filledAt, filledAt, "{}", TimeInForce.DAY);
    }
}
