package com.neuralarc.service;

import com.neuralarc.api.AlpacaClient;
import com.neuralarc.api.AlpacaPositionData;
import com.neuralarc.model.*;
import com.neuralarc.util.BrokerOrderStatusUtil;
import com.neuralarc.util.ClientOrderId;
import com.neuralarc.util.Monetary;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.logging.Logger;

public class StrategyService {
    private static final Logger LOGGER = Logger.getLogger(StrategyService.class.getName());
    public static final String EXIT_SOURCE_JSON_KEY = "neuralarcExitSource";
    public static final String EXIT_SUBMISSION_TYPE_JSON_KEY = "neuralarcSubmissionType";

    private final StrategyRepository strategyRepository;
    private final StrategyOrderRepository orderRepository;
    private final StrategyExecutionEventRepository eventRepository;
    private final AlpacaClient alpacaClient;
    private final StrategyValidator validator;
    private final boolean liveTradingEnabled;
    private final StrategyMode defaultStrategyMode;
    private final StrategyStateMachine stateMachine;
    private final StrategyEngine strategyEngine;
    private final PendingLimitOrderCanceler pendingLimitOrderCanceler;
    private final LiveStrategyPromotionFactory liveStrategyPromotionFactory;
    private final AppSettingsService appSettingsService;
    private final MarketHoursService marketHoursService;
    private final ManualBuyOrderSubmitter manualBuyOrderSubmitter;
    private WorkspaceCodeResolver workspaceCodeResolver = WorkspaceCodeResolver.unassigned();

    public StrategyService(
            StrategyRepository strategyRepository,
            StrategyOrderRepository orderRepository,
            StrategyExecutionEventRepository eventRepository,
            AlpacaClient alpacaClient,
            StrategyValidator validator,
            boolean liveTradingEnabled,
            StrategyMode defaultStrategyMode
    ) {
        this(
                strategyRepository,
                orderRepository,
                eventRepository,
                alpacaClient,
                validator,
                liveTradingEnabled,
                defaultStrategyMode,
                new AppSettingsService(),
                new MarketHoursService()
        );
    }

    public StrategyService(
            StrategyRepository strategyRepository,
            StrategyOrderRepository orderRepository,
            StrategyExecutionEventRepository eventRepository,
            AlpacaClient alpacaClient,
            StrategyValidator validator,
            boolean liveTradingEnabled,
            StrategyMode defaultStrategyMode,
            AppSettingsService appSettingsService,
            MarketHoursService marketHoursService
    ) {
        this.strategyRepository = strategyRepository;
        this.orderRepository = orderRepository;
        this.eventRepository = eventRepository;
        this.alpacaClient = alpacaClient;
        this.validator = validator;
        this.liveTradingEnabled = liveTradingEnabled;
        this.defaultStrategyMode = defaultStrategyMode == null ? StrategyMode.PAPER : defaultStrategyMode;
        this.appSettingsService = appSettingsService;
        this.marketHoursService = marketHoursService;
        StrategyEventBus eventBus = new StrategyEventBus();
        this.stateMachine = new StrategyStateMachine(eventRepository, eventBus);
        this.strategyEngine = new StrategyEngine(
                strategyRepository,
                orderRepository,
                stateMachine,
                alpacaClient,
                appSettingsService,
                marketHoursService
        );
        this.pendingLimitOrderCanceler = new PendingLimitOrderCanceler(alpacaClient, orderRepository);
        this.liveStrategyPromotionFactory = new LiveStrategyPromotionFactory();
        this.manualBuyOrderSubmitter = new ManualBuyOrderSubmitter(
                strategyRepository,
                orderRepository,
                alpacaClient,
                stateMachine
        );
    }

    private boolean serviceModeMatches(Strategy strategy) {
        return strategy != null && strategy.mode() == defaultStrategyMode;
    }

    private String serviceModeMismatchMessage(Strategy strategy) {
        StrategyMode strategyMode = strategy == null ? null : strategy.mode();
        return "Strategy " + (strategyMode == null ? "mode" : strategyMode.name())
                + " does not match this " + defaultStrategyMode.name() + " broker service";
    }

    public StrategyCreationResult createAndActivate(Strategy strategy) {
        List<String> errors = validator.validate(strategy);
        if (strategy.mode() == StrategyMode.LIVE && !liveTradingEnabled) {
            errors.add("LIVE mode is disabled. Set trading.live.enabled=true to allow live trading.");
        }
        if (!errors.isEmpty()) {
            strategy.setStatus(StrategyStatus.FAILED);
            strategy.setCurrentState(StrategyLifecycleState.FAILED);
            strategy.setLastError(String.join("; ", errors));
            strategyRepository.save(strategy);
            stateMachine.transition(strategy, StrategyLifecycleState.FAILED, StrategyEventType.VALIDATION_FAILED, strategy.lastError(), "{}");
            return StrategyCreationResult.failed(strategy.lastError());
        }

        strategy.setStatus(StrategyStatus.ACTIVE);
        strategy.setCurrentState(StrategyLifecycleState.VALIDATED);
        strategy.setPauseReason(PauseReason.NONE);
        strategy.clearLastError();
        strategyRepository.save(strategy);
        stateMachine.transition(strategy, StrategyLifecycleState.VALIDATED, StrategyEventType.STRATEGY_CREATED, "Strategy validated", "{}");

        StrategyOrder order = strategyEngine.submitBaseBuy(strategy, false);
        if (order == null || order.alpacaOrderId() == null || order.alpacaOrderId().isBlank()) {
            strategy.setStatus(StrategyStatus.FAILED);
            strategy.setCurrentState(StrategyLifecycleState.FAILED);
            String error = strategy.lastError() == null || strategy.lastError().isBlank()
                    ? "Failed to submit base buy order"
                    : strategy.lastError();
            strategy.setLastError(error);
            strategyRepository.save(strategy);
            stateMachine.transition(strategy, StrategyLifecycleState.FAILED, StrategyEventType.STRATEGY_FAILED, error, "{}");
            return StrategyCreationResult.failed(error);
        }
        return StrategyCreationResult.success(strategy.id(), order.id(), order.alpacaOrderId(), order.clientOrderId());
    }

    public Optional<Strategy> updateStrategy(Strategy strategy) {
        Optional<Strategy> existing = strategyRepository.findById(strategy.id());
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        List<String> errors = validator.validate(strategy);
        if (!errors.isEmpty()) {
            strategy.setLastError(String.join("; ", errors));
            strategyRepository.save(strategy);
            return Optional.empty();
        }

        Strategy persisted = existing.get();
        boolean refreshActiveOrders = persisted.status() == StrategyStatus.ACTIVE;
        boolean resubmitClosedStrategy = shouldResubmitClosedStrategyAfterEdit(persisted)
                && strategyEngine.canAutoRetryFailed(persisted);
        boolean resubmitManualCancelStrategy = shouldResubmitManualCancelStrategyAfterEdit(persisted)
                && strategyEngine.canAutoRetryFailed(persisted);
        boolean resubmitEditedStrategy = resubmitClosedStrategy || resubmitManualCancelStrategy;
        if (refreshActiveOrders) {
            // For active strategies, cancel any currently open Alpaca orders before applying
            // edited pricing/quantity so new orders are created from updated settings.
            cancelPendingRemoteOrders(persisted);
            if (!persisted.symbol().equalsIgnoreCase(strategy.symbol())) {
                cancelPendingRemoteOrders(strategy);
            }
        }

        strategy.clearLastError();
        strategy.setPauseReason(PauseReason.NONE);
        if (resubmitEditedStrategy) {
            cancelPendingLocalOrders(persisted);
            strategy.setStatus(StrategyStatus.ACTIVE);
            strategy.setCurrentState(StrategyLifecycleState.CREATED);
            strategy.setLatestOrderStatus("");
            strategy.setLatestAlpacaOrderId("");
        }
        strategyRepository.save(strategy);
        stateMachine.transition(strategy, strategy.currentState(), StrategyEventType.STRATEGY_UPDATED, "Strategy updated", "{}");

        if (refreshActiveOrders && strategy.status() == StrategyStatus.ACTIVE) {
            strategyEngine.resumeStrategy(strategy);
        }
        if (resubmitEditedStrategy) {
            strategyEngine.submitBaseBuy(strategy, false);
        }

        return Optional.of(strategy);
    }

