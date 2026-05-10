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
import com.neuralarc.service.StrategyApplyService;
import com.neuralarc.service.StrategyRepository;
import com.neuralarc.service.StrategyService;

import javax.swing.JOptionPane;
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
            Strategy existing = findLuckySimulation(selection.stock().symbol()).orElse(null);
            if (existing != null) {
                int choice = gateway.confirmDuplicate(selection.stock().symbol());
                if (choice == JOptionPane.CANCEL_OPTION || choice == JOptionPane.CLOSED_OPTION) {
                    gateway.log("[I Am Feeling Lucky] Simulation placement canceled by user.");
                    canceled = true;
                    break;
                }
                if (choice == JOptionPane.NO_OPTION) {
                    skipped++;
                    skippedReasons.add(selection.stock().symbol() + ": duplicate skipped");
                    continue;
                }
            }
            Strategy strategy = toStrategy(selection, recommendation, existing);
            StrategyService.StrategyCreationResult creationResult = gateway.createPaperStrategy(strategy);
            if (!creationResult.success()) {
                skipped++;
                skippedReasons.add(selection.stock().symbol() + ": " + creationResult.error());
                gateway.log("[I Am Feeling Lucky] Skipped " + selection.stock().symbol() + ": " + creationResult.error());
                continue;
            }
            if (existing == null) created++; else replaced++;
            gateway.log("[I Am Feeling Lucky] Started paper monitoring for " + strategy.symbol()
                    + " at base limit $" + strategy.baseBuyLimitPrice().toPlainString()
                    + ". Alpaca paper order id=" + creationResult.alpacaOrderId());
        }
        gateway.afterPlacement();
        return new PlacementResult(created, replaced, skipped, skippedReasons, canceled);
    }

    private Strategy toStrategy(LuckySimulationSelection selection, StrategyRecommendation recommendation, Strategy existing) {
        StrategyConfig config = luckySimulationConfig(selection, recommendation);
        String id = existing == null ? UUID.randomUUID().toString() : existing.id();
        Strategy strategy = Strategy.fromConfig(id, luckyStrategyName(selection.stock().symbol()), config, StrategyMode.PAPER);
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

    private Optional<Strategy> findLuckySimulation(String symbol) {
        String normalized = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
        return gateway.repository().findAll().stream()
                .filter(strategy -> strategy.mode() == StrategyMode.PAPER)
                .filter(strategy -> strategy.symbol().equalsIgnoreCase(normalized))
                .filter(strategy -> strategy.name().startsWith("I_AM_FEELING_LUCKY:"))
                .findFirst();
    }

    private StrategyConfig luckySimulationConfig(LuckySimulationSelection selection, StrategyRecommendation recommendation) {
        StrategyApplyService.AppliedStrategyValues values = applyService.applyRecommendationToCurrentStrategy(recommendation);
        return new StrategyConfig(
                selection.stock().symbol(),
                values.buyRulePrice(),
                1,
                true,
                values.stopLossPrice(),
                true,
                values.sellRulePrice(),
                values.lossBuy1Price(),
                1,
                values.lossBuy2Price(),
                1,
                false,
                false,
                BigDecimal.ZERO,
                60,
                true,
                false,
                false,
                ProfitHoldType.PERCENT_TRAILING,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                false,
                ProfitControlMode.SELL_TRIGGER,
                ThresholdType.FIXED_AMOUNT,
                BigDecimal.ZERO,
                TrailingType.PERCENTAGE,
                BigDecimal.ZERO,
                false
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

        int confirmDuplicate(String symbol);

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
