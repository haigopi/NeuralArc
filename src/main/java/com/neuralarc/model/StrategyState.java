package com.neuralarc.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public class StrategyState {
    private final EnumSet<RuleType> triggered = EnumSet.noneOf(RuleType.class);
    private boolean stopActivated;

    public synchronized boolean isTriggered(RuleType rule) {
        return triggered.contains(rule);
    }

    public synchronized boolean markTriggered(RuleType rule) {
        return triggered.add(rule);
    }

    public synchronized void setStopActivated(boolean stopActivated) {
        this.stopActivated = stopActivated;
    }

    public synchronized boolean isStopActivated() {
        return stopActivated;
    }

    public synchronized void reset() {
        triggered.clear();
        stopActivated = false;
    }

    public synchronized Set<RuleType> triggeredRules() {
        return Collections.unmodifiableSet(triggered.clone());
    }
}
