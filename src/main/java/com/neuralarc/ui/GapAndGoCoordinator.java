package com.neuralarc.ui;

import com.neuralarc.api.HttpAlpacaMarketDataApi;
import com.neuralarc.db.AppDatabase;
import com.neuralarc.db.SqliteGapAndGoScheduleRepository;
import com.neuralarc.db.SqliteStrategyRepository;
import com.neuralarc.gaprocket.GapRocketAnalyzer;
import com.neuralarc.gaprocket.GapRocketCandidate;
import com.neuralarc.gaprocket.GapRocketConfig;
import com.neuralarc.gaprocket.GapRocketLiveScanner;
import com.neuralarc.gaprocket.GapRocketRecommendation;
import com.neuralarc.gaprocket.GapRocketStrategyFactory;
import com.neuralarc.gaprocket.NewsCatalystResolver;
import com.neuralarc.model.AiProviderType;
import com.neuralarc.model.AiRecommendationSettings;
import com.neuralarc.model.GapAndGoSchedule;
import com.neuralarc.model.Strategy;
import com.neuralarc.service.AiRecommendationProviderFactory;
import com.neuralarc.service.AlpacaScreenerException;
import com.neuralarc.service.AppSettingsService;
import com.neuralarc.service.GapAndGoDiscoveryService;
import com.neuralarc.service.GapAndGoScheduleService;
import com.neuralarc.service.HttpAlpacaScreenerClient;
import com.neuralarc.service.MarketHoursService;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Owns the autonomous gap-and-go pipeline (discover → scan → news-enrich → analyze → apply) and the
 * premarket scheduling engine, keeping this logic out of {@code TradingFrame}. The frame supplies a
 * thin {@link Ui} gateway for credentials, the selected workspace, logging, button state, and the
 * post-apply UI refresh; it does not contain any of the orchestration itself.
 */
final class GapAndGoCoordinator {
    static final String RECOMMENDED_STATUS = "GAP_ROCKET_RECOMMENDED";

    /** Callbacks the coordinator needs from the host frame. */
    interface Ui {
        String runtimeApiKey();
        String runtimeApiSecret();
        boolean connectionOk();
        String selectedModeLabel();
        int defaultStrategyPollingSeconds();
        String selectedWorkspaceId();
        boolean isGapRocketWorkspaceSelected();
        void log(String message);
        void setScanButtonsEnabled(boolean enabled);
        void onRecommendationsApplied(String workspaceId, String firstAddedStrategyId);
        void onScheduleChanged(GapAndGoSchedule schedule);
        java.awt.Component dialogParent();
    }

    private final Ui ui;
    private final SqliteStrategyRepository strategyRepository;
    private final AppSettingsService appSettingsService;
    private final SqliteGapAndGoScheduleRepository scheduleRepository;
    private final GapAndGoScheduleService scheduleService;
    private final Executor backgroundExecutor;

    GapAndGoCoordinator(Ui ui, AppDatabase database, SqliteStrategyRepository strategyRepository,
                        AppSettingsService appSettingsService, MarketHoursService marketHoursService,
                        Executor backgroundExecutor) {
        this.ui = ui;
        this.strategyRepository = strategyRepository;
        this.appSettingsService = appSettingsService;
        this.backgroundExecutor = backgroundExecutor;
        this.scheduleRepository = new SqliteGapAndGoScheduleRepository(database);
        this.scheduleService = new GapAndGoScheduleService(marketHoursService, Clock.systemUTC(),
                schedule -> SwingUtilities.invokeLater(() -> runScheduled(schedule)), ui::log);
    }

    static boolean isPendingOrderPlacement(Strategy strategy) {
        return strategy != null && RECOMMENDED_STATUS.equalsIgnoreCase(strategy.latestOrderStatus());
    }

    /** Load any persisted enabled schedule and start the premarket scheduler. */
    void start() {
        Optional<GapAndGoSchedule> persisted = scheduleRepository.findAll().stream()
                .filter(GapAndGoSchedule::enabled)
                .findFirst();
        persisted.ifPresent(schedule -> {
            scheduleService.setSchedule(schedule);
            ui.log("[Gap Rocket] Restored autonomous schedule: scan " + schedule.scanTimeEt()
                    + " ET for workspace " + schedule.workspaceId()
                    + (schedule.executeAfterScan() ? " (auto-execute)." : " (recommendation only)."));
        });
        ui.onScheduleChanged(scheduleService.schedule());
        scheduleService.start();
    }

    GapAndGoSchedule currentSchedule() {
        return scheduleService.schedule();
    }

    /** Run an interactive scan for the selected workspace. */
    void analyze(GapRocketConfig config, boolean executeRequested) {
        if (ui.selectedWorkspaceId() == null || !ui.isGapRocketWorkspaceSelected()) {
            return;
        }
        runScan(ui.selectedWorkspaceId(), config, executeRequested, true);
    }

