package com.neuralarc.ui;

import com.neuralarc.api.AlpacaOrderData;
import com.neuralarc.api.AlpacaPositionData;
import com.neuralarc.api.HttpAlpacaClient;
import com.neuralarc.model.ApplicationMode;
import com.neuralarc.model.BrokerType;
import com.neuralarc.model.PauseReason;
import com.neuralarc.model.Position;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyOrder;
import com.neuralarc.model.StrategyOrderStatus;
import com.neuralarc.model.StrategyOrderSide;
import com.neuralarc.model.StrategyStage;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyStatus;
import com.neuralarc.util.BrokerOrderStatusUtil;
import com.neuralarc.service.StrategyExecutionEventRepository;
import com.neuralarc.service.StrategyRepository;
import com.neuralarc.service.StrategyOrderRepository;
import com.neuralarc.service.StrategyService;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

final class PortfolioRefreshController {
    private static final String REFRESH_ACTION_NAME = "Refresh & Reevaluate Portfolio";
    private static final int REFRESH_WATCHDOG_TIMEOUT_MILLIS = 45_000;
    private static final int REFRESH_RETRY_DELAY_MILLIS = 3_000;
    private static final int MAX_STUCK_REFRESH_RETRIES = 2;

    interface Gateway {
        boolean isConnected();
        BrokerType brokerType();
        HttpAlpacaClient alpacaClientForMode(ApplicationMode mode);
        void onRefreshStarted();
        void onRefreshFinished();
        void syncStrategies(List<Strategy> strategies);
        void applyPositionSnapshots(Map<String, Position> snapshots);
        void handleInvalidBrokerMissingStrategies(List<Strategy> invalidStrategies);
        void refreshStrategyTableContent();
        void refreshPanels();
        void updateStatusBar();
        void log(String message);
        void showConnectionRequired();
        void showRefreshFailed(String message);
    }

    private final StrategyRepository strategyRepository;
    private final StrategyOrderRepository orderRepository;
    private final StrategyExecutionEventRepository eventRepository;
    private final ExecutorService executor;
    private final Gateway gateway;
    private final UserActionLogSupport actionLog;
    private final AtomicBoolean refreshInFlight = new AtomicBoolean(false);
    private final AtomicInteger refreshGeneration = new AtomicInteger();

    PortfolioRefreshController(
            StrategyRepository strategyRepository,
            StrategyOrderRepository orderRepository,
            StrategyExecutionEventRepository eventRepository,
            ExecutorService executor,
            Gateway gateway
    ) {
        this.strategyRepository = strategyRepository;
        this.orderRepository = orderRepository;
        this.eventRepository = eventRepository;
        this.executor = executor;
        this.gateway = gateway;
        this.actionLog = new UserActionLogSupport(gateway::log);
    }

    void refresh(boolean manualTrigger) {
        if (!refreshInFlight.compareAndSet(false, true)) {
            if (manualTrigger) {
                actionLog.skipped(REFRESH_ACTION_NAME, "Refresh already in progress.");
            }
            return;
        }
        if (!gateway.isConnected() || gateway.brokerType() != BrokerType.ALPACA) {
            refreshInFlight.set(false);
            if (manualTrigger) {
                actionLog.failed(REFRESH_ACTION_NAME, "Connect to Alpaca before refreshing the portfolio.");
                runOnEdt(gateway::showConnectionRequired);
            }
            return;
        }
        if (manualTrigger) {
            actionLog.started(REFRESH_ACTION_NAME);
        }
        runOnEdt(gateway::onRefreshStarted);
        startRefreshAttempt(manualTrigger, 0);
    }

    private void startRefreshAttempt(boolean manualTrigger, int attempt) {
        int generation = refreshGeneration.incrementAndGet();
        if (attempt > 0) {
            gateway.log("[ACTION][" + REFRESH_ACTION_NAME + "] Retry " + attempt
                    + " of " + MAX_STUCK_REFRESH_RETRIES + " after timeout.");
        }
        startRefreshWatchdog(generation, manualTrigger, attempt);
        executor.submit(() -> refreshInBackground(manualTrigger, generation));
    }

