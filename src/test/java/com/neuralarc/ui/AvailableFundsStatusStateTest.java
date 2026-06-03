package com.neuralarc.ui;

import com.neuralarc.model.ApplicationMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvailableFundsStatusStateTest {
    @Test
    void storesFundsSeparatelyForPaperAndLiveModes() {
        AvailableFundsStatusState state = new AvailableFundsStatusState();

        state.update(ApplicationMode.PAPER, Optional.of(new BigDecimal("25000.00")));

        assertEquals("Funds Available: $25000.00", state.textFor(ApplicationMode.PAPER));
        assertEquals(AvailableFundsStatusState.EMPTY_TEXT, state.textFor(ApplicationMode.LIVE));

        state.update(ApplicationMode.LIVE, Optional.of(new BigDecimal("1000.50")));

        assertEquals("Funds Available: $25000.00", state.textFor(ApplicationMode.PAPER));
        assertEquals("Funds Available: $1000.50", state.textFor(ApplicationMode.LIVE));
    }

    @Test
    void throttlesFetchesPerMode() {
        AvailableFundsStatusState state = new AvailableFundsStatusState();

        state.markFetchStarted(ApplicationMode.PAPER, 1000L);

        assertFalse(state.shouldFetch(ApplicationMode.PAPER, 1500L, 1000L));
        assertTrue(state.shouldFetch(ApplicationMode.LIVE, 1500L, 1000L));
        assertTrue(state.shouldFetch(ApplicationMode.PAPER, 2000L, 1000L));
    }

    @Test
    void clearResetsTextAndFetchThrottleForOneMode() {
        AvailableFundsStatusState state = new AvailableFundsStatusState();
        state.update(ApplicationMode.LIVE, Optional.of(new BigDecimal("1000.50")));
        state.markFetchStarted(ApplicationMode.LIVE, 1000L);

        assertEquals(AvailableFundsStatusState.EMPTY_TEXT, state.clear(ApplicationMode.LIVE));

        assertEquals(AvailableFundsStatusState.EMPTY_TEXT, state.textFor(ApplicationMode.LIVE));
        assertTrue(state.shouldFetch(ApplicationMode.LIVE, 1001L, 1000L));
    }
}
