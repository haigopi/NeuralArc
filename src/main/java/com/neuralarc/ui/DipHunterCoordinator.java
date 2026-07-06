package com.neuralarc.ui;

import com.neuralarc.api.HttpAlpacaMarketDataApi;
import com.neuralarc.db.AppDatabase;
import com.neuralarc.db.SqliteDipHunterScheduleRepository;
import com.neuralarc.db.SqliteScanHistoryRepository;
import com.neuralarc.db.SqliteStrategyRepository;
import com.neuralarc.diphunter.DipHunterAnalyzer;
import com.neuralarc.diphunter.DipHunterCandidate;
import com.neuralarc.diphunter.DipHunterConfig;
import com.neuralarc.diphunter.DipHunterLiveScanner;
import com.neuralarc.diphunter.DipHunterRecommendation;
import com.neuralarc.diphunter.DipHunterStrategyFactory;
import com.neuralarc.model.DipHunterSchedule;
import com.neuralarc.model.ScanHistoryEntry;
import com.neuralarc.model.Strategy;
import com.neuralarc.service.AlpacaScreenerException;
import com.neuralarc.service.AppSettingsService;
import com.neuralarc.service.DipHunterDiscoveryService;
import com.neuralarc.service.DipHunterScheduleService;
import com.neuralarc.service.HttpAlpacaScreenerClient;
import com.neuralarc.service.MarketHoursService;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Owns the autonomous Dip Hunter pipeline (discover → scan → analyze → apply) and the regular-session
 * scheduling engine, keeping this logic out of {@code TradingFrame}. The frame supplies a thin
 * {@link Ui} gateway for credentials, the selected workspace, logging, button state, and the
 * post-apply UI refresh; it does not contain any of the orchestration itself.
 */
final class DipHunterCoordinator {
    static final String RECOMMENDED_STATUS = "DIP_HUNTER_RECOMMENDED";
    static final String MONITORING_STATUS = "DIP_HUNTER_MONITORING";

    /** Callbacks the coordinator needs from the host frame. */
    interface Ui {
        String runtimeApiKey();
        String runtimeApiSecret();
        boolean connectionOk();
        String selectedModeLabel();
        int defaultStrategyPollingSeconds();
        String selectedWorkspaceId();
        boolean isDipHunterWorkspaceSelected();
        void log(String message);
        void setScanButtonsEnabled(boolean enabled);
        void onRecommendationsApplied(String workspaceId, String firstAddedStrategyId);
        void onScheduleChanged(DipHunterSchedule schedule);
        java.awt.Component dialogParent();
    }

    private final Ui ui;
    private final SqliteStrategyRepository strategyRepository;
    private final AppSettingsService appSettingsService;
    private final SqliteDipHunterScheduleRepository scheduleRepository;
    private final SqliteScanHistoryRepository scanHistoryRepository;
    private final MarketHoursService marketHoursService;
    private final Map<String, DipHunterScheduleService> scheduleServicesByWorkspace = new LinkedHashMap<>();
    private final Executor backgroundExecutor;

    DipHunterCoordinator(Ui ui, AppDatabase database, SqliteStrategyRepository strategyRepository,
                         AppSettingsService appSettingsService, MarketHoursService marketHoursService,
                         SqliteScanHistoryRepository scanHistoryRepository, Executor backgroundExecutor) {
        this.ui = ui;
        this.strategyRepository = strategyRepository;
        this.appSettingsService = appSettingsService;
        this.backgroundExecutor = backgroundExecutor;
        this.marketHoursService = marketHoursService == null ? new MarketHoursService() : marketHoursService;
        this.scheduleRepository = new SqliteDipHunterScheduleRepository(database);
        this.scanHistoryRepository = scanHistoryRepository;
    }

    private void recordScan(String workspaceId, boolean interactive, String summary) {
        if (scanHistoryRepository == null || workspaceId == null) {
            return;
        }
        scanHistoryRepository.save(ScanHistoryEntry.now(workspaceId, interactive, summary));
    }

    static boolean isPendingOrderPlacement(Strategy strategy) {
        return strategy != null && RECOMMENDED_STATUS.equalsIgnoreCase(strategy.latestOrderStatus());
    }

