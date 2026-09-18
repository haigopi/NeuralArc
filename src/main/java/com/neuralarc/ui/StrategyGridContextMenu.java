package com.neuralarc.ui;

import com.neuralarc.model.StrategyWorkspace;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import java.awt.Font;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.ObjIntConsumer;
import java.util.function.Supplier;

final class StrategyGridContextMenu {
    private final JTable table;
    private final Font menuFont;
    private final IntFunction<String> rowTextProvider;
    private final java.util.function.Consumer<String> clipboardWriter;
    private final IntConsumer buyMoreAtMarketHandler;
    private final IntConsumer buyMoreAtLimitHandler;
    private final IntConsumer sellAtMarketPlaceHandler;
    private final IntConsumer repositionExpiredHandler;
    private final IntConsumer cancelPendingLimitBuyHandler;
    private final IntPredicate cancelPendingLimitBuyEnabled;
    private final IntConsumer placePendingBaseBuyHandler;
    private final IntPredicate placePendingBaseBuyEnabled;
    private final IntConsumer readjustLosingPendingBaseBuyHandler;
    private final IntPredicate readjustLosingPendingBaseBuyEnabled;
    private final IntConsumer repositionFromHistoryHandler;
    private final IntPredicate repositionFromHistoryEnabled;
    private final Supplier<Boolean> historyTabSelected;
    private final Supplier<List<StrategyWorkspace>> workspacesProvider;
    // (workspaceId, viewRow) — workspaceId is null to move the row back to All Stocks (unassigned).
    private final ObjIntConsumer<String> assignToWorkspaceHandler;
    private final IntConsumer openChartHandler;
    private final IntConsumer minimizeLossHandler;
    private final IntPredicate minimizeLossEnabled;

    StrategyGridContextMenu(
            JTable table,
            Font menuFont,
            IntFunction<String> rowTextProvider,
            java.util.function.Consumer<String> clipboardWriter,
            IntConsumer buyMoreAtMarketHandler,
            IntConsumer buyMoreAtLimitHandler,
            IntConsumer sellAtMarketPlaceHandler,
            IntConsumer repositionExpiredHandler,
            IntConsumer cancelPendingLimitBuyHandler,
            IntPredicate cancelPendingLimitBuyEnabled,
            IntConsumer placePendingBaseBuyHandler,
            IntPredicate placePendingBaseBuyEnabled,
            IntConsumer readjustLosingPendingBaseBuyHandler,
            IntPredicate readjustLosingPendingBaseBuyEnabled,
            IntConsumer repositionFromHistoryHandler,
            IntPredicate repositionFromHistoryEnabled,
            Supplier<Boolean> historyTabSelected,
            Supplier<List<StrategyWorkspace>> workspacesProvider,
            ObjIntConsumer<String> assignToWorkspaceHandler,
            IntConsumer openChartHandler,
            IntConsumer minimizeLossHandler,
            IntPredicate minimizeLossEnabled
    ) {
        this.table = table;
        this.menuFont = menuFont;
        this.rowTextProvider = rowTextProvider;
        this.clipboardWriter = clipboardWriter;
        this.buyMoreAtMarketHandler = buyMoreAtMarketHandler;
        this.buyMoreAtLimitHandler = buyMoreAtLimitHandler;
        this.sellAtMarketPlaceHandler = sellAtMarketPlaceHandler;
        this.repositionExpiredHandler = repositionExpiredHandler;
        this.cancelPendingLimitBuyHandler = cancelPendingLimitBuyHandler;
        this.cancelPendingLimitBuyEnabled = cancelPendingLimitBuyEnabled;
        this.placePendingBaseBuyHandler = placePendingBaseBuyHandler;
        this.placePendingBaseBuyEnabled = placePendingBaseBuyEnabled;
        this.readjustLosingPendingBaseBuyHandler = readjustLosingPendingBaseBuyHandler;
        this.readjustLosingPendingBaseBuyEnabled = readjustLosingPendingBaseBuyEnabled;
        this.repositionFromHistoryHandler = repositionFromHistoryHandler;
        this.repositionFromHistoryEnabled = repositionFromHistoryEnabled;
        this.historyTabSelected = historyTabSelected;
        this.workspacesProvider = workspacesProvider;
        this.assignToWorkspaceHandler = assignToWorkspaceHandler;
        this.openChartHandler = openChartHandler;
        this.minimizeLossHandler = minimizeLossHandler;
        this.minimizeLossEnabled = minimizeLossEnabled;
    }

