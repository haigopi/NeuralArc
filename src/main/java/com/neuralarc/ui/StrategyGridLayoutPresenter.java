package com.neuralarc.ui;

final class StrategyGridLayoutPresenter {
    static final int POLLING_COLUMN_INDEX = 7;
    static final int ACTIONS_COLUMN_INDEX = 11;

    ColumnWidth pollingColumnWidth() {
        return new ColumnWidth(230, 190);
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
