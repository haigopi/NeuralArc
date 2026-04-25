package com.neuralarc.service;

import com.neuralarc.model.StrategyExecutionEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class StrategyEventBus {
    private final List<Consumer<StrategyExecutionEvent>> listeners = new CopyOnWriteArrayList<>();

    public void subscribe(Consumer<StrategyExecutionEvent> listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void publish(StrategyExecutionEvent event) {
        for (Consumer<StrategyExecutionEvent> listener : listeners) {
            listener.accept(event);
        }
    }
}
