package com.neuralarc.ui;

import com.neuralarc.api.AlpacaPositionData;
import com.neuralarc.api.HttpAlpacaClient;
import com.neuralarc.model.ApplicationMode;
import com.neuralarc.model.BrokerType;
import com.neuralarc.model.Position;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyStatus;
import com.neuralarc.service.StrategyRepository;

import javax.swing.SwingUtilities;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

final class PortfolioRefreshController {
    interface Gateway {
        boolean isConnected();
        BrokerType brokerType();
        HttpAlpacaClient alpacaClientForMode(ApplicationMode mode);
        void onRefreshStarted();
        void onRefreshFinished();
        void syncStrategies(List<Strategy> strategies);
        void applyPositionSnapshots(Map<String, Position> snapshots);
        void refreshStrategyTableContent();
        void refreshPanels();
        void updateStatusBar();
        void log(String message);
        void showConnectionRequired();
        void showRefreshFailed(String message);
    }

    private final StrategyRepository strategyRepository;
    private final ExecutorService executor;
    private final Gateway gateway;
    private final AtomicBoolean refreshInFlight = new AtomicBoolean(false);

    PortfolioRefreshController(
            StrategyRepository strategyRepository,
            ExecutorService executor,
            Gateway gateway
    ) {
        this.strategyRepository = strategyRepository;
        this.executor = executor;
        this.gateway = gateway;
    }

    void refresh(boolean manualTrigger) {
        if (!refreshInFlight.compareAndSet(false, true)) {
            if (manualTrigger) {
                gateway.log("[PORTFOLIO][REFRESH] Refresh already in progress.");
            }
            return;
        }
        if (!gateway.isConnected() || gateway.brokerType() != BrokerType.ALPACA) {
            refreshInFlight.set(false);
            if (manualTrigger) {
                runOnEdt(gateway::showConnectionRequired);
            }
            return;
        }
        runOnEdt(gateway::onRefreshStarted);
        executor.submit(() -> refreshInBackground(manualTrigger));
    }

    private void refreshInBackground(boolean manualTrigger) {
        try {
            List<Strategy> stored = strategyRepository.findAll();
            Map<String, Position> snapshots = loadPositionSnapshots(stored);
            runOnEdt(() -> applySuccessfulRefresh(stored, snapshots));
        } catch (Exception ex) {
            runOnEdt(() -> applyFailedRefresh(manualTrigger, ex));
        }
    }

    private void applySuccessfulRefresh(List<Strategy> stored, Map<String, Position> snapshots) {
        try {
            gateway.syncStrategies(stored);
            gateway.applyPositionSnapshots(snapshots);
            gateway.refreshStrategyTableContent();
            gateway.refreshPanels();
            gateway.updateStatusBar();
            gateway.log("[PORTFOLIO][REFRESH] Refreshed "
                    + snapshots.size()
                    + " strategy position snapshot(s) from Alpaca.");
        } finally {
            finishRefresh();
        }
    }

    private void applyFailedRefresh(boolean manualTrigger, Exception ex) {
        try {
            gateway.log("[PORTFOLIO][REFRESH] Failed: " + ex.getMessage());
            if (manualTrigger) {
                gateway.showRefreshFailed(ex.getMessage());
            }
        } finally {
            finishRefresh();
        }
    }

    private void finishRefresh() {
        refreshInFlight.set(false);
        gateway.onRefreshFinished();
    }

    private Map<String, Position> loadPositionSnapshots(List<Strategy> stored) {
        if (stored == null || stored.isEmpty() || gateway.brokerType() != BrokerType.ALPACA) {
            return Map.of();
        }
        Map<String, Position> snapshots = new LinkedHashMap<>();
        loadPositionSnapshotsForMode(stored, StrategyMode.PAPER, gateway.alpacaClientForMode(ApplicationMode.PAPER), snapshots);
        loadPositionSnapshotsForMode(stored, StrategyMode.LIVE, gateway.alpacaClientForMode(ApplicationMode.LIVE), snapshots);
        return snapshots;
    }

    private void loadPositionSnapshotsForMode(
            List<Strategy> stored,
            StrategyMode mode,
            HttpAlpacaClient client,
            Map<String, Position> target
    ) {
        if (client == null) {
            return;
        }
        List<Strategy> strategiesForMode = stored.stream()
                .filter(strategy -> strategy.mode() == mode)
                .filter(this::includeInRefresh)
                .filter(strategy -> strategy.symbol() != null && !strategy.symbol().isBlank())
                .toList();
        if (strategiesForMode.isEmpty()) {
            return;
        }
        List<String> symbols = uniqueSymbols(strategiesForMode);
        Map<String, BigDecimal> latestPrices = client.getLatestPrices(symbols);
        Map<String, AlpacaPositionData> positionsBySymbol = client.getPositions().stream()
                .collect(Collectors.toMap(
                        position -> position.symbol().toUpperCase(Locale.ROOT),
                        position -> position,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        for (Strategy strategy : strategiesForMode) {
            target.put(strategy.id(), snapshotFor(strategy, positionsBySymbol, latestPrices));
        }
    }

    private List<String> uniqueSymbols(List<Strategy> strategies) {
        List<String> symbols = new ArrayList<>();
        for (Strategy strategy : strategies) {
            String symbol = strategy.symbol().toUpperCase(Locale.ROOT);
            if (!symbols.contains(symbol)) {
                symbols.add(symbol);
            }
        }
        return symbols;
    }

    private Position snapshotFor(
            Strategy strategy,
            Map<String, AlpacaPositionData> positionsBySymbol,
            Map<String, BigDecimal> latestPrices
    ) {
        String symbol = strategy.symbol().toUpperCase(Locale.ROOT);
        Position snapshot = new Position(strategy.symbol());
        AlpacaPositionData remotePosition = positionsBySymbol.get(symbol);
        if (remotePosition != null && remotePosition.exists()) {
            int quantity = remotePosition.quantity().setScale(0, RoundingMode.DOWN).intValue();
            if (quantity > 0) {
                snapshot.applyBuy(quantity, remotePosition.avgEntryPrice());
            }
            if (remotePosition.marketPrice() != null && remotePosition.marketPrice().compareTo(BigDecimal.ZERO) > 0) {
                snapshot.setLastPrice(remotePosition.marketPrice());
            }
        }
        BigDecimal latestPrice = latestPrices.get(symbol);
        if (latestPrice != null && latestPrice.compareTo(BigDecimal.ZERO) > 0) {
            snapshot.setLastPrice(latestPrice);
        }
        return snapshot;
    }

    private boolean includeInRefresh(Strategy strategy) {
        return strategy != null
                && strategy.status() != StrategyStatus.ARCHIVED
                && strategy.status() != StrategyStatus.STOPPED;
    }

    private void runOnEdt(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }
}
