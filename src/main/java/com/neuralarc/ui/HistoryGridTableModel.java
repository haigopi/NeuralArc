package com.neuralarc.ui;

import javax.swing.table.AbstractTableModel;
import java.util.List;

final class HistoryGridTableModel extends AbstractTableModel {
    static final String[] COLUMNS = {
            "Symbol", "Broker + Mode", "Strategy Status", "Stage", "Side",
            "Order Status", "Qty", "Buy Price", "Fill Price", "Realized P&L", "When"
    };

    private final List<HistoryTablePresenter.HistoryRow> rows;

    HistoryGridTableModel(List<HistoryTablePresenter.HistoryRow> rows) {
        this.rows = rows;
    }

    @Override public int getRowCount()    { return rows.size(); }
    @Override public int getColumnCount() { return COLUMNS.length; }
    @Override public String getColumnName(int col) { return COLUMNS[col]; }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        HistoryTablePresenter.HistoryRow row = rows.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> row.symbol();
            case 1 -> row.brokerMode();
            case 2 -> row.strategyStatus();
            case 3 -> row.stage();
            case 4 -> row.side();
            case 5 -> row.orderStatus();
            case 6 -> row.quantity();
            case 7 -> row.buyPrice();
            case 8 -> row.fillPrice();
            case 9 -> row.realizedPnl();
            case 10 -> row.whenDisplay();
            default -> "";
        };
    }
}
