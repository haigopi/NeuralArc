package com.neuralarc.ui;

import com.neuralarc.api.HttpAlpacaMarketDataApi;
import com.neuralarc.db.AppDatabase;
import com.neuralarc.db.SqliteProfitShieldScheduleRepository;
import com.neuralarc.db.SqliteScanHistoryRepository;
import com.neuralarc.db.SqliteStrategyRepository;
import com.neuralarc.model.ProfitShieldSchedule;
import com.neuralarc.model.ScanHistoryEntry;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.profitshield.ProfitShieldAnalyzer;
import com.neuralarc.profitshield.ProfitShieldCandidate;
import com.neuralarc.profitshield.ProfitShieldConfig;
import com.neuralarc.profitshield.ProfitShieldLiveScanner;
import com.neuralarc.profitshield.ProfitShieldRecommendation;
import com.neuralarc.profitshield.ProfitShieldStrategyFactory;
import com.neuralarc.service.AlpacaScreenerException;
import com.neuralarc.service.HttpAlpacaScreenerClient;
import com.neuralarc.service.MarketHoursService;
import com.neuralarc.service.ProfitShieldDiscoveryService;
import com.neuralarc.service.ProfitShieldScheduleService;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Owns the autonomous Profit Shield pipeline (discover → scan → analyze → apply) and its once-per-day
 * scheduling engine, keeping this logic out of {@code TradingFrame}. The frame supplies a thin
 * {@link Ui} gateway for credentials, the selected workspace, logging, button state, and the
 * post-apply UI refresh; it does not contain any of the orchestration itself.
 */
final class ProfitShieldCoordinator {
    static final String RECOMMENDED_STATUS = "PROFIT_SHIELD_RECOMMENDED";
    static final String MONITORING_STATUS = "PROFIT_SHIELD_MONITORING";

    /** Callbacks the coordinator needs from the host frame. */
    interface Ui {
        String runtimeApiKey();
        String runtimeApiSecret();
        boolean connectionOk();
        String selectedModeLabel();
        int defaultStrategyPollingSeconds();
        String selectedWorkspaceId();
        boolean isProfitShieldWorkspaceSelected();
        void log(String message);
        void setScanButtonsEnabled(boolean enabled);
        void onRecommendationsApplied(String workspaceId, String firstAddedStrategyId);
        void onScheduleChanged(ProfitShieldSchedule schedule);
        java.awt.Component dialogParent();
    }

    private final Ui ui;
    private final SqliteStrategyRepository strategyRepository;
    private final SqliteProfitShieldScheduleRepository scheduleRepository;
    private final SqliteScanHistoryRepository scanHistoryRepository;
    private final MarketHoursService marketHoursService;
    private final Map<String, ProfitShieldScheduleService> scheduleServicesByWorkspace = new LinkedHashMap<>();
    private final Executor backgroundExecutor;

    ProfitShieldCoordinator(Ui ui, AppDatabase database, SqliteStrategyRepository strategyRepository,
                            MarketHoursService marketHoursService,
                            SqliteScanHistoryRepository scanHistoryRepository, Executor backgroundExecutor) {
        this.ui = ui;
        this.strategyRepository = strategyRepository;
        this.backgroundExecutor = backgroundExecutor;
        this.marketHoursService = marketHoursService == null ? new MarketHoursService() : marketHoursService;
        this.scheduleRepository = new SqliteProfitShieldScheduleRepository(database);
        this.scanHistoryRepository = scanHistoryRepository;
    }

    static boolean isPendingOrderPlacement(Strategy strategy) {
        return strategy != null && RECOMMENDED_STATUS.equalsIgnoreCase(strategy.latestOrderStatus());
    }

