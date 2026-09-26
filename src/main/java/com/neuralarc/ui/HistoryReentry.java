package com.neuralarc.ui;

import com.neuralarc.analytics.RecentLow;
import com.neuralarc.model.MarketBar;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyConfig;
import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyOrder;
import com.neuralarc.model.StrategyOrderStatus;
import com.neuralarc.model.StrategyStatus;
import com.neuralarc.model.ThresholdType;
import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * "Re-enter Inactive Stocks": the stocks in Trade History that no workspace is trading any more, placed
 * again at a safe low in a workspace of their own.
 *
 * <p>The safe low is the lowest price the stock actually traded over the last week of sessions
 * ({@link RecentLow#weekLow}): cautious, but a price the stock has really printed, so the order can
 * fill. Each stock reuses its most recent traded plan, with every absolute price level — stop, target,
 * loss-buy levels — rescaled by the same ratio as the entry, so the plan keeps its percentages instead
 * of carrying stale prices from the old trade.
 */
final class HistoryReentry {
    static final String NAME_PREFIX = "HISTORY_REENTRY";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d", Locale.US);

    private HistoryReentry() {
    }

    /**
     * One source strategy per stock that has traded in {@code mode} (has a fill) but is not being
     * traded any more. A stock is excluded when any workspace holds a live row for it — active, paused
     * or awaiting placement, which covers both an entry still pending a fill and a filled position —
     * and when {@code heldSymbols} says shares are still held, which catches a position left behind by
     * a stopped or failed strategy. The most recently updated traded strategy is the source of its plan.
     */
    static List<Strategy> candidates(List<Strategy> strategies, StrategyMode mode,
                                     Function<String, List<StrategyOrder>> ordersByStrategyId,
                                     java.util.Set<String> heldSymbols) {
        java.util.Set<String> held = heldSymbols == null ? java.util.Set.of() : heldSymbols.stream()
                .filter(java.util.Objects::nonNull)
                .map(symbol -> symbol.toUpperCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        return candidates(strategies, mode, ordersByStrategyId).stream()
                .filter(source -> !held.contains(source.symbol().toUpperCase(Locale.ROOT)))
                .toList();
    }

    private static List<Strategy> candidates(List<Strategy> strategies, StrategyMode mode,
                                     Function<String, List<StrategyOrder>> ordersByStrategyId) {
        Map<String, List<Strategy>> bySymbol = new LinkedHashMap<>();
        for (Strategy strategy : strategies) {
            if (strategy != null && strategy.mode() == mode && strategy.symbol() != null && !strategy.symbol().isBlank()) {
                bySymbol.computeIfAbsent(strategy.symbol().toUpperCase(Locale.ROOT), ignored -> new java.util.ArrayList<>()).add(strategy);
            }
        }
        return bySymbol.values().stream()
                .filter(rows -> rows.stream().noneMatch(HistoryReentry::isLive))
                .map(rows -> rows.stream()
                        .filter(row -> hasFill(ordersByStrategyId.apply(row.id())))
                        .max(Comparator.comparing(row -> row.updatedAt() == null ? Instant.EPOCH : row.updatedAt())))
                .flatMap(java.util.Optional::stream)
                .sorted(Comparator.comparing(Strategy::symbol))
                .toList();
    }

    /** Sessions in the two-week window the picker averages for context. */
    static final int TWO_WEEK_SESSIONS = 10;

    /**
     * What the picker shows for one stock before the operator ticks it: the price it would go in at,
     * and the range it has been trading in. Zero means the prices could not be read.
     */
    record Levels(BigDecimal entryPrice, BigDecimal averageLow, BigDecimal averageHigh) {
        static Levels unknown() {
            return new Levels(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        boolean known() {
            return entryPrice != null && entryPrice.signum() > 0;
        }
    }

    /**
     * The entry price and the two-week averages for one stock, from its daily bars.
     *
     * <p>The entry price is the same {@link #safeLow} the placement uses, so the number the operator
     * ticks is the number that gets placed. The averages are the mean of each session's low and each
     * session's high over the last {@link #TWO_WEEK_SESSIONS} sessions: together they say whether the
     * entry sits under the stock's recent range or inside it.
     */
    static Levels levels(List<MarketBar> dailyBars, LocalDate today) {
        BigDecimal weekLow = safeLow(dailyBars, today);
        BigDecimal twoWeekLow = lowestLow(dailyBars);
        // Whichever of the two is lower. Going back a week alone put some re-entries above the price
        // the stock traded at only days earlier, which is not a pullback — it is paying up for a stock
        // that has already been cheaper this fortnight.
        BigDecimal entry = twoWeekLow.signum() > 0 && (weekLow.signum() <= 0 || twoWeekLow.compareTo(weekLow) < 0)
                ? twoWeekLow
                : weekLow;
        return new Levels(entry, average(dailyBars, MarketBar::low), average(dailyBars, MarketBar::high));
    }

    /** The lowest price traded over the last {@link #TWO_WEEK_SESSIONS} sessions, or zero when unknown. */
    static BigDecimal lowestLow(List<MarketBar> dailyBars) {
        if (dailyBars == null || dailyBars.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal lowest = null;
        for (MarketBar bar : dailyBars.subList(Math.max(0, dailyBars.size() - TWO_WEEK_SESSIONS), dailyBars.size())) {
            BigDecimal low = bar == null ? null : bar.low();
            if (low != null && low.signum() > 0 && (lowest == null || low.compareTo(lowest) < 0)) {
                lowest = low;
            }
        }
        return lowest == null ? BigDecimal.ZERO : Monetary.round(lowest);
    }

    /** The safe low: the lowest traded price over the last week of sessions, or zero when unknown. */
    static BigDecimal safeLow(List<MarketBar> dailyBars, LocalDate today) {
        return Monetary.round(RecentLow.weekLow(dailyBars, RecentLow.sessionLow(dailyBars, today)));
    }

    /** A new strategy re-entering {@code source}'s stock at {@code safeLow}, waiting to be placed. */
    static Strategy reentry(Strategy source, BigDecimal safeLow, String workspaceId) {
        StrategyConfig old = new ManagedStrategy(source).toConfig();
        BigDecimal oldBase = old.baseBuyPrice();
        BigDecimal ratio = oldBase == null || oldBase.signum() <= 0
                ? BigDecimal.ONE
                : safeLow.divide(oldBase, 8, RoundingMode.HALF_UP);
        StrategyConfig config = new StrategyConfig(
                old.symbol(),
                safeLow,
                Math.max(1, old.baseBuyQty()),
                old.stopLossEnabled(),
                scale(old.stopLoss(), ratio),
                old.sellTriggerEnabled(),
                scale(old.sellTriggerPrice(), ratio),
                scale(old.lossBuyLevel1Price(), ratio),
                old.lossBuyLevel1Qty(),
                scale(old.lossBuyLevel2Price(), ratio),
                old.lossBuyLevel2Qty(),
                old.lossBuyLevelsEnabled(),
                old.optionalLossExitEnabled(),
                scale(old.optionalLossExitPrice(), ratio),
                old.pollingSeconds(),
                old.paperTrading(),
                old.alpacaTrailingStopEnabled(),
                old.profitHoldEnabled(),
                old.profitHoldType(),
                old.profitHoldPercent(),
                old.profitHoldAmount(),
                old.repeatCycleAfterProfitExitEnabled(),
                old.profitControlMode(),
                old.automaticStopSellThresholdType(),
                old.automaticStopSellThresholdType() == ThresholdType.FIXED_AMOUNT
                        ? scale(old.automaticStopSellThreshold(), ratio)
                        : old.automaticStopSellThreshold(),
                old.automaticStopSellTrailingType(),
                old.automaticStopSellTrailingValue(),
                old.resubmitOnExpiryEnabled(),
                old.baseBuyRepostReductionPercent(),
                old.timeInForce(),
                old.autoAdjustRisk());
        Strategy strategy = Strategy.fromConfig(UUID.randomUUID().toString(),
                NAME_PREFIX + ": " + source.symbol() + " " + (source.mode() == StrategyMode.LIVE ? "Live" : "Paper"),
                config, source.mode());
        strategy.setWorkspaceId(workspaceId);
        strategy.setStatus(StrategyStatus.CREATED);
        strategy.setCurrentState(StrategyLifecycleState.CREATED);
        strategy.setLastEvent("Re-entered from Trade History at the safe low $" + safeLow.toPlainString()
                + " (lowest price traded this week).");
        return strategy;
    }

    /** "Comeback Picks · Sep 22" — the workspace a run's re-entries go into. */
    static String workspaceName(LocalDate day) {
        return "Comeback Picks · " + DAY.format(day);
    }

    /** The mean of one field over the last {@link #TWO_WEEK_SESSIONS} sessions, or zero when unknown. */
    private static BigDecimal average(List<MarketBar> dailyBars, Function<MarketBar, BigDecimal> field) {
        if (dailyBars == null || dailyBars.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = BigDecimal.ZERO;
        int counted = 0;
        for (MarketBar bar : dailyBars.subList(Math.max(0, dailyBars.size() - TWO_WEEK_SESSIONS), dailyBars.size())) {
            BigDecimal value = bar == null ? null : field.apply(bar);
            if (value != null && value.signum() > 0) {
                total = total.add(value);
                counted++;
            }
        }
        return counted == 0 ? BigDecimal.ZERO : Monetary.round(total.divide(BigDecimal.valueOf(counted), 6, RoundingMode.HALF_UP));
    }

    private static boolean isLive(Strategy strategy) {
        StrategyStatus status = strategy.status();
        return status == StrategyStatus.ACTIVE || status == StrategyStatus.PAUSED || status == StrategyStatus.CREATED;
    }

    private static boolean hasFill(List<StrategyOrder> orders) {
        return orders != null && orders.stream().anyMatch(order -> order.status() == StrategyOrderStatus.FILLED
                || order.status() == StrategyOrderStatus.PARTIALLY_FILLED
                || (order.filledQuantity() != null && order.filledQuantity().signum() > 0));
    }

    private static BigDecimal scale(BigDecimal price, BigDecimal ratio) {
        return price == null || price.signum() <= 0 ? price : Monetary.round(price.multiply(ratio));
    }
}
