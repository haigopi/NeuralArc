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
                workspace -> workspace.name(),
                () -> "Trade History"
        ));

        String tooltip = tabs.getToolTipTextAt(1);
        assertNotNull(tooltip);
        assertTrue(tooltip.contains("premarket gap-up stocks"));
        assertTrue(tooltip.contains("VWAP-pullback"));
    }

    @Test
    void tabsWithARunningLiquidationShowABlinkingDotAndLoseItWhenItStops() throws Exception {
        InMemoryWorkspaceRepository workspaceRepository = new InMemoryWorkspaceRepository();
        workspaceRepository.save(new StrategyWorkspace("a", "Alpha", "CODE1", StrategyMode.PAPER, false, Instant.now(), Instant.now()));
        workspaceRepository.save(new StrategyWorkspace("b", "Beta", "CODE2", StrategyMode.PAPER, false, Instant.now(), Instant.now()));
        WorkspaceService service = new WorkspaceService(workspaceRepository, new EmptyStrategyRepository());
        JTabbedPane tabs = new JTabbedPane();
        java.util.Set<String> running = new java.util.HashSet<>();
        final StrategyWorkspaceTabs[] coordinator = new StrategyWorkspaceTabs[1];
        SwingUtilities.invokeAndWait(() -> {
            coordinator[0] = new StrategyWorkspaceTabs(tabs, new JPanel(), new JPanel(), service,
                    () -> StrategyMode.PAPER, ignored -> { }, () -> "All Stocks", w -> w.name(), () -> "Trade History");
            coordinator[0].setRunningLiquidations(() -> running);
        });
        // Tabs: 0 All Stocks, 1 Alpha, 2 Beta, 3 Trade History. All Stocks (0) is selected.
        for (int i = 0; i < 4; i++) {
            assertNull(tabs.getIconAt(i));
        }
        assertFalse(coordinator[0].pulsing(), "nothing blinks while nothing runs");

        running.add("b");
        running.add(StrategyWorkspaceTabs.ALL_STOCKS);
        SwingUtilities.invokeAndWait(() -> coordinator[0].refreshRunningHighlight());

        assertNotNull(tabs.getIconAt(2));
        assertTrue(tabs.getToolTipTextAt(2).contains(StrategyWorkspaceTabs.RUNNING_TOOLTIP), "the dot explains itself");
        assertNull(tabs.getIconAt(0), "the selected tab is never marked");
        assertNull(tabs.getIconAt(1), "Alpha is not running");
        assertNull(tabs.getIconAt(3), "Trade History is never marked");
        assertTrue(coordinator[0].pulsing());

        SwingUtilities.invokeAndWait(() -> coordinator[0].rebuild());
        assertNotNull(tabs.getIconAt(2), "survives a tab rebuild");

        SwingUtilities.invokeAndWait(() -> tabs.setSelectedIndex(2));
        assertNull(tabs.getIconAt(2), "viewing the tab clears its dot");
        assertFalse(tabs.getToolTipTextAt(2).contains(StrategyWorkspaceTabs.RUNNING_TOOLTIP), "and its tooltip note");
        assertNotNull(tabs.getIconAt(0), "All Stocks is now hidden behind it");
        SwingUtilities.invokeAndWait(() -> tabs.setSelectedIndex(0));
        assertNotNull(tabs.getIconAt(2), "leaving the tab marks it again");

        running.clear();
        SwingUtilities.invokeAndWait(() -> coordinator[0].refreshRunningHighlight());
        assertNull(tabs.getIconAt(2));
        assertNull(tabs.getIconAt(0));
        assertFalse(coordinator[0].pulsing(), "the blink stops with the last dot");
    }

    @Test
    void theDotBlinksBetweenTheLiquidateButtonsTwoColours() {
        LiquidationPulse.DotIcon dot = new LiquidationPulse.DotIcon();
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(20, 20, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics g = image.getGraphics();
        dot.paintIcon(null, g, 4, 4);
        int first = image.getRGB(8, 10);
        dot.setAlternate(true);
        dot.paintIcon(null, g, 4, 4);
        int second = image.getRGB(8, 10);
        g.dispose();

        assertEquals(LiquidationPulse.PRIMARY.getRGB(), first);
        assertEquals(LiquidationPulse.ALTERNATE.getRGB(), second);
    }

    @Test
    void liquidateTabIdsUseStableWorkspaceIdsAndExcludeHistory() throws Exception {
        InMemoryWorkspaceRepository workspaceRepository = new InMemoryWorkspaceRepository();
        workspaceRepository.save(new StrategyWorkspace("paper-a", "Duplicated", "CODE1", StrategyMode.PAPER, false, Instant.now(), Instant.now()));
        workspaceRepository.save(new StrategyWorkspace("live-a", "Duplicated", "CODE1", StrategyMode.LIVE, false, Instant.now(), Instant.now()));
        WorkspaceService service = new WorkspaceService(workspaceRepository, new EmptyStrategyRepository());
        JTabbedPane tabs = new JTabbedPane();
        final StrategyMode[] mode = {StrategyMode.PAPER};
        final StrategyWorkspaceTabs[] coordinator = new StrategyWorkspaceTabs[1];

        SwingUtilities.invokeAndWait(() -> coordinator[0] = new StrategyWorkspaceTabs(
                tabs,
                new JPanel(),
                new JPanel(),
                service,
                () -> mode[0],
                ignored -> { },
                () -> "All Stocks",
                workspace -> workspace.name(),
                () -> "Trade History"
        ));

        SwingUtilities.invokeAndWait(() -> tabs.setSelectedIndex(1));
        assertEquals("workspace:paper-a", coordinator[0].selectedStrategyTabId());

        SwingUtilities.invokeAndWait(() -> {
            mode[0] = StrategyMode.LIVE;
            coordinator[0].rebuild();
            tabs.setSelectedIndex(1);
        });
        assertEquals("workspace:live-a", coordinator[0].selectedStrategyTabId());

        SwingUtilities.invokeAndWait(() -> tabs.setSelectedIndex(coordinator[0].historyTabIndex()));
        assertTrue(coordinator[0].isHistorySelected());
        assertNull(coordinator[0].selectedStrategyTabId());
    }

    @Test
    void workspaceTabTitlesCanShowDynamicCounts() throws Exception {
        InMemoryWorkspaceRepository workspaceRepository = new InMemoryWorkspaceRepository();
        workspaceRepository.save(new StrategyWorkspace("orb", "ORB Engine", "ORB", StrategyMode.PAPER, false, Instant.now(), Instant.now()));
        WorkspaceService service = new WorkspaceService(workspaceRepository, new EmptyStrategyRepository());
        JTabbedPane tabs = new JTabbedPane();
        final int[] count = {2};
        final StrategyWorkspaceTabs[] coordinator = new StrategyWorkspaceTabs[1];

        SwingUtilities.invokeAndWait(() -> coordinator[0] = new StrategyWorkspaceTabs(
                tabs,
                new JPanel(),
                new JPanel(),
                service,
                () -> StrategyMode.PAPER,
                ignored -> { },
                () -> "All Stocks - Paper (5)",
                workspace -> workspace.name() + " (" + count[0] + ")",
                () -> "Trade History"
        ));

        assertEquals("ORB Engine (2)", tabs.getTitleAt(1));

        count[0] = 3;
        SwingUtilities.invokeAndWait(() -> coordinator[0].refreshStrategyTitles());

        assertEquals("ORB Engine (3)", tabs.getTitleAt(1));
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
