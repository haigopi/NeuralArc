package com.neuralarc.ui;

import com.neuralarc.model.ProfitHoldType;
import com.neuralarc.model.StopLossType;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyStatus;
import org.junit.jupiter.api.Test;

import java.awt.BorderLayout;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.swing.JPanel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradingFrameRestoreSelectionGuardTest {

    @Test
    void allowsSelectingFirstRowWhenStrategiesAndVisibleRowsExist() {
        assertTrue(TradingFrame.canSelectFirstRestoredRow(1, 1));
        assertTrue(TradingFrame.canSelectFirstRestoredRow(5, 2));
    }

    @Test
    void blocksSelectingFirstRowWhenNoVisibleRows() {
        assertFalse(TradingFrame.canSelectFirstRestoredRow(3, 0));
    }

    @Test
    void blocksSelectingFirstRowWhenNoStrategies() {
        assertFalse(TradingFrame.canSelectFirstRestoredRow(0, 4));
        assertFalse(TradingFrame.canSelectFirstRestoredRow(0, 0));
    }

    @Test
    void composesFooterBarsWithThinPortfolioBarAboveMainStatusBar() {
        JPanel portfolioBar = new JPanel();
        JPanel mainBar = new JPanel();

        JPanel footerBars = TradingFrame.composeFooterBars(portfolioBar, mainBar);
        BorderLayout layout = (BorderLayout) footerBars.getLayout();

        assertSame(portfolioBar, layout.getLayoutComponent(BorderLayout.NORTH));
        assertSame(mainBar, layout.getLayoutComponent(BorderLayout.SOUTH));
    }

    @Test
    void keepsExpiredFailedStrategyVisibleInCurrentGrid() {
        Strategy strategy = failedStrategy();
        strategy.setLatestOrderStatus("expired");
        strategy.setResubmitOnExpiryEnabled(true);

        assertTrue(TradingFrame.includeFailedStrategyInCurrentTab(strategy));
    }

    @Test
    void keepsInvalidFailedStrategyVisibleInCurrentGrid() {
        Strategy strategy = failedStrategy();
        strategy.setLatestOrderStatus("invalid");

        assertTrue(TradingFrame.includeFailedStrategyInCurrentTab(strategy));
    }

    @Test
    void hidesOrdinaryFailedStrategyWithoutCurrentGridReason() {
        Strategy strategy = failedStrategy();
        strategy.setLatestOrderStatus("api_error");

        assertFalse(TradingFrame.includeFailedStrategyInCurrentTab(strategy));
    }

    @Test
    void keepsOrbRecommendationRowsVisibleInCurrentGrid() {
        Strategy strategy = failedStrategy();
        strategy.setStatus(StrategyStatus.CREATED);
        strategy.setCurrentState(StrategyLifecycleState.CREATED);
        strategy.setLatestOrderStatus("ORB_RECOMMENDED");

        assertTrue(TradingFrame.includeScannerRecommendationInCurrentTab(strategy));
    }

    @Test
    void keepsOrbArmedRowsVisibleInCurrentGrid() {
        Strategy strategy = failedStrategy();
        strategy.setStatus(StrategyStatus.CREATED);
        strategy.setCurrentState(StrategyLifecycleState.CREATED);
        strategy.setLatestOrderStatus("ORB_ARMED");

        assertTrue(TradingFrame.includeScannerRecommendationInCurrentTab(strategy));
    }

    @Test
    void portfolioActionScopeMatchesSelectedWorkspaceOnly() {
        Strategy strategy = failedStrategy();
        strategy.setStatus(StrategyStatus.ACTIVE);
        strategy.setMode(StrategyMode.PAPER);
        strategy.setWorkspaceId("orb-workspace");

        assertTrue(TradingFrame.matchesPortfolioActionScope(strategy, StrategyMode.PAPER, "orb-workspace"));
        assertFalse(TradingFrame.matchesPortfolioActionScope(strategy, StrategyMode.PAPER, "vwap-workspace"));
    }

    @Test
    void portfolioActionScopeAllStocksIncludesSelectedModeOnly() {
        Strategy strategy = failedStrategy();
        strategy.setStatus(StrategyStatus.ACTIVE);
        strategy.setMode(StrategyMode.PAPER);
        strategy.setWorkspaceId("orb-workspace");

        assertTrue(TradingFrame.matchesPortfolioActionScope(strategy, StrategyMode.PAPER, null));
        assertFalse(TradingFrame.matchesPortfolioActionScope(strategy, StrategyMode.LIVE, null));
    }

    @Test
    void startupViewModeDefaultsToLiveWhenLiveStrategiesExist() {
        Strategy paper = failedStrategy();
        paper.setMode(StrategyMode.PAPER);
        paper.setStatus(StrategyStatus.ACTIVE);
        Strategy live = failedStrategy();
        live.setMode(StrategyMode.LIVE);
        live.setStatus(StrategyStatus.ACTIVE);

        assertEquals(StrategyMode.LIVE, TradingFrame.startupViewMode(List.of(paper, live)));
    }

    @Test
    void startupViewModeDefaultsToPaperWhenNoLiveStrategiesExist() {
        Strategy paper = failedStrategy();
        paper.setMode(StrategyMode.PAPER);
        paper.setStatus(StrategyStatus.ACTIVE);

        assertEquals(StrategyMode.PAPER, TradingFrame.startupViewMode(List.of(paper)));
        assertEquals(StrategyMode.PAPER, TradingFrame.startupViewMode(List.of()));
    }

    @Test
    void startupViewModeIgnoresArchivedAndStoppedLiveHistory() {
        Strategy archivedLive = failedStrategy();
        archivedLive.setMode(StrategyMode.LIVE);
        archivedLive.setStatus(StrategyStatus.ARCHIVED);
        Strategy stoppedLive = failedStrategy();
        stoppedLive.setMode(StrategyMode.LIVE);
        stoppedLive.setStatus(StrategyStatus.STOPPED);

        assertEquals(StrategyMode.PAPER, TradingFrame.startupViewMode(List.of(archivedLive, stoppedLive)));
    }

    private Strategy failedStrategy() {
        return new Strategy(
                UUID.randomUUID().toString(),
                "AAPL Strategy",
                "AAPL",
                StrategyMode.PAPER,
                StrategyStatus.FAILED,
                StrategyLifecycleState.FAILED,
                new BigDecimal("100.00"),
                1,
                new BigDecimal("95.00"),
                1,
                new BigDecimal("90.00"),
                1,
                true,
                StopLossType.FIXED_PRICE,
                new BigDecimal("85.00"),
                BigDecimal.ZERO,
                false,
                BigDecimal.ZERO,
                true,
                new BigDecimal("120.00"),
                new BigDecimal("100.00"),
                true,
                false,
                ProfitHoldType.PERCENT_TRAILING,
                new BigDecimal("5.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                false,
                30,
                new BigDecimal("10000.00"),
                5,
                Instant.now(),
                Instant.now()
        );
    }
}
