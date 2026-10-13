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
 *
 * <p>An optional {@link ValueLimit} narrows the match to small holdings, e.g. "position value under
 * $10", so "sell every loser worth less than $10" is one rule.
 */
record ThresholdSellRule(Side side, Comparison comparison, Unit unit, BigDecimal value, ValueLimit valueLimit) {
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /** {@code ANY} is either side of break-even, for "every position making a profit or a loss". */
    enum Side { PROFIT, LOSS, ANY }

    /** What a {@link ValueLimit} measures: the whole holding, or the price of one share. */
    enum ValueBasis { POSITION_VALUE, SHARE_PRICE }

    /** Optional "only stocks valued under $N" cap, applied on top of the profit/loss test. */
    record ValueLimit(ValueBasis basis, BigDecimal amount) {
        ValueLimit {
            amount = amount == null ? BigDecimal.ZERO : amount.abs();
        }

        boolean allows(Position position) {
            BigDecimal measured = basis == ValueBasis.SHARE_PRICE ? position.getLastPrice() : position.marketValue();
            return measured != null && measured.compareTo(amount) < 0;
        }

        String describe() {
            return (basis == ValueBasis.SHARE_PRICE ? "share price" : "position value")
                    + " under $" + amount.stripTrailingZeros().toPlainString();
        }
    }

    enum Comparison { MORE_THAN, LESS_THAN }

    enum Unit { PERCENT, DOLLARS }

    ThresholdSellRule {
        value = value == null ? BigDecimal.ZERO : value.abs();
    }

    ThresholdSellRule(Side side, Comparison comparison, Unit unit, BigDecimal value) {
        this(side, comparison, unit, value, null);
    }

    boolean matches(Position position) {
        if (position == null || position.getTotalShares() <= 0) {
            return false;
        }
        BigDecimal pnl = position.unrealizedPnl();
        if (pnl.signum() == 0 || (side != Side.ANY && (pnl.signum() > 0) != (side == Side.PROFIT))) {
            return false;
        }
        if (valueLimit != null && !valueLimit.allows(position)) {
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
        String sideWord = switch (side) {
            case PROFIT -> "profit";
            case LOSS -> "loss";
            case ANY -> "profit or loss";
        };
        return sideWord + (comparison == Comparison.MORE_THAN ? " more than " : " less than ") + number
                + (valueLimit == null ? "" : " and " + valueLimit.describe());
    }
}