    /** Load any persisted enabled schedule and start the regular-session scheduler. */
    void start() {
        scheduleRepository.findAll().stream()
                .filter(ProfitShieldSchedule::enabled)
                .forEach(schedule -> {
                    ProfitShieldScheduleService service = scheduleServiceForWorkspace(schedule.workspaceId());
                    service.setSchedule(schedule);
                    service.start();
                    ui.log("[Profit Shield] Restored autonomous schedule: scan " + schedule.scanTimeEt()
                            + " ET for workspace " + schedule.workspaceId()
                            + (schedule.executeAfterScan() ? " (auto-execute)." : " (recommendation only)."));
                });
        ui.onScheduleChanged(currentSchedule());
    }

    ProfitShieldSchedule currentSchedule() {
        String workspaceId = ui.selectedWorkspaceId();
        if (workspaceId == null) {
            return null;
        }
        ProfitShieldScheduleService service = scheduleServicesByWorkspace.get(workspaceId);
        ProfitShieldSchedule inMemory = service == null ? null : service.schedule();
        if (inMemory != null) {
            return inMemory;
        }
        return scheduleRepository.findByWorkspaceId(workspaceId)
                .filter(ProfitShieldSchedule::enabled)
                .orElse(null);
    }

    /** Run an interactive scan for the selected workspace. */
    void analyze(ProfitShieldConfig config, boolean executeRequested) {
        if (ui.selectedWorkspaceId() == null || !ui.isProfitShieldWorkspaceSelected()) {
            return;
        }
        runScan(ui.selectedWorkspaceId(), config, executeRequested, true);
    }

