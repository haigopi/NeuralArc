package com.neuralarc.ui;

import com.neuralarc.util.Monetary;

import java.math.BigDecimal;

final class PortfolioCaptureIndicatorPresenter {
    private PortfolioCaptureIndicatorPresenter() {
    }

    static String targetMonitoringText(PortfolioCaptureSnapshot snapshot, PortfolioCaptureConfig config, BigDecimal contextPnl) {
        if (snapshot == null || config == null || config.mode() != PortfolioCaptureMode.TARGET_MONITORING) {
            return "";
        }
        String targetLabel = config.targetType() == PortfolioCaptureTargetType.PROFIT_PERCENT
                ? Monetary.round(config.targetValue()) + "%"
                : "$" + Monetary.round(config.targetValue());
        return "Armed"
                + " | P&L $" + Monetary.round(contextPnl)
                + " | Target " + targetLabel
                + " | " + Monetary.round(snapshot.targetProgressPercent()) + "%";
    }
}
