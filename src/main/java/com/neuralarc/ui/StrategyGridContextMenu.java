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
    private final Supplier<List<StrategyWorkspace>> workspacesProvider;
    // (workspaceId, viewRow) — workspaceId is null to move the row back to All Stocks (unassigned).
    private final ObjIntConsumer<String> assignToWorkspaceHandler;

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
            Supplier<List<StrategyWorkspace>> workspacesProvider,
            ObjIntConsumer<String> assignToWorkspaceHandler
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
        this.workspacesProvider = workspacesProvider;
        this.assignToWorkspaceHandler = assignToWorkspaceHandler;
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
        table.setRowSelectionInterval(viewRow, viewRow);
        table.setColumnSelectionInterval(viewCol, viewCol);

        JPopupMenu popup = new JPopupMenu();
        popup.add(copyMenu(viewRow, viewCol));
        popup.add(positionMenu(viewRow));
        JMenu workspaceMenu = workspaceMenu(viewRow);
        if (workspaceMenu != null) {
            popup.add(workspaceMenu);
        }
        popup.show(event.getComponent(), event.getX(), event.getY());
        return true;
    }

    private JMenu workspaceMenu(int viewRow) {
        if (workspacesProvider == null || assignToWorkspaceHandler == null) {
            return null;
        }
        List<StrategyWorkspace> workspaces = workspacesProvider.get();
        if (workspaces == null || workspaces.isEmpty()) {
            return null; // No workspaces created yet — nothing to move into.
        }
        JMenu menu = new JMenu("Move to Workspace");
        menu.setFont(menuFont);
        JMenuItem none = item("All Stocks (unassigned)");
        none.addActionListener(e -> assignToWorkspaceHandler.accept(null, viewRow));
        menu.add(none);
        for (StrategyWorkspace workspace : workspaces) {
            JMenuItem target = item(workspace.name());
            target.addActionListener(e -> assignToWorkspaceHandler.accept(workspace.id(), viewRow));
            menu.add(target);
        }
        return menu;
    }

    private JMenu copyMenu(int viewRow, int viewCol) {
        Object value = table.getValueAt(viewRow, viewCol);
        String text = value == null ? "" : value.toString();
        JMenu copy = new JMenu("Copy");
        copy.setFont(menuFont);
        JMenuItem copyCell = item("Cell Text");
        copyCell.addActionListener(e -> clipboardWriter.accept(text));
        copy.add(copyCell);
        JMenuItem copyRow = item("Row Text");
        copyRow.addActionListener(e -> clipboardWriter.accept(rowTextProvider.apply(viewRow)));
        copy.add(copyRow);
        return copy;
    }

    private JMenu positionMenu(int viewRow) {
        JMenu position = new JMenu("Position");
        position.setFont(menuFont);
        JMenuItem buy = item("Buy More at Market Price");
        buy.addActionListener(e -> buyMoreAtMarketHandler.accept(viewRow));
        position.add(buy);
        JMenuItem buyLimit = item("Buy More at Limit Price");
        buyLimit.addActionListener(e -> buyMoreAtLimitHandler.accept(viewRow));
        position.add(buyLimit);
        JMenuItem sell = item("Sell at Market-Place");
        sell.addActionListener(e -> sellAtMarketPlaceHandler.accept(viewRow));
        position.add(sell);
        JMenuItem reposition = item("Reposition Expired Stock");
        reposition.addActionListener(e -> repositionExpiredHandler.accept(viewRow));
        position.add(reposition);
        if (placePendingBaseBuyHandler != null
                && placePendingBaseBuyEnabled != null
                && placePendingBaseBuyEnabled.test(viewRow)) {
            JMenuItem placePending = item("Place Pending Base Buy");
            placePending.addActionListener(e -> placePendingBaseBuyHandler.accept(viewRow));
            position.add(placePending);
        }
        if (cancelPendingLimitBuyHandler != null
                && cancelPendingLimitBuyEnabled != null
                && cancelPendingLimitBuyEnabled.test(viewRow)) {
            JMenuItem cancelBuy = item("Cancel Pending Limit Buy");
            cancelBuy.addActionListener(e -> cancelPendingLimitBuyHandler.accept(viewRow));
            position.add(cancelBuy);
        }
        return position;
    }

    private JMenuItem item(String label) {
        JMenuItem item = new JMenuItem(label);
        item.setFont(menuFont);
        return item;
    }
}
