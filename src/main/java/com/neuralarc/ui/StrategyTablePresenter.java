package com.neuralarc.ui;

import com.neuralarc.model.PauseReason;
import com.neuralarc.model.Position;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyStatus;
import com.neuralarc.util.BrokerOrderStatusUtil;

import java.math.BigDecimal;

public final class StrategyTablePresenter {
    public String formatLifecycleStateForDisplay(StrategyLifecycleState state) {
        if (state == null) {
            return "";
        }
        return switch (state) {
            case CREATED -> "Created";
            case VALIDATED -> "Validated";
            case BASE_BUY_PLACED -> "Limit Base Buy Placed";
            case BASE_BUY_PARTIALLY_FILLED -> "Limit Base Buy Partially Filled";
            case BASE_BUY_FILLED -> "Base Buy Filled";
            case BUY_LIMIT_1_PLACED -> "Limit Buy 1 Placed";
            case BUY_LIMIT_1_PARTIALLY_FILLED -> "Limit Buy 1 Partially Filled";
            case BUY_LIMIT_1_FILLED -> "Buy Limit 1 Filled";
            case BUY_LIMIT_2_PLACED -> "Limit Buy 2 Placed";
            case BUY_LIMIT_2_PARTIALLY_FILLED -> "Limit Buy 2 Partially Filled";
            case BUY_LIMIT_2_FILLED -> "Buy Limit 2 Filled";
            case STOP_LOSS_ACTIVE -> "Stop Loss Active";
            case PROFIT_HOLD_ACTIVE -> "Profit Hold Active";
            case SELL_PLACED -> "Limit Sell Placed";
            case SELL_PARTIALLY_FILLED -> "Limit Sell Partially Filled";
            case QUEUED_FOR_OPEN -> "Queued For Open";
            case COMPLETED -> "Completed";
            case PAUSED -> "Canceled";
            case FAILED -> "Position Closed";
            case STOPPED -> "Stopped";
        };
    }

    public String displayStatusLabel(
            Strategy strategy,
            boolean marketClosedSuppressed,
            boolean waitingForFill,
            boolean queueableSessionError
    ) {
        if (strategy == null) {
            return "";
        }
        if ("QUEUED_FOR_OPEN".equalsIgnoreCase(strategy.latestOrderStatus())) {
            return "Queued For Open";
        }
        if (strategy.status() == StrategyStatus.ARCHIVED) {
            return "Archived";
        }
        if (strategy.status() == StrategyStatus.ACTIVE
                && strategy.pauseReason() == PauseReason.MANUAL_MARKET_CLOSED_OVERRIDE
                && marketClosedSuppressed) {
            return formatLifecycleStateForDisplay(strategy.currentState()) + " (Market Closed)";
        }
        if (strategy.status() == StrategyStatus.PAUSED
                && strategy.pauseReason() == PauseReason.AUTO_MARKET_CLOSED
                && marketClosedSuppressed) {
            return "Auto Paused (Market Closed)";
        }
        if (strategy.status() == StrategyStatus.PAUSED && strategy.pauseReason() == PauseReason.USER_PAUSED) {
            return "Canceled";
        }
        if (strategy.status() == StrategyStatus.PAUSED && strategy.pauseReason() == PauseReason.SYSTEM_ERROR) {
            return "Canceled (System Error)";
        }
        if (strategy.status() == StrategyStatus.FAILED && queueableSessionError) {
            return "Queued For Open";
        }
        String lifecycle = formatLifecycleStateForDisplay(strategy.currentState());
        if (strategy.status() == StrategyStatus.ACTIVE
                && waitingForFill
                && strategy.latestOrderStatus() != null
                && !strategy.latestOrderStatus().isBlank()) {
            return lifecycle + " (" + BrokerOrderStatusUtil.displayLabel(strategy.latestOrderStatus()) + ")";
        }
        return lifecycle;
    }

    public Object valueAt(Strategy strategy, Position position, int columnIndex, String statusLabel, String brokerModeLabel) {
        if (columnIndex >= 2 && columnIndex <= 6) {
            return switch (columnIndex) {
                case 2 -> position.getTotalShares();
                case 3 -> position.getTotalShares() > 0 ? position.getAverageCost().toPlainString() : "-";
                case 4 -> position.getLastPrice().compareTo(BigDecimal.ZERO) > 0 ? position.getLastPrice().toPlainString() : "-";
                case 5 -> position.getTotalShares() > 0 ? position.marketValue().toPlainString() : "-";
                case 6 -> position.getTotalShares() > 0 ? position.unrealizedPnl().toPlainString() : "-";
                default -> "";
            };
        }
        return switch (columnIndex) {
            case 0 -> strategy.symbol();
            case 1 -> statusLabel;
            case 7 -> strategy.pollingIntervalSeconds();
            case 8 -> brokerModeLabel;
            case 9 -> statusLabel;
            default -> "";
        };
    }
}

