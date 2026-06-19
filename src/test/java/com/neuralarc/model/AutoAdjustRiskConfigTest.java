package com.neuralarc.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class AutoAdjustRiskConfigTest {
    @Test
    void disabledIsNotActive() {
        assertFalse(AutoAdjustRiskConfig.disabled().isActive());
        assertFalse(AutoAdjustRiskConfig.disabled().enabled());
    }

    @Test
    void normalizesNegativeAndNullInputs() {
        AutoAdjustRiskConfig config = new AutoAdjustRiskConfig(true, -3, null, true, true, false);
        assertEquals(0, config.monitoringDays());
        assertEquals(new BigDecimal("0.00"), config.dailyAdjustmentPercent());
    }

    @Test
    void activeRequiresEnabledDaysPercentAndADirection() {
        assertTrue(new AutoAdjustRiskConfig(true, 3, new BigDecimal("5"), true, true, true).isActive());
        // No direction selected.
        assertFalse(new AutoAdjustRiskConfig(true, 3, new BigDecimal("5"), true, false, false).isActive());
        // Zero days.
        assertFalse(new AutoAdjustRiskConfig(true, 0, new BigDecimal("5"), true, true, true).isActive());
        // Zero percent.
        assertFalse(new AutoAdjustRiskConfig(true, 3, BigDecimal.ZERO, true, true, true).isActive());
        // Not after market close (the feature runs only after close).
        assertFalse(new AutoAdjustRiskConfig(true, 3, new BigDecimal("5"), false, true, true).isActive());
    }
}