    boolean show(MouseEvent event) {
        if (!event.isPopupTrigger() && event.getButton() != MouseEvent.BUTTON3) {
            return false;
        }
        int viewRow = table.rowAtPoint(event.getPoint());
        int viewCol = table.columnAtPoint(event.getPoint());
        if (viewRow < 0 || viewCol < 0) {
            return true;
        }
        // Right-clicking inside an existing selection keeps it, so a shift-selected block can be acted
        // on as a block; right-clicking outside it starts a fresh single-row selection.
        if (!table.isRowSelected(viewRow)) {
            table.setRowSelectionInterval(viewRow, viewRow);
        }
        table.setColumnSelectionInterval(viewCol, viewCol);
        int[] selectedRows = table.getSelectedRows();

        JPopupMenu popup = new JPopupMenu();
        if (openChartHandler != null) {
            JMenuItem chart = openChartItem(viewRow);
            // One chart per window: there is nothing sensible to open for ten rows at once.
            singleRowOnly(chart, selectedRows.length);
            popup.add(chart);
            popup.addSeparator();
        }
        popup.add(copyMenu(viewRow, viewCol, selectedRows));
        popup.add(positionMenu(viewRow, selectedRows));
        JMenu workspaceMenu = workspaceMenu(selectedRows);
        if (workspaceMenu != null) {
            popup.add(workspaceMenu);
        }
        popup.show(event.getComponent(), event.getX(), event.getY());
        return true;
    }

    private JMenu workspaceMenu(int[] selectedRows) {
        if (workspacesProvider == null || assignToWorkspaceHandler == null) {
            return null;
        }
        List<StrategyWorkspace> workspaces = workspacesProvider.get();
        if (workspaces == null || workspaces.isEmpty()) {
            return null; // No workspaces created yet — nothing to move into.
        }
        JMenu menu = new JMenu("Move to Workspace" + rowSuffix(selectedRows.length));
        menu.setFont(menuFont);
        JMenuItem none = item("All Stocks (unassigned)");
        none.addActionListener(e -> forEachSelected(selectedRows, row -> assignToWorkspaceHandler.accept(null, row)));
        menu.add(none);
        for (StrategyWorkspace workspace : workspaces) {
            JMenuItem target = item(workspace.name());
            target.addActionListener(e ->
                    forEachSelected(selectedRows, row -> assignToWorkspaceHandler.accept(workspace.id(), row)));
            menu.add(target);
        }
        return menu;
    }

    private JMenu copyMenu(int viewRow, int viewCol, int[] selectedRows) {
        Object value = table.getValueAt(viewRow, viewCol);
        String text = value == null ? "" : value.toString();
        JMenu copy = new JMenu("Copy");
        copy.setFont(menuFont);
        JMenuItem copyCell = item("Cell Text");
        copyCell.addActionListener(e -> clipboardWriter.accept(text));
        // One click landed on one cell; there is no "the cell" across a multi-row selection.
        singleRowOnly(copyCell, selectedRows.length);
        copy.add(copyCell);
        JMenuItem copyRow = item("Row Text" + rowSuffix(selectedRows.length));
        copyRow.addActionListener(e -> {
            StringBuilder rows = new StringBuilder();
            for (int row : selectedRows) {
                if (rows.length() > 0) {
                    rows.append(System.lineSeparator());
                }
                rows.append(rowTextProvider.apply(row));
            }
            clipboardWriter.accept(rows.toString());
        });
        copy.add(copyRow);
        return copy;
    }

