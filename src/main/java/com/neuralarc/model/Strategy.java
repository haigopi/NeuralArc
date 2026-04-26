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
    private StrategyLifecycleState currentState;
    private BigDecimal baseBuyLimitPrice;
    private int baseBuyQuantity;
    private BigDecimal buyLimit1Price;
    private int buyLimit1Quantity;
    private BigDecimal buyLimit2Price;
    private int buyLimit2Quantity;
    private boolean automatedStopLossEnabled;
    private StopLossType stopLossType;
    private BigDecimal stopLossPrice;
    private BigDecimal stopLossPercent;
    private boolean optionalLossExitEnabled;
    private BigDecimal optionalLossExitPrice;
    private boolean targetSellEnabled;
    private BigDecimal targetSellPrice;
    private BigDecimal targetSellQuantityOrPercent;
    private boolean targetSellPercentBased;
    private boolean profitHoldEnabled;
    private ProfitHoldType profitHoldType;
    private BigDecimal profitHoldPercent;
    private BigDecimal profitHoldAmount;
    private BigDecimal highestObservedPriceAfterTarget;
    private boolean restartAfterExitEnabled;
    private int maxTotalQuantity;
    private BigDecimal maxCapitalAllowed;
    private int pollingIntervalSeconds;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant lastPolledAt;
    private String lastError;
    private String lastEvent;
    private String latestOrderStatus;
    private String latestAlpacaOrderId;
    private String lastTriggeredRuleType;

    public Strategy(
            String id,
            String name,
            String symbol,
            StrategyMode mode,
            StrategyStatus status,
            StrategyLifecycleState currentState,
            BigDecimal baseBuyLimitPrice,
            int baseBuyQuantity,
            BigDecimal buyLimit1Price,
            int buyLimit1Quantity,
            BigDecimal buyLimit2Price,
            int buyLimit2Quantity,
            boolean automatedStopLossEnabled,
            StopLossType stopLossType,
            BigDecimal stopLossPrice,
            BigDecimal stopLossPercent,
            boolean optionalLossExitEnabled,
            BigDecimal optionalLossExitPrice,
            boolean targetSellEnabled,
            BigDecimal targetSellPrice,
            BigDecimal targetSellQuantityOrPercent,
            boolean targetSellPercentBased,
            boolean profitHoldEnabled,
            ProfitHoldType profitHoldType,
            BigDecimal profitHoldPercent,
            BigDecimal profitHoldAmount,
            BigDecimal highestObservedPriceAfterTarget,
            boolean restartAfterExitEnabled,
            int maxTotalQuantity,
            BigDecimal maxCapitalAllowed,
            int pollingIntervalSeconds,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = normalizeText(name);
        this.symbol = normalizeSymbol(symbol);
        this.mode = mode == null ? StrategyMode.PAPER : mode;
        this.status = status == null ? StrategyStatus.CREATED : status;
        this.currentState = currentState == null ? StrategyLifecycleState.CREATED : currentState;
        this.baseBuyLimitPrice = money(baseBuyLimitPrice);
        this.baseBuyQuantity = baseBuyQuantity;
        this.buyLimit1Price = money(buyLimit1Price);
        this.buyLimit1Quantity = buyLimit1Quantity;
        this.buyLimit2Price = money(buyLimit2Price);
        this.buyLimit2Quantity = buyLimit2Quantity;
        this.automatedStopLossEnabled = automatedStopLossEnabled;
        this.stopLossType = stopLossType == null ? StopLossType.FIXED_PRICE : stopLossType;
        this.stopLossPrice = money(stopLossPrice);
        this.stopLossPercent = money(stopLossPercent);
        this.optionalLossExitEnabled = optionalLossExitEnabled;
        this.optionalLossExitPrice = money(optionalLossExitPrice);
        this.targetSellEnabled = targetSellEnabled;
        this.targetSellPrice = money(targetSellPrice);
        this.targetSellQuantityOrPercent = money(targetSellQuantityOrPercent);
        this.targetSellPercentBased = targetSellPercentBased;
        this.profitHoldEnabled = profitHoldEnabled;
        this.profitHoldType = profitHoldType == null ? ProfitHoldType.PERCENT_TRAILING : profitHoldType;
        this.profitHoldPercent = money(profitHoldPercent);
        this.profitHoldAmount = money(profitHoldAmount);
        this.highestObservedPriceAfterTarget = money(highestObservedPriceAfterTarget);
        this.restartAfterExitEnabled = restartAfterExitEnabled;
        this.maxTotalQuantity = maxTotalQuantity;
        this.maxCapitalAllowed = money(maxCapitalAllowed);
        this.pollingIntervalSeconds = pollingIntervalSeconds;
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
                StrategyLifecycleState.CREATED,
                config.baseBuyPrice(),
                config.baseBuyQty(),
                config.lossBuyLevel1Price(),
                config.lossBuyLevel1Qty(),
                config.lossBuyLevel2Price(),
                config.lossBuyLevel2Qty(),
                true,
                StopLossType.FIXED_PRICE,
                config.stopLoss(),
                Monetary.zero(),
                false,
                Monetary.zero(),
                true,
                config.sellTriggerPrice(),
                BigDecimal.valueOf(100),
                true,
                config.profitHoldEnabled(),
                config.profitHoldType(),
                config.profitHoldPercent(),
                config.profitHoldAmount(),
                Monetary.zero(),
                false,
                maxQty,
                maxCapital,
                config.pollingSeconds(),
                Instant.now(),
                Instant.now()
        );
    }

    private static BigDecimal money(BigDecimal value) {
        return Monetary.round(value == null ? BigDecimal.ZERO : value);
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeSymbol(String value) {
        return normalizeText(value).toUpperCase();
    }

    public void touch() {
        updatedAt = Instant.now();
    }

    public BigDecimal estimatedTotalCapital() {
        BigDecimal total = baseBuyLimitPrice.multiply(BigDecimal.valueOf(baseBuyQuantity));
        total = total.add(buyLimit1Price.multiply(BigDecimal.valueOf(Math.max(0, buyLimit1Quantity))));
        total = total.add(buyLimit2Price.multiply(BigDecimal.valueOf(Math.max(0, buyLimit2Quantity))));
        return Monetary.round(total);
    }

    public int configuredTotalQuantity() {
        return Math.max(0, baseBuyQuantity) + Math.max(0, buyLimit1Quantity) + Math.max(0, buyLimit2Quantity);
    }

    public BigDecimal targetSellQuantity(BigDecimal positionQuantity) {
        BigDecimal normalizedPosition = money(positionQuantity);
        if (normalizedPosition.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        if (!targetSellPercentBased) {
            return targetSellQuantityOrPercent.min(normalizedPosition);
        }
        BigDecimal percent = targetSellQuantityOrPercent.divide(new BigDecimal("100"));
        BigDecimal quantity = normalizedPosition.multiply(percent);
        if (quantity.compareTo(BigDecimal.ONE) < 0) {
            quantity = BigDecimal.ONE;
        }
        return quantity.min(normalizedPosition);
    }

    public void markTransition(StrategyLifecycleState newState, String eventMessage) {
        this.currentState = newState == null ? this.currentState : newState;
        this.lastEvent = normalizeText(eventMessage);
        touch();
    }

    public void clearProfitHoldTracking() {
        this.highestObservedPriceAfterTarget = BigDecimal.ZERO;
        touch();
    }

    public void updateHighestObservedPriceAfterTarget(BigDecimal price) {
        BigDecimal normalized = money(price);
        if (normalized.compareTo(highestObservedPriceAfterTarget) > 0) {
            highestObservedPriceAfterTarget = normalized;
            touch();
        }
    }

    public String id() { return id; }
    public String name() { return name; }
    public String symbol() { return symbol; }
    public StrategyMode mode() { return mode; }
    public StrategyStatus status() { return status; }
    public StrategyLifecycleState currentState() { return currentState; }
    public BigDecimal baseBuyLimitPrice() { return baseBuyLimitPrice; }
    public int baseBuyQuantity() { return baseBuyQuantity; }
    public BigDecimal buyLimit1Price() { return buyLimit1Price; }
    public int buyLimit1Quantity() { return buyLimit1Quantity; }
    public BigDecimal buyLimit2Price() { return buyLimit2Price; }
    public int buyLimit2Quantity() { return buyLimit2Quantity; }
    public boolean automatedStopLossEnabled() { return automatedStopLossEnabled; }
    public StopLossType stopLossType() { return stopLossType; }
    public BigDecimal stopLossPrice() { return stopLossPrice; }
    public BigDecimal stopLossPercent() { return stopLossPercent; }
    public boolean optionalLossExitEnabled() { return optionalLossExitEnabled; }
    public BigDecimal optionalLossExitPrice() { return optionalLossExitPrice; }
    public boolean targetSellEnabled() { return targetSellEnabled; }
    public BigDecimal targetSellPrice() { return targetSellPrice; }
    public BigDecimal targetSellQuantityOrPercent() { return targetSellQuantityOrPercent; }
    public boolean targetSellPercentBased() { return targetSellPercentBased; }
    public boolean profitHoldEnabled() { return profitHoldEnabled; }
    public ProfitHoldType profitHoldType() { return profitHoldType; }
    public BigDecimal profitHoldPercent() { return profitHoldPercent; }
    public BigDecimal profitHoldAmount() { return profitHoldAmount; }
    public BigDecimal highestObservedPriceAfterTarget() { return highestObservedPriceAfterTarget; }
    public boolean restartAfterExitEnabled() { return restartAfterExitEnabled; }
    public int maxTotalQuantity() { return maxTotalQuantity; }
    public BigDecimal maxCapitalAllowed() { return maxCapitalAllowed; }
    public int pollingIntervalSeconds() { return pollingIntervalSeconds; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public Instant lastPolledAt() { return lastPolledAt; }
    public String lastError() { return lastError; }
    public String lastEvent() { return lastEvent; }
    public String latestOrderStatus() { return latestOrderStatus; }
    public String latestAlpacaOrderId() { return latestAlpacaOrderId; }
    public String lastTriggeredRuleType() { return lastTriggeredRuleType; }

    public void setName(String name) { this.name = normalizeText(name); touch(); }
    public void setSymbol(String symbol) { this.symbol = normalizeSymbol(symbol); touch(); }
    public void setMode(StrategyMode mode) { this.mode = mode == null ? StrategyMode.PAPER : mode; touch(); }
    public void setStatus(StrategyStatus status) { this.status = status == null ? this.status : status; touch(); }
    public void setCurrentState(StrategyLifecycleState currentState) { this.currentState = currentState == null ? this.currentState : currentState; touch(); }
    public void setBaseBuyLimitPrice(BigDecimal value) { this.baseBuyLimitPrice = money(value); touch(); }
    public void setBaseBuyQuantity(int value) { this.baseBuyQuantity = value; touch(); }
    public void setBuyLimit1Price(BigDecimal value) { this.buyLimit1Price = money(value); touch(); }
    public void setBuyLimit1Quantity(int value) { this.buyLimit1Quantity = value; touch(); }
    public void setBuyLimit2Price(BigDecimal value) { this.buyLimit2Price = money(value); touch(); }
    public void setBuyLimit2Quantity(int value) { this.buyLimit2Quantity = value; touch(); }
    public void setAutomatedStopLossEnabled(boolean enabled) { this.automatedStopLossEnabled = enabled; touch(); }
    public void setStopLossType(StopLossType stopLossType) { this.stopLossType = stopLossType == null ? StopLossType.FIXED_PRICE : stopLossType; touch(); }
    public void setStopLossPrice(BigDecimal value) { this.stopLossPrice = money(value); touch(); }
    public void setStopLossPercent(BigDecimal value) { this.stopLossPercent = money(value); touch(); }
    public void setOptionalLossExitEnabled(boolean enabled) { this.optionalLossExitEnabled = enabled; touch(); }
    public void setOptionalLossExitPrice(BigDecimal value) { this.optionalLossExitPrice = money(value); touch(); }
    public void setTargetSellEnabled(boolean enabled) { this.targetSellEnabled = enabled; touch(); }
    public void setTargetSellPrice(BigDecimal value) { this.targetSellPrice = money(value); touch(); }
    public void setTargetSellQuantityOrPercent(BigDecimal value) { this.targetSellQuantityOrPercent = money(value); touch(); }
    public void setTargetSellPercentBased(boolean targetSellPercentBased) { this.targetSellPercentBased = targetSellPercentBased; touch(); }
    public void setProfitHoldEnabled(boolean enabled) { this.profitHoldEnabled = enabled; touch(); }
    public void setProfitHoldType(ProfitHoldType profitHoldType) { this.profitHoldType = profitHoldType == null ? ProfitHoldType.PERCENT_TRAILING : profitHoldType; touch(); }
    public void setProfitHoldPercent(BigDecimal value) { this.profitHoldPercent = money(value); touch(); }
    public void setProfitHoldAmount(BigDecimal value) { this.profitHoldAmount = money(value); touch(); }
    public void setHighestObservedPriceAfterTarget(BigDecimal value) { this.highestObservedPriceAfterTarget = money(value); touch(); }
    public void setRestartAfterExitEnabled(boolean enabled) { this.restartAfterExitEnabled = enabled; touch(); }
    public void setMaxTotalQuantity(int value) { this.maxTotalQuantity = value; touch(); }
    public void setMaxCapitalAllowed(BigDecimal value) { this.maxCapitalAllowed = money(value); touch(); }
    public void setPollingIntervalSeconds(int value) { this.pollingIntervalSeconds = value; touch(); }
    public void setLastPolledAt(Instant value) { this.lastPolledAt = value; touch(); }
    public void setLastError(String value) { this.lastError = normalizeText(value); touch(); }
    public void clearLastError() { this.lastError = null; touch(); }
    public void setLastEvent(String value) { this.lastEvent = normalizeText(value); touch(); }
    public void setLatestOrderStatus(String value) { this.latestOrderStatus = normalizeText(value); touch(); }
    public void setLatestAlpacaOrderId(String value) { this.latestAlpacaOrderId = normalizeText(value); touch(); }
    public void setLastTriggeredRuleType(String value) { this.lastTriggeredRuleType = normalizeText(value); touch(); }
}
