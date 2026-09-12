package com.neuralarc.ui;

import com.neuralarc.analytics.WorkspaceAccounting;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceSummaryPresenterTest {
    private final WorkspaceSummaryPresenter presenter = new WorkspaceSummaryPresenter();

    @Test
    void listsTheWorkspacesPnlFiguresInDisplayOrder() {
        List<PortfolioScopePresenter.Figure> figures = presenter.figures("Momentum Lab", snapshot(12, 75.0));

        assertEquals(List.of("Realized", "Total", "Today", "Win"),
                figures.stream().map(PortfolioScopePresenter.Figure::caption).toList());
        assertEquals("$1,245.00", text(figures, "Realized"));
        assertEquals("$1,172.76", text(figures, "Total"));
        assertEquals("$40.00", text(figures, "Today"));
        assertEquals("75% of 12 sells", text(figures, "Win"));
    }

    @Test
    void profitAndLossFiguresAreTonedBySign() {
        List<PortfolioScopePresenter.Figure> figures = presenter.figures("Momentum Lab", snapshot(12, 75.0));

        assertEquals(PortfolioScopePresenter.Tone.POSITIVE, figure(figures, "Realized").tone());
        assertEquals(PortfolioScopePresenter.Tone.POSITIVE, figure(figures, "Total").tone());
        assertEquals(PortfolioScopePresenter.Tone.NEUTRAL, figure(figures, "Win").tone());
        List<PortfolioScopePresenter.Figure> losing = presenter.figures("Lab", new WorkspaceAccounting.Snapshot(
                new BigDecimal("-5.00"), BigDecimal.ZERO, new BigDecimal("-5.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                0, 1, 0.0, BigDecimal.ZERO, BigDecimal.ZERO));
        assertEquals(PortfolioScopePresenter.Tone.NEGATIVE, figure(losing, "Realized").tone());
        assertEquals(PortfolioScopePresenter.Tone.NEUTRAL, figure(losing, "Today").tone(), "zero has no tone");
    }

    @Test
    void theTabNamesTooltipKeepsTheFiguresLeftOutOfTheRow() {
        String tooltip = presenter.titleTooltip("Momentum Lab", snapshot(12, 75.0));

        assertTrue(tooltip.startsWith("<b>Momentum Lab</b>"), tooltip);
        assertTrue(tooltip.contains("Unrealized -$72.24"), tooltip);
        assertTrue(tooltip.contains("Open positions 3"), tooltip);
        assertTrue(tooltip.contains("Planned budget $3,500.00"), tooltip);
    }

    @Test
    void winRateReadsNaturallyBeforeAndAfterTheFirstSell() {
        assertEquals("no sells yet", text(presenter.figures("Lab", snapshot(0, 0.0)), "Win"));
        assertEquals("100% of 1 sell", text(presenter.figures("Lab", snapshot(1, 100.0)), "Win"));
    }

    @Test
    void tooltipsNameTheWorkspaceAndSayWhatEachFigureMeans() {
        List<PortfolioScopePresenter.Figure> figures = presenter.figures("R&D", snapshot(12, 75.0));

        String realized = figure(figures, "Realized").tooltipHtml();
        assertTrue(realized.startsWith("<b>R&amp;D</b>: "), realized);
        assertTrue(realized.contains("locked in by sells"), realized);
        assertTrue(figure(figures, "Today").tooltipHtml().contains("today's trading day"));
    }

    private static WorkspaceAccounting.Snapshot snapshot(int sells, double winRate) {
        return new WorkspaceAccounting.Snapshot(
                new BigDecimal("1245.00"),
                new BigDecimal("-72.24"),
                new BigDecimal("1172.76"),
                new BigDecimal("40.00"),
                new BigDecimal("3500.00"),
                3,
                sells,
                winRate,
                new BigDecimal("28.50"),
                new BigDecimal("-100.74")
        );
    }

    private static PortfolioScopePresenter.Figure figure(List<PortfolioScopePresenter.Figure> figures, String caption) {
        return figures.stream().filter(figure -> figure.caption().equals(caption)).findFirst().orElseThrow();
    }

    private static String text(List<PortfolioScopePresenter.Figure> figures, String caption) {
        return figure(figures, caption).text();
    }
}