    /** Load any persisted enabled schedule and start the regular-session scheduler. */
    void start() {
        scheduleRepository.findAll().stream()
                .filter(DipHunterSchedule::enabled)
                .forEach(schedule -> {
                    DipHunterScheduleService service = scheduleServiceForWorkspace(schedule.workspaceId());
                    service.setSchedule(schedule);
                    service.start();
                    ui.log("[Dip Hunter] Restored autonomous schedule: scan " + schedule.scanTimeEt()
                            + " ET for workspace " + schedule.workspaceId()
                            + (schedule.executeAfterScan() ? " (auto-execute)." : " (recommendation only)."));
                });
        ui.onScheduleChanged(currentSchedule());
    }

    DipHunterSchedule currentSchedule() {
        String workspaceId = ui.selectedWorkspaceId();
        if (workspaceId == null) {
            return null;
        }
        DipHunterScheduleService service = scheduleServicesByWorkspace.get(workspaceId);
        DipHunterSchedule inMemory = service == null ? null : service.schedule();
        if (inMemory != null) {
            return inMemory;
        }
        return scheduleRepository.findByWorkspaceId(workspaceId)
                .filter(DipHunterSchedule::enabled)
                .orElse(null);
    }

    /** Run an interactive scan for the selected workspace. */
    void analyze(DipHunterConfig config, boolean executeRequested) {
        if (ui.selectedWorkspaceId() == null || !ui.isDipHunterWorkspaceSelected()) {
            return;
        }
        runScan(ui.selectedWorkspaceId(), config, executeRequested, true);
    }

