package com.neuralarc.service;

import com.neuralarc.model.ProfitControlMode;
import com.neuralarc.model.ProfitHoldType;
import com.neuralarc.model.StopLossType;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyStatus;
import com.neuralarc.model.ThresholdType;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategyValidatorTest {
    @Test
    void profitHoldModeDoesNotRequireSellTrigger() {
        Strategy strategy = validStrategy();
        strategy.setTargetSellEnabled(false);
        strategy.setProfitControlMode(ProfitControlMode.PROFIT_HOLD);
        strategy.setAutomaticStopSellThresholdType(ThresholdType.FIXED_AMOUNT);
        strategy.setAutomaticStopSellThreshold(new BigDecimal("2.00"));
        strategy.setProfitHoldEnabled(true);
        strategy.setProfitHoldType(ProfitHoldType.PERCENT_TRAILING);
        strategy.setProfitHoldPercent(new BigDecimal("10.00"));

        List<String> errors = new StrategyValidator().validate(strategy);

        assertFalse(errors.contains("Sell trigger must be enabled when Profit Hold or Alpaca trailing stop is enabled"));
        assertTrue(errors.isEmpty(), () -> "Unexpected validation errors: " + errors);
    }

    @Test
    void profitHoldModeRequiresProfitActivationThreshold() {
        Strategy strategy = validStrategy();
        strategy.setTargetSellEnabled(false);
        strategy.setProfitControlMode(ProfitControlMode.PROFIT_HOLD);
        strategy.setAutomaticStopSellThreshold(BigDecimal.ZERO);
        strategy.setProfitHoldEnabled(true);

        List<String> errors = new StrategyValidator().validate(strategy);

        assertTrue(errors.contains("Profit activation threshold must be positive"));
    }

    private static Strategy validStrategy() {
        return new Strategy(
                UUID.randomUUID().toString(),
                "Test Strategy",
                "AAPL",
                StrategyMode.PAPER,
                StrategyStatus.CREATED,
                StrategyLifecycleState.CREATED,
                new BigDecimal("10.00"),
                10,
                new BigDecimal("9.00"),
                0,
                new BigDecimal("8.00"),
                0,
                true,
                StopLossType.FIXED_PRICE,
                new BigDecimal("9.00"),
                BigDecimal.ZERO,
                false,
                BigDecimal.ZERO,
                true,
                new BigDecimal("12.00"),
                new BigDecimal("100.00"),
                true,
                false,
                false,
                ProfitHoldType.PERCENT_TRAILING,
                new BigDecimal("10.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                false,
                10,
                new BigDecimal("100.00"),
                10,
                Instant.now(),
                Instant.now()
        );
    }
}
