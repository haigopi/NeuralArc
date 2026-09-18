package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import java.awt.Font;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void openChartOpensTheClickedRowsChart() {
        int[] opened = {-1};
        StrategyGridContextMenu menu = new StrategyGridContextMenu(
                new JTable(new DefaultTableModel(new Object[][]{{"AAPL"}}, new Object[]{"Symbol"})),
                new Font("Dialog", Font.PLAIN, 12),
                viewRow -> "row-" + viewRow, text -> { },
                viewRow -> { }, viewRow -> { }, viewRow -> { }, viewRow -> { }, viewRow -> { }, viewRow -> false,
                viewRow -> { }, viewRow -> false, viewRow -> { }, viewRow -> false, viewRow -> { }, viewRow -> false,
                () -> false, List::of, (workspaceId, viewRow) -> { },
                viewRow -> opened[0] = viewRow, viewRow -> { }, viewRow -> true);

        JMenuItem item = menu.openChartItem(3);
        item.doClick();

        assertEquals("Open Chart", item.getText());
        assertEquals(3, opened[0]);
    }

    @Test
    void actionsThatPromptPerRowAreDisabledWhileSeveralRowsAreSelected() throws Exception {
        // A modal dialog pumps the event queue, so a queued refresh could re-sort the grid between
        // prompts and the rest of the selection would act on different strategies than were picked.
        JMenu menu = positionMenu(recordingMenu(new java.util.ArrayList<>(), row -> true), 0, new int[]{0, 1, 2});

        for (String label : List.of("Buy More at Market Price", "Buy More at Limit Price",
                "Sell at Market-Place", "Reposition Expired Stock", "Minimize Loss Impact...")) {
            JMenuItem item = findItem(menu, label);
            assertNotNull(item, label);
            assertFalse(item.isEnabled(), label + " must be single-row only");
            assertNotNull(item.getToolTipText(), label + " should say why it is unavailable");
        }
    }

    @Test
    void theSameActionsStayAvailableForASingleRow() throws Exception {
        JMenu menu = positionMenu(recordingMenu(new java.util.ArrayList<>(), row -> true), 0, new int[]{0});

        for (String label : List.of("Buy More at Market Price", "Sell at Market-Place", "Minimize Loss Impact...")) {
            JMenuItem item = findItem(menu, label);
            assertNotNull(item, label);
            assertTrue(item.isEnabled(), label + " must still work for one row");
        }
    }

    @Test
    void aBulkActionRunsForEverySelectedRowThatQualifies() throws Exception {
        List<Integer> placed = new java.util.ArrayList<>();
        // Rows 0 and 2 qualify; row 1 does not and must be left alone.
        JMenu menu = positionMenu(recordingMenu(placed, row -> row != 1), 0, new int[]{0, 1, 2});

        JMenuItem item = findItem(menu, "Place Pending Base Buy  (2 rows)");
        assertNotNull(item, "the label should count the rows that actually qualify");
        item.doClick();

        assertEquals(List.of(0, 2), placed);
    }

    @Test
    void aBulkActionKeepsItsPlainLabelForOneRow() throws Exception {
        List<Integer> placed = new java.util.ArrayList<>();
        JMenu menu = positionMenu(recordingMenu(placed, row -> true), 0, new int[]{0});

        JMenuItem item = findItem(menu, "Place Pending Base Buy");
        assertNotNull(item, "no row count is shown when only one row is selected");
        item.doClick();

        assertEquals(List.of(0), placed);
    }

    @Test
    void copyingRowTextTakesEverySelectedRowAndCellTextStaysSingleRow() throws Exception {
        StringBuilder copied = new StringBuilder();
        JTable table = new JTable(new DefaultTableModel(new Object[][]{{"AAPL"}, {"MSFT"}, {"NVDA"}}, new Object[]{"Symbol"}));
        StrategyGridContextMenu menu = new StrategyGridContextMenu(
                table, new Font("Dialog", Font.PLAIN, 12),
                viewRow -> "row-" + viewRow, copied::append,
                viewRow -> { }, viewRow -> { }, viewRow -> { }, viewRow -> { }, viewRow -> { }, viewRow -> false,
                viewRow -> { }, viewRow -> false, viewRow -> { }, viewRow -> false, viewRow -> { }, viewRow -> false,
                () -> false, List::of, (workspaceId, viewRow) -> { },
                viewRow -> { }, viewRow -> { }, viewRow -> true);

        JMenu copy = copyMenu(menu, 0, 0, new int[]{0, 2});

        JMenuItem cell = findItem(copy, "Cell Text");
        assertNotNull(cell);
        assertFalse(cell.isEnabled(), "one click landed on one cell");

        JMenuItem rows = findItem(copy, "Row Text  (2 rows)");
        assertNotNull(rows);
        rows.doClick();
        assertTrue(copied.toString().contains("row-0") && copied.toString().contains("row-2"), copied.toString());
    }

    /** A menu whose Place Pending Base Buy records the rows it ran for, gated by {@code eligible}. */
    private static StrategyGridContextMenu recordingMenu(List<Integer> placed, java.util.function.IntPredicate eligible) {
        JTable table = new JTable(new DefaultTableModel(
                new Object[][]{{"AAPL"}, {"MSFT"}, {"NVDA"}}, new Object[]{"Symbol"}));
        return new StrategyGridContextMenu(
                table, new Font("Dialog", Font.PLAIN, 12),
                viewRow -> "row-" + viewRow, text -> { },
                viewRow -> { }, viewRow -> { }, viewRow -> { }, viewRow -> { }, viewRow -> { }, viewRow -> false,
                placed::add, eligible,
                viewRow -> { }, viewRow -> false,
                viewRow -> { }, viewRow -> false,
                () -> false, List::of, (workspaceId, viewRow) -> { },
                viewRow -> { }, viewRow -> { }, viewRow -> true);
    }

    private JMenu positionMenuForRow(int row, boolean eligible) throws Exception {
        return positionMenuForRow(row, eligible, true);
    }

    private JMenu positionMenuForRow(int row, boolean eligible, boolean historySelected) throws Exception {
        return positionMenuForRow(row, eligible, historySelected, true);
    }

    private JMenu positionMenuForRow(int row, boolean eligible, boolean historySelected, boolean losingPosition)
            throws Exception {
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
                (workspaceId, viewRow) -> { },
                viewRow -> { },
                viewRow -> { },
                viewRow -> losingPosition
        );
        return positionMenu(menu, row, new int[]{row});
    }

    private static JMenu positionMenu(StrategyGridContextMenu menu, int row, int[] selectedRows) throws Exception {
        Method method = StrategyGridContextMenu.class.getDeclaredMethod("positionMenu", int.class, int[].class);
        method.setAccessible(true);
        return (JMenu) method.invoke(menu, row, selectedRows);
    }

    private static JMenu copyMenu(StrategyGridContextMenu menu, int row, int column, int[] selectedRows)
            throws Exception {
        Method method = StrategyGridContextMenu.class.getDeclaredMethod(
                "copyMenu", int.class, int.class, int[].class);
        method.setAccessible(true);
        return (JMenu) method.invoke(menu, row, column, selectedRows);
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
