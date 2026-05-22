package com.neuralarc.ui;

import com.neuralarc.model.StrategyStatus;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategyActionsPresenterTest {
    private final StrategyActionsPresenter presenter = new StrategyActionsPresenter();

    @Test
    void archivedStrategiesDisableToggleAndPromotion() {
        StrategyActionsPresenter.StrategyActionsViewModel viewModel = presenter.present(
                new StrategyActionsPresenter.StrategyActionsState(StrategyStatus.ARCHIVED, false, false, "", true, false, true)
        );

        assertEquals("Archived", viewModel.toggleText());
        assertFalse(viewModel.toggleEnabled());
        assertFalse(viewModel.promoteEnabled());
    }

    @Test
    void busyStateShowsBusyTextAndDisablesPromotionWhenNotPaper() {
        StrategyActionsPresenter.StrategyActionsViewModel viewModel = presenter.present(
                new StrategyActionsPresenter.StrategyActionsState(StrategyStatus.PAUSED, false, true, "Canceling...", false, true, true)
        );

        assertEquals("Canceling...", viewModel.toggleText());
        assertFalse(viewModel.toggleEnabled());
        assertFalse(viewModel.promoteEnabled());
    }

    @Test
    void pausedPaperStrategiesShowResumeAndAllowPromotion() {
        StrategyActionsPresenter.StrategyActionsViewModel viewModel = presenter.present(
                new StrategyActionsPresenter.StrategyActionsState(StrategyStatus.PAUSED, false, false, "", true, true, true)
        );

        assertEquals("Resume", viewModel.toggleText());
        assertTrue(viewModel.toggleEnabled());
        assertTrue(viewModel.promoteEnabled());
    }

    @Test
    void manuallyCanceledStrategiesWithPositionShowResume() {
        StrategyActionsPresenter.StrategyActionsViewModel viewModel = presenter.present(
                new StrategyActionsPresenter.StrategyActionsState(StrategyStatus.PAUSED, true, false, "", true, true, true)
        );

        assertEquals("Resume", viewModel.toggleText());
        assertTrue(viewModel.toggleEnabled());
    }

    @Test
    void pausedWithoutPositionShowsPlaceLimitBuyAgain() {
        StrategyActionsPresenter.StrategyActionsViewModel viewModel = presenter.present(
                new StrategyActionsPresenter.StrategyActionsState(StrategyStatus.PAUSED, false, false, "", true, false, true)
        );

        assertEquals("Place Limit Buy Again", viewModel.toggleText());
    }

    @Test
    void manuallyCanceledWithoutPositionShowsPlaceLimitBuyAgain() {
        StrategyActionsPresenter.StrategyActionsViewModel viewModel = presenter.present(
                new StrategyActionsPresenter.StrategyActionsState(StrategyStatus.PAUSED, true, false, "", true, false, true)
        );

        assertEquals("Place Limit Buy Again", viewModel.toggleText());
    }

    @Test
    void completedStatusDisablesToggleAndPromotion() {
        StrategyActionsPresenter.StrategyActionsViewModel viewModel = presenter.present(
                new StrategyActionsPresenter.StrategyActionsState(StrategyStatus.COMPLETED, false, false, "", true, true, true)
        );

        assertEquals("Completed", viewModel.toggleText());
        assertEquals("icons/verify.svg", viewModel.toggleIconPath());
        assertFalse(viewModel.toggleEnabled());
        assertFalse(viewModel.promoteEnabled());
    }

    @Test
    void activeStatusKeepsPauseIconForCancelAction() {
        StrategyActionsPresenter.StrategyActionsViewModel viewModel = presenter.present(
                new StrategyActionsPresenter.StrategyActionsState(StrategyStatus.ACTIVE, false, false, "", true, true, true)
        );

        assertEquals("icons/pause.svg", viewModel.toggleIconPath());
    }

    @Test
    void failedStrategyUsesLatestBrokerStatusForToggleText() {
        StrategyActionsPresenter.StrategyActionsViewModel viewModel = presenter.present(
                new StrategyActionsPresenter.StrategyActionsState(StrategyStatus.FAILED, false, false, "", true, false, true, "expired")
        );

        assertEquals("Expired", viewModel.toggleText());
        assertFalse(viewModel.toggleEnabled());
    }

    @Test
    void failedInvalidStrategyShowsInvalidToggleText() {
        StrategyActionsPresenter.StrategyActionsViewModel viewModel = presenter.present(
                new StrategyActionsPresenter.StrategyActionsState(StrategyStatus.FAILED, false, false, "", true, false, true, "invalid")
        );

        assertEquals("Invalid", viewModel.toggleText());
        assertFalse(viewModel.toggleEnabled());
    }

    @Test
    void pausedStrategyDisablesResumeWhenMarketClosed() {
        StrategyActionsPresenter.StrategyActionsViewModel viewModel = presenter.present(
                new StrategyActionsPresenter.StrategyActionsState(StrategyStatus.PAUSED, false, false, "", true, true, false)
        );

        assertEquals("Resume", viewModel.toggleText());
        assertFalse(viewModel.toggleEnabled());
    }

    @Test
    void marketClosedDisablesSellButAllowsPromote() {
        StrategyActionsPresenter.StrategyActionsViewModel viewModel = presenter.present(
                new StrategyActionsPresenter.StrategyActionsState(StrategyStatus.ACTIVE, false, false, "", true, true, false)
        );

        assertTrue(viewModel.toggleEnabled());
        assertFalse(viewModel.sellEnabled());
        assertTrue(viewModel.promoteEnabled());
    }
}