    private boolean shouldResubmitClosedStrategyAfterEdit(Strategy persisted) {
        return persisted.status() == StrategyStatus.FAILED
                && (persisted.currentState() == StrategyLifecycleState.FAILED
                || isClosedBrokerStatus(persisted.latestOrderStatus()));
    }

    private boolean shouldResubmitManualCancelStrategyAfterEdit(Strategy persisted) {
        return persisted.status() == StrategyStatus.PAUSED
                && (persisted.pauseReason() == PauseReason.MANUAL_LIMIT_BUY_CANCELED
                || persisted.pauseReason() == PauseReason.USER_PAUSED);
    }

    private boolean isClosedBrokerStatus(String status) {
        String normalized = BrokerOrderStatusUtil.normalize(status);
        return "expired".equals(normalized)
                || "canceled".equals(normalized)
                || "cancelled".equals(normalized);
    }

    private void cancelPendingLocalOrders(Strategy strategy) {
        for (StrategyOrder order : orderRepository.findByStrategyId(strategy.id())) {
            if (!order.isPending()) {
                continue;
            }
            order.setStatus(StrategyOrderStatus.CANCELED);
            orderRepository.save(order);
        }
    }

    public void pause(String strategyId) {
        strategyRepository.findById(strategyId).ifPresent(strategy -> {
            cancelPendingRemoteOrders(strategy);
            rememberResumeStateBeforePause(strategy);
            strategy.setStatus(StrategyStatus.PAUSED);
            strategy.setCurrentState(StrategyLifecycleState.PAUSED);
            strategy.setPauseReason(PauseReason.MANUAL_LIMIT_BUY_CANCELED);
            strategyRepository.save(strategy);
            stateMachine.transition(
                    strategy,
                    StrategyLifecycleState.PAUSED,
                    StrategyEventType.STRATEGY_PAUSED,
                    "Manual cancel detected; waiting for user to click Place Limit Buy Again",
                    "{}"
            );
            LOGGER.info(() -> "[STRATEGY][" + strategy.symbol() + "] Manual cancel applied. "
                    + "Polling not resumed because user cancellation requires manual restart");
        });
    }

    public LimitBuyCancelResult cancelPendingLimitBuys(String strategyId) {
        Optional<Strategy> maybeStrategy = strategyRepository.findById(strategyId);
        if (maybeStrategy.isEmpty()) {
            return LimitBuyCancelResult.failed("Strategy not found");
        }
        Strategy strategy = maybeStrategy.get();
        if (!serviceModeMatches(strategy)) {
            return LimitBuyCancelResult.failed(serviceModeMismatchMessage(strategy));
        }
        int canceledCount = pendingLimitOrderCanceler.cancelPendingLimitBuys(strategy);
        if (canceledCount <= 0) {
            return LimitBuyCancelResult.failed("No pending limit buy orders found");
        }

        List<StrategyOrder> orders = orderRepository.findByStrategyId(strategy.id());
        if (hasOpenLocalExposure(orders)) {
            StrategyLifecycleState restoredState = stateAfterCanceledBuyWithExposure(strategy.currentState(), orders);
            strategy.setStatus(StrategyStatus.ACTIVE);
            strategy.setCurrentState(restoredState);
            strategy.setPauseReason(PauseReason.NONE);
            strategy.setLatestOrderStatus("canceled");
            strategy.clearLastError();
            strategyRepository.save(strategy);
            stateMachine.transition(
                    strategy,
                    restoredState,
                    StrategyEventType.ORDER_STATUS_UPDATED,
                    "Pending limit buy order(s) canceled; existing position remains active",
                    "{}"
            );
            return LimitBuyCancelResult.success(canceledCount);
        }

        rememberResumeStateBeforePause(strategy);
        strategy.setStatus(StrategyStatus.PAUSED);
        strategy.setCurrentState(StrategyLifecycleState.PAUSED);
        strategy.setPauseReason(PauseReason.MANUAL_LIMIT_BUY_CANCELED);
        strategy.setLatestOrderStatus("canceled");
        strategy.clearLastError();
        strategyRepository.save(strategy);
        stateMachine.transition(
                strategy,
                StrategyLifecycleState.PAUSED,
                StrategyEventType.STRATEGY_PAUSED,
                "Pending limit buy order(s) canceled; waiting for user to click Place Limit Buy Again",
                "{}"
        );
        return LimitBuyCancelResult.success(canceledCount);
    }

    /**
     * Reconcile a strategy's locally stored order statuses against the broker, so an order that is
     * still "accepted"/"new"/"pending" is never shown as filled after an app restart. Broker state
     * wins. Safe to call on startup; only non-terminal orders are looked up.
     */
    public void refreshOrderStatusesFromBroker(String strategyId) {
        if (strategyId == null) {
            return;
        }
        strategyRepository.findById(strategyId).ifPresent(strategy -> {
            if (serviceModeMatches(strategy)) {
                strategyEngine.refreshOrderStatuses(strategy);
            }
        });
    }

    public LimitSellCancelResult cancelPendingLimitSells(String strategyId) {
        Optional<Strategy> maybeStrategy = strategyRepository.findById(strategyId);
        if (maybeStrategy.isEmpty()) {
            return LimitSellCancelResult.failed("Strategy not found");
        }
        Strategy strategy = maybeStrategy.get();
        if (!serviceModeMatches(strategy)) {
            return LimitSellCancelResult.failed(serviceModeMismatchMessage(strategy));
        }
        int canceledCount = pendingLimitOrderCanceler.cancelPendingLimitSells(strategy);
        if (canceledCount <= 0) {
            return LimitSellCancelResult.failed("No pending limit sell orders found");
        }

        if (strategy.currentState() == StrategyLifecycleState.SELL_PLACED
                || strategy.currentState() == StrategyLifecycleState.SELL_PARTIALLY_FILLED) {
            strategy.setCurrentState(StrategyLifecycleState.BASE_BUY_FILLED);
        }
        strategy.setLatestOrderStatus("canceled");
        strategy.clearLastError();
        strategyRepository.save(strategy);
        stateMachine.transition(
                strategy,
                strategy.currentState(),
                StrategyEventType.ORDER_STATUS_UPDATED,
                "Pending limit sell order(s) canceled by portfolio action",
                "{}"
        );
        return LimitSellCancelResult.success(canceledCount);
    }

