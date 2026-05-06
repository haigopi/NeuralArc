package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategyActionsPresenterTest {
    private final StrategyActionsPresenter presenter = new StrategyActionsPresenter();

    @Test
    void archivedStrategiesDisableToggleAndPromotion() {
        StrategyActionsPresenter.StrategyActionsViewModel viewModel = presenter.present(
                new StrategyActionsPresenter.StrategyActionsState(true, false, false, "", true, false)
        );

        assertEquals("Archived", viewModel.toggleText());
        assertFalse(viewModel.toggleEnabled());
        assertFalse(viewModel.promoteEnabled());
    }

    @Test
    void busyStateShowsBusyTextAndDisablesPromotionWhenNotPaper() {
        StrategyActionsPresenter.StrategyActionsViewModel viewModel = presenter.present(
                new StrategyActionsPresenter.StrategyActionsState(false, true, true, "Canceling...", false, true)
        );

        assertEquals("Canceling...", viewModel.toggleText());
        assertTrue(viewModel.toggleEnabled());
        assertFalse(viewModel.promoteEnabled());
    }

    @Test
    void pausedPaperStrategiesShowResumeAndAllowPromotion() {
        StrategyActionsPresenter.StrategyActionsViewModel viewModel = presenter.present(
                new StrategyActionsPresenter.StrategyActionsState(false, true, false, "", true, true)
        );

        assertEquals("Resume", viewModel.toggleText());
        assertTrue(viewModel.toggleEnabled());
        assertTrue(viewModel.promoteEnabled());
    }
}
