package com.neuralarc.service;

import com.neuralarc.api.AlpacaClient;
import com.neuralarc.api.AlpacaOrderData;
import com.neuralarc.api.AlpacaPositionData;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyOrder;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Resolves position/open-orders/order-status data from a shared {@link BrokerSnapshotBatch}
 * when one is available for the current poll cycle, falling back to the original per-strategy
 * broker calls when it isn't (batch fetch failed, or the caller is outside the batched due-cycle
 * — e.g. a manual single-strategy poll).
 */
final class BrokerSnapshotResolver {
    private BrokerSnapshotResolver() {
    }

    static Optional<AlpacaPositionData> resolvePosition(AlpacaClient client, Strategy strategy, BrokerSnapshotBatch batch) {
        if (batch == null) {
            return client.getPosition(strategy.symbol());
        }
        return Optional.ofNullable(batch.positionsBySymbol().get(symbolKey(strategy.symbol())));
    }

    static List<AlpacaOrderData> resolveOpenOrders(AlpacaClient client, Strategy strategy, BrokerSnapshotBatch batch) {
        if (batch == null) {
            return client.getOpenOrders(strategy.symbol());
        }
        return batch.openOrdersBySymbol().getOrDefault(symbolKey(strategy.symbol()), List.of());
    }

    /**
     * An order still present in the shared open-orders snapshot hasn't changed since last cycle,
     * so the individual getOrder() call is skipped entirely — the common case, every cycle, for
     * every order that hasn't filled yet. Only an order that dropped out of the open set (it
     * transitioned — filled/canceled/expired/rejected) is worth an individual fetch to learn why.
     */
    static Optional<AlpacaOrderData> resolveOrderIfChanged(AlpacaClient client, StrategyOrder order, BrokerSnapshotBatch batch) {
        if (batch == null) {
            return client.getOrder(order.alpacaOrderId());
        }
        if (batch.openOrderIds().contains(order.alpacaOrderId())) {
            return Optional.empty();
        }
        return client.getOrder(order.alpacaOrderId());
    }

    private static String symbolKey(String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
    }
}
