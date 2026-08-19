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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategyRecommendationMarkersTest {

    @Test
    void recognisesEveryScannerPrefixAsARecommendationRow() {
        for (String status : new String[]{"GAP_ROCKET_RECOMMENDED", "ORB_RECOMMENDED", "DIP_HUNTER_RECOMMENDED",
                "VWAP_RECOMMENDED", "SWING_RECOMMENDED", "RANGE_RIDER_RECOMMENDED", "EARNINGS_HUNTER_RECOMMENDED",
                "PROFIT_SHIELD_RECOMMENDED", "PROFIT_SHIELD_MONITORING"}) {
            assertTrue(StrategyRecommendationMarkers.isScannerRecommendationRow(strategy(status)),
                    status + " should be recognised as a scanner row");
        }
    }

    @Test
    void ignoresBrokerOrderStatuses() {
        assertFalse(StrategyRecommendationMarkers.isScannerRecommendationRow(strategy("new")));
        assertFalse(StrategyRecommendationMarkers.isScannerRecommendationRow(strategy("filled")));
        assertFalse(StrategyRecommendationMarkers.isScannerRecommendationRow(strategy(null)));
        assertFalse(StrategyRecommendationMarkers.isScannerRecommendationRow(null));
    }

    @Test
    void namesTheSourceStrategyForEachPrefix() {
        assertEquals("Profit Shield strategy", StrategyRecommendationMarkers.sourceLabel(strategy("PROFIT_SHIELD_RECOMMENDED")));
        assertEquals("Range Rider strategy", StrategyRecommendationMarkers.sourceLabel(strategy("RANGE_RIDER_MONITORING")));
        assertEquals("Earnings Hunter strategy", StrategyRecommendationMarkers.sourceLabel(strategy("EARNINGS_HUNTER_RECOMMENDED")));
        assertEquals("", StrategyRecommendationMarkers.sourceLabel(strategy("filled")));
    }

    @Test
    void suppressesBrokerPositionForProfitShieldPlanningRows() {
        assertTrue(GapRocketDisplaySupport.suppressBrokerPosition(strategy("PROFIT_SHIELD_RECOMMENDED")));
    }

    private static Strategy strategy(String latestOrderStatus) {
        Strategy strategy = new Strategy(
                UUID.randomUUID().toString(), "KO Strategy", "KO", StrategyMode.PAPER,
                StrategyStatus.ACTIVE, StrategyLifecycleState.CREATED,
                new BigDecimal("100.00"), 1, new BigDecimal("90.00"), 1, new BigDecimal("80.00"), 1,
                true, StopLossType.FIXED_PRICE, new BigDecimal("75.00"), new BigDecimal("1.00"),
                false, BigDecimal.ZERO, true, new BigDecimal("110.00"), new BigDecimal("1000.00"),
                false, false, ProfitHoldType.PERCENT_TRAILING, new BigDecimal("1.00"), new BigDecimal("1.00"),
                BigDecimal.ZERO, false, 10, new BigDecimal("1000.00"), 10, Instant.now(), Instant.now());
        strategy.setLatestOrderStatus(latestOrderStatus);
        return strategy;
    }
}
