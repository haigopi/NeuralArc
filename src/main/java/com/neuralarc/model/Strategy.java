package com.neuralarc.model;

import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public class Strategy {
    private final String id;
    private String name;
    private String symbol;
    private StrategyMode mode;
    private StrategyStatus status;
    private BigDecimal initialBuyLimitPrice;
    private int initialBuyQuantity;
    private BigDecimal stopLossPrice;
    private BigDecimal targetSellPrice;
    private boolean profitHoldEnabled;
    private BigDecimal profitHoldPercentOrAmount;
    private BigDecimal lossBuyLevel1Price;
    private int lossBuyLevel1Quantity;
    private BigDecimal lossBuyLevel2Price;
    private int lossBuyLevel2Quantity;
    private int maxTotalQuantity;
    private BigDecimal maxCapitalAllowed;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant lastPolledAt;
    private String lastError;
    private BigDecimal highestPriceAfterTarget = Monetary.zero();
    private boolean profitHoldArmed;

    public Strategy(
            String id,
            String name,
            String symbol,
            StrategyMode mode,
            StrategyStatus status,
            BigDecimal initialBuyLimitPrice,
            int initialBuyQuantity,
            BigDecimal stopLossPrice,
            BigDecimal targetSellPrice,
            boolean profitHoldEnabled,
            BigDecimal profitHoldPercentOrAmount,
            BigDecimal lossBuyLevel1Price,
            int lossBuyLevel1Quantity,
            BigDecimal lossBuyLevel2Price,
            int lossBuyLevel2Quantity,
            int maxTotalQuantity,
            BigDecimal maxCapitalAllowed,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = name == null ? "" : name.trim();
        this.symbol = symbol == null ? "" : symbol.trim().toUpperCase();
        this.mode = mode == null ? StrategyMode.PAPER : mode;
        this.status = status == null ? StrategyStatus.CREATED : status;
        this.initialBuyLimitPrice = Monetary.round(initialBuyLimitPrice);
        this.initialBuyQuantity = initialBuyQuantity;
        this.stopLossPrice = Monetary.round(stopLossPrice);
        this.targetSellPrice = Monetary.round(targetSellPrice);
        this.profitHoldEnabled = profitHoldEnabled;
        this.profitHoldPercentOrAmount = Monetary.round(profitHoldPercentOrAmount);
        this.lossBuyLevel1Price = Monetary.round(lossBuyLevel1Price);
        this.lossBuyLevel1Quantity = lossBuyLevel1Quantity;
        this.lossBuyLevel2Price = Monetary.round(lossBuyLevel2Price);
        this.lossBuyLevel2Quantity = lossBuyLevel2Quantity;
        this.maxTotalQuantity = maxTotalQuantity;
        this.maxCapitalAllowed = Monetary.round(maxCapitalAllowed);
        this.createdAt = createdAt == null ? Instant.now() : createdAt;
        this.updatedAt = updatedAt == null ? this.createdAt : updatedAt;
    }

    public static Strategy fromConfig(String id, String name, StrategyConfig config, StrategyMode mode) {
        int maxQty = config.baseBuyQty() + config.lossBuyLevel1Qty() + config.lossBuyLevel2Qty();
        BigDecimal maxCapital = Monetary.round(config.baseBuyPrice().multiply(BigDecimal.valueOf(config.baseBuyQty()))
                .add(config.lossBuyLevel1Price().multiply(BigDecimal.valueOf(config.lossBuyLevel1Qty())))
                .add(config.lossBuyLevel2Price().multiply(BigDecimal.valueOf(config.lossBuyLevel2Qty()))));
        return new Strategy(
                id,
                name,
                config.symbol(),
                mode,
                StrategyStatus.CREATED,
                config.baseBuyPrice(),
                config.baseBuyQty(),
                config.stopLoss(),
                config.sellTriggerPrice(),
                config.holdAtTenPercentProfit(),
                new BigDecimal("2.00"),
                config.lossBuyLevel1Price(),
                config.lossBuyLevel1Qty(),
                config.lossBuyLevel2Price(),
                config.lossBuyLevel2Qty(),
                maxQty,
                maxCapital,
                Instant.now(),
                Instant.now()
        );
    }

    public void touch() {
        updatedAt = Instant.now();
    }

    public String id() { return id; }
    public String name() { return name; }
    public String symbol() { return symbol; }
    public StrategyMode mode() { return mode; }
    public StrategyStatus status() { return status; }
    public BigDecimal initialBuyLimitPrice() { return initialBuyLimitPrice; }
    public int initialBuyQuantity() { return initialBuyQuantity; }
    public BigDecimal stopLossPrice() { return stopLossPrice; }
    public BigDecimal targetSellPrice() { return targetSellPrice; }
    public boolean profitHoldEnabled() { return profitHoldEnabled; }
    public BigDecimal profitHoldPercentOrAmount() { return profitHoldPercentOrAmount; }
    public BigDecimal lossBuyLevel1Price() { return lossBuyLevel1Price; }
    public int lossBuyLevel1Quantity() { return lossBuyLevel1Quantity; }
    public BigDecimal lossBuyLevel2Price() { return lossBuyLevel2Price; }
    public int lossBuyLevel2Quantity() { return lossBuyLevel2Quantity; }
    public int maxTotalQuantity() { return maxTotalQuantity; }
    public BigDecimal maxCapitalAllowed() { return maxCapitalAllowed; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public Instant lastPolledAt() { return lastPolledAt; }
    public String lastError() { return lastError; }
    public BigDecimal highestPriceAfterTarget() { return highestPriceAfterTarget; }
    public boolean profitHoldArmed() { return profitHoldArmed; }

    public void setStatus(StrategyStatus status) { this.status = status; touch(); }
    public void setLastPolledAt(Instant lastPolledAt) { this.lastPolledAt = lastPolledAt; touch(); }
    public void setLastError(String lastError) { this.lastError = lastError; touch(); }
    public void clearLastError() { this.lastError = null; touch(); }
    public void armProfitHold(BigDecimal startingHigh) {
        this.profitHoldArmed = true;
        this.highestPriceAfterTarget = Monetary.round(startingHigh);
        touch();
    }

    public void updateHighestPriceAfterTarget(BigDecimal price) {
        BigDecimal normalized = Monetary.round(price);
        if (normalized.compareTo(highestPriceAfterTarget) > 0) {
            highestPriceAfterTarget = normalized;
            touch();
        }
    }
}

