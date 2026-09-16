package com.neuralarc.analytics;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LossHarvestingTest {
    private static final LocalDate NOVEMBER = LocalDate.of(2026, 11, 15);

    @Test
    void bookedLossesCancelGainsThenComeOffIncomeAndTheRestCarriesForward() {
        // 9,000 of losses against 2,000 of gains: 2,000 cancelled, 3,000 off income, 4,000 carried.
        LossHarvesting.Report report = LossHarvesting.analyze(
                List.of(losing("ORCL", "-5000", 200), losing("CRDO", "-4000", 100)),
                new BigDecimal("2000.00"), NOVEMBER);

        assertEquals(new BigDecimal("9000.00"), report.harvestableLoss());
        assertEquals(new BigDecimal("2000.00"), report.offsetsGains());
        assertEquals(new BigDecimal("3000.00"), report.ordinaryIncomeOffset());
        assertEquals(new BigDecimal("4000.00"), report.carryForward());
    }

    @Test
    void theHeadlineSaysWhatBookingTheLossesWouldDo() {
        LossHarvesting.Report report = LossHarvesting.analyze(
                List.of(losing("ORCL", "-5000", 200)), new BigDecimal("2000.00"), NOVEMBER);

        assertTrue(report.headline().contains("$5,000.00"), report.headline());
        assertTrue(report.headline().contains("cancels all $2,000.00"), report.headline());
        assertTrue(report.headline().contains("ordinary income"), report.headline());
    }

    @Test
    void withNoGainsThisYearTheLossStillShieldsIncomeAndCarriesForward() {
        LossHarvesting.Report report = LossHarvesting.analyze(
                List.of(losing("ORCL", "-5000", 200)), BigDecimal.ZERO, NOVEMBER);

        assertEquals(BigDecimal.ZERO.setScale(2), report.offsetsGains());
        assertEquals(new BigDecimal("3000.00"), report.ordinaryIncomeOffset());
        assertEquals(new BigDecimal("2000.00"), report.carryForward());
        assertTrue(report.headline().contains("No gains have been booked"), report.headline());
    }

    @Test
    void shortTermLossesAreListedFirstBecauseTheyShieldIncomeTaxedHigher() {
        LossHarvesting.Report report = LossHarvesting.analyze(List.of(
                held("LONG", "-6000", 500),      // bigger, but long-term
                held("SHORT", "-1000", 30)), BigDecimal.ZERO, NOVEMBER);

        assertEquals("SHORT", report.recommended().getFirst().symbol());
        assertEquals(LossHarvesting.Term.SHORT, report.recommended().getFirst().term());
        assertEquals(LossHarvesting.Term.LONG, report.recommended().get(1).term());
    }

    @Test
    void lossesBeyondThisYearsUseAreListedAsOptionalCarryForward() {
        // 1,000 of gains plus the 3,000 allowance is worth booking now; the rest only carries forward.
        LossHarvesting.Report report = LossHarvesting.analyze(List.of(
                losing("A", "-4000", 100), losing("B", "-2000", 50)),
                new BigDecimal("1000.00"), NOVEMBER);

        assertEquals(List.of("A"), report.recommended().stream().map(LossHarvesting.Lot::symbol).toList());
        assertEquals(List.of("B"), report.optional().stream().map(LossHarvesting.Lot::symbol).toList());
    }

    @Test
    void aStrategyThatBuysItselfBackIsFlaggedAsAWashSale() {
        LossHarvesting.Report report = LossHarvesting.analyze(
                List.of(new LossHarvesting.Position("ORCL", "Growth", 200, new BigDecimal("-5000"), -12.5, 45, true)),
                BigDecimal.ZERO, NOVEMBER);

        LossHarvesting.Lot lot = report.recommended().getFirst();
        assertTrue(lot.washSaleRisk());
        assertEquals("Sell 200 shares to book $5,000.00.", lot.action(), "the row stays short; the rule is explained once");
        assertTrue(report.notes().stream().anyMatch(note -> note.contains("buys back automatically")));
        assertTrue(report.notes().stream().anyMatch(note -> note.contains("the loss is disallowed")));
    }

    @Test
    void everyReportSaysItIsNotTaxAdviceAndNamesTheDeadline() {
        LossHarvesting.Report report = LossHarvesting.analyze(
                List.of(losing("ORCL", "-100", 10)), BigDecimal.ZERO, NOVEMBER);

        assertTrue(report.notes().stream().anyMatch(note -> note.contains("not tax advice")));
        assertTrue(report.notes().stream().anyMatch(note -> note.contains("Thu 31 Dec 2026")));
        assertTrue(report.notes().stream().anyMatch(note -> note.contains("Wash sale")));
    }

    @Test
    void saysTheDayToSellByAndTheDayTheSymbolMayBeBoughtAgain() {
        LossHarvesting.Report report = LossHarvesting.analyze(
                List.of(losing("ORCL", "-100", 10)), BigDecimal.ZERO, NOVEMBER);

        // 31 Dec 2026 is a Thursday, so it is the last trading day; 31 days on clears the wash-sale window.
        assertEquals(LocalDate.of(2026, 12, 31), report.sellBy());
        assertEquals(LocalDate.of(2027, 1, 31), report.repurchaseAllowedFrom());
        assertEquals("Thu 31 Dec 2026", LossHarvesting.date(report.sellBy()));
    }

    @Test
    void aYearEndingOnAWeekendMovesTheDeadlineBackToTheLastWeekday() {
        // 31 Dec 2028 is a Sunday; the last weekday before it is Friday the 29th.
        assertEquals(LocalDate.of(2028, 12, 29), LossHarvesting.lastTradingDayOfYear(LocalDate.of(2028, 7, 1)));
    }

    @Test
    void positionsInProfitAreNeverSuggested() {
        LossHarvesting.Report report = LossHarvesting.analyze(List.of(
                new LossHarvesting.Position("GOOG", "Growth", 10, new BigDecimal("500"), 5.0, 100, false),
                losing("ORCL", "-100", 10)), BigDecimal.ZERO, NOVEMBER);

        assertEquals(List.of("ORCL"), report.recommended().stream().map(LossHarvesting.Lot::symbol).toList());
    }

    @Test
    void withNothingLosingThereIsNothingToHarvest() {
        LossHarvesting.Report report = LossHarvesting.analyze(List.of(), new BigDecimal("500"), NOVEMBER);

        assertFalse(report.hasCandidates());
        assertEquals(BigDecimal.ZERO.setScale(2), report.harvestableLoss());
        assertTrue(report.headline().contains("nothing to harvest"), report.headline());
    }

    @Test
    void realisedGainsCountOnlyTheSellsThatFilledThisYear() {
        // Bought 10 at 100 last year; sold 5 at 120 last year and 5 at 130 this year.
        List<LossHarvesting.Fill> fills = List.of(
                fill(true, "10", "100", 2025, 3, 4),
                fill(false, "5", "120", 2025, 9, 9),
                fill(false, "5", "130", 2026, 2, 2));

        assertEquals(new BigDecimal("150.00"), LossHarvesting.realizedInYear(fills, 2026));
        assertEquals(new BigDecimal("100.00"), LossHarvesting.realizedInYear(fills, 2025));
    }

    @Test
    void earlierBuysStillShapeTheAverageCostThisYearsSellsAreMeasuredAgainst() {
        // 10 at 100 last year and 10 at 140 this year average 120; selling 10 at 130 books 100.
        List<LossHarvesting.Fill> fills = List.of(
                fill(true, "10", "100", 2025, 5, 1),
                fill(true, "10", "140", 2026, 1, 8),
                fill(false, "10", "130", 2026, 6, 1));

        assertEquals(new BigDecimal("100.00"), LossHarvesting.realizedInYear(fills, 2026));
    }

    @Test
    void fillsOutOfOrderAreReplayedByDateAndStraySellsIgnored() {
        List<LossHarvesting.Fill> shuffled = List.of(
                fill(false, "5", "130", 2026, 2, 2),
                fill(true, "10", "100", 2026, 1, 2));

        assertEquals(new BigDecimal("150.00"), LossHarvesting.realizedInYear(shuffled, 2026));
        assertEquals(BigDecimal.ZERO.setScale(2),
                LossHarvesting.realizedInYear(List.of(fill(false, "5", "130", 2026, 2, 2)), 2026),
                "a sell with nothing held books nothing");
        assertEquals(BigDecimal.ZERO.setScale(2), LossHarvesting.realizedInYear(null, 2026));
    }

    private static LossHarvesting.Fill fill(boolean buy, String quantity, String price, int year, int month, int day) {
        return new LossHarvesting.Fill(buy, new BigDecimal(quantity), new BigDecimal(price),
                LocalDate.of(year, month, day));
    }

    private static LossHarvesting.Position losing(String symbol, String pnl, int shares) {
        return new LossHarvesting.Position(symbol, "Growth", shares, new BigDecimal(pnl), -10.0, 45, false);
    }

    private static LossHarvesting.Position held(String symbol, String pnl, long heldDays) {
        return new LossHarvesting.Position(symbol, "Growth", 100, new BigDecimal(pnl), -10.0, heldDays, false);
    }
}
