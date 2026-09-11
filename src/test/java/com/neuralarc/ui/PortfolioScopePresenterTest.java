package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortfolioScopePresenterTest {
    private final PortfolioScopePresenter presenter = new PortfolioScopePresenter();

    @Test
    void showsWhatIsInvestedAgainstWhatTheWorkingBuysWillCostAndTheirTotal() {
        PortfolioScopePresenter.PortfolioScopeView view = presenter.present("Growth",
                metrics("15000", "12340", "4500.5", "250", 4, "-80.1", 2, 3, 1));

        assertEquals("$12,340.00 vs $4,500.50  (Total $16,840.50)", view.investedVsUpcoming().text());
        assertEquals("$4,500.50", view.upcomingText());
        assertEquals("$15,000.00", view.marketValue().text());
    }

    @Test
    void trendCountsCarryTheirCombinedProfitOrLoss() {
        PortfolioScopePresenter.PortfolioScopeView view = presenter.present("Growth",
                metrics("0", "0", "0", "250", 4, "-80.1", 2, 3, 1));

        assertEquals("4 · +$250.00", view.gaining().text());
        assertEquals("2 · -$80.10", view.losing().text());
        assertEquals("3", view.pendingBuy().text());
        assertEquals("1", view.pendingSell().text());
    }

    @Test
    void anEmptyScopeReadsAsZeros() {
        PortfolioScopePresenter.PortfolioScopeView view = presenter.present("Growth",
                metrics("0", "0", "0", "0", 0, "0", 0, 0, 0));

        assertEquals("0", view.gaining().text());
        assertEquals("0", view.losing().text());
        assertEquals("$0.00 vs $0.00  (Total $0.00)", view.investedVsUpcoming().text());
        assertTrue(view.gaining().tooltipHtml().contains("0 positions trading above the average cost."));
    }

    @Test
    void tooltipsNameTheScopeAndExplainEachFigureInPlainWords() {
        PortfolioScopePresenter.PortfolioScopeView view = presenter.present("Growth",
                metrics("15000", "12340", "4500", "250", 1, "-80.1", 2, 1, 1));

        assertTrue(view.investedVsUpcoming().tooltipHtml().startsWith("<b>Growth</b>"));
        assertTrue(view.investedVsUpcoming().tooltipHtml().contains("what you actually paid for the shares you hold now"));
        assertTrue(view.investedVsUpcoming().tooltipHtml().contains("will cost if they fill (1 position)"));
        assertTrue(view.gaining().tooltipHtml().contains("1 position trading above the average cost, up +$250.00"));
        assertTrue(view.losing().tooltipHtml().contains("down $80.10 in total"));
        assertTrue(view.pendingBuy().tooltipHtml().contains("has not filled yet, worth $4,500.00"));
        assertTrue(view.pendingSell().tooltipHtml().contains("for example a target sell"));
    }

    @Test
    void workspaceNamesAreEscapedInsideTooltips() {
        PortfolioScopePresenter.PortfolioScopeView view = presenter.present("R&D <tech>",
                metrics("0", "0", "0", "0", 0, "0", 0, 0, 0));

        assertEquals("R&D <tech>", view.scopeLabel());
        assertTrue(view.marketValue().tooltipHtml().startsWith("<b>R&amp;D &lt;tech&gt;</b>"));
        assertFalse(view.marketValue().tooltipHtml().contains("<tech>"));
    }

    @Test
    void aMissingScopeNameMeansAllStocks() {
        assertEquals("All Stocks", presenter.present(" ", metrics("0", "0", "0", "0", 0, "0", 0, 0, 0)).scopeLabel());
    }

    @Test
    void moneyGroupsThousandsAndSignsLosses() {
        assertEquals("$12,340.00", PortfolioScopePresenter.money(new BigDecimal("12340")));
        assertEquals("-$80.10", PortfolioScopePresenter.money(new BigDecimal("-80.1")));
        assertEquals("$0.00", PortfolioScopePresenter.money(null));
    }

    private static SystemMetricsPresenter.PortfolioScopeMetrics metrics(
            String marketValue, String invested, String upcoming,
            String gainingPnl, int gainingCount, String losingPnl, int losingCount,
            int pendingBuy, int pendingSell
    ) {
        return new SystemMetricsPresenter.PortfolioScopeMetrics(
                new BigDecimal(marketValue), new BigDecimal(invested), new BigDecimal(upcoming),
                new BigDecimal(gainingPnl), gainingCount, new BigDecimal(losingPnl), losingCount,
                pendingBuy, pendingSell);
    }
}
