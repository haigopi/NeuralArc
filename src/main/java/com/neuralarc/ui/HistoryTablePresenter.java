package com.neuralarc.ui;

import com.neuralarc.model.StrategyOrder;
import com.neuralarc.model.StrategyOrderSide;
import com.neuralarc.model.StrategyOrderStatus;
import com.neuralarc.model.StrategyStage;
import com.neuralarc.model.StrategyStatus;
import com.neuralarc.util.BrokerOrderStatusUtil;
import com.neuralarc.util.Monetary;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.logging.Logger;

public final class HistoryTablePresenter {
    private static final Logger LOGGER = Logger.getLogger(HistoryTablePresenter.class.getName());

    public List<HistoryRow> buildRows(List<HistorySource> sources, Function<Instant, String> timestampFormatter) {
        List<HistoryRow> rows = new ArrayList<>();
        for (HistorySource source : sources) {
            if (!includeInTradeHistory(source)) {
                continue;
            }
            rows.addAll(buildFilledRows(source, timestampFormatter));
            appendFallbackRowIfNeeded(rows, source, timestampFormatter);
        }
        rows.sort(Comparator
                .comparing(HistoryRow::groupKey, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(HistoryRow::sortPriority)
                .thenComparing(HistoryRow::sortTime, Comparator.nullsLast(Comparator.reverseOrder())));
        return withSubtotals(rows);
    }

    private boolean includeInTradeHistory(HistorySource source) {
        if (source == null || source.strategyStatusEnum() != StrategyStatus.COMPLETED) {
            return false;
        }
        return source.orders().stream()
                .anyMatch(order -> order.status() == StrategyOrderStatus.FILLED
                        && order.side() == StrategyOrderSide.SELL
                        && isCompletedSellStage(order.stage()));
    }

    private boolean isCompletedSellStage(StrategyStage stage) {
        return stage == StrategyStage.TARGET_SELL
                || stage == StrategyStage.PROFIT_EXIT
                || stage == StrategyStage.STOP_LOSS
                || stage == StrategyStage.LOSS_EXIT
                || stage == StrategyStage.MANUAL_EXIT
                || stage == StrategyStage.CLOSE_POSITION;
    }

    private List<HistoryRow> buildFilledRows(HistorySource source, Function<Instant, String> timestampFormatter) {
        List<StrategyOrder> filledOrders = source.orders().stream()
                .filter(order -> order.status() == StrategyOrderStatus.FILLED)
                .sorted(Comparator
                        .comparing(this::historyTimestamp, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(StrategyOrder::submittedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        List<HistoryRow> rows = new ArrayList<>();
        BigDecimal positionQty = BigDecimal.ZERO;
        BigDecimal averageCost = BigDecimal.ZERO;

        for (StrategyOrder order : filledOrders) {
            BigDecimal quantity = resolvedFilledQuantity(order);
            BigDecimal fillPrice = resolvedFillPrice(order);
            String realizedPnlDisplay = "-";

            if (order.side() == StrategyOrderSide.BUY) {
                BigDecimal runningCost = averageCost.multiply(positionQty).add(fillPrice.multiply(quantity));
                positionQty = positionQty.add(quantity);
                if (positionQty.compareTo(BigDecimal.ZERO) > 0) {
                    averageCost = runningCost.divide(positionQty, 8, java.math.RoundingMode.HALF_UP);
                }
            } else {
                BigDecimal sellQty = quantity.min(positionQty.max(BigDecimal.ZERO));
                if (sellQty.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal realizedPnl = Monetary.round(fillPrice.subtract(averageCost).multiply(sellQty));
                    realizedPnlDisplay = realizedPnl.toPlainString();
                    positionQty = positionQty.subtract(sellQty);
                    if (positionQty.compareTo(BigDecimal.ZERO) == 0) {
                        averageCost = BigDecimal.ZERO;
                    }
                }
            }

            Instant rowTime = historyTimestamp(order);
            rows.add(new HistoryRow(
                    source.symbol(),
                    source.symbol(),
                    source.brokerMode(),
                    source.strategyStatus(),
                    formatStageForHistory(order.stage()),
                    order.side().name(),
                    strategyOrderStatusForDisplay(null, order, order.status().name()),
                    quantity.compareTo(BigDecimal.ZERO) > 0 ? quantity.toPlainString() : "-",
                    fillPrice.compareTo(BigDecimal.ZERO) > 0 ? fillPrice.toPlainString() : "-",
                    realizedPnlDisplay,
                    rowTime == null ? "-" : timestampFormatter.apply(rowTime),
                    rowTime,
                    order.side() == StrategyOrderSide.SELL ? 0 : 1,
                    historyRowStyleFor(order.side(), realizedPnlDisplay)
            ));
        }
        return rows;
    }

    private void appendFallbackRowIfNeeded(List<HistoryRow> rows, HistorySource source, Function<Instant, String> timestampFormatter) {
        if (source.strategyStatusEnum() != StrategyStatus.FAILED && source.strategyStatusEnum() != StrategyStatus.COMPLETED) {
            return;
        }
        boolean hasFilledOrder = source.orders().stream().anyMatch(order -> order.status() == StrategyOrderStatus.FILLED);
        if (hasFilledOrder) {
            return;
        }
        rows.add(new HistoryRow(
                source.symbol(),
                source.symbol(),
                source.brokerMode(),
                source.strategyStatus(),
                source.currentStateLabel(),
                "-",
                strategyOrderStatusForDisplay(source.latestOrderStatus(), null, source.currentStateLabel()),
                "-",
                "-",
                "-",
                source.lastPolledAt() == null ? "-" : timestampFormatter.apply(source.lastPolledAt()),
                source.lastPolledAt(),
                2,
                source.strategyStatusEnum() == StrategyStatus.FAILED ? HistoryRowStyle.FAILED : HistoryRowStyle.COMPLETED
        ));
    }

    private List<HistoryRow> withSubtotals(List<HistoryRow> rows) {
        List<HistoryRow> withSubtotals = new ArrayList<>();
        String currentGroupKey = null;
        BigDecimal groupPnl = BigDecimal.ZERO;
        boolean groupHasNumericPnl = false;
        List<HistoryRow> currentGroupRows = new ArrayList<>();

        for (HistoryRow row : rows) {
            if (!row.groupKey().equalsIgnoreCase(currentGroupKey)) {
                if (currentGroupKey != null) {
                    withSubtotals.addAll(currentGroupRows);
                    if (groupHasNumericPnl) {
                        withSubtotals.add(buildSubtotalRow(currentGroupKey, groupPnl));
                    }
                }
                currentGroupKey = row.groupKey();
                groupPnl = BigDecimal.ZERO;
                groupHasNumericPnl = false;
                currentGroupRows = new ArrayList<>();
            }
            currentGroupRows.add(row);
            if (row.style() != HistoryRowStyle.SUBTOTAL
                    && row.realizedPnl() != null
                    && !"-".equals(row.realizedPnl())
                    && !row.realizedPnl().isBlank()) {
                try {
                    groupPnl = groupPnl.add(new BigDecimal(row.realizedPnl()));
                    groupHasNumericPnl = true;
                } catch (NumberFormatException ignored) {
                }
            }
        }

        if (currentGroupKey != null) {
            withSubtotals.addAll(currentGroupRows);
            if (groupHasNumericPnl) {
                withSubtotals.add(buildSubtotalRow(currentGroupKey, groupPnl));
            }
        }

        return withSubtotals;
    }

    private HistoryRowStyle historyRowStyleFor(StrategyOrderSide side, String realizedPnlDisplay) {
        if (side == StrategyOrderSide.BUY) {
            return HistoryRowStyle.BUY;
        }
        if (realizedPnlDisplay == null || realizedPnlDisplay.isBlank() || "-".equals(realizedPnlDisplay)) {
            return HistoryRowStyle.SELL_NEUTRAL;
        }
        try {
            BigDecimal realized = new BigDecimal(realizedPnlDisplay);
            if (realized.compareTo(BigDecimal.ZERO) > 0) {
                return HistoryRowStyle.SELL_GAIN;
            }
            if (realized.compareTo(BigDecimal.ZERO) < 0) {
                return HistoryRowStyle.SELL_LOSS;
            }
            return HistoryRowStyle.SELL_NEUTRAL;
        } catch (NumberFormatException ignored) {
            return HistoryRowStyle.SELL_NEUTRAL;
        }
    }

    private HistoryRow buildSubtotalRow(String groupKey, BigDecimal total) {
        return new HistoryRow(
                groupKey,
                groupKey,
                "",
                "",
                "Subtotal",
                "",
                "",
                "",
                "",
                Monetary.round(total).toPlainString(),
                "",
                null,
                3,
                HistoryRowStyle.SUBTOTAL
        );
    }

    private String strategyOrderStatusForDisplay(String latestStrategyOrderStatus, StrategyOrder order, String fallback) {
        String rawStatus = latestStrategyOrderStatus;
        if (order != null && order.rawResponseJson() != null && !order.rawResponseJson().isBlank()) {
            try {
                rawStatus = new JSONObject(order.rawResponseJson()).optString("status", rawStatus);
            } catch (Exception ignored) {
            }
        }
        String normalized = BrokerOrderStatusUtil.normalize(rawStatus);
        if (normalized.isBlank()) {
            return fallback == null || fallback.isBlank() ? "-" : fallback;
        }
        return BrokerOrderStatusUtil.displayLabel(normalized);
    }

    private Instant historyTimestamp(StrategyOrder order) {
        if (order == null) {
            return null;
        }
        if (order.filledAt() != null) {
            return order.filledAt();
        }
        if (order.updatedAt() != null) {
            return order.updatedAt();
        }
        return order.submittedAt();
    }

    private BigDecimal resolvedFillPrice(StrategyOrder order) {
        if (order == null) {
            return BigDecimal.ZERO;
        }
        if (order.filledAveragePrice() != null && order.filledAveragePrice().compareTo(BigDecimal.ZERO) > 0) {
            return order.filledAveragePrice();
        }
        return order.limitPrice() == null ? BigDecimal.ZERO : order.limitPrice();
    }

    private BigDecimal resolvedFilledQuantity(StrategyOrder order) {
        if (order == null) {
            return BigDecimal.ZERO;
        }
        if (order.filledQuantity() != null && order.filledQuantity().compareTo(BigDecimal.ZERO) > 0) {
            return order.filledQuantity();
        }
        if (order.status() == StrategyOrderStatus.FILLED
                && order.requestedQuantity() != null
                && order.requestedQuantity().compareTo(BigDecimal.ZERO) > 0) {
            LOGGER.fine(() -> "[HISTORY][PNL] quantityFallback requestedQuantityUsed"
                    + " orderId=" + order.id()
                    + " clientOrderId=" + order.clientOrderId()
                    + " symbol=" + order.symbol()
                    + " stage=" + order.stage()
                    + " side=" + order.side()
                    + " filledQty=" + order.filledQuantity()
                    + " requestedQty=" + order.requestedQuantity());
            return order.requestedQuantity();
        }
        return BigDecimal.ZERO;
    }

    private String formatStageForHistory(StrategyStage stage) {
        if (stage == null) {
            return "-";
        }
        String[] parts = stage.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.isEmpty() ? stage.name() : builder.toString();
    }

    public record HistorySource(
            String symbol,
            String brokerMode,
            String strategyStatus,
            String currentStateLabel,
            String latestOrderStatus,
            Instant lastPolledAt,
            StrategyStatus strategyStatusEnum,
            List<StrategyOrder> orders
    ) {
    }

    public record HistoryRow(
            String symbol,
            String groupKey,
            String brokerMode,
            String strategyStatus,
            String stage,
            String side,
            String orderStatus,
            String quantity,
            String fillPrice,
            String realizedPnl,
            String whenDisplay,
            Instant sortTime,
            int sortPriority,
            HistoryRowStyle style
    ) {
    }

    public enum HistoryRowStyle {
        BUY,
        SELL_GAIN,
        SELL_LOSS,
        SELL_NEUTRAL,
        FAILED,
        COMPLETED,
        SUBTOTAL
    }
}
