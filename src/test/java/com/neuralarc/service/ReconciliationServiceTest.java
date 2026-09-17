package com.neuralarc.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReconciliationServiceTest {
    private final ReconciliationService service = new ReconciliationService();

    private ReconciliationService.SymbolPosition pos(String symbol, String qty, String cost) {
        return new ReconciliationService.SymbolPosition(symbol, new BigDecimal(qty), new BigDecimal(cost));
    }

    private ReconciliationService.Status statusOf(ReconciliationService.Report report, String symbol) {
        return report.lines().stream()
                .filter(line -> line.symbol().equals(symbol))
                .findFirst().orElseThrow().status();
    }

    @Test
    void aggregatesLocalStrategiesPerSymbolAndMatchesBroker() {
        // ORB owns 10 @ 120, VWAP owns 5 @ 124 -> blended 15 @ 121.333..., broker shows 15 @ 121.33
        List<ReconciliationService.SymbolPosition> local = List.of(
                pos("NVDA", "10", "120.00"),
                pos("NVDA", "5", "124.00")
        );
        List<ReconciliationService.SymbolPosition> broker = List.of(pos("NVDA", "15", "121.33"));

        ReconciliationService.Report report = service.reconcile(local, broker);
        assertFalse(report.hasMismatches(), report.lines().toString());
        assertEquals(ReconciliationService.Status.MATCH, statusOf(report, "NVDA"));
    }

    @Test
    void flagsQuantityAndCostMismatches() {
        ReconciliationService.Report report = service.reconcile(
                List.of(pos("AAPL", "10", "150.00"), pos("MSFT", "4", "400.00")),
                List.of(pos("AAPL", "12", "150.00"), pos("MSFT", "4", "410.00"))
        );
        assertEquals(ReconciliationService.Status.QTY_MISMATCH, statusOf(report, "AAPL"));
        assertEquals(ReconciliationService.Status.COST_MISMATCH, statusOf(report, "MSFT"));
        assertEquals(2, report.mismatchCount());
    }

    @Test
    void aShortHeldOnBothSidesReconcilesRatherThanReadingAsFlat() {
        // MRVL: short 1 at the broker and short 1 in NeuralArc's own records. Measured as "> 0" both
        // sides looked flat, so a real exposure reported a clean match against nothing.
        ReconciliationService.Report report = service.reconcile(
                List.of(pos("MRVL", "-1", "201.20")),
                List.of(pos("MRVL", "-1", "201.20"))
        );

        assertEquals(ReconciliationService.Status.MATCH, statusOf(report, "MRVL"));
        assertFalse(report.hasMismatches());
    }

    @Test
    void aShortTheBrokerHoldsButNeuralArcDoesNotIsFlagged() {
        ReconciliationService.Report report = service.reconcile(
                List.of(),
                List.of(pos("OKLO", "-1", "38.12"))
        );

        assertEquals(ReconciliationService.Status.MISSING_LOCAL, statusOf(report, "OKLO"));
    }

    @Test
    void aShortThatDisagreesOnSizeIsAQuantityMismatch() {
        ReconciliationService.Report report = service.reconcile(
                List.of(pos("PL", "-5", "19.27")),
                List.of(pos("PL", "-1", "19.27"))
        );

        assertEquals(ReconciliationService.Status.QTY_MISMATCH, statusOf(report, "PL"));
    }

    @Test
    void aShortsAverageCostIsReportedAsAPositivePrice() {
        // The cost weight and the quantity are both negative, so the blended price must stay positive.
        ReconciliationService.Report report = service.reconcile(
                List.of(pos("TTAN", "-1", "84.96")),
                List.of(pos("TTAN", "-1", "84.96"))
        );

        assertEquals(new BigDecimal("84.96"), report.lines().stream()
                .filter(line -> line.symbol().equals("TTAN")).findFirst().orElseThrow().localAverageCost());
    }

    @Test
    void flagsMissingOnEitherSide() {
        ReconciliationService.Report report = service.reconcile(
                List.of(pos("TSLA", "3", "250.00")),   // local only
                List.of(pos("GOOG", "2", "140.00"))    // broker only
        );
        assertEquals(ReconciliationService.Status.MISSING_BROKER, statusOf(report, "TSLA"));
        assertEquals(ReconciliationService.Status.MISSING_LOCAL, statusOf(report, "GOOG"));
        assertTrue(report.hasMismatches());
    }
}
