package com.neuralarc.service;

import com.neuralarc.api.AlpacaOrderData;
import com.neuralarc.api.AlpacaPositionData;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One account-wide broker snapshot (all positions + all open orders) fetched once per poll
 * cycle and shared across every due strategy, replacing per-strategy getPosition/getOpenOrders
 * calls. An absent map entry means "no position/open orders for that symbol" — both source
 * calls are account-wide, so there is no partial-coverage ambiguity.
 */
record BrokerSnapshotBatch(
        Map<String, AlpacaPositionData> positionsBySymbol,
        Map<String, List<AlpacaOrderData>> openOrdersBySymbol,
        Set<String> openOrderIds
) {
    static final BrokerSnapshotBatch EMPTY = new BrokerSnapshotBatch(Map.of(), Map.of(), Set.of());
}
