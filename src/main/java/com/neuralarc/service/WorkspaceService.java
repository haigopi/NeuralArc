package com.neuralarc.service;

import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyWorkspace;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Create / rename / archive strategy workspaces and assign strategies to them.
 *
 * <p>Honors Paper/Live isolation: workspaces are mode-scoped and a strategy can only join a
 * workspace of its own mode. "Delete" in the UI maps to {@link #archive(String)} so historical
 * records are never lost — archived workspaces are simply hidden (no active tab).
 */
public final class WorkspaceService {
    private static final int MAX_CODE_LENGTH = 8;

    private final WorkspaceRepository workspaceRepository;
    private final StrategyRepository strategyRepository;

    public WorkspaceService(WorkspaceRepository workspaceRepository, StrategyRepository strategyRepository) {
        this.workspaceRepository = workspaceRepository;
        this.strategyRepository = strategyRepository;
    }

    public StrategyWorkspace create(String name, StrategyMode mode) {
        return create(name, StrategyWorkspace.normalizeCode(name), mode);
    }

    public StrategyWorkspace create(String name, String desiredCode, StrategyMode mode) {
        StrategyMode safeMode = mode == null ? StrategyMode.PAPER : mode;
        String codeSeed = desiredCode == null || desiredCode.isBlank() ? name : desiredCode;
        String code = uniqueCode(codeSeed, safeMode);
        String displayName = name == null || name.isBlank() ? code : name.trim();
        StrategyWorkspace workspace = new StrategyWorkspace(
                UUID.randomUUID().toString(), displayName, code, safeMode, false, Instant.now(), Instant.now());
        workspaceRepository.save(workspace);
        return workspace;
    }

    public Optional<StrategyWorkspace> rename(String id, String newName) {
        if (newName == null || newName.isBlank()) {
            return Optional.empty();
        }
        return workspaceRepository.findById(id).map(existing -> {
            StrategyWorkspace renamed = existing.withName(newName);
            workspaceRepository.save(renamed);
            return renamed;
        });
    }

    /** Archive (the UI "delete"): hides the workspace and removes its tab, retaining all records. */
    public Optional<StrategyWorkspace> archive(String id) {
        return setArchived(id, true);
    }

    public Optional<StrategyWorkspace> unarchive(String id) {
        return setArchived(id, false);
    }

    private Optional<StrategyWorkspace> setArchived(String id, boolean archived) {
        return workspaceRepository.findById(id).map(existing -> {
            StrategyWorkspace updated = existing.withArchived(archived);
            workspaceRepository.save(updated);
            return updated;
        });
    }

    /** Non-archived workspaces for the mode — what the dynamic tabs render. */
    public List<StrategyWorkspace> activeWorkspaces(StrategyMode mode) {
        return workspaceRepository.findActive(mode);
    }

    public List<StrategyWorkspace> allWorkspaces(StrategyMode mode) {
        return workspaceRepository.findByMode(mode);
    }

    public Optional<StrategyWorkspace> findById(String id) {
        return id == null ? Optional.empty() : workspaceRepository.findById(id);
    }

    /**
     * Assigns a strategy to a workspace, or clears it when {@code workspaceId} is {@code null}.
     * Returns false (no change) if the strategy is missing, the workspace is missing, or the
     * workspace belongs to a different mode (Paper/Live isolation).
     */
    public boolean assignStrategy(String strategyId, String workspaceId) {
        Optional<Strategy> strategyOpt = strategyRepository.findById(strategyId);
        if (strategyOpt.isEmpty()) {
            return false;
        }
        Strategy strategy = strategyOpt.get();
        if (workspaceId != null) {
            Optional<StrategyWorkspace> workspaceOpt = workspaceRepository.findById(workspaceId);
            if (workspaceOpt.isEmpty() || workspaceOpt.get().mode() != strategy.mode()) {
                return false;
            }
        }
        strategy.setWorkspaceId(workspaceId);
        strategyRepository.save(strategy);
        return true;
    }

    private String uniqueCode(String desired, StrategyMode mode) {
        String base = StrategyWorkspace.normalizeCode(desired);
        List<StrategyWorkspace> existing = workspaceRepository.findByMode(mode);
        if (!codeTaken(existing, base)) {
            return base;
        }
        for (int suffix = 2; suffix < 1000; suffix++) {
            String candidate = truncateForSuffix(base, suffix) + suffix;
            if (!codeTaken(existing, candidate)) {
                return candidate;
            }
        }
        return base + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }

    private boolean codeTaken(List<StrategyWorkspace> existing, String code) {
        return existing.stream().anyMatch(w -> w.code().equalsIgnoreCase(code));
    }

    private String truncateForSuffix(String base, int suffix) {
        int room = MAX_CODE_LENGTH - String.valueOf(suffix).length();
        return base.length() > room ? base.substring(0, Math.max(1, room)) : base;
    }
}
