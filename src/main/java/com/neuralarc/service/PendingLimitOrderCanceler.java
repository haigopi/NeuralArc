package com.neuralarc.service;

import com.neuralarc.api.AlpacaClient;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyOrder;
import com.neuralarc.model.StrategyOrderSide;
import com.neuralarc.model.StrategyOrderStatus;
import com.neuralarc.model.StrategyOrderType;
import com.neuralarc.util.BrokerOrderStatusUtil;

import java.util.List;

final class PendingLimitOrderCanceler {
    private final AlpacaClient alpacaClient;
    private final StrategyOrderRepository orderRepository;

    PendingLimitOrderCanceler(AlpacaClient alpacaClient, StrategyOrderRepository orderRepository) {
        this.alpacaClient = alpacaClient;
        this.orderRepository = orderRepository;
    }

    int cancelPendingLimitBuys(Strategy strategy) {
        return cancelPendingLimitOrders(strategy, StrategyOrderSide.BUY);
    }

    /**
     * Cancels only this strategy's own working limit buys of the given stages — an unfilled entry buy
     * without touching its loss-level buys, or the reverse. Unlike {@link #cancelPendingLimitBuys}, which
     * clears every open buy for the symbol, it goes order by order, so other stages and other strategies
     * trading the same symbol are left alone. An order the broker reports as already filled is not
     * marked cancelled; reconciliation records the fill.
     */
    int cancelPendingLimitBuys(Strategy strategy, java.util.Set<com.neuralarc.model.StrategyStage> stages) {
        int canceledCount = 0;
        for (StrategyOrder localOrder : orderRepository.findByStrategyId(strategy.id())) {
            if (!isPendingLimitOrder(localOrder, StrategyOrderSide.BUY) || !stages.contains(localOrder.stage())) {
                continue;
            }
            String orderId = localOrder.alpacaOrderId();
            if (orderId != null && !orderId.isBlank() && !alpacaClient.cancelOrder(orderId)) {
                // Refused: it may have filled or already gone. Only mark it cancelled if it is not working.
                java.util.Optional<com.neuralarc.api.AlpacaOrderData> remote = alpacaClient.getOrder(orderId);
                if (remote.isEmpty() || isPendingLimitOrder(remote.get(), StrategyOrderSide.BUY)
                        || "filled".equals(BrokerOrderStatusUtil.normalize(remote.get().status()))) {
                    continue;
                }
            }
            localOrder.setStatus(StrategyOrderStatus.CANCELED);
            orderRepository.save(localOrder);
            canceledCount++;
        }
        return canceledCount;
    }

    int cancelPendingLimitSells(Strategy strategy) {
        return cancelPendingLimitOrders(strategy, StrategyOrderSide.SELL);
    }

    private int cancelPendingLimitOrders(Strategy strategy, StrategyOrderSide side) {
        int canceledCount = 0;
        List<com.neuralarc.api.AlpacaOrderData> openOrders = alpacaClient.getOpenOrders(strategy.symbol());
        for (com.neuralarc.api.AlpacaOrderData remoteOrder : openOrders) {
            if (!isPendingLimitOrder(remoteOrder, side)) {
                continue;
            }
            if (alpacaClient.cancelOrder(remoteOrder.orderId())) {
                canceledCount++;
                markMatchingLocalOrderCanceled(strategy, remoteOrder, side);
            }
        }

        for (StrategyOrder localOrder : orderRepository.findByStrategyId(strategy.id())) {
            if (!isPendingLimitOrder(localOrder, side)) {
                continue;
            }
            localOrder.setStatus(StrategyOrderStatus.CANCELED);
            orderRepository.save(localOrder);
            canceledCount++;
        }
        return canceledCount;
    }

    private void markMatchingLocalOrderCanceled(
            Strategy strategy,
            com.neuralarc.api.AlpacaOrderData remoteOrder,
            StrategyOrderSide side
    ) {
        for (StrategyOrder localOrder : orderRepository.findByStrategyId(strategy.id())) {
            if (!isPendingLimitOrder(localOrder, side)) {
                continue;
            }
            boolean sameOrder = remoteOrder.orderId().equals(localOrder.alpacaOrderId())
                    || remoteOrder.clientOrderId().equals(localOrder.clientOrderId());
            if (!sameOrder) {
                continue;
            }
            localOrder.setStatus(StrategyOrderStatus.CANCELED);
            localOrder.setRawResponseJson(remoteOrder.rawJson());
            orderRepository.save(localOrder);
        }
    }

    private boolean isPendingLimitOrder(com.neuralarc.api.AlpacaOrderData order, StrategyOrderSide side) {
        if (order == null || !"limit".equalsIgnoreCase(order.type())) {
            return false;
        }
        if (side == StrategyOrderSide.BUY && !"buy".equalsIgnoreCase(order.side())) {
            return false;
        }
        if (side == StrategyOrderSide.SELL && !"sell".equalsIgnoreCase(order.side())) {
            return false;
        }
        String normalized = BrokerOrderStatusUtil.normalize(order.status());
        return !"filled".equals(normalized)
                && !"canceled".equals(normalized)
                && !"cancelled".equals(normalized)
                && !"expired".equals(normalized)
                && !"rejected".equals(normalized)
                && !"failed".equals(normalized);
    }

    private boolean isPendingLimitOrder(StrategyOrder order, StrategyOrderSide side) {
        return order != null
                && order.side() == side
                && order.orderType() == StrategyOrderType.LIMIT
                && order.isPending();
    }
}

