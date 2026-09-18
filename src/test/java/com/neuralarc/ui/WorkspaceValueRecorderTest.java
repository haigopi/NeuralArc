package com.neuralarc.ui;

import com.neuralarc.db.AppDatabase;
import com.neuralarc.db.SqliteWorkspaceValueRepository;
import com.neuralarc.model.IntradayValueSample;
import com.neuralarc.model.StrategyMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceValueRecorderTest {
    @TempDir
    Path tempDir;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-09-17T19:59:10Z"));

    @Test
    void theDayIsMeasuredFromTheWorkspacesPreviousClose() {
        WorkspaceValueRecorder recorder = recorder();
        recorder.record(StrategyMode.LIVE, "growth", new BigDecimal("5000"));   // Thursday's last reading
        clock.at("2026-09-18T14:31:00Z");
        recorder.record(StrategyMode.LIVE, "growth", new BigDecimal("5150"));

        List<IntradayValueSample> today = recorder.today(StrategyMode.LIVE, "growth");

        assertEquals(1, today.size(), "yesterday's reading is the baseline, not a point on today's line");
        assertEquals(new BigDecimal("5000.00"), today.get(0).baseline());
        assertEquals(new BigDecimal("150.00"), today.get(0).dayChange());
        assertEquals(WorkspaceValueRecorder.PREVIOUS_CLOSE, recorder.baselineLabel(StrategyMode.LIVE, "growth"));
    }

    @Test
    void withoutAPreviousDayTheFirstReadingIsTheBaselineAndSaysSo() {
        WorkspaceValueRecorder recorder = recorder();
        clock.at("2026-09-18T14:31:00Z");
        recorder.record(StrategyMode.LIVE, "growth", new BigDecimal("5000"));
        clock.at("2026-09-18T14:32:00Z");
        recorder.record(StrategyMode.LIVE, "growth", new BigDecimal("5040"));

        List<IntradayValueSample> today = recorder.today(StrategyMode.LIVE, "growth");

        assertEquals(new BigDecimal("5000.00"), today.get(1).baseline());
        assertEquals(WorkspaceValueRecorder.DAY_START, recorder.baselineLabel(StrategyMode.LIVE, "growth"));
    }

    @Test
    void workspacesAndModesAreSeparateLinesAndSurviveARestart() {
        clock.at("2026-09-18T14:31:00Z");
        WorkspaceValueRecorder recorder = recorder();
        recorder.record(StrategyMode.LIVE, "growth", new BigDecimal("5000"));
        recorder.record(StrategyMode.LIVE, "income", new BigDecimal("900"));
        recorder.record(StrategyMode.PAPER, "growth", new BigDecimal("70000"));

        WorkspaceValueRecorder restarted = recorder();

        assertEquals(new BigDecimal("5000.00"), restarted.today(StrategyMode.LIVE, "growth").get(0).value());
        assertEquals(new BigDecimal("900.00"), restarted.today(StrategyMode.LIVE, "income").get(0).value());
        assertEquals(new BigDecimal("70000.00"), restarted.today(StrategyMode.PAPER, "growth").get(0).value());
    }

    @Test
    void anEmptyWorkspaceReadingIsNotPlottedAsACrash() {
        WorkspaceValueRecorder recorder = recorder();

        recorder.record(StrategyMode.LIVE, "growth", BigDecimal.ZERO);

        assertTrue(recorder.today(StrategyMode.LIVE, "growth").isEmpty());
    }

    private WorkspaceValueRecorder recorder() {
        return new WorkspaceValueRecorder(
                new SqliteWorkspaceValueRepository(AppDatabase.open(tempDir.resolve("neuralarc.db"))),
                Runnable::run, clock, null);
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void at(String instant) {
            now = Instant.parse(instant);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
