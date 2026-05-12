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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public class LuckySimulationPlacementController {
    private final Gateway gateway;
    private final StrategyApplyService applyService;

    public LuckySimulationPlacementController(Gateway gateway) {
        this(gateway, new StrategyApplyService());
    }

    LuckySimulationPlacementController(Gateway gateway, StrategyApplyService applyService) {
        this.gateway = gateway;
        this.applyService = applyService;
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
            if (recommendation.currentPrice() != null
                    && recommendation.currentPrice().compareTo(BigDecimal.ZERO) > 0
                    && recommendation.baseBuyPrice().compareTo(recommendation.currentPrice()) > 0) {
                skipped++;
                skippedReasons.add(selection.stock().symbol() + ": recommended base buy price is above current price");
                gateway.log("[I Am Feeling Lucky] Skipped " + selection.stock().symbol()
                        + ": recommended base buy price is above current price.");
                continue;
            }
            Strategy existing = findExistingPaperStrategy(selection.stock().symbol()).orElse(null);
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
                    StrategyMode.PAPER,
                    gateway.repository().findAll(),
                    gateway.allowDuplicateSymbols()
            )) {
                skipped++;
                skippedReasons.add(selection.stock().symbol() + ": duplicate symbol policy blocked a new paper strategy");
                gateway.log("[I Am Feeling Lucky] Skipped " + selection.stock().symbol()
                        + ": duplicate symbol policy blocked a new paper strategy.");
                continue;
            }
            Strategy strategy = toStrategy(selection, recommendation);
            StrategyService.StrategyCreationResult creationResult = gateway.createPaperStrategy(strategy);
            if (!creationResult.success()) {
                skipped++;
                skippedReasons.add(selection.stock().symbol() + ": " + creationResult.error());
                gateway.log("[I Am Feeling Lucky] Skipped " + selection.stock().symbol() + ": " + creationResult.error());
                continue;
            }
            if (existingForReplace == null) created++; else replaced++;
            gateway.log("[I Am Feeling Lucky] Started paper monitoring for " + strategy.symbol()
                    + " at base limit $" + strategy.baseBuyLimitPrice().toPlainString()
                    + " qty=" + strategy.baseBuyQuantity()
                    + ". Alpaca paper order id=" + creationResult.alpacaOrderId());
        }
        gateway.afterPlacement();
        return new PlacementResult(created, replaced, skipped, skippedReasons, canceled);
    }

    private Strategy toStrategy(LuckySimulationSelection selection, StrategyRecommendation recommendation) {
        StrategyConfig config = luckySimulationConfig(selection, recommendation);
        Strategy strategy = Strategy.fromConfig(UUID.randomUUID().toString(), luckyStrategyName(selection.stock().symbol()), config, StrategyMode.PAPER);
        strategy.setStatus(StrategyStatus.CREATED);
        strategy.setCurrentState(StrategyLifecycleState.CREATED);
        strategy.setLastEvent("Alpaca Paper mode from I Am Feeling Lucky. Selected "
                + selection.selectedRecommendationType().name()
                + ". Base limit buy $" + recommendation.baseBuyPrice().toPlainString()
                + ".");
        strategy.setLatestOrderStatus("PAPER_PENDING");
        strategy.setLatestAlpacaOrderId("");
        return strategy;
    }

    private Optional<Strategy> findExistingPaperStrategy(String symbol) {
        String normalized = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
        return gateway.repository().findAll().stream()
                .filter(strategy -> strategy.mode() == StrategyMode.PAPER)
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
                true,
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

    private String luckyStrategyName(String symbol) {
        return "I_AM_FEELING_LUCKY: " + symbol + " Paper";
    }

    public String summaryMessage(PlacementResult result) {
        String message = "Started " + result.created() + " Alpaca Paper strateg"
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
}
