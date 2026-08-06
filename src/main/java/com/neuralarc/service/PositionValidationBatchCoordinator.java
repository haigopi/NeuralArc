package com.neuralarc.service;

import com.neuralarc.api.AlpacaClient;
import com.neuralarc.api.AlpacaOrderData;
import com.neuralarc.api.AlpacaPositionData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Fetches one account-wide {@link BrokerSnapshotBatch} per poll cycle (all positions + all open
 * orders in two bulk calls) so due strategies share a single broker round-trip instead of each
 * making its own getPosition/getOpenOrders calls. The two calls run concurrently — run
 * sequentially they would roughly double the time the once-per-second scheduling loop spends
 * blocked on broker I/O before it can dispatch any strategy's poll.
 */
final class PositionValidationBatchCoordinator {
    private static final Logger LOGGER = Logger.getLogger(PositionValidationBatchCoordinator.class.getName());
    private static final long FETCH_TIMEOUT_SECONDS = 12L;

    private final AlpacaClient alpacaClient;
    private final ExecutorService fetchExecutor;

    PositionValidationBatchCoordinator(AlpacaClient alpacaClient) {
        this.alpacaClient = alpacaClient;
        this.fetchExecutor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "neuralarc-snapshot-batch");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Never returns a half-populated batch — an empty-but-non-null batch would be
     * indistinguishable from "confirmed no positions exist," which is actively dangerous for a
     * trading app. Any fetch failure or timeout returns {@code null}, which triggers the existing
     * per-strategy fallback path (via {@link BrokerSnapshotResolver}) for that cycle only.
     */
    BrokerSnapshotBatch fetchSnapshotBatchOrNull() {
        try {
            CompletableFuture<List<AlpacaPositionData>> positionsFuture =
                    CompletableFuture.supplyAsync(alpacaClient::getPositions, fetchExecutor);
            CompletableFuture<List<AlpacaOrderData>> openOrdersFuture =
                    CompletableFuture.supplyAsync(alpacaClient::getOpenOrders, fetchExecutor);

            List<AlpacaPositionData> positions = positionsFuture.get(FETCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            List<AlpacaOrderData> openOrders = openOrdersFuture.get(FETCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            Map<String, AlpacaPositionData> positionsBySymbol = new LinkedHashMap<>();
            for (AlpacaPositionData position : positions) {
                positionsBySymbol.put(symbolKey(position.symbol()), position);
            }

            Map<String, List<AlpacaOrderData>> openOrdersBySymbol = new LinkedHashMap<>();
            Set<String> openOrderIds = new LinkedHashSet<>();
            for (AlpacaOrderData order : openOrders) {
                openOrdersBySymbol.computeIfAbsent(symbolKey(order.symbol()), key -> new ArrayList<>()).add(order);
                if (!order.orderId().isBlank()) {
                    openOrderIds.add(order.orderId());
                }
            }

            LOGGER.info(() -> "[POLL][BATCH] combined position+order snapshot fetch: "
                    + positions.size() + " position(s), " + openOrders.size() + " open order(s)");
            return new BrokerSnapshotBatch(positionsBySymbol, openOrdersBySymbol, openOrderIds);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "[POLL][BATCH] combined snapshot fetch failed or timed out, falling back to per-strategy calls this cycle", ex);
            return null;
        }
    }

    void shutdown() {
        fetchExecutor.shutdownNow();
    }

    private static String symbolKey(String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
    }
}
