package com.neuralarc.ui;

import com.neuralarc.model.StrategyStatus;
import com.neuralarc.util.BrokerOrderStatusUtil;

import java.awt.Color;

public final class StrategyActionsPresenter {
    private static final Color DISABLED = new Color(88, 106, 118);
    private static final Color RESUME = new Color(46, 125, 50);
    private static final Color CANCEL = new Color(198, 40, 40);
    private static final Color SELL = new Color(230, 81, 0);
    private static final Color PROMOTE_ENABLED = new Color(25, 118, 210);

    public StrategyActionsViewModel present(StrategyActionsState state) {
        StrategyStatus status = state.status();
        boolean busy = state.busy();
        boolean paused = status == StrategyStatus.PAUSED;
        boolean marketOpenForUi = state.marketOpenForUi();
        boolean actionableToggle = status == StrategyStatus.ACTIVE || status == StrategyStatus.PAUSED;
        boolean canToggle = actionableToggle
                && !busy
                && (status == StrategyStatus.ACTIVE || marketOpenForUi);
        boolean canPromote = state.paperMode()
                && (status == StrategyStatus.ACTIVE || status == StrategyStatus.PAUSED)
                && marketOpenForUi
                && !busy;
        boolean canSell = state.hasPosition()
                && status != StrategyStatus.ARCHIVED
                && status != StrategyStatus.CREATED
                && marketOpenForUi
                && !busy;

        String toggleText = busy
                ? state.busyText()
                : status == StrategyStatus.ARCHIVED
                ? "Archived"
                : status == StrategyStatus.COMPLETED
                ? "Completed"
                : status == StrategyStatus.FAILED
                ? failedStatusText(state.latestOrderStatus())
                : status == StrategyStatus.STOPPED
                ? "Stopped"
                : paused
                ? (!state.hasPosition() || state.manuallyCanceled()) ? "Place Limit Buy Again" : "Resume"
                : "Cancel";

        Color toggleColor = busy || !actionableToggle
                ? DISABLED
                : paused
                ? RESUME
                : CANCEL;
        Color sellColor = canSell ? SELL : DISABLED;
        Color promoteColor = canPromote ? PROMOTE_ENABLED : DISABLED;

        return new StrategyActionsViewModel(
                toggleText,
                toggleColor,
                canToggle,
                canSell,
                sellColor,
                canPromote,
                promoteColor
        );
    }

    private String failedStatusText(String latestOrderStatus) {
        String normalized = BrokerOrderStatusUtil.normalize(latestOrderStatus);
        return normalized.isBlank() ? "Failed" : BrokerOrderStatusUtil.displayLabel(normalized);
    }

    public record StrategyActionsState(
            StrategyStatus status,
            boolean manuallyCanceled,
            boolean busy,
            String busyText,
            boolean paperMode,
            boolean hasPosition,
            boolean marketOpenForUi,
            String latestOrderStatus
    ) {
        public StrategyActionsState(
                StrategyStatus status,
                boolean manuallyCanceled,
                boolean busy,
                String busyText,
                boolean paperMode,
                boolean hasPosition,
                boolean marketOpenForUi
        ) {
            this(status, manuallyCanceled, busy, busyText, paperMode, hasPosition, marketOpenForUi, "");
        }

        public StrategyActionsState {
            busyText = busyText == null || busyText.isBlank() ? "Working..." : busyText;
            latestOrderStatus = latestOrderStatus == null ? "" : latestOrderStatus;
        }
    }

    public record StrategyActionsViewModel(
            String toggleText,
            Color toggleColor,
            boolean toggleEnabled,
            boolean sellEnabled,
            Color sellColor,
            boolean promoteEnabled,
            Color promoteColor
    ) {
    }
}
