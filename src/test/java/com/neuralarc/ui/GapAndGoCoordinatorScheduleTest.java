package com.neuralarc.ui;

import com.neuralarc.db.AppDatabase;
import com.neuralarc.db.SqliteGapAndGoScheduleRepository;
import com.neuralarc.gaprocket.GapRocketConfig;
import com.neuralarc.model.GapAndGoSchedule;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.service.MarketHoursService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Component;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GapAndGoCoordinatorScheduleTest {
    @TempDir
    Path tempDir;

    @Test
    void aScheduleWhoseWorkspaceWasDeletedIsRemovedInsteadOfScanning() {
        // Regression guard: three schedules of deleted workspaces kept firing every trading morning,
        // all with auto-execute on and in LIVE mode, alongside the real Gap Rocket schedule.
        AppDatabase database = AppDatabase.open(tempDir.resolve("neuralarc.db"));
        SqliteGapAndGoScheduleRepository repository = new SqliteGapAndGoScheduleRepository(database);
        repository.save(schedule("kept", "gap-rocket"));
        repository.save(schedule("orphan-1", "deleted-a"));
        repository.save(schedule("orphan-2", "deleted-b"));
        StubUi ui = new StubUi(Set.of("gap-rocket"));
        GapAndGoCoordinator coordinator = coordinator(ui, database);

        coordinator.start();
        try {
            repository.invalidateCache(); // the coordinator deleted through its own repository instance
            assertEquals(List.of("kept"), repository.findAll().stream().map(GapAndGoSchedule::id).toList());
            assertTrue(ui.log.stream().anyMatch(line -> line.contains("deleted-a") && line.contains("Removed")), ui.log.toString());
        } finally {
            coordinator.cancelScheduleForWorkspace("gap-rocket");
        }
    }

    @Test
    void deletingAWorkspaceCancelsItsSchedule() {
        AppDatabase database = AppDatabase.open(tempDir.resolve("neuralarc.db"));
        SqliteGapAndGoScheduleRepository repository = new SqliteGapAndGoScheduleRepository(database);
        repository.save(schedule("s1", "gap-rocket"));
        GapAndGoCoordinator coordinator = coordinator(new StubUi(Set.of("gap-rocket")), database);
        coordinator.start();

        assertTrue(coordinator.cancelScheduleForWorkspace("gap-rocket"));

        repository.invalidateCache();
        assertTrue(repository.findAll().isEmpty());
    }

    private static GapAndGoCoordinator coordinator(StubUi ui, AppDatabase database) {
        return new GapAndGoCoordinator(ui, database, null, null, new MarketHoursService(), null, Runnable::run);
    }

    private static GapAndGoSchedule schedule(String id, String workspaceId) {
        return new GapAndGoSchedule(id, true, LocalTime.of(9, 5), LocalTime.of(9, 45), LocalTime.of(11, 0),
                true, workspaceId, GapRocketConfig.defaults(StrategyMode.LIVE));
    }

    private static final class StubUi implements GapAndGoCoordinator.Ui {
        private final Set<String> workspaces;
        private final List<String> log = new ArrayList<>();

        private StubUi(Set<String> workspaces) {
            this.workspaces = workspaces;
        }

        @Override public String runtimeApiKey() { return ""; }
        @Override public String runtimeApiSecret() { return ""; }
        @Override public boolean connectionOk() { return false; }
        @Override public String selectedModeLabel() { return "Paper"; }
        @Override public int defaultStrategyPollingSeconds() { return 30; }
        @Override public String selectedWorkspaceId() { return null; }
        @Override public boolean isGapRocketWorkspaceSelected() { return false; }
        @Override public boolean workspaceExists(String workspaceId) { return workspaces.contains(workspaceId); }
        @Override public void log(String message) { log.add(message); }
        @Override public void setScanButtonsEnabled(boolean enabled) { }
        @Override public void onRecommendationsApplied(String workspaceId, String firstAddedStrategyId) { }
        @Override public void onScheduleChanged(GapAndGoSchedule schedule) { }
        @Override public Component dialogParent() { return null; }
    }
}
