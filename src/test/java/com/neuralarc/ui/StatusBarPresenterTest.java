package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatusBarPresenterTest {
    private final StatusBarPresenter presenter = new StatusBarPresenter();

    @Test
    void pollSummaryShowsClosedWhenSuppressed() {
        StatusBarPresenter.StatusBarViewModel vm = presenter.present(state(1, 0, true, true, 3, false, true,
                "Market: Closed", false, "Funds Available: -", "CPU: -", "Memory: 1 MB", "All Stocks", emptyMetrics()));

        assertEquals("Paused for market close", vm.pollingText());
        assertEquals(StatusBarPresenter.Tone.MUTED, vm.pollingTone());
    }

    @Test
    void brokerStatusShowsRetryingWhenConnectionRetryPending() {
        StatusBarPresenter.StatusBarViewModel vm = presenter.present(state(0, 1, false, false, 4, true, false,
                "Market: Open (Regular)", true, "Funds Available: -", "CPU: -", "Memory: 1 MB", "All Stocks", emptyMetrics()));

        assertEquals("<html><b>FAILED</b> Retrying...</html>", vm.brokerText());
        assertEquals(StatusBarPresenter.Tone.ERR, vm.brokerTone());
    }

    @Test
    void brokerStatusShowsConnectedWithoutActiveStrategies() {
        StatusBarPresenter.StatusBarViewModel vm = presenter.present(state(0, 2, false, false, 5, false, true,
                "Market: Open (Regular)", true, "Funds Available: -", "CPU: -", "Memory: 1 MB", "All Stocks", emptyMetrics()));

        assertEquals("Connected (No active)", vm.brokerText());
        assertEquals("Strategies 2  Active 0  Inactive 2  History 5", vm.strategyCountText());
        assertEquals(StatusBarPresenter.Tone.WARN, vm.brokerTone());
    }

    @Test
    void normalizesStatusBarValuesForItemLayout() {
        StatusBarPresenter.StatusBarViewModel vm = presenter.present(state(1, 1, true, false, 5, false, true,
                "Market: Open (Regular)", true, "Funds Available: $1000", "CPU: 12%", "Memory: 256 MB", "All Stocks",
                emptyMetrics()));

        assertEquals("Open (Regular)", vm.marketText());
        assertEquals("$1000", vm.availableFundsText());
        assertEquals("12%", vm.cpuText());
        assertEquals("256 MB", vm.memoryText());
    }

    @Test
    void portfolioFiguresAreThoseOfTheSelectedGrid() {
        SystemMetricsPresenter.PortfolioScopeMetrics metrics = new SystemMetricsPresenter.PortfolioScopeMetrics(
                new BigDecimal("1200.00"), new BigDecimal("900.00"), new BigDecimal("500.00"),
                new BigDecimal("300.00"), 3, new BigDecimal("-100.00"), 1, 2, 1);

        PortfolioScopePresenter.PortfolioScopeView scope = presenter.present(state(1, 1, true, false, 5, false, true,
                "Market: Open (Regular)", true, "Funds Available: $1000", "CPU: 12%", "Memory: 256 MB", "Growth",
                metrics)).portfolioScope();

        assertEquals("Growth", scope.scopeLabel());
        assertEquals("$1,200.00", scope.marketValue().text());
        assertEquals("$900.00 vs $500.00  (Total $1,400.00)", scope.investedVsUpcoming().text());
        assertEquals("3 · +$300.00", scope.gaining().text());
        assertEquals("1 · -$100.00", scope.losing().text());
        assertEquals("2", scope.pendingBuy().text());
        assertEquals("1", scope.pendingSell().text());
    }

    @Test
    void ownsNetworkIconStatusModel() {
        StatusBarPresenter.NetworkStatusViewModel vm = presenter.presentNetworkStatus(false);

        assertEquals(StatusBarPresenter.NETWORK_ICON_PATH, vm.iconPath());
        assertEquals(StatusBarPresenter.Tone.ERR, vm.tone());
        assertEquals(true, vm.blink());
    }

    static SystemMetricsPresenter.PortfolioScopeMetrics emptyMetrics() {
        return new SystemMetricsPresenter().computePortfolioScopeMetrics(null, null);
    }

    private static StatusBarPresenter.StatusBarState state(
            long running,
            long inactive,
            boolean pollEvaluated,
            boolean pollSuppressed,
            long historyRows,
            boolean retryPending,
            boolean connectionOk,
            String marketLabel,
            boolean marketOpen,
            String fundsText,
            String cpuText,
            String memoryText,
            String scopeLabel,
            SystemMetricsPresenter.PortfolioScopeMetrics metrics
    ) {
        return new StatusBarPresenter.StatusBarState(running, inactive, pollEvaluated, pollSuppressed, 0, 0, historyRows,
                retryPending, connectionOk, marketLabel, "tooltip", marketOpen, fundsText, cpuText, memoryText,
                scopeLabel, metrics);
    }
}
