package com.neuralarc.ui;

import com.neuralarc.model.LuckySimulationSelection;
import com.neuralarc.model.ProfitControlMode;
import com.neuralarc.model.ProfitHoldType;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyConfig;
import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyRecommendation;
import com.neuralarc.model.StrategyStatus;
import com.neuralarc.model.ThresholdType;
import com.neuralarc.model.TrailingType;
import com.neuralarc.service.AppSettingsService;
import com.neuralarc.service.StrategyApplyService;
import com.neuralarc.service.StrategyRepository;
import com.neuralarc.service.StrategyService;
import com.neuralarc.util.BrokerOrderStatusUtil;
import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public class LuckySimulationPlacementController {
    private final Gateway gateway;
    private final StrategyApplyService applyService;
    private final StrategyMode targetMode;

    public LuckySimulationPlacementController(Gateway gateway) {
        this(gateway, new StrategyApplyService(), StrategyMode.PAPER);
    }

    public LuckySimulationPlacementController(Gateway gateway, StrategyMode targetMode) {
        this(gateway, new StrategyApplyService(), targetMode);
    }

    LuckySimulationPlacementController(Gateway gateway, StrategyApplyService applyService) {
        this(gateway, applyService, StrategyMode.PAPER);
    }

    LuckySimulationPlacementController(Gateway gateway, StrategyApplyService applyService, StrategyMode targetMode) {
        this.gateway = gateway;
        this.applyService = applyService;
        this.targetMode = targetMode == null ? StrategyMode.PAPER : targetMode;
    }

    public PlacementResult place(List<LuckySimulationSelection> selections) {
        if (selections == null || selections.isEmpty()) {
            gateway.log("[I Am Feeling Lucky] No reviewed stocks selected for simulation.");
            return new PlacementResult(0, 0, 0, List.of(), false);
        }
        int created = 0;
        int skipped = 0;
        int replaced = 0;
        boolean canceled = false;
        List<String> skippedReasons = new ArrayList<>();
        for (LuckySimulationSelection selection : selections) {
            StrategyRecommendation recommendation = recommendationFor(selection);
            if (recommendation == null || !recommendation.isApplicable()
                    || recommendation.baseBuyPrice().compareTo(BigDecimal.ZERO) <= 0) {
                skipped++;
                skippedReasons.add(selection.stock().symbol() + ": missing valid base limit buy price");
                gateway.log("[I Am Feeling Lucky] Skipped " + selection.stock().symbol() + ": missing valid base limit buy price.");
                continue;
            }
            Strategy existing = findExistingStrategy(selection.stock().symbol()).orElse(null);
            Strategy existingForReplace = null;
            if (existing != null && isWaitingForFill(existing)) {
                if (!gateway.confirmReplaceWaitingPaperStrategy(selection.stock().symbol())) {
                    gateway.log("[I Am Feeling Lucky] Placement stopped by user for " + selection.stock().symbol() + ".");
                    canceled = true;
                    break;
                }
                gateway.cancelAndDeletePaperStrategy(existing.id());
                existingForReplace = existing;
            } else if (existing != null && DuplicateSymbolPolicy.wouldBeDuplicate(
                    selection.stock().symbol(),
                    targetMode,
                    gateway.repository().findAll(),
                    gateway.allowDuplicateSymbols()
            )) {
                skipped++;
                skippedReasons.add(selection.stock().symbol() + ": duplicate symbol policy blocked a new paper strategy");
                gateway.log("[I Am Feeling Lucky] Skipped " + selection.stock().symbol()
                        + ": duplicate symbol policy blocked a new paper strategy.");
                continue;
            }
            BigDecimal effectiveBaseBuyPrice = effectiveBaseBuyPrice(selection, recommendation);
            Strategy strategy = toStrategy(selection, recommendation, effectiveBaseBuyPrice);
            StrategyService.StrategyCreationResult creationResult = gateway.createStrategy(strategy, targetMode);
            if (!creationResult.success()) {
                skipped++;
                skippedReasons.add(selection.stock().symbol() + ": " + creationResult.error());
                gateway.log("[I Am Feeling Lucky] Skipped " + selection.stock().symbol() + ": " + creationResult.error());
                continue;
            }
            if (existingForReplace == null) created++; else replaced++;
            gateway.log("[I Am Feeling Lucky] Started " + targetMode.name() + " monitoring for " + strategy.symbol()
                    + " at base limit $" + strategy.baseBuyLimitPrice().toPlainString()
                    + " qty=" + strategy.baseBuyQuantity()
                    + ". Alpaca paper order id=" + creationResult.alpacaOrderId());
        }
        gateway.afterPlacement();
        return new PlacementResult(created, replaced, skipped, skippedReasons, canceled);
    }

    private Strategy toStrategy(
            LuckySimulationSelection selection,
            StrategyRecommendation recommendation,
            BigDecimal effectiveBaseBuyPrice
    ) {
        StrategyConfig config = luckySimulationConfig(selection, recommendation);
        Strategy strategy = Strategy.fromConfig(UUID.randomUUID().toString(), luckyStrategyName(selection), config, targetMode);
        strategy.setBaseBuyLimitPrice(effectiveBaseBuyPrice);
        strategy.setStatus(StrategyStatus.CREATED);
        strategy.setCurrentState(StrategyLifecycleState.CREATED);
        String stockReason = selection.stock().reason() == null || selection.stock().reason().isBlank()
                ? "lucky-simulation"
                : selection.stock().reason();
        strategy.setLastEvent("Alpaca " + modeLabel() + " mode from I Am Feeling Lucky. Selected "
                + selection.selectedRecommendationType().name()
                + ". Source " + stockReason
                + ". Base limit buy $" + strategy.baseBuyLimitPrice().toPlainString()
                + ".");
        strategy.setLatestOrderStatus("PAPER_PENDING");
        strategy.setLatestAlpacaOrderId("");
        return strategy;
    }

    private BigDecimal effectiveBaseBuyPrice(LuckySimulationSelection selection, StrategyRecommendation recommendation) {
        BigDecimal recommendedBase = recommendation.baseBuyPrice();
        BigDecimal current = resolveCurrentPrice(selection, recommendation);
        BigDecimal adjusted = adjustedLuckyPaperBaseBuyPrice(recommendedBase, current);
        if (adjusted.compareTo(recommendedBase) != 0) {
            gateway.log("[I Am Feeling Lucky] Adjusted base limit buy for " + selection.stock().symbol()
                    + " from $" + recommendedBase.toPlainString()
                    + " to $" + adjusted.toPlainString()
                    + " (1% below current $" + current.toPlainString() + ").");
        }
        return adjusted;
    }

    static BigDecimal adjustedLuckyPaperBaseBuyPrice(BigDecimal proposedBasePrice, BigDecimal currentPrice) {
        if (proposedBasePrice == null) {
            return BigDecimal.ZERO;
        }
        if (currentPrice != null
                && currentPrice.compareTo(BigDecimal.ZERO) > 0
                && proposedBasePrice.compareTo(currentPrice) >= 0) {
            return Monetary.round(currentPrice.multiply(new BigDecimal("0.99")));
        }
        return proposedBasePrice;
    }

    private BigDecimal resolveCurrentPrice(LuckySimulationSelection selection, StrategyRecommendation recommendation) {
        BigDecimal latestPrice = selection == null || selection.stock() == null
                ? null
                : selection.stock().latestPrice();
        if (latestPrice != null && latestPrice.compareTo(BigDecimal.ZERO) > 0) {
            return latestPrice;
        }
        return recommendation == null ? null : recommendation.currentPrice();
    }

    private Optional<Strategy> findExistingStrategy(String symbol) {
        String normalized = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
        return gateway.repository().findAll().stream()
                .filter(strategy -> strategy.mode() == targetMode)
                .filter(strategy -> strategy.symbol().equalsIgnoreCase(normalized))
                .findFirst();
    }

    private boolean isWaitingForFill(Strategy strategy) {
        if (strategy == null) {
            return false;
        }
        String latestOrderStatus = strategy.latestOrderStatus();
        if (latestOrderStatus != null && BrokerOrderStatusUtil.isWaitingForFill(latestOrderStatus)) {
            return true;
        }
        StrategyLifecycleState state = strategy.currentState();
        return state == StrategyLifecycleState.BASE_BUY_PLACED
                || state == StrategyLifecycleState.BASE_BUY_PARTIALLY_FILLED
                || state == StrategyLifecycleState.BUY_LIMIT_1_PLACED
                || state == StrategyLifecycleState.BUY_LIMIT_1_PARTIALLY_FILLED
                || state == StrategyLifecycleState.BUY_LIMIT_2_PLACED
                || state == StrategyLifecycleState.BUY_LIMIT_2_PARTIALLY_FILLED
                || state == StrategyLifecycleState.SELL_PLACED
                || state == StrategyLifecycleState.SELL_PARTIALLY_FILLED;
    }

    private StrategyConfig luckySimulationConfig(LuckySimulationSelection selection, StrategyRecommendation recommendation) {
        StrategyApplyService.AppliedStrategyValues values = applyService.applyRecommendationToCurrentStrategy(recommendation);
        int defaultPollingSeconds = Math.max(1, gateway.defaultStrategyPollingSeconds());
        return new StrategyConfig(
                selection.stock().symbol(),
                values.buyRulePrice(),
                Math.max(1, selection.buyQuantity()),
                targetMode == StrategyMode.PAPER,
                values.stopLossPrice(),
                true,
                values.sellRulePrice(),
                values.lossBuy1Price(),
                Math.max(1, selection.buyQuantity()),
                values.lossBuy2Price(),
                Math.max(1, selection.buyQuantity()),
                false,
                false,
                BigDecimal.ZERO,
                defaultPollingSeconds,
                true,
                false,
                false,
                ProfitHoldType.PERCENT_TRAILING,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                gateway.defaultRepeatCycleAfterProfitExitEnabled(),
                ProfitControlMode.SELL_TRIGGER,
                ThresholdType.FIXED_AMOUNT,
                BigDecimal.ZERO,
                TrailingType.PERCENTAGE,
                BigDecimal.ZERO,
                gateway.defaultResubmitOnExpiryEnabled()
        );
    }

    private StrategyRecommendation recommendationFor(LuckySimulationSelection selection) {
        if (selection == null || selection.analysis() == null) {
            return null;
        }
        return switch (selection.selectedRecommendationType()) {
            case HIGH_RISK_SHORT_TERM -> selection.analysis().highRiskShortTermRecommendation();
            case LONG_TERM -> selection.analysis().longTermRecommendation();
            default -> selection.analysis().shortTermRecommendation();
        };
    }

    private String luckyStrategyName(LuckySimulationSelection selection) {
        return "I_AM_FEELING_LUCKY_" + luckySourceToken(selection) + ": " + selection.stock().symbol() + " " + modeLabel();
    }

    private String luckySourceToken(LuckySimulationSelection selection) {
        String reason = selection == null || selection.stock() == null || selection.stock().reason() == null
                ? ""
                : selection.stock().reason().toLowerCase(Locale.ROOT);
        if (reason.contains("gainer")) {
            return "GAINERS";
        }
        if (reason.contains("loser")) {
            return "LOSERS";
        }
        return "REVIEWED";
    }

    public String summaryMessage(PlacementResult result) {
        String message = "Started " + result.created() + " Alpaca " + modeLabel() + " strateg"
                + (result.created() == 1 ? "y" : "ies")
                + (result.replaced() > 0 ? ", replaced " + result.replaced() : "")
                + (result.skipped() > 0 ? ", skipped " + result.skipped() : "")
                + ".";
        if (!result.skippedReasons().isEmpty()) {
            message += "\n\nSkipped:\n" + String.join("\n", result.skippedReasons());
        }
        return message;
    }

    public interface Gateway {
        StrategyRepository repository();

        default StrategyService.StrategyCreationResult createStrategy(Strategy strategy, StrategyMode mode) {
            return createPaperStrategy(strategy);
        }

        StrategyService.StrategyCreationResult createPaperStrategy(Strategy strategy);

        boolean confirmReplaceWaitingPaperStrategy(String symbol);

        boolean allowDuplicateSymbols();

        default int defaultStrategyPollingSeconds() {
            return AppSettingsService.DEFAULT_STRATEGY_POLLING_SECONDS;
        }

        default boolean defaultRepeatCycleAfterProfitExitEnabled() {
            return AppSettingsService.DEFAULT_REPEAT_CYCLE_AFTER_PROFIT_EXIT_ENABLED;
        }

        default boolean defaultResubmitOnExpiryEnabled() {
            return AppSettingsService.DEFAULT_RESUBMIT_ON_EXPIRY_ENABLED;
        }

        void cancelAndDeletePaperStrategy(String strategyId);

        void afterPlacement();

        void log(String message);
    }

    public record PlacementResult(
            int created,
            int replaced,
            int skipped,
            List<String> skippedReasons,
            boolean canceled
    ) {}

    private String modeLabel() {
        return targetMode == StrategyMode.LIVE ? "Live" : "Paper";
    }
}
