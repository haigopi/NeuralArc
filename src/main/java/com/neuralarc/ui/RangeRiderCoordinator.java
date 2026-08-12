package com.neuralarc.ui;

import com.neuralarc.api.HttpAlpacaMarketDataApi;
import com.neuralarc.db.AppDatabase;
import com.neuralarc.db.SqliteRangeRiderScheduleRepository;
import com.neuralarc.db.SqliteScanHistoryRepository;
import com.neuralarc.db.SqliteStrategyRepository;
import com.neuralarc.model.RangeRiderSchedule;
import com.neuralarc.model.ScanHistoryEntry;
import com.neuralarc.model.Strategy;
import com.neuralarc.rangerider.RangeRiderAnalyzer;
import com.neuralarc.rangerider.RangeRiderCandidate;
import com.neuralarc.rangerider.RangeRiderConfig;
import com.neuralarc.rangerider.RangeRiderLiveScanner;
import com.neuralarc.rangerider.RangeRiderRecommendation;
import com.neuralarc.rangerider.RangeRiderStrategyFactory;
import com.neuralarc.service.AlpacaScreenerException;
import com.neuralarc.service.HttpAlpacaScreenerClient;
import com.neuralarc.service.MarketHoursService;
import com.neuralarc.service.RangeRiderDiscoveryService;
import com.neuralarc.service.RangeRiderScheduleService;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Owns the autonomous Range Rider pipeline (discover → scan → analyze → apply) and the regular-session
 * scheduling engine, keeping this logic out of {@code TradingFrame}. The frame supplies a thin
 * {@link Ui} gateway for credentials, the selected workspace, logging, button state, and the
 * post-apply UI refresh; it does not contain any of the orchestration itself.
 */
final class RangeRiderCoordinator {
    static final String RECOMMENDED_STATUS = "RANGE_RIDER_RECOMMENDED";
    static final String MONITORING_STATUS = "RANGE_RIDER_MONITORING";

    /** Callbacks the coordinator needs from the host frame. */
    interface Ui {
        String runtimeApiKey();
        String runtimeApiSecret();
        boolean connectionOk();
        String selectedModeLabel();
        int defaultStrategyPollingSeconds();
        String selectedWorkspaceId();
        boolean isRangeRiderWorkspaceSelected();
        void log(String message);
        void setScanButtonsEnabled(boolean enabled);
        void onRecommendationsApplied(String workspaceId, String firstAddedStrategyId);
        void onScheduleChanged(RangeRiderSchedule schedule);
        java.awt.Component dialogParent();
    }

    private final Ui ui;
    private final SqliteStrategyRepository strategyRepository;
    private final SqliteRangeRiderScheduleRepository scheduleRepository;
    private final SqliteScanHistoryRepository scanHistoryRepository;
    private final MarketHoursService marketHoursService;
    private final Map<String, RangeRiderScheduleService> scheduleServicesByWorkspace = new LinkedHashMap<>();
    private final Executor backgroundExecutor;

    RangeRiderCoordinator(Ui ui, AppDatabase database, SqliteStrategyRepository strategyRepository,
                          MarketHoursService marketHoursService,
                          SqliteScanHistoryRepository scanHistoryRepository, Executor backgroundExecutor) {
        this.ui = ui;
        this.strategyRepository = strategyRepository;
        this.backgroundExecutor = backgroundExecutor;
        this.marketHoursService = marketHoursService == null ? new MarketHoursService() : marketHoursService;
        this.scheduleRepository = new SqliteRangeRiderScheduleRepository(database);
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
                .filter(RangeRiderSchedule::enabled)
                .forEach(schedule -> {
                    RangeRiderScheduleService service = scheduleServiceForWorkspace(schedule.workspaceId());
                    service.setSchedule(schedule);
                    service.start();
                    ui.log("[Range Rider] Restored autonomous schedule: scan " + schedule.scanTimeEt()
                            + " ET for workspace " + schedule.workspaceId()
                            + (schedule.executeAfterScan() ? " (auto-execute)." : " (recommendation only)."));
                });
        ui.onScheduleChanged(currentSchedule());
    }

    RangeRiderSchedule currentSchedule() {
        String workspaceId = ui.selectedWorkspaceId();
        if (workspaceId == null) {
            return null;
        }
        RangeRiderScheduleService service = scheduleServicesByWorkspace.get(workspaceId);
        RangeRiderSchedule inMemory = service == null ? null : service.schedule();
        if (inMemory != null) {
            return inMemory;
        }
        return scheduleRepository.findByWorkspaceId(workspaceId)
                .filter(RangeRiderSchedule::enabled)
                .orElse(null);
    }

    /** Run an interactive scan for the selected workspace. */
    void analyze(RangeRiderConfig config, boolean executeRequested) {
        if (ui.selectedWorkspaceId() == null || !ui.isRangeRiderWorkspaceSelected()) {
            return;
        }
        runScan(ui.selectedWorkspaceId(), config, executeRequested, true);
    }

