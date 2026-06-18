package com.neuralarc.ui;

import com.neuralarc.api.HttpAlpacaMarketDataApi;
import com.neuralarc.db.AppDatabase;
import com.neuralarc.db.SqliteOrbScheduleRepository;
import com.neuralarc.db.SqliteStrategyRepository;
import com.neuralarc.model.AiProviderType;
import com.neuralarc.model.AiRecommendationSettings;
import com.neuralarc.model.OrbSchedule;
import com.neuralarc.model.Strategy;
import com.neuralarc.orb.OpeningRangeCaptureService;
import com.neuralarc.orb.OpeningRangeSnapshot;
import com.neuralarc.orb.OrbAiInsightService;
import com.neuralarc.orb.OrbAnalyzer;
import com.neuralarc.orb.OrbCandidate;
import com.neuralarc.orb.OrbConfig;
import com.neuralarc.orb.OrbDiscoveryService;
import com.neuralarc.orb.OrbRecommendation;
import com.neuralarc.orb.OrbRunMode;
import com.neuralarc.orb.OrbStrategyFactory;
import com.neuralarc.service.AiRecommendationProviderFactory;
import com.neuralarc.service.AlpacaScreenerException;
import com.neuralarc.service.AppSettingsService;
import com.neuralarc.service.HttpAlpacaScreenerClient;
import com.neuralarc.service.MarketHoursService;
import com.neuralarc.service.OrbScheduleService;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

final class OrbCoordinator {
    static final String RECOMMENDED_STATUS = "ORB_RECOMMENDED";
    static final String ARMED_STATUS = "ORB_ARMED";

    interface Ui {
        String runtimeApiKey();
        String runtimeApiSecret();
        boolean connectionOk();
        String selectedModeLabel();
        int defaultStrategyPollingSeconds();
        String selectedWorkspaceId();
        boolean isOrbWorkspaceSelected();
        void log(String message);
        void setAnalyzeButtonEnabled(boolean enabled);
        void onRecommendationsApplied(String workspaceId, String firstAddedStrategyId);
        void onScheduleChanged(OrbSchedule schedule);
        java.awt.Component dialogParent();
    }

    private final Ui ui;
    private final SqliteStrategyRepository strategyRepository;
    private final AppSettingsService appSettingsService;
    private final SqliteOrbScheduleRepository scheduleRepository;
    private final MarketHoursService marketHoursService;
    private final Map<String, OrbScheduleService> scheduleServicesByWorkspace = new LinkedHashMap<>();
    private final Executor backgroundExecutor;

    OrbCoordinator(Ui ui, AppDatabase database, SqliteStrategyRepository strategyRepository,
                   AppSettingsService appSettingsService, MarketHoursService marketHoursService,
                   Executor backgroundExecutor) {
        this.ui = ui;
        this.strategyRepository = strategyRepository;
        this.appSettingsService = appSettingsService;
        this.backgroundExecutor = backgroundExecutor;
        this.marketHoursService = marketHoursService == null ? new MarketHoursService() : marketHoursService;
        this.scheduleRepository = new SqliteOrbScheduleRepository(database);
    }

    static boolean isPendingOrderPlacement(Strategy strategy) {
        return strategy != null && (RECOMMENDED_STATUS.equalsIgnoreCase(strategy.latestOrderStatus())
                || ARMED_STATUS.equalsIgnoreCase(strategy.latestOrderStatus()));
    }

    /** Load any persisted enabled schedule and start the post-range scheduler. */
    void start() {
        scheduleRepository.findAll().stream()
                .filter(OrbSchedule::enabled)
                .forEach(schedule -> {
                    OrbScheduleService service = scheduleServiceForWorkspace(schedule.workspaceId());
                    service.setSchedule(schedule);
                    service.start();
                    ui.log("[ORB] Restored autonomous schedule: analysis " + schedule.rangeAnalysisTimeEt()
                            + " ET for workspace " + schedule.workspaceId()
                            + (schedule.executeAfterRangeClose() ? " (auto-execute)." : " (recommendation only)."));
                });
        ui.onScheduleChanged(currentSchedule());
    }

