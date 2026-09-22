package com.neuralarc.ui;

import com.neuralarc.db.AppDatabase;
import com.neuralarc.db.SqliteScanHistoryRepository;
import com.neuralarc.db.SqliteSmartPicksScheduleRepository;
import com.neuralarc.model.RecommendationType;
import com.neuralarc.model.SmartPicksSchedule;
import com.neuralarc.model.StrategyMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmartPicksWorkspaceCoordinatorTest {
    @TempDir
    Path tempDir;

    private final StubUi ui = new StubUi();
    private SmartPicksWorkspaceCoordinator coordinator;

    @AfterEach
    void stop() {
        if (coordinator != null) {
            coordinator.shutdown();
        }
    }

    @Test
    void aScheduleOfADeletedWorkspaceIsRemovedAtStartup() {
        AppDatabase database = AppDatabase.open(tempDir.resolve("neuralarc.db"));
        SqliteSmartPicksScheduleRepository repository = new SqliteSmartPicksScheduleRepository(database);
        repository.save(schedule("kept", "w-kept", false));
        repository.save(schedule("orphan", "w-deleted", false));
        ui.workspaces = Set.of("w-kept");
        coordinator = coordinator(database, repository);

        coordinator.start();

        assertEquals(List.of("kept"), repository.findAll().stream().map(SmartPicksSchedule::id).toList());
    }

    @Test
    void aScanThatFiresBeforeTheBrokerConnectsWaitsInsteadOfRunning() {
        AppDatabase database = AppDatabase.open(tempDir.resolve("neuralarc.db"));
        coordinator = coordinator(database, new SqliteSmartPicksScheduleRepository(database));
        ui.connected = false;

        coordinator.runScheduled(schedule("s1", "w1", false));

        assertTrue(ui.scans.isEmpty());
        assertTrue(ui.log.stream().anyMatch(line -> line.contains("will retry")), ui.log.toString());
    }

    @Test
    void aRecommendationOnlyScanRunsTheWorkspacesStrategyAndRecordsItsPicks() {
        AppDatabase database = AppDatabase.open(tempDir.resolve("neuralarc.db"));
        SqliteScanHistoryRepository history = new SqliteScanHistoryRepository(database);
        coordinator = new SmartPicksWorkspaceCoordinator(ui, new SqliteSmartPicksScheduleRepository(database), history,
                null, Runnable::run, null);

        coordinator.runScheduled(schedule("s1", "w1", false));

        assertEquals(List.of(SmartPicksWorkspaceKind.REBOUND), ui.scans);
        assertEquals("Recommendation only: 2 pick(s): [AAA, BBB]",
                history.findRecentByWorkspace("w1", 1).get(0).summary());
    }

    @Test
    void savingASecondScheduleReplacesTheFirstAndCancellingRemovesIt() {
        AppDatabase database = AppDatabase.open(tempDir.resolve("neuralarc.db"));
        SqliteSmartPicksScheduleRepository repository = new SqliteSmartPicksScheduleRepository(database);
        coordinator = coordinator(database, repository);

        coordinator.save(schedule("first", "w1", false));
        coordinator.save(schedule("second", "w1", true));

        assertEquals(List.of("second"), repository.findAll().stream().map(SmartPicksSchedule::id).toList());
        assertTrue(coordinator.cancelScheduleForWorkspace("w1"));
        assertTrue(coordinator.schedule("w1").isEmpty());
    }

    private SmartPicksWorkspaceCoordinator coordinator(AppDatabase database, SqliteSmartPicksScheduleRepository repository) {
        return new SmartPicksWorkspaceCoordinator(ui, repository, new SqliteScanHistoryRepository(database), null,
                Runnable::run, null);
    }

    private static SmartPicksSchedule schedule(String id, String workspaceId, boolean placeOrders) {
        return new SmartPicksSchedule(id, true, workspaceId, "REBOUND", LocalTime.of(15, 30),
                EnumSet.of(DayOfWeek.FRIDAY), placeOrders, 2, RecommendationType.SHORT_TERM, StrategyMode.PAPER);
    }

    private static final class StubUi implements SmartPicksWorkspaceCoordinator.Ui {
        private Set<String> workspaces = Set.of("w1");
        private boolean connected = true;
        private final List<SmartPicksWorkspaceKind> scans = new ArrayList<>();
        private final List<String> log = new ArrayList<>();

        @Override public boolean connectionOk() { return connected; }
        @Override public boolean workspaceExists(String workspaceId) { return workspaces.contains(workspaceId); }
        @Override public String runScan(SmartPicksSchedule schedule, SmartPicksWorkspaceKind kind) {
            scans.add(kind);
            return "2 pick(s): [AAA, BBB]";
        }
        @Override public void onScheduleChanged() { }
        @Override public void log(String message) { log.add(message); }
    }
}
