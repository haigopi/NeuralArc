package com.neuralarc.api;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Process-wide counters for outbound broker / market-data HTTP calls.
 *
 * <p>Surfaced in the bottom status bar's network indicator so operators can see API usage at a
 * glance (how many calls were made and how many failed). Counters are recorded from background
 * broker threads and read on the Swing EDT, so all state is kept thread-safe.
 *
 * <p>A call is counted as <em>failed</em> when it could not reach the broker (transport error or
 * timeout) or the broker returned a server error (HTTP 5xx). Ordinary 4xx responses (for example a
 * 404 when a strategy has no open position) are treated as completed calls, not failures, so normal
 * polling does not inflate the failure count.
 */
public final class ApiCallMetrics {
    private static final AtomicLong total = new AtomicLong();
    private static final AtomicLong failed = new AtomicLong();
    private static volatile Instant startedAt = Instant.now();

    private ApiCallMetrics() {
    }

    /** Records one completed HTTP call. {@code success} is false for transport errors or 5xx responses. */
    public static void record(boolean success) {
        total.incrementAndGet();
        if (!success) {
            failed.incrementAndGet();
        }
    }

    public static Snapshot snapshot() {
        long totalCalls = total.get();
        long failedCalls = failed.get();
        return new Snapshot(totalCalls, failedCalls, Math.max(0, totalCalls - failedCalls), startedAt);
    }

    /** Clears the counters and restarts the tracking window. */
    public static void reset() {
        total.set(0);
        failed.set(0);
        startedAt = Instant.now();
    }

    public record Snapshot(long total, long failed, long succeeded, Instant since) {
        public double successRatePercent() {
            return total == 0 ? 100.0 : (succeeded * 100.0) / total;
        }
    }
}
