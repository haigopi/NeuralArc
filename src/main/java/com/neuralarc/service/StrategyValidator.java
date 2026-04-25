package com.neuralarc.service;

import com.neuralarc.model.StopLossType;
import com.neuralarc.model.Strategy;
import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class StrategyValidator {
    public List<String> validate(Strategy strategy) {
        List<String> errors = new ArrayList<>();
        if (strategy.symbol() == null || strategy.symbol().isBlank()) {
            errors.add("Symbol must not be empty");
        }
        if (strategy.name() == null || strategy.name().isBlank()) {
            errors.add("Strategy name must not be empty");
        }
        if (strategy.baseBuyQuantity() <= 0) {
            errors.add("Base buy quantity must be positive");
        }
        if (strategy.baseBuyLimitPrice().compareTo(Monetary.zero()) <= 0) {
            errors.add("Base buy limit price must be positive");
        }
        if (strategy.buyLimit1Quantity() < 0 || strategy.buyLimit2Quantity() < 0) {
            errors.add("Staged buy quantities must not be negative");
        }
        if (strategy.buyLimit1Quantity() > 0 && strategy.buyLimit1Price().compareTo(Monetary.zero()) <= 0) {
            errors.add("Buy Limit 1 price must be positive");
        }
        if (strategy.buyLimit2Quantity() > 0 && strategy.buyLimit2Price().compareTo(Monetary.zero()) <= 0) {
            errors.add("Buy Limit 2 price must be positive");
        }
        if (strategy.maxTotalQuantity() <= 0) {
            errors.add("Max total quantity must be positive");
        }
        if (strategy.maxCapitalAllowed().compareTo(Monetary.zero()) <= 0) {
            errors.add("Max capital allowed must be positive");
        }
        if (strategy.pollingIntervalSeconds() <= 0) {
            errors.add("Polling interval seconds must be positive");
        }
        if (strategy.configuredTotalQuantity() > strategy.maxTotalQuantity()) {
            errors.add("Configured buy quantities exceed max total quantity");
        }
        if (strategy.estimatedTotalCapital().compareTo(strategy.maxCapitalAllowed()) > 0) {
            errors.add("Configured buys exceed max capital allowed");
        }
        if (strategy.automatedStopLossEnabled()) {
            if (strategy.stopLossType() == StopLossType.FIXED_PRICE && strategy.stopLossPrice().compareTo(Monetary.zero()) <= 0) {
                errors.add("Stop loss price must be positive");
            }
            if (strategy.stopLossType() == StopLossType.PERCENT_BELOW_AVERAGE_COST
                    && (strategy.stopLossPercent().compareTo(Monetary.zero()) <= 0
                    || strategy.stopLossPercent().compareTo(new BigDecimal("100")) >= 0)) {
                errors.add("Stop loss percent must be between 0 and 100");
            }
        }
        if (strategy.optionalLossExitEnabled() && strategy.optionalLossExitPrice().compareTo(Monetary.zero()) <= 0) {
            errors.add("Optional loss exit price must be positive");
        }
        if (strategy.targetSellEnabled()) {
            if (strategy.targetSellPrice().compareTo(Monetary.zero()) <= 0) {
                errors.add("Target sell price must be positive");
            }
            if (strategy.targetSellQuantityOrPercent().compareTo(Monetary.zero()) <= 0) {
                errors.add("Target sell quantity or percent must be positive");
            }
        }
        if (strategy.profitHoldEnabled()) {
            if (strategy.profitHoldType().name().equals("PERCENT_TRAILING")
                    && (strategy.profitHoldPercent().compareTo(Monetary.zero()) <= 0
                    || strategy.profitHoldPercent().compareTo(new BigDecimal("100")) >= 0)) {
                errors.add("Profit hold percent must be between 0 and 100");
            }
            if (strategy.profitHoldType().name().equals("FIXED_AMOUNT_TRAILING")
                    && strategy.profitHoldAmount().compareTo(Monetary.zero()) <= 0) {
                errors.add("Profit hold trailing amount must be positive");
            }
        }
        return errors;
    }
}
