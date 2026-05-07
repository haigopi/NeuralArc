package com.neuralarc.model;

import com.neuralarc.util.Monetary;

import java.math.BigDecimal;

public record StrategyConfig(
        String symbol,
        BigDecimal baseBuyPrice,
        int baseBuyQty,
        boolean stopLossEnabled,
        BigDecimal stopLoss,
        boolean sellTriggerEnabled,
        BigDecimal sellTriggerPrice,
        BigDecimal lossBuyLevel1Price,
        int lossBuyLevel1Qty,
        BigDecimal lossBuyLevel2Price,
        int lossBuyLevel2Qty,
        boolean lossBuyLevelsEnabled,
        boolean optionalLossExitEnabled,
        BigDecimal optionalLossExitPrice,
        int pollingSeconds,
        boolean paperTrading,
        boolean alpacaTrailingStopEnabled,
        boolean profitHoldEnabled,
        ProfitHoldType profitHoldType,
        BigDecimal profitHoldPercent,
        BigDecimal profitHoldAmount,
        boolean repeatCycleAfterProfitExitEnabled,
        ProfitControlMode profitControlMode,
        ThresholdType automaticStopSellThresholdType,
        BigDecimal automaticStopSellThreshold,
        TrailingType automaticStopSellTrailingType,
        BigDecimal automaticStopSellTrailingValue,
        boolean resubmitOnExpiryEnabled
) {
    public StrategyConfig {
        baseBuyPrice = Monetary.round(baseBuyPrice);
        stopLoss = Monetary.round(stopLoss);
        sellTriggerPrice = Monetary.round(sellTriggerPrice);
        lossBuyLevel1Price = Monetary.round(lossBuyLevel1Price);
        lossBuyLevel2Price = Monetary.round(lossBuyLevel2Price);
        optionalLossExitPrice = Monetary.round(optionalLossExitPrice);
        profitHoldType = profitHoldType == null ? ProfitHoldType.PERCENT_TRAILING : profitHoldType;
        profitHoldPercent = Monetary.round(profitHoldPercent);
        profitHoldAmount = Monetary.round(profitHoldAmount);
        profitControlMode = normalizeProfitControlMode(
                profitControlMode,
                sellTriggerEnabled,
                alpacaTrailingStopEnabled,
                profitHoldEnabled
        );
        automaticStopSellThresholdType = automaticStopSellThresholdType == null ? ThresholdType.FIXED_AMOUNT : automaticStopSellThresholdType;
        automaticStopSellThreshold = Monetary.round(automaticStopSellThreshold);
        automaticStopSellTrailingType = automaticStopSellTrailingType == null ? TrailingType.PERCENTAGE : automaticStopSellTrailingType;
        automaticStopSellTrailingValue = Monetary.round(automaticStopSellTrailingValue);
    }

    public StrategyConfig(
            String symbol,
            BigDecimal baseBuyPrice,
            int baseBuyQty,
            boolean stopLossEnabled,
            BigDecimal stopLoss,
            boolean sellTriggerEnabled,
            BigDecimal sellTriggerPrice,
            BigDecimal lossBuyLevel1Price,
            int lossBuyLevel1Qty,
            BigDecimal lossBuyLevel2Price,
            int lossBuyLevel2Qty,
            boolean lossBuyLevelsEnabled,
            boolean optionalLossExitEnabled,
            BigDecimal optionalLossExitPrice,
            int pollingSeconds,
            boolean paperTrading,
            boolean alpacaTrailingStopEnabled,
            boolean profitHoldEnabled,
            ProfitHoldType profitHoldType,
            BigDecimal profitHoldPercent,
            BigDecimal profitHoldAmount,
            boolean repeatCycleAfterProfitExitEnabled,
            ProfitControlMode profitControlMode,
            ThresholdType automaticStopSellThresholdType,
            BigDecimal automaticStopSellThreshold,
            TrailingType automaticStopSellTrailingType,
            BigDecimal automaticStopSellTrailingValue
    ) {
        this(
                symbol,
                baseBuyPrice,
                baseBuyQty,
                stopLossEnabled,
                stopLoss,
                sellTriggerEnabled,
                sellTriggerPrice,
                lossBuyLevel1Price,
                lossBuyLevel1Qty,
                lossBuyLevel2Price,
                lossBuyLevel2Qty,
                lossBuyLevelsEnabled,
                optionalLossExitEnabled,
                optionalLossExitPrice,
                pollingSeconds,
                paperTrading,
                alpacaTrailingStopEnabled,
                profitHoldEnabled,
                profitHoldType,
                profitHoldPercent,
                profitHoldAmount,
                repeatCycleAfterProfitExitEnabled,
                profitControlMode,
                automaticStopSellThresholdType,
                automaticStopSellThreshold,
                automaticStopSellTrailingType,
                automaticStopSellTrailingValue,
                false
        );
    }

    public StrategyConfig(
            String symbol,
            BigDecimal baseBuyPrice,
            int baseBuyQty,
            boolean stopLossEnabled,
            BigDecimal stopLoss,
            BigDecimal sellTriggerPrice,
            BigDecimal lossBuyLevel1Price,
            int lossBuyLevel1Qty,
            BigDecimal lossBuyLevel2Price,
            int lossBuyLevel2Qty,
            boolean optionalLossExitEnabled,
            BigDecimal optionalLossExitPrice,
            int pollingSeconds,
            boolean paperTrading,
            boolean profitHoldEnabled,
            ProfitHoldType profitHoldType,
            BigDecimal profitHoldPercent,
            BigDecimal profitHoldAmount
    ) {
        this(
                symbol,
                baseBuyPrice,
                baseBuyQty,
                stopLossEnabled,
                stopLoss,
                true,
                sellTriggerPrice,
                lossBuyLevel1Price,
                lossBuyLevel1Qty,
                lossBuyLevel2Price,
                lossBuyLevel2Qty,
                true,
                optionalLossExitEnabled,
                optionalLossExitPrice,
                pollingSeconds,
                paperTrading,
                false,
                profitHoldEnabled,
                profitHoldType,
                profitHoldPercent,
                profitHoldAmount,
                false,
                ProfitControlMode.NONE,
                ThresholdType.FIXED_AMOUNT,
                BigDecimal.ZERO,
                TrailingType.PERCENTAGE,
                BigDecimal.ZERO,
                false
        );
    }

    public StrategyConfig(
            String symbol,
            BigDecimal baseBuyPrice,
            int baseBuyQty,
            BigDecimal stopLoss,
            BigDecimal sellTriggerPrice,
            BigDecimal lossBuyLevel1Price,
            int lossBuyLevel1Qty,
            BigDecimal lossBuyLevel2Price,
            int lossBuyLevel2Qty,
            boolean optionalLossExitEnabled,
            BigDecimal optionalLossExitPrice,
            int pollingSeconds,
            boolean paperTrading,
            boolean profitHoldEnabled,
            ProfitHoldType profitHoldType,
            BigDecimal profitHoldPercent,
            BigDecimal profitHoldAmount
    ) {
        this(
                symbol,
                baseBuyPrice,
                baseBuyQty,
                true,
                stopLoss,
                true,
                sellTriggerPrice,
                lossBuyLevel1Price,
                lossBuyLevel1Qty,
                lossBuyLevel2Price,
                lossBuyLevel2Qty,
                true,
                optionalLossExitEnabled,
                optionalLossExitPrice,
                pollingSeconds,
                paperTrading,
                false,
                profitHoldEnabled,
                profitHoldType,
                profitHoldPercent,
                profitHoldAmount,
                false,
                ProfitControlMode.NONE,
                ThresholdType.FIXED_AMOUNT,
                BigDecimal.ZERO,
                TrailingType.PERCENTAGE,
                BigDecimal.ZERO,
                false
        );
    }

    public StrategyConfig(
            String symbol,
            BigDecimal baseBuyPrice,
            int baseBuyQty,
            BigDecimal stopLoss,
            boolean sellTriggerEnabled,
            BigDecimal sellTriggerPrice,
            BigDecimal lossBuyLevel1Price,
            int lossBuyLevel1Qty,
            BigDecimal lossBuyLevel2Price,
            int lossBuyLevel2Qty,
            boolean lossBuyLevelsEnabled,
            boolean optionalLossExitEnabled,
            BigDecimal optionalLossExitPrice,
            int pollingSeconds,
            boolean paperTrading,
            boolean alpacaTrailingStopEnabled,
            boolean profitHoldEnabled,
            ProfitHoldType profitHoldType,
            BigDecimal profitHoldPercent,
            BigDecimal profitHoldAmount,
            boolean repeatCycleAfterProfitExitEnabled
    ) {
        this(
                symbol,
                baseBuyPrice,
                baseBuyQty,
                true,
                stopLoss,
                sellTriggerEnabled,
                sellTriggerPrice,
                lossBuyLevel1Price,
                lossBuyLevel1Qty,
                lossBuyLevel2Price,
                lossBuyLevel2Qty,
                lossBuyLevelsEnabled,
                optionalLossExitEnabled,
                optionalLossExitPrice,
                pollingSeconds,
                paperTrading,
                alpacaTrailingStopEnabled,
                profitHoldEnabled,
                profitHoldType,
                profitHoldPercent,
                profitHoldAmount,
                repeatCycleAfterProfitExitEnabled,
                ProfitControlMode.NONE,
                ThresholdType.FIXED_AMOUNT,
                BigDecimal.ZERO,
                TrailingType.PERCENTAGE,
                BigDecimal.ZERO,
                false
        );
    }

    public StrategyConfig(
            String symbol,
            BigDecimal baseBuyPrice,
            int baseBuyQty,
            BigDecimal stopLoss,
            BigDecimal sellTriggerPrice,
            BigDecimal lossBuyLevel1Price,
            int lossBuyLevel1Qty,
            BigDecimal lossBuyLevel2Price,
            int lossBuyLevel2Qty,
            boolean lossBuyLevelsEnabled,
            boolean optionalLossExitEnabled,
            BigDecimal optionalLossExitPrice,
            int pollingSeconds,
            boolean paperTrading,
            boolean profitHoldEnabled,
            ProfitHoldType profitHoldType,
            BigDecimal profitHoldPercent,
            BigDecimal profitHoldAmount,
            boolean repeatCycleAfterProfitExitEnabled
    ) {
        this(
                symbol,
                baseBuyPrice,
                baseBuyQty,
                true,
                stopLoss,
                true,
                sellTriggerPrice,
                lossBuyLevel1Price,
                lossBuyLevel1Qty,
                lossBuyLevel2Price,
                lossBuyLevel2Qty,
                lossBuyLevelsEnabled,
                optionalLossExitEnabled,
                optionalLossExitPrice,
                pollingSeconds,
                paperTrading,
                false,
                profitHoldEnabled,
                profitHoldType,
                profitHoldPercent,
                profitHoldAmount,
                repeatCycleAfterProfitExitEnabled,
                ProfitControlMode.NONE,
                ThresholdType.FIXED_AMOUNT,
                BigDecimal.ZERO,
                TrailingType.PERCENTAGE,
                BigDecimal.ZERO,
                false
        );
    }

    public StrategyConfig(
            String symbol,
            BigDecimal baseBuyPrice,
            int baseBuyQty,
            BigDecimal stopLoss,
            BigDecimal sellTriggerPrice,
            BigDecimal lossBuyLevel1Price,
            int lossBuyLevel1Qty,
            BigDecimal lossBuyLevel2Price,
            int lossBuyLevel2Qty,
            int pollingSeconds,
            boolean paperTrading,
            boolean holdAtTenPercentProfit
    ) {
        this(
                symbol,
                baseBuyPrice,
                baseBuyQty,
                true,
                stopLoss,
                true,
                sellTriggerPrice,
                lossBuyLevel1Price,
                lossBuyLevel1Qty,
                lossBuyLevel2Price,
                lossBuyLevel2Qty,
                true,
                false,
                BigDecimal.ZERO,
                pollingSeconds,
                paperTrading,
                false,
                holdAtTenPercentProfit,
                ProfitHoldType.PERCENT_TRAILING,
                holdAtTenPercentProfit ? new BigDecimal("10.00") : BigDecimal.ZERO,
                BigDecimal.ZERO,
                false,
                ProfitControlMode.NONE,
                ThresholdType.FIXED_AMOUNT,
                BigDecimal.ZERO,
                TrailingType.PERCENTAGE,
                BigDecimal.ZERO,
                false
        );
    }

    public boolean holdAtTenPercentProfit() {
        return profitHoldEnabled
                && profitHoldType == ProfitHoldType.PERCENT_TRAILING
                && profitHoldPercent.compareTo(new BigDecimal("10.00")) == 0;
    }

    private static ProfitControlMode normalizeProfitControlMode(
            ProfitControlMode mode,
            boolean sellTriggerEnabled,
            boolean alpacaTrailingStopEnabled,
            boolean profitHoldEnabled
    ) {
        if (mode != null && mode != ProfitControlMode.NONE) {
            return mode;
        }
        if (profitHoldEnabled) {
            return ProfitControlMode.PROFIT_HOLD;
        }
        if (alpacaTrailingStopEnabled) {
            return ProfitControlMode.AUTOMATIC_STOP_SELL;
        }
        if (sellTriggerEnabled) {
            return ProfitControlMode.SELL_TRIGGER;
        }
        return ProfitControlMode.NONE;
    }
}