    private void refreshInBackground(boolean manualTrigger, int generation) {
        try {
            List<Strategy> stored = strategyRepository.findAll();
            int purgedCount = purgeNeverVisibleStaleStrategies(stored);
            if (purgedCount > 0) {
                stored = strategyRepository.findAll();
            }
            Map<String, Position> snapshots = loadPositionSnapshots(stored);
            int reconciledCount = reconcileLeftoverLocalBrokerState(stored, snapshots);
            if (reconciledCount > 0) {
                stored = strategyRepository.findAll();
            }
            List<Strategy> invalidStrategies = findInvalidBrokerMissingStrategies(stored);
            List<Strategy> refreshedStored = stored;
            runOnEdt(() -> applySuccessfulRefresh(generation, refreshedStored, snapshots, invalidStrategies));
        } catch (Exception ex) {
            runOnEdt(() -> applyFailedRefresh(generation, manualTrigger, ex));
        }
    }

    private int purgeNeverVisibleStaleStrategies(List<Strategy> stored) {
        if (stored == null || stored.isEmpty()) {
            return 0;
        }
        int deleted = 0;
        for (Strategy strategy : stored) {
            if (strategy == null || strategy.id() == null || strategy.id().isBlank()) {
                continue;
            }
            List<StrategyOrder> orders = orderRepository.findByStrategyId(strategy.id());
            if (!isNeverVisibleStaleStrategy(strategy, orders)) {
                continue;
            }
            orderRepository.deleteByStrategyId(strategy.id());
            eventRepository.deleteByStrategyId(strategy.id());
            strategyRepository.deleteById(strategy.id());
            deleted++;
        }
        if (deleted > 0) {
            gateway.log("[Portfolio Refresh] Deleted " + deleted
                    + " stale strategy record(s) that are never shown in current or history grids.");
        }
        return deleted;
    }

    private boolean isNeverVisibleStaleStrategy(Strategy strategy, List<StrategyOrder> orders) {
        StrategyStatus status = strategy.status();
        if (status == null) {
            return false;
        }
        if (status == StrategyStatus.ARCHIVED || status == StrategyStatus.STOPPED) {
            return !hasFilledOrders(orders);
        }
        if (status == StrategyStatus.COMPLETED) {
            return strategy.restartAfterExitEnabled() && !hasFilledOrders(orders);
        }
        if (status != StrategyStatus.FAILED) {
            return false;
        }
        if (isFailedVisibleInCurrentGrid(strategy)) {
            return false;
        }
        if (hasPendingOrders(orders)) {
            return false;
        }
        return !hasFilledOrders(orders);
    }

    private boolean isFailedVisibleInCurrentGrid(Strategy strategy) {
        String normalized = BrokerOrderStatusUtil.normalize(strategy.latestOrderStatus());
        return "invalid".equals(normalized) || "expired".equals(normalized);
    }

    private boolean hasPendingOrders(List<StrategyOrder> orders) {
        return orders != null && orders.stream().anyMatch(StrategyOrder::isPending);
    }

    private boolean hasFilledOrders(List<StrategyOrder> orders) {
        return orders != null && orders.stream().anyMatch(order -> order.status() == StrategyOrderStatus.FILLED);
    }

    private void applySuccessfulRefresh(
            int generation,
            List<Strategy> stored,
            Map<String, Position> snapshots,
            List<Strategy> invalidStrategies
    ) {
        if (generation != refreshGeneration.get()) {
            return;
        }
        try {
            gateway.syncStrategies(stored);
            gateway.applyPositionSnapshots(snapshots);
            gateway.handleInvalidBrokerMissingStrategies(invalidStrategies);
            gateway.refreshStrategyTableContent();
            gateway.refreshPanels();
            gateway.updateStatusBar();
            actionLog.completed(REFRESH_ACTION_NAME, refreshSuccessMessage(stored, snapshots));
        } finally {
            finishRefresh();
        }
    }