    private JMenu positionMenu(int viewRow, int[] selectedRows) {
        int selectedCount = selectedRows.length;
        JMenu position = new JMenu("Position");
        position.setFont(menuFont);

        // Anything that asks for details one row at a time stays single-row. Ten prompts in a row is
        // poor enough, but a modal dialog also pumps the event queue: a queued grid refresh could
        // re-sort the table between prompts, and the rest of the selection would then point at
        // different strategies than the operator picked.
        JMenuItem buy = item("Buy More at Market Price");
        buy.addActionListener(e -> buyMoreAtMarketHandler.accept(viewRow));
        singleRowOnly(buy, selectedCount);
        position.add(buy);
        JMenuItem buyLimit = item("Buy More at Limit Price");
        buyLimit.addActionListener(e -> buyMoreAtLimitHandler.accept(viewRow));
        singleRowOnly(buyLimit, selectedCount);
        position.add(buyLimit);
        JMenuItem sell = item("Sell at Market-Place");
        sell.addActionListener(e -> sellAtMarketPlaceHandler.accept(viewRow));
        singleRowOnly(sell, selectedCount);
        position.add(sell);
        JMenuItem reposition = item("Reposition Expired Stock");
        reposition.addActionListener(e -> repositionExpiredHandler.accept(viewRow));
        singleRowOnly(reposition, selectedCount);
        position.add(reposition);

        // These run without asking anything, so they apply to the whole selection. The label counts
        // the rows that actually qualify, not the rows highlighted.
        if (placePendingBaseBuyHandler != null && placePendingBaseBuyEnabled != null) {
            int eligible = countEligible(selectedRows, placePendingBaseBuyEnabled);
            if (eligible > 0) {
                JMenuItem placePending = item("Place Pending Base Buy" + rowSuffix(eligible));
                placePending.addActionListener(e ->
                        forEachEligible(selectedRows, placePendingBaseBuyEnabled, placePendingBaseBuyHandler));
                position.add(placePending);
            }
        }
        if (readjustLosingPendingBaseBuyHandler != null && readjustLosingPendingBaseBuyEnabled != null) {
            int eligible = countEligible(selectedRows, readjustLosingPendingBaseBuyEnabled);
            JMenuItem readjustPending = item("Readjust Losing Pending Base Buy" + rowSuffix(eligible));
            readjustPending.setEnabled(eligible > 0);
            readjustPending.addActionListener(e -> forEachEligible(
                    selectedRows, readjustLosingPendingBaseBuyEnabled, readjustLosingPendingBaseBuyHandler));
            position.add(readjustPending);
        }
        if (Boolean.TRUE.equals(historyTabSelected == null ? null : historyTabSelected.get())
                && repositionFromHistoryHandler != null
                && repositionFromHistoryEnabled != null) {
            JMenuItem repositionHistory = item("Reposition Stock");
            repositionHistory.setEnabled(repositionFromHistoryEnabled.test(viewRow));
            repositionHistory.addActionListener(e -> repositionFromHistoryHandler.accept(viewRow));
            singleRowOnly(repositionHistory, selectedCount);
            position.add(repositionHistory);
        }
        if (minimizeLossHandler != null && minimizeLossEnabled != null) {
            // Only a position actually under water has a loss to work down, and the plan is reviewed
            // one symbol at a time.
            JMenuItem minimizeLoss = item("Minimize Loss Impact...");
            minimizeLoss.setEnabled(minimizeLossEnabled.test(viewRow));
            minimizeLoss.addActionListener(e -> minimizeLossHandler.accept(viewRow));
            singleRowOnly(minimizeLoss, selectedCount);
            position.add(minimizeLoss);
        }
        if (cancelPendingLimitBuyHandler != null && cancelPendingLimitBuyEnabled != null) {
            int eligible = countEligible(selectedRows, cancelPendingLimitBuyEnabled);
            if (eligible > 0) {
                JMenuItem cancelBuy = item("Cancel Pending Limit Buy" + rowSuffix(eligible));
                cancelBuy.addActionListener(e ->
                        forEachEligible(selectedRows, cancelPendingLimitBuyEnabled, cancelPendingLimitBuyHandler));
                position.add(cancelBuy);
            }
        }
        return position;
    }

    /** Greys out an action that only makes sense for one row, saying why. Never re-enables. */
    private static void singleRowOnly(JMenuItem item, int selectedRowCount) {
        if (selectedRowCount > 1) {
            item.setEnabled(false);
            item.setToolTipText("Select a single row: this action asks for details one row at a time.");
        }
    }

    private static String rowSuffix(int count) {
        return count > 1 ? "  (" + count + " rows)" : "";
    }

    /**
     * Runs an action for each selected row. Safe against the view indices shifting: this runs on the
     * EDT, and a grid refresh can only repaint once the whole loop has returned.
     */
    private void forEachSelected(int[] selectedRows, IntConsumer action) {
        for (int row : selectedRows) {
            action.accept(row);
        }
    }

    private void forEachEligible(int[] selectedRows, IntPredicate eligible, IntConsumer action) {
        for (int row : selectedRows) {
            if (eligible.test(row)) {
                action.accept(row);
            }
        }
    }

    private static int countEligible(int[] selectedRows, IntPredicate eligible) {
        int count = 0;
        for (int row : selectedRows) {
            if (eligible.test(row)) {
                count++;
            }
        }
        return count;
    }

    /** Opens the row's stock chart, with the strategy's own levels drawn on it. */
    JMenuItem openChartItem(int viewRow) {
        JMenuItem open = item("Open Chart");
        open.addActionListener(event -> openChartHandler.accept(viewRow));
        return open;
    }

    private JMenuItem item(String label) {
        JMenuItem item = new JMenuItem(label);
        item.setFont(menuFont);
        return item;
    }
}
