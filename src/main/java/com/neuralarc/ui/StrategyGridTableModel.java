package com.neuralarc.ui;

import com.neuralarc.model.MarketBar;
import com.neuralarc.model.Position;

import javax.swing.table.AbstractTableModel;
import java.util.List;
import java.util.function.Function;

final class StrategyGridTableModel extends AbstractTableModel {
    static final String[] COLUMNS = {
            "Shares", "Symbol", "Buy Down Price", "Avg Cost", "Open", "Today's Low", "Today's High",
            "Current Price", "P&L", "Market Value",
            "Status", "Polling", "Time In Force", "Entry Source", "Exit Source", "Actions"
    };

    // Maps the grid column order to the presenter column contract used by StrategyTablePresenter.
    // Presenter indices are a stable contract (0 symbol, 1/11 status, 2 shares, 3 avg cost,
    // 4 current price, 5 market value, 6 P&L, 7 polling, 8 TIF, 9 entry, 10 exit, 12 buy down,
    // 13 open, 14 low, 15 high) — display order is expressed here, not by renumbering them.
    private static final int[] MODEL_TO_PRESENTER_COLUMN_INDEX = {
            2, 0, 12, 3, 13, 14, 15, 4, 6, 5, 1, 7, 8, 9, 10, 11
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
                statusLabel,
                dayPrices(entry)
        );
    }

    private StrategyTablePresenter.DayPrices dayPrices(ManagedStrategy entry) {
        if (entry == null) {
            return StrategyTablePresenter.DayPrices.EMPTY;
        }
        MarketBar dailyBar = entry.cachedDailyBar();
        return new StrategyTablePresenter.DayPrices(
                entry.cachedBaseBuyExecutedPrice(),
                dailyBar == null ? null : dailyBar.open(),
                dailyBar == null ? null : dailyBar.low(),
                dailyBar == null ? null : dailyBar.high()
        );
    }

    private Position displayPosition(ManagedStrategy entry) {
        if (entry == null || entry.strategy == null) {
            return new Position("");
        }
        if (GapRocketDisplaySupport.suppressBrokerPosition(entry.strategy)) {
            Position scannerPosition = new Position(entry.strategy.symbol());
            Position cached = entry.cachedPosition();
            if (cached != null && cached.getLastPrice().compareTo(java.math.BigDecimal.ZERO) > 0) {
                scannerPosition.setLastPrice(cached.getLastPrice());
            }
            return scannerPosition;
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
