package com.neuralarc.ui;

import com.neuralarc.db.SqliteScanHistoryRepository;
import com.neuralarc.db.SqliteSmartPicksScheduleRepository;
import com.neuralarc.model.ScanHistoryEntry;
import com.neuralarc.model.SmartPicksSchedule;
import com.neuralarc.service.MarketHoursService;
import com.neuralarc.service.SmartPicksScheduleService;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Owns the autonomous scans of the Smart Picks workspaces (High Volatility Movers, Diversified Leaders,
 * Weekend Rebound): one scheduler per workspace, restored at startup, each running its workspace's
 * Smart Picks strategy through the same pipeline as the interactive dialog. The frame supplies a thin
 * {@link Ui} gateway; the scan itself runs off the EDT.
 */
final class SmartPicksWorkspaceCoordinator {
    interface Ui {
        boolean connectionOk();
        boolean workspaceExists(String workspaceId);
        /** Runs the schedule's scan to completion (blocking, off the EDT) and returns a one-line summary. */
        String runScan(SmartPicksSchedule schedule, SmartPicksWorkspaceKind kind);
        void onScheduleChanged();
        void log(String message);
    }

    private final Ui ui;
    private final SqliteSmartPicksScheduleRepository repository;
    private final SqliteScanHistoryRepository scanHistory;
    private final MarketHoursService marketHours;
    private final Executor background;
    private final Clock clock;
    private final Map<String, SmartPicksScheduleService> services = new LinkedHashMap<>();

    SmartPicksWorkspaceCoordinator(Ui ui, SqliteSmartPicksScheduleRepository repository,
                                   SqliteScanHistoryRepository scanHistory, MarketHoursService marketHours,
                                   Executor background, Clock clock) {
        this.ui = ui;
        this.repository = repository;
        this.scanHistory = scanHistory;
        this.marketHours = marketHours == null ? new MarketHoursService() : marketHours;
        this.background = background;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    /**
     * Restores every enabled schedule. One whose workspace was deleted is removed instead, so it cannot
     * keep scanning — and placing orders — for a workspace that no longer exists.
     */
    void start() {
        for (SmartPicksSchedule schedule : repository.findAll()) {
            if (!ui.workspaceExists(schedule.workspaceId())) {
                repository.deleteById(schedule.id());
                ui.log("[Smart Picks] Removed the schedule of deleted workspace " + schedule.workspaceId() + ".");
                continue;
            }
            if (schedule.enabled()) {
                serviceFor(schedule.workspaceId()).setSchedule(schedule);
                serviceFor(schedule.workspaceId()).start();
                ui.log("[Smart Picks] Restored schedule for " + kindName(schedule) + ": " + schedule.summary() + ".");
            }
        }
        ui.onScheduleChanged();
    }

    Optional<SmartPicksSchedule> schedule(String workspaceId) {
        return workspaceId == null ? Optional.empty() : repository.findByWorkspaceId(workspaceId);
    }

    /** Saves (or replaces) the workspace's schedule and starts it. */
    void save(SmartPicksSchedule schedule) {
        repository.findByWorkspaceId(schedule.workspaceId())
                .filter(existing -> !existing.id().equals(schedule.id()))
                .ifPresent(existing -> repository.deleteById(existing.id()));
        repository.save(schedule);
        SmartPicksScheduleService service = serviceFor(schedule.workspaceId());
        service.setSchedule(schedule);
        service.start();
        ui.log("[Smart Picks] Schedule set for " + kindName(schedule) + ": " + schedule.summary()
                + (schedule.executeAfterScan() ? ", placing orders automatically." : ", recommendation only.")
                + " NeuralArc must be running.");
        ui.onScheduleChanged();
    }

    /** Stops and deletes the workspace's schedule; true when there was one. */
    boolean cancelScheduleForWorkspace(String workspaceId) {
        if (workspaceId == null) {
            return false;
        }
        SmartPicksScheduleService service = services.remove(workspaceId);
        if (service != null) {
            service.clearSchedule();
            service.stop();
        }
        List<SmartPicksSchedule> persisted = repository.findAll().stream()
                .filter(schedule -> workspaceId.equals(schedule.workspaceId()))
                .toList();
        persisted.forEach(schedule -> repository.deleteById(schedule.id()));
        if (!persisted.isEmpty()) {
            ui.log("[Smart Picks] Schedule cancelled for workspace " + workspaceId + ".");
            ui.onScheduleChanged();
        }
        return !persisted.isEmpty();
    }

    void shutdown() {
        services.values().forEach(SmartPicksScheduleService::stop);
    }

    /** Runs a schedule's scan now; package-private so tests can drive it without waiting for the clock. */
    void runScheduled(SmartPicksSchedule schedule) {
        Optional<SmartPicksWorkspaceKind> kind = SmartPicksWorkspaceKind.fromCode(schedule.workspaceCode());
        if (kind.isEmpty()) {
            ui.log("[Smart Picks] Skipped a schedule with an unknown workspace type: " + schedule.workspaceCode());
            return;
        }
        if (!ui.connectionOk()) {
            // The tick fired before the broker was connected; retry rather than lose today's scan.
            serviceFor(schedule.workspaceId()).deferToday();
            ui.log("[Smart Picks] Scheduled " + kind.get().title() + " scan will retry: the broker is not connected yet.");
            return;
        }
        background.execute(() -> {
            String summary;
            try {
                summary = ui.runScan(schedule, kind.get());
            } catch (RuntimeException ex) {
                summary = "Failed: " + ex.getMessage();
            }
            ui.log("[Smart Picks] Scheduled " + kind.get().title() + " scan finished: " + summary);
            if (scanHistory != null) {
                scanHistory.save(ScanHistoryEntry.now(schedule.workspaceId(), false,
                        (schedule.executeAfterScan() ? "" : "Recommendation only: ") + summary));
            }
        });
    }

    private SmartPicksScheduleService serviceFor(String workspaceId) {
        return services.computeIfAbsent(workspaceId, ignored ->
                new SmartPicksScheduleService(marketHours, clock, this::runScheduled, ui::log));
    }

    private static String kindName(SmartPicksSchedule schedule) {
        return SmartPicksWorkspaceKind.fromCode(schedule.workspaceCode())
                .map(SmartPicksWorkspaceKind::title)
                .orElse(schedule.workspaceCode());
    }
}
