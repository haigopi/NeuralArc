package com.neuralarc.service;

import com.neuralarc.api.AlpacaClient;
import com.neuralarc.api.AlpacaPositionData;
import com.neuralarc.api.AlpacaTradeUpdateEvent;
import com.neuralarc.model.PauseReason;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyEventType;
import com.neuralarc.model.StrategyExecutionEvent;
import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyOrder;
import com.neuralarc.model.StrategyStage;
import com.neuralarc.model.StrategyStatus;
import com.neuralarc.util.BrokerOrderStatusUtil;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class StrategyPollingService {
    private static final Logger LOGGER = Logger.getLogger(StrategyPollingService.class.getName());
    private static final int STREAM_HEALTHY_GRACE_SECONDS = 120;
    private static final int STREAM_POLL_BACKOFF_MULTIPLIER = 3;
    // A single poll makes a handful of broker calls, each capped by the HTTP client's own request
    // timeout, so a healthy poll finishes well inside this. Anything still "in flight" past it is
    // wedged, and must be released or that strategy would never be polled again.
    private static final long STALE_IN_FLIGHT_TIMEOUT_MILLIS = 90_000L;

    private final StrategyRepository strategyRepository;
    private final StrategyOrderRepository orderRepository;
    private final StrategyExecutionEventRepository eventRepository;
    private final StrategyEngine strategyEngine;
    private final StrategyService strategyService;
    private final AppSettingsService appSettingsService;
    private final MarketHoursService marketHoursService;
    private final AlpacaClient alpacaClient;
    private final StrategyMode strategyMode;
    private final TradingSessionPolicy tradingSessionPolicy;
    private final ExecutorService pollExecutor;
    private final PositionValidationBatchCoordinator batchCoordinator;
    private final PositionValidationAttemptTracker attemptTracker = new PositionValidationAttemptTracker();
    private final AdaptivePollingPacer adaptivePollingPacer = new AdaptivePollingPacer();
    // strategyId -> epoch millis the poll was marked in flight. Timestamped (not a plain Set) so a
    // wedged/never-returning poll worker can't permanently block a strategy from ever being
    // dispatched again; see STALE_IN_FLIGHT_TIMEOUT_MILLIS.
    private final Map<String, Long> inFlightStrategySince = new ConcurrentHashMap<>();
    private volatile Instant lastStreamingEventAt;
    private volatile Boolean lastTradingSessionOpen;
    private volatile AppSettingsService.AppSettings lastLoadedSettings;
    private volatile PollListener pollListener = PollListener.NOOP;
    private volatile PollCycleSnapshot lastPollCycleSnapshot = new PollCycleSnapshot(false, false, 0, 0, 0, 0);
    private final Set<String> pendingMarketClosedAutoRepairedStrategyIds = Collections.synchronizedSet(new LinkedHashSet<>());
    private final Map<String, RepairCategory> strategyIdToRepairCategory = Collections.synchronizedMap(new LinkedHashMap<>());

    public StrategyPollingService(
            StrategyRepository strategyRepository,
            StrategyOrderRepository orderRepository,
            StrategyExecutionEventRepository eventRepository,
            AlpacaClient alpacaClient
    ) {
        this(
                strategyRepository,
                orderRepository,
                eventRepository,
                alpacaClient,
                new AppSettingsService(),
                new MarketHoursService(),
                null
        );
    }

    public StrategyPollingService(
            StrategyRepository strategyRepository,
            StrategyOrderRepository orderRepository,
            StrategyExecutionEventRepository eventRepository,
            AlpacaClient alpacaClient,
            AppSettingsService appSettingsService,
            MarketHoursService marketHoursService
    ) {
        this(
                strategyRepository,
                orderRepository,
                eventRepository,
                alpacaClient,
                appSettingsService,
                marketHoursService,
                null
        );
    }

    public StrategyPollingService(
            StrategyRepository strategyRepository,
            StrategyOrderRepository orderRepository,
            StrategyExecutionEventRepository eventRepository,
            AlpacaClient alpacaClient,
            AppSettingsService appSettingsService,
            MarketHoursService marketHoursService,
            StrategyMode strategyMode
    ) {
        this(
                strategyRepository,
                orderRepository,
                eventRepository,
                alpacaClient,
                appSettingsService,
                marketHoursService,
                strategyMode,
                null
        );
    }

    public StrategyPollingService(
            StrategyRepository strategyRepository,
            StrategyOrderRepository orderRepository,
            StrategyExecutionEventRepository eventRepository,
            AlpacaClient alpacaClient,
            AppSettingsService appSettingsService,
            MarketHoursService marketHoursService,
            StrategyMode strategyMode,
            WorkspaceRepository workspaceRepository
    ) {
        this.strategyRepository = strategyRepository;
        this.orderRepository = orderRepository;
        this.eventRepository = eventRepository;
        this.appSettingsService = appSettingsService;
        this.marketHoursService = marketHoursService;
        this.alpacaClient = alpacaClient;
        this.strategyMode = strategyMode;
        this.batchCoordinator = new PositionValidationBatchCoordinator(alpacaClient);
        this.tradingSessionPolicy = new TradingSessionPolicy(marketHoursService, alpacaClient);
        StrategyEventBus eventBus = new StrategyEventBus();
        StrategyStateMachine stateMachine = new StrategyStateMachine(eventRepository, eventBus);
        this.strategyEngine = new StrategyEngine(
                strategyRepository,
                orderRepository,
                stateMachine,
                alpacaClient,
                appSettingsService,
                marketHoursService,
                workspaceRepository
        );
        this.strategyService = new StrategyService(
                strategyRepository,
                orderRepository,
                eventRepository,
                alpacaClient,
                new StrategyValidator(),
                true,
                null,
                appSettingsService,
                marketHoursService
        );
        int threadCount = Math.max(2, Math.min(6, Runtime.getRuntime().availableProcessors()));
        this.pollExecutor = Executors.newFixedThreadPool(threadCount, runnable -> {
            Thread thread = new Thread(runnable, "neuralarc-poll-worker");
            thread.setDaemon(true);
            return thread;
        });
    }

    public int pollDueStrategies() {
        Instant now = Instant.now();
        AppSettingsService.AppSettings settings = appSettingsService.load();
        this.lastLoadedSettings = settings;
        boolean autoPauseForMarketClose = settings.autoPausePollingWhenMarketClosed();
        int totalStrategies = 0;
        int eligibleStrategies = 0;
        int skippedNotDue = 0;
        int marketClosedStatusRefreshes = 0;
        boolean suppressedForSession = false;
        if (autoPauseForMarketClose) {
            boolean marketOpen = marketHoursService.isTradingSessionOpen(settings.extendedHoursTradingEnabled());
            handleMarketSessionTransition(marketOpen, settings.extendedHoursTradingEnabled(), now);
        } else {
            lastTradingSessionOpen = null;
        }

        // Collect eligible strategies and separate out which are due this cycle.
        List<Strategy> eligible = new ArrayList<>();
        List<Strategy> due = new ArrayList<>();
        for (Strategy strategy : strategyRepository.findAll()) {
            if (!matchesRuntimeMode(strategy)) {
                continue;
            }
            totalStrategies++;
            boolean sessionOpenForStrategy = !autoPauseForMarketClose
                    || tradingSessionPolicy.isTradingSessionOpen(strategy, settings, now);

            if (autoPauseForMarketClose
                    && strategy.status() == StrategyStatus.PAUSED
                    && strategy.pauseReason() == PauseReason.AUTO_MARKET_CLOSED
                    && sessionOpenForStrategy) {
                strategyService.autoResumeFromMarketClose(strategy.id(), "Strategy auto-resumed because market is open");
                recordMarketClosedAutoRepair(strategy.id(), RepairCategory.AUTO_MARKET_CLOSED);
                strategy = strategyRepository.findById(strategy.id()).orElse(strategy);
            }
            if (autoPauseForMarketClose
                    && strategy.status() == StrategyStatus.ACTIVE
                    && strategy.pauseReason() == PauseReason.MANUAL_MARKET_CLOSED_OVERRIDE
                    && sessionOpenForStrategy) {
                strategy.setPauseReason(PauseReason.NONE);
                strategyRepository.save(strategy);
                recordMarketClosedAutoRepair(strategy.id(), RepairCategory.MANUAL_MARKET_CLOSED_OVERRIDE);
            }
            if (!isPollEligible(strategy)) {
                continue;
            }
            if (autoPauseForMarketClose && !sessionOpenForStrategy) {
                skippedNotDue++;
                suppressedForSession = true;
                if (refreshOrderStatusWhileMarketClosed(strategy, now)) {
                    marketClosedStatusRefreshes++;
                }
                continue;
            }
            eligibleStrategies++;
            eligible.add(strategy);
            if (shouldRetryExpiredResubmit(strategy, now)) {
                due.add(strategy);
            } else if (shouldPoll(strategy, now)) {
                due.add(strategy);
            } else {
                skippedNotDue++;
            }
        }

        // Pull forward strategies that aren't due yet but are within the 5s catch-up window of a
        // same-bucket strategy that IS due this cycle, so they join the same batch instead of
        // triggering a separate broker round-trip a few seconds later. Never overrides a
        // strategy's own pollingIntervalSeconds — only ever fires it up to 5s early.
        if (!due.isEmpty() && !eligible.isEmpty()) {
            Set<String> dueIds = new LinkedHashSet<>();
            Set<Long> dueBuckets = new LinkedHashSet<>();
            for (Strategy s : due) {
                dueIds.add(s.id());
                dueBuckets.add(PollingBatchScheduler.nearestBucketSeconds(s.pollingIntervalSeconds()));
            }
            Map<String, Long> notYetDueNaturalDueAt = new LinkedHashMap<>();
            Map<String, Long> notYetDueBuckets = new LinkedHashMap<>();
            Map<String, Strategy> notYetDueById = new LinkedHashMap<>();
            for (Strategy s : eligible) {
                if (dueIds.contains(s.id()) || s.lastPolledAt() == null) {
                    continue;
                }
                long naturalDueAtMillis = s.lastPolledAt().toEpochMilli()
                        + effectivePollingIntervalSeconds(s, now) * 1000L;
                notYetDueNaturalDueAt.put(s.id(), naturalDueAtMillis);
                notYetDueBuckets.put(s.id(), PollingBatchScheduler.nearestBucketSeconds(s.pollingIntervalSeconds()));
                notYetDueById.put(s.id(), s);
            }
            Set<String> pulledForwardIds = PollingBatchScheduler.pullForwardCandidateStrategyIds(
                    dueBuckets, notYetDueNaturalDueAt, notYetDueBuckets, now.toEpochMilli());
            for (String pulledId : pulledForwardIds) {
                Strategy pulled = notYetDueById.get(pulledId);
                if (pulled != null) {
                    due.add(pulled);
                    skippedNotDue--;
                }
            }
        }

        // Batch-fetch latest prices for ALL eligible strategy symbols in one API call
        // whenever at least one strategy is due to poll.  This replaces per-strategy
        // getLatestPrice() calls inside reconcile() and reduces total broker API usage.
        Map<String, BigDecimal> priceCache = Map.of();
        BrokerSnapshotBatch snapshotBatch = null;
        if (!due.isEmpty()) {
            List<String> symbols = new ArrayList<>();
            for (Strategy s : eligible) {
                if (s.symbol() != null && !s.symbol().isBlank()) {
                    String upper = s.symbol().toUpperCase(Locale.ROOT);
                    if (!symbols.contains(upper)) {
                        symbols.add(upper);
                    }
                }
            }
            if (!symbols.isEmpty()) {
                try {
                    Map<String, BigDecimal> fetched = alpacaClient.getLatestPrices(symbols);
                    priceCache = fetched;
                    LOGGER.fine(() -> "[POLL][PRICE_CACHE] Batch-fetched prices for " + symbols.size()
                            + " symbol(s): " + symbols + " → " + fetched.size() + " result(s)");
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "[POLL][PRICE_CACHE] Batch price fetch failed, will fall back to per-symbol calls", ex);
                }
            }
            snapshotBatch = batchCoordinator.fetchSnapshotBatchOrNull();
            attemptTracker.recordCycleBatchResult(snapshotBatch != null);
            final int dueCountForLog = due.size();
            LOGGER.info(() -> "[POLL][BATCH] one combined position+order snapshot fetch covers " + dueCountForLog
                    + " due strategies this cycle (previously " + dueCountForLog + " individual getPosition/getOpenOrders calls)");
        }

        int dueStrategies = 0;
        final Map<String, BigDecimal> finalPriceCache = priceCache;
        final BrokerSnapshotBatch finalSnapshotBatch = snapshotBatch;
        for (Strategy strategy : due) {
            String strategyId = strategy.id();
            if (submitPollTask(strategyId, finalPriceCache, finalSnapshotBatch)) {
                dueStrategies++;
            }
        }
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("[POLL][CYCLE] autoPause=" + autoPauseForMarketClose
                    + " marketSuppressed=" + suppressedForSession
                    + " scanned=" + totalStrategies
                    + " eligible=" + eligibleStrategies
                    + " due=" + dueStrategies
                    + " statusRefreshes=" + marketClosedStatusRefreshes
                    + " skippedNotDue=" + skippedNotDue);
        }
        lastPollCycleSnapshot = new PollCycleSnapshot(
                true,
                suppressedForSession,
                totalStrategies,
                eligibleStrategies,
                dueStrategies,
                skippedNotDue
        );
        return dueStrategies;
    }

    private boolean matchesRuntimeMode(Strategy strategy) {
        return strategyMode == null || (strategy != null && strategy.mode() == strategyMode);
    }

    public PollCycleSnapshot lastPollCycleSnapshot() {
        return lastPollCycleSnapshot;
    }

    /**
     * Returns and clears the current pending list of market-close auto-repaired strategy IDs
     * with category counts. Intended for one-time startup audit logging at the UI layer.
     */
    public MarketClosedAutoRepairSummary drainMarketClosedAutoRepairedStrategyIds() {
        synchronized (pendingMarketClosedAutoRepairedStrategyIds) {
            if (pendingMarketClosedAutoRepairedStrategyIds.isEmpty()) {
                return new MarketClosedAutoRepairSummary(List.of(), Map.of());
            }
            List<String> snapshot = new ArrayList<>(pendingMarketClosedAutoRepairedStrategyIds);
            Map<RepairCategory, Integer> categoryCounts = new LinkedHashMap<>();
            for (String id : snapshot) {
                RepairCategory category = strategyIdToRepairCategory.getOrDefault(id, RepairCategory.AUTO_MARKET_CLOSED);
                categoryCounts.put(category, categoryCounts.getOrDefault(category, 0) + 1);
            }
            snapshot.sort(String::compareTo);
            pendingMarketClosedAutoRepairedStrategyIds.clear();
            strategyIdToRepairCategory.clear();
            return new MarketClosedAutoRepairSummary(snapshot, categoryCounts);
        }
    }

    public void pollActiveStrategies() {
        pollDueStrategies();
    }

    /** Legacy single-strategy poll (no price cache). Used in tests and manual triggers. */
    public void pollStrategy(String strategyId) {
        runPollIfNotInFlight(strategyId, Map.of(), null);
    }

    /**
     * Forces an immediate out-of-band poll for one strategy, bypassing the due-time gate, and
     * clears the shared validation-attempt tracker — the "Refresh Now" affordance shown when
     * polling is in the Warning-Paused state.
     */
    public void pollStrategyNow(String strategyId) {
        attemptTracker.resetOnManualRefresh();
        runPollIfNotInFlight(strategyId, Map.of(), null);
    }

    /** True once the shared batch snapshot fetch has failed at least once in a row this session. */
    public boolean isValidationWarningPaused() {
        return attemptTracker.isWarningPaused();
    }

    public int activeValidationAttempt() {
        return attemptTracker.activeAttempt();
    }

    /** 0 means unlimited/disabled — matches {@code AppSettings.maxValidationAttemptsBeforePause()}. */
    public int maxValidationAttemptsBeforePause() {
        AppSettingsService.AppSettings settings = lastLoadedSettings;
        return settings == null ? 0 : settings.maxValidationAttemptsBeforePause();
    }

    /**
     * Dispatches explicit strategy polls on the polling worker pool. Used by recovery
     * flows that need to refresh multiple strategies without serializing broker I/O.
     */
    public int pollStrategiesAsync(List<String> strategyIds) {
        if (strategyIds == null || strategyIds.isEmpty()) {
            return 0;
        }
        int submitted = 0;
        Set<String> seen = new LinkedHashSet<>();
        for (String strategyId : strategyIds) {
            if (strategyId == null || strategyId.isBlank() || !seen.add(strategyId)) {
                continue;
            }
            if (submitPollTask(strategyId, Map.of(), null)) {
                submitted++;
            }
        }
        return submitted;
    }

    /** Poll a single strategy using the pre-fetched price cache from the current cycle. */
    void pollStrategy(String strategyId, Map<String, BigDecimal> priceCache) {
        runPollIfNotInFlight(strategyId, priceCache, null);
    }

    private boolean submitPollTask(String strategyId, Map<String, BigDecimal> priceCache, BrokerSnapshotBatch snapshotBatch) {
        if (!markStrategyInFlight(strategyId)) {
            LOGGER.fine(() -> "[POLL][SCHEDULER][" + strategyId + "] Skipping dispatch because a poll is already in flight");
            return false;
        }
        try {
            pollExecutor.submit(() -> executePoll(strategyId, priceCache, snapshotBatch));
            return true;
        } catch (RuntimeException ex) {
            inFlightStrategySince.remove(strategyId);
            throw ex;
        }
    }

    private void runPollIfNotInFlight(String strategyId, Map<String, BigDecimal> priceCache, BrokerSnapshotBatch snapshotBatch) {
        if (!markStrategyInFlight(strategyId)) {
            LOGGER.fine(() -> "[POLL][SCHEDULER][" + strategyId + "] Ignoring poll request because a poll is already in flight");
            return;
        }
        executePoll(strategyId, priceCache, snapshotBatch);
    }

    /** Test seam: pins a strategy in flight without a real worker, simulating a wedged poll. */
    void markStrategyInFlightForTest(String strategyId, boolean stale) {
        long since = stale
                ? System.currentTimeMillis() - STALE_IN_FLIGHT_TIMEOUT_MILLIS - 1L
                : System.currentTimeMillis();
        inFlightStrategySince.put(strategyId, since);
    }

    boolean isStrategyInFlightForTest(String strategyId) {
        return inFlightStrategySince.containsKey(strategyId);
    }

    private boolean markStrategyInFlight(String strategyId) {
        if (strategyId == null || strategyId.isBlank()) {
            return false;
        }
        long now = System.currentTimeMillis();
        Long existingSince = inFlightStrategySince.putIfAbsent(strategyId, now);
        if (existingSince == null) {
            return true;
        }
        long heldMillis = now - existingSince;
        if (heldMillis < STALE_IN_FLIGHT_TIMEOUT_MILLIS) {
            LOGGER.fine(() -> "[POLL][SCHEDULER][" + strategyId + "] Skipping dispatch; a poll has been in flight for "
                    + heldMillis + "ms");
            return false;
        }
        // Reclaim the slot. Without this a wedged worker would keep this strategy out of every
        // future cycle, silently freezing its rule evaluation (stop loss, target sell) forever.
        if (!inFlightStrategySince.replace(strategyId, existingSince, now)) {
            return false;
        }
        LOGGER.warning(() -> "[POLL][SCHEDULER][" + strategyId + "] Previous poll never completed after "
                + heldMillis + "ms; releasing the stale in-flight lock and re-dispatching");
        return true;
    }

    private boolean refreshOrderStatusWhileMarketClosed(Strategy strategy, Instant now) {
        if (!shouldRefreshOrderStatusWhileMarketClosed(strategy, now)) {
            return false;
        }
        String strategyId = strategy.id();
        if (!markStrategyInFlight(strategyId)) {
            LOGGER.fine(() -> "[POLL][MARKET_CLOSED_STATUS][" + strategy.symbol()
                    + "] Skipping refresh because a poll is already in flight");
            return false;
        }
        try {
            pollExecutor.submit(() -> executeMarketClosedOrderStatusRefresh(strategy, now));
            return true;
        } catch (RuntimeException ex) {
            inFlightStrategySince.remove(strategyId);
            throw ex;
        }
    }

    private void executeMarketClosedOrderStatusRefresh(Strategy strategy, Instant now) {
        String strategyId = strategy.id();
        try {
            pollListener.onPollStarted(strategyId);
            strategyEngine.refreshOrderStatuses(strategy, null);
            strategyRepository.findById(strategyId).ifPresent(updated -> {
                if (isExpiryResubmitEligible(updated) && strategyEngine.canAutoResubmitExpiredEntryOrder(updated)) {
                    LOGGER.info(() -> "[POLL][MARKET_CLOSED_EXPIRY_RESUBMIT][" + updated.symbol()
                            + "] Repositioning expired entry order after market-closed status refresh");
                    strategyService.repositionExpiredStrategy(updated.id());
                    strategyRepository.findById(strategyId).ifPresent(resubmitted -> {
                        resubmitted.setLastPolledAt(now);
                        strategyRepository.save(resubmitted);
                    });
                } else {
                    updated.setLastPolledAt(now);
                    strategyRepository.save(updated);
                }
            });
            eventRepository.save(event(strategyId, StrategyEventType.POLL_SUCCESS,
                    "Market-closed order status refresh completed", "{\"strategyId\":\"" + strategyId + "\"}"));
            pollListener.onPollCompleted(strategyId);
            LOGGER.fine(() -> "[POLL][MARKET_CLOSED_STATUS][" + strategy.symbol()
                    + "] Refreshed tracked Alpaca order status while trading was suppressed");
        } catch (Exception ex) {
            eventRepository.save(event(strategyId, StrategyEventType.POLL_ERROR, ex.getMessage(), "{}"));
            pollListener.onPollFailed(strategyId);
            LOGGER.log(Level.WARNING, "Market-closed order status refresh failed for strategy " + strategyId, ex);
        } finally {
            inFlightStrategySince.remove(strategyId);
        }
    }

    private boolean shouldRefreshOrderStatusWhileMarketClosed(Strategy strategy, Instant now) {
        return strategy != null
                && strategy.status() == StrategyStatus.ACTIVE
                && strategy.latestAlpacaOrderId() != null
                && !strategy.latestAlpacaOrderId().isBlank()
                && BrokerOrderStatusUtil.isWaitingForFill(strategy.latestOrderStatus())
                && shouldPoll(strategy, now);
    }

    private void executePoll(String strategyId, Map<String, BigDecimal> priceCache, BrokerSnapshotBatch snapshotBatch) {
        try {
            pollListener.onPollStarted(strategyId);
            PollExecutionResult result = doPollStrategy(strategyId, priceCache, snapshotBatch);
            if (result == PollExecutionResult.FAILED) {
                pollListener.onPollFailed(strategyId);
            } else {
                pollListener.onPollCompleted(strategyId);
            }
        } catch (Exception ex) {
            eventRepository.save(event(strategyId, StrategyEventType.POLL_ERROR, ex.getMessage(), "{}"));
            pollListener.onPollFailed(strategyId);
            LOGGER.log(Level.WARNING, "Polling failed for strategy " + strategyId, ex);
        } finally {
            inFlightStrategySince.remove(strategyId);
        }
    }

    private PollExecutionResult doPollStrategy(String strategyId, Map<String, BigDecimal> priceCache, BrokerSnapshotBatch snapshotBatch) {
        Optional<Strategy> maybeStrategy = strategyRepository.findById(strategyId);
        if (maybeStrategy.isEmpty()) {
            return PollExecutionResult.COMPLETED;
        }
        Strategy strategy = maybeStrategy.get();
        if (!matchesRuntimeMode(strategy)) {
            return PollExecutionResult.COMPLETED;
        }
        if (isExpiryResubmitEligible(strategy)) {
            if (!strategyEngine.canAutoResubmitExpiredEntryOrder(strategy)) {
                strategy.setLastPolledAt(Instant.now());
                strategyRepository.save(strategy);
                return PollExecutionResult.COMPLETED;
            }
            String symbol = strategy.symbol();
            LOGGER.info(() -> "[POLL][EXPIRY_RESUBMIT][" + symbol + "] Reactivating expired entry order for automatic resubmission");
            strategyService.repositionExpiredStrategy(strategy.id());
            maybeStrategy = strategyRepository.findById(strategyId);
            if (maybeStrategy.isEmpty()) {
                return PollExecutionResult.COMPLETED;
            }
            strategy = maybeStrategy.get();
        }
        if (strategy.status() != StrategyStatus.ACTIVE) {
            return PollExecutionResult.COMPLETED;
        }
        AppSettingsService.AppSettings settings = lastLoadedSettings;
        if (settings != null && settings.maxValidationAttemptsBeforePause() > 0
                && attemptTracker.activeAttempt() > settings.maxValidationAttemptsBeforePause()) {
            String symbol = strategy.symbol();
            LOGGER.fine(() -> "[POLL][MAX_ATTEMPTS][" + symbol + "] Skipping reconcile; validation paused after "
                    + settings.maxValidationAttemptsBeforePause() + " consecutive failed batch fetches. Use Refresh Now to resume.");
            // Stamp the attempt so the scheduler doesn't re-select this strategy every tick while paused.
            strategy.setLastPolledAt(Instant.now());
            strategyRepository.save(strategy);
            return PollExecutionResult.COMPLETED;
        }

        try {
            List<StrategyEngine.RuleOutcome> outcomes = strategyEngine.reconcileTracked(strategy, priceCache, snapshotBatch);
            eventRepository.save(event(strategy.id(), StrategyEventType.POLL_SUCCESS,
                    "Poll completed", "{\"strategyId\":\"" + strategy.id() + "\"}"));
            if (!outcomes.isEmpty()) {
                pollListener.onRulesAnalyzed(strategy.id(), strategy.symbol(), outcomes);
            }
            recordAdaptivePacingObservation(strategy, priceCache, snapshotBatch);
            return PollExecutionResult.COMPLETED;
        } catch (Exception ex) {
            strategy.setStatus(StrategyStatus.PAUSED);
            strategy.setCurrentState(StrategyLifecycleState.PAUSED);
            strategy.setPauseReason(PauseReason.SYSTEM_ERROR);
            strategy.setLastPolledAt(Instant.now());
            strategy.setLastError(ex.getMessage());
            strategyRepository.save(strategy);
            eventRepository.save(event(strategy.id(), StrategyEventType.POLL_ERROR, ex.getMessage(), "{}"));
            LOGGER.log(Level.WARNING, "Polling failed for strategy " + strategy.id(), ex);
            return PollExecutionResult.FAILED;
        }
    }

    /**
     * Feeds this cycle's observed price/position into the adaptive pacer, unless an order is
     * still pending (an in-flight order always polls at full speed regardless of pacing).
     */
    private void recordAdaptivePacingObservation(Strategy strategy, Map<String, BigDecimal> priceCache, BrokerSnapshotBatch snapshotBatch) {
        if (isPendingBuyOrderState(strategy) || isPendingSellOrderState(strategy)) {
            adaptivePollingPacer.reset(strategy.id());
            return;
        }
        String symbolKey = strategy.symbol() == null ? "" : strategy.symbol().trim().toUpperCase(Locale.ROOT);
        BigDecimal price = priceCache.get(symbolKey);
        AlpacaPositionData position = snapshotBatch == null ? null : snapshotBatch.positionsBySymbol().get(symbolKey);
        adaptivePollingPacer.recordObservation(
                strategy.id(),
                price,
                position == null ? null : position.quantity(),
                position == null ? null : position.avgEntryPrice()
        );
    }

    public void setPollListener(PollListener pollListener) {
        this.pollListener = pollListener == null ? PollListener.NOOP : pollListener;
    }

    /** Propagates the workspace-code resolver to this service's engine and internal service. */
    public void setWorkspaceCodeResolver(WorkspaceCodeResolver resolver) {
        strategyEngine.setWorkspaceCodeResolver(resolver);
        strategyService.setWorkspaceCodeResolver(resolver);
    }

    public void setEmailNotificationListener(TradeEmailNotificationService.EmailNotificationListener listener) {
        strategyEngine.setEmailNotificationListener(listener);
    }

    /** Surfaces automatic safety corrections (e.g. a repaired stop loss) to the UI. */
    public void setAutoCorrectionListener(StopLossAutoCorrector.AutoCorrectionListener listener) {
        strategyEngine.setAutoCorrectionListener(listener);
    }

    public void shutdown() {
        pollExecutor.shutdownNow();
        batchCoordinator.shutdown();
    }

    public Optional<String> onTradeUpdate(AlpacaTradeUpdateEvent updateEvent) {
        if (updateEvent == null || updateEvent.orderData() == null) {
            return Optional.empty();
        }
        try {
            Optional<String> appliedStrategyId = strategyEngine.applyStreamingOrderUpdate(updateEvent.orderData());
            if (appliedStrategyId.isPresent()) {
                lastStreamingEventAt = Instant.now();
                LOGGER.info(() -> "Applied trade update event "
                        + updateEvent.eventType()
                        + " for orderId=" + updateEvent.orderData().orderId()
                        + " clientOrderId=" + updateEvent.orderData().clientOrderId()
                        + " strategyId=" + appliedStrategyId.get());
            } else {
                LOGGER.info(() -> "Ignored trade update event "
                        + updateEvent.eventType()
                        + " because no matching local order was found for orderId="
                        + updateEvent.orderData().orderId()
                        + " clientOrderId=" + updateEvent.orderData().clientOrderId()
                        + " symbol=" + updateEvent.orderData().symbol()
                        + " side=" + updateEvent.orderData().side()
                        + " status=" + updateEvent.orderData().status()
                        + " localPendingSameSymbol=" + localPendingOrderSummary(updateEvent.orderData().symbol()));
            }
            return appliedStrategyId;
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Failed to process streaming trade update", ex);
            return Optional.empty();
        }
    }

    private boolean shouldPoll(Strategy strategy, Instant now) {
        if (strategy.lastPolledAt() == null) {
            return true;
        }
        long elapsedSeconds = Duration.between(strategy.lastPolledAt(), now).getSeconds();
        long pollInterval = effectivePollingIntervalSeconds(strategy, now);
        return elapsedSeconds >= pollInterval;
    }

    public long effectivePollingIntervalSeconds(Strategy strategy) {
        return effectivePollingIntervalSeconds(strategy, Instant.now());
    }

    long effectivePollingIntervalSeconds(Strategy strategy, Instant now) {
        if (strategy == null) {
            return 1L;
        }
        long pollInterval = Math.max(1, strategy.pollingIntervalSeconds());
        if (isStreamHealthy(now) && !isPendingSellOrderState(strategy)) {
            pollInterval = pollInterval * STREAM_POLL_BACKOFF_MULTIPLIER;
        }
        AppSettingsService.AppSettings settings = lastLoadedSettings;
        if (settings != null && settings.adaptivePacingEnabled()
                && !isPendingBuyOrderState(strategy) && !isPendingSellOrderState(strategy)) {
            long multiplier = adaptivePollingPacer.pacingMultiplier(strategy.id(), settings.adaptivePacingMaxMultiplier());
            pollInterval = pollInterval * Math.max(1L, multiplier);
        }
        return pollInterval;
    }

    private boolean shouldRetryExpiredResubmit(Strategy strategy, Instant now) {
        if (!isExpiryResubmitEligible(strategy)) {
            return false;
        }
        if (strategy.lastPolledAt() == null) {
            return true;
        }
        long elapsedSeconds = Duration.between(strategy.lastPolledAt(), now).getSeconds();
        long retryInterval = Math.min(60L, Math.max(1L, strategy.pollingIntervalSeconds()));
        return elapsedSeconds >= retryInterval;
    }

    private boolean isPollEligible(Strategy strategy) {
        if (strategy == null) {
            return false;
        }
        if (strategy.status() == StrategyStatus.PAUSED
                && strategy.pauseReason() == PauseReason.MANUAL_LIMIT_BUY_CANCELED) {
            LOGGER.fine(() -> "[POLL][SCHEDULER][" + strategy.symbol() + "] Polling not resumed because user cancellation requires manual restart");
            return false;
        }
        // FAILED/COMPLETED/STOPPED/ARCHIVED belong to history and should not run in poll cycles
        // unless the operator explicitly enabled expiry resubmission for an expired order.
        return strategy.status() == StrategyStatus.ACTIVE || isExpiryResubmitEligible(strategy);
    }

    private boolean isExpiryResubmitEligible(Strategy strategy) {
        return strategy != null
                && strategy.resubmitOnExpiryEnabled()
                && "expired".equals(BrokerOrderStatusUtil.normalize(strategy.latestOrderStatus()))
                && (strategy.status() == StrategyStatus.FAILED
                || (strategy.status() == StrategyStatus.ACTIVE && isPendingBuyOrderState(strategy)));
    }

    private boolean isPendingBuyOrderState(Strategy strategy) {
        StrategyLifecycleState state = strategy.currentState();
        boolean pendingManualBuy = state == StrategyLifecycleState.BASE_BUY_FILLED
                && StrategyStageSupport.stageForRuleType(strategy.lastTriggeredRuleType())
                .filter(stage -> stage == StrategyStage.MANUAL_BUY)
                .isPresent();
        return state == StrategyLifecycleState.BASE_BUY_PLACED
                || state == StrategyLifecycleState.BASE_BUY_PARTIALLY_FILLED
                || state == StrategyLifecycleState.BUY_LIMIT_1_PLACED
                || state == StrategyLifecycleState.BUY_LIMIT_1_PARTIALLY_FILLED
                || state == StrategyLifecycleState.BUY_LIMIT_2_PLACED
                || state == StrategyLifecycleState.BUY_LIMIT_2_PARTIALLY_FILLED
                || pendingManualBuy;
    }

    private boolean isPendingSellOrderState(Strategy strategy) {
        if (strategy == null) {
            return false;
        }
        StrategyLifecycleState state = strategy.currentState();
        return state == StrategyLifecycleState.SELL_PLACED
                || state == StrategyLifecycleState.SELL_PARTIALLY_FILLED;
    }

    private String localPendingOrderSummary(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return "[]";
        }
        String normalizedSymbol = symbol.trim().toUpperCase(Locale.ROOT);
        List<String> summaries = new ArrayList<>();
        for (Strategy strategy : strategyRepository.findAll()) {
            if (strategy.symbol() == null || !normalizedSymbol.equals(strategy.symbol().trim().toUpperCase(Locale.ROOT))) {
                continue;
            }
            for (StrategyOrder order : orderRepository.findByStrategyId(strategy.id())) {
                if (order.isPending()) {
                    summaries.add("{strategyId=" + strategy.id()
                            + ",stage=" + order.stage()
                            + ",side=" + order.side()
                            + ",status=" + order.status()
                            + ",alpacaOrderId=" + safeLogValue(order.alpacaOrderId())
                            + ",clientOrderId=" + safeLogValue(order.clientOrderId())
                            + "}");
                }
            }
        }
        return summaries.isEmpty() ? "[]" : summaries.toString();
    }

    private String safeLogValue(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
    }

    private boolean isStreamHealthy(Instant now) {
        return lastStreamingEventAt != null
                && Duration.between(lastStreamingEventAt, now).getSeconds() <= STREAM_HEALTHY_GRACE_SECONDS;
    }

    private void handleMarketSessionTransition(boolean marketOpen, boolean extendedHoursEnabled, Instant now) {
        boolean sessionChanged = lastTradingSessionOpen == null || lastTradingSessionOpen != marketOpen;
        if (sessionChanged) {
            lastTradingSessionOpen = marketOpen;
            if (marketOpen) {
                LOGGER.info(() -> "Market session open. Auto-resuming eligible strategies."
                        + " Extended hours enabled=" + extendedHoursEnabled);
            } else {
                LOGGER.info(() -> "Market session closed. Poll cycles will be suppressed until "
                        + marketHoursService.nextMarketOpen(now, extendedHoursEnabled)
                        + ". Extended hours enabled=" + extendedHoursEnabled);
            }
        }
        if (marketOpen) {
            LOGGER.fine("[POLL][SCHEDULER] Market-open transition: evaluating auto-resume candidates");
            resumeAutoPausedStrategies();
            if (sessionChanged) {
                resubmitExpiredStrategiesAfterMarketOpen();
            }
        }
    }

    private void resumeAutoPausedStrategies() {
        for (Strategy strategy : strategyRepository.findAll()) {
            if (strategy.status() != StrategyStatus.PAUSED) {
                continue;
            }
            if (strategy.pauseReason() != PauseReason.AUTO_MARKET_CLOSED) {
                if (strategy.pauseReason() == PauseReason.MANUAL_LIMIT_BUY_CANCELED) {
                    LOGGER.fine(() -> "[POLL][SCHEDULER][" + strategy.symbol() + "] Manual cancel detected; waiting for user to click Place Limit Buy Again");
                }
                continue;
            }
            strategyService.autoResumeFromMarketClose(strategy.id(), "Strategy auto-resumed because market is open");
            recordMarketClosedAutoRepair(strategy.id(), RepairCategory.AUTO_MARKET_CLOSED);
        }
    }

    private void resubmitExpiredStrategiesAfterMarketOpen() {
        int submitted = 0;
        for (Strategy strategy : strategyRepository.findAll()) {
            if (!matchesRuntimeMode(strategy) || !isExpiryResubmitEligible(strategy)) {
                continue;
            }
            if (!strategyEngine.canAutoResubmitExpiredEntryOrder(strategy)) {
                continue;
            }
            if (submitPollTask(strategy.id(), Map.of(), null)) {
                submitted++;
            }
        }
        if (submitted > 0) {
            int submittedCount = submitted;
            LOGGER.info(() -> "[POLL][MARKET_OPEN_EXPIRY_RESUBMIT] Submitted "
                    + submittedCount + " expired auto-extension strategy poll(s)");
        }
    }

    private enum PollExecutionResult {
        COMPLETED,
        FAILED
    }

    private void recordMarketClosedAutoRepair(String strategyId, RepairCategory category) {
        if (strategyId == null || strategyId.isBlank()) {
            return;
        }
        pendingMarketClosedAutoRepairedStrategyIds.add(strategyId);
        strategyIdToRepairCategory.put(strategyId, category);
    }

    private StrategyExecutionEvent event(String strategyId, StrategyEventType type, String message, String metadataJson) {
        return new StrategyExecutionEvent(UUID.randomUUID().toString(), strategyId, type, message, metadataJson, Instant.now());
    }

    public interface PollListener {
        PollListener NOOP = new PollListener() {};

        default void onPollStarted(String strategyId) {}
        default void onPollCompleted(String strategyId) {}
        default void onPollFailed(String strategyId) {}

        /**
         * Called after each successful poll cycle with the list of rules that were evaluated.
         * Implementors may use this to surface rule analysis summaries to a UI log.
         *
         * @param strategyId the strategy that was polled
         * @param symbol     the ticker symbol (for display)
         * @param outcomes   ordered list of rule evaluation results from this cycle
         */
        default void onRulesAnalyzed(String strategyId, String symbol, List<StrategyEngine.RuleOutcome> outcomes) {}
    }

    public enum RepairCategory {
        AUTO_MARKET_CLOSED("AUTO_MARKET_CLOSED→ACTIVE"),
        MANUAL_MARKET_CLOSED_OVERRIDE("MANUAL_OVERRIDE→NONE");

        private final String label;

        RepairCategory(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record MarketClosedAutoRepairSummary(
            List<String> strategyIds,
            Map<RepairCategory, Integer> categoryCounts
    ) {
        public boolean isEmpty() {
            return strategyIds.isEmpty();
        }

        public String formatSummary() {
            if (strategyIds.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (RepairCategory category : RepairCategory.values()) {
                Integer count = categoryCounts.get(category);
                if (count != null && count > 0) {
                    if (!first) sb.append(", ");
                    sb.append(category.label()).append(" (").append(count).append(")");
                    first = false;
                }
            }
            return sb.toString();
        }
    }

    public record PollCycleSnapshot(
            boolean cycleEvaluated,
            boolean marketClosedSuppressed,
            int scanned,
            int eligible,
            int due,
            int skippedNotDue
    ) {}
}
