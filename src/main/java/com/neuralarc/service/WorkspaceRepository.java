package com.neuralarc.service;

import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyWorkspace;

import java.util.List;
import java.util.Optional;

/**
 * Persistence for {@link StrategyWorkspace}s. Mirrors {@link StrategyRepository} so the workspace
 * grouping reuses the same in-memory-cache + SQLite pattern.
 */
public interface WorkspaceRepository {
    void save(StrategyWorkspace workspace);

    Optional<StrategyWorkspace> findById(String id);

    List<StrategyWorkspace> findAll();

    /** All workspaces in a mode (Paper/Live isolation), archived or not. */
    List<StrategyWorkspace> findByMode(StrategyMode mode);

    /** Non-archived workspaces in a mode — what the dynamic tabs render. */
    List<StrategyWorkspace> findActive(StrategyMode mode);

    void deleteById(String id);
}
