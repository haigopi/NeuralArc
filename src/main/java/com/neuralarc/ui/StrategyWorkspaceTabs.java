package com.neuralarc.ui;

import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyWorkspace;
import com.neuralarc.service.WorkspaceService;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
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
    private final JTabbedPane tabs;
    private final JComponent gridComponent;
    private final WorkspaceService workspaceService;
    private final Supplier<StrategyMode> modeSupplier;
    private final Consumer<String> onWorkspaceSelected;
    private final Supplier<String> allStocksTitle;

    private final JPanel allStocksHost = new JPanel(new BorderLayout());
    // Parallel to the tab order: workspaceId for a workspace tab, null for All Stocks / Trade History.
    private final List<String> workspaceIdByTab = new ArrayList<>();
    private boolean rebuilding;

    StrategyWorkspaceTabs(
            JTabbedPane tabs,
            JComponent gridComponent,
            JComponent historyComponent,
            WorkspaceService workspaceService,
            Supplier<StrategyMode> modeSupplier,
            Consumer<String> onWorkspaceSelected,
            Supplier<String> allStocksTitle,
            Supplier<String> historyTitle
    ) {
        this.tabs = tabs;
        this.gridComponent = gridComponent;
        this.workspaceService = workspaceService;
        this.modeSupplier = modeSupplier;
        this.onWorkspaceSelected = onWorkspaceSelected;
        this.allStocksTitle = allStocksTitle;

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

    /** Rebuilds the workspace tabs for the active mode, preserving the current selection if possible. */
    void rebuild() {
        rebuilding = true;
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
        handleSelection();
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
        return workspace.name();
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
        return workspace.name();
    }
}
