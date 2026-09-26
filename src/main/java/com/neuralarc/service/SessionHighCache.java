package com.neuralarc.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The highest price seen for a symbol today, from every part of the app that sees one.
 *
 * <p>The strategy engine only ever sees the price at the instant it polls, roughly once a minute.
 * A stock that touches its high between two polls is invisible to it, which is how a profit hold
 * armed at $385 never noticed a $386.67 print. The grid already loads each symbol's daily bar for
 * its "Today's High" column, so the true session high is in the app — it just never reached the
 * engine. This is where the two meet: the UI records the bar's high, the engine records every price
 * it polls, and the profit hold arms off whichever is higher.
 *
 * <p>Yesterday's high must never arm today's hold, so every entry carries its trading day and a
 * stale one reads as unknown.
 */
public final class SessionHighCache {
    private static final SessionHighCache SHARED = new SessionHighCache();

    private record Entry(LocalDate day, BigDecimal high) {
    }

    private final Map<String, Entry> highs = new ConcurrentHashMap<>();

    /** The instance the running app shares; tests build their own. */
    public static SessionHighCache shared() {
        return SHARED;
    }

    /** Records a price or bar high seen for {@code symbol} on {@code day}; lower values are ignored. */
    public void record(String symbol, LocalDate day, BigDecimal price) {
        if (symbol == null || symbol.isBlank() || day == null || price == null || price.signum() <= 0) {
            return;
        }
        highs.merge(key(symbol), new Entry(day, price),
                (existing, candidate) -> !existing.day().equals(candidate.day())
                        || candidate.high().compareTo(existing.high()) > 0 ? candidate : existing);
    }

    /** The high recorded for {@code symbol} on {@code today}, or zero when nothing is known. */
    public BigDecimal highFor(String symbol, LocalDate today) {
        if (symbol == null || today == null) {
            return BigDecimal.ZERO;
        }
        Entry entry = highs.get(key(symbol));
        return entry == null || !entry.day().equals(today) ? BigDecimal.ZERO : entry.high();
    }

    /**
     * Forgets every recorded high. The running app never needs this — the day check already retires
     * stale entries — but a test must not inherit the prices another test recorded into the shared
     * instance.
     */
    public void clear() {
        highs.clear();
    }

    private static String key(String symbol) {
        return symbol.trim().toUpperCase(Locale.ROOT);
    }
}
