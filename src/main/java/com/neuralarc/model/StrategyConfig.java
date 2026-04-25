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
        boolean holdAtTenPercentProfit) {
    public StrategyConfig {
        baseBuyPrice = Monetary.round(baseBuyPrice);
        stopLoss = Monetary.round(stopLoss);
        sellTriggerPrice = Monetary.round(sellTriggerPrice);
        lossBuyLevel1Price = Monetary.round(lossBuyLevel1Price);
        lossBuyLevel2Price = Monetary.round(lossBuyLevel2Price);
    }
}