    /** Register a new schedule, or offer to cancel the existing one for the selected workspace. */
    void scheduleOrCancel(DipHunterConfig config) {
        String workspaceId = ui.selectedWorkspaceId();
        if (workspaceId == null || !ui.isDipHunterWorkspaceSelected()) {
            return;
        }
        DipHunterScheduleService selectedScheduleService = scheduleServiceForWorkspace(workspaceId);
        DipHunterSchedule existing = selectedScheduleService.schedule();
        if (existing == null) {
            existing = scheduleRepository.findByWorkspaceId(workspaceId)
                    .filter(DipHunterSchedule::enabled)
                    .orElse(null);
            if (existing != null) {
                selectedScheduleService.setSchedule(existing);
                selectedScheduleService.start();
            }
        }
        if (existing != null && workspaceId.equals(existing.workspaceId())) {
            int cancel = JOptionPane.showConfirmDialog(ui.dialogParent(),
                    "An autonomous Dip Hunter schedule already runs at " + existing.scanTimeEt() + " ET for this workspace.\n"
                            + "Cancel it?",
                    "Dip Hunter Schedule", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (cancel == JOptionPane.YES_OPTION) {
                cancelSchedule();
            }
            return;
        }
        int execute = JOptionPane.showConfirmDialog(ui.dialogParent(),
                "Schedule an autonomous Dip Hunter scan at " + DipHunterSchedule.DEFAULT_SCAN_TIME_ET + " ET on trading days,\n"
                        + "carrying through the 10:00–15:30 ET execution window.\n\n"
                        + "Auto-execute trades after each scan? (No = build the recommendation list only.)\n\n"
                        + "Note: NeuralArc must be running at the scheduled time.",
                "Dip Hunter Schedule", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (execute == JOptionPane.CANCEL_OPTION || execute == JOptionPane.CLOSED_OPTION) {
            return;
        }
        boolean executeAfterScan = execute == JOptionPane.YES_OPTION;
        DipHunterSchedule schedule = DipHunterSchedule.create(workspaceId, config, executeAfterScan);
        scheduleRepository.save(schedule);
        selectedScheduleService.setSchedule(schedule);
        selectedScheduleService.start();
        ui.onScheduleChanged(schedule);
        ui.log("[Dip Hunter] Autonomous schedule set: scan " + schedule.scanTimeEt() + " ET"
                + (executeAfterScan ? " with auto-execute." : " (recommendation only).") + " NeuralArc must be running.");
        JOptionPane.showMessageDialog(ui.dialogParent(),
                "Autonomous Dip Hunter scan scheduled for " + schedule.scanTimeEt() + " ET on trading days.\n"
                        + (executeAfterScan ? "Trades will be armed automatically after each scan.\n" : "")
                        + "NeuralArc must be running at that time.",
                "Dip Hunter Schedule", JOptionPane.INFORMATION_MESSAGE);
    }

    /** Cancel and forget the current schedule. */
    void cancelSchedule() {
        String workspaceId = ui.selectedWorkspaceId();
        if (workspaceId == null) {
            return;
        }
        DipHunterScheduleService selectedScheduleService = scheduleServicesByWorkspace.get(workspaceId);
        DipHunterSchedule existing = selectedScheduleService == null ? null : selectedScheduleService.schedule();
        if (existing == null) {
            return;
        }
        selectedScheduleService.clearSchedule();
        scheduleRepository.deleteById(existing.id());
        ui.onScheduleChanged(null);
        ui.log("[Dip Hunter] Autonomous schedule cancelled.");
    }

    private DipHunterScheduleService scheduleServiceForWorkspace(String workspaceId) {
        String key = workspaceId == null ? "" : workspaceId;
        return scheduleServicesByWorkspace.computeIfAbsent(key, ignored ->
                new DipHunterScheduleService(marketHoursService, Clock.systemUTC(),
                        schedule -> SwingUtilities.invokeLater(() -> runScheduled(schedule)), ui::log));
    }

    private void runScheduled(DipHunterSchedule schedule) {
        if (schedule == null) {
            return;
        }
        runScan(schedule.workspaceId(), schedule.config(), schedule.executeAfterScan(), false);
    }

    /**
     * Shared scan path for both manual and scheduled runs. Interactive runs surface dialogs and
     * toggle the scan buttons; scheduled runs only log.
     */
    private void runScan(String workspaceId, DipHunterConfig config, boolean executeRequested, boolean interactive) {
        if (!ui.connectionOk() || ui.runtimeApiKey().isBlank()) {
            String message = ui.selectedModeLabel()
                    + " Alpaca credentials are required before scanning Dip Hunter live market data.";
            if (interactive) {
                JOptionPane.showMessageDialog(ui.dialogParent(), message, "Dip Hunter Analysis", JOptionPane.WARNING_MESSAGE);
            } else {
                ui.log("[Dip Hunter] Scheduled scan skipped: " + message);
            }
            return;
        }
        if (interactive) {
            ui.setScanButtonsEnabled(false);
        }
        backgroundExecutor.execute(() -> {
            try {
                List<String> symbols = resolveCandidateSymbols(config);
                if (symbols.isEmpty()) {
                    recordScan(workspaceId, interactive, "No live candidates found");
                    if (interactive) {
                        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(ui.dialogParent(),
                                "No live Dip Hunter candidates were found right now. Try again during the regular session, "
                                        + "or enter symbols manually.",
                                "Dip Hunter Analysis", JOptionPane.INFORMATION_MESSAGE));
                    } else {
                        ui.log("[Dip Hunter] Scheduled scan found no live candidates right now.");
                    }
                    return;
                }
                DipHunterLiveScanner scanner = new DipHunterLiveScanner(
                        new HttpAlpacaMarketDataApi(ui.runtimeApiKey(), ui.runtimeApiSecret()),
                        Clock.systemDefaultZone(), ui::log);
                DipHunterAnalyzer analyzer = new DipHunterAnalyzer(Clock.systemUTC(), ui::log);
                List<DipHunterCandidate> scanned = scanner.candidates(symbols);
                List<DipHunterRecommendation> recommendations = analyzer.analyze(scanned, config);
                SwingUtilities.invokeLater(() ->
                        applyRecommendations(workspaceId, config, executeRequested, interactive, recommendations));
            } finally {
                if (interactive) {
                    SwingUtilities.invokeLater(() -> ui.setScanButtonsEnabled(true));
                }
            }
        });
    }

    /**
     * Resolve the symbols to scan: an operator's manual entry always wins; otherwise auto-discover
     * the day's pulled-back leaders from Alpaca's screener so no manual monitoring is required.
     */
    private List<String> resolveCandidateSymbols(DipHunterConfig config) {
        if (!config.candidateSymbols().isEmpty()) {
            return config.candidateSymbols();
        }
        try {
            DipHunterDiscoveryService discovery = new DipHunterDiscoveryService(
                    new HttpAlpacaScreenerClient(ui.runtimeApiKey(), ui.runtimeApiSecret()));
            int target = Math.max(30, config.maxStocksToAdd() * 3);
            List<String> discovered = discovery.discoverCandidates(config, target);
            ui.log("[Dip Hunter] Auto-discovered " + discovered.size() + " candidate(s) from Alpaca screener.");
            return discovered;
        } catch (AlpacaScreenerException ex) {
            ui.log("[Dip Hunter] Auto-discovery failed: " + ex.getMessage());
            return List.of();
        }
    }

    private void applyRecommendations(String workspaceId, DipHunterConfig config, boolean executeRequested,
                                      boolean interactive, List<DipHunterRecommendation> recommendations) {
        if (workspaceId == null) {
            return;
        }
        if (recommendations.isEmpty()) {
            recordScan(workspaceId, interactive, "No qualifying candidates");
            if (interactive) {
                JOptionPane.showMessageDialog(ui.dialogParent(),
                        "No Dip Hunter candidates met the current live-data filters. Try widening the pullback range, or lowering the relative-volume, trend, or price requirements.",
                        "Dip Hunter Analysis", JOptionPane.INFORMATION_MESSAGE);
            } else {
                ui.log("[Dip Hunter] Scheduled scan produced no qualifying candidates.");
            }
            return;
        }
        DipHunterStrategyFactory factory = new DipHunterStrategyFactory();
        int added = 0;
        int updated = 0;
        int skipped = 0;
        String firstAddedStrategyId = null;
        for (DipHunterRecommendation recommendation : recommendations) {
            Optional<Strategy> existing = findTrackedStrategy(recommendation.symbol(), config.mode(), workspaceId);
            if (existing.isPresent()) {
                Strategy existingStrategy = existing.get();
                if (isPendingOrderPlacement(existingStrategy)) {
                    existingStrategy.setBaseBuyLimitPrice(recommendation.plannedEntryPrice());
                    existingStrategy.setStopLossPrice(recommendation.stopLossPrice());
                    existingStrategy.setTargetSellPrice(recommendation.takeProfitPrice());
                    existingStrategy.setLastEvent("Dip Hunter recommendation refreshed: score=" + recommendation.strategyScore()
                            + ", plannedEntry=$" + recommendation.plannedEntryPrice().toPlainString()
                            + ", target=$" + recommendation.takeProfitPrice().toPlainString()
                            + ". No broker order was submitted.");
                    strategyRepository.save(existingStrategy);
                    updated++;
                } else {
                    skipped++;
                    ui.log("[Dip Hunter] Skipped " + recommendation.symbol() + ": already has an order or active state in this strategy tab.");
                }
                continue;
            }
            Strategy strategy = factory.toStrategy(recommendation, workspaceId, executeRequested,
                    ui.defaultStrategyPollingSeconds());
            strategyRepository.save(strategy);
            if (firstAddedStrategyId == null) {
                firstAddedStrategyId = strategy.id();
            }
            added++;
            ui.log("[Dip Hunter] Added " + recommendation.symbol()
                    + " score=" + recommendation.strategyScore()
                    + " pullback=" + recommendation.pullbackPercent().toPlainString() + "%"
                    + " plannedEntry=$" + recommendation.plannedEntryPrice().toPlainString()
                    + " mode=" + recommendation.mode()
                    + (executeRequested ? " monitoring enabled" : " recommendation only") + ".");
        }
        recordScan(workspaceId, interactive, ScanHistoryEntry.summarize(added, updated, skipped));
        ui.onRecommendationsApplied(workspaceId, firstAddedStrategyId);
        ui.log("[Dip Hunter] Added " + added + " Dip Hunter candidate" + (added == 1 ? "" : "s")
                + " to the Dip Hunter grid"
                + (updated > 0 ? "; refreshed " + updated + " existing pending row" + (updated == 1 ? "" : "s") : "")
                + (skipped > 0 ? "; skipped " + skipped + " duplicate symbol" + (skipped == 1 ? "" : "s") : "")
                + ". No broker orders were submitted.");
    }

    private Optional<Strategy> findTrackedStrategy(String symbol, com.neuralarc.model.StrategyMode mode, String workspaceId) {
        return strategyRepository.findAll().stream()
                .filter(strategy -> strategy.mode() == mode)
                .filter(strategy -> workspaceId.equals(strategy.workspaceId()))
                .filter(strategy -> strategy.symbol().equalsIgnoreCase(symbol))
                .findFirst();
    }
}
