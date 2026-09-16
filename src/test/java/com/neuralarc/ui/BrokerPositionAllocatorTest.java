package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BrokerPositionAllocatorTest {
    private static final Instant OLDER = Instant.parse("2026-05-01T10:00:00Z");
    private static final Instant NEWER = Instant.parse("2026-05-06T10:00:00Z");

    @Test
    void singleStrategyKeepsTheWholePosition() {
        Map<String, Integer> allocation = BrokerPositionAllocator.allocate(
                10, List.of(new BrokerPositionAllocator.Claim("only", 10, OLDER)));

        assertEquals(Map.of("only", 10), allocation);
    }

    @Test
    void onePositionIsNotCountedTwiceWhenTwoRowsShareTheSymbol() {
        // The AVGO case: two rows, one broker position of 10 that only one of them actually bought.
        Map<String, Integer> allocation = BrokerPositionAllocator.allocate(10, List.of(
                new BrokerPositionAllocator.Claim("holder", 10, OLDER),
                new BrokerPositionAllocator.Claim("phantom", 0, NEWER)
        ));

        assertEquals(10, allocation.get("holder"));
        assertEquals(0, allocation.get("phantom"));
        assertEquals(10, allocation.values().stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void partialSetsEachKeepTheirOwnShares() {
        Map<String, Integer> allocation = BrokerPositionAllocator.allocate(15, List.of(
                new BrokerPositionAllocator.Claim("first", 10, OLDER),
                new BrokerPositionAllocator.Claim("second", 5, NEWER)
        ));

        assertEquals(10, allocation.get("first"));
        assertEquals(5, allocation.get("second"));
    }

    @Test
    void claimsBeyondTheBrokerPositionGetNothing() {
        Map<String, Integer> allocation = BrokerPositionAllocator.allocate(10, List.of(
                new BrokerPositionAllocator.Claim("first", 10, OLDER),
                new BrokerPositionAllocator.Claim("second", 10, NEWER)
        ));

        assertEquals(10, allocation.get("first"));
        assertEquals(0, allocation.get("second"));
    }

    @Test
    void sharesTheBrokerHoldsBeyondEveryClaimStayOnThePrimaryRow() {
        Map<String, Integer> allocation = BrokerPositionAllocator.allocate(12, List.of(
                new BrokerPositionAllocator.Claim("first", 6, OLDER),
                new BrokerPositionAllocator.Claim("second", 4, NEWER)
        ));

        assertEquals(8, allocation.get("first"), "the 2 unaccounted shares stay with the primary row");
        assertEquals(4, allocation.get("second"));
        assertEquals(12, allocation.values().stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void whenNoRowClaimsAnythingTheOldestVisibleRowShowsThePosition() {
        Map<String, Integer> allocation = BrokerPositionAllocator.allocate(10, List.of(
                new BrokerPositionAllocator.Claim("newer", 0, NEWER),
                new BrokerPositionAllocator.Claim("older", 0, OLDER)
        ));

        assertEquals(10, allocation.get("older"));
        assertEquals(0, allocation.get("newer"));
    }

    @Test
    void unclaimedSharesSkipAHiddenRowSoThePositionStaysOnScreen() {
        // The NVDA case: the oldest row is a completed-and-restarting strategy the grid never draws.
        // Left there, 10 real shares vanished from the screen while still counting in the totals.
        Map<String, Integer> allocation = BrokerPositionAllocator.allocate(10, List.of(
                new BrokerPositionAllocator.Claim("hidden-oldest", 0, OLDER, true),
                new BrokerPositionAllocator.Claim("visible", 0, NEWER, false)
        ));

        assertEquals(10, allocation.get("visible"));
        assertEquals(0, allocation.get("hidden-oldest"));
        assertEquals(10, allocation.values().stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void aHiddenRowStillKeepsSharesItsOwnFillsAccountFor() {
        // Visibility only steers the unclaimed remainder; it must not move shares a row really bought,
        // or the same shares would be counted on two rows.
        Map<String, Integer> allocation = BrokerPositionAllocator.allocate(12, List.of(
                new BrokerPositionAllocator.Claim("hidden-holder", 10, OLDER, true),
                new BrokerPositionAllocator.Claim("visible", 0, NEWER, false)
        ));

        assertEquals(10, allocation.get("hidden-holder"));
        assertEquals(2, allocation.get("visible"), "only the 2 unaccounted shares move to the visible row");
    }

    @Test
    void whenEveryRowIsHiddenTheTotalsStillMatchTheBroker() {
        Map<String, Integer> allocation = BrokerPositionAllocator.allocate(10, List.of(
                new BrokerPositionAllocator.Claim("hidden-older", 0, OLDER, true),
                new BrokerPositionAllocator.Claim("hidden-newer", 0, NEWER, true)
        ));

        assertEquals(10, allocation.values().stream().mapToInt(Integer::intValue).sum(),
                "nothing is dropped even when no row can show it");
        assertEquals(10, allocation.get("hidden-older"));
    }

    @Test
    void closedBrokerPositionLeavesEveryRowEmpty() {
        Map<String, Integer> allocation = BrokerPositionAllocator.allocate(0, List.of(
                new BrokerPositionAllocator.Claim("first", 10, OLDER),
                new BrokerPositionAllocator.Claim("second", 5, NEWER)
        ));

        assertEquals(0, allocation.get("first"));
        assertEquals(0, allocation.get("second"));
    }

    @Test
    void allocationIsStableRegardlessOfInputOrder() {
        List<BrokerPositionAllocator.Claim> claims = List.of(
                new BrokerPositionAllocator.Claim("a", 5, OLDER),
                new BrokerPositionAllocator.Claim("b", 5, NEWER)
        );

        assertEquals(
                BrokerPositionAllocator.allocate(7, claims),
                BrokerPositionAllocator.allocate(7, List.of(claims.get(1), claims.get(0)))
        );
    }
}
