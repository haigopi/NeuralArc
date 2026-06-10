package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategyGridLayoutPresenterTest {
    private final StrategyGridLayoutPresenter presenter = new StrategyGridLayoutPresenter();

    @Test
    void liveActionsColumnReleasesWidthSoPollingBarKeepsRoom() {
        StrategyGridLayoutPresenter.ColumnWidth polling = presenter.pollingColumnWidth();
        StrategyGridLayoutPresenter.ColumnWidth paperActions = presenter.actionsColumnWidth(true);
        StrategyGridLayoutPresenter.ColumnWidth liveActions = presenter.actionsColumnWidth(false);

        assertEquals(170, polling.preferred());
        assertEquals(150, polling.minimum());
        assertTrue(liveActions.minimum() < paperActions.minimum());
        assertTrue(liveActions.preferred() < paperActions.preferred());
        assertTrue(liveActions.minimum() + polling.minimum() <= paperActions.minimum());
    }

    @Test
    void actionButtonCountTracksPromoteVisibility() {
        assertEquals(5, presenter.actionButtonCount(true));
        assertEquals(4, presenter.actionButtonCount(false));
    }
}
