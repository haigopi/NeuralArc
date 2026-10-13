package com.neuralarc.ui;

import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyWorkspace;
import com.neuralarc.service.WorkspaceService;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Manages the dynamic strategy-workspace tabs in the strategies {@link JTabbedPane}.
 *
 * <p>The tab order is always {@code [All Stocks] [workspace…] [Trade History]}. A single shared
 * strategies grid component is re-parented into whichever All Stocks / workspace tab is selected,
 * so the existing grid, search box, and row filtering are reused rather than duplicated per tab.
 * For current installations with no workspaces, the layout is identical to before (All Stocks at
 * index 0, Trade History at index 1).
 *
 * <p>All mutations run on the EDT (Swing). A re-entrancy guard suppresses the selection callback
 * while tabs are being rebuilt.
 */
final class StrategyWorkspaceTabs {
    /** Stands for the All Stocks tab in the set of tabs with a liquidation running. */
    static final String ALL_STOCKS = "";
    /** Appended to the tooltip of a tab that carries the running dot, so the dot explains itself. */
    static final String RUNNING_TOOLTIP = "A Liquidate Portfolio monitor is running in this workspace.";

    private final JTabbedPane tabs;
    private final JComponent gridComponent;
    private final WorkspaceService workspaceService;
    private final Supplier<StrategyMode> modeSupplier;
    private final Consumer<String> onWorkspaceSelected;
    private final Supplier<String> allStocksTitle;
    private final Function<StrategyWorkspace, String> workspaceTitle;

    private final JPanel allStocksHost = new JPanel(new BorderLayout());
    // Parallel to the tab order: workspaceId for a workspace tab, null for All Stocks / Trade History.
    private final List<String> workspaceIdByTab = new ArrayList<>();
    private boolean rebuilding;
    private Supplier<Set<String>> runningLiquidations = Set::of;
    // One dot per running tab, all flipping together on one timer that exists only while a dot is shown.
    private final Map<Integer, LiquidationPulse.DotIcon> dotsByTab = new HashMap<>();
    private final Map<Integer, String> originalTooltips = new HashMap<>();
    private boolean pulseAlternate;
    private final Timer pulseTimer = new Timer(LiquidationPulse.INTERVAL_MILLIS, event -> flipPulse());

    StrategyWorkspaceTabs(
            JTabbedPane tabs,
            JComponent gridComponent,
            JComponent historyComponent,
            WorkspaceService workspaceService,
            Supplier<StrategyMode> modeSupplier,
            Consumer<String> onWorkspaceSelected,
            Supplier<String> allStocksTitle,
            Function<StrategyWorkspace, String> workspaceTitle,
            Supplier<String> historyTitle
    ) {
        this.tabs = tabs;
        this.gridComponent = gridComponent;
        this.workspaceService = workspaceService;
        this.modeSupplier = modeSupplier;
        this.onWorkspaceSelected = onWorkspaceSelected;
        this.allStocksTitle = allStocksTitle;
        this.workspaceTitle = workspaceTitle;

        allStocksHost.setOpaque(false);
        allStocksHost.add(gridComponent, BorderLayout.CENTER);
        tabs.addTab(allStocksTitle.get(), allStocksHost);
        tabs.addTab(historyTitle.get(), historyComponent);

        tabs.addChangeListener(event -> {
            if (!rebuilding) {
                handleSelection();
            }
        });
        rebuild();
    }

    /**
     * Which tabs have a liquidation running, as workspace ids ({@link #ALL_STOCKS} for the All Stocks tab).
     * Only one tab is visible at a time, so a running liquidation in another workspace would otherwise go unseen.
     */
    void setRunningLiquidations(Supplier<Set<String>> runningLiquidations) {
        this.runningLiquidations = runningLiquidations == null ? Set::of : runningLiquidations;
        refreshRunningHighlight();
    }

    /**
     * Marks the tabs that have a liquidation running with the same blinking dot as the Liquidate Portfolio
     * button; call whenever the set changes or the selection moves. The selected tab is never marked: the
     * operator is already looking at it and at its own liquidation button, so the dot only flags the runs
     * hidden behind other tabs.
     */
    void refreshRunningHighlight() {
        Set<String> running = runningLiquidations.get();
        int selected = tabs.getSelectedIndex();
        boolean anyDot = false;
        for (int index = 0; index < historyTabIndex() && index < workspaceIdByTab.size(); index++) {
            String workspaceId = workspaceIdByTab.get(index);
            boolean isRunning = index != selected && running.contains(workspaceId == null ? ALL_STOCKS : workspaceId);
            anyDot |= isRunning;
            markTab(index, isRunning);
        }
        if (anyDot) {
            if (!pulseTimer.isRunning()) {
                pulseTimer.start();
            }
        } else {
            pulseTimer.stop();
        }
    }