    private String refreshSuccessMessage(List<Strategy> stored, Map<String, Position> snapshots) {
        long eligibleStoredCount = stored == null ? 0L : stored.stream().filter(this::includeInRefresh).count();
        return "Refreshed " + snapshots.size()
                + " position snapshot(s) for " + eligibleStoredCount
                + " current/recoverable strategy row(s) across all workspaces and modes. "
                + "The visible All Stocks grid may show fewer current rows because it is filtered by selected mode, "
                + "workspace scope, and current/history visibility.";
    }

    private void applyFailedRefresh(int generation, boolean manualTrigger, Exception ex) {
        if (generation != refreshGeneration.get()) {
            return;
        }
        try {
            actionLog.failed(REFRESH_ACTION_NAME, ex.getMessage());
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

    private void startRefreshWatchdog(int generation, boolean manualTrigger, int attempt) {
        Timer timer = new Timer(REFRESH_WATCHDOG_TIMEOUT_MILLIS, ignored -> {
            if (generation != refreshGeneration.get()) {
                return;
            }
            if (!refreshInFlight.get()) {
                return;
            }
            if (attempt < MAX_STUCK_REFRESH_RETRIES) {
                scheduleRefreshRetry(generation, manualTrigger, attempt + 1);
                return;
            }
            if (!refreshInFlight.compareAndSet(true, false)) {
                return;
            }
            refreshGeneration.compareAndSet(generation, generation + 1);
            actionLog.failed(REFRESH_ACTION_NAME, "Refresh timed out after "
                    + (MAX_STUCK_REFRESH_RETRIES + 1)
                    + " attempt(s) while waiting for Alpaca portfolio data.");
            gateway.onRefreshFinished();
            if (manualTrigger) {
                gateway.showRefreshFailed("Refresh timed out while waiting for Alpaca portfolio data. "
                        + "The app retried automatically and has re-enabled the button.");
            }
        });
        timer.setRepeats(false);
        timer.start();
    }

    private void scheduleRefreshRetry(int generation, boolean manualTrigger, int nextAttempt) {
        if (!refreshInFlight.get()) {
            return;
        }
        actionLog.failed(REFRESH_ACTION_NAME, "Refresh attempt " + nextAttempt
                + " of " + (MAX_STUCK_REFRESH_RETRIES + 1)
                + " timed out while waiting for Alpaca portfolio data. Retrying automatically.");
        Timer retryTimer = new Timer(REFRESH_RETRY_DELAY_MILLIS, ignored -> {
            if (generation != refreshGeneration.get() || !refreshInFlight.get()) {
                return;
            }
            startRefreshAttempt(manualTrigger, nextAttempt);
        });
        retryTimer.setRepeats(false);
        retryTimer.start();
    }

    private Map<String, Position> loadPositionSnapshots(List<Strategy> stored) {
        if (stored == null || stored.isEmpty() || gateway.brokerType() != BrokerType.ALPACA) {
            return Map.of();
        }
        return BrokerSnapshotLoader.loadPositionSnapshots(stored, gateway::alpacaClientForMode, this::includeInRefresh);
    }

    private boolean includeInRefresh(Strategy strategy) {
        if (strategy == null) {
            return false;
        }
        if (strategy.status() == StrategyStatus.ACTIVE
                || strategy.status() == StrategyStatus.PAUSED
                || strategy.status() == StrategyStatus.FAILED) {
            return true;
        }
        return strategy.status() == StrategyStatus.CREATED
                && StrategyRecommendationMarkers.isScannerRecommendationRow(strategy);
    }

    private List<Strategy> findInvalidBrokerMissingStrategies(List<Strategy> stored) {
        if (stored == null || stored.isEmpty() || gateway.brokerType() != BrokerType.ALPACA) {
            return List.of();
        }
        List<Strategy> invalid = new ArrayList<>();
        invalid.addAll(findInvalidBrokerMissingStrategiesForMode(stored, StrategyMode.PAPER, ApplicationMode.PAPER));
        invalid.addAll(findInvalidBrokerMissingStrategiesForMode(stored, StrategyMode.LIVE, ApplicationMode.LIVE));
        return invalid;
    }

    private int reconcileLeftoverLocalBrokerState(List<Strategy> stored, Map<String, Position> snapshots) {
        if (stored == null || stored.isEmpty() || gateway.brokerType() != BrokerType.ALPACA) {
            return 0;
        }
        int count = 0;
        count += reconcileLeftoverLocalBrokerStateForMode(stored, snapshots, StrategyMode.PAPER, ApplicationMode.PAPER);
        count += reconcileLeftoverLocalBrokerStateForMode(stored, snapshots, StrategyMode.LIVE, ApplicationMode.LIVE);
        return count;
    }

    private int reconcileLeftoverLocalBrokerStateForMode(
            List<Strategy> stored,
            Map<String, Position> snapshots,
            StrategyMode strategyMode,
            ApplicationMode applicationMode
    ) {
        List<Strategy> candidates = stored.stream()
                .filter(strategy -> strategy.mode() == strategyMode)
                .filter(this::isWaitingLocalOrderCandidate)
                .toList();
        if (candidates.isEmpty()) {
            return 0;
        }
        HttpAlpacaClient client = gateway.alpacaClientForMode(applicationMode);
        if (client == null) {
            return 0;
        }
        Map<String, AlpacaOrderData> openOrdersById = new LinkedHashMap<>();
        for (AlpacaOrderData order : client.getOpenOrders()) {
            if (order.orderId() != null && !order.orderId().isBlank()) {
                openOrdersById.put(order.orderId(), order);
            }
        }

        int reconciled = 0;
        for (Strategy strategy : candidates) {
            Optional<StrategyOrder> maybeOrder = latestTrackedOrder(strategy);
            if (maybeOrder.isEmpty()) {
                continue;
            }
            StrategyOrder order = maybeOrder.get();
            String orderId = order.alpacaOrderId();
            if (orderId == null || orderId.isBlank()) {
                continue;
            }
            AlpacaOrderData openOrder = openOrdersById.get(orderId);
            if (openOrder != null) {
                applyBrokerOrderStatus(strategy, order, openOrder);
                reconciled++;
                continue;
            }
            Optional<AlpacaOrderData> brokerOrder = client.getOrder(orderId);
            if (brokerOrder.isPresent()) {
                applyBrokerOrderStatus(strategy, order, brokerOrder.get());
                reconciled++;
                continue;
            }
            Position snapshot = snapshots.get(strategy.id());
            if (snapshot != null && snapshot.getTotalShares() > 0 && order.side() == StrategyOrderSide.BUY) {
                markLocalBuyFilledFromBrokerPosition(strategy, order);
                reconciled++;
            }
        }
        if (reconciled > 0) {
            gateway.log("[Portfolio Refresh] Reconciled " + reconciled
                    + " leftover local order status record(s) from Alpaca.");
        }
        return reconciled;
    }

    private Optional<StrategyOrder> latestTrackedOrder(Strategy strategy) {
        List<StrategyOrder> orders = orderRepository.findByStrategyId(strategy.id());
        String latestAlpacaOrderId = strategy.latestAlpacaOrderId();
        if (latestAlpacaOrderId != null && !latestAlpacaOrderId.isBlank()) {
            Optional<StrategyOrder> byLatestId = orders.stream()
                    .filter(order -> latestAlpacaOrderId.equals(order.alpacaOrderId()))
                    .findFirst();
            if (byLatestId.isPresent()) {
                return byLatestId;
            }
        }
        return orders.stream()
                .filter(order -> order.alpacaOrderId() != null && !order.alpacaOrderId().isBlank())
                .filter(order -> order.isPending() || isPendingOrCanceledLocalState(strategy.currentState()))
                .max(Comparator.comparing(StrategyOrder::updatedAt));
    }

    private void applyBrokerOrderStatus(Strategy strategy, StrategyOrder order, AlpacaOrderData brokerOrder) {
        String normalized = BrokerOrderStatusUtil.normalize(brokerOrder.status());
        StrategyOrderStatus mapped = StrategyService.mapOrderStatus(normalized);
        order.setStatus(mapped);
        order.setFilledQuantity(brokerOrder.filledQuantity());
        order.setFilledAveragePrice(brokerOrder.filledAveragePrice());
        order.setRawResponseJson(brokerOrder.rawJson());
        if (mapped == StrategyOrderStatus.FILLED && order.filledAt() == null) {
            order.setFilledAt(Instant.now());
        }
        orderRepository.save(order);

        strategy.setLatestOrderStatus(normalized);
        strategy.setLatestAlpacaOrderId(order.alpacaOrderId() == null ? "" : order.alpacaOrderId());
        if (mapped == StrategyOrderStatus.FILLED || mapped == StrategyOrderStatus.PARTIALLY_FILLED) {
            strategy.setCurrentState(filledLifecycleState(order.stage(), mapped, strategy.currentState()));
            strategy.clearLastError();
        } else if (isClosedBrokerStatus(normalized)) {
            strategy.setStatus(StrategyStatus.FAILED);
            strategy.setCurrentState(StrategyLifecycleState.FAILED);
            strategy.setLastError("Alpaca order " + BrokerOrderStatusUtil.displayLabel(normalized).toLowerCase(Locale.ROOT));
            strategy.setLastEvent("Updated during portfolio refresh from Alpaca order status: "
                    + BrokerOrderStatusUtil.displayLabel(normalized));
        }
        strategyRepository.save(strategy);
    }

    private void markLocalBuyFilledFromBrokerPosition(Strategy strategy, StrategyOrder order) {
        order.setStatus(StrategyOrderStatus.FILLED);
        if (order.filledAt() == null) {
            order.setFilledAt(Instant.now());
        }
        orderRepository.save(order);
        strategy.setStatus(StrategyStatus.ACTIVE);
        strategy.setCurrentState(filledLifecycleState(order.stage(), StrategyOrderStatus.FILLED, strategy.currentState()));
        strategy.setLatestOrderStatus("filled");
        strategy.setLatestAlpacaOrderId(order.alpacaOrderId() == null ? "" : order.alpacaOrderId());
        strategy.clearLastError();
        strategy.setLastEvent("Updated during portfolio refresh from Alpaca position snapshot.");
        strategyRepository.save(strategy);
    }

    private StrategyLifecycleState filledLifecycleState(
            StrategyStage stage,
            StrategyOrderStatus orderStatus,
            StrategyLifecycleState fallback
    ) {
        boolean partial = orderStatus == StrategyOrderStatus.PARTIALLY_FILLED;
        return switch (stage) {
            case BASE_BUY -> partial ? StrategyLifecycleState.BASE_BUY_PARTIALLY_FILLED : StrategyLifecycleState.BASE_BUY_FILLED;
            case BUY_LIMIT_1 -> partial ? StrategyLifecycleState.BUY_LIMIT_1_PARTIALLY_FILLED : StrategyLifecycleState.BUY_LIMIT_1_FILLED;
            case BUY_LIMIT_2 -> partial ? StrategyLifecycleState.BUY_LIMIT_2_PARTIALLY_FILLED : StrategyLifecycleState.BUY_LIMIT_2_FILLED;
            case TARGET_SELL, PROFIT_EXIT, STOP_LOSS, LOSS_EXIT, MANUAL_EXIT, CLOSE_POSITION ->
                    partial ? StrategyLifecycleState.SELL_PARTIALLY_FILLED : StrategyLifecycleState.COMPLETED;
            default -> fallback == null ? StrategyLifecycleState.VALIDATED : fallback;
        };
    }

    private boolean isClosedBrokerStatus(String normalizedStatus) {
        return "expired".equals(normalizedStatus)
                || "canceled".equals(normalizedStatus)
                || "cancelled".equals(normalizedStatus)
                || "rejected".equals(normalizedStatus)
                || "suspended".equals(normalizedStatus);
    }

    private boolean isWaitingLocalOrderCandidate(Strategy strategy) {
        if (strategy == null
                || strategy.status() == StrategyStatus.ARCHIVED
                || strategy.status() == StrategyStatus.STOPPED
                || strategy.status() == StrategyStatus.COMPLETED) {
            return false;
        }
        return BrokerOrderStatusUtil.isWaitingForFill(strategy.latestOrderStatus())
                || isPendingOrCanceledLocalState(strategy.currentState());
    }

    private List<Strategy> findInvalidBrokerMissingStrategiesForMode(
            List<Strategy> stored,
            StrategyMode strategyMode,
            ApplicationMode applicationMode
    ) {
        List<Strategy> candidates = stored.stream()
                .filter(strategy -> strategy.mode() == strategyMode)
                .filter(this::isInvalidCandidate)
                .toList();
        if (candidates.isEmpty()) {
            return List.of();
        }
        HttpAlpacaClient client = gateway.alpacaClientForMode(applicationMode);
        if (client == null) {
            return List.of();
        }
        Set<String> openOrderSymbols = new LinkedHashSet<>();
        for (AlpacaOrderData order : client.getOpenOrders()) {
            if (order.symbol() != null && !order.symbol().isBlank()) {
                openOrderSymbols.add(order.symbol().toUpperCase(Locale.ROOT));
            }
        }
        Map<String, AlpacaPositionData> positionsBySymbol = new LinkedHashMap<>();
        for (AlpacaPositionData position : client.getPositions()) {
            if (position.symbol() != null && !position.symbol().isBlank()) {
                positionsBySymbol.put(position.symbol().toUpperCase(Locale.ROOT), position);
            }
        }
        List<Strategy> invalid = new ArrayList<>();
        for (Strategy strategy : candidates) {
            String symbol = strategy.symbol().toUpperCase(Locale.ROOT);
            AlpacaPositionData position = positionsBySymbol.get(symbol);
            boolean hasPosition = position != null && position.exists();
            boolean hasOpenOrder = openOrderSymbols.contains(symbol);
            if (!hasPosition && !hasOpenOrder) {
                invalid.add(strategy);
            }
        }
        return invalid;
    }

    private boolean isInvalidCandidate(Strategy strategy) {
        if (strategy == null
                || strategy.status() == StrategyStatus.ARCHIVED
                || strategy.status() == StrategyStatus.STOPPED
                || strategy.status() == StrategyStatus.COMPLETED) {
            return false;
        }
        String normalized = BrokerOrderStatusUtil.normalize(strategy.latestOrderStatus());
        if ("expired".equals(normalized)) {
            return false;
        }
        if ("invalid".equals(normalized) || "invalid_local".equals(normalized)) {
            return true;
        }
        if (strategy.status() == StrategyStatus.FAILED) {
            return true;
        }
        if ("failed_transport".equals(normalized) || "api_error".equals(normalized) || "failed".equals(normalized)) {
            return true;
        }
        if (("canceled".equals(normalized) || "cancelled".equals(normalized))
                && isPendingOrCanceledLocalState(strategy.currentState())) {
            return true;
        }
        return strategy.status() == StrategyStatus.PAUSED
                && (strategy.pauseReason() == PauseReason.MANUAL_LIMIT_BUY_CANCELED
                || strategy.pauseReason() == PauseReason.SYSTEM_ERROR)
                && isPendingOrCanceledLocalState(strategy.currentState());
    }

    private boolean isPendingOrCanceledLocalState(StrategyLifecycleState state) {
        return state == StrategyLifecycleState.BASE_BUY_PLACED
                || state == StrategyLifecycleState.BASE_BUY_PARTIALLY_FILLED
                || state == StrategyLifecycleState.BUY_LIMIT_1_PLACED
                || state == StrategyLifecycleState.BUY_LIMIT_1_PARTIALLY_FILLED
                || state == StrategyLifecycleState.BUY_LIMIT_2_PLACED
                || state == StrategyLifecycleState.BUY_LIMIT_2_PARTIALLY_FILLED
                || state == StrategyLifecycleState.PAUSED
                || state == StrategyLifecycleState.FAILED;
    }

    private void runOnEdt(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }
}
