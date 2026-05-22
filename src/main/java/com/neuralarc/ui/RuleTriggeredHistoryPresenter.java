package com.neuralarc.ui;

import com.neuralarc.model.StrategyOrder;
import com.neuralarc.model.StrategyOrderSide;
import com.neuralarc.model.StrategyOrderStatus;
import com.neuralarc.model.StrategyStage;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

final class RuleTriggeredHistoryPresenter {
    String buildLabel(String currentRuleText, List<StrategyOrder> orders, Function<Instant, String> timestampFormatter) {
        String current = normalizeCurrentRule(currentRuleText);
        List<String> history = historyEntries(orders, timestampFormatter);
        if (history.isEmpty()) {
            return currentRuleText == null || currentRuleText.isBlank() ? "Rules: -" : currentRuleText;
        }
        return "<html><div style='width:1120px;'>"
                + "<b>Current:</b> " + escape(current)
                + "<br><b>Past:</b> " + escape(String.join(" | ", history))
                + "</div></html>";
    }

    String buildTooltip(String currentRuleHtml, List<StrategyOrder> orders, Function<Instant, String> timestampFormatter) {
        List<String> history = historyEntries(orders, timestampFormatter);
        if (history.isEmpty()) {
            return currentRuleHtml;
        }
        return currentRuleHtml
                + "<br><br><b>Triggered Rule History:</b><br>"
                + escape(String.join("<br>", history)).replace("&lt;br&gt;", "<br>");
    }

    private List<String> historyEntries(List<StrategyOrder> orders, Function<Instant, String> timestampFormatter) {
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
        List<String> entries = new ArrayList<>();
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
            entries.addAll(entriesFor(order, timestampFormatter, latestFilledByStage));
        }
        return entries;
    }

    private List<String> entriesFor(
            StrategyOrder order,
            Function<Instant, String> timestampFormatter,
            Map<StrategyStage, Instant> latestFilledByStage
    ) {
        if (order == null) {
            return List.of();
        }
        if (isSupersededFailure(order, latestFilledByStage)) {
            return List.of();
        }
        java.util.ArrayList<String> entries = new java.util.ArrayList<>();
        String label = stageLabel(order.stage(), order.side());
        entries.add(label + " placed" + orderPrice(order.limitPrice(), order.stopPrice(), order.requestedQuantity())
                + " on " + format(order.submittedAt(), timestampFormatter));

        if (order.status() == StrategyOrderStatus.PARTIALLY_FILLED && positive(order.filledQuantity())) {
            entries.add(label + " partially filled" + fillPrice(order)
                    + " on " + format(order.updatedAt(), timestampFormatter));
        } else if (order.status() == StrategyOrderStatus.FILLED) {
            String completion = order.side() == StrategyOrderSide.SELL ? "sold" : "filled";
            entries.add(label + " " + completion + fillPrice(order)
                    + " on " + format(order.filledAt() == null ? order.updatedAt() : order.filledAt(), timestampFormatter));
        } else if (order.status() == StrategyOrderStatus.CANCELED
                || order.status() == StrategyOrderStatus.REJECTED
                || order.status() == StrategyOrderStatus.FAILED) {
            entries.add(label + " " + order.status().name().toLowerCase().replace('_', ' ')
                    + " on " + format(order.updatedAt(), timestampFormatter));
        }
        return entries;
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

    private String consolidatedFailureEntry(FailureGroup group, Function<Instant, String> timestampFormatter) {
        StrategyOrder first = group.firstOrder();
        StrategyOrder last = group.lastOrder();
        String label = stageLabel(first.stage(), first.side());
        String status = first.status().name().toLowerCase().replace('_', ' ');
        String range = format(first.submittedAt(), timestampFormatter);
        Instant lastUpdated = last.updatedAt() == null ? last.submittedAt() : last.updatedAt();
        if (lastUpdated != null && !lastUpdated.equals(first.submittedAt())) {
            range += " to " + format(lastUpdated, timestampFormatter);
        }
        return label + " " + status + " x" + group.count()
                + orderPrice(first.limitPrice(), first.stopPrice(), first.requestedQuantity())
                + " from " + range;
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
