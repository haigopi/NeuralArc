package com.neuralarc.ui;

import com.neuralarc.model.Position;
import com.neuralarc.model.ProfitHoldType;
import com.neuralarc.model.StopLossType;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyStatus;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OrbPendingBaseBuyRowStylerTest {
    @Test
    void usesYellowWhenOrbPendingBaseBuyIsAboveCurrentPrice() {
        Strategy strategy = strategy("ORB_RECOMMENDED", "101.00");
        Position position = position("100.00");

        Color foreground = OrbPendingBaseBuyRowStyler.foreground(strategy, position);

        assertEquals(OrbPendingBaseBuyRowStyler.BASE_BUY_ABOVE_CURRENT, foreground);
    }

    @Test
    void usesGreenWhenOrbPendingBaseBuyIsBelowCurrentPrice() {
        Strategy strategy = strategy("ORB_ARMED", "99.00");
        Position position = position("100.00");

        Color foreground = OrbPendingBaseBuyRowStyler.foreground(strategy, position);

        assertEquals(OrbPendingBaseBuyRowStyler.BASE_BUY_BELOW_CURRENT, foreground);
    }

    @Test
    void skipsEqualPricesAndNonOrbRows() {
        assertNull(OrbPendingBaseBuyRowStyler.foreground(strategy("ORB_RECOMMENDED", "100.00"), position("100.00")));
        assertNull(OrbPendingBaseBuyRowStyler.foreground(strategy("GAP_ROCKET_RECOMMENDED", "101.00"), position("100.00")));
    }

    @Test
    void skipsRowsWithoutCurrentPrice() {
        assertNull(OrbPendingBaseBuyRowStyler.foreground(strategy("ORB_RECOMMENDED", "101.00"), new Position("AAPL")));
    }

    private static Position position(String lastPrice) {
        Position position = new Position("AAPL");
        position.setLastPrice(new BigDecimal(lastPrice));
        return position;
    }

    private static Strategy strategy(String latestOrderStatus, String baseBuyPrice) {
        Strategy strategy = new Strategy(
                "s1",
                "ORB_ENGINE: AAPL PAPER",
                "AAPL",
                StrategyMode.PAPER,
                StrategyStatus.CREATED,
                StrategyLifecycleState.CREATED,
                new BigDecimal(baseBuyPrice),
                1,
                new BigDecimal("90.00"),
                0,
                new BigDecimal("80.00"),
                0,
                false,
                StopLossType.FIXED_PRICE,
                new BigDecimal("95.00"),
                BigDecimal.ZERO,
                true,
                new BigDecimal("110.00"),
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
                1,
                new BigDecimal("1000.00"),
                5,
                Instant.now(),
                Instant.now()
        );
        strategy.setLatestOrderStatus(latestOrderStatus);
        return strategy;
    }
}
