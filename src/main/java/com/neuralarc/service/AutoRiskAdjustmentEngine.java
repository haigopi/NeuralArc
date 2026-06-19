package com.neuralarc.service;

import com.neuralarc.model.Strategy;
import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Pure decision logic for the Auto Adjust Risk &amp; Stop Loss feature. Given a strategy, the current
 * price, and the market date, it decides whether (and how) to nudge the stop-loss and loss buy levels
 * up or down by the configured percentage for the day.
 *
 * <p>This class performs no I/O and holds no state, so it is fully unit-testable. {@link
 * AutoRiskAdjustmentService} owns the after-close scheduling, price lookup, and persistence around it.
 *
 * <p>Safety rules enforced here:
 * <ul>
 *   <li>Runs only when the feature is active and Stop Loss is enabled on the strategy.</li>
 *   <li>Adjusts at most once per market day (guards on the last adjusted date).</li>
 *   <li>Stops after the configured number of monitoring days is reached.</li>
 *   <li>Keeps the stop-loss strictly positive and strictly below the current price, so it never
 *       becomes an invalid or unsafe (above-market) protective stop.</li>
 *   <li>Reads the strategy's <em>current</em> stop/levels, so a manual edit between runs is respected
 *       rather than reverted.</li>
 * </ul>
 */
public final class AutoRiskAdjustmentEngine {

    private AutoRiskAdjustmentEngine() {
    }

    public static Optional<AutoRiskAdjustment> evaluate(Strategy strategy, BigDecimal currentPrice, LocalDate marketDate) {
        if (strategy == null || marketDate == null) {
            return Optional.empty();
        }
        if (!strategy.autoAdjustRiskConfig().isActive()) {
            return Optional.empty();
        }
        // Stop Loss must be enabled — the feature adjusts the stop-loss and its loss buy ladder.
        if (!strategy.automatedStopLossEnabled()) {
            return Optional.empty();
        }
        // Stop after the configured number of monitoring days.
        if (strategy.autoAdjustDayCount() >= strategy.autoAdjustMonitoringDays()) {
            return Optional.empty();
        }
        // Never adjust the same stock twice on the same market day.
        if (marketDate.toString().equals(strategy.autoAdjustLastAdjustedDate())) {
            return Optional.empty();
        }
        if (currentPrice == null || currentPrice.signum() <= 0) {
            return Optional.empty();
        }

        BigDecimal reference = referencePrice(strategy, currentPrice);
        int comparison = currentPrice.compareTo(reference);
        BigDecimal percent = strategy.autoAdjustDailyPercent();
        BigDecimal factor;
        AutoRiskAdjustment.Direction direction;
        if (comparison < 0 && strategy.autoAdjustOnDecrease()) {
            factor = BigDecimal.ONE.subtract(percent.movePointLeft(2));
            direction = AutoRiskAdjustment.Direction.DECREASE;
        } else if (comparison > 0 && strategy.autoAdjustOnIncrease()) {
            factor = BigDecimal.ONE.add(percent.movePointLeft(2));
            direction = AutoRiskAdjustment.Direction.INCREASE;
        } else {
            factor = BigDecimal.ONE;
            direction = AutoRiskAdjustment.Direction.NONE;
        }

        BigDecimal newStop = strategy.stopLossPrice();
        BigDecimal newBuy1 = strategy.buyLimit1Price();
        BigDecimal newBuy2 = strategy.buyLimit2Price();
        if (direction != AutoRiskAdjustment.Direction.NONE) {
            newStop = safeStop(strategy.stopLossPrice().multiply(factor), currentPrice, strategy.stopLossPrice());
            newBuy1 = scaleLevel(strategy.buyLimit1Price(), factor);
            newBuy2 = scaleLevel(strategy.buyLimit2Price(), factor);
        }

        String description = "Auto Adjust (" + direction + "): day " + (strategy.autoAdjustDayCount() + 1)
                + "/" + strategy.autoAdjustMonitoringDays()
                + ", price=$" + Monetary.round(currentPrice).toPlainString()
                + ", stop $" + Monetary.round(strategy.stopLossPrice()).toPlainString()
                + " -> $" + newStop.toPlainString() + ".";

        return Optional.of(new AutoRiskAdjustment(
                newStop,
                newBuy1,
                newBuy2,
                strategy.autoAdjustDayCount() + 1,
                marketDate.toString(),
                Monetary.round(currentPrice),
                direction,
                description));
    }

    private static BigDecimal referencePrice(Strategy strategy, BigDecimal currentPrice) {
        if (strategy.autoAdjustReferencePrice() != null && strategy.autoAdjustReferencePrice().signum() > 0) {
            return strategy.autoAdjustReferencePrice();
        }
        if (strategy.baseBuyLimitPrice() != null && strategy.baseBuyLimitPrice().signum() > 0) {
            return strategy.baseBuyLimitPrice();
        }
        return currentPrice;
    }

    /**
     * Keep the stop-loss valid: strictly positive and strictly below the current price (a protective
     * stop above the market makes no sense). If the proposed stop cannot satisfy both, keep the prior
     * stop rather than persisting an unsafe value.
     */
    private static BigDecimal safeStop(BigDecimal proposed, BigDecimal currentPrice, BigDecimal previousStop) {
        BigDecimal rounded = Monetary.round(proposed);
        BigDecimal ceiling = Monetary.round(currentPrice.multiply(new BigDecimal("0.99")));
        if (ceiling.signum() <= 0) {
            return previousStop;
        }
        if (rounded.compareTo(ceiling) > 0) {
            rounded = ceiling;
        }
        if (rounded.signum() <= 0) {
            return previousStop;
        }
        return rounded;
    }

    /** Scale a loss buy level by the factor; only touches levels that are actually configured (&gt; 0). */
    private static BigDecimal scaleLevel(BigDecimal level, BigDecimal factor) {
        if (level == null || level.signum() <= 0) {
            return level == null ? BigDecimal.ZERO : level;
        }
        BigDecimal scaled = Monetary.round(level.multiply(factor));
        return scaled.signum() <= 0 ? level : scaled;
    }
}
