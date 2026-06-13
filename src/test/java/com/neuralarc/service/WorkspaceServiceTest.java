package com.neuralarc.service;

import com.neuralarc.model.ProfitControlMode;
import com.neuralarc.model.ProfitHoldType;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyConfig;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyWorkspace;
import com.neuralarc.model.ThresholdType;
import com.neuralarc.model.TimeInForce;
import com.neuralarc.model.TrailingType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceServiceTest {
    private final InMemoryWorkspaceRepository workspaces = new InMemoryWorkspaceRepository();
    private final InMemoryStrategyRepository strategies = new InMemoryStrategyRepository();
    private final WorkspaceService service = new WorkspaceService(workspaces, strategies);

    @Test
    void createGeneratesUniqueCodePerMode() {
        StrategyWorkspace first = service.create("ORB Engine", StrategyMode.PAPER);
        StrategyWorkspace second = service.create("ORB Engine", StrategyMode.PAPER);
        assertEquals("ORBENGIN", first.code());
        assertFalse(second.code().equalsIgnoreCase(first.code()), "second code must differ");

        // Same code is allowed in a different mode (Paper/Live are isolated).
        StrategyWorkspace live = service.create("ORB Engine", StrategyMode.LIVE);
        assertEquals("ORBENGIN", live.code());
    }

    @Test
    void activeWorkspacesAreModeScoped() {
        service.create("Momentum Lab", StrategyMode.PAPER);
        service.create("VWAP Desk", StrategyMode.LIVE);

        assertEquals(1, service.activeWorkspaces(StrategyMode.PAPER).size());
        assertEquals(1, service.activeWorkspaces(StrategyMode.LIVE).size());
    }

    @Test
    void deleteRemovesAnEmptyWorkspace() {
        StrategyWorkspace workspace = service.create("Empty Book", StrategyMode.PAPER);
        assertEquals(WorkspaceService.DeleteResult.DELETED, service.delete(workspace.id()));
        assertTrue(service.activeWorkspaces(StrategyMode.PAPER).isEmpty());
    }

    @Test
    void deleteRejectsANonEmptyWorkspace() {
        StrategyWorkspace workspace = service.create("Busy Book", StrategyMode.PAPER);
        strategies.save(paperStrategy("s1", StrategyMode.PAPER));
        service.assignStrategy("s1", workspace.id());

        assertEquals(WorkspaceService.DeleteResult.REJECTED_NOT_EMPTY, service.delete(workspace.id()));
        assertEquals(1, service.activeWorkspaces(StrategyMode.PAPER).size()); // still present

        // After moving the strategy out, deletion succeeds.
        service.assignStrategy("s1", null);
        assertEquals(WorkspaceService.DeleteResult.DELETED, service.delete(workspace.id()));
    }

    @Test
    void deleteUnknownWorkspaceReportsNotFound() {
        assertEquals(WorkspaceService.DeleteResult.NOT_FOUND, service.delete("missing"));
    }

    @Test
    void findOrCreateReusesExistingWorkspaceInsteadOfDuplicating() {
        StrategyWorkspace first = service.findOrCreate("ORB Engine", "ORB", StrategyMode.PAPER);
        StrategyWorkspace again = service.findOrCreate("ORB Engine", "ORB", StrategyMode.PAPER);

        assertEquals(first.id(), again.id());                       // same workspace, not a duplicate
        assertEquals(1, service.activeWorkspaces(StrategyMode.PAPER).size());

        // A different mode is isolated, so it does create its own.
        service.findOrCreate("ORB Engine", "ORB", StrategyMode.LIVE);
        assertEquals(1, service.activeWorkspaces(StrategyMode.LIVE).size());
    }

    @Test
    void renameUpdatesName() {
        StrategyWorkspace created = service.create("Old", StrategyMode.PAPER);
        service.rename(created.id(), "New Name");
        assertEquals("New Name", service.findById(created.id()).orElseThrow().name());
    }

    @Test
    void assignStrategySetsAndClearsWorkspace() {
        StrategyWorkspace workspace = service.create("Swing Vault", StrategyMode.PAPER);
        strategies.save(paperStrategy("s1", StrategyMode.PAPER));

        assertTrue(service.assignStrategy("s1", workspace.id()));
        assertEquals(workspace.id(), strategies.findById("s1").orElseThrow().workspaceId());

        assertTrue(service.assignStrategy("s1", null));
        assertNull(strategies.findById("s1").orElseThrow().workspaceId());
    }

    @Test
    void assignStrategyEnforcesModeIsolation() {
        StrategyWorkspace liveWorkspace = service.create("Live Book", StrategyMode.LIVE);
        strategies.save(paperStrategy("s1", StrategyMode.PAPER));

        assertFalse(service.assignStrategy("s1", liveWorkspace.id()), "cross-mode assignment must be rejected");
        assertNull(strategies.findById("s1").orElseThrow().workspaceId());
    }

    @Test
    void assignStrategyRejectsUnknownStrategyOrWorkspace() {
        assertFalse(service.assignStrategy("missing", null));
        strategies.save(paperStrategy("s1", StrategyMode.PAPER));
        assertFalse(service.assignStrategy("s1", "missing-workspace"));
    }

    private Strategy paperStrategy(String id, StrategyMode mode) {
        return Strategy.fromConfig(id, "Test", config(), mode);
    }

    private StrategyConfig config() {
        return new StrategyConfig(
                "NEO", new BigDecimal("8.00"), 10, true, new BigDecimal("7.00"), true,
                new BigDecimal("10.00"), new BigDecimal("7.40"), 5, new BigDecimal("6.40"), 5,
                true, false, BigDecimal.ZERO, 2, true, false, false, ProfitHoldType.PERCENT_TRAILING,
                BigDecimal.ZERO, BigDecimal.ZERO, false, ProfitControlMode.NONE, ThresholdType.FIXED_AMOUNT,
                BigDecimal.ZERO, TrailingType.PERCENTAGE, BigDecimal.ZERO, true, new BigDecimal("5.00"),
                TimeInForce.GTC
        );
    }

    private static final class InMemoryWorkspaceRepository implements WorkspaceRepository {
        private final Map<String, StrategyWorkspace> store = new LinkedHashMap<>();

        @Override public void save(StrategyWorkspace workspace) { store.put(workspace.id(), workspace); }
        @Override public Optional<StrategyWorkspace> findById(String id) { return Optional.ofNullable(store.get(id)); }
        @Override public List<StrategyWorkspace> findAll() { return new ArrayList<>(store.values()); }

        @Override public List<StrategyWorkspace> findByMode(StrategyMode mode) {
            List<StrategyWorkspace> result = new ArrayList<>();
            for (StrategyWorkspace w : store.values()) {
                if (w.mode() == mode) {
                    result.add(w);
                }
            }
            return result;
        }

        @Override public List<StrategyWorkspace> findActive(StrategyMode mode) {
            List<StrategyWorkspace> result = new ArrayList<>();
            for (StrategyWorkspace w : store.values()) {
                if (w.mode() == mode && !w.archived()) {
                    result.add(w);
                }
            }
            return result;
        }

        @Override public void deleteById(String id) { store.remove(id); }
    }

    private static final class InMemoryStrategyRepository implements StrategyRepository {
        private final Map<String, Strategy> store = new LinkedHashMap<>();

        @Override public void save(Strategy strategy) { store.put(strategy.id(), strategy); }
        @Override public Optional<Strategy> findById(String id) { return Optional.ofNullable(store.get(id)); }
        @Override public List<Strategy> findAll() { return new ArrayList<>(store.values()); }
        @Override public List<Strategy> findActive() { return findAll(); }
        @Override public void deleteById(String id) { store.remove(id); }
    }
}