    public void resume(String strategyId) {
        strategyRepository.findById(strategyId).ifPresent(strategy -> {
            boolean manualCancelResume = strategy.pauseReason() == PauseReason.MANUAL_LIMIT_BUY_CANCELED;
            strategy.setStatus(StrategyStatus.ACTIVE);
            restoreResumeStateAfterPause(strategy);
            boolean marketClosed = shouldSuppressPollingForMarketClose(strategy);
            strategy.setPauseReason(marketClosed ? PauseReason.MANUAL_MARKET_CLOSED_OVERRIDE : PauseReason.NONE);
            strategy.clearLastError();
            strategyRepository.save(strategy);
            stateMachine.transition(
                    strategy,
                    strategy.currentState(),
                    StrategyEventType.STRATEGY_RESUMED,
                    manualCancelResume
                            ? "Manual cancel cleared by user action (Place Limit Buy Again)"
                            : "Strategy resumed",
                    "{}"
            );
            if (!marketClosed) {
                if (manualCancelResume) {
                    LOGGER.info(() -> "[STRATEGY][" + strategy.symbol() + "] User requested Place Limit Buy Again. "
                            + "Clearing manual-cancel state and resuming strategy execution");
                }
                strategyEngine.resumeStrategy(strategy);
            } else if (manualCancelResume) {
                LOGGER.info(() -> "[STRATEGY][" + strategy.symbol() + "] User requested Place Limit Buy Again, "
                        + "but polling not resumed because market-closed suppression is active");
            }
        });
    }

    public void autoPauseForMarketClose(String strategyId, String reasonMessage) {
        strategyRepository.findById(strategyId).ifPresent(strategy -> {
            if (strategy.status() != StrategyStatus.ACTIVE) {
                return;
            }
            if (strategy.pauseReason() == PauseReason.MANUAL_MARKET_CLOSED_OVERRIDE) {
                return;
            }
            rememberResumeStateBeforePause(strategy);
            strategy.setStatus(StrategyStatus.PAUSED);
            strategy.setCurrentState(StrategyLifecycleState.PAUSED);
            strategy.setPauseReason(PauseReason.AUTO_MARKET_CLOSED);
            strategy.clearLastError();
            strategyRepository.save(strategy);
            stateMachine.transition(strategy, StrategyLifecycleState.PAUSED, StrategyEventType.STRATEGY_PAUSED, reasonMessage, "{}");
        });
    }

    public void autoResumeFromMarketClose(String strategyId, String reasonMessage) {
        strategyRepository.findById(strategyId).ifPresent(strategy -> {
            if (strategy.status() != StrategyStatus.PAUSED || strategy.pauseReason() != PauseReason.AUTO_MARKET_CLOSED) {
                if (strategy.pauseReason() == PauseReason.MANUAL_LIMIT_BUY_CANCELED) {
                    LOGGER.fine(() -> "[STRATEGY][" + strategy.symbol() + "] Auto-resume skipped: "
                            + "manual cancel requires Place Limit Buy Again");
                }
                return;
            }
            strategy.setStatus(StrategyStatus.ACTIVE);
            restoreResumeStateAfterPause(strategy);
            strategy.setPauseReason(PauseReason.NONE);
            strategy.clearLastError();
            strategyRepository.save(strategy);
            stateMachine.transition(strategy, strategy.currentState(), StrategyEventType.STRATEGY_RESUMED, reasonMessage, "{}");
            strategyEngine.resumeStrategy(strategy);
        });
    }

    public void stop(String strategyId) {
        strategyRepository.findById(strategyId).ifPresent(strategy -> {
            strategy.setStatus(StrategyStatus.STOPPED);
            strategy.setCurrentState(StrategyLifecycleState.STOPPED);
            strategy.setPauseReason(PauseReason.NONE);
            strategyRepository.save(strategy);
            stateMachine.transition(strategy, StrategyLifecycleState.STOPPED, StrategyEventType.STRATEGY_STOPPED, "Strategy stopped", "{}");
        });
    }

    public void delete(String strategyId) {
        strategyRepository.findById(strategyId).ifPresent(this::cancelPendingRemoteOrders);
        stop(strategyId);
        strategyRepository.deleteById(strategyId);
        orderRepository.deleteByStrategyId(strategyId);
        eventRepository.deleteByStrategyId(strategyId);
    }

    public Optional<Strategy> recoverStaleRestartFailure(String strategyId) {
        Optional<Strategy> maybeStrategy = strategyRepository.findById(strategyId);
        if (maybeStrategy.isEmpty()) {
            return Optional.empty();
        }

        Strategy strategy = maybeStrategy.get();
        if (strategy.status() != StrategyStatus.FAILED
                || strategy.currentState() != StrategyLifecycleState.FAILED
                || !"Projected quantity exceeds maxTotalQuantity".equals(strategy.lastError())) {
            return Optional.of(strategy);
        }

        List<StrategyOrder> orders = orderRepository.findByStrategyId(strategy.id());
        Optional<StrategyOrder> latestFilledExitOrder = orders.stream()
                .filter(order -> order.side() == StrategyOrderSide.SELL)
                .filter(order -> order.status() == StrategyOrderStatus.FILLED)
                .max(Comparator.comparing(order -> order.filledAt() == null ? Instant.EPOCH : order.filledAt()));
        if (latestFilledExitOrder.isEmpty()) {
            return Optional.of(strategy);
        }

        Optional<AlpacaPositionData> position = alpacaClient.getPosition(strategy.symbol());
        if (position.isPresent() && position.get().exists()) {
            return Optional.of(strategy);
        }

        cancelPendingRemoteOrders(strategy);
        for (StrategyOrder order : orders) {
            if (!order.isPending()) {
                continue;
            }
            order.setStatus(StrategyOrderStatus.CANCELED);
            orderRepository.save(order);
        }

        strategy.clearLastError();
        strategy.setPauseReason(PauseReason.NONE);
        strategy.clearProfitHoldTracking();
        if (strategy.restartAfterExitEnabled() && isProfitableExitStage(latestFilledExitOrder.get().stage())) {
            strategy.setCurrentState(StrategyLifecycleState.CREATED);
            strategy.setStatus(StrategyStatus.ACTIVE);
            strategy.setLastEvent("Recovered from stale restart failure");
        } else {
            strategy.setCurrentState(StrategyLifecycleState.COMPLETED);
            strategy.setStatus(StrategyStatus.COMPLETED);
            strategy.setLastEvent("Recovered from stale failure after completed exit");
        }
        strategyRepository.save(strategy);
        return Optional.of(strategy);
    }