    /** Register a new schedule, or offer to cancel the existing one for the selected workspace. */
    void scheduleOrCancel(ProfitShieldConfig config) {
        String workspaceId = ui.selectedWorkspaceId();
        if (workspaceId == null || !ui.isProfitShieldWorkspaceSelected()) {
            return;
        }
        ProfitShieldScheduleService selectedScheduleService = scheduleServiceForWorkspace(workspaceId);
        ProfitShieldSchedule existing = selectedScheduleService.schedule();
        if (existing == null) {
            existing = scheduleRepository.findByWorkspaceId(workspaceId)
                    .filter(ProfitShieldSchedule::enabled)
                    .orElse(null);
            if (existing != null) {
                selectedScheduleService.setSchedule(existing);
                selectedScheduleService.start();
            }
        }
        if (existing != null && workspaceId.equals(existing.workspaceId())) {
            int cancel = JOptionPane.showConfirmDialog(ui.dialogParent(),
                    "An autonomous Profit Shield scan already runs at " + existing.scanTimeEt() + " ET for this workspace.\n"
                            + "Cancel it?",
                    "Profit Shield Schedule", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (cancel == JOptionPane.YES_OPTION) {
                cancelSchedule();
            }
            return;
        }
        int execute = JOptionPane.showConfirmDialog(ui.dialogParent(),
                "Schedule an autonomous Profit Shield scan at " + ProfitShieldSchedule.DEFAULT_SCAN_TIME_ET
                        + " ET once per trading day.\n\n"
                        + "Auto-execute trades after each scan? (No = build the recommendation list only.)\n\n"
                        + "Note: NeuralArc must be running at the scheduled time.",
                "Profit Shield Schedule", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (execute == JOptionPane.CANCEL_OPTION || execute == JOptionPane.CLOSED_OPTION) {
            return;
        }
        boolean executeAfterScan = execute == JOptionPane.YES_OPTION;
        ProfitShieldSchedule schedule = ProfitShieldSchedule.create(workspaceId, config, executeAfterScan);
        scheduleRepository.save(schedule);
        selectedScheduleService.setSchedule(schedule);
        selectedScheduleService.start();
        ui.onScheduleChanged(schedule);
        ui.log("[Profit Shield] Autonomous schedule set: scan " + schedule.scanTimeEt() + " ET"
                + (executeAfterScan ? " with auto-execute." : " (recommendation only).") + " NeuralArc must be running.");
        JOptionPane.showMessageDialog(ui.dialogParent(),
                "Autonomous Profit Shield scan scheduled for " + schedule.scanTimeEt() + " ET on trading days.\n"
                        + (executeAfterScan ? "Trades will be armed automatically after each scan.\n" : "")
                        + "NeuralArc must be running at that time.",
                "Profit Shield Schedule", JOptionPane.INFORMATION_MESSAGE);
    }

    /** Cancel and forget the current schedule. */
    void cancelSchedule() {
        String workspaceId = ui.selectedWorkspaceId();
        if (workspaceId == null) {
            return;
        }
        ProfitShieldScheduleService selectedScheduleService = scheduleServicesByWorkspace.get(workspaceId);
        ProfitShieldSchedule existing = selectedScheduleService == null ? null : selectedScheduleService.schedule();
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
        ui.log("[Profit Shield] Autonomous schedule cancelled.");
    }

    private ProfitShieldScheduleService scheduleServiceForWorkspace(String workspaceId) {
        String key = workspaceId == null ? "" : workspaceId;
        return scheduleServicesByWorkspace.computeIfAbsent(key, ignored ->
                new ProfitShieldScheduleService(marketHoursService, Clock.systemUTC(),
                        schedule -> SwingUtilities.invokeLater(() -> runScheduled(schedule)), ui::log));
    }

    private void runScheduled(ProfitShieldSchedule schedule) {
        if (schedule == null) {
            return;
        }
        runScan(schedule.workspaceId(), schedule.config(), schedule.executeAfterScan(), false);
    }

    /**
     * Shared scan path for both manual and scheduled runs. Interactive runs surface dialogs and
     * toggle the scan buttons; scheduled runs only log.
     */
    private void runScan(String workspaceId, ProfitShieldConfig config, boolean executeRequested, boolean interactive) {
        if (!ui.connectionOk() || ui.runtimeApiKey().isBlank()) {
            String message = ui.selectedModeLabel()
                    + " Alpaca credentials are required before scanning Profit Shield live market data.";
            if (interactive) {
                JOptionPane.showMessageDialog(ui.dialogParent(), message, "Profit Shield Analysis", JOptionPane.WARNING_MESSAGE);
            } else {
                ui.log("[Profit Shield] Scheduled scan skipped: " + message);
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
                                "No live Profit Shield candidates were found right now. Try again during the regular session, "
                                        + "or enter symbols manually.",
                                "Profit Shield Analysis", JOptionPane.INFORMATION_MESSAGE));
                    } else {
                        ui.log("[Profit Shield] Scheduled scan found no live candidates right now.");
                    }
                    return;
                }
                ProfitShieldLiveScanner scanner = new ProfitShieldLiveScanner(
                        new HttpAlpacaMarketDataApi(ui.runtimeApiKey(), ui.runtimeApiSecret()),
                        Clock.systemDefaultZone(), ui::log);
                ProfitShieldAnalyzer analyzer = new ProfitShieldAnalyzer(Clock.systemUTC(), ui::log);
                List<ProfitShieldCandidate> scanned = scanner.candidates(symbols, config);
                List<ProfitShieldRecommendation> recommendations = analyzer.analyze(scanned, config);
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
     * day's most actively traded stocks from Alpaca's screener and let the defensive filters decide.
     */
    private List<String> resolveCandidateSymbols(ProfitShieldConfig config) {
        if (!config.candidateSymbols().isEmpty()) {
            return config.candidateSymbols();
        }
        try {
            ProfitShieldDiscoveryService discovery = new ProfitShieldDiscoveryService(
                    new HttpAlpacaScreenerClient(ui.runtimeApiKey(), ui.runtimeApiSecret()));
            int target = Math.max(50, config.maxStocksToAdd() * 5);
            List<String> discovered = discovery.discoverCandidates(config, target);
            ui.log("[Profit Shield] Auto-discovered " + discovered.size() + " candidate(s) from Alpaca screener.");
            return discovered;
        } catch (AlpacaScreenerException ex) {
            ui.log("[Profit Shield] Auto-discovery failed: " + ex.getMessage());
            return List.of();
        }
    }

    private void applyRecommendations(String workspaceId, ProfitShieldConfig config, boolean executeRequested,
                                      boolean interactive, List<ProfitShieldRecommendation> recommendations) {
        if (workspaceId == null) {
            return;
        }
        if (recommendations.isEmpty()) {
            recordScan(workspaceId, interactive, "No qualifying candidates");
            if (interactive) {
                JOptionPane.showMessageDialog(ui.dialogParent(),
                        "No Profit Shield candidates met the current live-data filters. Try raising Maximum Daily Volatility "
                                + "or Maximum Drawdown, widening Maximum Distance Below High, or relaxing the trend filter.",
                        "Profit Shield Analysis", JOptionPane.INFORMATION_MESSAGE);
            } else {
                ui.log("[Profit Shield] Scheduled scan produced no qualifying candidates.");
            }
            return;
        }
        ProfitShieldStrategyFactory factory = new ProfitShieldStrategyFactory();
        int added = 0;
        int updated = 0;
        int skipped = 0;
        String firstAddedStrategyId = null;
        for (ProfitShieldRecommendation recommendation : recommendations) {
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
                        ui.log("[Profit Shield] Skipped " + recommendation.symbol()
                                + ": scheduled scan already has the same planned prices in this workspace.");
                        continue;
                    }
                    existingStrategy.setBaseBuyLimitPrice(recommendation.plannedEntryPrice());
                    existingStrategy.setStopLossPrice(recommendation.stopLossPrice());
                    existingStrategy.setTargetSellPrice(recommendation.targetPrice());
                    existingStrategy.setLastEvent("Profit Shield recommendation refreshed: score="
                            + recommendation.strategyScore()
                            + ", protectiveStop=$" + recommendation.stopLossPrice().toPlainString()
                            + " (" + recommendation.stopLossPercent().toPlainString() + "%)"
                            + ". No broker order was submitted.");
                    strategyRepository.save(existingStrategy);
                    updated++;
                } else {
                    skipped++;
                    ui.log("[Profit Shield] Skipped " + recommendation.symbol()
                            + ": already has an order or active state in this strategy tab.");
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
            ui.log("[Profit Shield] Added " + recommendation.symbol()
                    + " score=" + recommendation.strategyScore()
                    + " protectionScore=" + recommendation.protectionScore()
                    + " volatility=" + recommendation.atrPercent().toPlainString() + "%"
                    + " maxDrawdown=" + recommendation.maxDrawdownPercent().toPlainString() + "%"
                    + " plannedEntry=$" + recommendation.plannedEntryPrice().toPlainString()
                    + " protectiveStop=$" + recommendation.stopLossPrice().toPlainString()
                    + " mode=" + recommendation.mode()
                    + (executeRequested ? " monitoring enabled" : " recommendation only") + ".");
        }
        recordScan(workspaceId, interactive, ScanHistoryEntry.summarize(added, updated, skipped));
        ui.onRecommendationsApplied(workspaceId, firstAddedStrategyId);
        ui.log("[Profit Shield] Added " + added + " Profit Shield candidate" + (added == 1 ? "" : "s")
                + " to the Profit Shield grid"
                + (updated > 0 ? "; refreshed " + updated + " existing pending row" + (updated == 1 ? "" : "s") : "")
                + (skipped > 0 ? "; skipped " + skipped + " duplicate symbol" + (skipped == 1 ? "" : "s") : "")
                + ". No broker orders were submitted.");
    }

    private void recordScan(String workspaceId, boolean interactive, String summary) {
        if (scanHistoryRepository == null || workspaceId == null) {
            return;
        }
        scanHistoryRepository.save(ScanHistoryEntry.now(workspaceId, interactive, summary));
    }

    private Optional<Strategy> findTrackedStrategy(String symbol, StrategyMode mode, String workspaceId) {
        return strategyRepository.findAll().stream()
                .filter(strategy -> strategy.mode() == mode)
                .filter(strategy -> workspaceId.equals(strategy.workspaceId()))
                .filter(strategy -> strategy.symbol().equalsIgnoreCase(symbol))
                .findFirst();
    }
}
