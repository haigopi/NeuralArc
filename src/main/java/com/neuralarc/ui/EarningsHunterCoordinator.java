package com.neuralarc.ui;

import com.neuralarc.api.HttpAlpacaMarketDataApi;
import com.neuralarc.db.SqliteScanHistoryRepository;
import com.neuralarc.db.SqliteStrategyRepository;
import com.neuralarc.earningshunter.EarningsHunterAnalyzer;
import com.neuralarc.earningshunter.EarningsHunterCandidate;
import com.neuralarc.earningshunter.EarningsHunterConfig;
import com.neuralarc.earningshunter.EarningsHunterLiveScanner;
import com.neuralarc.earningshunter.EarningsHunterRecommendation;
import com.neuralarc.earningshunter.EarningsHunterStrategyFactory;
import com.neuralarc.model.ScanHistoryEntry;
import com.neuralarc.model.Strategy;
import com.neuralarc.service.AlpacaScreenerException;
import com.neuralarc.service.EarningsHunterDiscoveryService;
import com.neuralarc.service.HttpAlpacaNewsClient;
import com.neuralarc.service.HttpAlpacaScreenerClient;

import javax.swing.*;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

final class EarningsHunterCoordinator {
    static final String RECOMMENDED_STATUS = "EARNINGS_HUNTER_RECOMMENDED";
    static final String MONITORING_STATUS = "EARNINGS_HUNTER_MONITORING";

    interface Ui {
        String runtimeApiKey();
        String runtimeApiSecret();
        boolean connectionOk();
        String selectedModeLabel();
        int defaultStrategyPollingSeconds();
        String selectedWorkspaceId();
        boolean isEarningsHunterWorkspaceSelected();
        void log(String message);
        void setScanButtonsEnabled(boolean enabled);
        void onRecommendationsApplied(String workspaceId, String firstAddedStrategyId);
        java.awt.Component dialogParent();
    }

    private final Ui ui;
    private final SqliteStrategyRepository strategyRepository;
    private final SqliteScanHistoryRepository scanHistoryRepository;
    private final Executor backgroundExecutor;

    EarningsHunterCoordinator(Ui ui, SqliteStrategyRepository strategyRepository,
                              SqliteScanHistoryRepository scanHistoryRepository, Executor backgroundExecutor) {
        this.ui = ui;
        this.strategyRepository = strategyRepository;
        this.scanHistoryRepository = scanHistoryRepository;
        this.backgroundExecutor = backgroundExecutor;
    }

    static boolean isPendingOrderPlacement(Strategy strategy) {
        return strategy != null && RECOMMENDED_STATUS.equalsIgnoreCase(strategy.latestOrderStatus());
    }

    void analyze(EarningsHunterConfig config, boolean executeRequested) {
        if (ui.selectedWorkspaceId() == null || !ui.isEarningsHunterWorkspaceSelected()) {
            return;
        }
        runScan(ui.selectedWorkspaceId(), config, executeRequested, true);
    }

