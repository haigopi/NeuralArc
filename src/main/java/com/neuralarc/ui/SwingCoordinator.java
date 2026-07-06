package com.neuralarc.ui;

import com.neuralarc.api.HttpAlpacaMarketDataApi;
import com.neuralarc.db.AppDatabase;
import com.neuralarc.db.SqliteScanHistoryRepository;
import com.neuralarc.db.SqliteStrategyRepository;
import com.neuralarc.db.SqliteSwingScheduleRepository;
import com.neuralarc.model.ScanHistoryEntry;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.SwingSchedule;
import com.neuralarc.service.AlpacaScreenerException;
import com.neuralarc.service.AppSettingsService;
import com.neuralarc.service.HttpAlpacaScreenerClient;
import com.neuralarc.service.MarketHoursService;
import com.neuralarc.service.SwingDiscoveryService;
import com.neuralarc.service.SwingScheduleService;
import com.neuralarc.swing.SwingAnalyzer;
import com.neuralarc.swing.SwingCandidate;
import com.neuralarc.swing.SwingConfig;
import com.neuralarc.swing.SwingLiveScanner;
import com.neuralarc.swing.SwingRecommendation;
import com.neuralarc.swing.SwingStrategyFactory;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Owns the autonomous Swing Vault pipeline (discover → scan → analyze → apply) and the once-per-day
 * scheduling engine, keeping this logic out of {@code TradingFrame}. The frame supplies a thin
 * {@link Ui} gateway for credentials, the selected workspace, logging, button state, and the
 * post-apply UI refresh; it does not contain any of the orchestration itself.
 */
final class SwingCoordinator {
    static final String RECOMMENDED_STATUS = "SWING_RECOMMENDED";
    static final String MONITORING_STATUS = "SWING_MONITORING";

    /** Callbacks the coordinator needs from the host frame. */
    interface Ui {
        String runtimeApiKey();
        String runtimeApiSecret();
        boolean connectionOk();
        String selectedModeLabel();
        int defaultStrategyPollingSeconds();
        String selectedWorkspaceId();
        boolean isSwingWorkspaceSelected();
        void log(String message);
        void setScanButtonsEnabled(boolean enabled);
        void onRecommendationsApplied(String workspaceId, String firstAddedStrategyId);
        void onScheduleChanged(SwingSchedule schedule);
        java.awt.Component dialogParent();
    }

    private final Ui ui;
    private final SqliteStrategyRepository strategyRepository;
    private final AppSettingsService appSettingsService;
    private final SqliteSwingScheduleRepository scheduleRepository;
    private final SqliteScanHistoryRepository scanHistoryRepository;
    private final MarketHoursService marketHoursService;
    private final Map<String, SwingScheduleService> scheduleServicesByWorkspace = new LinkedHashMap<>();
    private final Executor backgroundExecutor;

