package com.neuralarc.ui;

import com.neuralarc.model.ApplicationMode;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

final class AvailableFundsStatusState {
    static final String EMPTY_TEXT = "Funds Available: -";

    private final Map<ApplicationMode, String> textByMode = new EnumMap<>(ApplicationMode.class);
    private final Map<ApplicationMode, Long> lastFetchAtMillisByMode = new EnumMap<>(ApplicationMode.class);

    synchronized String textFor(ApplicationMode mode) {
        return textByMode.getOrDefault(safeMode(mode), EMPTY_TEXT);
    }

    synchronized boolean shouldFetch(ApplicationMode mode, long nowMillis, long refreshIntervalMillis) {
        long lastFetchAtMillis = lastFetchAtMillisByMode.getOrDefault(safeMode(mode), 0L);
        return nowMillis - lastFetchAtMillis >= refreshIntervalMillis;
    }

    synchronized void markFetchStarted(ApplicationMode mode, long nowMillis) {
        lastFetchAtMillisByMode.put(safeMode(mode), nowMillis);
    }

    synchronized String update(ApplicationMode mode, Optional<BigDecimal> funds) {
        String text = funds
                .map(value -> "Funds Available: $" + value.toPlainString())
                .orElse(EMPTY_TEXT);
        textByMode.put(safeMode(mode), text);
        return text;
    }

    synchronized String clear(ApplicationMode mode) {
        ApplicationMode safeMode = safeMode(mode);
        textByMode.put(safeMode, EMPTY_TEXT);
        lastFetchAtMillisByMode.remove(safeMode);
        return EMPTY_TEXT;
    }

    private ApplicationMode safeMode(ApplicationMode mode) {
        return mode == null ? ApplicationMode.PAPER : mode;
    }
}
