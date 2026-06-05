package com.neuralarc.ui;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import java.awt.Font;
import java.awt.event.MouseEvent;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

final class StrategyGridContextMenu {
    private final JTable table;
    private final Font menuFont;
    private final IntFunction<String> rowTextProvider;
    private final java.util.function.Consumer<String> clipboardWriter;
    private final IntConsumer sellAtMarketPlaceHandler;
    private final IntConsumer repositionExpiredHandler;

    StrategyGridContextMenu(
            JTable table,
            Font menuFont,
            IntFunction<String> rowTextProvider,
            java.util.function.Consumer<String> clipboardWriter,
            IntConsumer sellAtMarketPlaceHandler,
            IntConsumer repositionExpiredHandler
    ) {
        this.table = table;
        this.menuFont = menuFont;
        this.rowTextProvider = rowTextProvider;
        this.clipboardWriter = clipboardWriter;
        this.sellAtMarketPlaceHandler = sellAtMarketPlaceHandler;
        this.repositionExpiredHandler = repositionExpiredHandler;
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
        popup.show(event.getComponent(), event.getX(), event.getY());
        return true;
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
        JMenuItem sell = item("Sell at Market-Place");
        sell.addActionListener(e -> sellAtMarketPlaceHandler.accept(viewRow));
        position.add(sell);
        JMenuItem reposition = item("Reposition Expired Stock");
        reposition.addActionListener(e -> repositionExpiredHandler.accept(viewRow));
        position.add(reposition);
        return position;
    }

    private JMenuItem item(String label) {
        JMenuItem item = new JMenuItem(label);
        item.setFont(menuFont);
        return item;
    }
}
