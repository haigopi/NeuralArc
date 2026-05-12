package com.neuralarc.service;

import com.neuralarc.api.AlpacaClient;
import com.neuralarc.api.AlpacaPositionData;
import com.neuralarc.model.*;
import com.neuralarc.util.BrokerOrderStatusUtil;
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

    private final StrategyRepository strategyRepository;
    private final StrategyOrderRepository orderRepository;
    private final StrategyExecutionEventRepository eventRepository;
    private final AlpacaClient alpacaClient;
    private final StrategyValidator validator;
    private final boolean liveTradingEnabled;
    private final StrategyMode defaultStrategyMode;
    private final StrategyStateMachine stateMachine;
    private final StrategyEngine strategyEngine;
    private final AppSettingsService appSettingsService;
    private final MarketHoursService marketHoursService;

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
        int canceledCount = cancelPendingLimitBuyOrders(strategy);
        if (canceledCount <= 0) {
            return LimitBuyCancelResult.failed("No pending limit buy orders found");
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
        if (pendingPaperOrders > 0) {
            issues.add("Paper strategy still has " + pendingPaperOrders + " pending local order(s).");
        }

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
        LivePromotionPreview preview = previewLivePromotion(strategyId);
        if (!preview.exists()) {
            return LivePromotionResult.failed(preview.issues().isEmpty() ? "Strategy not found." : preview.issues().getFirst());
        }
        if (!preview.eligible()) {
            return LivePromotionResult.failed(String.join(" ", preview.issues()));
        }
        Strategy paperStrategy = preview.strategy();

        Strategy liveStrategy = cloneStrategyForLivePromotion(paperStrategy);
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

    public StrategyCreationResult closePosition(String strategyId) {
        Optional<Strategy> maybeStrategy = strategyRepository.findById(strategyId);
        if (maybeStrategy.isEmpty()) {
            return StrategyCreationResult.failed("Strategy not found");
        }
        Strategy strategy = maybeStrategy.get();
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
        String clientOrderId = buildClientOrderId(strategy.id(), StrategyStage.MANUAL_EXIT);
        com.neuralarc.api.AlpacaOrderData submitted = alpacaClient.submitLimitSellOrder(strategy.symbol(), quantity, latestPrice, clientOrderId);
        Instant submittedAt = submitted.submittedAt() == null ? Instant.now() : submitted.submittedAt();
        StrategyOrder order = new StrategyOrder(
                java.util.UUID.randomUUID().toString(),
                strategy.id(),
                StrategyStage.MANUAL_EXIT,
                submitted.orderId(),
                clientOrderId,
                strategy.symbol(),
                StrategyOrderSide.SELL,
                StrategyOrderType.LIMIT,
                latestPrice,
                BigDecimal.ZERO,
                BigDecimal.valueOf(quantity),
                submitted.filledQuantity(),
                submitted.filledAveragePrice(),
                mapOrderStatus(submitted.status()),
                submittedAt,
                Instant.now(),
                null,
                submitted.rawJson()
        );
        orderRepository.save(order);
        strategy.setLatestOrderStatus(BrokerOrderStatusUtil.normalize(submitted.status()));
        strategy.setLatestAlpacaOrderId(order.alpacaOrderId());
        strategy.setLastTriggeredRuleType("MANUAL_EXIT");
        strategyRepository.save(strategy);
        stateMachine.transition(strategy, StrategyLifecycleState.SELL_PLACED, StrategyEventType.ORDER_SUBMITTED, "Manual sell order submitted", submitted.rawJson());
        return StrategyCreationResult.success(strategy.id(), order.id(), order.alpacaOrderId(), order.clientOrderId());
    }

    public static String buildClientOrderId(String strategyId, StrategyStage stage) {
        return "neuralarc-" + strategyId + "-" + stage.name() + "-" + System.currentTimeMillis();
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
            case "failed_transport" -> StrategyOrderStatus.FAILED;
            case "failed" -> StrategyOrderStatus.REJECTED;
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

    private int cancelPendingLimitBuyOrders(Strategy strategy) {
        int canceledCount = 0;
        List<com.neuralarc.api.AlpacaOrderData> openOrders = alpacaClient.getOpenOrders(strategy.symbol());
        for (com.neuralarc.api.AlpacaOrderData remoteOrder : openOrders) {
            if (!isPendingLimitBuy(remoteOrder)) {
                continue;
            }
            if (alpacaClient.cancelOrder(remoteOrder.orderId())) {
                canceledCount++;
                markMatchingLocalLimitBuyCanceled(strategy, remoteOrder);
            }
        }

        for (StrategyOrder localOrder : orderRepository.findByStrategyId(strategy.id())) {
            if (!isPendingLimitBuy(localOrder)) {
                continue;
            }
            localOrder.setStatus(StrategyOrderStatus.CANCELED);
            orderRepository.save(localOrder);
            canceledCount++;
        }
        return canceledCount;
    }

    private void markMatchingLocalLimitBuyCanceled(Strategy strategy, com.neuralarc.api.AlpacaOrderData remoteOrder) {
        for (StrategyOrder localOrder : orderRepository.findByStrategyId(strategy.id())) {
            if (!isPendingLimitBuy(localOrder)) {
                continue;
            }
            boolean sameOrder = remoteOrder.orderId().equals(localOrder.alpacaOrderId())
                    || remoteOrder.clientOrderId().equals(localOrder.clientOrderId());
            if (!sameOrder) {
                continue;
            }
            localOrder.setStatus(StrategyOrderStatus.CANCELED);
            localOrder.setRawResponseJson(remoteOrder.rawJson());
            orderRepository.save(localOrder);
        }
    }

    private boolean isPendingLimitBuy(com.neuralarc.api.AlpacaOrderData order) {
        if (order == null || !"buy".equalsIgnoreCase(order.side()) || !"limit".equalsIgnoreCase(order.type())) {
            return false;
        }
        String normalized = BrokerOrderStatusUtil.normalize(order.status());
        return !"filled".equals(normalized)
                && !"canceled".equals(normalized)
                && !"cancelled".equals(normalized)
                && !"expired".equals(normalized)
                && !"rejected".equals(normalized)
                && !"failed".equals(normalized);
    }

    private boolean isPendingLimitBuy(StrategyOrder order) {
        return order != null
                && order.side() == StrategyOrderSide.BUY
                && order.orderType() == StrategyOrderType.LIMIT
                && order.isPending();
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

    private boolean isProfitableExitStage(StrategyStage stage) {
        return stage == StrategyStage.TARGET_SELL
                || stage == StrategyStage.PROFIT_EXIT
                || stage == StrategyStage.MANUAL_EXIT;
    }

    private Strategy cloneStrategyForLivePromotion(Strategy paperStrategy) {
        Strategy liveStrategy = new Strategy(
                UUID.randomUUID().toString(),
                liveStrategyName(paperStrategy),
                paperStrategy.symbol(),
                StrategyMode.LIVE,
                StrategyStatus.CREATED,
                StrategyLifecycleState.CREATED,
                paperStrategy.baseBuyLimitPrice(),
                paperStrategy.baseBuyQuantity(),
                paperStrategy.buyLimit1Price(),
                paperStrategy.buyLimit1Quantity(),
                paperStrategy.buyLimit2Price(),
                paperStrategy.buyLimit2Quantity(),
                paperStrategy.automatedStopLossEnabled(),
                paperStrategy.stopLossType(),
                paperStrategy.stopLossPrice(),
                paperStrategy.stopLossPercent(),
                paperStrategy.optionalLossExitEnabled(),
                paperStrategy.optionalLossExitPrice(),
                paperStrategy.targetSellEnabled(),
                paperStrategy.targetSellPrice(),
                paperStrategy.targetSellQuantityOrPercent(),
                paperStrategy.targetSellPercentBased(),
                paperStrategy.profitHoldEnabled(),
                paperStrategy.profitHoldType(),
                paperStrategy.profitHoldPercent(),
                paperStrategy.profitHoldAmount(),
                BigDecimal.ZERO,
                paperStrategy.restartAfterExitEnabled(),
                paperStrategy.maxTotalQuantity(),
                paperStrategy.maxCapitalAllowed(),
                paperStrategy.pollingIntervalSeconds(),
                Instant.now(),
                Instant.now()
        );
        liveStrategy.setLossBuyLevelsEnabled(paperStrategy.lossBuyLevelsEnabled());
        liveStrategy.setAlpacaTrailingStopEnabled(paperStrategy.alpacaTrailingStopEnabled());
        liveStrategy.setProfitControlMode(paperStrategy.profitControlMode());
        liveStrategy.setAutomaticStopSellThresholdType(paperStrategy.automaticStopSellThresholdType());
        liveStrategy.setAutomaticStopSellThreshold(paperStrategy.automaticStopSellThreshold());
        liveStrategy.setAutomaticStopSellTrailingType(paperStrategy.automaticStopSellTrailingType());
        liveStrategy.setAutomaticStopSellTrailingValue(paperStrategy.automaticStopSellTrailingValue());
        liveStrategy.setResubmitOnExpiryEnabled(paperStrategy.resubmitOnExpiryEnabled());
        liveStrategy.setPauseReason(PauseReason.NONE);
        liveStrategy.setResumeStateBeforePause(StrategyLifecycleState.CREATED);
        return liveStrategy;
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

    private String liveStrategyName(Strategy paperStrategy) {
        String currentName = paperStrategy.name() == null ? "" : paperStrategy.name().trim();
        if (currentName.isBlank()) {
            return paperStrategy.symbol() + " Live Strategy";
        }
        if (currentName.toLowerCase().contains("live")) {
            return currentName;
        }
        return currentName + " Live";
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
                remoteOrder.clientOrderId().isBlank() ? buildClientOrderId(strategy.id(), mapRemoteStage(remoteOrder)) : remoteOrder.clientOrderId(),
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
        if (!strategyEngine.canAutoRetryFailed(strategy)) {
            return StrategyCreationResult.failed("Open orders or positions still exist for this symbol");
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

    public record StrategyCreationResult(boolean success, String strategyId, String strategyOrderId, String alpacaOrderId, String clientOrderId, String error) {
        public static StrategyCreationResult success(String strategyId, String strategyOrderId, String alpacaOrderId, String clientOrderId) {
            return new StrategyCreationResult(true, strategyId, strategyOrderId, alpacaOrderId, clientOrderId, null);
        }

        public static StrategyCreationResult failed(String error) {
            return new StrategyCreationResult(false, null, null, null, null, error == null ? "Unknown error" : error);
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
