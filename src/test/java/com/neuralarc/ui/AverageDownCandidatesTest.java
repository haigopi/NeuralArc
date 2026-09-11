package com.neuralarc.ui;

import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Predicate;

import static com.neuralarc.ui.AverageDownTestEntries.inWorkspace;
import static com.neuralarc.ui.AverageDownTestEntries.position;
import static com.neuralarc.ui.AverageDownTestEntries.withState;
import static com.neuralarc.ui.AverageDownTestEntries.withWorkingSell;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AverageDownCandidatesTest {
    /** The rule the bulk action applies when it submits — the tests use the real one, not a stand-in. */
    private static final Predicate<ManagedStrategy> ELIGIBLE =
            PortfolioActionsSupport.BulkAction.AVERAGE_LOSING_POSITIONS::matches;

    @Test
    void listsEveryLosingPositionIncludingOnesWithAWorkingSell() {
        // The reported bug: losers with a working limit sell were dropped, so the count read 8 while
        // the grid showed more red rows.
        List<AverageDownCandidates.Candidate> candidates = AverageDownCandidates.collect(List.of(
                position("KLC", 1, "2.66", "2.58"),
                withWorkingSell(position("RKTO", 1, "0.85", "0.69")),
                position("CURI", 1, "3.53", "2.66"),
                position("VISN", 1, "6.10", "6.46"),
                position("WFF", 0, "0", "1.93")
        ), ELIGIBLE, id -> "");

        assertEquals(List.of("CURI", "KLC", "RKTO"), candidates.stream().map(AverageDownCandidates.Candidate::symbol).toList(),
                "every loser is listed; the winner and the empty row are not");
        AverageDownCandidates.Candidate locked = candidates.getLast();
        assertFalse(locked.selectable());
        assertTrue(locked.lockReason().contains("Limit sell working"), locked.lockReason());
        assertEquals(2, candidates.stream().filter(AverageDownCandidates.Candidate::selectable).count());
    }

    @Test
    void selectabilityIsExactlyTheBulkActionsOwnRule() {
        List<ManagedStrategy> scope = List.of(
                position("KLC", 1, "2.66", "2.58"),
                withWorkingSell(position("RKTO", 1, "0.85", "0.69")),
                withState(position("ESTC", 1, "93.56", "88.12"), StrategyStatus.ACTIVE, StrategyLifecycleState.STOP_LOSS_ACTIVE));

        for (AverageDownCandidates.Candidate candidate : AverageDownCandidates.collect(scope, ELIGIBLE, id -> "")) {
            assertEquals(ELIGIBLE.test(candidate.entry()), candidate.selectable(), candidate.symbol());
        }
    }

    @Test
    void aCancelledSellNoLongerLocksThePosition() {
        ManagedStrategy cancelled = withWorkingSell(position("AMA", 1, "34.12", "21.33"));
        cancelled.strategy.setLatestOrderStatus("canceled");

        AverageDownCandidates.Candidate candidate =
                AverageDownCandidates.collect(List.of(cancelled), ELIGIBLE, id -> "").getFirst();

        assertTrue(candidate.selectable());
    }

    @Test
    void ordersSelectableRowsFirstThenTheDeepestLoss() {
        List<AverageDownCandidates.Candidate> candidates = AverageDownCandidates.collect(List.of(
                withWorkingSell(position("DEEP", 1, "100", "10")),
                position("SMALL", 1, "10", "9"),
                position("BIG", 1, "50", "20")
        ), ELIGIBLE, id -> "");

        assertEquals(List.of("BIG", "SMALL", "DEEP"),
                candidates.stream().map(AverageDownCandidates.Candidate::symbol).toList());
    }

    @Test
    void namesWhatWouldHaveToChangeForEachLockedPosition() {
        assertTrue(AverageDownCandidates.lockReason(
                withState(position("A", 1, "2", "1"), StrategyStatus.ACTIVE, StrategyLifecycleState.SELL_PARTIALLY_FILLED))
                .contains("Exit in progress"));
        assertTrue(AverageDownCandidates.lockReason(
                withState(position("B", 1, "2", "1"), StrategyStatus.FAILED, StrategyLifecycleState.FAILED))
                .contains("Strategy failed"));
        assertTrue(AverageDownCandidates.lockReason(
                withState(position("C", 1, "2", "1"), StrategyStatus.CREATED, StrategyLifecycleState.CREATED))
                .contains("Only active or paused"));
    }

    @Test
    void resolvesEachRowsWorkspaceName() {
        List<AverageDownCandidates.Candidate> candidates = AverageDownCandidates.collect(List.of(
                inWorkspace(position("KLC", 1, "2.66", "2.58"), "orb"),
                position("CURI", 1, "3.53", "2.66")
        ), ELIGIBLE, id -> id == null ? "Unassigned" : "ORB Engine");

        assertEquals(List.of("Unassigned", "ORB Engine"),
                candidates.stream().map(AverageDownCandidates.Candidate::workspaceLabel).toList());
    }

    @Test
    void reportsWhetherAnyLosingPositionExists() {
        assertTrue(AverageDownCandidates.anyLosingOpenPosition(List.of(withWorkingSell(position("RKTO", 1, "0.85", "0.69")))),
                "a locked loser still counts, so the dialog can explain why it cannot be bought");
        assertFalse(AverageDownCandidates.anyLosingOpenPosition(List.of(position("VISN", 1, "6.10", "6.46"))));
        assertFalse(AverageDownCandidates.anyLosingOpenPosition(null));
    }
}
