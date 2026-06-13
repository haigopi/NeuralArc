package com.neuralarc.analytics;

import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure strategy-level risk analytics for the risk dashboard: exposure by symbol and by workspace,
 * capital concentration, largest winner/loser, and a workspace P&amp;L ranking.
 *
 * <p>All inputs are already mode-scoped strategy holdings (NeuralArc accounting), so the numbers
 * are strategy-centric, not Alpaca's blended view. Side-effect free for unit testing.
 */
public final class RiskAnalytics {
    private RiskAnalytics() {
    }

    /** One strategy's contribution: its symbol, owning workspace label, market value and total P&L. */
    public record Holding(String symbol, String workspaceLabel, BigDecimal marketValue, BigDecimal totalPnl) {
    }

    public record Exposure(String key, BigDecimal value, double percentOfTotal) {
    }

    public record Report(
            BigDecimal totalCapital,
            List<Exposure> exposureBySymbol,
            List<Exposure> exposureByWorkspace,
            double topSymbolConcentrationPercent,
            String largestWinnerSymbol,
            BigDecimal largestWinnerPnl,
            String largestLoserSymbol,
            BigDecimal largestLoserPnl,
            List<Exposure> workspacePnlRanking
    ) {
    }

    public static Report analyze(List<Holding> holdings) {
        Map<String, BigDecimal> marketValueBySymbol = new LinkedHashMap<>();
        Map<String, BigDecimal> pnlBySymbol = new LinkedHashMap<>();
        Map<String, BigDecimal> marketValueByWorkspace = new LinkedHashMap<>();
        Map<String, BigDecimal> pnlByWorkspace = new LinkedHashMap<>();
        BigDecimal totalCapital = BigDecimal.ZERO;

        if (holdings != null) {
            for (Holding holding : holdings) {
                if (holding == null) {
                    continue;
                }
                String symbol = holding.symbol() == null ? "" : holding.symbol().toUpperCase();
                String workspace = holding.workspaceLabel() == null || holding.workspaceLabel().isBlank()
                        ? "Unassigned" : holding.workspaceLabel();
                BigDecimal value = holding.marketValue() == null ? BigDecimal.ZERO : holding.marketValue();
                BigDecimal pnl = holding.totalPnl() == null ? BigDecimal.ZERO : holding.totalPnl();
                marketValueBySymbol.merge(symbol, value, BigDecimal::add);
                pnlBySymbol.merge(symbol, pnl, BigDecimal::add);
                marketValueByWorkspace.merge(workspace, value, BigDecimal::add);
                pnlByWorkspace.merge(workspace, pnl, BigDecimal::add);
                totalCapital = totalCapital.add(value);
            }
        }

        List<Exposure> exposureBySymbol = toExposures(marketValueBySymbol, totalCapital);
        List<Exposure> exposureByWorkspace = toExposures(marketValueByWorkspace, totalCapital);
        double concentration = exposureBySymbol.isEmpty() ? 0.0 : exposureBySymbol.get(0).percentOfTotal();

        String winnerSymbol = "";
        BigDecimal winnerPnl = BigDecimal.ZERO;
        String loserSymbol = "";
        BigDecimal loserPnl = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> entry : pnlBySymbol.entrySet()) {
            if (winnerSymbol.isEmpty() || entry.getValue().compareTo(winnerPnl) > 0) {
                winnerSymbol = entry.getKey();
                winnerPnl = entry.getValue();
            }
            if (loserSymbol.isEmpty() || entry.getValue().compareTo(loserPnl) < 0) {
                loserSymbol = entry.getKey();
                loserPnl = entry.getValue();
            }
        }

        List<Exposure> ranking = new ArrayList<>();
        pnlByWorkspace.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .forEach(entry -> ranking.add(new Exposure(entry.getKey(), Monetary.round(entry.getValue()), 0.0)));

        return new Report(
                Monetary.round(totalCapital),
                exposureBySymbol,
                exposureByWorkspace,
                concentration,
                winnerSymbol,
                Monetary.round(winnerPnl),
                loserSymbol,
                Monetary.round(loserPnl),
                ranking
        );
    }

    private static List<Exposure> toExposures(Map<String, BigDecimal> valueByKey, BigDecimal total) {
        List<Exposure> exposures = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : valueByKey.entrySet()) {
            double percent = total.compareTo(BigDecimal.ZERO) > 0
                    ? entry.getValue().multiply(BigDecimal.valueOf(100))
                        .divide(total, 2, RoundingMode.HALF_UP).doubleValue()
                    : 0.0;
            exposures.add(new Exposure(entry.getKey(), Monetary.round(entry.getValue()), percent));
        }
        exposures.sort(Comparator.comparing(Exposure::value).reversed());
        return exposures;
    }
}
