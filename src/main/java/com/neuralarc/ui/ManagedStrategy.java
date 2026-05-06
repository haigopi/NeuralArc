package com.neuralarc.ui;

import com.neuralarc.model.PauseReason;
import com.neuralarc.model.Position;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyConfig;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyStatus;

/**
 * Runtime wrapper for a Strategy with cached broker position state,
 * polling countdown fields, and busy-button flags.
 * Package-private — only UI coordinator classes in this package should use it.
 */
final class ManagedStrategy {
    Strategy strategy;
    private Position cachedPosition;
    volatile long lastDisplayedPositionFetchAtMillis;
    volatile long pollIntervalMillis;
    volatile long nextPollDueAtMillis;
    volatile boolean countdownActive;
    volatile boolean pollInFlight;
    private volatile boolean pauseResumeBusy;
    private volatile String pauseResumeBusyText = "";

    ManagedStrategy(Strategy strategy) {
        this.strategy = strategy;
        this.cachedPosition = new Position(strategy.symbol());
    }

    void syncFrom(Strategy updated) {
        this.strategy = updated;
    }

    boolean isPaused() {
        return strategy.status() == StrategyStatus.PAUSED;
    }

    String pauseLabel() {
        if (strategy.pauseReason() == PauseReason.AUTO_MARKET_CLOSED) {
            return "Market Closed";
        }
        if (strategy.pauseReason() == PauseReason.SYSTEM_ERROR) {
            return "System Error";
        }
        return "Canceled";
    }

    String pauseTooltip() {
        if (strategy.pauseReason() == PauseReason.AUTO_MARKET_CLOSED) {
            return "Polling auto-paused because the market is closed";
        }
        if (strategy.pauseReason() == PauseReason.SYSTEM_ERROR) {
            return "Polling canceled because of a system error";
        }
        return "Orders canceled by the user";
    }

    boolean isPauseResumeBusy() {
        return pauseResumeBusy;
    }

    void setPauseResumeBusy(boolean pauseResumeBusy) {
        this.pauseResumeBusy = pauseResumeBusy;
    }

    String pauseResumeBusyText() {
        return pauseResumeBusyText;
    }

    void setPauseResumeBusyText(String pauseResumeBusyText) {
        this.pauseResumeBusyText = pauseResumeBusyText == null ? "" : pauseResumeBusyText;
    }

    StrategyConfig toConfig() {
        return new StrategyConfig(
                strategy.symbol(),
                strategy.baseBuyLimitPrice(),
                strategy.baseBuyQuantity(),
                strategy.automatedStopLossEnabled(),
                strategy.stopLossPrice(),
                strategy.targetSellEnabled(),
                strategy.targetSellPrice(),
                strategy.buyLimit1Price(),
                strategy.buyLimit1Quantity(),
                strategy.buyLimit2Price(),
                strategy.buyLimit2Quantity(),
                strategy.lossBuyLevelsEnabled(),
                strategy.optionalLossExitEnabled(),
                strategy.optionalLossExitPrice(),
                strategy.pollingIntervalSeconds(),
                strategy.mode() == StrategyMode.PAPER,
                strategy.alpacaTrailingStopEnabled(),
                strategy.profitHoldEnabled(),
                strategy.profitHoldType(),
                strategy.profitHoldPercent(),
                strategy.profitHoldAmount(),
                strategy.restartAfterExitEnabled()
        );
    }

    Position cachedPosition() {
        return cachedPosition.copy();
    }

    void setCachedPosition(Position position) {
        this.cachedPosition = position == null ? new Position(strategy.symbol()) : position.copy();
        this.lastDisplayedPositionFetchAtMillis = System.currentTimeMillis();
    }

    boolean shouldRefreshDisplayedPosition() {
        long refreshIntervalMillis = Math.max(1L, strategy.pollingIntervalSeconds()) * 1000L;
        return lastDisplayedPositionFetchAtMillis == 0L
                || System.currentTimeMillis() - lastDisplayedPositionFetchAtMillis >= refreshIntervalMillis;
    }
}