    public LivePromotionPreview previewLivePromotion(String strategyId) {
        Optional<Strategy> maybeStrategy = strategyRepository.findById(strategyId);
        if (maybeStrategy.isEmpty()) {
            return LivePromotionPreview.missing("Strategy not found.");
        }
        Strategy strategy = maybeStrategy.get();
        List<String> issues = new java.util.ArrayList<>();
        if (strategy.mode() != StrategyMode.PAPER) {
            issues.add("Only paper strategies can be promoted to LIVE.");
        }
        if (strategy.status() == StrategyStatus.ARCHIVED) {
            issues.add("Archived strategies cannot be promoted to LIVE.");
        }
        if (!liveTradingEnabled) {
            issues.add("LIVE mode is disabled. Set trading.live.enabled=true in app.properties first.");
        }
        if (defaultStrategyMode != StrategyMode.LIVE) {
            issues.add("Switch the application connection to LIVE mode before promoting this strategy.");
        }
        List<String> validationErrors = validator.validate(strategy);
        if (!validationErrors.isEmpty()) {
            issues.add("Strategy validation must pass before live promotion.");
        }

        int pendingPaperOrders = (int) orderRepository.findByStrategyId(strategy.id()).stream()
                .filter(StrategyOrder::isPending)
                .count();

        boolean liveStrategyConflict = strategyRepository.findAll().stream()
                .filter(existing -> !existing.id().equals(strategy.id()))
                .filter(existing -> existing.mode() == StrategyMode.LIVE)
                .filter(existing -> existing.status() != StrategyStatus.ARCHIVED)
                .anyMatch(existing -> existing.symbol().equalsIgnoreCase(strategy.symbol()));
        if (liveStrategyConflict) {
            issues.add("A non-archived LIVE strategy for " + strategy.symbol() + " already exists.");
        }

        List<com.neuralarc.api.AlpacaOrderData> liveOpenOrders = alpacaClient.getOpenOrders(strategy.symbol());
        if (!liveOpenOrders.isEmpty()) {
            issues.add("The LIVE account already has " + liveOpenOrders.size() + " open order(s) for " + strategy.symbol() + ".");
        }
        Optional<com.neuralarc.api.AlpacaPositionData> livePosition = alpacaClient.getPosition(strategy.symbol());
        if (livePosition.isPresent() && livePosition.get().exists()) {
            issues.add("The LIVE account already has an open position for " + strategy.symbol() + ".");
        }

        AppSettingsService.AppSettings settings = appSettingsService.load();
        boolean marketSessionOpen = marketHoursService.isTradingSessionOpen(settings.extendedHoursTradingEnabled());
        return new LivePromotionPreview(
                strategy,
                issues.isEmpty(),
                List.copyOf(issues),
                List.copyOf(validationErrors),
                pendingPaperOrders,
                liveOpenOrders.size(),
                livePosition.filter(com.neuralarc.api.AlpacaPositionData::exists)
                        .map(com.neuralarc.api.AlpacaPositionData::quantity)
                        .orElse(BigDecimal.ZERO),
                marketSessionOpen
        );
    }

    public LivePromotionResult promotePaperStrategyToLive(String strategyId) {
        return promotePaperStrategyToLive(strategyId, null);
    }

    public LivePromotionResult promotePaperStrategyToLive(String strategyId, LivePromotionEdits edits) {
        LivePromotionPreview preview = previewLivePromotion(strategyId);
        if (!preview.exists()) {
            return LivePromotionResult.failed(preview.issues().isEmpty() ? "Strategy not found." : preview.issues().getFirst());
        }
        if (!preview.eligible()) {
            return LivePromotionResult.failed(String.join(" ", preview.issues()));
        }

        if (edits != null) {
            String validationError = validatePromotionEdits(edits, preview.strategy());
            if (validationError != null) {
                return LivePromotionResult.failed(validationError);
            }
        }

        Strategy paperStrategy = preview.strategy();

        LivePromotionEdits normalizedEdits = edits == null
                ? null
                : new LivePromotionEdits(
                        Monetary.round(edits.baseBuyPrice()),
                        edits.baseBuyQty(),
                        edits.buyLevel1Price() != null ? Monetary.round(edits.buyLevel1Price()) : null,
                        edits.buyLevel1Qty(),
                        edits.buyLevel2Price() != null ? Monetary.round(edits.buyLevel2Price()) : null,
                        edits.buyLevel2Qty(),
                        Monetary.round(edits.targetSellPrice()),
                        edits.lossBuyLevelsEnabled()
                );
        Strategy liveStrategy = liveStrategyPromotionFactory.cloneFromPaper(paperStrategy, normalizedEdits);
        StrategyCreationResult creationResult = createAndActivate(liveStrategy);
        if (!creationResult.success()) {
            return LivePromotionResult.failed(creationResult.error());
        }

        archivePaperStrategyAfterPromotion(paperStrategy, liveStrategy.id());
        return LivePromotionResult.success(
                paperStrategy.id(),
                liveStrategy.id(),
                creationResult.alpacaOrderId(),
                creationResult.clientOrderId()
        );
    }

    private String validatePromotionEdits(LivePromotionEdits edits, Strategy paperStrategy) {
        if (edits.baseBuyPrice() == null || edits.baseBuyPrice().compareTo(BigDecimal.ZERO) <= 0) {
            return "Base buy price must be greater than zero for live promotion.";
        }
        if (edits.baseBuyQty() != null && edits.baseBuyQty() <= 0) {
            return "Base buy quantity must be greater than zero for live promotion.";
        }

        boolean lossBuyLevelsEnabled = edits.lossBuyLevelsEnabled() != null
                ? edits.lossBuyLevelsEnabled()
                : paperStrategy.lossBuyLevelsEnabled();
        if (lossBuyLevelsEnabled) {
            BigDecimal level1Price = edits.buyLevel1Price() != null
                    ? edits.buyLevel1Price() : paperStrategy.buyLimit1Price();
            BigDecimal level2Price = edits.buyLevel2Price() != null
                    ? edits.buyLevel2Price() : paperStrategy.buyLimit2Price();

            if (level1Price.compareTo(BigDecimal.ZERO) <= 0) {
                return "Buy Level 1 price must be greater than zero.";
            }
            if (level1Price.compareTo(edits.baseBuyPrice()) >= 0) {
                return "Buy Level 1 price must be less than base buy price.";
            }
            if (edits.buyLevel1Qty() != null && edits.buyLevel1Qty() <= 0) {
                return "Buy Level 1 quantity must be greater than zero.";
            }
            if (level2Price.compareTo(BigDecimal.ZERO) <= 0) {
                return "Buy Level 2 price must be greater than zero.";
            }
            if (level2Price.compareTo(level1Price) >= 0) {
                return "Buy Level 2 price must be less than Buy Level 1 price.";
            }
            if (edits.buyLevel2Qty() != null && edits.buyLevel2Qty() <= 0) {
                return "Buy Level 2 quantity must be greater than zero.";
            }
        }

        if (edits.targetSellPrice() == null) {
            return "Target sell price is required for live promotion.";
        }
        if (paperStrategy.targetSellEnabled()) {
            if (edits.targetSellPrice().compareTo(BigDecimal.ZERO) <= 0) {
                return "Target sell price must be greater than zero when target sell is enabled.";
            }
            if (edits.targetSellPrice().compareTo(edits.baseBuyPrice()) < 0) {
                return "Target sell price must be greater than or equal to base buy price when target sell is enabled.";
            }
        } else if (edits.targetSellPrice().compareTo(BigDecimal.ZERO) < 0) {
            return "Target sell price cannot be negative.";
        }
        return null;
    }

