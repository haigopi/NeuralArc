package com.neuralarc.ui;

import com.neuralarc.agent.tools.PortfolioActionCatalog;
import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.neuralarc.ui.AverageDownTestEntries.position;
import static com.neuralarc.ui.AverageDownTestEntries.withState;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortfolioActionPreviewsTest {
    private final ManagedStrategy armedStopLoss = armedStopLoss("AAPL");
    private final ManagedStrategy unfilledEntry = withState(position("MSFT", 0, "0", "400"),
            StrategyStatus.ACTIVE, StrategyLifecycleState.BASE_BUY_PLACED);

    @Test
    void aPreviewCountsTheRowsTheMenuItselfWouldActOn() {
        PortfolioActionCatalog catalog = catalog(List.of(armedStopLoss, unfilledEntry));

        PortfolioActionCatalog.ActionPreview stopLosses = catalog.preview("Cancel All Stop Losses").orElseThrow();

        assertEquals(1, stopLosses.matchCount());
        assertEquals(List.of("AAPL"), stopLosses.symbols(), "the unfilled entry has no stop loss to cancel");
        assertTrue(stopLosses.description().contains("no automatic downside protection"));
        assertFalse(stopLosses.description().contains("<br>"), "the model reads sentences, not markup");
    }

    @Test
    void anActionThatMatchesNothingSaysWhyRatherThanGoingSilent() {
        PortfolioActionCatalog.ActionPreview preview =
                catalog(List.of(unfilledEntry)).preview("Cancel All Stop Losses").orElseThrow();

        assertEquals(0, preview.matchCount());
        assertTrue(preview.symbols().isEmpty());
        assertTrue(preview.emptyReason().toLowerCase().contains("no strategy"));
    }

    @Test
    void theActionCanBeNamedTheWayTheMenuDoesOrTheWayAModelWould() {
        PortfolioActionCatalog catalog = catalog(List.of(armedStopLoss));

        assertTrue(catalog.preview("Cancel All Stop Losses").isPresent());
        assertTrue(catalog.preview("cancel all stop losses").isPresent(), "case must not matter");
        assertTrue(catalog.preview("CANCEL_STOP_LOSSES").isPresent(), "nor the enum spelling");
        assertTrue(catalog.preview("cancel-all-stop-losses").isPresent(), "nor punctuation");
        assertTrue(catalog.preview("sell everything immediately").isEmpty(), "an invented action is not offered");
        assertTrue(catalog.preview("  ").isEmpty());
    }

    @Test
    void everyMenuActionIsListedWithItsOwnWording() {
        List<PortfolioActionCatalog.ActionPreview> all = catalog(List.of(armedStopLoss)).previewAll();

        assertEquals(PortfolioActionsSupport.BulkAction.values().length, all.size());
        assertTrue(all.stream().allMatch(preview -> !preview.name().isBlank() && !preview.description().isBlank()));
    }

    @Test
    void tradeHistoryCleanupReadsTheWiderScopeTheMenuGivesIt() {
        ManagedStrategy archived = withState(position("NIO", 0, "0", "4"),
                StrategyStatus.ARCHIVED, StrategyLifecycleState.COMPLETED);
        PortfolioActionPreviews previews = new PortfolioActionPreviews(List::of, () -> List.of(archived));

        assertEquals(0, previews.preview("Cancel All Stop Losses").orElseThrow().matchCount(),
                "the current tab is empty, so tab-scoped actions match nothing");
        assertTrue(previews.preview("Clean Trade History").orElseThrow().matchCount() > 0,
                "history cleanup looks at the whole scope, as the menu does");
    }

    @Test
    void theMenusMarkupIsStrippedButItsWordsAreKept() {
        assertEquals("One thing. Then another & a third.",
                PortfolioActionPreviews.plainText("One thing.<br><b>Then</b> another &amp; a third."));
        assertEquals("", PortfolioActionPreviews.plainText(null));
    }

    private static ManagedStrategy armedStopLoss(String symbol) {
        ManagedStrategy entry = withState(position(symbol, 10, "180", "170"),
                StrategyStatus.ACTIVE, StrategyLifecycleState.BASE_BUY_FILLED);
        entry.strategy.setAutomatedStopLossEnabled(true);
        return entry;
    }

    private static PortfolioActionCatalog catalog(List<ManagedStrategy> strategies) {
        return new PortfolioActionPreviews(() -> strategies, () -> strategies);
    }
}