    SwingCoordinator(Ui ui, AppDatabase database, SqliteStrategyRepository strategyRepository,
                     AppSettingsService appSettingsService, MarketHoursService marketHoursService,
                     SqliteScanHistoryRepository scanHistoryRepository, Executor backgroundExecutor) {
        this.ui = ui;
        this.strategyRepository = strategyRepository;
        this.appSettingsService = appSettingsService;
        this.backgroundExecutor = backgroundExecutor;
        this.marketHoursService = marketHoursService == null ? new MarketHoursService() : marketHoursService;
        this.scheduleRepository = new SqliteSwingScheduleRepository(database);
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

    /** Load any persisted enabled schedule and start the once-per-day scheduler. */
    void start() {
        scheduleRepository.findAll().stream()
                .filter(SwingSchedule::enabled)
                .forEach(schedule -> {
                    SwingScheduleService service = scheduleServiceForWorkspace(schedule.workspaceId());
                    service.setSchedule(schedule);
                    service.start();
                    ui.log("[Swing Vault] Restored autonomous schedule: scan " + schedule.scanTimeEt()
                            + " ET for workspace " + schedule.workspaceId()
                            + (schedule.executeAfterScan() ? " (auto-execute)." : " (recommendation only)."));
                });
        ui.onScheduleChanged(currentSchedule());
    }

    SwingSchedule currentSchedule() {
        String workspaceId = ui.selectedWorkspaceId();
        if (workspaceId == null) {
            return null;
        }
        SwingScheduleService service = scheduleServicesByWorkspace.get(workspaceId);
        SwingSchedule inMemory = service == null ? null : service.schedule();
        if (inMemory != null) {
            return inMemory;
        }
        return scheduleRepository.findByWorkspaceId(workspaceId)
                .filter(SwingSchedule::enabled)
                .orElse(null);
    }

    /** Run an interactive scan for the selected workspace. */
    void analyze(SwingConfig config, boolean executeRequested) {
        if (ui.selectedWorkspaceId() == null || !ui.isSwingWorkspaceSelected()) {
            return;
        }
        runScan(ui.selectedWorkspaceId(), config, executeRequested, true);
    }

    /** Register a new schedule, or offer to cancel the existing one for the selected workspace. */
    void scheduleOrCancel(SwingConfig config) {
        String workspaceId = ui.selectedWorkspaceId();
        if (workspaceId == null || !ui.isSwingWorkspaceSelected()) {
            return;
        }
        SwingScheduleService selectedScheduleService = scheduleServiceForWorkspace(workspaceId);
        SwingSchedule existing = selectedScheduleService.schedule();
        if (existing == null) {
            existing = scheduleRepository.findByWorkspaceId(workspaceId)
                    .filter(SwingSchedule::enabled)
                    .orElse(null);
            if (existing != null) {
                selectedScheduleService.setSchedule(existing);
                selectedScheduleService.start();
            }
        }
        if (existing != null && workspaceId.equals(existing.workspaceId())) {
            int cancel = JOptionPane.showConfirmDialog(ui.dialogParent(),
                    "An autonomous Swing Vault schedule already runs at " + existing.scanTimeEt() + " ET for this workspace.\n"
                            + "Cancel it?",
                    "Swing Vault Schedule", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (cancel == JOptionPane.YES_OPTION) {
                cancelSchedule();
            }
            return;
        }
        int execute = JOptionPane.showConfirmDialog(ui.dialogParent(),
                "Schedule an autonomous Swing Vault scan at " + SwingSchedule.DEFAULT_SCAN_TIME_ET + " ET on trading days\n"
                        + "(once per session, daily-bar setups).\n\n"
                        + "Auto-execute trades after each scan? (No = build the recommendation list only.)\n\n"
                        + "Note: NeuralArc must be running at the scheduled time.",
                "Swing Vault Schedule", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (execute == JOptionPane.CANCEL_OPTION || execute == JOptionPane.CLOSED_OPTION) {
            return;
        }
        boolean executeAfterScan = execute == JOptionPane.YES_OPTION;
        SwingSchedule schedule = SwingSchedule.create(workspaceId, config, executeAfterScan);
        scheduleRepository.save(schedule);
        selectedScheduleService.setSchedule(schedule);
        selectedScheduleService.start();
        ui.onScheduleChanged(schedule);
        ui.log("[Swing Vault] Autonomous schedule set: scan " + schedule.scanTimeEt() + " ET"
                + (executeAfterScan ? " with auto-execute." : " (recommendation only).") + " NeuralArc must be running.");
        JOptionPane.showMessageDialog(ui.dialogParent(),
                "Autonomous Swing Vault scan scheduled for " + schedule.scanTimeEt() + " ET on trading days.\n"
                        + (executeAfterScan ? "Trades will be armed automatically after each scan.\n" : "")
                        + "NeuralArc must be running at that time.",
                "Swing Vault Schedule", JOptionPane.INFORMATION_MESSAGE);
    }

    /** Cancel and forget the current schedule. */
    void cancelSchedule() {
        String workspaceId = ui.selectedWorkspaceId();
        if (workspaceId == null) {
            return;
        }
        SwingScheduleService selectedScheduleService = scheduleServicesByWorkspace.get(workspaceId);
        SwingSchedule existing = selectedScheduleService == null ? null : selectedScheduleService.schedule();
        if (existing == null) {
            existing = scheduleRepository.findByWorkspaceId(workspaceId).orElse(null);
        }
        if (existing == null) {
            return;
        }
        if (selectedScheduleService != null) {
            selectedScheduleService.clearSchedule();
        }
        scheduleRepository.deleteById(existing.id());
        ui.onScheduleChanged(null);
        ui.log("[Swing Vault] Autonomous schedule cancelled.");
    }

    private SwingScheduleService scheduleServiceForWorkspace(String workspaceId) {
        String key = workspaceId == null ? "" : workspaceId;
        return scheduleServicesByWorkspace.computeIfAbsent(key, ignored ->
                new SwingScheduleService(marketHoursService, Clock.systemUTC(),
                        schedule -> SwingUtilities.invokeLater(() -> runScheduled(schedule)), ui::log));
    }

    private void runScheduled(SwingSchedule schedule) {
        if (schedule == null) {
            return;
        }
        runScan(schedule.workspaceId(), schedule.config(), schedule.executeAfterScan(), false);
    }

    /**
     * Shared scan path for both manual and scheduled runs. Interactive runs surface dialogs and
     * toggle the scan buttons; scheduled runs only log.
     */
    private void runScan(String workspaceId, SwingConfig config, boolean executeRequested, boolean interactive) {
        if (!ui.connectionOk() || ui.runtimeApiKey().isBlank()) {
            String message = ui.selectedModeLabel()
                    + " Alpaca credentials are required before scanning Swing Vault live market data.";
            if (interactive) {
                JOptionPane.showMessageDialog(ui.dialogParent(), message, "Swing Vault Analysis", JOptionPane.WARNING_MESSAGE);
            } else {
                ui.log("[Swing Vault] Scheduled scan skipped: " + message);
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
                                "No live Swing Vault candidates were found right now. Try again during the regular session, "
                                        + "or enter symbols manually.",
                                "Swing Vault Analysis", JOptionPane.INFORMATION_MESSAGE));
                    } else {
                        ui.log("[Swing Vault] Scheduled scan found no live candidates right now.");
                    }
                    return;
                }
                SwingLiveScanner scanner = new SwingLiveScanner(
                        new HttpAlpacaMarketDataApi(ui.runtimeApiKey(), ui.runtimeApiSecret()),
                        Clock.systemDefaultZone(), ui::log);
                SwingAnalyzer analyzer = new SwingAnalyzer(Clock.systemUTC(), ui::log);
                List<SwingCandidate> scanned = scanner.candidates(symbols);
                List<SwingRecommendation> recommendations = analyzer.analyze(scanned, config);
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
    private List<String> resolveCandidateSymbols(SwingConfig config) {
        if (!config.candidateSymbols().isEmpty()) {
            return config.candidateSymbols();
        }
        try {
            SwingDiscoveryService discovery = new SwingDiscoveryService(
                    new HttpAlpacaScreenerClient(ui.runtimeApiKey(), ui.runtimeApiSecret()));
            int target = Math.max(30, config.maxStocksToAdd() * 3);
            List<String> discovered = discovery.discoverCandidates(config, target);
            ui.log("[Swing Vault] Auto-discovered " + discovered.size() + " candidate(s) from Alpaca screener.");
            return discovered;
        } catch (AlpacaScreenerException ex) {
            ui.log("[Swing Vault] Auto-discovery failed: " + ex.getMessage());
            return List.of();
        }
    }

    private void applyRecommendations(String workspaceId, SwingConfig config, boolean executeRequested,
                                      boolean interactive, List<SwingRecommendation> recommendations) {
        if (workspaceId == null) {
            return;
        }
        if (recommendations.isEmpty()) {
            recordScan(workspaceId, interactive, "No qualifying candidates");
            if (interactive) {
                JOptionPane.showMessageDialog(ui.dialogParent(),
                        "No Swing Vault candidates met the current live-data filters. Try widening the pullback range, or lowering the relative-volume, trend, or price requirements.",
                        "Swing Vault Analysis", JOptionPane.INFORMATION_MESSAGE);
            } else {
                ui.log("[Swing Vault] Scheduled scan produced no qualifying candidates.");
            }
            return;
        }
        SwingStrategyFactory factory = new SwingStrategyFactory();
        int added = 0;
        int updated = 0;
        int skipped = 0;
        String firstAddedStrategyId = null;
        for (SwingRecommendation recommendation : recommendations) {
            Optional<Strategy> existing = findTrackedStrategy(recommendation.symbol(), config.mode(), workspaceId);
            if (existing.isPresent()) {
                Strategy existingStrategy = existing.get();
                if (isPendingOrderPlacement(existingStrategy)) {
                    existingStrategy.setBaseBuyLimitPrice(recommendation.plannedEntryPrice());
                    existingStrategy.setStopLossPrice(recommendation.stopLossPrice());
                    existingStrategy.setTargetSellPrice(recommendation.targetPrice());
                    existingStrategy.setLastEvent("Swing Vault recommendation refreshed: score=" + recommendation.strategyScore()
                            + ", plannedEntry=$" + recommendation.plannedEntryPrice().toPlainString()
                            + ", target=$" + recommendation.targetPrice().toPlainString()
                            + ". No broker order was submitted.");
                    strategyRepository.save(existingStrategy);
                    updated++;
                } else {
                    skipped++;
                    ui.log("[Swing Vault] Skipped " + recommendation.symbol() + ": already has an order or active state in this strategy tab.");
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
            ui.log("[Swing Vault] Added " + recommendation.symbol()
                    + " score=" + recommendation.strategyScore()
                    + " pullback=" + recommendation.pullbackPercent().toPlainString() + "%"
                    + " plannedEntry=$" + recommendation.plannedEntryPrice().toPlainString()
                    + " mode=" + recommendation.mode()
                    + (executeRequested ? " monitoring enabled" : " recommendation only") + ".");
        }
        recordScan(workspaceId, interactive, ScanHistoryEntry.summarize(added, updated, skipped));
        ui.onRecommendationsApplied(workspaceId, firstAddedStrategyId);
        ui.log("[Swing Vault] Added " + added + " Swing Vault candidate" + (added == 1 ? "" : "s")
                + " to the Swing Vault grid"
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
