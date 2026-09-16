package com.neuralarc.analytics;

import com.neuralarc.model.MarketBar;
import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A plan to cut the loss on a position that is under water: buy a measured number of extra shares
 * low enough to pull the average cost down, then exit at a price the stock has actually traded at
 * recently.
 *
 * <p>Every number is anchored to the last month of daily bars rather than hope. The add is priced at
 * or below the market and never below the month's low, so it can fill. The exit is the median of the
 * month's daily highs — a level reached on half the sessions — so the plan asks for an ordinary move,
 * not a recovery to where the position was bought. When the maths needs more shares than the
 * operator's capital allows, the plan says how far the loss is cut rather than pretending.
 *
 * <p>Averaging down adds money to a position already losing; the plan reports the extra cost and the
 * new break-even so that trade-off is explicit. Pure and side-effect free.
 */
public final class LossRecoveryPlan {
    /** Beyond this multiple of the shares held, the plan is flagged as disproportionate. */
    private static final BigDecimal HEAVY_ADD_MULTIPLE = new BigDecimal("3");

    private LossRecoveryPlan() {
    }

    /**
     * @param feasible               whether the plan can be acted on
     * @param reason                 why not, when it cannot
     * @param addShares              extra shares to buy, 0 when the exit already clears the current cost
     * @param sessionsAtOrAboveExit  sessions in the window whose high reached the exit price
     */
    public record Plan(
            boolean feasible,
            String reason,
            int addShares,
            BigDecimal addLimitPrice,
            BigDecimal addCost,
            BigDecimal newAverageCost,
            BigDecimal exitPrice,
            BigDecimal lossNow,
            BigDecimal resultAtExit,
            int sessionsAtOrAboveExit,
            int sessionsAnalyzed,
            BigDecimal monthLow,
            BigDecimal monthHigh,
            List<String> warnings
    ) {
        public Plan {
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }

        /** True when the plan still leaves a loss at the exit price, having only reduced it. */
        public boolean partialRecovery() {
            return feasible && resultAtExit != null && resultAtExit.signum() < 0;
        }

        static Plan notPossible(String reason) {
            return new Plan(false, reason, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, List.of());
        }
    }

    /**
     * @param month                 daily bars for roughly the last month; more is fine, empty is not
     * @param maxAdditionalCapital  cap on what may be spent adding, or zero/null for no cap
     */
    public static Plan forPosition(
            int shares,
            BigDecimal averageCost,
            BigDecimal currentPrice,
            List<MarketBar> month,
            BigDecimal maxAdditionalCapital
    ) {
        return forPosition(shares, averageCost, currentPrice, month, maxAdditionalCapital, BigDecimal.ZERO);
    }

