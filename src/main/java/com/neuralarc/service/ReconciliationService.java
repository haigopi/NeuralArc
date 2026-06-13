package com.neuralarc.service;

import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Compares NeuralArc's strategy-level positions (aggregated per symbol) against Alpaca's
 * broker-level positions and reports mismatches.
 *
 * <p>NeuralArc is the strategy accounting authority; Alpaca is the broker/position authority. The
 * two can legitimately differ in presentation (Alpaca blends a symbol held by several strategies),
 * but the per-symbol <em>totals</em> should agree. This service surfaces discrepancies and
 * <strong>never auto-corrects</strong> — it only produces an immutable {@link Report}.
 *
 * <p>The comparison is a pure function so it is fully unit-testable; the broker pull happens in the
 * UI layer on a background thread and feeds the aggregated inputs here.
 */
public final class ReconciliationService {
    private static final BigDecimal DEFAULT_COST_TOLERANCE = new BigDecimal("0.01");

    private final BigDecimal costTolerance;

    public ReconciliationService() {
        this(DEFAULT_COST_TOLERANCE);
    }

    public ReconciliationService(BigDecimal costTolerance) {
        this.costTolerance = costTolerance == null ? DEFAULT_COST_TOLERANCE : costTolerance.abs();
    }

    public Report reconcile(List<SymbolPosition> localPositions, List<SymbolPosition> brokerPositions) {
        Map<String, SymbolPosition> local = aggregateBySymbol(localPositions);
        Map<String, SymbolPosition> broker = aggregateBySymbol(brokerPositions);

        TreeSet<String> symbols = new TreeSet<>();
        symbols.addAll(local.keySet());
        symbols.addAll(broker.keySet());

        List<Line> lines = new ArrayList<>();
        for (String symbol : symbols) {
            SymbolPosition localPos = local.get(symbol);
            SymbolPosition brokerPos = broker.get(symbol);
            BigDecimal localQty = localPos == null ? BigDecimal.ZERO : localPos.quantity();
            BigDecimal brokerQty = brokerPos == null ? BigDecimal.ZERO : brokerPos.quantity();
            BigDecimal localCost = localPos == null ? BigDecimal.ZERO : localPos.averageCost();
            BigDecimal brokerCost = brokerPos == null ? BigDecimal.ZERO : brokerPos.averageCost();
            lines.add(new Line(symbol, localQty, brokerQty, localCost, brokerCost,
                    classify(localPos, brokerPos)));
        }
        return new Report(List.copyOf(lines), Instant.now());
    }

    private Status classify(SymbolPosition local, SymbolPosition broker) {
        boolean hasLocal = local != null && local.quantity().compareTo(BigDecimal.ZERO) > 0;
        boolean hasBroker = broker != null && broker.quantity().compareTo(BigDecimal.ZERO) > 0;
        if (hasLocal && !hasBroker) {
            return Status.MISSING_BROKER;   // tracked locally, broker shows nothing
        }
        if (!hasLocal && hasBroker) {
            return Status.MISSING_LOCAL;     // broker holds it, no local strategy owns it
        }
        if (!hasLocal) {
            return Status.MATCH;             // both flat
        }
        if (local.quantity().compareTo(broker.quantity()) != 0) {
            return Status.QTY_MISMATCH;
        }
        if (local.averageCost().subtract(broker.averageCost()).abs().compareTo(costTolerance) > 0) {
            return Status.COST_MISMATCH;
        }
        return Status.MATCH;
    }

    private Map<String, SymbolPosition> aggregateBySymbol(List<SymbolPosition> positions) {
        Map<String, BigDecimal> qtyBySymbol = new LinkedHashMap<>();
        Map<String, BigDecimal> costWeightBySymbol = new LinkedHashMap<>();
        if (positions != null) {
            for (SymbolPosition position : positions) {
                if (position == null || position.symbol() == null || position.symbol().isBlank()) {
                    continue;
                }
                String symbol = position.symbol().toUpperCase();
                BigDecimal qty = position.quantity() == null ? BigDecimal.ZERO : position.quantity();
                BigDecimal cost = position.averageCost() == null ? BigDecimal.ZERO : position.averageCost();
                qtyBySymbol.merge(symbol, qty, BigDecimal::add);
                costWeightBySymbol.merge(symbol, cost.multiply(qty), BigDecimal::add);
            }
        }
        Map<String, SymbolPosition> result = new LinkedHashMap<>();
        for (Map.Entry<String, BigDecimal> entry : qtyBySymbol.entrySet()) {
            BigDecimal qty = entry.getValue();
            BigDecimal avgCost = qty.compareTo(BigDecimal.ZERO) > 0
                    ? costWeightBySymbol.get(entry.getKey()).divide(qty, 4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            result.put(entry.getKey(), new SymbolPosition(entry.getKey(), qty, Monetary.round(avgCost)));
        }
        return result;
    }

    public record SymbolPosition(String symbol, BigDecimal quantity, BigDecimal averageCost) {
    }

    public enum Status {
        MATCH,
        QTY_MISMATCH,
        COST_MISMATCH,
        MISSING_LOCAL,   // present at broker, no local strategy
        MISSING_BROKER   // tracked locally, absent at broker
    }

    public record Line(
            String symbol,
            BigDecimal localQuantity,
            BigDecimal brokerQuantity,
            BigDecimal localAverageCost,
            BigDecimal brokerAverageCost,
            Status status
    ) {
    }

    public record Report(List<Line> lines, Instant generatedAt) {
        public long mismatchCount() {
            return lines.stream().filter(line -> line.status() != Status.MATCH).count();
        }

        public boolean hasMismatches() {
            return mismatchCount() > 0;
        }
    }
}
