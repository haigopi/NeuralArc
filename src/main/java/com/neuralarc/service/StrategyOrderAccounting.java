package com.neuralarc.service;

import com.neuralarc.model.StrategyOrder;
import com.neuralarc.model.StrategyOrderSide;
import com.neuralarc.model.StrategyOrderStatus;
import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

final class StrategyOrderAccounting {
    private StrategyOrderAccounting() {
    }

    static BigDecimal realizedPnlForOrders(List<StrategyOrder> orders) {
        BigDecimal positionQty = BigDecimal.ZERO;
        BigDecimal averageCost = BigDecimal.ZERO;
        BigDecimal realized = BigDecimal.ZERO;

        for (StrategyOrder order : filledOrders(orders)) {
            BigDecimal quantity = order.filledQuantity();
            BigDecimal fillPrice = fillPrice(order);

            if (order.side() == StrategyOrderSide.BUY) {
                BigDecimal runningCost = averageCost.multiply(positionQty).add(fillPrice.multiply(quantity));
                positionQty = positionQty.add(quantity);
                if (positionQty.compareTo(BigDecimal.ZERO) > 0) {
                    averageCost = runningCost.divide(positionQty, 8, java.math.RoundingMode.HALF_UP);
                }
                continue;
            }

            SellBasis.Result basis = SellBasis.of(fillPrice, quantity, positionQty, averageCost,
                    SellBasis.brokerAverageEntry(order));
            if (basis.isEmpty()) {
                continue;
            }
            realized = realized.add(basis.realized());
            // Only the tracked shares leave the tracked position, however the sale was priced.
            positionQty = positionQty.subtract(quantity.min(positionQty.max(BigDecimal.ZERO)));
            if (positionQty.compareTo(BigDecimal.ZERO) <= 0) {
                positionQty = BigDecimal.ZERO;
                averageCost = BigDecimal.ZERO;
            }
        }

        return Monetary.round(realized);
    }

    /**
     * Realized P&amp;L booked by one sell fill, against the average cost held at the moment it filled.
     *
     * <p>Distinct from {@link #realizedPnlForOrders(List)}, which is the strategy's running total: a
     * profitable exit on a strategy that lost money earlier still has to read as a profit.
     */
    static BigDecimal realizedPnlForSellOrder(List<StrategyOrder> orders, StrategyOrder sellOrder) {
        if (sellOrder == null || sellOrder.side() != StrategyOrderSide.SELL) {
            return Monetary.zero();
        }
        List<StrategyOrder> history = filledOrders(orders);
        if (history.stream().noneMatch(order -> order.id().equals(sellOrder.id()))) {
            history = filledOrders(concat(orders, sellOrder));
        }

        BigDecimal positionQty = BigDecimal.ZERO;
        BigDecimal averageCost = BigDecimal.ZERO;

        for (StrategyOrder order : history) {
            BigDecimal quantity = order.filledQuantity();
            BigDecimal fillPrice = fillPrice(order);

            if (order.side() == StrategyOrderSide.BUY) {
                BigDecimal runningCost = averageCost.multiply(positionQty).add(fillPrice.multiply(quantity));
                positionQty = positionQty.add(quantity);
                if (positionQty.compareTo(BigDecimal.ZERO) > 0) {
                    averageCost = runningCost.divide(positionQty, 8, java.math.RoundingMode.HALF_UP);
                }
                continue;
            }

            SellBasis.Result basis = SellBasis.of(fillPrice, quantity, positionQty, averageCost,
                    SellBasis.brokerAverageEntry(order));
            if (order.id().equals(sellOrder.id())) {
                return Monetary.round(basis.realized());
            }
            if (basis.isEmpty()) {
                continue;
            }
            positionQty = positionQty.subtract(quantity.min(positionQty.max(BigDecimal.ZERO)));
            if (positionQty.compareTo(BigDecimal.ZERO) <= 0) {
                positionQty = BigDecimal.ZERO;
                averageCost = BigDecimal.ZERO;
            }
        }
        return Monetary.zero();
    }

    private static List<StrategyOrder> concat(List<StrategyOrder> orders, StrategyOrder extra) {
        List<StrategyOrder> combined = new java.util.ArrayList<>(orders == null ? List.of() : orders);
        combined.add(extra);
        return combined;
    }

    private static List<StrategyOrder> filledOrders(List<StrategyOrder> orders) {
        if (orders == null || orders.isEmpty()) {
            return List.of();
        }
        return orders.stream()
                .filter(order -> order.status() == StrategyOrderStatus.FILLED
                        || order.status() == StrategyOrderStatus.PARTIALLY_FILLED)
                .filter(order -> order.filledQuantity().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator
                        .comparing(StrategyOrderAccounting::filledSortTime, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(StrategyOrder::submittedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private static Instant filledSortTime(StrategyOrder order) {
        return order == null ? null : order.filledAt();
    }

    private static BigDecimal fillPrice(StrategyOrder order) {
        return order.filledAveragePrice().compareTo(BigDecimal.ZERO) > 0
                ? order.filledAveragePrice()
                : order.limitPrice();
    }
}
