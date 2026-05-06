package com.neuralarc.ui;

import com.neuralarc.model.ProfitHoldType;
import com.neuralarc.model.StopLossType;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuplicateSymbolPolicyTest {

    // ---- allowDuplicates = true ----

    @Test
    void allowDuplicatesTrueNeverReportsDuplicate() {
        Strategy active = strategy("AAPL", StrategyMode.PAPER, StrategyStatus.ACTIVE);
        assertFalse(DuplicateSymbolPolicy.wouldBeDuplicate("AAPL", StrategyMode.PAPER, List.of(active), true));
    }

    @Test
    void allowDuplicatesTrueReturnsFalseForEmptyList() {
        assertFalse(DuplicateSymbolPolicy.wouldBeDuplicate("TSLA", StrategyMode.PAPER, List.of(), true));
    }

    // ---- allowDuplicates = false (default) ----

    @Test
    void activeStrategyBlocksSameSymbolAndMode() {
        Strategy active = strategy("AAPL", StrategyMode.PAPER, StrategyStatus.ACTIVE);
        assertTrue(DuplicateSymbolPolicy.wouldBeDuplicate("AAPL", StrategyMode.PAPER, List.of(active), false));
    }

    @Test
    void pausedStrategyBlocksSameSymbolAndMode() {
        Strategy paused = strategy("MSFT", StrategyMode.PAPER, StrategyStatus.PAUSED);
        assertTrue(DuplicateSymbolPolicy.wouldBeDuplicate("MSFT", StrategyMode.PAPER, List.of(paused), false));
    }

    @Test
    void stoppedStrategyDoesNotBlockSameSymbol() {
        Strategy stopped = strategy("TSLA", StrategyMode.PAPER, StrategyStatus.STOPPED);
        assertFalse(DuplicateSymbolPolicy.wouldBeDuplicate("TSLA", StrategyMode.PAPER, List.of(stopped), false));
    }

    @Test
    void completedStrategyDoesNotBlockSameSymbol() {
        Strategy completed = strategy("NVDA", StrategyMode.PAPER, StrategyStatus.COMPLETED);
        assertFalse(DuplicateSymbolPolicy.wouldBeDuplicate("NVDA", StrategyMode.PAPER, List.of(completed), false));
    }

    @Test
    void failedStrategyDoesNotBlockSameSymbol() {
        Strategy failed = strategy("AMZN", StrategyMode.PAPER, StrategyStatus.FAILED);
        assertFalse(DuplicateSymbolPolicy.wouldBeDuplicate("AMZN", StrategyMode.PAPER, List.of(failed), false));
    }

    @Test
    void archivedStrategyDoesNotBlockSameSymbol() {
        Strategy archived = strategy("GOOG", StrategyMode.PAPER, StrategyStatus.ARCHIVED);
        assertFalse(DuplicateSymbolPolicy.wouldBeDuplicate("GOOG", StrategyMode.PAPER, List.of(archived), false));
    }

    @Test
    void symbolCheckIsCaseInsensitive() {
        Strategy active = strategy("aapl", StrategyMode.PAPER, StrategyStatus.ACTIVE);
        assertTrue(DuplicateSymbolPolicy.wouldBeDuplicate("AAPL", StrategyMode.PAPER, List.of(active), false));
    }

    @Test
    void modeIsRespectedPaperDoesNotBlockLive() {
        Strategy paperActive = strategy("AAPL", StrategyMode.PAPER, StrategyStatus.ACTIVE);
        assertFalse(DuplicateSymbolPolicy.wouldBeDuplicate("AAPL", StrategyMode.LIVE, List.of(paperActive), false));
    }

    @Test
    void modeIsRespectedLiveDoesNotBlockPaper() {
        Strategy liveActive = strategy("AAPL", StrategyMode.LIVE, StrategyStatus.ACTIVE);
        assertFalse(DuplicateSymbolPolicy.wouldBeDuplicate("AAPL", StrategyMode.PAPER, List.of(liveActive), false));
    }

    @Test
    void differentSymbolDoesNotBlock() {
        Strategy active = strategy("MSFT", StrategyMode.PAPER, StrategyStatus.ACTIVE);
        assertFalse(DuplicateSymbolPolicy.wouldBeDuplicate("AAPL", StrategyMode.PAPER, List.of(active), false));
    }

    @Test
    void emptyListDoesNotBlock() {
        assertFalse(DuplicateSymbolPolicy.wouldBeDuplicate("AAPL", StrategyMode.PAPER, List.of(), false));
    }

    @Test
    void onlyHistoryStrategiesDoNotBlockNewEntry() {
        // Simulates the reported bug: stock in trade history (stopped) was blocking new strategy
        Strategy stopped = strategy("AAPL", StrategyMode.PAPER, StrategyStatus.STOPPED);
        Strategy completed = strategy("AAPL", StrategyMode.PAPER, StrategyStatus.COMPLETED);
        assertFalse(DuplicateSymbolPolicy.wouldBeDuplicate("AAPL", StrategyMode.PAPER,
                List.of(stopped, completed), false));
    }

    @Test
    void mixedListBlocksWhenOneIsActive() {
        Strategy stopped = strategy("AAPL", StrategyMode.PAPER, StrategyStatus.STOPPED);
        Strategy active = strategy("AAPL", StrategyMode.PAPER, StrategyStatus.ACTIVE);
        assertTrue(DuplicateSymbolPolicy.wouldBeDuplicate("AAPL", StrategyMode.PAPER,
                List.of(stopped, active), false));
    }

    // ---- AddStrategyDuplicateCheck tests for settings integration ----

    @Test
    void allowDuplicatesTruePermitsAddingSymbolAlreadyActive() {
        Strategy active = strategy("NVDA", StrategyMode.PAPER, StrategyStatus.ACTIVE);
        // Even an active strategy does not block when setting is enabled
        assertFalse(DuplicateSymbolPolicy.wouldBeDuplicate("NVDA", StrategyMode.PAPER, List.of(active), true));
    }

    // ---- helpers ----

    private static Strategy strategy(String symbol, StrategyMode mode, StrategyStatus status) {
        Strategy s = new Strategy(
                UUID.randomUUID().toString(),
                symbol + " Strategy",
                symbol,
                mode,
                status,
                StrategyLifecycleState.CREATED,
                new BigDecimal("100"),
                1,
                BigDecimal.ZERO,
                0,
                BigDecimal.ZERO,
                0,
                false,
                StopLossType.FIXED_PRICE,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                false,
                BigDecimal.ZERO,
                false,
                BigDecimal.ZERO,
                new BigDecimal("1000"),
                false,
                false,
                ProfitHoldType.PERCENT_TRAILING,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                false,
                10,
                new BigDecimal("5000"),
                30,
                Instant.now(),
                Instant.now()
        );
        s.setStatus(status);
        return s;
    }
}

