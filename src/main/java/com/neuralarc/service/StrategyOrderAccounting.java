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

            BigDecimal sellQty = quantity.min(positionQty.max(BigDecimal.ZERO));
            if (sellQty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            realized = realized.add(fillPrice.subtract(averageCost).multiply(sellQty));
            positionQty = positionQty.subtract(sellQty);
            if (positionQty.compareTo(BigDecimal.ZERO) == 0) {
                averageCost = BigDecimal.ZERO;
            }
        }

        return Monetary.round(realized);
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
