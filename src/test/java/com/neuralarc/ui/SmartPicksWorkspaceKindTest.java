package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SmartPicksWorkspaceKindTest {
    @Test
    void eachStrategyDefaultsToItsFittedSchedule() {
        assertEquals(EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), SmartPicksWorkspaceKind.MOVERS.defaultDays());
        assertEquals(LocalTime.of(10, 0), SmartPicksWorkspaceKind.MOVERS.defaultScanTimeEt());
        assertEquals(EnumSet.of(DayOfWeek.MONDAY), SmartPicksWorkspaceKind.LEADERS.defaultDays());
        assertEquals(EnumSet.of(DayOfWeek.FRIDAY), SmartPicksWorkspaceKind.REBOUND.defaultDays());
        assertEquals(LocalTime.of(15, 30), SmartPicksWorkspaceKind.REBOUND.defaultScanTimeEt());
    }

    @Test
    void codesAndStrategiesResolveBothWays() {
        assertEquals(Optional.of(SmartPicksWorkspaceKind.REBOUND), SmartPicksWorkspaceKind.fromCode(" rebound "));
        assertEquals(Optional.empty(), SmartPicksWorkspaceKind.fromCode("SWING"));
        assertEquals(SmartPicksWorkspaceKind.LEADERS,
                SmartPicksWorkspaceKind.forStrategy(PortfolioCaptureSmartPicksStrategy.DIVERSIFIED_TOP_20));
    }

    @Test
    void theScheduleDialogStartsFromTheFittedDefaultAndRecommendOnly() {
        var schedule = SmartPicksScheduleDialog.defaults(SmartPicksWorkspaceKind.REBOUND, "w1", null);

        assertEquals("Fri 15:30 ET", schedule.summary());
        assertEquals(false, schedule.executeAfterScan(), "placing orders is opt-in");
        assertEquals(LocalTime.of(15, 30), SmartPicksScheduleDialog.nearestOffered(LocalTime.of(15, 37)));
    }
}
