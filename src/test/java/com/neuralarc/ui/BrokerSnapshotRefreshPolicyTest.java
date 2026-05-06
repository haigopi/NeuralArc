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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrokerSnapshotRefreshPolicyTest {

    @Test
    void usesStrictMinimumAmongActiveStrategiesWhenAboveFloor() {
        Strategy a = strategy("a", "AAPL", StrategyStatus.ACTIVE, 9);
        Strategy b = strategy("b", "MSFT", StrategyStatus.ACTIVE, 3);

        long interval = BrokerSnapshotRefreshPolicy.resolveIntervalMillis(List.of(a, b));

        assertEquals(3_000L, interval);
    }

    @Test
    void appliesMinimumFloorWhenActiveMinimumIsLowerThanFloor() {
        Strategy a = strategy("a", "AAPL", StrategyStatus.ACTIVE, 1);

        long interval = BrokerSnapshotRefreshPolicy.resolveIntervalMillis(List.of(a));

        assertEquals(2_000L, interval);
    }

    @Test
    void fallsBackWhenNoActiveEligibleStrategiesExist() {
        Strategy paused = strategy("a", "AAPL", StrategyStatus.PAUSED, 2);
        Strategy stopped = strategy("b", "MSFT", StrategyStatus.STOPPED, 2);

        long interval = BrokerSnapshotRefreshPolicy.resolveIntervalMillis(List.of(paused, stopped));

        assertEquals(5_000L, interval);
    }

    @Test
    void eligibilityRequiresActiveStatusAndSymbol() {
        Strategy active = strategy("a", "AAPL", StrategyStatus.ACTIVE, 2);
        Strategy paused = strategy("b", "AAPL", StrategyStatus.PAUSED, 2);
        Strategy blankSymbol = strategy("c", " ", StrategyStatus.ACTIVE, 2);

        assertTrue(BrokerSnapshotRefreshPolicy.eligibleForBrokerSnapshot(active));
        assertFalse(BrokerSnapshotRefreshPolicy.eligibleForBrokerSnapshot(paused));
        assertFalse(BrokerSnapshotRefreshPolicy.eligibleForBrokerSnapshot(blankSymbol));
    }

    private static Strategy strategy(String id, String symbol, StrategyStatus status, int pollingIntervalSeconds) {
        Strategy strategy = new Strategy(
                id,
                symbol + " Strategy",
                symbol,
                StrategyMode.PAPER,
                status,
                StrategyLifecycleState.CREATED,
                new BigDecimal("10"),
                1,
                new BigDecimal("9"),
                1,
                new BigDecimal("8"),
                1,
                true,
                StopLossType.FIXED_PRICE,
                new BigDecimal("7"),
                new BigDecimal("1"),
                false,
                BigDecimal.ZERO,
                true,
                new BigDecimal("11"),
                new BigDecimal("100"),
                false,
                false,
                ProfitHoldType.PERCENT_TRAILING,
                new BigDecimal("1"),
                new BigDecimal("1"),
                BigDecimal.ZERO,
                false,
                10,
                new BigDecimal("1000"),
                pollingIntervalSeconds,
                Instant.now(),
                Instant.now()
        );
        strategy.setStatus(status);
        return strategy;
    }
}