    private void runScan(String workspaceId, EarningsHunterConfig config, boolean executeRequested, boolean interactive) {
        if (!ui.connectionOk() || ui.runtimeApiKey().isBlank()) {
            String message = ui.selectedModeLabel()
                    + " Alpaca credentials are required before scanning Earnings Hunter live market data and news.";
            if (interactive) {
                JOptionPane.showMessageDialog(ui.dialogParent(), message, "Earnings Hunter Analysis", JOptionPane.WARNING_MESSAGE);
            } else {
                ui.log("[Earnings Hunter] Scheduled scan skipped: " + message);
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
                                "No live Earnings Hunter candidates were found right now. Try entering symbols manually, or scan during active earnings season.",
                                "Earnings Hunter Analysis", JOptionPane.INFORMATION_MESSAGE));
                    }
                    return;
                }
                EarningsHunterLiveScanner scanner = new EarningsHunterLiveScanner(
                        new HttpAlpacaMarketDataApi(ui.runtimeApiKey(), ui.runtimeApiSecret()),
                        new HttpAlpacaNewsClient(ui.runtimeApiKey(), ui.runtimeApiSecret()),
                        Clock.systemDefaultZone(), ui::log);
                EarningsHunterAnalyzer analyzer = new EarningsHunterAnalyzer(Clock.systemUTC(), ui::log);
                List<EarningsHunterCandidate> scanned = scanner.candidates(symbols, config);
                List<EarningsHunterRecommendation> recommendations = analyzer.analyze(scanned, config);
                SwingUtilities.invokeLater(() -> applyRecommendations(workspaceId, config, executeRequested,
                        interactive, recommendations));
            } finally {
                if (interactive) {
                    SwingUtilities.invokeLater(() -> ui.setScanButtonsEnabled(true));
                }
            }
        });
    }

    private List<String> resolveCandidateSymbols(EarningsHunterConfig config) {
        if (!config.candidateSymbols().isEmpty()) {
            return config.candidateSymbols();
        }
        try {
            EarningsHunterDiscoveryService discovery = new EarningsHunterDiscoveryService(
                    new HttpAlpacaScreenerClient(ui.runtimeApiKey(), ui.runtimeApiSecret()));
            int target = Math.max(50, config.maxStocksToAdd() * 5);
            List<String> discovered = discovery.discoverCandidates(config, target);
            ui.log("[Earnings Hunter] Auto-discovered " + discovered.size() + " candidate(s) from Alpaca screener.");
            return discovered;
        } catch (AlpacaScreenerException ex) {
            ui.log("[Earnings Hunter] Auto-discovery failed: " + ex.getMessage());
            return List.of();
        }
    }

    private void applyRecommendations(String workspaceId, EarningsHunterConfig config, boolean executeRequested,
                                      boolean interactive, List<EarningsHunterRecommendation> recommendations) {
        if (recommendations.isEmpty()) {
            recordScan(workspaceId, interactive, "No qualifying candidates");
            if (interactive) {
                JOptionPane.showMessageDialog(ui.dialogParent(),
                        "No Earnings Hunter candidates met the current filters. Try lowering Minimum News Score, Relative Volume, or entering symbols manually.",
                        "Earnings Hunter Analysis", JOptionPane.INFORMATION_MESSAGE);
            }
            return;
        }
        EarningsHunterStrategyFactory factory = new EarningsHunterStrategyFactory();
        int added = 0;
        int updated = 0;
        int skipped = 0;
        String firstAddedStrategyId = null;
        for (EarningsHunterRecommendation recommendation : recommendations) {
            Optional<Strategy> existing = findTrackedStrategy(recommendation.symbol(), config.mode(), workspaceId);
            if (existing.isPresent()) {
                Strategy existingStrategy = existing.get();
                if (isPendingOrderPlacement(existingStrategy)) {
                    existingStrategy.setBaseBuyLimitPrice(recommendation.plannedEntryPrice());
                    existingStrategy.setStopLossPrice(recommendation.stopLossPrice());
                    existingStrategy.setTargetSellPrice(recommendation.targetPrice());
                    existingStrategy.setLastEvent("Earnings Hunter recommendation refreshed: score="
                            + recommendation.strategyScore() + ", catalyst=" + recommendation.catalystSummary()
                            + ". No broker order was submitted.");
                    strategyRepository.save(existingStrategy);
                    updated++;
                } else {
                    skipped++;
                    ui.log("[Earnings Hunter] Skipped " + recommendation.symbol()
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
            ui.log("[Earnings Hunter] Added " + recommendation.symbol()
                    + " score=" + recommendation.strategyScore()
                    + " catalystScore=" + recommendation.catalystScore()
                    + " plannedEntry=$" + recommendation.plannedEntryPrice().toPlainString()
                    + " mode=" + recommendation.mode()
                    + (executeRequested ? " monitoring enabled" : " recommendation only") + ".");
        }
        recordScan(workspaceId, interactive, ScanHistoryEntry.summarize(added, updated, skipped));
        ui.onRecommendationsApplied(workspaceId, firstAddedStrategyId);
        ui.log("[Earnings Hunter] Added " + added + " Earnings Hunter candidate" + (added == 1 ? "" : "s")
                + " to the Earnings Hunter grid"
                + (updated > 0 ? "; refreshed " + updated + " existing pending row" + (updated == 1 ? "" : "s") : "")
                + (skipped > 0 ? "; skipped " + skipped + " duplicate symbol" + (skipped == 1 ? "" : "s") : "")
                + ". No broker orders were submitted.");
    }

    private void recordScan(String workspaceId, boolean interactive, String summary) {
        if (scanHistoryRepository != null && workspaceId != null) {
            scanHistoryRepository.save(ScanHistoryEntry.now(workspaceId, interactive, summary));
        }
    }

    private Optional<Strategy> findTrackedStrategy(String symbol, com.neuralarc.model.StrategyMode mode, String workspaceId) {
        return strategyRepository.findAll().stream()
                .filter(strategy -> strategy.mode() == mode)
                .filter(strategy -> workspaceId.equals(strategy.workspaceId()))
                .filter(strategy -> strategy.symbol().equalsIgnoreCase(symbol))
                .findFirst();
    }
}
