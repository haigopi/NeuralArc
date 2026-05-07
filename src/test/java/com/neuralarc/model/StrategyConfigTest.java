package com.neuralarc.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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

    @Test
    void profitActivationThresholdDefaultsToFixedAmount() {
        StrategyConfig config = new StrategyConfig(
                "NEO",
                new BigDecimal("8.00"),
                10,
                new BigDecimal("7.00"),
                new BigDecimal("10.00"),
                new BigDecimal("7.40"),
                5,
                new BigDecimal("6.40"),
                5,
                2,
                true,
                false
        );

        assertEquals(ThresholdType.FIXED_AMOUNT, config.automaticStopSellThresholdType());
    }

    @Test
    void sellTriggerIsDefaultProfitControlModeWhenEnabled() {
        StrategyConfig config = new StrategyConfig(
                "NEO",
                new BigDecimal("8.00"),
                10,
                new BigDecimal("7.00"),
                new BigDecimal("10.00"),
                new BigDecimal("7.40"),
                5,
                new BigDecimal("6.40"),
                5,
                2,
                true,
                false
        );

        assertEquals(ProfitControlMode.SELL_TRIGGER, config.profitControlMode());
    }

    @Test
    void manualOnlyModeRemainsAvailableWhenSellTriggerIsDisabled() {
        StrategyConfig config = new StrategyConfig(
                "NEO",
                new BigDecimal("8.00"),
                10,
                true,
                new BigDecimal("7.00"),
                false,
                BigDecimal.ZERO,
                new BigDecimal("7.40"),
                5,
                new BigDecimal("6.40"),
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
                ThresholdType.FIXED_AMOUNT,
                BigDecimal.ZERO,
                TrailingType.PERCENTAGE,
                BigDecimal.ZERO
        );

        assertEquals(ProfitControlMode.NONE, config.profitControlMode());
        assertFalse(config.resubmitOnExpiryEnabled());
    }

}