    public List<Strategy> syncRemoteStrategies() {
        Set<String> localSymbols = strategyRepository.findAll().stream()
                .map(Strategy::symbol)
                .map(String::toUpperCase)
                .collect(Collectors.toCollection(HashSet::new));

        Map<String, List<com.neuralarc.api.AlpacaOrderData>> openOrdersBySymbol = alpacaClient.getOpenOrders().stream()
                .filter(order -> order.symbol() != null && !order.symbol().isBlank())
                .collect(Collectors.groupingBy(order -> order.symbol().toUpperCase(), HashMap::new, Collectors.toList()));
        Map<String, com.neuralarc.api.AlpacaPositionData> positionsBySymbol = alpacaClient.getPositions().stream()
                .filter(position -> position.symbol() != null && !position.symbol().isBlank())
                .collect(Collectors.toMap(position -> position.symbol().toUpperCase(), position -> position, (left, right) -> left, HashMap::new));

        Set<String> remoteSymbols = new HashSet<>(openOrdersBySymbol.keySet());
        remoteSymbols.addAll(positionsBySymbol.keySet());

        List<Strategy> created = new java.util.ArrayList<>();
        for (String symbol : remoteSymbols) {
            if (localSymbols.contains(symbol)) {
                continue;
            }
            Strategy strategy = buildRemoteStrategy(symbol, openOrdersBySymbol.getOrDefault(symbol, List.of()), positionsBySymbol.get(symbol));
            strategyRepository.save(strategy);
            for (com.neuralarc.api.AlpacaOrderData order : openOrdersBySymbol.getOrDefault(symbol, List.of())) {
                orderRepository.save(buildRemoteOrder(strategy, order));
            }
            stateMachine.transition(strategy, strategy.currentState(), StrategyEventType.STRATEGY_RESUMED,
                    "Remote Alpaca strategy synced and resumed", "{}");
            created.add(strategy);
        }
        return created;
    }

    public StrategyCreationResult buyMoreAtMarket(String strategyId, int quantity) {
        return manualBuyOrderSubmitter.submitMarket(strategyId, quantity);
    }

    public StrategyCreationResult buyMoreAtLimit(String strategyId, int quantity, BigDecimal limitPrice) {
        return buyMoreAtLimit(strategyId, quantity, limitPrice, false);
    }

    public StrategyCreationResult buyMoreAtLimit(
            String strategyId,
            int quantity,
            BigDecimal limitPrice,
            boolean repositionAfterExpiry
    ) {
        return manualBuyOrderSubmitter.submitLimit(strategyId, quantity, limitPrice, repositionAfterExpiry);
    }

    public StrategyCreationResult closePosition(String strategyId) {
        return closePosition(strategyId, SellSubmissionType.LIMIT);
    }

    public StrategyCreationResult closePosition(String strategyId, SellSubmissionType submissionType) {
        return closePosition(strategyId, submissionType, SellExecutionSource.MANUAL_USER);
    }

    public StrategyCreationResult closePosition(
            String strategyId,
            SellSubmissionType submissionType,
            SellExecutionSource executionSource
    ) {
        Optional<Strategy> maybeStrategy = strategyRepository.findById(strategyId);
        if (maybeStrategy.isEmpty()) {
            return StrategyCreationResult.failed("Strategy not found");
        }
        Strategy strategy = maybeStrategy.get();
        if (!serviceModeMatches(strategy)) {
            return StrategyCreationResult.failed(serviceModeMismatchMessage(strategy));
        }
        Optional<com.neuralarc.api.AlpacaPositionData> position = alpacaClient.getPosition(strategy.symbol());
        if (position.isEmpty() || !position.get().exists()) {
            return StrategyCreationResult.failed("No open position to close");
        }
        // Extract marketPrice from position response (avoids redundant /trades/latest API call)
        BigDecimal latestPrice = position.get().marketPrice() != null && position.get().marketPrice().compareTo(BigDecimal.ZERO) > 0
                ? position.get().marketPrice()
                : alpacaClient.getLatestPrice(strategy.symbol());
        int quantity = position.get().quantity().setScale(0, java.math.RoundingMode.DOWN).intValue();
        if (quantity <= 0) {
            return StrategyCreationResult.failed("No open quantity to close");
        }
        cancelPendingRemoteOrders(strategy);
        String clientOrderId = buildClientOrderId(strategy, StrategyStage.MANUAL_EXIT, workspaceCodeResolver);
        SellSubmissionType effectiveType = submissionType == null ? SellSubmissionType.LIMIT : submissionType;
        com.neuralarc.api.AlpacaOrderData submitted = effectiveType == SellSubmissionType.MARKET
                ? alpacaClient.submitMarketSellOrder(strategy.symbol(), quantity, clientOrderId)
                : alpacaClient.submitLimitSellOrder(strategy.symbol(), quantity, latestPrice, clientOrderId);
        Instant submittedAt = submitted.submittedAt() == null ? Instant.now() : submitted.submittedAt();
        String enrichedRawJson = withExitMetadata(submitted.rawJson(), executionSource, effectiveType);
        StrategyOrder order = new StrategyOrder(
                java.util.UUID.randomUUID().toString(),
                strategy.id(),
                StrategyStage.MANUAL_EXIT,
                submitted.orderId(),
                clientOrderId,
                strategy.symbol(),
                StrategyOrderSide.SELL,
                effectiveType == SellSubmissionType.MARKET ? StrategyOrderType.MARKET : StrategyOrderType.LIMIT,
                effectiveType == SellSubmissionType.MARKET ? BigDecimal.ZERO : latestPrice,
                BigDecimal.ZERO,
                BigDecimal.valueOf(quantity),
                submitted.filledQuantity(),
                submitted.filledAveragePrice(),
                mapOrderStatus(submitted.status()),
                submittedAt,
                Instant.now(),
                null,
                enrichedRawJson
        );
        orderRepository.save(order);
        strategy.setLatestOrderStatus(BrokerOrderStatusUtil.normalize(submitted.status()));
        strategy.setLatestAlpacaOrderId(order.alpacaOrderId());
        strategy.setLastTriggeredRuleType("MANUAL_EXIT");
        strategyRepository.save(strategy);
        stateMachine.transition(
                strategy,
                StrategyLifecycleState.SELL_PLACED,
                StrategyEventType.ORDER_SUBMITTED,
                effectiveType == SellSubmissionType.MARKET ? "Manual market sell order submitted" : "Manual limit sell order submitted",
                enrichedRawJson
        );
        return StrategyCreationResult.success(
                strategy.id(),
                order.id(),
                order.alpacaOrderId(),
                order.clientOrderId(),
                submitted.filledQuantity(),
                submitted.filledAveragePrice()
        );
    }

    private String withExitMetadata(
            String rawJson,
            SellExecutionSource executionSource,
            SellSubmissionType submissionType
    ) {
        try {
            JSONObject json = rawJson == null || rawJson.isBlank() ? new JSONObject() : new JSONObject(rawJson);
            json.put(EXIT_SOURCE_JSON_KEY, (executionSource == null ? SellExecutionSource.MANUAL_USER : executionSource).name());
            json.put(EXIT_SUBMISSION_TYPE_JSON_KEY, (submissionType == null ? SellSubmissionType.LIMIT : submissionType).name());
            return json.toString();
        } catch (Exception ignored) {
            return rawJson == null ? "" : rawJson;
        }
    }

