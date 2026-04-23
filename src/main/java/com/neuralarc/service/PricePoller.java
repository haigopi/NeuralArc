package com.neuralarc.service;

import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class PricePoller {
    private static final int MAX_POOL_SIZE = 100;
    private static final ScheduledThreadPoolExecutor STRATEGY_EXECUTOR =
            (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(MAX_POOL_SIZE);

    static {
        STRATEGY_EXECUTOR.setRemoveOnCancelPolicy(true);
    }

    private ScheduledFuture<?> scheduledTask;

    public void start(int intervalSeconds, Runnable task) {
        stop();
        try {
            scheduledTask = STRATEGY_EXECUTOR.scheduleAtFixedRate(task, 0, intervalSeconds, TimeUnit.SECONDS);
        } catch (RejectedExecutionException ex) {
            throw new IllegalStateException("Strategy execution pool is full or unavailable", ex);
        }
    }

    public void stop() {
        Future<?> task = scheduledTask;
        if (task != null) {
            task.cancel(true);
            scheduledTask = null;
        }
    }

    public static void shutdownExecutor() {
        STRATEGY_EXECUTOR.shutdownNow();
    }
}
