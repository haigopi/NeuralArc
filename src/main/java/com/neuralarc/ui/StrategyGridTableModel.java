package com.neuralarc.ui;

import com.neuralarc.model.Position;

import javax.swing.table.AbstractTableModel;
import java.util.List;
import java.util.function.Function;

final class StrategyGridTableModel extends AbstractTableModel {
    static final String[] COLUMNS = {
            "Shares", "Symbol", "Avg Cost", "Stock Price", "Market Value", "P&L",
            "Status", "Polling", "Time In Force", "Entry Source", "Exit Source", "Actions"
    };

    // Maps the grid column order to the presenter column contract used by StrategyTablePresenter.
    private static final int[] MODEL_TO_PRESENTER_COLUMN_INDEX = {
            2, 0, 3, 4, 5, 6, 1, 7, 8, 9, 10, 11
    };

    private final List<ManagedStrategy> strategies;
    private final Function<ManagedStrategy, String> statusLabelFn;
    private final StrategyTablePresenter strategyTablePresenter;

    StrategyGridTableModel(
            List<ManagedStrategy> strategies,
            Function<ManagedStrategy, String> statusLabelFn,
            StrategyTablePresenter strategyTablePresenter
    ) {
        this.strategies = strategies;
        this.statusLabelFn = statusLabelFn;
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
                displayPosition(entry),
                entry.cachedLastSellPrice(),
                entry.cachedRealizedPnl(),
                presenterColumnIndex(columnIndex),
                statusLabel
        );
    }

    private Position displayPosition(ManagedStrategy entry) {
        if (entry == null || entry.strategy == null) {
            return new Position("");
        }
        if (GapRocketDisplaySupport.suppressBrokerPosition(entry.strategy)) {
            return new Position(entry.strategy.symbol());
        }
        return entry.cachedPosition();
    }

    private int presenterColumnIndex(int modelColumnIndex) {
        if (modelColumnIndex < 0 || modelColumnIndex >= MODEL_TO_PRESENTER_COLUMN_INDEX.length) {
            return modelColumnIndex;
        }
        return MODEL_TO_PRESENTER_COLUMN_INDEX[modelColumnIndex];
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false; // Actions handled via table MouseListener; no cell editor needed.
    }
}
