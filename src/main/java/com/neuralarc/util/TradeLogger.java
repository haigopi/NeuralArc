package com.neuralarc.util;

import java.time.Instant;
import java.util.function.Consumer;

public class TradeLogger {
    private final Consumer<String> sink;

    public TradeLogger(Consumer<String> sink) {
        this.sink = sink;
    }

    public void log(String message) {
        sink.accept("[" + Instant.now() + "] " + message);
    }
}
