package com.neuralarc.ui;

import com.neuralarc.api.HttpAlpacaMarketDataApi;
import com.neuralarc.db.AppDatabase;
import com.neuralarc.db.SqliteScanHistoryRepository;
import com.neuralarc.db.SqliteStrategyRepository;
import com.neuralarc.db.SqliteVwapScheduleRepository;
import com.neuralarc.model.ScanHistoryEntry;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.VwapSchedule;
import com.neuralarc.service.AlpacaScreenerException;
import com.neuralarc.service.AppSettingsService;
import com.neuralarc.service.HttpAlpacaScreenerClient;
import com.neuralarc.service.MarketHoursService;
import com.neuralarc.service.VwapDiscoveryService;
import com.neuralarc.service.VwapScheduleService;
import com.neuralarc.vwap.VwapAnalyzer;
import com.neuralarc.vwap.VwapCandidate;
import com.neuralarc.vwap.VwapConfig;
import com.neuralarc.vwap.VwapLiveScanner;
import com.neuralarc.vwap.VwapRecommendation;
import com.neuralarc.vwap.VwapStrategyFactory;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Owns the autonomous VWAP Desk pipeline (discover → scan → analyze → apply) and the regular-session
 * scheduling engine, keeping this logic out of {@code TradingFrame}. The frame supplies a thin
 * {@link Ui} gateway for credentials, the selected workspace, logging, button state, and the
 * post-apply UI refresh; it does not contain any of the orchestration itself.
 */
final class VwapCoordinator {
    static final String RECOMMENDED_STATUS = "VWAP_RECOMMENDED";
    static final String MONITORING_STATUS = "VWAP_MONITORING";

    /** Callbacks the coordinator needs from the host frame. */
    interface Ui {
        String runtimeApiKey();
        String runtimeApiSecret();
        boolean connectionOk();
        String selectedModeLabel();
        int defaultStrategyPollingSeconds();
        String selectedWorkspaceId();
        boolean isVwapWorkspaceSelected();
        void log(String message);
        void setScanButtonsEnabled(boolean enabled);
        void onRecommendationsApplied(String workspaceId, String firstAddedStrategyId);
        void onScheduleChanged(VwapSchedule schedule);
        java.awt.Component dialogParent();
    }

    private final Ui ui;
    private final SqliteStrategyRepository strategyRepository;
    private final AppSettingsService appSettingsService;
    private final SqliteVwapScheduleRepository scheduleRepository;
    private final SqliteScanHistoryRepository scanHistoryRepository;
    private final MarketHoursService marketHoursService;
    private final Map<String, VwapScheduleService> scheduleServicesByWorkspace = new LinkedHashMap<>();
    private final Executor backgroundExecutor;

    VwapCoordinator(Ui ui, AppDatabase database, SqliteStrategyRepository strategyRepository,
                    AppSettingsService appSettingsService, MarketHoursService marketHoursService,
                    SqliteScanHistoryRepository scanHistoryRepository, Executor backgroundExecutor) {
        this.ui = ui;
        this.strategyRepository = strategyRepository;
        this.appSettingsService = appSettingsService;
        this.backgroundExecutor = backgroundExecutor;
        this.marketHoursService = marketHoursService == null ? new MarketHoursService() : marketHoursService;
        this.scheduleRepository = new SqliteVwapScheduleRepository(database);
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
                .filter(VwapSchedule::enabled)
                .forEach(schedule -> {
                    VwapScheduleService service = scheduleServiceForWorkspace(schedule.workspaceId());
                    service.setSchedule(schedule);
                    service.start();
                    ui.log("[VWAP Desk] Restored autonomous schedule: scan " + schedule.scanTimeEt()
                            + " ET for workspace " + schedule.workspaceId()
                            + (schedule.executeAfterScan() ? " (auto-execute)." : " (recommendation only)."));
                });
        ui.onScheduleChanged(currentSchedule());
    }

    VwapSchedule currentSchedule() {
        String workspaceId = ui.selectedWorkspaceId();
        if (workspaceId == null) {
            return null;
        }
        VwapScheduleService service = scheduleServicesByWorkspace.get(workspaceId);
        VwapSchedule inMemory = service == null ? null : service.schedule();
        if (inMemory != null) {
            return inMemory;
        }
        return scheduleRepository.findByWorkspaceId(workspaceId)
                .filter(VwapSchedule::enabled)
                .orElse(null);
    }

    /** Run an interactive scan for the selected workspace. */
    void analyze(VwapConfig config, boolean executeRequested) {
        if (ui.selectedWorkspaceId() == null || !ui.isVwapWorkspaceSelected()) {
            return;
        }
        runScan(ui.selectedWorkspaceId(), config, executeRequested, true);
    }