    public enum SellExecutionSource {
        MANUAL_USER,
        PORTFOLIO_ACTION,
        PORTFOLIO_CAPTURE
    }

    /**
     * Builds a structured Alpaca {@code client_order_id} that embeds the strategy's mode, workspace
     * code, symbol and order stage (see {@link ClientOrderId}). The {@code resolver} maps the
     * strategy's workspace to its short code (or "ALL" when unassigned).
     */
    public static String buildClientOrderId(Strategy strategy, StrategyStage stage, WorkspaceCodeResolver resolver) {
        String code = resolver == null
                ? ClientOrderId.UNASSIGNED_CODE
                : resolver.codeForWorkspace(strategy.workspaceId());
        return ClientOrderId.build(strategy.mode(), code, strategy.symbol(), stage.name());
    }

    /** Sets the workspace-code resolver and propagates it to the order-submitting collaborators. */
    public void setWorkspaceCodeResolver(WorkspaceCodeResolver resolver) {
        this.workspaceCodeResolver = resolver == null ? WorkspaceCodeResolver.unassigned() : resolver;
        strategyEngine.setWorkspaceCodeResolver(this.workspaceCodeResolver);
        manualBuyOrderSubmitter.setWorkspaceCodeResolver(this.workspaceCodeResolver);
    }

    public static StrategyOrderStatus mapOrderStatus(String alpacaStatus) {
        String normalized = alpacaStatus == null ? "" : alpacaStatus.trim().toLowerCase();
        return switch (normalized) {
            case "new", "accepted", "pending_new", "accepted_for_bidding" -> StrategyOrderStatus.SUBMITTED;
            case "partially_filled" -> StrategyOrderStatus.PARTIALLY_FILLED;
            case "filled" -> StrategyOrderStatus.FILLED;
            case "canceled", "expired" -> StrategyOrderStatus.CANCELED;
            case "rejected", "suspended" -> StrategyOrderStatus.REJECTED;
            case "pending_cancel", "pending_replace", "calculated" -> StrategyOrderStatus.PENDING;
            case "failed_transport", "api_error", "failed" -> StrategyOrderStatus.FAILED;
            default -> StrategyOrderStatus.PENDING;
        };
    }

    private void cancelPendingRemoteOrders(Strategy strategy) {
        List<com.neuralarc.api.AlpacaOrderData> openOrders = alpacaClient.getOpenOrders(strategy.symbol());
        if (openOrders.isEmpty()) {
            return;
        }
        boolean canceledAny = false;
        for (com.neuralarc.api.AlpacaOrderData order : openOrders) {
            if (alpacaClient.cancelOrder(order.orderId())) {
                canceledAny = true;
                orderRepository.findByStrategyId(strategy.id()).stream()
                        .filter(localOrder -> localOrder.isPending())
                        .filter(localOrder -> order.orderId().equals(localOrder.alpacaOrderId())
                                || order.clientOrderId().equals(localOrder.clientOrderId()))
                        .forEach(localOrder -> {
                            localOrder.setStatus(StrategyOrderStatus.CANCELED);
                            localOrder.setRawResponseJson(order.rawJson());
                            orderRepository.save(localOrder);
                        });
            }
        }
        if (canceledAny) {
            stateMachine.transition(strategy, strategy.currentState(), StrategyEventType.ORDER_STATUS_UPDATED,
                    "Open Alpaca orders canceled for strategy " + strategy.symbol(), "{}");
        }
    }

    private Strategy buildRemoteStrategy(
            String symbol,
            List<com.neuralarc.api.AlpacaOrderData> openOrders,
            com.neuralarc.api.AlpacaPositionData position
    ) {
        com.neuralarc.api.AlpacaOrderData latestOpenOrder = openOrders.stream()
                .max(Comparator.comparing(com.neuralarc.api.AlpacaOrderData::orderId))
                .orElse(null);
        BigDecimal baseBuyPrice = position != null && position.avgEntryPrice().compareTo(BigDecimal.ZERO) > 0
                ? position.avgEntryPrice()
                : latestOpenOrder != null && latestOpenOrder.limitPrice().compareTo(BigDecimal.ZERO) > 0
                ? latestOpenOrder.limitPrice()
                : BigDecimal.ONE;
        int baseBuyQuantity = position != null && position.quantity().compareTo(BigDecimal.ZERO) > 0
                ? position.quantity().intValue()
                : requestedQuantity(latestOpenOrder);
        if (baseBuyQuantity <= 0) {
            baseBuyQuantity = 1;
        }
        StrategyLifecycleState state = determineRemoteState(latestOpenOrder, position);
        Strategy strategy = new Strategy(
                UUID.randomUUID().toString(),
                symbol + " Remote Strategy",
                symbol,
                defaultStrategyMode,
                StrategyStatus.ACTIVE,
                state,
                baseBuyPrice,
                baseBuyQuantity,
                BigDecimal.ZERO,
                0,
                BigDecimal.ZERO,
                0,
                false,
                StopLossType.FIXED_PRICE,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                false,
                BigDecimal.ZERO,
                false,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                true,
                false,
                ProfitHoldType.PERCENT_TRAILING,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                false,
                Math.max(baseBuyQuantity, baseBuyQuantity * 2),
                baseBuyPrice.multiply(BigDecimal.valueOf(Math.max(baseBuyQuantity, 1L) * 2L)),
                30,
                Instant.now(),
                Instant.now()
        );
        strategy.setLastEvent("Synced from Alpaca remote state");
        strategy.setLatestOrderStatus(latestOpenOrder == null ? "" : BrokerOrderStatusUtil.normalize(latestOpenOrder.status()));
        strategy.setLatestAlpacaOrderId(latestOpenOrder == null ? "" : latestOpenOrder.orderId());
        return strategy;
    }

    private void rememberResumeStateBeforePause(Strategy strategy) {
        if (strategy.currentState() != null && strategy.currentState() != StrategyLifecycleState.PAUSED) {
            strategy.setResumeStateBeforePause(strategy.currentState());
        }
    }