    /** Register a new schedule, or offer to cancel the existing one for the selected workspace. */
    void scheduleOrCancel(GapRocketConfig config) {
        String workspaceId = ui.selectedWorkspaceId();
        if (workspaceId == null || !ui.isGapRocketWorkspaceSelected()) {
            return;
        }
        GapAndGoSchedule existing = scheduleService.schedule();
        if (existing != null && workspaceId.equals(existing.workspaceId())) {
            int cancel = JOptionPane.showConfirmDialog(ui.dialogParent(),
                    "An autonomous schedule already runs at " + existing.scanTimeEt() + " ET for this workspace.\n"
                            + "Cancel it?",
                    "Gap-and-Go Schedule", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (cancel == JOptionPane.YES_OPTION) {
                cancelSchedule();
            }
            return;
        }
        int execute = JOptionPane.showConfirmDialog(ui.dialogParent(),
                "Schedule an autonomous premarket scan at " + GapAndGoSchedule.DEFAULT_SCAN_TIME_ET + " ET on trading days,\n"
                        + "carrying through the 9:45–11:00 ET execution window.\n\n"
                        + "Auto-execute trades after each scan? (No = build the recommendation list only.)\n\n"
                        + "Note: NeuralArc must be running at the scheduled time.",
                "Gap-and-Go Schedule", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (execute == JOptionPane.CANCEL_OPTION || execute == JOptionPane.CLOSED_OPTION) {
            return;
        }
        boolean executeAfterScan = execute == JOptionPane.YES_OPTION;
        GapAndGoSchedule schedule = GapAndGoSchedule.create(workspaceId, config, executeAfterScan);
        scheduleRepository.save(schedule);
        scheduleService.setSchedule(schedule);
        ui.onScheduleChanged(schedule);
        ui.log("[Gap Rocket] Autonomous schedule set: scan " + schedule.scanTimeEt() + " ET"
                + (executeAfterScan ? " with auto-execute." : " (recommendation only).") + " NeuralArc must be running.");
        JOptionPane.showMessageDialog(ui.dialogParent(),
                "Autonomous gap-and-go scan scheduled for " + schedule.scanTimeEt() + " ET on trading days.\n"
                        + (executeAfterScan ? "Trades will be armed automatically after each scan.\n" : "")
                        + "NeuralArc must be running at that time.",
                "Gap-and-Go Schedule", JOptionPane.INFORMATION_MESSAGE);
    }

    /** Cancel and forget the current schedule. */
    void cancelSchedule() {
        GapAndGoSchedule existing = scheduleService.schedule();
        if (existing == null) {
            return;
        }
        scheduleService.clearSchedule();
        scheduleRepository.deleteById(existing.id());
        ui.onScheduleChanged(null);
        ui.log("[Gap Rocket] Autonomous schedule cancelled.");
    }

    private void runScheduled(GapAndGoSchedule schedule) {
        if (schedule == null) {
            return;
        }
        runScan(schedule.workspaceId(), schedule.config(), schedule.executeAfterScan(), false);
    }

    /**
     * Shared scan path for both manual and scheduled runs. Interactive runs surface dialogs and
     * toggle the scan buttons; scheduled runs only log.
     */
    private void runScan(String workspaceId, GapRocketConfig config, boolean executeRequested, boolean interactive) {
        if (!ui.connectionOk() || ui.runtimeApiKey().isBlank()) {
            String message = ui.selectedModeLabel()
                    + " Alpaca credentials are required before scanning Gap Rocket live market data.";
            if (interactive) {
                JOptionPane.showMessageDialog(ui.dialogParent(), message, "Gap-and-Go Analysis", JOptionPane.WARNING_MESSAGE);
            } else {
                ui.log("[Gap Rocket] Scheduled scan skipped: " + message);
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
                    if (interactive) {
                        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(ui.dialogParent(),
                                "No live gap-and-go candidates were found right now. Try again during the premarket/opening "
                                        + "window, or enter symbols manually.",
                                "Gap-and-Go Analysis", JOptionPane.INFORMATION_MESSAGE));
                    } else {
                        ui.log("[Gap Rocket] Scheduled scan found no live candidates right now.");
                    }
                    return;
                }
                GapRocketLiveScanner scanner = new GapRocketLiveScanner(
                        new HttpAlpacaMarketDataApi(ui.runtimeApiKey(), ui.runtimeApiSecret()),
                        Clock.systemDefaultZone(), ui::log);
                GapRocketAnalyzer analyzer = new GapRocketAnalyzer(Clock.systemUTC(), ui::log);
                List<GapRocketCandidate> scanned = scanner.candidates(symbols);
                GapRocketConfig effectiveConfig = config;
                AiRecommendationSettings aiSettings = appSettingsService.loadAiRecommendationSettings();
                if (isAiProviderConfigured(aiSettings)) {
                    NewsCatalystResolver resolver = new NewsCatalystResolver(
                            AiRecommendationProviderFactory.create(aiSettings), Clock.systemUTC(), ui::log);
                    scanned = SmartPicksParallelExecutor.mapPreservingOrder(
                            scanned, "gap-rocket-news", resolver::enrich, null);
                } else if (config.newsCatalystRequired()) {
                    effectiveConfig = config.withNewsCatalystRequired(false);
                    ui.log("[Gap Rocket] No AI provider configured; ranking on gap/volume only "
                            + "(news-catalyst filter skipped). Configure an AI provider in Settings to enable it.");
                }
                List<GapRocketRecommendation> recommendations = analyzer.analyze(scanned, effectiveConfig);
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
     * the top live gappers from Alpaca's screener so no manual monitoring is required.
     */
    private List<String> resolveCandidateSymbols(GapRocketConfig config) {
        if (!config.candidateSymbols().isEmpty()) {
            return config.candidateSymbols();
        }
        try {
            GapAndGoDiscoveryService discovery = new GapAndGoDiscoveryService(
                    new HttpAlpacaScreenerClient(ui.runtimeApiKey(), ui.runtimeApiSecret()));
            int target = Math.max(30, config.maxStocksToAdd() * 3);
            List<String> discovered = discovery.discoverCandidates(config, target);
            ui.log("[Gap Rocket] Auto-discovered " + discovered.size() + " candidate(s) from Alpaca screener.");
            return discovered;
        } catch (AlpacaScreenerException ex) {
            ui.log("[Gap Rocket] Auto-discovery failed: " + ex.getMessage());
            return List.of();
        }
    }

    private boolean isAiProviderConfigured(AiRecommendationSettings settings) {
        if (settings == null) {
            return false;
        }
        return settings.providerType() == AiProviderType.OPENAI
                ? !settings.openAiApiKey().isBlank()
                : !settings.jetsonHost().isBlank();
    }

    private void applyRecommendations(String workspaceId, GapRocketConfig config, boolean executeRequested,
                                      boolean interactive, List<GapRocketRecommendation> recommendations) {
        if (workspaceId == null) {
            return;
        }
        if (recommendations.isEmpty()) {
            if (interactive) {
                JOptionPane.showMessageDialog(ui.dialogParent(),
                        "No Gap-and-Go candidates met the current live-data filters. Try lowering the gap, volume, relative-volume, catalyst, or price requirements.",
                        "Gap-and-Go Analysis", JOptionPane.INFORMATION_MESSAGE);
            } else {
                ui.log("[Gap Rocket] Scheduled scan produced no qualifying candidates.");
            }
            return;
        }
        GapRocketStrategyFactory factory = new GapRocketStrategyFactory();
        int added = 0;
        int updated = 0;
        int skipped = 0;
        String firstAddedStrategyId = null;
        for (GapRocketRecommendation recommendation : recommendations) {
            Optional<Strategy> existing = findTrackedStrategy(recommendation.symbol(), config.mode(), workspaceId);
            if (existing.isPresent()) {
                Strategy existingStrategy = existing.get();
                if (isPendingOrderPlacement(existingStrategy)) {
                    existingStrategy.setBaseBuyLimitPrice(recommendation.plannedEntryPrice());
                    existingStrategy.setStopLossPrice(recommendation.stopLossPrice());
                    existingStrategy.setTargetSellPrice(recommendation.takeProfitPrice());
                    existingStrategy.setLastEvent("Gap-and-Go recommendation refreshed: score=" + recommendation.strategyScore()
                            + ", plannedEntry=$" + recommendation.plannedEntryPrice().toPlainString()
                            + ", target=$" + recommendation.takeProfitPrice().toPlainString()
                            + ". No broker order was submitted.");
                    strategyRepository.save(existingStrategy);
                    updated++;
                    ui.log("[Gap Rocket] Updated existing " + recommendation.symbol()
                            + " plannedEntry=$" + recommendation.plannedEntryPrice().toPlainString()
                            + " target=$" + recommendation.takeProfitPrice().toPlainString() + ".");
                } else {
                    skipped++;
                    ui.log("[Gap Rocket] Skipped " + recommendation.symbol() + ": already has an order or active state in this strategy tab.");
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
            ui.log("[Gap Rocket] Added " + recommendation.symbol()
                    + " score=" + recommendation.strategyScore()
                    + " plannedEntry=$" + recommendation.plannedEntryPrice().toPlainString()
                    + " mode=" + recommendation.mode()
                    + (executeRequested ? " monitoring enabled" : " recommendation only") + ".");
        }
        ui.onRecommendationsApplied(workspaceId, firstAddedStrategyId);
        String summary = "[Gap Rocket] Added " + added + " Gap-and-Go candidate" + (added == 1 ? "" : "s")
                + " to the Gap Rocket grid"
                + (updated > 0 ? "; refreshed " + updated + " existing pending row" + (updated == 1 ? "" : "s") : "")
                + (skipped > 0 ? "; skipped " + skipped + " duplicate symbol" + (skipped == 1 ? "" : "s") : "")
                + ". No broker orders were submitted.";
        ui.log(summary);
    }

    private Optional<Strategy> findTrackedStrategy(String symbol, com.neuralarc.model.StrategyMode mode, String workspaceId) {
        return strategyRepository.findAll().stream()
                .filter(strategy -> strategy.mode() == mode)
                .filter(strategy -> workspaceId.equals(strategy.workspaceId()))
                .filter(strategy -> strategy.symbol().equalsIgnoreCase(symbol))
                .findFirst();
    }
}
