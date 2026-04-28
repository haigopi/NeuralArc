package com.neuralarc.model;

import com.neuralarc.util.Monetary;

import java.math.BigDecimal;

public record StrategyConfig(
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
        BigDecimal profitHoldAmount,
        boolean repeatCycleAfterProfitExitEnabled) {
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
                sellTriggerPrice,
                lossBuyLevel1Price,
                lossBuyLevel1Qty,
                lossBuyLevel2Price,
                lossBuyLevel2Qty,
                optionalLossExitEnabled,
                optionalLossExitPrice,
                pollingSeconds,
                paperTrading,
                profitHoldEnabled,
                profitHoldType,
                profitHoldPercent,
                profitHoldAmount,
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
                stopLoss,
                sellTriggerPrice,
                lossBuyLevel1Price,
                lossBuyLevel1Qty,
                lossBuyLevel2Price,
                lossBuyLevel2Qty,
                optionalLossExitEnabled,
                optionalLossExitPrice,
                pollingSeconds,
                paperTrading,
                profitHoldEnabled,
                profitHoldType,
                profitHoldPercent,
                profitHoldAmount,
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
            BigDecimal profitHoldAmount,
            boolean repeatCycleAfterProfitExitEnabled
    ) {
        this(
                symbol,
                baseBuyPrice,
                baseBuyQty,
                true,
                stopLoss,
                sellTriggerPrice,
                lossBuyLevel1Price,
                lossBuyLevel1Qty,
                lossBuyLevel2Price,
                lossBuyLevel2Qty,
                optionalLossExitEnabled,
                optionalLossExitPrice,
                pollingSeconds,
                paperTrading,
                profitHoldEnabled,
                profitHoldType,
                profitHoldPercent,
                profitHoldAmount,
                repeatCycleAfterProfitExitEnabled
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
                sellTriggerPrice,
                lossBuyLevel1Price,
                lossBuyLevel1Qty,
                lossBuyLevel2Price,
                lossBuyLevel2Qty,
                false,
                BigDecimal.ZERO,
                pollingSeconds,
                paperTrading,
                profitHoldEnabled,
                profitHoldType,
                profitHoldPercent,
                profitHoldAmount,
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
                sellTriggerPrice,
                lossBuyLevel1Price,
                lossBuyLevel1Qty,
                lossBuyLevel2Price,
                lossBuyLevel2Qty,
                false,
                BigDecimal.ZERO,
                pollingSeconds,
                paperTrading,
                holdAtTenPercentProfit,
                ProfitHoldType.PERCENT_TRAILING,
                holdAtTenPercentProfit ? new BigDecimal("10.00") : BigDecimal.ZERO,
                BigDecimal.ZERO,
                false
        );
    }

    public boolean holdAtTenPercentProfit() {
        return profitHoldEnabled
                && profitHoldType == ProfitHoldType.PERCENT_TRAILING
                && profitHoldPercent.compareTo(new BigDecimal("10.00")) == 0;
    }
}
