package com.neuralarc.ui;

import com.neuralarc.model.StrategyOrder;
import com.neuralarc.model.StrategyOrderSide;
import com.neuralarc.model.StrategyOrderStatus;
import com.neuralarc.model.StrategyStage;
import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

final class RuleTriggeredHistoryPresenter {
    private static final String FAILURE_COLOR = "#B71C1C";
    private static final String FAILURE_BACKGROUND = "#FFF59D";

    /** Most recent steps shown inline; the section is a plain label with no scrollbar. */
    private static final int MAX_INLINE_ENTRIES = 6;

    String buildLabel(String currentRuleText, List<StrategyOrder> orders, Function<Instant, String> timestampFormatter) {
        String current = normalizeCurrentRule(currentRuleText);
        List<HistoryEntry> history = historyEntries(orders, timestampFormatter);
        if (history.isEmpty()) {
            return currentRuleText == null || currentRuleText.isBlank() ? "Rules: -" : currentRuleText;
        }
        int hidden = Math.max(0, history.size() - MAX_INLINE_ENTRIES);
        List<HistoryEntry> visible = history.subList(hidden, history.size());
        String heading = hidden > 0
                ? "Timeline (latest " + visible.size() + " of " + history.size() + " — hover for all):"
                : "Timeline:";
        return "<html><div style='width:1120px;'>"
                + "<b>Current:</b> " + escape(current)
                + "<br><b>" + heading + "</b>"
                + timelineTableHtml(visible, hidden)
                + "</div></html>";
    }

    String buildTooltip(String currentRuleHtml, List<StrategyOrder> orders, Function<Instant, String> timestampFormatter) {
        List<HistoryEntry> history = historyEntries(orders, timestampFormatter);
        if (history.isEmpty()) {
            return currentRuleHtml;
        }
        return currentRuleHtml
                + "<br><br><b>Triggered Rule Timeline:</b>"
                + timelineTableHtml(history, 0);
    }

    /**
     * Oldest to newest, one step per row, with the timestamp in its own aligned column so the
     * sequence reads as a timeline instead of a single run-on line.
     */
    private String timelineTableHtml(List<HistoryEntry> entries, int hiddenCount) {
        StringBuilder html = new StringBuilder("<table cellpadding='0' cellspacing='0'>");
        int step = hiddenCount + 1;
        for (HistoryEntry entry : entries) {
            html.append("<tr>")
                    .append("<td style='padding-right:8px;'>").append(step).append(".</td>")
                    .append("<td style='padding-right:12px;'>").append(escape(entry.timeText())).append("</td>")
                    .append("<td>").append(entryHtml(entry)).append("</td>")
                    .append("</tr>");
            step++;
        }
        return html.append("</table>").toString();
    }

    private String entryHtml(HistoryEntry entry) {
        String escaped = escape(entry.eventText());
        return entry.failure()
                ? "<span style='color:" + FAILURE_COLOR + "; background-color:" + FAILURE_BACKGROUND + ";'>"
                + escaped + "</span>"
                : escaped;
    }

    private List<HistoryEntry> historyEntries(List<StrategyOrder> orders, Function<Instant, String> timestampFormatter) {
        if (orders == null || orders.isEmpty()) {
            return List.of();
        }
        Map<StrategyStage, Instant> latestFilledByStage = orders.stream()
                .filter(order -> order != null && order.stage() != null && order.status() == StrategyOrderStatus.FILLED)
                .collect(Collectors.toMap(
                        StrategyOrder::stage,
                        order -> order.filledAt() == null ? order.updatedAt() : order.filledAt(),
                        (left, right) -> left.isAfter(right) ? left : right
                ));
        List<StrategyOrder> sortedOrders = orders.stream()
                .sorted(Comparator.comparing(StrategyOrder::submittedAt))
                .toList();
        Map<FailureGroupKey, FailureGroup> failureGroups = failureGroups(sortedOrders, latestFilledByStage);
        RunningAverageCost runningAverage = new RunningAverageCost(countBuyFills(sortedOrders));
        List<HistoryEntry> entries = new ArrayList<>();
        for (StrategyOrder order : sortedOrders) {
            if (isSupersededFailure(order, latestFilledByStage)) {
                continue;
            }
            FailureGroupKey failureKey = failureGroupKey(order);
            FailureGroup failureGroup = failureKey == null ? null : failureGroups.get(failureKey);
            if (failureGroup != null && failureGroup.count() > 1) {
                if (failureGroup.firstOrder() == order) {
                    entries.add(consolidatedFailureEntry(failureGroup, timestampFormatter));
                }
                continue;
            }
            entries.addAll(entriesFor(order, timestampFormatter, latestFilledByStage, runningAverage));
        }
        entries.sort(Comparator.comparing(HistoryEntry::when, Comparator.nullsLast(Comparator.naturalOrder())));
        return entries;
    }

