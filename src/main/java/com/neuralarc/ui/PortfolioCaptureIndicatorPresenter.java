package com.neuralarc.ui;

import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Builds the "Liquidation Monitor Active" status line shown next to the Liquidate Portfolio button.
 *
 * <p>Every figure is derived from the same two inputs the monitor evaluates its target against: the
 * open (unrealized) P&amp;L of the positions a liquidation would sell, and the capital still at risk in
 * them. So the line predicts what the next monitoring tick will do, and an operator can verify the
 * arithmetic by eye. Earlier revisions mixed bases (the tab's realized-inclusive P&amp;L against an
 * eligible-subset progress figure), which produced readings such as "P&amp;L $-34.43 ... 100.00%".
 *
 * <p>Realized P&amp;L is deliberately absent: profit banked by closed trades cannot be captured again,
 * so it never moves the target.
 *
 * <p>The configured target is always restated in BOTH units: a percent target also shows the
 * expected P&amp;L in dollars, and an amount target also shows the equivalent percent of capital, so
 * the expected P&amp;L for the target is never hidden behind the chosen target type.
 */
final class PortfolioCaptureIndicatorPresenter {
    static final String SEPARATOR = " | ";
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private PortfolioCaptureIndicatorPresenter() {
    }

    /** The status line itself. Returns an empty string when the mode has no target to report on. */
    static String targetMonitoringText(PortfolioCaptureConfig config, BigDecimal contextPnl, BigDecimal contextCapital) {
        if (config == null || config.mode() != PortfolioCaptureMode.TARGET_MONITORING) {
            return "";
        }
        Figures figures = Figures.of(config, contextPnl, contextCapital);
        StringBuilder text = new StringBuilder("Armed");
        text.append(SEPARATOR).append("Open P&L ").append(signedMoney(figures.pnl));
        if (figures.capitalKnown()) {
            text.append(" (").append(signedPercent(figures.pnlPercent())).append(')');
        }
        text.append(SEPARATOR).append("Target ").append(figures.targetLabel());
        if (!figures.targetAmountKnown()) {
            return text.append(SEPARATOR).append(figures.unknownTargetReason()).toString();
        }
        text.append(SEPARATOR).append("Progress ").append(Monetary.round(figures.progressPercent())).append('%');
        text.append(SEPARATOR).append(figures.targetReached()
                ? "Target met"
                : "Need " + signedMoney(figures.remaining()));
        return text.toString();
    }

    /** Plain-text explanation of how the line's numbers relate, appended to the indicator tooltip. */
    static String targetMonitoringExplanation(PortfolioCaptureConfig config, BigDecimal contextPnl, BigDecimal contextCapital) {
        if (config == null || config.mode() != PortfolioCaptureMode.TARGET_MONITORING) {
            return "";
        }
        Figures figures = Figures.of(config, contextPnl, contextCapital);
        StringBuilder text = new StringBuilder();
        text.append("Open P&L is the unrealized profit of the positions this liquidation would sell — "
                + "the P&L it would actually realize. Profit already banked by closed trades is excluded, "
                + "because it cannot be captured again.");
        if (figures.capitalKnown()) {
            text.append(" Capital still at risk in those positions is $")
                    .append(Monetary.round(figures.capital)).append('.');
        }
        if (figures.targetAmountKnown()) {
            text.append(figures.percentTarget
                    ? " Target " + signedPercent(figures.target) + " of that capital is an expected P&L of "
                            + signedMoney(figures.targetAmount()) + "."
                    : " Target " + signedMoney(figures.target) + " is "
                            + (figures.capitalKnown() ? signedPercent(figures.targetPercent()) + " of that capital." : "an absolute P&L amount."));
            text.append(" Progress is open P&L measured against that expected P&L and is clamped to 0-100%, "
                    + "so it stays at 0% while the positions are at a loss.");
            text.append(figures.targetReached()
                    ? " The target is met; the monitor liquidates on its next evaluation tick."
                    : " Need is the additional P&L required before the portfolio is liquidated.");
        } else {
            text.append(' ').append(figures.unknownTargetReason())
                    .append(figures.percentTarget
                            ? ": a percent target only converts to an expected P&L once capital is allocated."
                            : ".");
        }
        return text.toString();
    }

    private static String signedMoney(BigDecimal value) {
        BigDecimal rounded = Monetary.round(value);
        int sign = rounded.signum();
        if (sign == 0) {
            return "$" + rounded;
        }
        return (sign < 0 ? "-$" : "+$") + rounded.abs();
    }

    private static String signedPercent(BigDecimal value) {
        BigDecimal rounded = Monetary.round(value);
        int sign = rounded.signum();
        if (sign == 0) {
            return rounded + "%";
        }
        return (sign < 0 ? "-" : "+") + rounded.abs() + "%";
    }

    /**
     * Derived figures for one render. {@code targetAmount} is null when a percent target cannot be
     * converted to dollars yet because no capital is allocated.
     */
    private record Figures(
            boolean percentTarget,
            BigDecimal target,
            BigDecimal pnl,
            BigDecimal capital
    ) {
        static Figures of(PortfolioCaptureConfig config, BigDecimal contextPnl, BigDecimal contextCapital) {
            return new Figures(
                    config.targetType() == PortfolioCaptureTargetType.PROFIT_PERCENT,
                    Monetary.round(config.targetValue()),
                    Monetary.round(contextPnl),
                    Monetary.round(contextCapital)
            );
        }

        boolean capitalKnown() {
            return capital.signum() > 0;
        }

        BigDecimal pnlPercent() {
            return capitalKnown() ? ratioPercent(pnl, capital) : Monetary.zero();
        }

        BigDecimal targetAmount() {
            if (!percentTarget) {
                return target;
            }
            return capitalKnown()
                    ? Monetary.round(capital.multiply(target).divide(ONE_HUNDRED, 6, RoundingMode.HALF_UP))
                    : null;
        }

        BigDecimal targetPercent() {
            return percentTarget ? target : ratioPercent(target, capital);
        }

        boolean targetAmountKnown() {
            BigDecimal amount = targetAmount();
            return amount != null && amount.signum() > 0;
        }

        String unknownTargetReason() {
            if (target.signum() <= 0) {
                return "No profit target configured";
            }
            return "Waiting for allocated capital";
        }

        BigDecimal progressPercent() {
            BigDecimal progress = ratioPercent(pnl, targetAmount());
            return progress.max(BigDecimal.ZERO).min(ONE_HUNDRED);
        }

        BigDecimal remaining() {
            return Monetary.round(targetAmount().subtract(pnl));
        }

        boolean targetReached() {
            return pnl.compareTo(targetAmount()) >= 0;
        }

        String targetLabel() {
            if (percentTarget) {
                BigDecimal amount = targetAmount();
                return amount == null
                        ? signedPercent(target)
                        : signedPercent(target) + " = " + signedMoney(amount);
            }
            return capitalKnown()
                    ? signedMoney(target) + " = " + signedPercent(targetPercent())
                    : signedMoney(target);
        }

        private static BigDecimal ratioPercent(BigDecimal numerator, BigDecimal denominator) {
            if (denominator == null || denominator.signum() == 0) {
                return Monetary.zero();
            }
            return numerator.multiply(ONE_HUNDRED).divide(denominator, 4, RoundingMode.HALF_UP);
        }
    }
}
