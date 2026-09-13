package com.neuralarc.analytics;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Everything the portfolio snapshot email reports, taken from NeuralArc's own accounting at one
 * moment: the totals across every workspace (the bottom status bar's figures), each workspace's
 * figures (the footer under its grid), and the Risk Dashboard's analysis of the positions held.
 */
public record PortfolioSnapshot(
        String occasion,
        ZonedDateTime takenAt,
        String modeLabel,
        String fundsAvailable,
        Figures allWorkspaces,
        List<WorkspaceRow> workspaces,
        RiskAnalytics.Report risk,
        List<RiskAnalytics.PositionRisk> positionRisks,
        BrokerCheck brokerCheck
) {
    public PortfolioSnapshot {
        Objects.requireNonNull(takenAt, "takenAt");
        occasion = occasion == null || occasion.isBlank() ? "Snapshot" : occasion.trim();
        modeLabel = modeLabel == null || modeLabel.isBlank() ? "-" : modeLabel.trim();
        fundsAvailable = fundsAvailable == null || fundsAvailable.isBlank() ? "-" : fundsAvailable.trim();
        allWorkspaces = allWorkspaces == null ? Figures.empty() : allWorkspaces;
        workspaces = workspaces == null ? List.of() : List.copyOf(workspaces);
        positionRisks = positionRisks == null ? List.of() : List.copyOf(positionRisks);
    }

    /** A snapshot whose broker reconciliation has not been run yet. */
    public PortfolioSnapshot(
            String occasion,
            ZonedDateTime takenAt,
            String modeLabel,
            String fundsAvailable,
            Figures allWorkspaces,
            List<WorkspaceRow> workspaces,
            RiskAnalytics.Report risk,
            List<RiskAnalytics.PositionRisk> positionRisks
    ) {
        this(occasion, takenAt, modeLabel, fundsAvailable, allWorkspaces, workspaces, risk, positionRisks, null);
    }

    public PortfolioSnapshot withBrokerCheck(BrokerCheck check) {
        return new PortfolioSnapshot(occasion, takenAt, modeLabel, fundsAvailable, allWorkspaces, workspaces, risk,
                positionRisks, check);
    }

    /**
     * What comparing NeuralArc's positions with Alpaca's found: how many symbols were compared and
     * which disagree, or why the comparison could not be made.
     */
    public record BrokerCheck(boolean checked, int symbolCount, List<Mismatch> mismatches, String note) {
        public BrokerCheck {
            mismatches = mismatches == null ? List.of() : List.copyOf(mismatches);
            note = note == null ? "" : note.trim();
        }

        public static BrokerCheck compared(int symbolCount, List<Mismatch> mismatches) {
            return new BrokerCheck(true, symbolCount, mismatches, "");
        }

        public static BrokerCheck unavailable(String reason) {
            return new BrokerCheck(false, 0, List.of(), reason);
        }
    }

    public record Mismatch(String symbol, String description) {
    }

    /** One scope's figures: its money, its P&amp;L, and how its positions stand. */
    public record Figures(
            BigDecimal marketValue,
            BigDecimal invested,
            BigDecimal upcomingBuys,
            BigDecimal realized,
            BigDecimal unrealized,
            BigDecimal total,
            BigDecimal today,
            int sells,
            double winRatePercent,
            int gainingCount,
            BigDecimal gainingPnl,
            int losingCount,
            BigDecimal losingPnl,
            int pendingBuyPositions,
            int pendingSellPositions,
            int openPositions
    ) {
        public Figures {
            marketValue = nz(marketValue);
            invested = nz(invested);
            upcomingBuys = nz(upcomingBuys);
            realized = nz(realized);
            unrealized = nz(unrealized);
            total = nz(total);
            today = nz(today);
            gainingPnl = nz(gainingPnl);
            losingPnl = nz(losingPnl);
        }

        public static Figures empty() {
            return new Figures(null, null, null, null, null, null, null, 0, 0.0, 0, null, 0, null, 0, 0, 0);
        }

        /** What is invested plus what the working buys will cost once they fill. */
        public BigDecimal committed() {
            return invested.add(upcomingBuys);
        }

        /** True when the scope holds nothing, has no order working and has never sold. */
        public boolean isEmpty() {
            return openPositions == 0 && sells == 0 && pendingBuyPositions == 0 && pendingSellPositions == 0
                    && invested.signum() == 0 && upcomingBuys.signum() == 0 && realized.signum() == 0;
        }

        private static BigDecimal nz(BigDecimal value) {
            return value == null ? BigDecimal.ZERO : value;
        }
    }

    public record WorkspaceRow(String name, Figures figures) {
    }
}
