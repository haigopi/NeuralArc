package com.neuralarc.ui;

import com.neuralarc.model.RecommendationType;
import com.neuralarc.model.SmartPicksSchedule;
import com.neuralarc.model.StrategyMode;
import org.junit.jupiter.api.Test;

import java.awt.Font;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmartPicksWorkspaceViewTest {
    private final SmartPicksWorkspaceView view = new SmartPicksWorkspaceView(new Font("Dialog", Font.BOLD, 11), () -> { }, () -> { }, () -> { });

    @Test
    void anEmptyWorkspaceShowsItsCardAndHidesTheActionBarButtons() {
        view.refresh(Optional.of(SmartPicksWorkspaceKind.MOVERS), true, Optional.empty());

        assertFalse(view.analyzeButton().isVisible());
        assertTrue(view.emptyText().getText().contains("biggest gainers and losers"));
        assertTrue(view.emptyText().getText().contains("Not scheduled yet"));
    }

    @Test
    void aScheduledWorkspaceWithRowsShowsItsBadgeAndCancelButton() {
        SmartPicksSchedule schedule = new SmartPicksSchedule("s", true, "w", "REBOUND", LocalTime.of(15, 30),
                EnumSet.of(DayOfWeek.FRIDAY), true, 1, RecommendationType.SHORT_TERM, StrategyMode.PAPER);

        view.refresh(Optional.of(SmartPicksWorkspaceKind.REBOUND), false, Optional.of(schedule));

        assertTrue(view.analyzeButton().isVisible());
        assertEquals("Analyze Weekend Rebound", view.analyzeButton().getText());
        assertEquals("Scheduled: Fri 15:30 ET · auto-orders", view.scheduleStatus().getText());
        assertTrue(view.cancelScheduleButton().isVisible());
        assertEquals("Edit Schedule…", view.scheduleButton().getText());
    }

    @Test
    void anotherWorkspaceTypeHidesEverything() {
        view.refresh(Optional.empty(), false, Optional.empty());

        assertFalse(view.analyzeButton().isVisible());
        assertFalse(view.scheduleButton().isVisible());
        assertFalse(view.cancelScheduleButton().isVisible());
    }
}
