package com.neuralarc.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategyFromConfigTest {
    @Test
    void disabledLossBuyLevelsAreExcludedFromRiskSizing() {
        StrategyConfig config = new StrategyConfig(
                "tsla",
                new BigDecimal("200.00"),
                3,
                true,
                new BigDecimal("180.00"),
                true,
                new BigDecimal("240.00"),
                new BigDecimal("190.00"),
                4,
                new BigDecimal("175.00"),
                5,
                false,
                false,
                BigDecimal.ZERO,
                30,
                true,
                false,
                false,
                ProfitHoldType.PERCENT_TRAILING,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                false,
                ProfitControlMode.NONE,
                ThresholdType.PERCENTAGE,
                BigDecimal.ZERO,
                TrailingType.PERCENTAGE,
                BigDecimal.ZERO
        );

        Strategy strategy = Strategy.fromConfig("strategy-1", "TSLA Strategy", config, StrategyMode.PAPER);

        assertEquals("TSLA", strategy.symbol());
        assertFalse(strategy.lossBuyLevelsEnabled());
        assertEquals(3, strategy.maxTotalQuantity());
        assertEquals(new BigDecimal("600.00"), strategy.maxCapitalAllowed());
        assertEquals(new BigDecimal("600.00"), strategy.estimatedTotalCapital());
        assertEquals(3, strategy.configuredTotalQuantity());
    }

    @Test
    void dialogConfigFlagsPropagateToStrategyRules() {
        StrategyConfig config = new StrategyConfig(
                "aapl",
                new BigDecimal("100.00"),
                2,
                false,
                BigDecimal.ZERO,
                false,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                BigDecimal.ZERO,
                0,
                false,
                false,
                BigDecimal.ZERO,
                45,
                true,
                true,
                true,
                ProfitHoldType.FIXED_AMOUNT_TRAILING,
                BigDecimal.ZERO,
                new BigDecimal("1.25"),
                true,
                ProfitControlMode.NONE,
                ThresholdType.PERCENTAGE,
                BigDecimal.ZERO,
                TrailingType.PERCENTAGE,
                BigDecimal.ZERO
        );

        Strategy strategy = Strategy.fromConfig("strategy-2", "AAPL Strategy", config, StrategyMode.LIVE);

        assertEquals(StrategyMode.LIVE, strategy.mode());
        assertFalse(strategy.automatedStopLossEnabled());
        assertEquals(BigDecimal.ZERO.setScale(2), strategy.stopLossPrice());
        assertFalse(strategy.targetSellEnabled());
        assertTrue(strategy.alpacaTrailingStopEnabled());
        assertTrue(strategy.profitHoldEnabled());
        assertEquals(ProfitHoldType.FIXED_AMOUNT_TRAILING, strategy.profitHoldType());
        assertEquals(new BigDecimal("1.25"), strategy.profitHoldAmount());
        assertTrue(strategy.restartAfterExitEnabled());
        assertEquals(45, strategy.pollingIntervalSeconds());
    }
}
