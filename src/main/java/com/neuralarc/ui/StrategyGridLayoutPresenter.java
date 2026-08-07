package com.neuralarc.ui;

final class StrategyGridLayoutPresenter {
    // Must match StrategyGridTableModel.COLUMNS ordering.
    static final int STATUS_COLUMN_INDEX = 10;
    static final int POLLING_COLUMN_INDEX = 11;
    static final int ACTIONS_COLUMN_INDEX = 15;

    ColumnWidth pollingColumnWidth() {
        return new ColumnWidth(230, 120);
    }

    ColumnWidth actionsColumnWidth(boolean promoteVisible) {
        int width = StrategyGridActionLayout.columnWidth(promoteVisible);
        return new ColumnWidth(width, width);
    }

    int actionButtonCount(boolean promoteVisible) {
        return StrategyGridActionLayout.buttonCount(promoteVisible);
    }

    record ColumnWidth(int preferred, int minimum) {
    }
}
