package com.neuralarc.service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PricePoller {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public void start(int intervalSeconds, Runnable task) {
        scheduler.scheduleAtFixedRate(task, 0, intervalSeconds, TimeUnit.SECONDS);
    }

    public void stop() {
        scheduler.shutdownNow();
    }
}