    OrbSchedule currentSchedule() {
        String workspaceId = ui.selectedWorkspaceId();
        if (workspaceId == null) {
            return null;
        }
        OrbScheduleService service = scheduleServicesByWorkspace.get(workspaceId);
        OrbSchedule inMemory = service == null ? null : service.schedule();
        if (inMemory != null) {
            return inMemory;
        }
        return scheduleRepository.findByWorkspaceId(workspaceId)
                .filter(OrbSchedule::enabled)
                .orElse(null);
    }

    void run(OrbConfig config, OrbRunMode mode) {
        if (ui.selectedWorkspaceId() == null || !ui.isOrbWorkspaceSelected()) {
            return;
        }
        if (mode == OrbRunMode.SCHEDULE) {
            scheduleOrCancel(config);
            return;
        }
        runAnalysis(ui.selectedWorkspaceId(), config, mode == OrbRunMode.ANALYZE_AND_ARM_NOW);
    }

    /** Register a new schedule, or offer to cancel the existing one for the selected workspace. */
    void scheduleOrCancel(OrbConfig config) {
        String workspaceId = ui.selectedWorkspaceId();
        if (workspaceId == null || !ui.isOrbWorkspaceSelected()) {
            return;
        }
        OrbScheduleService selectedService = scheduleServiceForWorkspace(workspaceId);
        OrbSchedule existing = selectedService.schedule();
        if (existing == null) {
            existing = scheduleRepository.findByWorkspaceId(workspaceId)
                    .filter(OrbSchedule::enabled)
                    .orElse(null);
            if (existing != null) {
                selectedService.setSchedule(existing);
                selectedService.start();
            }
        }
        if (existing != null && workspaceId.equals(existing.workspaceId())) {
            int cancel = JOptionPane.showConfirmDialog(ui.dialogParent(),
                    "An autonomous ORB schedule already runs at " + existing.rangeAnalysisTimeEt()
                            + " ET for this workspace.\nCancel it?",
                    "ORB Schedule", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (cancel == JOptionPane.YES_OPTION) {
                cancelSchedule();
            }
            return;
        }
        OrbConfig safeConfig = config == null ? OrbConfig.defaults(null) : config;
        String analysisTime = OrbSchedule.create(workspaceId, safeConfig, false).rangeAnalysisTimeEt().toString();
        int execute = JOptionPane.showConfirmDialog(ui.dialogParent(),
                "Schedule an autonomous ORB analysis at " + analysisTime + " ET on trading days\n"
                        + "(after the " + safeConfig.rangeDurationMinutes() + "-minute opening range closes),\n"
                        + "within the execution window up to " + safeConfig.latestEntryTimeEt() + " ET.\n\n"
                        + "Auto-execute trades after analysis? (No = build the recommendation list only.)\n\n"
                        + "Note: NeuralArc must be running at the scheduled time.",
                "ORB Schedule", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (execute == JOptionPane.CANCEL_OPTION || execute == JOptionPane.CLOSED_OPTION) {
            return;
        }
        boolean executeAfterRangeClose = execute == JOptionPane.YES_OPTION;
        OrbSchedule schedule = OrbSchedule.create(workspaceId, safeConfig, executeAfterRangeClose);
        scheduleRepository.save(schedule);
        selectedService.setSchedule(schedule);
        selectedService.start();
        ui.onScheduleChanged(schedule);
        ui.log("[ORB] Autonomous schedule set: analysis " + schedule.rangeAnalysisTimeEt() + " ET"
                + (executeAfterRangeClose ? " with auto-execute." : " (recommendation only).")
                + " NeuralArc must be running.");
        JOptionPane.showMessageDialog(ui.dialogParent(),
                "Autonomous ORB analysis scheduled for " + schedule.rangeAnalysisTimeEt() + " ET on trading days.\n"
                        + (executeAfterRangeClose ? "Trades will be armed automatically after each analysis.\n" : "")
                        + "NeuralArc must be running at that time.",
                "ORB Schedule", JOptionPane.INFORMATION_MESSAGE);
    }

    /** Cancel and forget the current schedule for the selected workspace. */
    void cancelSchedule() {
        String workspaceId = ui.selectedWorkspaceId();
        if (workspaceId == null) {
            return;
        }
        OrbScheduleService selectedService = scheduleServicesByWorkspace.get(workspaceId);
        OrbSchedule existing = selectedService == null ? null : selectedService.schedule();
        if (existing == null) {
            return;
        }
        selectedService.clearSchedule();
        scheduleRepository.deleteById(existing.id());
        ui.onScheduleChanged(null);
        ui.log("[ORB] Autonomous schedule cancelled.");
    }

    private OrbScheduleService scheduleServiceForWorkspace(String workspaceId) {
        String key = workspaceId == null ? "" : workspaceId;
        return scheduleServicesByWorkspace.computeIfAbsent(key, ignored ->
                new OrbScheduleService(marketHoursService, Clock.systemUTC(),
                        schedule -> SwingUtilities.invokeLater(() -> runScheduled(schedule)), ui::log));
    }

    private void runScheduled(OrbSchedule schedule) {
        if (schedule == null) {
            return;
        }
        runAnalysis(schedule.workspaceId(), schedule.config(), schedule.executeAfterRangeClose());
    }

    private void runAnalysis(String workspaceId, OrbConfig config, boolean armRequested) {
        if (!ui.connectionOk() || ui.runtimeApiKey().isBlank()) {
            JOptionPane.showMessageDialog(ui.dialogParent(),
                    ui.selectedModeLabel() + " Alpaca credentials are required before scanning ORB live market data.",
                    "ORB Engine", JOptionPane.WARNING_MESSAGE);
            return;
        }
        ui.setAnalyzeButtonEnabled(false);
        backgroundExecutor.execute(() -> {
            try {
                OrbConfig safeConfig = config == null ? OrbConfig.defaults(null) : config;
                List<OrbCandidate> candidates = resolveCandidates(safeConfig);
                if (candidates.isEmpty()) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(ui.dialogParent(),
                            "No live ORB candidates were found right now. Try again during the opening window, or enter symbols manually.",
                            "ORB Engine", JOptionPane.INFORMATION_MESSAGE));
                    return;
                }
                candidates = enrichWithAi(candidates);
                OpeningRangeCaptureService captureService = new OpeningRangeCaptureService(
                        new HttpAlpacaMarketDataApi(ui.runtimeApiKey(), ui.runtimeApiSecret()));
                LocalDate sessionDate = LocalDate.now(OpeningRangeCaptureService.EASTERN);
                List<OpeningRangeSnapshot> snapshots = candidates.stream()
                        .map(candidate -> capture(candidate, sessionDate, safeConfig, captureService))
                        .toList();
                List<OrbRecommendation> recommendations = new OrbAnalyzer(Clock.systemUTC(), ui::log)
                        .analyze(snapshots, candidates, safeConfig);
                SwingUtilities.invokeLater(() -> applyRecommendations(workspaceId, safeConfig, armRequested, recommendations));
            } finally {
                SwingUtilities.invokeLater(() -> ui.setAnalyzeButtonEnabled(true));
            }
        });
    }

