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

        assertEquals(230, polling.preferred());
        assertEquals(190, polling.minimum());
        assertEquals(StrategyGridActionLayout.columnWidth(true), paperActions.preferred());
        assertEquals(StrategyGridActionLayout.columnWidth(false), liveActions.preferred());
        assertEquals(paperActions.preferred(), paperActions.minimum());
        assertEquals(liveActions.preferred(), liveActions.minimum());
        assertTrue(liveActions.minimum() < paperActions.minimum());
        assertTrue(liveActions.preferred() < paperActions.preferred());
    }

    @Test
    void actionButtonCountTracksPromoteVisibility() {
        assertEquals(5, presenter.actionButtonCount(true));
        assertEquals(4, presenter.actionButtonCount(false));
    }
}
