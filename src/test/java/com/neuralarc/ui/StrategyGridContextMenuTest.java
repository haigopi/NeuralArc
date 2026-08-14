package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import java.awt.Font;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategyGridContextMenuTest {
    @Test
    void readjustLosingPendingBaseBuyMenuItemIsEnabledWhenEligible() throws Exception {
        JMenu positionMenu = positionMenuForRow(0, true);
        JMenuItem item = findItem(positionMenu, "Readjust Losing Pending Base Buy");
        assertNotNull(item);
        assertTrue(item.isEnabled());
    }

    @Test
    void readjustLosingPendingBaseBuyMenuItemIsDisabledWhenNotEligible() throws Exception {
        JMenu positionMenu = positionMenuForRow(0, false);
        JMenuItem item = findItem(positionMenu, "Readjust Losing Pending Base Buy");
        assertNotNull(item);
        assertFalse(item.isEnabled());
    }

    @Test
    void repositionStockMenuItemAppearsOnlyInHistoryContext() throws Exception {
        JMenu historyMenu = positionMenuForRow(0, true, true);
        assertNotNull(findItem(historyMenu, "Reposition Stock"));

        JMenu nonHistoryMenu = positionMenuForRow(0, true, false);
        assertNull(findItem(nonHistoryMenu, "Reposition Stock"));
    }

    private JMenu positionMenuForRow(int row, boolean eligible) throws Exception {
        return positionMenuForRow(row, eligible, true);
    }

    private JMenu positionMenuForRow(int row, boolean eligible, boolean historySelected) throws Exception {
        JTable table = new JTable(new DefaultTableModel(new Object[][]{{"AAPL"}}, new Object[]{"Symbol"}));
        StrategyGridContextMenu menu = new StrategyGridContextMenu(
                table,
                new Font("Dialog", Font.PLAIN, 12),
                viewRow -> "row-" + viewRow,
                text -> { },
                viewRow -> { },
                viewRow -> { },
                viewRow -> { },
                viewRow -> { },
                viewRow -> { },
                viewRow -> false,
                viewRow -> { },
                viewRow -> false,
                viewRow -> { },
                viewRow -> eligible,
                viewRow -> { },
                viewRow -> eligible,
                () -> historySelected,
                List::of,
                (workspaceId, viewRow) -> { }
        );
        Method method = StrategyGridContextMenu.class.getDeclaredMethod("positionMenu", int.class);
        method.setAccessible(true);
        return (JMenu) method.invoke(menu, row);
    }

    private JMenuItem findItem(JMenu menu, String text) {
        for (int index = 0; index < menu.getItemCount(); index++) {
            JMenuItem item = menu.getItem(index);
            if (item != null && text.equals(item.getText())) {
                return item;
            }
        }
        return null;
    }
}
