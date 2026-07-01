package com.neuralarc.ui;

import com.neuralarc.model.ProfitControlMode;
import com.neuralarc.model.ProfitHoldType;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyConfig;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyWorkspace;
import com.neuralarc.model.ThresholdType;
import com.neuralarc.model.TimeInForce;
import com.neuralarc.model.TrailingType;
import com.neuralarc.service.StrategyRepository;
import com.neuralarc.service.WorkspaceRepository;
import com.neuralarc.service.WorkspaceService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NewStrategyWorkspaceAssignmentTest {
    @Test
    void assignsSelectedWorkspaceWhenModesMatch() {
        StrategyWorkspace workspace = new StrategyWorkspace(
                "paper-1", "NIO Playground", "NIOPLAY", StrategyMode.PAPER, false, Instant.now(), Instant.now());
        WorkspaceService workspaceService = workspaceService(List.of(workspace));
        Strategy strategy = strategy(StrategyMode.PAPER);

        NewStrategyWorkspaceAssignment.apply(strategy, workspace.id(), workspaceService);

        assertEquals(workspace.id(), strategy.workspaceId());
    }

    @Test
    void ignoresSelectedWorkspaceWhenModesDoNotMatch() {
        StrategyWorkspace workspace = new StrategyWorkspace(
                "live-1", "NIO Playground", "NIOPLAY", StrategyMode.LIVE, false, Instant.now(), Instant.now());
        WorkspaceService workspaceService = workspaceService(List.of(workspace));
        Strategy strategy = strategy(StrategyMode.PAPER);

        NewStrategyWorkspaceAssignment.apply(strategy, workspace.id(), workspaceService);

        assertNull(strategy.workspaceId());
    }

    @Test
    void ignoresBlankSelection() {
        WorkspaceService workspaceService = workspaceService(List.of());
        Strategy strategy = strategy(StrategyMode.PAPER);

        NewStrategyWorkspaceAssignment.apply(strategy, " ", workspaceService);

        assertNull(strategy.workspaceId());
    }

    private WorkspaceService workspaceService(List<StrategyWorkspace> workspaces) {
        return new WorkspaceService(new InMemoryWorkspaceRepository(workspaces), new EmptyStrategyRepository());
    }

    private Strategy strategy(StrategyMode mode) {
        return Strategy.fromConfig("strategy-1", "NIO Strategy", new StrategyConfig(
                "NIO",
                new BigDecimal("4.50"),
                10,
                true,
                new BigDecimal("4.10"),
                true,
                new BigDecimal("5.20"),
                new BigDecimal("4.25"),
                5,
                new BigDecimal("4.00"),
                5,
                true,
                false,
                BigDecimal.ZERO,
                30,
                mode == StrategyMode.PAPER,
                false,
                false,
                ProfitHoldType.PERCENT_TRAILING,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                false,
                ProfitControlMode.NONE,
                ThresholdType.FIXED_AMOUNT,
                BigDecimal.ZERO,
                TrailingType.PERCENTAGE,
                BigDecimal.ZERO,
                true,
                new BigDecimal("5.00"),
                TimeInForce.GTC
        ), mode);
    }

    private static final class InMemoryWorkspaceRepository implements WorkspaceRepository {
        private final Map<String, StrategyWorkspace> store = new LinkedHashMap<>();

        private InMemoryWorkspaceRepository(List<StrategyWorkspace> workspaces) {
            for (StrategyWorkspace workspace : workspaces) {
                store.put(workspace.id(), workspace);
            }
        }

        @Override public void save(StrategyWorkspace workspace) { store.put(workspace.id(), workspace); }
        @Override public Optional<StrategyWorkspace> findById(String id) { return Optional.ofNullable(store.get(id)); }
        @Override public List<StrategyWorkspace> findAll() { return new ArrayList<>(store.values()); }

        @Override public List<StrategyWorkspace> findByMode(StrategyMode mode) {
            return store.values().stream().filter(workspace -> workspace.mode() == mode).toList();
        }

        @Override public List<StrategyWorkspace> findActive(StrategyMode mode) {
            return store.values().stream()
                    .filter(workspace -> workspace.mode() == mode && !workspace.archived())
                    .toList();
        }

        @Override public void deleteById(String id) { store.remove(id); }
    }

    private static final class EmptyStrategyRepository implements StrategyRepository {
        @Override public void save(Strategy strategy) { }
        @Override public Optional<Strategy> findById(String id) { return Optional.empty(); }
        @Override public List<Strategy> findAll() { return List.of(); }
        @Override public List<Strategy> findActive() { return List.of(); }
        @Override public void deleteById(String id) { }
    }
}
