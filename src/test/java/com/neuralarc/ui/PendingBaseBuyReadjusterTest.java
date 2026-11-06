package com.neuralarc.ui;

import com.neuralarc.model.MarketBar;
import com.neuralarc.model.Position;
import com.neuralarc.model.Strategy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static com.neuralarc.ui.OrbPendingBaseBuyRowStylerTest.position;
import static com.neuralarc.ui.OrbPendingBaseBuyRowStylerTest.strategy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingBaseBuyReadjusterTest {
    @Test
    void lowersAStaleBaseBuyToThePrice() {
        // BMBL: a 6.01 base buy while the stock trades at 5.96.
        Optional<PendingBaseBuyReadjuster.Adjustment> plan =
                PendingBaseBuyReadjuster.plan(strategy("GAP_ROCKET_RECOMMENDED", "6.01"), position("5.96"), null);

        assertTrue(plan.isPresent());
        assertEquals(new BigDecimal("5.96"), plan.get().to());
        assertEquals(new BigDecimal("6.01"), plan.get().from());
        assertTrue(plan.get().event().contains("$6.01") && plan.get().event().contains("$5.96"));
    }

    @Test
    void aBaseBuyAboveThePriceIsLoweredToIt() {
        assertEquals(new BigDecimal("100.00"),
                PendingBaseBuyReadjuster.plan(strategy("VWAP_RECOMMENDED", "103.00"), position("100.00"), null).orElseThrow().to());
        assertTrue(PendingBaseBuyReadjuster.plan(strategy("VWAP_RECOMMENDED", "99.00"), position("100.00"), null).isEmpty(),
                "a base buy already under the price is left alone");
    }

    @Test
    void neverRaisesABaseBuyAndLeavesAGoodOneAlone() {
        assertTrue(PendingBaseBuyReadjuster.plan(strategy("ORB_RECOMMENDED", "90.00"), position("100.00"), null).isEmpty());
        assertTrue(PendingBaseBuyReadjuster.plan(strategy("ORB_RECOMMENDED", "100.00"), position("100.00"), null).isEmpty());
    }

    @Test
    void ignoresRowsWithoutAPriceOrNotWaitingForPlacement() {
        assertTrue(PendingBaseBuyReadjuster.plan(strategy("GAP_ROCKET_RECOMMENDED", "6.01"), new Position("AAPL"), null).isEmpty());
        assertTrue(PendingBaseBuyReadjuster.plan(strategy("FILLED", "6.01"), position("5.96"), null).isEmpty());
        assertTrue(PendingBaseBuyReadjuster.plan(null, position("5.96"), null).isEmpty());
    }

    @Test
    void manualAdditionsAreNeverReadjusted() {
        Strategy manual = strategy("PAPER_PENDING", "6.01");
        manual.setName("my own trade");

        assertTrue(PendingBaseBuyReadjuster.plan(manual, position("5.96"), null).isEmpty(),
                "the typed price is kept; it is corrected only when the order is placed");
    }

    @Test
    void measuresFromTheLowestOfOpenLowAndCurrentPrice() {
        // BMBL: opened 5.98, low 5.95, now 5.96 -> measured from 5.95.
        MarketBar bar = bar("5.98", "5.95");

        PendingBaseBuyReadjuster.Adjustment adjustment =
                PendingBaseBuyReadjuster.plan(strategy("GAP_ROCKET_RECOMMENDED", "6.01"), position("5.96"), bar).orElseThrow();

        assertEquals(new BigDecimal("5.95"), adjustment.to());
        assertEquals(new BigDecimal("5.95"), adjustment.reference());
    }

    @Test
    void anOpenBelowTheCurrentPriceAndLowSetsTheReference() {
        // Opened 100, ran to 110 (now 108), low 99 -> 99 is the lowest.
        assertEquals(new BigDecimal("99.00"),
                PendingBaseBuyReadjuster.plan(strategy("VWAP_RECOMMENDED", "105.00"), position("108.00"),
                        bar("100.00", "99.00")).orElseThrow().to());
        // A base buy already at or under the lowest price is left alone.
        assertTrue(PendingBaseBuyReadjuster.plan(strategy("VWAP_RECOMMENDED", "98.00"), position("108.00"),
                bar("100.00", "99.00")).isEmpty());
    }

    private static MarketBar bar(String open, String low) {
        return new MarketBar("AAPL", "2026-10-13T04:00:00Z", new BigDecimal(open), new BigDecimal("999"),
                new BigDecimal(low), new BigDecimal("999"), BigDecimal.ONE);
    }
}