    /** Register a new schedule, or offer to cancel the existing one for the selected workspace. */
    void scheduleOrCancel(VwapConfig config) {
        String workspaceId = ui.selectedWorkspaceId();
        if (workspaceId == null || !ui.isVwapWorkspaceSelected()) {
            return;
        }
        VwapScheduleService selectedScheduleService = scheduleServiceForWorkspace(workspaceId);
        VwapSchedule existing = selectedScheduleService.schedule();
        if (existing == null) {
            existing = scheduleRepository.findByWorkspaceId(workspaceId)
                    .filter(VwapSchedule::enabled)
                    .orElse(null);
            if (existing != null) {
                selectedScheduleService.setSchedule(existing);
                selectedScheduleService.start();
            }
        }
        if (existing != null && workspaceId.equals(existing.workspaceId())) {
            int cancel = JOptionPane.showConfirmDialog(ui.dialogParent(),
                    "An autonomous VWAP Desk schedule already runs at " + existing.scanTimeEt() + " ET for this workspace.\n"
                            + "Cancel it?",
                    "VWAP Desk Schedule", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (cancel == JOptionPane.YES_OPTION) {
                cancelSchedule();
            }
            return;
        }
        int execute = JOptionPane.showConfirmDialog(ui.dialogParent(),
                "Schedule an autonomous VWAP Desk scan at " + VwapSchedule.DEFAULT_SCAN_TIME_ET + " ET on trading days,\n"
                        + "carrying through the 10:00–15:30 ET execution window.\n\n"
                        + "Auto-execute trades after each scan? (No = build the recommendation list only.)\n\n"
                        + "Note: NeuralArc must be running at the scheduled time.",
                "VWAP Desk Schedule", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (execute == JOptionPane.CANCEL_OPTION || execute == JOptionPane.CLOSED_OPTION) {
            return;
        }
        boolean executeAfterScan = execute == JOptionPane.YES_OPTION;
        VwapSchedule schedule = VwapSchedule.create(workspaceId, config, executeAfterScan);
        scheduleRepository.save(schedule);
        selectedScheduleService.setSchedule(schedule);
        selectedScheduleService.start();
        ui.onScheduleChanged(schedule);
        ui.log("[VWAP Desk] Autonomous schedule set: scan " + schedule.scanTimeEt() + " ET"
                + (executeAfterScan ? " with auto-execute." : " (recommendation only).") + " NeuralArc must be running.");
        JOptionPane.showMessageDialog(ui.dialogParent(),
                "Autonomous VWAP Desk scan scheduled for " + schedule.scanTimeEt() + " ET on trading days.\n"
                        + (executeAfterScan ? "Trades will be armed automatically after each scan.\n" : "")
                        + "NeuralArc must be running at that time.",
                "VWAP Desk Schedule", JOptionPane.INFORMATION_MESSAGE);
    }

    /** Cancel and forget the current schedule. */
    void cancelSchedule() {
        String workspaceId = ui.selectedWorkspaceId();
        if (workspaceId == null) {
            return;
        }
        VwapScheduleService selectedScheduleService = scheduleServicesByWorkspace.get(workspaceId);
        VwapSchedule existing = selectedScheduleService == null ? null : selectedScheduleService.schedule();
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
        ui.log("[VWAP Desk] Autonomous schedule cancelled.");
    }

    private VwapScheduleService scheduleServiceForWorkspace(String workspaceId) {
        String key = workspaceId == null ? "" : workspaceId;
        return scheduleServicesByWorkspace.computeIfAbsent(key, ignored ->
                new VwapScheduleService(marketHoursService, Clock.systemUTC(),
                        schedule -> SwingUtilities.invokeLater(() -> runScheduled(schedule)), ui::log));
    }

    private void runScheduled(VwapSchedule schedule) {
        if (schedule == null) {
            return;
        }
        runScan(schedule.workspaceId(), schedule.config(), schedule.executeAfterScan(), false);
    }

    /**
     * Shared scan path for both manual and scheduled runs. Interactive runs surface dialogs and
     * toggle the scan buttons; scheduled runs only log.
     */
    private void runScan(String workspaceId, VwapConfig config, boolean executeRequested, boolean interactive) {
        if (!ui.connectionOk() || ui.runtimeApiKey().isBlank()) {
            String message = ui.selectedModeLabel()
                    + " Alpaca credentials are required before scanning VWAP Desk live market data.";
            if (interactive) {
                JOptionPane.showMessageDialog(ui.dialogParent(), message, "VWAP Desk Analysis", JOptionPane.WARNING_MESSAGE);
            } else {
                ui.log("[VWAP Desk] Scheduled scan skipped: " + message);
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
                                "No live VWAP Desk candidates were found right now. Try again during the regular session, "
                                        + "or enter symbols manually.",
                                "VWAP Desk Analysis", JOptionPane.INFORMATION_MESSAGE));
                    } else {
                        ui.log("[VWAP Desk] Scheduled scan found no live candidates right now.");
                    }
                    return;
                }
                VwapLiveScanner scanner = new VwapLiveScanner(
                        new HttpAlpacaMarketDataApi(ui.runtimeApiKey(), ui.runtimeApiSecret()),
                        Clock.systemDefaultZone(), ui::log);
                VwapAnalyzer analyzer = new VwapAnalyzer(Clock.systemUTC(), ui::log);
                List<VwapCandidate> scanned = scanner.candidates(symbols);
                List<VwapRecommendation> recommendations = analyzer.analyze(scanned, config);
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
     * the day's discounted leaders from Alpaca's screener so no manual monitoring is required.
     */
    private List<String> resolveCandidateSymbols(VwapConfig config) {
        if (!config.candidateSymbols().isEmpty()) {
            return config.candidateSymbols();
        }
        try {
            VwapDiscoveryService discovery = new VwapDiscoveryService(
                    new HttpAlpacaScreenerClient(ui.runtimeApiKey(), ui.runtimeApiSecret()));
            int target = Math.max(30, config.maxStocksToAdd() * 3);
            List<String> discovered = discovery.discoverCandidates(config, target);
            ui.log("[VWAP Desk] Auto-discovered " + discovered.size() + " candidate(s) from Alpaca screener.");
            return discovered;
        } catch (AlpacaScreenerException ex) {
            ui.log("[VWAP Desk] Auto-discovery failed: " + ex.getMessage());
            return List.of();
        }
    }

    private void applyRecommendations(String workspaceId, VwapConfig config, boolean executeRequested,
                                      boolean interactive, List<VwapRecommendation> recommendations) {
        if (workspaceId == null) {
            return;
        }
        if (recommendations.isEmpty()) {
            recordScan(workspaceId, interactive, "No qualifying candidates");
            if (interactive) {
                JOptionPane.showMessageDialog(ui.dialogParent(),
                        "No VWAP Desk candidates met the current live-data filters. Try widening the discount range, or lowering the relative-volume, trend, or price requirements.",
                        "VWAP Desk Analysis", JOptionPane.INFORMATION_MESSAGE);
            } else {
                ui.log("[VWAP Desk] Scheduled scan produced no qualifying candidates.");
            }
            return;
        }
        VwapStrategyFactory factory = new VwapStrategyFactory();
        int added = 0;
        int updated = 0;
        int skipped = 0;
        String firstAddedStrategyId = null;
        for (VwapRecommendation recommendation : recommendations) {
            Optional<Strategy> existing = findTrackedStrategy(recommendation.symbol(), config.mode(), workspaceId);
            if (existing.isPresent()) {
                Strategy existingStrategy = existing.get();
                if (isPendingOrderPlacement(existingStrategy)) {
                    if (!interactive && ScheduledScanDuplicatePolicy.samePlannedPrices(
                            existingStrategy,
                            recommendation.plannedEntryPrice(),
                            recommendation.stopLossPrice(),
                            recommendation.targetPrice()
                    )) {
                        skipped++;
                        ui.log("[VWAP Desk] Skipped " + recommendation.symbol()
                                + ": scheduled scan already has the same planned prices in this workspace.");
                        continue;
                    }
                    existingStrategy.setBaseBuyLimitPrice(recommendation.plannedEntryPrice());
                    existingStrategy.setStopLossPrice(recommendation.stopLossPrice());
                    existingStrategy.setTargetSellPrice(recommendation.targetPrice());
                    existingStrategy.setLastEvent("VWAP Desk recommendation refreshed: score=" + recommendation.strategyScore()
                            + ", plannedEntry=$" + recommendation.plannedEntryPrice().toPlainString()
                            + ", target(VWAP)=$" + recommendation.targetPrice().toPlainString()
                            + ". No broker order was submitted.");
                    strategyRepository.save(existingStrategy);
                    updated++;
                } else {
                    skipped++;
                    ui.log("[VWAP Desk] Skipped " + recommendation.symbol() + ": already has an order or active state in this strategy tab.");
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
            ui.log("[VWAP Desk] Added " + recommendation.symbol()
                    + " score=" + recommendation.strategyScore()
                    + " discount=" + recommendation.discountPercent().toPlainString() + "%"
                    + " plannedEntry=$" + recommendation.plannedEntryPrice().toPlainString()
                    + " mode=" + recommendation.mode()
                    + (executeRequested ? " monitoring enabled" : " recommendation only") + ".");
        }
        recordScan(workspaceId, interactive, ScanHistoryEntry.summarize(added, updated, skipped));
        ui.onRecommendationsApplied(workspaceId, firstAddedStrategyId);
        ui.log("[VWAP Desk] Added " + added + " VWAP Desk candidate" + (added == 1 ? "" : "s")
                + " to the VWAP Desk grid"
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
