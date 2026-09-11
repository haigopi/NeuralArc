package com.neuralarc.ui;

import com.neuralarc.model.Position;
import com.neuralarc.model.Strategy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * The strategy's own plan, drawn on its stock chart as horizontal price lines. Built from the row's
 * cached snapshot only, so opening a chart never touches the broker from the UI thread.
 */
final class StockChartLevels {
    private static final double SAME_PRICE_TOLERANCE = 0.005;

    private StockChartLevels() {
    }

    enum Kind {
        BASE_BUY,
        LOSS_BUY,
        AVERAGE_COST,
        STOP_LOSS,
        TARGET,
        WORKING_SELL,
        WORKING_BUY
    }

    /** One line on the chart: what it is, its price, and a sentence explaining it. */
    record Level(Kind kind, String label, double price, String meaning) {
    }

    static List<Level> from(ManagedStrategy entry) {
        List<Level> levels = new ArrayList<>();
        if (entry == null || entry.strategy == null) {
            return levels;
        }
        Strategy strategy = entry.strategy;
        Position position = entry.cachedPosition();
        boolean holding = position.getTotalShares() > 0 && positive(position.getAverageCost());

        if (holding) {
            levels.add(new Level(Kind.AVERAGE_COST, "Your avg cost", position.getAverageCost().doubleValue(),
                    "What you paid per share on average. Above this line the position is in profit."));
        } else if (positive(strategy.baseBuyLimitPrice())) {
            levels.add(new Level(Kind.BASE_BUY, "Planned buy", strategy.baseBuyLimitPrice().doubleValue(),
                    "Where the strategy places its first buy."));
        }
        if (strategy.lossBuyLevelsEnabled()) {
            if (positive(strategy.buyLimit1Price())) {
                levels.add(new Level(Kind.LOSS_BUY, "Buy level 1", strategy.buyLimit1Price().doubleValue(),
                        "An extra buy the strategy adds if price falls this far."));
            }
            if (positive(strategy.buyLimit2Price())) {
                levels.add(new Level(Kind.LOSS_BUY, "Buy level 2", strategy.buyLimit2Price().doubleValue(),
                        "A second extra buy, further down."));
            }
        }
        if (strategy.automatedStopLossEnabled() && positive(strategy.stopLossPrice())) {
            levels.add(new Level(Kind.STOP_LOSS, "Stop loss", strategy.stopLossPrice().doubleValue(),
                    "If price falls here, the strategy sells to limit the loss."));
        }

        StrategyTablePresenter.PendingOrderSummary sell = entry.cachedPendingLimitSell();
        double workingSell = sell != null && positive(sell.limitPrice()) ? sell.limitPrice().doubleValue() : Double.NaN;
        if (strategy.targetSellEnabled() && positive(strategy.targetSellPrice())) {
            double target = strategy.targetSellPrice().doubleValue();
            boolean sellAtTarget = !Double.isNaN(workingSell) && Math.abs(workingSell - target) < SAME_PRICE_TOLERANCE;
            levels.add(new Level(Kind.TARGET, sellAtTarget ? "Target · sell working" : "Target", target,
                    sellAtTarget
                            ? "Where the strategy takes profit. A limit sell is already working at this price."
                            : "Where the strategy aims to take profit."));
            if (sellAtTarget) {
                workingSell = Double.NaN;
            }
        }
        if (!Double.isNaN(workingSell)) {
            levels.add(new Level(Kind.WORKING_SELL, "Sell order", workingSell,
                    "A limit sell working at the broker now."));
        }
        StrategyTablePresenter.PendingOrderSummary buy = entry.cachedPendingManualBuy();
        if (buy != null && positive(buy.limitPrice())) {
            levels.add(new Level(Kind.WORKING_BUY, "Buy order", buy.limitPrice().doubleValue(),
                    "A limit buy working at the broker now."));
        }
        return levels;
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }
}
