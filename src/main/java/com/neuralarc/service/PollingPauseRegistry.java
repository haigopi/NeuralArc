package com.neuralarc.service;

import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyMode;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Which workspaces the operator has paused polling for.
 *
 * <p>A pause is a switch on the polling loop, not a change to any strategy: statuses, orders and
 * lifecycle states stay as they are, so resuming simply starts polling the same rows again. It is
 * held in memory on purpose. A pause is temporary, and one that outlived a restart would leave
 * positions unwatched with nothing on screen to say why.
 *
 * <p>A scope is a mode plus a workspace id. A {@code null} workspace id is the All Stocks view and
 * covers every workspace in that mode. Pausing it pauses everything; resuming it clears the mode's
 * individual pauses too, so nothing stays paused behind an "everything is running" screen.
 */
public final class PollingPauseRegistry {
    private final Set<Scope> paused = Collections.synchronizedSet(new LinkedHashSet<>());

    /** A mode and a workspace; a null workspace id means every workspace in the mode. */
    public record Scope(StrategyMode mode, String workspaceId) {
        public Scope {
            workspaceId = workspaceId == null || workspaceId.isBlank() ? null : workspaceId.trim();
        }
    }

    public void pause(StrategyMode mode, String workspaceId) {
        if (mode != null) {
            paused.add(new Scope(mode, workspaceId));
        }
    }

    /** Resuming All Stocks resumes every workspace in the mode; resuming one workspace leaves the rest. */
    public void resume(StrategyMode mode, String workspaceId) {
        if (mode == null) {
            return;
        }
        Scope scope = new Scope(mode, workspaceId);
        synchronized (paused) {
            if (scope.workspaceId() == null) {
                paused.removeIf(existing -> existing.mode() == mode);
            } else {
                paused.remove(scope);
            }
        }
    }

    /** True when polling is paused for this scope, either directly or because All Stocks is paused. */
    public boolean isPaused(StrategyMode mode, String workspaceId) {
        if (mode == null) {
            return false;
        }
        Scope scope = new Scope(mode, workspaceId);
        return paused.contains(scope) || paused.contains(new Scope(mode, null));
    }

    /** True only when this exact scope was paused, not inherited from All Stocks. */
    public boolean isPausedDirectly(StrategyMode mode, String workspaceId) {
        return mode != null && paused.contains(new Scope(mode, workspaceId));
    }

    public boolean isPaused(Strategy strategy) {
        return strategy != null && isPaused(strategy.mode(), strategy.workspaceId());
    }

    public boolean isAnyPaused() {
        return !paused.isEmpty();
    }
}
