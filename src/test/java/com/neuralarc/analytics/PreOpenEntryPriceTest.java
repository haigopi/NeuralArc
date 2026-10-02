package com.neuralarc.analytics;

import com.neuralarc.model.MarketBar;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreOpenEntryPriceTest {
    @Test
    void theNewEntrySitsJustUnderYesterdaysClose() {
        PreOpenEntryPrice.Plan plan = PreOpenEntryPrice.fromPreviousSession(List.of(
                bar("2026-09-28", "9.00", "11.00", "8.00", "10.00"),
                bar("2026-09-29", "10.00", "12.00", "9.50", "11.00"),
                bar("2026-09-30", "11.00", "13.00", "10.00", "12.00")
        ));

        assertTrue(plan.known());
        // 12.00 less the 0.25% default discount, floored to the cent.
        assertEquals(new BigDecimal("11.97"), plan.price());
        assertEquals(new BigDecimal("12.00"), plan.previousClose());
    }

    @Test
    void theEntryIsNeverAboveTheCloseItWasDerivedFrom() {
        PreOpenEntryPrice.Plan plan = PreOpenEntryPrice.fromPreviousSession(
                List.of(bar("2026-09-30", "20.00", "25.00", "19.00", "20.00")), BigDecimal.ZERO);

        assertTrue(plan.price().compareTo(plan.previousClose()) <= 0,
                "a buy limit above the market fills at once at whatever is asked");
    }

    @Test
    void theWeeksLowIsTheFloorSoTheLimitCanStillFill() {
        // A deep discount would aim under everything the stock has traded all week.
        PreOpenEntryPrice.Plan plan = PreOpenEntryPrice.fromPreviousSession(List.of(
                bar("2026-09-29", "10.00", "10.50", "9.80", "10.00"),
                bar("2026-09-30", "10.00", "10.20", "9.90", "10.00")
        ), new BigDecimal("20"));

        assertEquals(new BigDecimal("9.80"), plan.price());
        assertEquals(new BigDecimal("9.80"), plan.weekLow());
        assertTrue(plan.describe().contains("floored at the week's low"), plan.describe());
    }

    @Test
    void onlyTheLastWeekOfSessionsSetsTheFloor() {
        PreOpenEntryPrice.Plan plan = PreOpenEntryPrice.fromPreviousSession(List.of(
                bar("2026-09-22", "10.00", "10.00", "2.00", "10.00"),
                bar("2026-09-23", "10.00", "10.50", "9.80", "10.00"),
                bar("2026-09-24", "10.00", "10.50", "9.80", "10.00"),
                bar("2026-09-25", "10.00", "10.50", "9.80", "10.00"),
                bar("2026-09-26", "10.00", "10.50", "9.80", "10.00"),
                bar("2026-09-30", "10.00", "10.20", "9.90", "10.00")
        ), new BigDecimal("20"));

        assertEquals(new BigDecimal("9.80"), plan.price(), "a crash eight sessions ago is not this week's floor");
    }

    @Test
    void anUnreadableSessionPlansNothingRatherThanGuessing() {
        assertFalse(PreOpenEntryPrice.fromPreviousSession(null).known());
        assertFalse(PreOpenEntryPrice.fromPreviousSession(List.of()).known());
        assertFalse(PreOpenEntryPrice.fromPreviousSession(
                List.of(bar("2026-09-30", "0", "0", "0", "0"))).known());
        assertEquals("yesterday's session could not be read",
                PreOpenEntryPrice.Plan.unknown().describe());
    }

    private static MarketBar bar(String day, String open, String high, String low, String close) {
        return new MarketBar("TEST", day + "T00:00:00Z", new BigDecimal(open), new BigDecimal(high),
                new BigDecimal(low), new BigDecimal(close), new BigDecimal("1000"));
    }
}