    private int countBuyFills(List<StrategyOrder> sortedOrders) {
        return (int) sortedOrders.stream()
                .filter(order -> order.side() == StrategyOrderSide.BUY)
                .filter(order -> order.status() == StrategyOrderStatus.FILLED
                        || order.status() == StrategyOrderStatus.PARTIALLY_FILLED)
                .count();
    }

    private List<HistoryEntry> entriesFor(
            StrategyOrder order,
            Function<Instant, String> timestampFormatter,
            Map<StrategyStage, Instant> latestFilledByStage,
            RunningAverageCost runningAverage
    ) {
        if (order == null) {
            return List.of();
        }
        if (isSupersededFailure(order, latestFilledByStage)) {
            return List.of();
        }
        java.util.ArrayList<HistoryEntry> entries = new java.util.ArrayList<>();
        String label = stageLabel(order.stage(), order.side());
        entries.add(entry(order.submittedAt(), timestampFormatter,
                label + " placed" + orderPrice(order.limitPrice(), order.stopPrice(), order.requestedQuantity()),
                false));

        if (order.status() == StrategyOrderStatus.PARTIALLY_FILLED && positive(order.filledQuantity())) {
            entries.add(entry(order.updatedAt(), timestampFormatter,
                    label + " partially filled" + fillPrice(order) + averagedInSuffix(order, runningAverage),
                    false));
        } else if (order.status() == StrategyOrderStatus.FILLED) {
            String completion = order.side() == StrategyOrderSide.SELL ? "sold" : "filled";
            entries.add(entry(order.filledAt() == null ? order.updatedAt() : order.filledAt(), timestampFormatter,
                    label + " " + completion + fillPrice(order) + averagedInSuffix(order, runningAverage),
                    false));
        } else if (order.status() == StrategyOrderStatus.CANCELED
                || order.status() == StrategyOrderStatus.REJECTED
                || order.status() == StrategyOrderStatus.FAILED) {
            entries.add(entry(order.updatedAt(), timestampFormatter,
                    label + " " + order.status().name().toLowerCase().replace('_', ' '),
                    true));
        }
        return entries;
    }

    /**
     * After a buy fill, the blended cost the position is now averaged in at. Only shown once a
     * position has actually been averaged (more than one buy fill), since for a single entry the
     * fill price already is the average.
     */
    private String averagedInSuffix(StrategyOrder order, RunningAverageCost runningAverage) {
        BigDecimal average = runningAverage.apply(order);
        if (average == null) {
            return "";
        }
        return " - averaged in @ $" + average.toPlainString();
    }

    private HistoryEntry entry(Instant when, Function<Instant, String> timestampFormatter, String eventText, boolean failure) {
        return new HistoryEntry(when, format(when, timestampFormatter), eventText, failure);
    }

