package com.neuralarc.service;

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
        if (strategy.initialBuyQuantity() <= 0) {
            errors.add("Initial quantity must be positive");
        }
        if (strategy.initialBuyLimitPrice().compareTo(Monetary.zero()) <= 0) {
            errors.add("Initial buy limit price must be positive");
        }
        if (strategy.maxTotalQuantity() <= 0) {
            errors.add("Max total quantity must be positive");
        }
        if (strategy.maxCapitalAllowed().compareTo(Monetary.zero()) <= 0) {
            errors.add("Max capital allowed must be positive");
        }

        BigDecimal projectedInitialCapital = strategy.initialBuyLimitPrice()
                .multiply(BigDecimal.valueOf(strategy.initialBuyQuantity()));
        if (projectedInitialCapital.compareTo(strategy.maxCapitalAllowed()) > 0) {
            errors.add("Initial buy exceeds max capital allowed");
        }
        if (strategy.initialBuyQuantity() > strategy.maxTotalQuantity()) {
            errors.add("Initial quantity exceeds max total quantity");
        }
        return errors;
    }
}

