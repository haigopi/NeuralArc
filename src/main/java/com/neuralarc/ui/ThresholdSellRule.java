package com.neuralarc.ui;

import com.neuralarc.model.Position;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The operator's profit/loss cut-off for the threshold sell: which side of break-even a position
 * sits on, whether it must be beyond or short of the number, and whether the number is a percent of
 * cost or dollars of unrealized P&amp;L.
 *
 * <p>The number is always a magnitude, so "Loss more than 5%" means down by more than 5% and "Profit
 * less than 5%" means up, but by under 5%. A position exactly at break-even is neither profit nor
 * loss and never matches, and the comparison is strict, so a position sitting exactly on the number
 * is left out.
 */
record ThresholdSellRule(Side side, Comparison comparison, Unit unit, BigDecimal value) {
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    enum Side { PROFIT, LOSS }

    enum Comparison { MORE_THAN, LESS_THAN }

    enum Unit { PERCENT, DOLLARS }

    ThresholdSellRule {
        value = value == null ? BigDecimal.ZERO : value.abs();
    }

    boolean matches(Position position) {
        if (position == null || position.getTotalShares() <= 0) {
            return false;
        }
        BigDecimal pnl = position.unrealizedPnl();
        if (pnl.signum() == 0 || (pnl.signum() > 0) != (side == Side.PROFIT)) {
            return false;
        }
        int against = magnitude(position, pnl).compareTo(value);
        return comparison == Comparison.MORE_THAN ? against > 0 : against < 0;
    }

    /** How far the position is from break-even, in the rule's unit. */
    private BigDecimal magnitude(Position position, BigDecimal pnl) {
        if (unit == Unit.DOLLARS) {
            return pnl.abs();
        }
        BigDecimal invested = position.totalInvested();
        if (invested == null || invested.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return pnl.abs().multiply(HUNDRED).divide(invested, 4, RoundingMode.HALF_UP);
    }

    /** The rule in the operator's words, e.g. "loss more than 5%" or "profit less than $50". */
    String describe() {
        String number = unit == Unit.PERCENT
                ? value.stripTrailingZeros().toPlainString() + "%"
                : "$" + value.stripTrailingZeros().toPlainString();
        return (side == Side.PROFIT ? "profit" : "loss")
                + (comparison == Comparison.MORE_THAN ? " more than " : " less than ")
                + number;
    }
}
