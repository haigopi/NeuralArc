package com.neuralarc.analytics;

import com.neuralarc.model.MarketBar;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TodaySessionTest {
    @Test
    void usesTodaysDailyBarForTheOpenAndTheLowestLowAcrossAllBars() {
        MarketBar daily = bar("5.98", "5.96");
        List<MarketBar> intraday = List.of(bar("5.90", "5.95"), bar("5.97", "5.99"));

        assertEquals(new BigDecimal("5.98"), TodaySession.open(daily, intraday));
        assertEquals(new BigDecimal("5.95"), TodaySession.low(daily, intraday));
    }

    @Test
    void fallsBackToIntradayBarsWhenThereIsNoDailyBarYet() {
        List<MarketBar> intraday = List.of(bar("5.90", "5.95"), bar("5.97", "5.93"));

        assertEquals(new BigDecimal("5.90"), TodaySession.open(null, intraday));
        assertEquals(new BigDecimal("5.93"), TodaySession.low(null, intraday));
    }

    @Test
    void isZeroWhenNothingHasTraded() {
        assertEquals(BigDecimal.ZERO, TodaySession.open(null, null));
        assertEquals(BigDecimal.ZERO, TodaySession.low(null, List.of()));
    }

    private static MarketBar bar(String open, String low) {
        return new MarketBar("BMBL", "2026-10-13T14:00:00Z", new BigDecimal(open), new BigDecimal("6.10"),
                new BigDecimal(low), new BigDecimal("6.00"), BigDecimal.ONE);
    }
}
