package com.neuralarc.ui;

import com.neuralarc.model.ProfitHoldType;
import com.neuralarc.model.StopLossType;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduledScanDuplicatePolicyTest {
    @Test
    void detectsSamePlannedPricesAfterMoneyRounding() {
        Strategy strategy = strategy();
        strategy.setBaseBuyLimitPrice(new BigDecimal("101.100"));
        strategy.setStopLossPrice(new BigDecimal("99.000"));
        strategy.setTargetSellPrice(new BigDecimal("104.130"));

        assertTrue(ScheduledScanDuplicatePolicy.samePlannedPrices(
                strategy,
                new BigDecimal("101.10"),
                new BigDecimal("99.00"),
                new BigDecimal("104.13")
        ));
    }

    @Test
    void rejectsWhenAnyPlannedPriceChanges() {
        Strategy strategy = strategy();
        strategy.setBaseBuyLimitPrice(new BigDecimal("101.10"));
        strategy.setStopLossPrice(new BigDecimal("99.00"));
        strategy.setTargetSellPrice(new BigDecimal("104.13"));

        assertFalse(ScheduledScanDuplicatePolicy.samePlannedPrices(
                strategy,
                new BigDecimal("101.10"),
                new BigDecimal("99.00"),
                new BigDecimal("104.14")
        ));
    }

    private Strategy strategy() {
        return new Strategy(
                UUID.randomUUID().toString(),
                "AAPL Strategy",
                "AAPL",
                StrategyMode.PAPER,
                StrategyStatus.CREATED,
                StrategyLifecycleState.CREATED,
                new BigDecimal("10"),
                1,
                BigDecimal.ZERO,
                0,
                BigDecimal.ZERO,
                0,
                false,
                StopLossType.FIXED_PRICE,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                false,
                BigDecimal.ZERO,
                false,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                false,
                false,
                ProfitHoldType.PERCENT_TRAILING,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                false,
                25,
                BigDecimal.ZERO,
                2,
                Instant.now(),
                Instant.now()
        );
    }
}
