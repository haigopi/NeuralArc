package com.neuralarc.ui;

import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

record PortfolioCaptureExecutionResult(
        int capturedCount,
        BigDecimal totalInvestment,
        BigDecimal estimatedPortfolioValue,
        BigDecimal actualBrokerExecutionValue,
        BigDecimal estimatedPnl,
        BigDecimal actualPnl,
        BigDecimal executionVariance,
        Instant timestamp,
        List<String> successes,
        List<String> failures
) {
    static PortfolioCaptureExecutionResult from(
            PortfolioCaptureSnapshot snapshot,
            List<String> successes,
            List<String> failures
    ) {
        BigDecimal estimatedValue = snapshot == null ? Monetary.zero() : snapshot.marketValue();
        BigDecimal estimatedPnl = snapshot == null ? Monetary.zero() : snapshot.unrealizedPnl();
        return from(snapshot, successes, failures, estimatedValue, estimatedPnl, Monetary.zero());
    }

    static PortfolioCaptureExecutionResult from(
            PortfolioCaptureSnapshot snapshot,
            List<String> successes,
            List<String> failures,
            BigDecimal actualBrokerExecutionValue,
            BigDecimal actualPnl,
            BigDecimal executionVariance
    ) {
        BigDecimal estimatedValue = snapshot == null ? Monetary.zero() : snapshot.marketValue();
        BigDecimal investment = snapshot == null ? Monetary.zero() : snapshot.totalInvestment();
        BigDecimal estimatedPnl = snapshot == null ? Monetary.zero() : snapshot.unrealizedPnl();
        return new PortfolioCaptureExecutionResult(
                successes.size(),
                investment,
                estimatedValue,
                Monetary.round(actualBrokerExecutionValue),
                estimatedPnl,
                Monetary.round(actualPnl),
                Monetary.round(executionVariance),
                Instant.now(),
                List.copyOf(successes),
                List.copyOf(failures)
        );
    }
}