    private void markTab(int index, boolean running) {
        if (running && !dotsByTab.containsKey(index)) {
            LiquidationPulse.DotIcon dot = new LiquidationPulse.DotIcon();
            dot.setAlternate(pulseAlternate);
            dotsByTab.put(index, dot);
            tabs.setIconAt(index, dot);
            String original = tabs.getToolTipTextAt(index);
            originalTooltips.put(index, original);
            tabs.setToolTipTextAt(index, original == null || original.isBlank()
                    ? RUNNING_TOOLTIP : original + " " + RUNNING_TOOLTIP);
        } else if (!running && dotsByTab.containsKey(index)) {
            clearMark(index);
        }
    }

    private void clearMark(int index) {
        dotsByTab.remove(index);
        if (index < tabs.getTabCount()) {
            tabs.setIconAt(index, null);
            tabs.setToolTipTextAt(index, originalTooltips.get(index));
        }
        originalTooltips.remove(index);
    }

    private void flipPulse() {
        pulseAlternate = !pulseAlternate;
        dotsByTab.values().forEach(dot -> dot.setAlternate(pulseAlternate));
        tabs.repaint();
    }

    /** Whether the dots are blinking, for tests. */
    boolean pulsing() {
        return pulseTimer.isRunning();
    }

    /** Rebuilds the workspace tabs for the active mode, preserving the current selection if possible. */
    void rebuild() {
        rebuilding = true;
        for (Integer index : List.copyOf(dotsByTab.keySet())) {
            clearMark(index);
        }
        String previouslySelected = selectedWorkspaceId();
        try {
            // Remove existing workspace tabs (everything between All Stocks and the last Trade History tab).
            for (int i = tabs.getTabCount() - 2; i >= 1; i--) {
                tabs.removeTabAt(i);
            }
            workspaceIdByTab.clear();
            workspaceIdByTab.add(null); // All Stocks

            List<StrategyWorkspace> active = workspaceService.activeWorkspaces(modeSupplier.get());
            int insertAt = 1;
            for (StrategyWorkspace workspace : active) {
                JPanel host = new JPanel(new BorderLayout());
                host.setOpaque(false);
                tabs.insertTab(tabTitle(workspace), null, host, tabTooltip(workspace), insertAt);
                workspaceIdByTab.add(workspace.id());
                insertAt++;
            }
            workspaceIdByTab.add(null); // Trade History (now the last tab)

            int restoreIndex = previouslySelected == null ? 0 : Math.max(0, workspaceIdByTab.indexOf(previouslySelected));
            tabs.setTitleAt(0, allStocksTitle.get());
            tabs.setSelectedIndex(restoreIndex);
        } finally {
            rebuilding = false;
        }
        refreshRunningHighlight();
        handleSelection();
    }

    void refreshStrategyTitles() {
        if (tabs.getTabCount() == 0) {
            return;
        }
        tabs.setTitleAt(0, allStocksTitle.get());
        for (int index = 1; index < historyTabIndex(); index++) {
            String workspaceId = workspaceIdAt(index);
            if (workspaceId == null) {
                continue;
            }
            int tabIndex = index;
            workspaceService.findById(workspaceId)
                    .ifPresent(workspace -> tabs.setTitleAt(tabIndex, tabTitle(workspace)));
        }
        refreshRunningHighlight();
    }

    /**
     * Returns the workspace matching {@code name}/{@code code} in the current mode (creating it only
     * if none exists), rebuilds the tabs, and selects it. Repeated calls for the same template
     * highlight the existing tab instead of creating a duplicate.
     */
    StrategyWorkspace createOrSelect(String name, String code) {
        StrategyWorkspace target = workspaceService.findOrCreate(name, code, modeSupplier.get());
        rebuild();
        selectWorkspace(target.id());
        return target;
    }

    /** Deletes a workspace (only when empty) and removes its tab; rebuilds on success. */
    WorkspaceService.DeleteResult deleteWorkspace(String workspaceId) {
        if (workspaceId == null) {
            return WorkspaceService.DeleteResult.NOT_FOUND;
        }
        WorkspaceService.DeleteResult result = workspaceService.delete(workspaceId);
        if (result == WorkspaceService.DeleteResult.DELETED) {
            rebuild();
        }
        return result;
    }

    void renameWorkspace(String workspaceId, String newName) {
        if (workspaceId == null) {
            return;
        }
        workspaceService.rename(workspaceId, newName);
        rebuild();
    }

