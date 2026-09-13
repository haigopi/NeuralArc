package com.neuralarc.service;

import com.neuralarc.analytics.PortfolioSnapshot;
import com.neuralarc.analytics.RiskAnalytics;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortfolioSnapshotEmailBuilderTest {
    private final PortfolioSnapshotEmailBuilder builder = new PortfolioSnapshotEmailBuilder();

    @Test
    void theSubjectSaysWhichSnapshotAndTheOpenPnl() {
        assertEquals("NeuralArc: Live - Portfolio snapshot: Before the open, Mon Sep 14 - Open P&L +$23.78",
                builder.subject(snapshot()));
    }

    @Test
    void theEmailCarriesTheStatusBarTotalsAndEachWorkspace() {
        String html = builder.html(snapshot());

        assertTrue(html.contains("Portfolio snapshot"), html);
        assertTrue(html.contains("Before the open &middot; Mon, Sep 14, 2026 at 9:25 AM ET"), html);
        assertTrue(html.contains("All workspaces"));
        assertTrue(html.contains("$3,523.78"), "market value");
        assertTrue(html.contains("$3,950.00"), "invested plus upcoming");
        assertTrue(html.contains("$2,000.00"), "funds available");
        assertTrue(html.contains("By workspace"));
        assertTrue(html.contains("R&amp;D &lt;Lab&gt;"), "workspace names are escaped");
        assertFalse(html.contains("<Lab>"));
    }

    @Test
    void theRiskAnalysisMatchesTheDashboard() {
        String html = builder.html(snapshot());

        assertTrue(html.contains("Risk analysis"));
        assertTrue(html.contains("Capital allocated"));
        assertTrue(html.contains("Top concentration"));
        assertTrue(html.contains("Open P&amp;L by symbol"));
        assertTrue(html.contains("width:100%;background:" + PortfolioSnapshotEmailBuilder.POSITIVE),
                "the largest move fills its half of the chart");
        assertTrue(html.contains("width:79%;background:" + PortfolioSnapshotEmailBuilder.NEGATIVE),
                "a loss of 91.02 against a largest move of 114.80");
        assertTrue(html.contains("Exposure by symbol"));
        assertTrue(html.contains("Exposure by workspace"));
        assertTrue(html.contains("Cut-loss candidates"));
        assertTrue(html.contains("No at-risk winners. Gains have a healthy cushion."));
        int cutLoss = html.indexOf("Cut-loss candidates");
        assertTrue(html.indexOf("CRDO", cutLoss) > cutLoss, "CRDO is below its stop");
    }

    @Test
    void theEmailSaysWhatItIsAndIsNot() {
        String html = builder.html(snapshot());

        assertTrue(html.contains("not a recommendation to buy or sell"));
        assertTrue(html.contains("Live mode"));
    }

    @Test
    void thePlainTextVersionCarriesTheSameFigures() {
        String text = builder.text(snapshot());

        assertTrue(text.contains("Invested vs upcoming buys: $3,500.00 vs $450.00 (total $3,950.00)"), text);
        assertTrue(text.contains("Gaining: 1 (+$114.80)   Losing: 1 (-$91.02)"), text);
        assertTrue(text.contains("- Growth: invested"), text);
        assertTrue(text.contains("GOOG +$114.80"), text);
        assertTrue(text.contains("Cut-loss candidates:"), text);
    }

    @Test
    void anEmptyPortfolioStillReadsWell() {
        PortfolioSnapshot empty = new PortfolioSnapshot("Lunch", ZonedDateTime.of(2026, 9, 14, 12, 0, 0, 0, ZoneId.of("America/New_York")),
                "Paper", null, null, List.of(), RiskAnalytics.analyze(List.of()), List.of());

        String html = builder.html(empty);

        assertTrue(html.contains("No open positions."));
        assertTrue(html.contains("Nothing held."));
        assertFalse(html.contains("By workspace"));
        assertTrue(builder.subject(empty).endsWith("Open P&L $0.00"));
    }

    @Test
    void moneyIsGroupedAndSigned() {
        assertEquals("$12,340.00", PortfolioSnapshotEmailBuilder.money(new BigDecimal("12340")));
        assertEquals("-$80.10", PortfolioSnapshotEmailBuilder.signedMoney(new BigDecimal("-80.1")));
        assertEquals("+$0.01", PortfolioSnapshotEmailBuilder.signedMoney(new BigDecimal("0.01")));
    }

    @Test
    void theBrokerReconciliationSaysWhetherAlpacaAgrees() {
        String matching = builder.html(snapshot().withBrokerCheck(PortfolioSnapshot.BrokerCheck.compared(2, List.of())));
        assertTrue(matching.contains("Broker reconciliation (NeuralArc vs Alpaca)"));
        assertTrue(matching.contains("All 2 symbols match Alpaca."));

        String mismatched = builder.html(snapshot().withBrokerCheck(PortfolioSnapshot.BrokerCheck.compared(2, List.of(
                new PortfolioSnapshot.Mismatch("CRDO", "NeuralArc tracks 20 shares, Alpaca holds 15 shares")))));
        assertTrue(mismatched.contains("1 mismatch among 2 symbols:"));
        assertTrue(mismatched.contains("NeuralArc tracks 20 shares, Alpaca holds 15 shares"));
        assertTrue(builder.text(snapshot().withBrokerCheck(PortfolioSnapshot.BrokerCheck.compared(2, List.of(
                new PortfolioSnapshot.Mismatch("CRDO", "NeuralArc tracks 20 shares, Alpaca holds 15 shares")))))
                .contains("CRDO: NeuralArc tracks 20 shares, Alpaca holds 15 shares"));

        String unavailable = builder.html(snapshot().withBrokerCheck(
                PortfolioSnapshot.BrokerCheck.unavailable("Not connected to Alpaca, so positions were not compared.")));
        assertTrue(unavailable.contains("Not connected to Alpaca, so positions were not compared."));

        assertFalse(builder.html(snapshot()).contains("Broker reconciliation"), "no section before a check has run");
    }

    static PortfolioSnapshot snapshot() {
        List<RiskAnalytics.Holding> holdings = List.of(
                new RiskAnalytics.Holding("GOOG", "Growth", new BigDecimal("1614.80"), new BigDecimal("114.80")),
                new RiskAnalytics.Holding("CRDO", "R&D <Lab>", new BigDecimal("1908.98"), new BigDecimal("-91.02")));
        List<RiskAnalytics.PositionInput> positions = List.of(
                new RiskAnalytics.PositionInput("GOOG", "Growth", new BigDecimal("10"), new BigDecimal("150"),
                        new BigDecimal("161.48"), new BigDecimal("140"), new BigDecimal("180")),
                new RiskAnalytics.PositionInput("CRDO", "R&D <Lab>", new BigDecimal("20"), new BigDecimal("100"),
                        new BigDecimal("95.449"), new BigDecimal("96"), BigDecimal.ZERO));
        PortfolioSnapshot.Figures all = figures("3523.78", "3500.00", "450.00", "23.78");
        return new PortfolioSnapshot(
                "Before the open",
                ZonedDateTime.of(2026, 9, 14, 9, 25, 0, 0, ZoneId.of("America/New_York")),
                "Live",
                "$2,000.00",
                all,
                List.of(new PortfolioSnapshot.WorkspaceRow("Growth", figures("1614.80", "1500.00", "450.00", "114.80")),
                        new PortfolioSnapshot.WorkspaceRow("R&D <Lab>", figures("1908.98", "2000.00", "0", "-91.02"))),
                RiskAnalytics.analyze(holdings),
                RiskAnalytics.classify(positions));
    }

    private static PortfolioSnapshot.Figures figures(String marketValue, String invested, String upcoming, String unrealized) {
        return new PortfolioSnapshot.Figures(new BigDecimal(marketValue), new BigDecimal(invested), new BigDecimal(upcoming),
                new BigDecimal("40.00"), new BigDecimal(unrealized), new BigDecimal(unrealized).add(new BigDecimal("40.00")),
                new BigDecimal("12.50"), 4, 75.0, 1, new BigDecimal("114.80"), 1, new BigDecimal("-91.02"), 2, 1, 2);
    }
}
