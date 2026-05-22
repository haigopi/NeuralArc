package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import java.awt.Color;
import java.time.Instant;
import java.util.List;

import static javax.swing.SwingConstants.RIGHT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryRowStylerTest {
    private final HistoryRowStyler styler = new HistoryRowStyler();
    private final HistoryRowStyler.Palette palette = new HistoryRowStyler.Palette(
            Color.BLUE,
            Color.WHITE,
            Color.DARK_GRAY,
            Color.WHITE,
            Color.BLACK,
            Color.GREEN,
            Color.BLACK,
            Color.RED,
            Color.WHITE,
            Color.GRAY,
            Color.BLACK,
            Color.ORANGE,
            Color.BLACK,
            Color.LIGHT_GRAY,
            Color.BLACK,
            Color.YELLOW,
            Color.BLACK
    );

    @Test
    void repeatedSymbolRowsBlankOutFollowingSymbols() {
        JTable table = new JTable(new DefaultTableModel(2, 11));
        List<HistoryTablePresenter.HistoryRow> rows = List.of(
                new HistoryTablePresenter.HistoryRow("AAPL", "AAPL", "Paper", "Active", "Base Buy", "BUY", "Filled", "10", "100", "100", "-", "now", Instant.now(), 1, HistoryTablePresenter.HistoryRowStyle.BUY),
                new HistoryTablePresenter.HistoryRow("AAPL", "AAPL", "Paper", "Active", "Target Sell", "SELL", "Filled", "10", "100", "110", "10", "now", Instant.now(), 0, HistoryTablePresenter.HistoryRowStyle.SELL_GAIN)
        );

        HistoryRowStyler.CellStyle first = styler.style(table, 0, 0, false, rows.get(0), rows, palette);
        HistoryRowStyler.CellStyle second = styler.style(table, 1, 0, false, rows.get(1), rows, palette);

        assertTrue(first.bold());
        assertFalse(first.blankText());
        assertTrue(second.blankText());
    }

    @Test
    void subtotalStageCellIsBoldItalic() {
        JTable table = new JTable(new DefaultTableModel(1, 11));
        List<HistoryTablePresenter.HistoryRow> rows = List.of(
                new HistoryTablePresenter.HistoryRow("AAPL", "AAPL", "", "", "Subtotal", "", "", "", "", "", "10", "", null, 3, HistoryTablePresenter.HistoryRowStyle.SUBTOTAL)
        );

        HistoryRowStyler.CellStyle subtotalStage = styler.style(table, 0, 3, false, rows.get(0), rows, palette);

        assertTrue(subtotalStage.bold());
        assertTrue(subtotalStage.italic());
    }

    @Test
    void numericHistoryColumnsAreRightAligned() {
        JTable table = new JTable(new DefaultTableModel(1, 11));
        List<HistoryTablePresenter.HistoryRow> rows = List.of(
                new HistoryTablePresenter.HistoryRow("AAPL", "AAPL", "Paper", "Active", "Target Sell", "SELL", "Filled", "10", "100", "110", "10", "now", Instant.now(), 0, HistoryTablePresenter.HistoryRowStyle.SELL_GAIN)
        );

        assertEquals(RIGHT, styler.style(table, 0, 6, false, rows.getFirst(), rows, palette).horizontalAlignment());
        assertEquals(RIGHT, styler.style(table, 0, 7, false, rows.getFirst(), rows, palette).horizontalAlignment());
        assertEquals(RIGHT, styler.style(table, 0, 8, false, rows.getFirst(), rows, palette).horizontalAlignment());
        assertEquals(RIGHT, styler.style(table, 0, 9, false, rows.getFirst(), rows, palette).horizontalAlignment());
    }
}
