package com.neuralarc.service;

import com.neuralarc.model.AutoAdjustRiskConfig;
import com.neuralarc.model.ProfitControlMode;
import com.neuralarc.model.ProfitHoldType;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyConfig;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.ThresholdType;
import com.neuralarc.model.TimeInForce;
import com.neuralarc.model.TrailingType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AutoRiskAdjustmentEngineTest {
    private static final LocalDate DAY1 = LocalDate.of(2026, 6, 15);
    private static final LocalDate DAY2 = LocalDate.of(2026, 6, 16);

    /** Builds a strategy bought at 400 with a 365 stop and 380/370 loss buy levels. */
    private Strategy strategy(boolean stopLossEnabled, AutoAdjustRiskConfig autoAdjust) {
        StrategyConfig config = new StrategyConfig(
                "TEST", new BigDecimal("400"), 10, stopLossEnabled, new BigDecimal("365"),
                false, BigDecimal.ZERO,
                new BigDecimal("380"), 5, new BigDecimal("370"), 5, true,
                false, BigDecimal.ZERO, 10, true, false, false,
                ProfitHoldType.PERCENT_TRAILING, BigDecimal.ZERO, BigDecimal.ZERO, false,
                ProfitControlMode.NONE, ThresholdType.FIXED_AMOUNT, BigDecimal.ZERO,
                TrailingType.PERCENTAGE, BigDecimal.ZERO, false,
                StrategyConfig.DEFAULT_BASE_BUY_REPOST_REDUCTION_PERCENT, TimeInForce.DAY, autoAdjust);
        return Strategy.fromConfig("s1", "Test", config, StrategyMode.PAPER);
    }

    private static AutoAdjustRiskConfig active(int days, String percent, boolean dec, boolean inc) {
        return new AutoAdjustRiskConfig(true, days, new BigDecimal(percent), true, dec, inc);
    }

    @Test
    void reducesStopAndBuyLevelsOnDownwardTrend() {
        Strategy s = strategy(true, active(3, "5", true, true));
        Optional<AutoRiskAdjustment> result = AutoRiskAdjustmentEngine.evaluate(s, new BigDecimal("385"), DAY1);

        assertTrue(result.isPresent());
        AutoRiskAdjustment a = result.get();
        assertEquals(AutoRiskAdjustment.Direction.DECREASE, a.direction());
        assertEquals(new BigDecimal("346.75"), a.newStopLossPrice());   // 365 * 0.95
        assertEquals(new BigDecimal("361.00"), a.newBuyLimit1Price());  // 380 * 0.95
        assertEquals(new BigDecimal("351.50"), a.newBuyLimit2Price());  // 370 * 0.95
        assertEquals(1, a.newDayCount());
        assertEquals("2026-06-15", a.marketDate());
        assertEquals(new BigDecimal("385.00"), a.newReferencePrice());
    }

    @Test
    void raisesStopAndBuyLevelsOnUpwardTrend() {
        Strategy s = strategy(true, active(3, "5", true, true));
        Optional<AutoRiskAdjustment> result = AutoRiskAdjustmentEngine.evaluate(s, new BigDecimal("420"), DAY1);

        assertTrue(result.isPresent());
        AutoRiskAdjustment a = result.get();
        assertEquals(AutoRiskAdjustment.Direction.INCREASE, a.direction());
        assertEquals(new BigDecimal("383.25"), a.newStopLossPrice());   // 365 * 1.05, still below price
        assertEquals(new BigDecimal("399.00"), a.newBuyLimit1Price());  // 380 * 1.05
        assertTrue(a.newStopLossPrice().compareTo(new BigDecimal("420")) < 0);
    }

    @Test
    void respectsDirectionToggles() {
        // Only adjust on decrease: an up day produces no value change (direction NONE) but still advances.
        Strategy s = strategy(true, active(3, "5", true, false));
        AutoRiskAdjustment a = AutoRiskAdjustmentEngine.evaluate(s, new BigDecimal("420"), DAY1).orElseThrow();
        assertEquals(AutoRiskAdjustment.Direction.NONE, a.direction());
        assertEquals(s.stopLossPrice(), a.newStopLossPrice());
        assertEquals(1, a.newDayCount());
    }

    @Test
    void neverLetsStopCrossAboveOrToCurrentPrice() {
        // Stop already near price; an upward adjustment must be clamped strictly below price.
        Strategy s = strategy(true, active(3, "5", true, true));
        s.setStopLossPrice(new BigDecimal("410"));
        AutoRiskAdjustment a = AutoRiskAdjustmentEngine.evaluate(s, new BigDecimal("415"), DAY1).orElseThrow();
        assertEquals(new BigDecimal("410.85"), a.newStopLossPrice()); // clamped to 415 * 0.99
        assertTrue(a.newStopLossPrice().compareTo(new BigDecimal("415")) < 0);
        assertTrue(a.newStopLossPrice().signum() > 0);
    }

    @Test
    void doesNotAdjustTwiceOnSameMarketDay() {
        Strategy s = strategy(true, active(3, "5", true, true));
        s.setAutoAdjustLastAdjustedDate("2026-06-15");
        assertTrue(AutoRiskAdjustmentEngine.evaluate(s, new BigDecimal("385"), DAY1).isEmpty());
        // A different day is allowed again.
        assertTrue(AutoRiskAdjustmentEngine.evaluate(s, new BigDecimal("385"), DAY2).isPresent());
    }

    @Test
    void stopsAfterConfiguredNumberOfDays() {
        Strategy s = strategy(true, active(2, "5", true, true));
        s.setAutoAdjustDayCount(2); // already monitored the configured 2 days
        assertTrue(AutoRiskAdjustmentEngine.evaluate(s, new BigDecimal("385"), DAY1).isEmpty());
    }

    @Test
    void adjustsFromCurrentValueSoManualChangesAreNotReverted() {
        Strategy s = strategy(true, active(3, "5", true, true));
        // Operator manually moved the stop and the engine already has a reference from a prior day.
        s.setStopLossPrice(new BigDecimal("300"));
        s.setAutoAdjustReferencePrice(new BigDecimal("390"));
        s.setAutoAdjustLastAdjustedDate("2026-06-14");
        AutoRiskAdjustment a = AutoRiskAdjustmentEngine.evaluate(s, new BigDecimal("380"), DAY1).orElseThrow();
        // 380 < reference 390 -> decrease from the manual 300, not from the original 365.
        assertEquals(new BigDecimal("285.00"), a.newStopLossPrice());
    }

    @Test
    void inactiveWhenFeatureDisabled() {
        Strategy s = strategy(true, AutoAdjustRiskConfig.disabled());
        assertTrue(AutoRiskAdjustmentEngine.evaluate(s, new BigDecimal("385"), DAY1).isEmpty());
    }

    @Test
    void inactiveWhenStopLossDisabled() {
        // Feature requested, but Stop Loss is off — the section is not applicable.
        Strategy s = strategy(false, active(3, "5", true, true));
        assertTrue(AutoRiskAdjustmentEngine.evaluate(s, new BigDecimal("385"), DAY1).isEmpty());
    }
}
