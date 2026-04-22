package com.neuralarc.model;

import java.math.BigDecimal;

public record StrategyConfig(
        String symbol,
        BigDecimal baseBuyPrice,
        int baseBuyQty,
        BigDecimal stopActivationPrice,
        BigDecimal sellTriggerPrice,
        BigDecimal lossBuyLevel1Price,
        int lossBuyLevel1Qty,
        BigDecimal lossBuyLevel2Price,
        int lossBuyLevel2Qty,
        int pollingSeconds,
        boolean paperTrading) {
}