    /** Register a new schedule, or offer to cancel the existing one for the selected workspace. */
    void scheduleOrCancel(RangeRiderConfig config) {
        String workspaceId = ui.selectedWorkspaceId();
        if (workspaceId == null || !ui.isRangeRiderWorkspaceSelected()) {
            return;
        }
        RangeRiderScheduleService selectedScheduleService = scheduleServiceForWorkspace(workspaceId);
        RangeRiderSchedule existing = selectedScheduleService.schedule();
        if (existing == null) {
            existing = scheduleRepository.findByWorkspaceId(workspaceId)
                    .filter(RangeRiderSchedule::enabled)
                    .orElse(null);
            if (existing != null) {
                selectedScheduleService.setSchedule(existing);
                selectedScheduleService.start();
            }
        }
        if (existing != null && workspaceId.equals(existing.workspaceId())) {
            int cancel = JOptionPane.showConfirmDialog(ui.dialogParent(),
                    "An autonomous Range Rider schedule already runs at " + existing.scanTimeEt() + " ET for this workspace.\n"
                            + "Cancel it?",
                    "Range Rider Schedule", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (cancel == JOptionPane.YES_OPTION) {
                cancelSchedule();
            }
            return;
        }
        int execute = JOptionPane.showConfirmDialog(ui.dialogParent(),
                "Schedule an autonomous Range Rider scan at " + RangeRiderSchedule.DEFAULT_SCAN_TIME_ET + " ET on trading days,\n"
                        + "carrying through the 9:45–15:30 ET execution window.\n\n"
                        + "Auto-execute trades after each scan? (No = build the recommendation list only.)\n\n"
                        + "Note: NeuralArc must be running at the scheduled time.",
                "Range Rider Schedule", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (execute == JOptionPane.CANCEL_OPTION || execute == JOptionPane.CLOSED_OPTION) {
            return;
        }
        boolean executeAfterScan = execute == JOptionPane.YES_OPTION;
        RangeRiderSchedule schedule = RangeRiderSchedule.create(workspaceId, config, executeAfterScan);
        scheduleRepository.save(schedule);
        selectedScheduleService.setSchedule(schedule);
        selectedScheduleService.start();
        ui.onScheduleChanged(schedule);
        ui.log("[Range Rider] Autonomous schedule set: scan " + schedule.scanTimeEt() + " ET"
                + (executeAfterScan ? " with auto-execute." : " (recommendation only).") + " NeuralArc must be running.");
        JOptionPane.showMessageDialog(ui.dialogParent(),
                "Autonomous Range Rider scan scheduled for " + schedule.scanTimeEt() + " ET on trading days.\n"
                        + (executeAfterScan ? "Trades will be armed automatically after each scan.\n" : "")
                        + "NeuralArc must be running at that time.",
                "Range Rider Schedule", JOptionPane.INFORMATION_MESSAGE);
    }

    /** Cancel and forget the current schedule. */
    void cancelSchedule() {
        String workspaceId = ui.selectedWorkspaceId();
        if (workspaceId == null) {
            return;
        }
        RangeRiderScheduleService selectedScheduleService = scheduleServicesByWorkspace.get(workspaceId);
        RangeRiderSchedule existing = selectedScheduleService == null ? null : selectedScheduleService.schedule();
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
        ui.log("[Range Rider] Autonomous schedule cancelled.");
    }

    private RangeRiderScheduleService scheduleServiceForWorkspace(String workspaceId) {
        String key = workspaceId == null ? "" : workspaceId;
        return scheduleServicesByWorkspace.computeIfAbsent(key, ignored ->
                new RangeRiderScheduleService(marketHoursService, Clock.systemUTC(),
                        schedule -> SwingUtilities.invokeLater(() -> runScheduled(schedule)), ui::log));
    }

    private void runScheduled(RangeRiderSchedule schedule) {
        if (schedule == null) {
            return;
        }
        runScan(schedule.workspaceId(), schedule.config(), schedule.executeAfterScan(), false);
    }

    /**
     * Shared scan path for both manual and scheduled runs. Interactive runs surface dialogs and
     * toggle the scan buttons; scheduled runs only log.
     */
    private void runScan(String workspaceId, RangeRiderConfig config, boolean executeRequested, boolean interactive) {
        if (!ui.connectionOk() || ui.runtimeApiKey().isBlank()) {
            String message = ui.selectedModeLabel()
                    + " Alpaca credentials are required before scanning Range Rider live market data.";
            if (interactive) {
                JOptionPane.showMessageDialog(ui.dialogParent(), message, "Range Rider Analysis", JOptionPane.WARNING_MESSAGE);
            } else {
                ui.log("[Range Rider] Scheduled scan skipped: " + message);
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
                                "No live Range Rider candidates were found right now. Try again during the regular session, "
                                        + "or enter symbols manually.",
                                "Range Rider Analysis", JOptionPane.INFORMATION_MESSAGE));
                    } else {
                        ui.log("[Range Rider] Scheduled scan found no live candidates right now.");
                    }
                    return;
                }
                RangeRiderLiveScanner scanner = new RangeRiderLiveScanner(
                        new HttpAlpacaMarketDataApi(ui.runtimeApiKey(), ui.runtimeApiSecret()),
                        Clock.systemDefaultZone(), ui::log);
                RangeRiderAnalyzer analyzer = new RangeRiderAnalyzer(Clock.systemUTC(), ui::log);
                List<RangeRiderCandidate> scanned = scanner.candidates(symbols, config.lookbackSessions());
                List<RangeRiderRecommendation> recommendations = analyzer.analyze(scanned, config);
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
     * Resolve the symbols to scan: an operator's manual entry always wins; otherwise auto-discover the
     * day's most actively traded stocks from Alpaca's screener so no manual monitoring is required.
     */
    private List<String> resolveCandidateSymbols(RangeRiderConfig config) {
        if (!config.candidateSymbols().isEmpty()) {
            return config.candidateSymbols();
        }
        try {
            RangeRiderDiscoveryService discovery = new RangeRiderDiscoveryService(
                    new HttpAlpacaScreenerClient(ui.runtimeApiKey(), ui.runtimeApiSecret()));
            int target = Math.max(30, config.maxStocksToAdd() * 3);
            List<String> discovered = discovery.discoverCandidates(config, target);
            ui.log("[Range Rider] Auto-discovered " + discovered.size() + " candidate(s) from Alpaca screener.");
            return discovered;
        } catch (AlpacaScreenerException ex) {
            ui.log("[Range Rider] Auto-discovery failed: " + ex.getMessage());
            return List.of();
        }
    }

    private void applyRecommendations(String workspaceId, RangeRiderConfig config, boolean executeRequested,
                                      boolean interactive, List<RangeRiderRecommendation> recommendations) {
        if (workspaceId == null) {
            return;
        }
        if (recommendations.isEmpty()) {
            recordScan(workspaceId, interactive, "No qualifying candidates");
            if (interactive) {
                JOptionPane.showMessageDialog(ui.dialogParent(),
                        "No Range Rider candidates met the current live-data filters. Try widening the average daily range "
                                + "bounds, lowering the minimum same-day fill rate, or relaxing the volume and price requirements.",
                        "Range Rider Analysis", JOptionPane.INFORMATION_MESSAGE);
            } else {
                ui.log("[Range Rider] Scheduled scan produced no qualifying candidates.");
            }
            return;
        }
        RangeRiderStrategyFactory factory = new RangeRiderStrategyFactory();
        int added = 0;
        int updated = 0;
        int skipped = 0;
        String firstAddedStrategyId = null;
        for (RangeRiderRecommendation recommendation : recommendations) {
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
                        ui.log("[Range Rider] Skipped " + recommendation.symbol()
                                + ": scheduled scan already has the same planned prices in this workspace.");
                        continue;
                    }
                    existingStrategy.setBaseBuyLimitPrice(recommendation.plannedEntryPrice());
                    existingStrategy.setStopLossPrice(recommendation.stopLossPrice());
                    existingStrategy.setTargetSellPrice(recommendation.targetPrice());
                    existingStrategy.setLastEvent("Range Rider recommendation refreshed: score=" + recommendation.strategyScore()
                            + ", plannedBuy=$" + recommendation.plannedEntryPrice().toPlainString()
                            + ", plannedSell=$" + recommendation.targetPrice().toPlainString()
                            + ". No broker order was submitted.");
                    strategyRepository.save(existingStrategy);
                    updated++;
                } else {
                    skipped++;
                    ui.log("[Range Rider] Skipped " + recommendation.symbol() + ": already has an order or active state in this strategy tab.");
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
            ui.log("[Range Rider] Added " + recommendation.symbol()
                    + " score=" + recommendation.strategyScore()
                    + " avgLow=$" + recommendation.averageLow().toPlainString()
                    + " avgHigh=$" + recommendation.averageHigh().toPlainString()
                    + " plannedBuy=$" + recommendation.plannedEntryPrice().toPlainString()
                    + " plannedSell=$" + recommendation.targetPrice().toPlainString()
                    + " sameDayFillRate=" + recommendation.sameDayFillRatePercent().toPlainString() + "%"
                    + " mode=" + recommendation.mode()
                    + (executeRequested ? " monitoring enabled" : " recommendation only") + ".");
        }
        recordScan(workspaceId, interactive, ScanHistoryEntry.summarize(added, updated, skipped));
        ui.onRecommendationsApplied(workspaceId, firstAddedStrategyId);
        ui.log("[Range Rider] Added " + added + " Range Rider candidate" + (added == 1 ? "" : "s")
                + " to the Range Rider grid"
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
