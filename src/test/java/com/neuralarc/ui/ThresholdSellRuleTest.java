package com.neuralarc.ui;

import com.neuralarc.model.Position;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.neuralarc.ui.ThresholdSellRule.Comparison.LESS_THAN;
import static com.neuralarc.ui.ThresholdSellRule.Comparison.MORE_THAN;
import static com.neuralarc.ui.ThresholdSellRule.Side.LOSS;
import static com.neuralarc.ui.ThresholdSellRule.Side.PROFIT;
import static com.neuralarc.ui.ThresholdSellRule.Unit.DOLLARS;
import static com.neuralarc.ui.ThresholdSellRule.Unit.PERCENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThresholdSellRuleTest {
    @Test
    void profitMoreThanPicksOnlyGainsBeyondTheNumber() {
        ThresholdSellRule rule = rule(PROFIT, MORE_THAN, PERCENT, "5");

        assertTrue(rule.matches(held(10, "10", "10.60")), "+6%");
        assertFalse(rule.matches(held(10, "10", "10.30")), "+3%");
        assertFalse(rule.matches(held(10, "10", "9")), "a loss is never a profit");
    }

    @Test
    void profitLessThanPicksSmallGainsButNeverLosses() {
        ThresholdSellRule rule = rule(PROFIT, LESS_THAN, PERCENT, "5");

        assertTrue(rule.matches(held(10, "10", "10.30")), "+3%");
        assertFalse(rule.matches(held(10, "10", "10.60")), "+6%");
        assertFalse(rule.matches(held(10, "10", "9.90")), "a small loss is not a small profit");
    }

    @Test
    void lossMoreThanPicksTheDeeperLossesAndLossLessThanTheShallowOnes() {
        ThresholdSellRule deeper = rule(LOSS, MORE_THAN, PERCENT, "5");
        ThresholdSellRule shallower = rule(LOSS, LESS_THAN, PERCENT, "5");
        Position down8 = held(10, "10", "9.20");
        Position down2 = held(10, "10", "9.80");

        assertTrue(deeper.matches(down8));
        assertFalse(deeper.matches(down2));
        assertTrue(shallower.matches(down2));
        assertFalse(shallower.matches(down8));
        assertFalse(shallower.matches(held(10, "10", "11")), "a gain is not a loss");
    }

    @Test
    void dollarRulesUseTheWholePositionsPnlNotThePercent() {
        ThresholdSellRule rule = rule(PROFIT, MORE_THAN, DOLLARS, "50");

        assertTrue(rule.matches(held(100, "10", "10.60")), "+$60");
        assertFalse(rule.matches(held(10, "10", "10.60")), "+6% but only +$6 on ten shares");
    }

    @Test
    void aPositionExactlyOnTheNumberOrAtBreakEvenIsLeftOut() {
        assertFalse(rule(PROFIT, MORE_THAN, PERCENT, "5").matches(held(10, "10", "10.50")), "exactly +5%");
        assertFalse(rule(PROFIT, LESS_THAN, PERCENT, "5").matches(held(10, "10", "10.50")), "exactly +5%");
        assertFalse(rule(PROFIT, MORE_THAN, PERCENT, "0").matches(held(10, "10", "10")), "break-even is neither");
        assertFalse(rule(LOSS, MORE_THAN, PERCENT, "0").matches(held(10, "10", "10")), "break-even is neither");
    }

    @Test
    void zeroWithMoreThanMeansEveryWinnerOrEveryLoser() {
        assertTrue(rule(PROFIT, MORE_THAN, PERCENT, "0").matches(held(10, "10", "10.01")));
        assertTrue(rule(LOSS, MORE_THAN, PERCENT, "0").matches(held(10, "10", "9.99")));
    }

    @Test
    void anEmptyPositionNeverMatches() {
        assertFalse(rule(PROFIT, MORE_THAN, PERCENT, "0").matches(new Position("AAPL")));
        assertFalse(rule(PROFIT, MORE_THAN, PERCENT, "0").matches(null));
    }

    @Test
    void theValueIsAlwaysAMagnitude() {
        assertEquals(new BigDecimal("5"), rule(LOSS, MORE_THAN, PERCENT, "-5").value());
    }

    @Test
    void describesTheRuleInTheOperatorsWords() {
        assertEquals("loss more than 5%", rule(LOSS, MORE_THAN, PERCENT, "5.0").describe());
        assertEquals("profit less than $50", rule(PROFIT, LESS_THAN, DOLLARS, "50").describe());
    }

    @Test
    void anySideMatchesEveryPositionNotAtBreakEven() {
        ThresholdSellRule rule = rule(ThresholdSellRule.Side.ANY, MORE_THAN, PERCENT, "0");

        assertTrue(rule.matches(held(10, "10", "10.01")));
        assertTrue(rule.matches(held(10, "10", "9.99")));
        assertFalse(rule.matches(held(10, "10", "10")));
        assertEquals("profit or loss more than 0%", rule.describe());
    }

    @Test
    void positionValueCapKeepsOnlySmallHoldings() {
        ThresholdSellRule losers = capped(LOSS, ThresholdSellRule.ValueBasis.POSITION_VALUE, "10");

        assertTrue(losers.matches(held(2, "5", "4")), "$8 position, down");
        assertFalse(losers.matches(held(10, "5", "4")), "$40 position, down");
        assertFalse(losers.matches(held(2, "3", "4")), "$8 position but a winner");
        assertFalse(losers.matches(held(2, "6", "5")), "exactly $10 is not under $10");
        assertEquals("loss more than 0% and position value under $10", losers.describe());
    }

    @Test
    void sharePriceCapLooksAtOneShareNotTheHolding() {
        ThresholdSellRule winners = capped(PROFIT, ThresholdSellRule.ValueBasis.SHARE_PRICE, "10");

        assertTrue(winners.matches(held(100, "4", "5")), "$5 share, $500 holding");
        assertFalse(winners.matches(held(1, "11", "12")), "$12 share");
    }

    private static ThresholdSellRule capped(ThresholdSellRule.Side side, ThresholdSellRule.ValueBasis basis, String cap) {
        return new ThresholdSellRule(side, MORE_THAN, PERCENT, BigDecimal.ZERO,
                new ThresholdSellRule.ValueLimit(basis, new BigDecimal(cap)));
    }

    private static ThresholdSellRule rule(ThresholdSellRule.Side side, ThresholdSellRule.Comparison comparison,
                                          ThresholdSellRule.Unit unit, String value) {
        return new ThresholdSellRule(side, comparison, unit, new BigDecimal(value));
    }

    private static Position held(int shares, String averageCost, String lastPrice) {
        Position position = new Position("AAPL");
        position.applyBuy(shares, new BigDecimal(averageCost));
        position.setLastPrice(new BigDecimal(lastPrice));
        return position;
    }
}