    private boolean hasOpenLocalExposure(List<StrategyOrder> orders) {
        BigDecimal quantity = BigDecimal.ZERO;
        for (StrategyOrder order : orders) {
            if (order == null || order.filledQuantity() == null
                    || order.filledQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            if (order.side() == StrategyOrderSide.BUY) {
                quantity = quantity.add(order.filledQuantity());
            } else if (order.side() == StrategyOrderSide.SELL) {
                quantity = quantity.subtract(order.filledQuantity());
            }
        }
        return quantity.compareTo(BigDecimal.ZERO) > 0;
    }

    private StrategyLifecycleState stateAfterCanceledBuyWithExposure(
            StrategyLifecycleState currentState,
            List<StrategyOrder> orders
    ) {
        if (currentState == StrategyLifecycleState.BUY_LIMIT_2_PLACED
                || currentState == StrategyLifecycleState.BUY_LIMIT_2_PARTIALLY_FILLED) {
            return hasFilledBuyStage(orders, StrategyStage.BUY_LIMIT_2)
                    ? StrategyLifecycleState.BUY_LIMIT_2_FILLED
                    : latestFilledBuyState(orders);
        }
        if (currentState == StrategyLifecycleState.BUY_LIMIT_1_PLACED
                || currentState == StrategyLifecycleState.BUY_LIMIT_1_PARTIALLY_FILLED) {
            return hasFilledBuyStage(orders, StrategyStage.BUY_LIMIT_1)
                    ? StrategyLifecycleState.BUY_LIMIT_1_FILLED
                    : latestFilledBuyState(orders);
        }
        if (currentState == StrategyLifecycleState.BASE_BUY_PLACED
                || currentState == StrategyLifecycleState.BASE_BUY_PARTIALLY_FILLED
                || currentState == StrategyLifecycleState.PAUSED
                || currentState == StrategyLifecycleState.FAILED
                || currentState == null) {
            return latestFilledBuyState(orders);
        }
        return currentState;
    }

    private StrategyLifecycleState latestFilledBuyState(List<StrategyOrder> orders) {
        if (hasFilledBuyStage(orders, StrategyStage.BUY_LIMIT_2)) {
            return StrategyLifecycleState.BUY_LIMIT_2_FILLED;
        }
        if (hasFilledBuyStage(orders, StrategyStage.BUY_LIMIT_1)) {
            return StrategyLifecycleState.BUY_LIMIT_1_FILLED;
        }
        return StrategyLifecycleState.BASE_BUY_FILLED;
    }

    private boolean hasFilledBuyStage(List<StrategyOrder> orders, StrategyStage stage) {
        return orders.stream()
                .filter(order -> order != null && order.stage() == stage && order.side() == StrategyOrderSide.BUY)
                .anyMatch(order -> order.filledQuantity() != null
                        && order.filledQuantity().compareTo(BigDecimal.ZERO) > 0);
    }

    private boolean isProfitableExitStage(StrategyStage stage) {
        return stage == StrategyStage.TARGET_SELL
                || stage == StrategyStage.PROFIT_EXIT
                || stage == StrategyStage.MANUAL_EXIT;
    }

    private void archivePaperStrategyAfterPromotion(Strategy paperStrategy, String promotedLiveStrategyId) {
        paperStrategy.setStatus(StrategyStatus.ARCHIVED);
        paperStrategy.setCurrentState(StrategyLifecycleState.STOPPED);
        paperStrategy.setPauseReason(PauseReason.NONE);
        paperStrategy.setResumeStateBeforePause(StrategyLifecycleState.STOPPED);
        paperStrategy.setLastEvent("Archived after promotion to LIVE strategy " + promotedLiveStrategyId);
        paperStrategy.clearLastError();
        strategyRepository.save(paperStrategy);
        stateMachine.transition(
                paperStrategy,
                StrategyLifecycleState.STOPPED,
                StrategyEventType.STRATEGY_ARCHIVED,
                "Paper strategy archived after live promotion",
                "{}"
        );
    }


    private void restoreResumeStateAfterPause(Strategy strategy) {
        if (strategy.currentState() == StrategyLifecycleState.PAUSED) {
            StrategyLifecycleState restoreState = strategy.resumeStateBeforePause();
            strategy.setCurrentState(restoreState == null || restoreState == StrategyLifecycleState.PAUSED
                    ? StrategyLifecycleState.VALIDATED
                    : restoreState);
        }
    }

    private boolean shouldSuppressPollingForMarketClose(Strategy strategy) {
        AppSettingsService.AppSettings settings = appSettingsService.load();
        if (!settings.autoPausePollingWhenMarketClosed()) {
            return false;
        }
        boolean extendedEnabled = settings.extendedHoursTradingEnabled();
        if (!extendedEnabled) {
            return !marketHoursService.isTradingSessionOpen(false);
        }
        boolean overnightEligible = strategy != null && alpacaClient.supportsOvernightSession(strategy.symbol());
        return !marketHoursService.isTradingSessionOpen(Instant.now(), true, overnightEligible);
    }

    private StrategyOrder buildRemoteOrder(Strategy strategy, com.neuralarc.api.AlpacaOrderData remoteOrder) {
        Instant submittedAt = remoteOrder.submittedAt() == null ? Instant.now() : remoteOrder.submittedAt();
        return new StrategyOrder(
                UUID.randomUUID().toString(),
                strategy.id(),
                mapRemoteStage(remoteOrder),
                remoteOrder.orderId(),
                remoteOrder.clientOrderId().isBlank() ? buildClientOrderId(strategy, mapRemoteStage(remoteOrder), workspaceCodeResolver) : remoteOrder.clientOrderId(),
                strategy.symbol(),
                "sell".equalsIgnoreCase(remoteOrder.side()) ? StrategyOrderSide.SELL : StrategyOrderSide.BUY,
                StrategyOrderType.LIMIT,
                remoteOrder.limitPrice(),
                BigDecimal.ZERO,
                BigDecimal.valueOf(requestedQuantity(remoteOrder)),
                remoteOrder.filledQuantity(),
                remoteOrder.filledAveragePrice(),
                mapOrderStatus(remoteOrder.status()),
                submittedAt,
                Instant.now(),
                null,
                remoteOrder.rawJson()
        );
    }

    private StrategyStage mapRemoteStage(com.neuralarc.api.AlpacaOrderData remoteOrder) {
        if ("sell".equalsIgnoreCase(remoteOrder.side())) {
            return StrategyStage.TARGET_SELL;
        }
        return StrategyStage.BASE_BUY;
    }

    private StrategyLifecycleState determineRemoteState(com.neuralarc.api.AlpacaOrderData latestOpenOrder, com.neuralarc.api.AlpacaPositionData position) {
        if (latestOpenOrder != null && "sell".equalsIgnoreCase(latestOpenOrder.side())) {
            return StrategyLifecycleState.SELL_PLACED;
        }
        if (position != null && position.exists()) {
            return StrategyLifecycleState.BASE_BUY_FILLED;
        }
        if (latestOpenOrder != null && "buy".equalsIgnoreCase(latestOpenOrder.side())) {
            return StrategyLifecycleState.BASE_BUY_PLACED;
        }
        return StrategyLifecycleState.VALIDATED;
    }

    private int requestedQuantity(com.neuralarc.api.AlpacaOrderData order) {
        if (order == null || order.rawJson() == null || order.rawJson().isBlank()) {
            return 0;
        }
        try {
            JSONObject json = new JSONObject(order.rawJson());
            return new BigDecimal(json.optString("qty", "0")).intValue();
        } catch (Exception ex) {
            return 0;
        }
    }

    public ArchiveResult archiveStrategy(String strategyId, String reason) {
        if (strategyId == null || strategyId.isBlank()) {
            return ArchiveResult.failed("Strategy id is missing");
        }
        Optional<Strategy> maybeStrategy = strategyRepository.findById(strategyId);
        if (maybeStrategy.isEmpty()) {
            return ArchiveResult.failed("Strategy not found");
        }
        Strategy strategy = maybeStrategy.get();
        strategy.setStatus(StrategyStatus.ARCHIVED);
        strategy.setCurrentState(StrategyLifecycleState.STOPPED);
        strategy.setPauseReason(PauseReason.NONE);
        strategy.setResumeStateBeforePause(StrategyLifecycleState.STOPPED);
        strategy.setLastEvent((reason == null || reason.isBlank())
                ? "Archived by portfolio action"
                : reason);
        strategy.clearLastError();
        strategyRepository.save(strategy);
        stateMachine.transition(
                strategy,
                StrategyLifecycleState.STOPPED,
                StrategyEventType.STRATEGY_ARCHIVED,
                strategy.lastEvent(),
                "{}"
        );
        return ArchiveResult.success(strategy.id());
    }

    public StrategyCreationResult repositionExpiredStrategy(String strategyId) {
        if (strategyId == null || strategyId.isBlank()) {
            return StrategyCreationResult.failed("Strategy id is missing");
        }
        Optional<Strategy> maybeStrategy = strategyRepository.findById(strategyId);
        if (maybeStrategy.isEmpty()) {
            return StrategyCreationResult.failed("Strategy not found");
        }
        Strategy strategy = maybeStrategy.get();
        boolean expired = strategy.status() == StrategyStatus.FAILED
                && "expired".equals(BrokerOrderStatusUtil.normalize(strategy.latestOrderStatus()));
        if (!expired) {
            return StrategyCreationResult.failed("Strategy is not in an expired state");
        }
        if (!strategyEngine.canAutoResubmitExpiredEntryOrder(strategy)) {
            return StrategyCreationResult.failed("Expired order is not eligible for automatic resubmission");
        }

        cancelPendingLocalOrders(strategy);
        strategy.setStatus(StrategyStatus.ACTIVE);
        strategy.setCurrentState(StrategyLifecycleState.CREATED);
        strategy.setPauseReason(PauseReason.NONE);
        strategy.setLatestOrderStatus("");
        strategy.setLatestAlpacaOrderId("");
        strategy.clearLastError();
        strategyRepository.save(strategy);
        stateMachine.transition(
                strategy,
                StrategyLifecycleState.CREATED,
                StrategyEventType.STRATEGY_RESUMED,
                "Expired strategy reposition requested",
                "{}"
        );

        StrategyOrder order = strategyEngine.resubmitExpiredEntryOrder(strategy);
        if (order == null || order.alpacaOrderId() == null || order.alpacaOrderId().isBlank()) {
            strategy.setStatus(StrategyStatus.FAILED);
            strategy.setCurrentState(StrategyLifecycleState.FAILED);
            String error = strategy.lastError() == null || strategy.lastError().isBlank()
                    ? "Failed to resubmit expired order"
                    : strategy.lastError();
            strategy.setLastError(error);
            strategyRepository.save(strategy);
            stateMachine.transition(strategy, StrategyLifecycleState.FAILED, StrategyEventType.STRATEGY_FAILED, error, "{}");
            return StrategyCreationResult.failed(error);
        }
        return StrategyCreationResult.success(strategy.id(), order.id(), order.alpacaOrderId(), order.clientOrderId());
    }

    public record StrategyCreationResult(
            boolean success,
            String strategyId,
            String strategyOrderId,
            String alpacaOrderId,
            String clientOrderId,
            String error,
            BigDecimal filledQuantity,
            BigDecimal filledAveragePrice
    ) {
        public static StrategyCreationResult success(String strategyId, String strategyOrderId, String alpacaOrderId, String clientOrderId) {
            return success(strategyId, strategyOrderId, alpacaOrderId, clientOrderId, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        public static StrategyCreationResult success(
                String strategyId,
                String strategyOrderId,
                String alpacaOrderId,
                String clientOrderId,
                BigDecimal filledQuantity,
                BigDecimal filledAveragePrice
        ) {
            return new StrategyCreationResult(
                    true,
                    strategyId,
                    strategyOrderId,
                    alpacaOrderId,
                    clientOrderId,
                    null,
                    filledQuantity == null ? BigDecimal.ZERO : filledQuantity,
                    filledAveragePrice == null ? BigDecimal.ZERO : filledAveragePrice
            );
        }

        public static StrategyCreationResult failed(String error) {
            return new StrategyCreationResult(false, null, null, null, null, error == null ? "Unknown error" : error, BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }

    public record ArchiveResult(boolean success, String strategyId, String error) {
        public static ArchiveResult success(String strategyId) {
            return new ArchiveResult(true, strategyId, null);
        }

        public static ArchiveResult failed(String error) {
            return new ArchiveResult(false, null, error == null ? "Unknown error" : error);
        }
    }

    public record LimitBuyCancelResult(boolean success, int canceledCount, String error) {
        public static LimitBuyCancelResult success(int canceledCount) {
            return new LimitBuyCancelResult(true, canceledCount, null);
        }

        public static LimitBuyCancelResult failed(String error) {
            return new LimitBuyCancelResult(false, 0, error == null ? "Unknown error" : error);
        }
    }

    public record LimitSellCancelResult(boolean success, int canceledCount, String error) {
        public static LimitSellCancelResult success(int canceledCount) {
            return new LimitSellCancelResult(true, canceledCount, null);
        }

        public static LimitSellCancelResult failed(String error) {
            return new LimitSellCancelResult(false, 0, error == null ? "Unknown error" : error);
        }
    }

    public record LivePromotionResult(
            boolean success,
            String paperStrategyId,
            String liveStrategyId,
            String alpacaOrderId,
            String clientOrderId,
            String error
    ) {
        public static LivePromotionResult success(String paperStrategyId, String liveStrategyId, String alpacaOrderId, String clientOrderId) {
            return new LivePromotionResult(true, paperStrategyId, liveStrategyId, alpacaOrderId, clientOrderId, null);
        }

        public static LivePromotionResult failed(String error) {
            return new LivePromotionResult(false, null, null, null, null, error == null ? "Unknown error" : error);
        }
    }

    public record LivePromotionEdits(
            BigDecimal baseBuyPrice,
            Integer baseBuyQty,
            BigDecimal buyLevel1Price,
            Integer buyLevel1Qty,
            BigDecimal buyLevel2Price,
            Integer buyLevel2Qty,
            BigDecimal targetSellPrice,
            Boolean lossBuyLevelsEnabled
    ) {
        /**
         * Convenience constructor that leaves the loss-buy-levels toggle unspecified
         * ({@code null}), so promotion falls back to the paper strategy's setting.
         */
        public LivePromotionEdits(
                BigDecimal baseBuyPrice,
                Integer baseBuyQty,
                BigDecimal buyLevel1Price,
                Integer buyLevel1Qty,
                BigDecimal buyLevel2Price,
                Integer buyLevel2Qty,
                BigDecimal targetSellPrice
        ) {
            this(baseBuyPrice, baseBuyQty, buyLevel1Price, buyLevel1Qty,
                    buyLevel2Price, buyLevel2Qty, targetSellPrice, null);
        }

        /** Convenience constructor for the price-only path used in older tests. */
        public LivePromotionEdits(BigDecimal baseBuyPrice, BigDecimal targetSellPrice) {
            this(baseBuyPrice, null, null, null, null, null, targetSellPrice, null);
        }
    }

    public record LivePromotionPreview(
            Strategy strategy,
            boolean eligible,
            List<String> issues,
            List<String> validationErrors,
            int pendingPaperOrders,
            int liveOpenOrders,
            BigDecimal livePositionQuantity,
            boolean marketSessionOpen
    ) {
        public static LivePromotionPreview missing(String issue) {
            return new LivePromotionPreview(null, false, List.of(issue), List.of(), 0, 0, BigDecimal.ZERO, false);
        }

        public boolean exists() {
            return strategy != null;
        }
    }
}
