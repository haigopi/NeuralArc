package com.neuralarc.model;

import com.neuralarc.util.Monetary;

import java.math.BigDecimal;

/**
 * Configuration for the "Auto Adjust Risk &amp; Stop Loss" feature: when enabled (and the strategy's
 * Stop Loss and Loss Buy Levels controls are on), NeuralArc adjusts the strategy's stop-loss and loss
 * buy levels by a fixed percentage per day, after market close, following the stock's movement over a
 * bounded number of trading days.
 *
 * <p>This record holds only the user-configured intent. The mutable per-day progress (how many days
 * have been adjusted, the last adjusted market date, the reference price) lives on {@link Strategy} as
 * runtime state, because it changes as the market moves rather than as the operator configures.
 *
 * <p>The compact constructor normalises null/negative inputs so persisted or legacy configs never
 * produce a broken adjuster.
 */
public record AutoAdjustRiskConfig(
        boolean enabled,
        int monitoringDays,
        BigDecimal dailyAdjustmentPercent,
        boolean applyAfterMarketClose,
        boolean adjustOnDecrease,
        boolean adjustOnIncrease
) {
    public AutoAdjustRiskConfig {
        if (monitoringDays < 0) {
            monitoringDays = 0;
        }
        dailyAdjustmentPercent = Monetary.round(
                dailyAdjustmentPercent == null || dailyAdjustmentPercent.signum() < 0
                        ? BigDecimal.ZERO
                        : dailyAdjustmentPercent);
    }

    /** A disabled, safe-default configuration used when the feature is off or a config is missing. */
    public static AutoAdjustRiskConfig disabled() {
        return new AutoAdjustRiskConfig(false, 0, BigDecimal.ZERO, true, true, true);
    }

    /**
     * Whether this configuration can actually drive an adjustment: it must be enabled, run after market
     * close, monitor at least one day, move at least some percent per day, and adjust in at least one
     * direction.
     */
    public boolean isActive() {
        return enabled
                && applyAfterMarketClose
                && monitoringDays > 0
                && dailyAdjustmentPercent.signum() > 0
                && (adjustOnDecrease || adjustOnIncrease);
    }
}
