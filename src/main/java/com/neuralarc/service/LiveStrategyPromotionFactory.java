package com.neuralarc.service;

import com.neuralarc.model.PauseReason;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

final class LiveStrategyPromotionFactory {
    Strategy cloneFromPaper(Strategy paperStrategy) {
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