    private OpeningRangeSnapshot capture(OrbCandidate candidate, LocalDate sessionDate, OrbConfig config,
                                         OpeningRangeCaptureService captureService) {
        try {
            return captureService.capture(candidate.symbol(), sessionDate, config);
        } catch (Exception ex) {
            ui.log("[ORB] Range capture failed for " + candidate.symbol() + ": " + ex.getMessage());
            return new OpeningRangeSnapshot(candidate.symbol(), null, null, null, null,
                    java.math.BigDecimal.ZERO, 0, false, "range capture failed");
        }
    }

    private List<OrbCandidate> resolveCandidates(OrbConfig config) {
        OrbDiscoveryService discovery = new OrbDiscoveryService(new HttpAlpacaScreenerClient(ui.runtimeApiKey(), ui.runtimeApiSecret()));
        if (!config.candidateSymbols().isEmpty()) {
            List<OrbCandidate> manual = discovery.manualCandidates(config);
            ui.log("[ORB] Using " + manual.size() + " manual candidate(s).");
            return manual;
        }
        if (!config.autoDiscoverEnabled()) {
            return List.of();
        }
        try {
            int target = Math.max(30, config.maxStocksToAdd() * 3);
            List<OrbCandidate> discovered = discovery.discoverCandidates(config, target);
            ui.log("[ORB] Auto-discovered " + discovered.size() + " candidate(s) from Alpaca screener.");
            return discovered;
        } catch (AlpacaScreenerException ex) {
            ui.log("[ORB] Auto-discovery failed: " + ex.getMessage());
            return List.of();
        }
    }

