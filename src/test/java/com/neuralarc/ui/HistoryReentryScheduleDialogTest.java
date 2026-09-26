package com.neuralarc.ui;

import com.neuralarc.model.HistoryReentrySchedule;
import com.neuralarc.model.StrategyMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryReentryScheduleDialogTest {
    @Test
    void theScheduleIsOffUntilTheOperatorTurnsItOn() {
        HistoryReentryScheduleDialog dialog = new HistoryReentryScheduleDialog(null, StrategyMode.PAPER, null);

        assertFalse(dialog.enabledBox().isSelected());
        assertFalse(dialog.current().enabled());
        assertEquals(HistoryReentrySchedule.Group.GAINS, dialog.current().group(),
                "the safer half of the history is the default");
        assertEquals(HistoryReentrySchedule.DEFAULT_MAX_STOCKS, dialog.current().maxStocks());
        dialog.dispose();
    }

    @Test
    void anExistingScheduleOpensAsItWasSavedAndKeepsItsLastRunDate() {
        HistoryReentrySchedule saved = new HistoryReentrySchedule("s1", true, StrategyMode.LIVE, DayOfWeek.THURSDAY,
                LocalTime.of(11, 0), HistoryReentrySchedule.Cadence.BIWEEKLY, HistoryReentrySchedule.Group.ALL,
                5, LocalDate.of(2026, 9, 10));

        HistoryReentryScheduleDialog dialog = new HistoryReentryScheduleDialog(null, StrategyMode.LIVE, saved);

        assertEquals(saved, dialog.current(), "reopening and saving unchanged must not reset the cadence clock");
        dialog.dispose();
    }

    @Test
    void theSummaryReadsAsASentenceTheOperatorCanCheck() {
        HistoryReentrySchedule schedule = new HistoryReentrySchedule(null, true, StrategyMode.PAPER, DayOfWeek.MONDAY,
                LocalTime.of(10, 0), HistoryReentrySchedule.Cadence.BIWEEKLY, HistoryReentrySchedule.Group.GAINS,
                10, null);

        assertEquals("Every 2 weeks · Mon 10:00 ET · up to 10 stocks that closed in profit", schedule.summary());
    }

    @Test
    void theGroupFilterSplitsTheHistoryTheSameWayThePickerDoes() {
        assertTrue(HistoryReentrySchedule.Group.GAINS.accepts(new BigDecimal("40")));
        assertFalse(HistoryReentrySchedule.Group.GAINS.accepts(new BigDecimal("-1")));
        assertTrue(HistoryReentrySchedule.Group.LOSSES.accepts(new BigDecimal("-1")));
        assertFalse(HistoryReentrySchedule.Group.LOSSES.accepts(BigDecimal.ZERO), "break-even is not a loss");
        assertTrue(HistoryReentrySchedule.Group.ALL.accepts(new BigDecimal("-1")));
        assertTrue(HistoryReentrySchedule.Group.ALL.accepts(null));
    }

    @Test
    void anAbsurdCapIsClampedRatherThanTrusted() {
        HistoryReentrySchedule schedule = new HistoryReentrySchedule(null, true, StrategyMode.PAPER, DayOfWeek.MONDAY,
                LocalTime.of(10, 0), HistoryReentrySchedule.Cadence.WEEKLY, HistoryReentrySchedule.Group.ALL,
                5_000, null);

        assertEquals(HistoryReentrySchedule.MAX_STOCKS_CEILING, schedule.maxStocks());
    }
}
