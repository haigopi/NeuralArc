package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepeatedLogFilterTest {
    @Test
    void aRepeatOfTheLastMessageForAKeyIsDropped() {
        RepeatedLogFilter filter = new RepeatedLogFilter();

        assertTrue(filter.shouldLog("portfolio-refresh", "GOOG failed"));
        assertFalse(filter.shouldLog("portfolio-refresh", "GOOG failed"));
    }

    @Test
    void aChangedMessageLogsAgain() {
        RepeatedLogFilter filter = new RepeatedLogFilter();
        filter.shouldLog("portfolio-refresh", "GOOG failed");

        assertTrue(filter.shouldLog("portfolio-refresh", "GOOG failed | TGT failed"));
    }

    @Test
    void keysAreIndependent() {
        RepeatedLogFilter filter = new RepeatedLogFilter();
        filter.shouldLog("restore", "GOOG failed");

        assertTrue(filter.shouldLog("portfolio-refresh", "GOOG failed"));
    }

    @Test
    void aClearedConditionIsNewsAgainWhenItReturns() {
        RepeatedLogFilter filter = new RepeatedLogFilter();
        filter.shouldLog("portfolio-refresh", "GOOG failed");
        filter.clear("portfolio-refresh");

        assertTrue(filter.shouldLog("portfolio-refresh", "GOOG failed"));
    }
}
