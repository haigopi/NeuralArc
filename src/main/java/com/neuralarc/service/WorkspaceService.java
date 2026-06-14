package com.neuralarc.service;

import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyWorkspace;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Create / rename / delete strategy workspaces and assign strategies to them.
 *
 * <p>Honors Paper/Live isolation: workspaces are mode-scoped and a strategy can only join a
 * workspace of its own mode. "Delete" in the UI maps to {@link #archive(String)} so historical
 * records are never lost — archived workspaces are simply hidden (no active tab).
 */
public final class WorkspaceService {
    private static final int MAX_CODE_LENGTH = 9;

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

    /**
     * Returns the existing active workspace matching the desired code or name in this mode, or
     * creates one if none exists. This keeps repeated Smart Picks template clicks from minting
     * duplicate workspaces/tabs — the caller can just select the returned workspace's tab.
     */
    public StrategyWorkspace findOrCreate(String name, String desiredCode, StrategyMode mode) {
        StrategyMode safeMode = mode == null ? StrategyMode.PAPER : mode;
        String codeSeed = desiredCode == null || desiredCode.isBlank() ? name : desiredCode;
        String normalizedCode = StrategyWorkspace.normalizeCode(codeSeed);
        String trimmedName = name == null ? "" : name.trim();
        return workspaceRepository.findActive(safeMode).stream()
                .filter(existing -> existing.code().equalsIgnoreCase(normalizedCode)
                        || (!trimmedName.isBlank() && existing.name().equalsIgnoreCase(trimmedName)))
                .findFirst()
                .orElseGet(() -> create(name, desiredCode, safeMode));
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

    /**
     * Deletes a workspace, but only when it owns no strategies. A non-empty workspace is rejected
     * (the operator must move/remove its strategies first) so trade records are never orphaned.
     */
    public DeleteResult delete(String id) {
        if (workspaceRepository.findById(id).isEmpty()) {
            return DeleteResult.NOT_FOUND;
        }
        if (!strategiesIn(id).isEmpty()) {
            return DeleteResult.REJECTED_NOT_EMPTY;
        }
        workspaceRepository.deleteById(id);
        return DeleteResult.DELETED;
    }

    /** Strategies currently assigned to a workspace. */
    public List<Strategy> strategiesIn(String workspaceId) {
        List<Strategy> result = new ArrayList<>();
        if (workspaceId == null) {
            return result;
        }
        for (Strategy strategy : strategyRepository.findAll()) {
            if (workspaceId.equals(strategy.workspaceId())) {
                result.add(strategy);
            }
        }
        return result;
    }

    /** Workspaces shown as tabs for the mode. */
    public List<StrategyWorkspace> activeWorkspaces(StrategyMode mode) {
        return workspaceRepository.findActive(mode);
    }

    public enum DeleteResult {
        DELETED,
        REJECTED_NOT_EMPTY,
        NOT_FOUND
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
