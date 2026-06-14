package com.neuralarc.ui;

import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyWorkspace;
import com.neuralarc.service.StrategyRepository;
import com.neuralarc.service.WorkspaceRepository;
import com.neuralarc.service.WorkspaceService;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class StrategyWorkspaceTabsTest {
    @Test
    void gapRocketTabUsesMeaningfulTooltip() throws Exception {
        InMemoryWorkspaceRepository workspaceRepository = new InMemoryWorkspaceRepository();
        StrategyWorkspace gapRocket = new StrategyWorkspace("w1", "Gap Rocket", "GAPROCKET", StrategyMode.PAPER, false, Instant.now(), Instant.now());
        workspaceRepository.save(gapRocket);
        WorkspaceService service = new WorkspaceService(workspaceRepository, new EmptyStrategyRepository());
        JTabbedPane tabs = new JTabbedPane();

        SwingUtilities.invokeAndWait(() -> new StrategyWorkspaceTabs(
                tabs,
                new JPanel(),
                new JPanel(),
                service,
                () -> StrategyMode.PAPER,
                ignored -> { },
                () -> "All Stocks",
                () -> "Trade History"
        ));

        String tooltip = tabs.getToolTipTextAt(1);
        assertNotNull(tooltip);
        assertTrue(tooltip.contains("premarket gap-up stocks"));
        assertTrue(tooltip.contains("VWAP-pullback"));
    }

    private static final class InMemoryWorkspaceRepository implements WorkspaceRepository {
        private final List<StrategyWorkspace> workspaces = new ArrayList<>();
        @Override public synchronized void save(StrategyWorkspace workspace) { workspaces.removeIf(w -> w.id().equals(workspace.id())); workspaces.add(workspace); }
        @Override public synchronized List<StrategyWorkspace> findAll() { return List.copyOf(workspaces); }
        @Override public synchronized List<StrategyWorkspace> findByMode(StrategyMode mode) { return workspaces.stream().filter(w -> w.mode() == mode).toList(); }
        @Override public synchronized List<StrategyWorkspace> findActive(StrategyMode mode) { return findByMode(mode).stream().filter(w -> !w.archived()).toList(); }
        @Override public synchronized Optional<StrategyWorkspace> findById(String id) { return workspaces.stream().filter(w -> w.id().equals(id)).findFirst(); }
        @Override public synchronized void deleteById(String id) { workspaces.removeIf(w -> w.id().equals(id)); }
    }

    private static final class EmptyStrategyRepository implements StrategyRepository {
        @Override public void save(Strategy strategy) { }
        @Override public List<Strategy> findAll() { return List.of(); }
        @Override public List<Strategy> findActive() { return List.of(); }
        @Override public Optional<Strategy> findById(String id) { return Optional.empty(); }
        @Override public void deleteById(String id) { }
    }
}
