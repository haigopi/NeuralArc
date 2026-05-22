package com.neuralarc.ui;

import javax.swing.BorderFactory;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.util.List;

public final class HistoryRowStyler {
    public CellStyle style(
            JTable table,
            int viewRow,
            int column,
            boolean isSelected,
            HistoryTablePresenter.HistoryRow rowData,
            List<HistoryTablePresenter.HistoryRow> rows,
            Palette palette
    ) {
        boolean subtotal = rowData.style() == HistoryTablePresenter.HistoryRowStyle.SUBTOTAL;
        boolean firstInGroup = isFirstRowOfGroup(table, viewRow, rows);
        Color background = isSelected ? palette.selectionBackground() : backgroundForRow(rowData, palette);
        Color foreground = isSelected ? palette.selectionForeground() : foregroundForRow(rowData, palette);
        boolean blankText = column == 0 && (subtotal || !firstInGroup);
        boolean bold = (column == 0 && !subtotal && firstInGroup) || (subtotal && column == 3);
        boolean italic = subtotal && column == 3;
        return new CellStyle(
                background,
                foreground,
                horizontalAlignment(column),
                cellBorder(table, viewRow, column, rowData, rows, palette.groupBorder()),
                blankText,
                bold,
                italic
        );
    }

    private boolean isFirstRowOfGroup(JTable table, int viewRow, List<HistoryTablePresenter.HistoryRow> rows) {
        if (viewRow == 0) {
            return true;
        }
        int previousModelRow = table.convertRowIndexToModel(viewRow - 1);
        int currentModelRow = table.convertRowIndexToModel(viewRow);
        if (previousModelRow < 0 || previousModelRow >= rows.size()) {
            return true;
        }
        if (currentModelRow < 0 || currentModelRow >= rows.size()) {
            return true;
        }
        return !rows.get(previousModelRow).groupKey().equalsIgnoreCase(rows.get(currentModelRow).groupKey());
    }

    private Border cellBorder(
            JTable table,
            int viewRow,
            int column,
            HistoryTablePresenter.HistoryRow rowData,
            List<HistoryTablePresenter.HistoryRow> rows,
            Color groupBorder
    ) {
        int top = 1;
        if (viewRow == 0) {
            top = 3;
        } else {
            int previousModelRow = table.convertRowIndexToModel(viewRow - 1);
            if (previousModelRow >= 0 && previousModelRow < rows.size()) {
                HistoryTablePresenter.HistoryRow previous = rows.get(previousModelRow);
                if (!previous.groupKey().equalsIgnoreCase(rowData.groupKey())) {
                    top = 3;
                }
            }
        }
        int horizontalPadding = isNumericColumn(column) ? 4 : 8;
        return BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(top, 0, 0, 0, groupBorder),
                new EmptyBorder(6, horizontalPadding, 6, horizontalPadding)
        );
    }

    private int horizontalAlignment(int column) {
        return isNumericColumn(column) ? SwingConstants.RIGHT : SwingConstants.LEFT;
    }

    private boolean isNumericColumn(int column) {
        return column >= 6 && column <= 9;
    }

    private Color backgroundForRow(HistoryTablePresenter.HistoryRow row, Palette palette) {
        return switch (row.style()) {
            case BUY -> palette.buyBackground();
            case SELL_GAIN -> palette.sellGainBackground();
            case SELL_LOSS -> palette.sellLossBackground();
            case SELL_NEUTRAL -> palette.sellNeutralBackground();
            case FAILED -> palette.failedBackground();
            case COMPLETED -> palette.completedBackground();
            case SUBTOTAL -> palette.subtotalBackground();
        };
    }

    private Color foregroundForRow(HistoryTablePresenter.HistoryRow row, Palette palette) {
        return switch (row.style()) {
            case BUY -> palette.buyForeground();
            case SELL_GAIN -> palette.sellGainForeground();
            case SELL_LOSS -> palette.sellLossForeground();
            case SELL_NEUTRAL -> palette.sellNeutralForeground();
            case FAILED -> palette.failedForeground();
            case COMPLETED -> palette.completedForeground();
            case SUBTOTAL -> palette.subtotalForeground();
        };
    }

    public record Palette(
            Color selectionBackground,
            Color selectionForeground,
            Color groupBorder,
            Color buyBackground,
            Color buyForeground,
            Color sellGainBackground,
            Color sellGainForeground,
            Color sellLossBackground,
            Color sellLossForeground,
            Color sellNeutralBackground,
            Color sellNeutralForeground,
            Color failedBackground,
            Color failedForeground,
            Color completedBackground,
            Color completedForeground,
            Color subtotalBackground,
            Color subtotalForeground
    ) {
    }

    public record CellStyle(
            Color background,
            Color foreground,
            int horizontalAlignment,
            Border border,
            boolean blankText,
            boolean bold,
            boolean italic
    ) {
    }
}