    /** workspaceId of the selected tab, or null for All Stocks / Trade History. */
    String selectedWorkspaceId() {
        int index = tabs.getSelectedIndex();
        if (index < 0 || index >= workspaceIdByTab.size()) {
            return null;
        }
        return workspaceIdByTab.get(index);
    }

    /** Stable internal id for the selected liquidate-capable strategy tab, or null for Trade History. */
    String selectedStrategyTabId() {
        int index = tabs.getSelectedIndex();
        if (index < 0 || index == historyTabIndex()) {
            return null;
        }
        String workspaceId = workspaceIdAt(index);
        return workspaceId == null ? PortfolioCaptureUiStateStore.ALL_STOCKS_TAB_ID : "workspace:" + workspaceId;
    }

    boolean isHistorySelected() {
        int index = tabs.getSelectedIndex();
        return index >= 0 && index == historyTabIndex();
    }

    /** workspaceId of the tab at {@code tabIndex}, or null. */
    String workspaceIdAt(int tabIndex) {
        if (tabIndex < 0 || tabIndex >= workspaceIdByTab.size()) {
            return null;
        }
        return workspaceIdByTab.get(tabIndex);
    }

    int historyTabIndex() {
        return tabs.getTabCount() - 1;
    }

    private void selectWorkspace(String workspaceId) {
        int index = workspaceIdByTab.indexOf(workspaceId);
        if (index > 0) {
            tabs.setSelectedIndex(index);
        }
    }

    private void handleSelection() {
        refreshRunningHighlight();
        int index = tabs.getSelectedIndex();
        if (index < 0 || index == historyTabIndex()) {
            return; // Trade History manages its own content; nothing to re-parent.
        }
        JPanel host = index == 0 ? allStocksHost : asPanel(tabs.getComponentAt(index));
        if (host != null && gridComponent.getParent() != host) {
            host.add(gridComponent, BorderLayout.CENTER);
            host.revalidate();
            host.repaint();
        }
        onWorkspaceSelected.accept(workspaceIdAt(index));
    }

    private JPanel asPanel(java.awt.Component component) {
        return component instanceof JPanel panel ? panel : null;
    }

    private String tabTitle(StrategyWorkspace workspace) {
        if (workspaceTitle == null) {
            return workspace.name();
        }
        String title = workspaceTitle.apply(workspace);
        return title == null || title.isBlank() ? workspace.name() : title;
    }

    private String tabTooltip(StrategyWorkspace workspace) {
        if ("GAPROCKET".equalsIgnoreCase(workspace.code())) {
            return TooltipStyler.text("Scan premarket gap-up stocks, rank the strongest movers, and track opening-range, breakout-retest, or VWAP-pullback setups in a dedicated morning strategy grid.", 360);
        }
        if ("ORB".equalsIgnoreCase(workspace.code())) {
            return TooltipStyler.text("Capture the market's opening range from live Alpaca data, then arm long breakout entries after the range closes.", 360);
        }
        if ("DIP".equalsIgnoreCase(workspace.code())) {
            return TooltipStyler.text("Scan strong, up-trending stocks that have pulled back from a recent high, rank the best bounce setups on live data, and track planned entries in a dedicated Dip Hunter grid.", 360);
        }
        if ("VWAP".equalsIgnoreCase(workspace.code())) {
            return TooltipStyler.text("Scan still-strong stocks trading at a discount below their intraday VWAP, rank the best mean-reversion setups on live data, and track planned entries (target = VWAP) in a dedicated VWAP Desk grid.", 360);
        }
        if ("SWING".equalsIgnoreCase(workspace.code())) {
            return TooltipStyler.text("Scan strong, up-trending stocks that have pulled back to a rising moving-average support zone on the daily chart, rank the best multi-day swing setups on live data, and track planned entries (target = recent high) in a dedicated Swing Vault grid.", 360);
        }
        if ("SHIELD".equalsIgnoreCase(workspace.code())) {
            return TooltipStyler.text("Scan liquid stocks with quiet daily ranges, shallow historical drawdowns, and an intact long-term trend, rank the most resilient on live data, and track capital-preserving entries with a support-tightened protective stop in a dedicated Profit Shield grid.", 360);
        }
        if ("RANGE".equalsIgnoreCase(workspace.code())) {
            return TooltipStyler.text("Analyze the last three weeks of daily bars for actively traded stocks, average each session's open, high, and low, and plan a same-day income trade — buy near the average daily low, sell near the average daily high — in a dedicated Range Rider grid.", 360);
        }
        return workspace.name();
    }
}
