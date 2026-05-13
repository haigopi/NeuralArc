package com.neuralarc.ui;

import com.neuralarc.model.Strategy;

import javax.swing.table.AbstractTableModel;
import java.util.List;
import java.util.function.Function;

final class StrategyGridTableModel extends AbstractTableModel {
    static final String[] COLUMNS = {
            "Symbol", "Status", "Shares", "Avg Cost", "Stock Price", "Market Value",
            "P&L", "Polling", "Broker + Mode", "Entry Source", "Exit Source", "Actions"
    };

    private final List<ManagedStrategy> strategies;
    private final Function<ManagedStrategy, String> statusLabelFn;
    private final Function<Strategy, String> brokerModeLabelFn;
    private final StrategyTablePresenter strategyTablePresenter;

    StrategyGridTableModel(
            List<ManagedStrategy> strategies,
            Function<ManagedStrategy, String> statusLabelFn,
            Function<Strategy, String> brokerModeLabelFn,
            StrategyTablePresenter strategyTablePresenter
    ) {
        this.strategies = strategies;
        this.statusLabelFn = statusLabelFn;
        this.brokerModeLabelFn = brokerModeLabelFn;
        this.strategyTablePresenter = strategyTablePresenter;
    }

    @Override public int getRowCount()    { return strategies.size(); }
    @Override public int getColumnCount() { return COLUMNS.length; }
    @Override public String getColumnName(int col) { return COLUMNS[col]; }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        ManagedStrategy entry = strategies.get(rowIndex);
        String statusLabel = statusLabelFn.apply(entry);
        return strategyTablePresenter.valueAt(
                entry.strategy,
                entry.cachedPosition(),
                entry.cachedLastSellPrice(),
                entry.cachedRealizedPnl(),
                columnIndex,
                statusLabel,
                brokerModeLabelFn.apply(entry.strategy)
        );
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false; // Actions handled via table MouseListener; no cell editor needed.
    }
}