    /**
     * @param todaysLow the low the stock has traded at today, or zero when the session has no bar yet.
     *                  The add is priced there when known: it is a price the market has actually paid
     *                  today, so the limit can fill, rather than a discount guessed off the last print.
     */
    public static Plan forPosition(
            int shares,
            BigDecimal averageCost,
            BigDecimal currentPrice,
            List<MarketBar> month,
            BigDecimal maxAdditionalCapital,
            BigDecimal todaysLow
    ) {
        if (shares <= 0 || !positive(averageCost) || !positive(currentPrice)) {
            return Plan.notPossible("This row has no open position with a known cost and price to work with.");
        }
        List<MarketBar> bars = month == null ? List.of() : month.stream()
                .filter(bar -> bar != null && positive(bar.high()) && positive(bar.low()))
                .toList();
        if (bars.isEmpty()) {
            return Plan.notPossible("Alpaca returned no daily history for this symbol, so there is nothing to plan against.");
        }

        BigDecimal lossNow = Monetary.round(currentPrice.subtract(averageCost).multiply(BigDecimal.valueOf(shares)));
        if (lossNow.signum() >= 0) {
            return Plan.notPossible("This position is not at a loss, so there is nothing to recover.");
        }

        BigDecimal monthLow = bars.stream().map(MarketBar::low).min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        BigDecimal monthHigh = bars.stream().map(MarketBar::high).max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        BigDecimal addLimitPrice = positive(todaysLow)
                ? PlannedEntryPrice.notAboveMarket(todaysLow, currentPrice)
                : PlannedEntryPrice.atOrBelowMarket(currentPrice, monthLow, PlannedEntryPrice.DEFAULT_DISCOUNT_PERCENT);
        BigDecimal exitPrice = medianHigh(bars);
        if (!positive(addLimitPrice) || !positive(exitPrice)) {
            return Plan.notPossible("The last month of prices is incomplete, so no honest plan can be built.");
        }
        if (exitPrice.compareTo(addLimitPrice) <= 0) {
            return Plan.notPossible("This stock has not traded above " + Monetary.round(addLimitPrice).toPlainString()
                    + " often enough this month for an exit to be realistic. Averaging down here would only add risk.");
        }

        int requiredShares = requiredShares(shares, averageCost, addLimitPrice, exitPrice);
        int addShares = requiredShares;
        List<String> warnings = new ArrayList<>();
        if (positive(maxAdditionalCapital)) {
            int affordable = maxAdditionalCapital.divide(addLimitPrice, 0, RoundingMode.DOWN).intValue();
            if (affordable < requiredShares) {
                addShares = Math.max(0, affordable);
                warnings.add("Breaking even at the exit price needs " + requiredShares + " shares; the capital allowed"
                        + " covers " + addShares + ". The plan below cuts the loss rather than clearing it.");
            }
        }

        BigDecimal addCost = Monetary.round(addLimitPrice.multiply(BigDecimal.valueOf(addShares)));
        int totalShares = shares + addShares;
        BigDecimal newAverageCost = Monetary.round(
                averageCost.multiply(BigDecimal.valueOf(shares))
                        .add(addLimitPrice.multiply(BigDecimal.valueOf(addShares)))
                        .divide(BigDecimal.valueOf(totalShares), 6, RoundingMode.HALF_UP));
        BigDecimal resultAtExit = Monetary.round(
                exitPrice.subtract(newAverageCost).multiply(BigDecimal.valueOf(totalShares)));
        int sessionsAtOrAboveExit = (int) bars.stream()
                .filter(bar -> bar.high().compareTo(exitPrice) >= 0)
                .count();

        if (addShares == 0) {
            warnings.add("No extra shares are needed: the exit price already clears the cost of the shares held.");
        }
        if (addShares > 0 && BigDecimal.valueOf(addShares)
                .compareTo(BigDecimal.valueOf(shares).multiply(HEAVY_ADD_MULTIPLE)) > 0) {
            warnings.add("This plan needs more than three times the shares already held. That is a large bet on one "
                    + "symbol recovering — consider booking the loss instead.");
        }
        if (addShares > 0) {
            warnings.add("Averaging down commits " + Monetary.round(addCost).toPlainString()
                    + " more to a position that is already losing. If the price keeps falling, the larger position loses faster.");
        }

        return new Plan(true, "", addShares, Monetary.round(addLimitPrice), addCost, newAverageCost,
                Monetary.round(exitPrice), lossNow, resultAtExit, sessionsAtOrAboveExit, bars.size(),
                Monetary.round(monthLow), Monetary.round(monthHigh), warnings);
    }

    /** Extra shares needed for the blended cost to reach the exit price; 0 when it is already there. */
    private static int requiredShares(int shares, BigDecimal averageCost, BigDecimal addLimitPrice, BigDecimal exitPrice) {
        if (averageCost.compareTo(exitPrice) <= 0) {
            return 0;
        }
        BigDecimal numerator = averageCost.subtract(exitPrice).multiply(BigDecimal.valueOf(shares));
        BigDecimal denominator = exitPrice.subtract(addLimitPrice);
        return numerator.divide(denominator, 0, RoundingMode.CEILING).intValue();
    }

    /** The middle of the month's daily highs: a level the stock reached on about half the sessions. */
    private static BigDecimal medianHigh(List<MarketBar> bars) {
        List<BigDecimal> highs = new ArrayList<>(bars.stream().map(MarketBar::high).toList());
        highs.sort(Comparator.naturalOrder());
        return highs.get(highs.size() / 2);
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }
}
