package com.neuralarc.model;

import com.neuralarc.util.Monetary;

import java.math.BigDecimal;

public record StrategyConfig(
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
        BigDecimal profitHoldAmount) {
    public StrategyConfig {
        baseBuyPrice = Monetary.round(baseBuyPrice);
        stopLoss = Monetary.round(stopLoss);
        sellTriggerPrice = Monetary.round(sellTriggerPrice);
        lossBuyLevel1Price = Monetary.round(lossBuyLevel1Price);
        lossBuyLevel2Price = Monetary.round(lossBuyLevel2Price);
        profitHoldType = profitHoldType == null ? ProfitHoldType.PERCENT_TRAILING : profitHoldType;
        profitHoldPercent = Monetary.round(profitHoldPercent);
        profitHoldAmount = Monetary.round(profitHoldAmount);
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
                stopLoss,
                sellTriggerPrice,
                lossBuyLevel1Price,
                lossBuyLevel1Qty,
                lossBuyLevel2Price,
                lossBuyLevel2Qty,
                pollingSeconds,
                paperTrading,
                holdAtTenPercentProfit,
                ProfitHoldType.PERCENT_TRAILING,
                holdAtTenPercentProfit ? new BigDecimal("10.00") : BigDecimal.ZERO,
                BigDecimal.ZERO
        );
    }

    public boolean holdAtTenPercentProfit() {
        return profitHoldEnabled
                && profitHoldType == ProfitHoldType.PERCENT_TRAILING
                && profitHoldPercent.compareTo(new BigDecimal("10.00")) == 0;
    }
}
