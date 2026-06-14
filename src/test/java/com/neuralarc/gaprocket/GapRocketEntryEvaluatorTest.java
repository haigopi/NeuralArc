package com.neuralarc.gaprocket;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

class GapRocketEntryEvaluatorTest {
    private final GapRocketEntryEvaluator evaluator = new GapRocketEntryEvaluator();

    @Test
    void openingRangeLogicDoesNotAutoBuyUnlessEnabled() {
        GapRocketStatus status = evaluator.evaluate(GapRocketStatus.WAITING_FOR_BREAKOUT, GapRocketConfig.EntryStyle.OPENING_RANGE_BREAKOUT,
                LocalTime.of(9, 46), LocalTime.of(9, 30), 15, new BigDecimal("125"), new BigDecimal("120"),
                new BigDecimal("126"), null, true, false);
        assertEquals(GapRocketStatus.READY_TO_BUY, status);
    }

    @Test
    void breakoutRetestMarksReadyAfterPullbackNearOpeningRangeHigh() {
        GapRocketStatus breakout = evaluator.evaluate(GapRocketStatus.WAITING_FOR_BREAKOUT, GapRocketConfig.EntryStyle.BREAKOUT_RETEST,
                LocalTime.of(9, 50), LocalTime.of(9, 30), 15, new BigDecimal("125"), new BigDecimal("120"),
                new BigDecimal("127"), null, true, false);
        assertEquals(GapRocketStatus.WAITING_FOR_PULLBACK, breakout);
        GapRocketStatus retest = evaluator.evaluate(breakout, GapRocketConfig.EntryStyle.BREAKOUT_RETEST,
                LocalTime.of(9, 52), LocalTime.of(9, 30), 15, new BigDecimal("125"), new BigDecimal("120"),
                new BigDecimal("125.50"), null, true, false);
        assertEquals(GapRocketStatus.READY_TO_BUY, retest);
    }

    @Test
    void vwapPullbackLogicWorksAndOverSellIsPrevented() {
        GapRocketStatus status = evaluator.evaluate(GapRocketStatus.WAITING_FOR_PULLBACK, GapRocketConfig.EntryStyle.PULLBACK_TO_VWAP,
                LocalTime.of(10, 0), LocalTime.of(9, 30), 15, new BigDecimal("50"), new BigDecimal("48"),
                new BigDecimal("49.20"), new BigDecimal("49"), true, false);
        assertEquals(GapRocketStatus.READY_TO_BUY, status);
        assertFalse(evaluator.canSell(11, 10));
        assertTrue(evaluator.canSell(10, 10));
    }
}
