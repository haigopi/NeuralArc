package com.neuralarc.ui;

final class StrategyGridLayoutPresenter {
    static final int POLLING_COLUMN_INDEX = 7;
    static final int ACTIONS_COLUMN_INDEX = 11;

    ColumnWidth pollingColumnWidth() {
        return new ColumnWidth(170, 150);
    }

    ColumnWidth actionsColumnWidth(boolean promoteVisible) {
        return promoteVisible
                ? new ColumnWidth(520, 430)
                : new ColumnWidth(320, 260);
    }

    int actionButtonCount(boolean promoteVisible) {
        return promoteVisible ? 5 : 4;
    }

    record ColumnWidth(int preferred, int minimum) {
    }
}