    private boolean isSupersededFailure(StrategyOrder order, Map<StrategyStage, Instant> latestFilledByStage) {
        if (order == null || order.stage() == null || latestFilledByStage == null || latestFilledByStage.isEmpty()) {
            return false;
        }
        if (order.status() != StrategyOrderStatus.CANCELED
                && order.status() != StrategyOrderStatus.REJECTED
                && order.status() != StrategyOrderStatus.FAILED) {
            return false;
        }
        Instant latestFillAt = latestFilledByStage.get(order.stage());
        if (latestFillAt == null) {
            return false;
        }
        Instant failedAt = order.updatedAt() == null ? order.submittedAt() : order.updatedAt();
        if (failedAt == null) {
            return true;
        }
        return !latestFillAt.isBefore(failedAt);
    }

    private Map<FailureGroupKey, FailureGroup> failureGroups(
            List<StrategyOrder> orders,
            Map<StrategyStage, Instant> latestFilledByStage
    ) {
        Map<FailureGroupKey, FailureGroup> groups = new LinkedHashMap<>();
        for (StrategyOrder order : orders) {
            if (isSupersededFailure(order, latestFilledByStage)) {
                continue;
            }
            FailureGroupKey key = failureGroupKey(order);
            if (key == null) {
                continue;
            }
            groups.compute(key, (ignored, existing) -> existing == null
                    ? new FailureGroup(order, order, 1)
                    : existing.with(order));
        }
        return groups;
    }

    private FailureGroupKey failureGroupKey(StrategyOrder order) {
        if (order == null
                || order.stage() == null
                || (order.status() != StrategyOrderStatus.CANCELED
                && order.status() != StrategyOrderStatus.REJECTED
                && order.status() != StrategyOrderStatus.FAILED)) {
            return null;
        }
        return new FailureGroupKey(
                order.stage(),
                order.side(),
                order.status(),
                normalized(order.limitPrice()),
                normalized(order.stopPrice()),
                normalized(order.requestedQuantity())
        );
    }

    private HistoryEntry consolidatedFailureEntry(FailureGroup group, Function<Instant, String> timestampFormatter) {
        StrategyOrder first = group.firstOrder();
        StrategyOrder last = group.lastOrder();
        String label = stageLabel(first.stage(), first.side());
        String status = first.status().name().toLowerCase().replace('_', ' ');
        Instant lastUpdated = last.updatedAt() == null ? last.submittedAt() : last.updatedAt();
        String timeText = format(first.submittedAt(), timestampFormatter);
        if (lastUpdated != null && !lastUpdated.equals(first.submittedAt())) {
            timeText += " → " + format(lastUpdated, timestampFormatter);
        }
        return new HistoryEntry(
                first.submittedAt(),
                timeText,
                label + " " + status + " x" + group.count()
                        + orderPrice(first.limitPrice(), first.stopPrice(), first.requestedQuantity()),
                true
        );
    }

    private String stageLabel(StrategyStage stage, StrategyOrderSide side) {
        if (stage == null) {
            return side == StrategyOrderSide.SELL ? "Sell rule" : "Buy rule";
        }
        return switch (stage) {
            case BASE_BUY -> "Base Buy";
            case BUY_LIMIT_1 -> "Buy Limit 1";
            case BUY_LIMIT_2 -> "Buy Limit 2";
            case STOP_LOSS -> "Stop Loss";
            case TARGET_SELL -> "Target Sell";
            case LOSS_EXIT -> "Loss Exit";
            case PROFIT_EXIT -> "Profit Exit";
            case MANUAL_BUY -> "Manual Buy";
            case MANUAL_EXIT -> "Manual Exit";
            case CLOSE_POSITION -> "Close Position";
        };
    }

    private String orderPrice(BigDecimal limitPrice, BigDecimal stopPrice, BigDecimal quantity) {
        BigDecimal price = positive(limitPrice) ? limitPrice : stopPrice;
        String priceText = positive(price) ? " @ $" + price.toPlainString() : "";
        String quantityText = positive(quantity) ? "/" + quantity.stripTrailingZeros().toPlainString() : "";
        return priceText + quantityText;
    }

