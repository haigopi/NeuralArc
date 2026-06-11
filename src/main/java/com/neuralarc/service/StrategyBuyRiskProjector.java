package com.neuralarc.service;

import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyOrder;
import com.neuralarc.model.StrategyOrderSide;
import com.neuralarc.model.StrategyOrderStatus;
import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.util.List;

final class StrategyBuyRiskProjector {
    private StrategyBuyRiskProjector() {
    }

    static RiskProjection projectedRisk(
            Strategy strategy,
            List<StrategyOrder> orders,
            BigDecimal newOrderQty,
            BigDecimal newOrderPrice
    ) {
        BigDecimal projectedQty = newOrderQty;
        BigDecimal projectedCapital = Monetary.round(newOrderPrice.multiply(newOrderQty));

        for (StrategyOrder order : orders) {
            if (order.side() != StrategyOrderSide.BUY) {
                continue;
            }
            if (order.status() == StrategyOrderStatus.CANCELED
                    || order.status() == StrategyOrderStatus.REJECTED
                    || order.status() == StrategyOrderStatus.FAILED) {
                continue;
            }
            projectedQty = projectedQty.add(order.requestedQuantity());
            projectedCapital = Monetary.round(projectedCapital.add(order.limitPrice().multiply(order.requestedQuantity())));
        }

        if (projectedQty.compareTo(BigDecimal.valueOf(strategy.maxTotalQuantity())) > 0) {
            return new RiskProjection(false, "Projected quantity exceeds maxTotalQuantity");
        }
        if (projectedCapital.compareTo(strategy.maxCapitalAllowed()) > 0) {
            return new RiskProjection(false, "Projected capital exceeds maxCapitalAllowed");
        }
        return new RiskProjection(true, "");
    }

    record RiskProjection(boolean allowed, String reason) {
    }
}
