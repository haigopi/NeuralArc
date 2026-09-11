package com.neuralarc.ui;

import com.neuralarc.model.Position;
import com.neuralarc.model.TimeInForce;
import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The operator's average-down plan: how to buy, how many shares, and which positions.
 *
 * <p>The per-position order math lives here rather than in the controller so the review dialog's
 * preview and the orders actually submitted are computed by the same code and cannot drift apart.
 */
record AverageLosingPositionsSelection(
        OrderType orderType,
        QuantityMode quantityMode,
        int quantity,
        BigDecimal limitDiscountPercent,
        TimeInForce timeInForce,
        Set<String> strategyIds
) {
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    AverageLosingPositionsSelection {
        limitDiscountPercent = limitDiscountPercent == null ? BigDecimal.ZERO : limitDiscountPercent;
        timeInForce = timeInForce == null ? TimeInForce.DAY : timeInForce;
        strategyIds = strategyIds == null ? Set.of() : Set.copyOf(strategyIds);
    }

    AverageLosingPositionsSelection(
            OrderType orderType,
            QuantityMode quantityMode,
            int quantity,
            BigDecimal limitDiscountPercent,
            TimeInForce timeInForce
    ) {
        this(orderType, quantityMode, quantity, limitDiscountPercent, timeInForce, Set.of());
    }

    AverageLosingPositionsSelection(OrderType orderType, QuantityMode quantityMode, int quantity, BigDecimal limitDiscountPercent) {
        this(orderType, quantityMode, quantity, limitDiscountPercent, TimeInForce.DAY);
    }

    /** Shares to buy for one position under this plan. */
    int quantityFor(Position position) {
        if (quantityMode == QuantityMode.FIXED_INPUT_QUANTITY) {
            return quantity;
        }
        return position == null ? 0 : position.getTotalShares();
    }

    /** Limit price below the cached market price, or zero when the market price is unknown. */
    BigDecimal limitPriceFor(Position position) {
        BigDecimal currentPrice = position == null ? null : position.getLastPrice();
        if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal multiplier = BigDecimal.ONE.subtract(limitDiscountPercent.divide(HUNDRED, 8, RoundingMode.HALF_UP));
        return Monetary.round(currentPrice.multiply(multiplier));
    }

    /** Price used to estimate the order's cost: the limit price, or the last price for a market buy. */
    BigDecimal estimatedPriceFor(Position position) {
        if (orderType == OrderType.MARKET) {
            return position == null ? BigDecimal.ZERO : Monetary.round(position.getLastPrice());
        }
        return limitPriceFor(position);
    }

    /**
     * The positions the operator ticked, re-checked against {@code eligible}. A row must be both
     * chosen and still eligible when the orders go out, so a position whose sell started working
     * while the dialog was open is never bought into.
     */
    List<ManagedStrategy> selectedFrom(List<ManagedStrategy> scope, Predicate<ManagedStrategy> eligible) {
        if (scope == null || scope.isEmpty() || strategyIds.isEmpty()) {
            return List.of();
        }
        return scope.stream()
                .filter(entry -> entry != null && entry.strategy != null)
                .filter(entry -> strategyIds.contains(entry.strategy.id()))
                .filter(entry -> eligible == null || eligible.test(entry))
                .toList();
    }

    enum OrderType {
        MARKET,
        LIMIT_BELOW_MARKET
    }

    enum QuantityMode {
        CURRENT_POSITION_QUANTITY,
        FIXED_INPUT_QUANTITY
    }
}
