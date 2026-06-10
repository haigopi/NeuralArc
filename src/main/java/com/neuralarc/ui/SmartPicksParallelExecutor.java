package com.neuralarc.ui;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

final class SmartPicksParallelExecutor {
    private static final Logger LOGGER = Logger.getLogger(SmartPicksParallelExecutor.class.getName());
    private static final int MAX_THREADS = 6;

    private SmartPicksParallelExecutor() {}

    static <T, R> List<R> mapPreservingOrder(
            List<T> inputs,
            String threadName,
            Function<T, R> mapper,
            IntConsumer completedCallback
    ) {
        if (inputs == null || inputs.isEmpty()) {
            return List.of();
        }
        int threadCount = threadCount(inputs.size());
        ExecutorService executor = Executors.newFixedThreadPool(threadCount, runnable -> {
            Thread thread = new Thread(runnable, threadName);
            thread.setDaemon(true);
            return thread;
        });
        AtomicInteger completed = new AtomicInteger();
        try {
            List<CompletableFuture<R>> futures = inputs.stream()
                    .map(input -> CompletableFuture.supplyAsync(() -> mapper.apply(input), executor)
                            .whenComplete((ignored, thrown) -> {
                                int completedCount = completed.incrementAndGet();
                                if (completedCallback != null) {
                                    completedCallback.accept(completedCount);
                                }
                            }))
                    .toList();
            return futures.stream()
                    .map(SmartPicksParallelExecutor::join)
                    .toList();
        } finally {
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    LOGGER.warning("Timed out waiting for Smart Picks parallel workers to stop.");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }

    static int threadCount(int taskCount) {
        return Math.min(taskCount, Math.max(2, Math.min(MAX_THREADS, Runtime.getRuntime().availableProcessors())));
    }

    private static <R> R join(CompletableFuture<R> future) {
        try {
            return future.join();
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw ex;
        } catch (RuntimeException ex) {
            LOGGER.log(Level.FINE, "Smart Picks parallel worker failed", ex);
            throw ex;
        }
    }
}
