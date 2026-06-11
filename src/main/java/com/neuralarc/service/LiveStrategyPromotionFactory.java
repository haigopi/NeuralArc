package com.neuralarc.service;

import com.neuralarc.model.PauseReason;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyStatus;
import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

final class LiveStrategyPromotionFactory {
    Strategy cloneFromPaper(Strategy paperStrategy) {
        return cloneFromPaper(paperStrategy, null);
    }

    Strategy cloneFromPaper(Strategy paperStrategy, StrategyService.LivePromotionEdits edits) {
        BigDecimal baseBuyPrice = edits != null && edits.baseBuyPrice() != null
                ? edits.baseBuyPrice()
                : paperStrategy.baseBuyLimitPrice();
        int baseBuyQty = edits != null && edits.baseBuyQty() != null && edits.baseBuyQty() > 0
                ? edits.baseBuyQty()
                : paperStrategy.baseBuyQuantity();
        BigDecimal level1Price = edits != null && edits.buyLevel1Price() != null
                ? edits.buyLevel1Price()
                : paperStrategy.buyLimit1Price();
        int level1Qty = edits != null && edits.buyLevel1Qty() != null && edits.buyLevel1Qty() > 0
                ? edits.buyLevel1Qty()
                : paperStrategy.buyLimit1Quantity();
        BigDecimal level2Price = edits != null && edits.buyLevel2Price() != null
                ? edits.buyLevel2Price()
                : paperStrategy.buyLimit2Price();
        int level2Qty = edits != null && edits.buyLevel2Qty() != null && edits.buyLevel2Qty() > 0
                ? edits.buyLevel2Qty()
                : paperStrategy.buyLimit2Quantity();
        BigDecimal targetSellPrice = edits != null && edits.targetSellPrice() != null
                ? edits.targetSellPrice()
                : paperStrategy.targetSellPrice();

        Strategy liveStrategy = new Strategy(
                UUID.randomUUID().toString(),
                liveStrategyName(paperStrategy),
                paperStrategy.symbol(),
                StrategyMode.LIVE,
                StrategyStatus.CREATED,
                StrategyLifecycleState.CREATED,
                baseBuyPrice,
                baseBuyQty,
                level1Price,
                level1Qty,
                level2Price,
                level2Qty,
                paperStrategy.automatedStopLossEnabled(),
                paperStrategy.stopLossType(),
                paperStrategy.stopLossPrice(),
                paperStrategy.stopLossPercent(),
                paperStrategy.optionalLossExitEnabled(),
                paperStrategy.optionalLossExitPrice(),
                paperStrategy.targetSellEnabled(),
                targetSellPrice,
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
        liveStrategy.setBaseBuyRepostReductionPercent(paperStrategy.baseBuyRepostReductionPercent());
        liveStrategy.setTimeInForce(paperStrategy.timeInForce());
        liveStrategy.setPauseReason(PauseReason.NONE);
        liveStrategy.setResumeStateBeforePause(StrategyLifecycleState.CREATED);

        // Recalculate derived capacity fields now that prices/quantities may have been overridden.
        boolean lossBuysEnabled = paperStrategy.lossBuyLevelsEnabled();
        int maxQty = baseBuyQty + (lossBuysEnabled ? level1Qty + level2Qty : 0);
        BigDecimal maxCapital = Monetary.round(baseBuyPrice.multiply(BigDecimal.valueOf(baseBuyQty))
                .add(lossBuysEnabled
                        ? level1Price.multiply(BigDecimal.valueOf(level1Qty))
                                .add(level2Price.multiply(BigDecimal.valueOf(level2Qty)))
                        : BigDecimal.ZERO));
        liveStrategy.setMaxTotalQuantity(maxQty);
        liveStrategy.setMaxCapitalAllowed(maxCapital);

        return liveStrategy;
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
}
