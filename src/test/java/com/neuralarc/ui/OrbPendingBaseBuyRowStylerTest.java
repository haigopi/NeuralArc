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
    void usesRedWhenPendingBaseBuyIsAboveCurrentPrice() {
        Strategy strategy = strategy("ORB_RECOMMENDED", "101.00");
        Position position = position("100.00");

        Color foreground = OrbPendingBaseBuyRowStyler.foreground(strategy, position);

        assertEquals(OrbPendingBaseBuyRowStyler.BASE_BUY_ABOVE_CURRENT, foreground);
        assertEquals(OrbPendingBaseBuyRowStyler.PriceDirection.RED_ABOVE_MARKET,
                OrbPendingBaseBuyRowStyler.priceDirection(strategy, position));
    }

    @Test
    void usesGreenWhenOrbPendingBaseBuyIsBelowCurrentPrice() {
        Strategy strategy = strategy("ORB_ARMED", "99.00");
        Position position = position("100.00");

        Color foreground = OrbPendingBaseBuyRowStyler.foreground(strategy, position);

        assertEquals(OrbPendingBaseBuyRowStyler.BASE_BUY_BELOW_CURRENT, foreground);
        assertEquals(OrbPendingBaseBuyRowStyler.PriceDirection.GREEN_GAINER,
                OrbPendingBaseBuyRowStyler.priceDirection(strategy, position));
    }

    @Test
    void skipsEqualPricesAndRowsThatAreNotWaitingForPlacement() {
        assertNull(OrbPendingBaseBuyRowStyler.foreground(strategy("ORB_RECOMMENDED", "100.00"), position("100.00")));
        assertNull(OrbPendingBaseBuyRowStyler.foreground(strategy("FILLED", "101.00"), position("100.00")));
    }

    @Test
    void everyScannerRowIsRedWhenItsBaseBuyIsAboveTheCurrentPrice() {
        for (String status : new String[] {"GAP_ROCKET_RECOMMENDED", "VWAP_MONITORING", "DIP_HUNTER_RECOMMENDED",
                "SWING_RECOMMENDED", "RANGE_RIDER_RECOMMENDED", "EARNINGS_HUNTER_RECOMMENDED", "PROFIT_SHIELD_RECOMMENDED"}) {
            assertEquals(OrbPendingBaseBuyRowStyler.BASE_BUY_ABOVE_CURRENT,
                    OrbPendingBaseBuyRowStyler.foreground(strategy(status, "6.01"), position("5.96")), status);
        }
    }

    @Test
    void theAboveCurrentColourIsRed() {
        Color red = OrbPendingBaseBuyRowStyler.BASE_BUY_ABOVE_CURRENT;
        org.junit.jupiter.api.Assertions.assertTrue(red.getRed() > 180 && red.getGreen() < 140 && red.getBlue() < 140);
    }

    @Test
    void greenAndRedStayReadableOnTheDarkRowTheSelectionBlueAndALightRow() {
        Color darkRow = new Color(60, 63, 65);
        Color selectionBlue = new Color(70, 110, 190);
        Color lightRow = new Color(255, 255, 255);
        Strategy below = strategy("ORB_RECOMMENDED", "99.00");
        Strategy above = strategy("ORB_RECOMMENDED", "101.00");
        Position position = position("100.00");

        for (Color background : new Color[] {darkRow, selectionBlue, lightRow}) {
            for (Strategy strategy : new Strategy[] {below, above}) {
                Color foreground = OrbPendingBaseBuyRowStyler.foreground(strategy, position, background);
                org.junit.jupiter.api.Assertions.assertTrue(
                        OrbPendingBaseBuyRowStyler.contrast(foreground, background) >= 3.5,
                        foreground + " on " + background);
            }
        }
    }

    @Test
    void keepsTheThemeColourWhenItAlreadyReadsWell() {
        assertEquals(OrbPendingBaseBuyRowStyler.BASE_BUY_BELOW_CURRENT,
                OrbPendingBaseBuyRowStyler.foreground(strategy("ORB_ARMED", "99.00"), position("100.00"), new Color(60, 63, 65)));
    }

    @Test
    void skipsRowsWithoutCurrentPrice() {
        assertNull(OrbPendingBaseBuyRowStyler.foreground(strategy("ORB_RECOMMENDED", "101.00"), new Position("AAPL")));
    }

    static Position position(String lastPrice) {
        Position position = new Position("AAPL");
        position.setLastPrice(new BigDecimal(lastPrice));
        return position;
    }

    static Strategy strategy(String latestOrderStatus, String baseBuyPrice) {
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
