package com.neuralarc.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StrategyConfigTest {
    @Test
    void monetaryFieldsAreRoundedToTwoDecimals() {
        StrategyConfig config = new StrategyConfig(
                "NEO",
                new BigDecimal("8.005"),
                10,
                true,
                new BigDecimal("7.994"),
                true,
                new BigDecimal("10.999"),
                new BigDecimal("7.444"),
                5,
                new BigDecimal("6.445"),
                5,
                true,
                false,
                BigDecimal.ZERO,
                2,
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

        assertEquals(new BigDecimal("8.01"), config.baseBuyPrice());
        assertEquals(new BigDecimal("7.99"), config.stopLoss());
        assertEquals(new BigDecimal("11.00"), config.sellTriggerPrice());
        assertEquals(new BigDecimal("7.44"), config.lossBuyLevel1Price());
        assertEquals(new BigDecimal("6.45"), config.lossBuyLevel2Price());
        assertEquals(true, config.stopLossEnabled());
    }

    @Test
    void stopLossEnabledCanBeDisabledInExplicitConstructor() {
        StrategyConfig config = new StrategyConfig(
                "NEO",
                new BigDecimal("8.00"),
                10,
                false,
                new BigDecimal("0.00"),
                true,
                new BigDecimal("10.00"),
                new BigDecimal("7.40"),
                5,
                new BigDecimal("6.40"),
                5,
                false,
                false,
                BigDecimal.ZERO,
                2,
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

        assertEquals(false, config.stopLossEnabled());
        assertEquals(BigDecimal.ZERO.setScale(2), config.stopLoss());
    }

}

