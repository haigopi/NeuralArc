package com.neuralarc.analytics;

import com.neuralarc.model.MarketBar;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecentLowTest {
    @Test
    void takesTheLowestOfTheLastWeekOfSessions() {
        List<MarketBar> sessions = List.of(
                bar("90.00"), bar("80.00"),  // older than a week; ignored
                bar("99.00"), bar("97.50"), bar("98.20"), bar("96.40"), bar("97.10"));

        assertEquals(new BigDecimal("96.40"), RecentLow.weekLow(sessions, BigDecimal.ZERO));
    }

    @Test
    void todaysLowCountsWhenItIsTheLowestSoFar() {
        List<MarketBar> sessions = List.of(bar("99.00"), bar("97.50"));

        assertEquals(new BigDecimal("95.10"), RecentLow.weekLow(sessions, new BigDecimal("95.10")));
        assertEquals(new BigDecimal("97.50"), RecentLow.weekLow(sessions, new BigDecimal("99.90")));
    }

    @Test
    void withoutUsableHistoryThereIsNoWeeksLow() {
        assertEquals(BigDecimal.ZERO, RecentLow.weekLow(null, BigDecimal.ZERO));
        assertEquals(BigDecimal.ZERO, RecentLow.weekLow(List.of(), null));
        assertEquals(new BigDecimal("12.00"), RecentLow.weekLow(List.of(), new BigDecimal("12.00")));
    }

    @Test
    void todaysLowIsReadFromThatDaysOwnBar() {
        List<MarketBar> sessions = List.of(dated("2026-09-15", "97.50"), dated("2026-09-16", "95.10"));

        assertEquals(new BigDecimal("95.10"), RecentLow.sessionLow(sessions, LocalDate.of(2026, 9, 16)));
        assertEquals(new BigDecimal("97.50"), RecentLow.sessionLow(sessions, LocalDate.of(2026, 9, 15)));
    }

    @Test
    void aSessionThatHasNotTradedYetHasNoLow() {
        List<MarketBar> sessions = List.of(dated("2026-09-15", "97.50"));

        // Before the session trades there is no bar for it, and a guessed price is worse than none.
        assertEquals(BigDecimal.ZERO, RecentLow.sessionLow(sessions, LocalDate.of(2026, 9, 16)));
        assertEquals(BigDecimal.ZERO, RecentLow.sessionLow(null, LocalDate.of(2026, 9, 16)));
        assertEquals(BigDecimal.ZERO, RecentLow.sessionLow(sessions, null));
    }

    private static MarketBar bar(String low) {
        return new MarketBar("NVDA", "", new BigDecimal("100.00"), new BigDecimal("101.00"),
                new BigDecimal(low), new BigDecimal("100.50"), BigDecimal.ZERO);
    }

    private static MarketBar dated(String isoDate, String low) {
        return new MarketBar("NVDA", isoDate + "T04:00:00Z", new BigDecimal("100.00"), new BigDecimal("101.00"),
                new BigDecimal(low), new BigDecimal("100.50"), BigDecimal.ZERO);
    }
}