    private String fillPrice(StrategyOrder order) {
        BigDecimal quantity = positive(order.filledQuantity()) ? order.filledQuantity() : order.requestedQuantity();
        BigDecimal price = positive(order.filledAveragePrice()) ? order.filledAveragePrice() : order.limitPrice();
        return orderPrice(price, BigDecimal.ZERO, quantity);
    }

    private String normalizeCurrentRule(String currentRuleText) {
        if (currentRuleText == null || currentRuleText.isBlank()) {
            return "-";
        }
        return currentRuleText.startsWith("Rules: ")
                ? currentRuleText.substring("Rules: ".length())
                : currentRuleText;
    }

    private String format(Instant instant, Function<Instant, String> timestampFormatter) {
        if (instant == null) {
            return "-";
        }
        return timestampFormatter == null ? instant.toString() : timestampFormatter.apply(instant);
    }

    private boolean positive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private BigDecimal normalized(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.stripTrailingZeros();
    }

    private String escape(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private String historyEntryHtml(String entry) {
        String escaped = escape(entry);
        return isFailureEntry(entry)
                ? "<span style='color:" + FAILURE_COLOR + "; background-color:" + FAILURE_BACKGROUND + ";'>"
                + escaped + "</span>"
                : escaped;
    }

    private boolean isFailureEntry(String entry) {
        if (entry == null) {
            return false;
        }
        String normalized = entry.toLowerCase();
        return normalized.contains(" failed") || normalized.contains(" rejected");
    }

    private record HistoryEntry(Instant when, String timeText, String eventText, boolean failure) {
    }

    /**
     * Walks buy fills in order and tracks the blended cost basis, so each averaging step can show
     * the price the position is averaged in at after that fill. A full exit resets the basis so a
     * re-entry starts a fresh cycle rather than blending across unrelated trades.
     */
    private static final class RunningAverageCost {
        private final int totalBuyFills;
        private BigDecimal totalCost = BigDecimal.ZERO;
        private BigDecimal totalQuantity = BigDecimal.ZERO;
        private int buyFillsApplied;

        private RunningAverageCost(int totalBuyFills) {
            this.totalBuyFills = totalBuyFills;
        }

        /** Returns the blended average after applying this order, or null when it isn't an averaging step. */
        private BigDecimal apply(StrategyOrder order) {
            BigDecimal quantity = order.filledQuantity() != null && order.filledQuantity().compareTo(BigDecimal.ZERO) > 0
                    ? order.filledQuantity()
                    : order.requestedQuantity();
            if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
                return null;
            }
            if (order.side() == StrategyOrderSide.SELL) {
                totalQuantity = totalQuantity.subtract(quantity);
                if (totalQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                    totalQuantity = BigDecimal.ZERO;
                    totalCost = BigDecimal.ZERO;
                    buyFillsApplied = 0;
                }
                return null;
            }
            BigDecimal price = order.filledAveragePrice() != null
                    && order.filledAveragePrice().compareTo(BigDecimal.ZERO) > 0
                    ? order.filledAveragePrice()
                    : order.limitPrice();
            if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
                return null;
            }
            totalCost = totalCost.add(price.multiply(quantity));
            totalQuantity = totalQuantity.add(quantity);
            buyFillsApplied++;
            // A single entry's fill price already is the average; only show it once averaged.
            if (buyFillsApplied < 2 || totalBuyFills < 2 || totalQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                return null;
            }
            return Monetary.round(totalCost.divide(totalQuantity, 6, RoundingMode.HALF_UP));
        }
    }

    private record FailureGroupKey(
            StrategyStage stage,
            StrategyOrderSide side,
            StrategyOrderStatus status,
            BigDecimal limitPrice,
            BigDecimal stopPrice,
            BigDecimal requestedQuantity
    ) {
    }

    private record FailureGroup(StrategyOrder firstOrder, StrategyOrder lastOrder, int count) {
        FailureGroup with(StrategyOrder order) {
            return new FailureGroup(firstOrder, order, count + 1);
        }
    }
}
