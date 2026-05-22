package com.neuralarc.service;

import com.neuralarc.api.AlpacaClient;
import com.neuralarc.api.AlpacaTradeUpdateEvent;
import com.neuralarc.model.PauseReason;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyEventType;
import com.neuralarc.model.StrategyExecutionEvent;
import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyMode;
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

    private final StrategyRepository strategyRepository;
    private final StrategyExecutionEventRepository eventRepository;
    private final StrategyEngine strategyEngine;
    private final StrategyService strategyService;
    private final AppSettingsService appSettingsService;
    private final MarketHoursService marketHoursService;
    private final AlpacaClient alpacaClient;
    private final StrategyMode strategyMode;
    private final TradingSessionPolicy tradingSessionPolicy;
    private final ExecutorService pollExecutor;
    private final Set<String> inFlightStrategyIds = ConcurrentHashMap.newKeySet();
    private volatile Instant lastStreamingEventAt;
    private volatile Boolean lastTradingSessionOpen;
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
        this.strategyRepository = strategyRepository;
        this.eventRepository = eventRepository;
        this.appSettingsService = appSettingsService;
        this.marketHoursService = marketHoursService;
        this.alpacaClient = alpacaClient;
        this.strategyMode = strategyMode;
        this.tradingSessionPolicy = new TradingSessionPolicy(marketHoursService, alpacaClient);
        StrategyEventBus eventBus = new StrategyEventBus();
        StrategyStateMachine stateMachine = new StrategyStateMachine(eventRepository, eventBus);
        this.strategyEngine = new StrategyEngine(
                strategyRepository,
                orderRepository,
                stateMachine,
                alpacaClient,
                appSettingsService,
                marketHoursService
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
        boolean autoPauseForMarketClose = settings.autoPausePollingWhenMarketClosed();
        int totalStrategies = 0;
        int eligibleStrategies = 0;
        int skippedNotDue = 0;
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
                continue;
            }
            eligibleStrategies++;
            eligible.add(strategy);
            if (shouldPoll(strategy, now)) {
                due.add(strategy);
            } else {
                skippedNotDue++;
            }
        }

        // Batch-fetch latest prices for ALL eligible strategy symbols in one API call
        // whenever at least one strategy is due to poll.  This replaces per-strategy
        // getLatestPrice() calls inside reconcile() and reduces total broker API usage.
        Map<String, BigDecimal> priceCache = Map.of();
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
        }

        int dueStrategies = 0;
        final Map<String, BigDecimal> finalPriceCache = priceCache;
        for (Strategy strategy : due) {
            String strategyId = strategy.id();
            if (submitPollTask(strategyId, finalPriceCache)) {
                dueStrategies++;
            }
        }
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("[POLL][CYCLE] autoPause=" + autoPauseForMarketClose
                    + " marketSuppressed=" + suppressedForSession
                    + " scanned=" + totalStrategies
                    + " eligible=" + eligibleStrategies
                    + " due=" + dueStrategies
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
        runPollIfNotInFlight(strategyId, Map.of());
    }

    /** Poll a single strategy using the pre-fetched price cache from the current cycle. */
    void pollStrategy(String strategyId, Map<String, BigDecimal> priceCache) {
        runPollIfNotInFlight(strategyId, priceCache);
    }

    private boolean submitPollTask(String strategyId, Map<String, BigDecimal> priceCache) {
        if (!markStrategyInFlight(strategyId)) {
            LOGGER.fine(() -> "[POLL][SCHEDULER][" + strategyId + "] Skipping dispatch because a poll is already in flight");
            return false;
        }
        try {
            pollExecutor.submit(() -> executePoll(strategyId, priceCache));
            return true;
        } catch (RuntimeException ex) {
            inFlightStrategyIds.remove(strategyId);
            throw ex;
        }
    }

    private void runPollIfNotInFlight(String strategyId, Map<String, BigDecimal> priceCache) {
        if (!markStrategyInFlight(strategyId)) {
            LOGGER.fine(() -> "[POLL][SCHEDULER][" + strategyId + "] Ignoring poll request because a poll is already in flight");
            return;
        }
        executePoll(strategyId, priceCache);
    }

    private boolean markStrategyInFlight(String strategyId) {
        return strategyId != null && !strategyId.isBlank() && inFlightStrategyIds.add(strategyId);
    }

    private void executePoll(String strategyId, Map<String, BigDecimal> priceCache) {
        try {
            doPollStrategy(strategyId, priceCache);
        } finally {
            inFlightStrategyIds.remove(strategyId);
        }
    }

    private void doPollStrategy(String strategyId, Map<String, BigDecimal> priceCache) {
        Optional<Strategy> maybeStrategy = strategyRepository.findById(strategyId);
        if (maybeStrategy.isEmpty()) {
            return;
        }
        Strategy strategy = maybeStrategy.get();
        if (!matchesRuntimeMode(strategy)) {
            return;
        }
        if (strategy.status() == StrategyStatus.FAILED) {
            if (!isExpiryResubmitEligible(strategy) || !strategyEngine.canAutoRetryFailed(strategy)) {
                return;
            }
            LOGGER.info(() -> "[POLL][EXPIRY_RESUBMIT][" + strategy.symbol() + "] Reactivating expired strategy for automatic resubmission");
            strategy.setStatus(StrategyStatus.ACTIVE);
            strategy.setCurrentState(StrategyLifecycleState.CREATED);
            strategy.setPauseReason(PauseReason.NONE);
            strategy.clearLastError();
            strategyRepository.save(strategy);
        }
        if (strategy.status() != StrategyStatus.ACTIVE) {
            return;
        }

        try {
            pollListener.onPollStarted(strategy.id());
            List<StrategyEngine.RuleOutcome> outcomes = strategyEngine.reconcileTracked(strategy, priceCache);
            eventRepository.save(event(strategy.id(), StrategyEventType.POLL_SUCCESS,
                    "Poll completed", "{\"strategyId\":\"" + strategy.id() + "\"}"));
            pollListener.onPollCompleted(strategy.id());
            if (!outcomes.isEmpty()) {
                pollListener.onRulesAnalyzed(strategy.id(), strategy.symbol(), outcomes);
            }
        } catch (Exception ex) {
            strategy.setStatus(StrategyStatus.PAUSED);
            strategy.setCurrentState(StrategyLifecycleState.PAUSED);
            strategy.setPauseReason(PauseReason.SYSTEM_ERROR);
            strategy.setLastPolledAt(Instant.now());
            strategy.setLastError(ex.getMessage());
            strategyRepository.save(strategy);
            eventRepository.save(event(strategy.id(), StrategyEventType.POLL_ERROR, ex.getMessage(), "{}"));
            pollListener.onPollFailed(strategy.id());
            LOGGER.log(Level.WARNING, "Polling failed for strategy " + strategy.id(), ex);
        }
    }

    public void setPollListener(PollListener pollListener) {
        this.pollListener = pollListener == null ? PollListener.NOOP : pollListener;
    }

    public void setEmailNotificationListener(TradeEmailNotificationService.EmailNotificationListener listener) {
        strategyEngine.setEmailNotificationListener(listener);
    }

    public void shutdown() {
        pollExecutor.shutdownNow();
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
                        + " clientOrderId=" + updateEvent.orderData().clientOrderId());
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
        long pollInterval = Math.max(1, strategy.pollingIntervalSeconds());
        if (isStreamHealthy(now)) {
            pollInterval = pollInterval * STREAM_POLL_BACKOFF_MULTIPLIER;
        }
        return elapsedSeconds >= pollInterval;
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
                && strategy.status() == StrategyStatus.FAILED
                && strategy.resubmitOnExpiryEnabled()
                && "expired".equals(BrokerOrderStatusUtil.normalize(strategy.latestOrderStatus()));
    }

    private boolean isStreamHealthy(Instant now) {
        return lastStreamingEventAt != null
                && Duration.between(lastStreamingEventAt, now).getSeconds() <= STREAM_HEALTHY_GRACE_SECONDS;
    }

    private void handleMarketSessionTransition(boolean marketOpen, boolean extendedHoursEnabled, Instant now) {
        if (lastTradingSessionOpen == null || lastTradingSessionOpen != marketOpen) {
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
