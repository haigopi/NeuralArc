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

    /**
     * A held position's inputs for risk classification. Entry/current/stop/target are per-share prices;
     * shares is the open quantity. Recommendation-only rows (no shares) are not classified.
     */
    public record PositionInput(
            String symbol, String workspaceLabel, BigDecimal shares,
            BigDecimal entryPrice, BigDecimal currentPrice, BigDecimal stopPrice, BigDecimal targetPrice
    ) {
    }

    /** How a held position is trending against its plan. */
    public enum RiskVerdict {
        /** Comfortable gain with a cushion above entry. */
        ON_TRACK_WINNER,
        /** "Possible loser": in profit but the cushion above entry is thin — a small pullback flips it red. */
        PROTECT_GAINS,
        /** "Possible winner in losing": underwater but still above the stop — wait to book the loss. */
        WAIT_TO_BOOK_LOSS,
        /** Underwater and at/through the stop — the risk plan is breached, consider cutting the loss. */
        CUT_LOSS
    }

    /** A classified held position: its plan distances, current verdict, and the plain-language advice. */
    public record PositionRisk(
            String symbol, String workspaceLabel, BigDecimal shares, BigDecimal entryPrice,
            BigDecimal currentPrice, BigDecimal stopPrice, BigDecimal targetPrice, BigDecimal marketValue,
            BigDecimal unrealizedPnl, double unrealizedPercent, double distanceToStopPercent,
            double distanceToTargetPercent, RiskVerdict verdict, String advice
    ) {
    }

    /** Winner-cushion threshold (percent above entry) under which a profitable position becomes "at risk". */
    private static final BigDecimal PROTECT_GAINS_BUFFER_PERCENT = new BigDecimal("1.5");

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

    /**
     * Classify each held position against its plan (entry, stop, target) into a risk verdict with
     * plain-language advice. Positions with no open shares are skipped. Pure / side-effect free.
     */
    public static List<PositionRisk> classify(List<PositionInput> positions) {
        List<PositionRisk> result = new ArrayList<>();
        if (positions == null) {
            return result;
        }
        for (PositionInput p : positions) {
            if (p == null) {
                continue;
            }
            BigDecimal shares = nz(p.shares());
            BigDecimal entry = nz(p.entryPrice());
            BigDecimal current = nz(p.currentPrice());
            if (shares.compareTo(BigDecimal.ZERO) <= 0 || current.compareTo(BigDecimal.ZERO) <= 0
                    || entry.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal stop = nz(p.stopPrice());
            BigDecimal target = nz(p.targetPrice());
            boolean hasStop = stop.compareTo(BigDecimal.ZERO) > 0 && stop.compareTo(entry) < 0;
            boolean hasTarget = target.compareTo(BigDecimal.ZERO) > 0 && target.compareTo(entry) > 0;

            BigDecimal unrealizedPnl = Monetary.round(current.subtract(entry).multiply(shares));
            BigDecimal marketValue = Monetary.round(current.multiply(shares));
            double unrealizedPercent = percent(current.subtract(entry), entry);
            double distanceToStopPercent = hasStop ? percent(current.subtract(stop), current) : 0.0;
            double distanceToTargetPercent = hasTarget ? percent(target.subtract(current), current) : 0.0;

            RiskVerdict verdict;
            if (hasStop && current.compareTo(stop) <= 0) {
                verdict = RiskVerdict.CUT_LOSS;
            } else if (current.compareTo(entry) < 0) {
                verdict = RiskVerdict.WAIT_TO_BOOK_LOSS;
            } else if (BigDecimal.valueOf(unrealizedPercent).compareTo(PROTECT_GAINS_BUFFER_PERCENT) <= 0) {
                verdict = RiskVerdict.PROTECT_GAINS;
            } else {
                verdict = RiskVerdict.ON_TRACK_WINNER;
            }

            String advice = advice(verdict, unrealizedPercent, entry, stop, target, hasStop, hasTarget);
            result.add(new PositionRisk(
                    p.symbol() == null ? "" : p.symbol().toUpperCase(),
                    p.workspaceLabel() == null || p.workspaceLabel().isBlank() ? "Unassigned" : p.workspaceLabel(),
                    shares, Monetary.round(entry), Monetary.round(current),
                    hasStop ? Monetary.round(stop) : BigDecimal.ZERO,
                    hasTarget ? Monetary.round(target) : BigDecimal.ZERO,
                    marketValue, unrealizedPnl, unrealizedPercent, distanceToStopPercent,
                    distanceToTargetPercent, verdict, advice));
        }
        return result;
    }

    private static String advice(RiskVerdict verdict, double unrealizedPercent, BigDecimal entry,
                                 BigDecimal stop, BigDecimal target, boolean hasStop, boolean hasTarget) {
        return switch (verdict) {
            case CUT_LOSS -> "Price is at or below the stop " + money(stop)
                    + ". The risk plan is breached — consider booking the loss to cap downside.";
            case WAIT_TO_BOOK_LOSS -> "Down " + pct(Math.abs(unrealizedPercent)) + " but "
                    + (hasStop ? "still above the stop " + money(stop) : "no protective stop is set")
                    + (hasTarget ? "; room to the target " + money(target) : "")
                    + ". Wait to book the loss while the setup holds.";
            case PROTECT_GAINS -> "Only " + pct(unrealizedPercent) + " above entry " + money(entry)
                    + " — a small pullback flips this to a loss. Protect gains"
                    + (hasStop ? " or tighten the stop above entry." : " by tightening a stop.");
            case ON_TRACK_WINNER -> "Up " + pct(unrealizedPercent)
                    + (hasTarget ? "; target " + money(target) : "") + ". On track — let it work.";
        };
    }

    private static double percent(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return 0.0;
        }
        return numerator.multiply(BigDecimal.valueOf(100))
                .divide(denominator, 4, RoundingMode.HALF_UP).doubleValue();
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String money(BigDecimal value) {
        BigDecimal v = Monetary.round(nz(value));
        return v.signum() < 0 ? "-$" + v.abs().toPlainString() : "$" + v.toPlainString();
    }

    private static String pct(double value) {
        return String.format("%.1f%%", value);
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