    private List<OrbCandidate> enrichWithAi(List<OrbCandidate> candidates) {
        AiRecommendationSettings settings = appSettingsService.loadAiRecommendationSettings();
        if (!isAiProviderConfigured(settings)) {
            ui.log("[ORB] No AI provider configured; ORB ranking will use live range and screener data only.");
            return candidates;
        }
        OrbAiInsightService ai = new OrbAiInsightService(
                AiRecommendationProviderFactory.create(settings), Clock.systemUTC(), ui::log);
        return SmartPicksParallelExecutor.mapPreservingOrder(candidates, "orb-ai", ai::enrich, null);
    }

    private boolean isAiProviderConfigured(AiRecommendationSettings settings) {
        if (settings == null) return false;
        return settings.providerType() == AiProviderType.OPENAI
                ? !settings.openAiApiKey().isBlank()
                : !settings.jetsonHost().isBlank();
    }

    private void applyRecommendations(String workspaceId, OrbConfig config, boolean armRequested,
                                      List<OrbRecommendation> recommendations) {
        if (recommendations.isEmpty()) {
            JOptionPane.showMessageDialog(ui.dialogParent(),
                    "No ORB candidates met the current live-data filters. Try lowering range, price, or relative-volume requirements.",
                    "ORB Engine", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        OrbStrategyFactory factory = new OrbStrategyFactory();
        int added = 0;
        int updated = 0;
        int skipped = 0;
        String firstAddedStrategyId = null;
        for (OrbRecommendation recommendation : recommendations) {
            Optional<Strategy> existing = findTrackedStrategy(recommendation.symbol(), config.mode(), workspaceId);
            if (existing.isPresent()) {
                Strategy strategy = existing.get();
                if (isPendingOrderPlacement(strategy)) {
                    strategy.setBaseBuyLimitPrice(recommendation.plannedEntry());
                    strategy.setStopLossPrice(recommendation.stop());
                    strategy.setTargetSellPrice(recommendation.target());
                    strategy.setLastEvent("ORB recommendation refreshed: score=" + recommendation.score()
                            + ", entry=$" + recommendation.plannedEntry().toPlainString()
                            + ", target=$" + recommendation.target().toPlainString()
                            + ". No broker order was submitted.");
                    strategyRepository.save(strategy);
                    updated++;
                } else {
                    skipped++;
                    ui.log("[ORB] Skipped " + recommendation.symbol() + ": already has an order or active state in this strategy tab.");
                }
                continue;
            }
            Strategy strategy = factory.toStrategy(recommendation, workspaceId, armRequested, ui.defaultStrategyPollingSeconds());
            strategyRepository.save(strategy);
            if (firstAddedStrategyId == null) firstAddedStrategyId = strategy.id();
            added++;
        }
        ui.onRecommendationsApplied(workspaceId, firstAddedStrategyId);
        ui.log("[ORB] Added " + added + " ORB candidate" + (added == 1 ? "" : "s")
                + " to the ORB Engine grid"
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
