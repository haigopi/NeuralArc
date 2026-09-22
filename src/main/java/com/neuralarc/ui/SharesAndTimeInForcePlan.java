package com.neuralarc.ui;

import com.neuralarc.model.Strategy;
import com.neuralarc.model.TimeInForce;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * The edits behind "Change Shares & Time In Force": one row per unfilled entry, each with a new share
 * count and time in force, and an include flag. A global value applies to every included row, so
 * "everything except these few" is a global change with those rows excluded; any row can still be set
 * individually afterwards. Only included rows whose values actually differ become changes.
 */
final class SharesAndTimeInForcePlan {
    static final class Row {
        final String strategyId;
        final String symbol;
        final boolean workingAtBroker;
        final BigDecimal baseLimit;
        final int currentQuantity;
        final TimeInForce currentTimeInForce;
        boolean included = true;
        int quantity;
        TimeInForce timeInForce;

        Row(String strategyId, String symbol, boolean workingAtBroker, BigDecimal baseLimit,
            int currentQuantity, TimeInForce currentTimeInForce) {
            this.strategyId = strategyId;
            this.symbol = symbol;
            this.workingAtBroker = workingAtBroker;
            this.baseLimit = baseLimit;
            this.currentQuantity = currentQuantity;
            this.currentTimeInForce = currentTimeInForce == null ? TimeInForce.DAY : currentTimeInForce;
            this.quantity = currentQuantity;
            this.timeInForce = this.currentTimeInForce;
        }

        boolean changed() {
            return included && (quantity != currentQuantity || timeInForce != currentTimeInForce);
        }
    }

    /** One strategy's new values. */
    record Change(String strategyId, String symbol, boolean workingAtBroker, int quantity, TimeInForce timeInForce) {
    }

    private final List<Row> rows;

    SharesAndTimeInForcePlan(List<Row> rows) {
        this.rows = List.copyOf(rows);
    }

    static Row rowFor(Strategy strategy, boolean workingAtBroker) {
        return new Row(strategy.id(), strategy.symbol(), workingAtBroker, strategy.baseBuyLimitPrice(),
                strategy.baseBuyQuantity(), strategy.timeInForce());
    }

    List<Row> rows() {
        return rows;
    }

    /** Sets the quantity (when positive) and time in force (when not null) on every included row. */
    void applyToIncluded(int quantity, TimeInForce timeInForce) {
        for (Row row : rows) {
            if (!row.included) {
                continue;
            }
            if (quantity > 0) {
                row.quantity = quantity;
            }
            if (timeInForce != null) {
                row.timeInForce = timeInForce;
            }
        }
    }

    List<Change> changes() {
        List<Change> changes = new ArrayList<>();
        for (Row row : rows) {
            if (row.changed()) {
                changes.add(new Change(row.strategyId, row.symbol, row.workingAtBroker, Math.max(1, row.quantity), row.timeInForce));
            }
        }
        return changes;
    }

    /**
     * The strategy with the new quantity and time in force, its share and capital caps raised (or
     * lowered) by the same amount — otherwise a larger entry would be refused by its own risk limits.
     */
    static void apply(Strategy strategy, int quantity, TimeInForce timeInForce) {
        int delta = quantity - strategy.baseBuyQuantity();
        strategy.setMaxTotalQuantity(Math.max(quantity, strategy.maxTotalQuantity() + delta));
        BigDecimal price = strategy.baseBuyLimitPrice() == null ? BigDecimal.ZERO : strategy.baseBuyLimitPrice();
        BigDecimal capital = strategy.maxCapitalAllowed().add(price.multiply(BigDecimal.valueOf(delta)));
        BigDecimal entryCapital = price.multiply(BigDecimal.valueOf(quantity));
        strategy.setMaxCapitalAllowed(capital.max(entryCapital));
        strategy.setBaseBuyQuantity(quantity);
        strategy.setTimeInForce(timeInForce);
    }
}
