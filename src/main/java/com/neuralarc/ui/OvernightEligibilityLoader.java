package com.neuralarc.ui;

import com.neuralarc.api.HttpAlpacaClient;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyMode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Logger;

final class OvernightEligibilityLoader {
    private static final Logger LOGGER = Logger.getLogger(OvernightEligibilityLoader.class.getName());
    private static final int MAX_THREADS = 6;

    private OvernightEligibilityLoader() {}

    static Map<String, Boolean> load(
            List<Strategy> strategies,
            Function<StrategyMode, HttpAlpacaClient> clientResolver
    ) {
        if (strategies == null || strategies.isEmpty() || clientResolver == null) {
            return Map.of();
        }
        Map<StrategyKey, StrategyKey> uniqueKeys = new LinkedHashMap<>();
        Map<String, StrategyKey> strategyIdToKey = new LinkedHashMap<>();
        for (Strategy strategy : strategies) {
            if (strategy == null || strategy.id() == null || strategy.id().isBlank()
                    || strategy.symbol() == null || strategy.symbol().isBlank()) {
                continue;
            }
            StrategyKey key = new StrategyKey(strategy.mode(), strategy.symbol().trim().toUpperCase(Locale.ROOT));
            uniqueKeys.putIfAbsent(key, key);
            strategyIdToKey.put(strategy.id(), key);
        }
        if (uniqueKeys.isEmpty()) {
            return Map.of();
        }
        ExecutorService executor = Executors.newFixedThreadPool(threadCount(uniqueKeys.size()), runnable -> {
            Thread thread = new Thread(runnable, "neuralarc-overnight-eligibility");
            thread.setDaemon(true);
            return thread;
        });
        try {
            Map<StrategyKey, CompletableFuture<Boolean>> futuresByKey = new LinkedHashMap<>();
            for (StrategyKey key : uniqueKeys.keySet()) {
                futuresByKey.put(key, CompletableFuture.supplyAsync(() -> supportsOvernightSession(key, clientResolver), executor));
            }
            Map<StrategyKey, Boolean> eligibilityByKey = new LinkedHashMap<>();
            for (Map.Entry<StrategyKey, CompletableFuture<Boolean>> entry : futuresByKey.entrySet()) {
                eligibilityByKey.put(entry.getKey(), entry.getValue().join());
            }
            Map<String, Boolean> byStrategyId = new LinkedHashMap<>();
            for (Map.Entry<String, StrategyKey> entry : strategyIdToKey.entrySet()) {
                byStrategyId.put(entry.getKey(), eligibilityByKey.getOrDefault(entry.getValue(), false));
            }
            return byStrategyId;
        } finally {
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    LOGGER.warning("Timed out waiting for overnight eligibility workers to stop.");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }

    static int threadCount(int taskCount) {
        return Math.min(taskCount, Math.max(2, Math.min(MAX_THREADS, Runtime.getRuntime().availableProcessors())));
    }

    private static boolean supportsOvernightSession(
            StrategyKey key,
            Function<StrategyMode, HttpAlpacaClient> clientResolver
    ) {
        HttpAlpacaClient client = clientResolver.apply(key.mode());
        return client != null && client.supportsOvernightSession(key.symbol());
    }

    private record StrategyKey(StrategyMode mode, String symbol) {
        private StrategyKey {
            symbol = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
            Objects.requireNonNull(mode, "mode");
        }
    }
}
