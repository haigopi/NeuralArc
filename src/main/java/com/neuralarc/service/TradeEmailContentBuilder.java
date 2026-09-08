package com.neuralarc.service;

import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyOrder;
import com.neuralarc.model.StrategyOrderSide;
import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class TradeEmailContentBuilder {
    private static final int HISTORY_LIMIT = 25;
    /** Shared subject prefix so live NeuralArc mail sorts and filters together in an inbox. */
    private static final String SUBJECT_PREFIX = "NeuralArc: Live - ";
    private static final String NEUTRAL_HEADER_BACKGROUND = "#111827";
    private static final String PROFIT_HEADER_BACKGROUND = "#047857";
    private static final String LOSS_HEADER_BACKGROUND = "#B91C1C";
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault());

    String buySubject(Strategy strategy) {
        return SUBJECT_PREFIX + "Buy order placed: " + strategy.symbol();
    }

    /**
     * Says on the subject line what the fill actually did: which exit stage closed the trade and
     * whether it booked a profit or a loss. "sell order executed: INTC" alone left that unanswered
     * until the mail was opened.
     */
    String sellSubject(Strategy strategy, StrategyOrder order, TradeEmailNotificationContext context) {
        return SUBJECT_PREFIX + "Sell order executed: " + strategy.symbol()
                + " - " + stageLabel(order)
                + " - " + outcomeLabel(sellRealizedPnl(order, context));
    }

    /**
     * What this fill booked, not the strategy's running total: an exit that took a profit must read
     * as a profit even when earlier exits on the same strategy lost money.
     */
    private BigDecimal sellRealizedPnl(StrategyOrder order, TradeEmailNotificationContext context) {
        return StrategyOrderAccounting.realizedPnlForSellOrder(safe(context, order).orderHistory(), order);
    }

    /** PROFIT above zero, LOSS below, FLAT at exactly zero. */
    private static Outcome outcome(BigDecimal netPnl) {
        BigDecimal rounded = Monetary.round(netPnl);
        if (rounded.compareTo(BigDecimal.ZERO) > 0) {
            return Outcome.PROFIT;
        }
        return rounded.compareTo(BigDecimal.ZERO) < 0 ? Outcome.LOSS : Outcome.FLAT;
    }

    private String outcomeLabel(BigDecimal netPnl) {
        return switch (outcome(netPnl)) {
            case PROFIT -> "Profit " + money(netPnl);
            case LOSS -> "Loss " + money(netPnl);
            case FLAT -> "Flat " + money(netPnl);
        };
    }

    private String stageLabel(StrategyOrder order) {
        if (order == null || order.stage() == null) {
            return "Sell";
        }
        String[] parts = order.stage().name().toLowerCase(Locale.ROOT).split("_");
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
        return builder.isEmpty() ? "Sell" : builder.toString();
    }

    private enum Outcome {
        PROFIT,
        LOSS,
        FLAT
    }

    String buyText(Strategy strategy, StrategyOrder order, TradeEmailNotificationContext context) {
        return text("Buy order placed: " + strategy.symbol() + " - waiting for fill.", strategy, order, context);
    }

    String sellText(Strategy strategy, StrategyOrder order, TradeEmailNotificationContext context) {
        TradeEmailNotificationContext safeContext = safe(context, order);
        return text("Sell order executed: " + strategy.symbol() + " - " + stageLabel(order)
                        + " - " + outcomeLabel(sellRealizedPnl(order, safeContext)) + ".",
                strategy, order, safeContext);
    }

    String buyHtml(Strategy strategy, StrategyOrder order, TradeEmailNotificationContext context) {
        return html("Buy order placed: " + strategy.symbol(), "Waiting for fill.",
                NEUTRAL_HEADER_BACKGROUND, strategy, order, context);
    }

    String sellHtml(Strategy strategy, StrategyOrder order, TradeEmailNotificationContext context) {
        TradeEmailNotificationContext safeContext = safe(context, order);
        BigDecimal netPnl = sellRealizedPnl(order, safeContext);
        Outcome outcome = outcome(netPnl);
        String heading = "Sell order executed: " + strategy.symbol() + " - " + stageLabel(order);
        String subtitle = switch (outcome) {
            case PROFIT -> "This exit booked a profit of " + money(netPnl) + ".";
            case LOSS -> "This exit booked a loss of " + money(netPnl) + ".";
            case FLAT -> "This exit closed flat.";
        };
        String background = switch (outcome) {
            case PROFIT -> PROFIT_HEADER_BACKGROUND;
            case LOSS -> LOSS_HEADER_BACKGROUND;
            case FLAT -> NEUTRAL_HEADER_BACKGROUND;
        };
        return html(heading, subtitle, background, strategy, order, safeContext);
    }

    private String text(String heading, Strategy strategy, StrategyOrder order, TradeEmailNotificationContext context) {
        TradeEmailNotificationContext safeContext = safe(context, order);
        return heading + "\n\n"
                + "Order\n"
                + "Symbol: " + strategy.symbol() + "\n"
                + "Strategy: " + strategy.name() + "\n"
                + "Stage: " + order.stage() + "\n"
                + "Side: " + order.side() + "\n"
                + "Status: " + order.status() + "\n"
                + "Requested quantity: " + order.requestedQuantity() + "\n"
                + "Filled quantity: " + order.filledQuantity() + "\n"
                + "Limit price: " + money(order.limitPrice()) + "\n"
                + "Average fill price: " + money(order.filledAveragePrice()) + "\n\n"
                + "Workspace\n"
                + "Name: " + safeContext.workspaceName() + "\n"
                + "Code: " + safeContext.workspaceCode() + "\n"
                + "Strategy net P&L: " + money(safeContext.strategyNetPnl()) + "\n"
                + "Workspace net P&L: " + money(safeContext.workspaceNetPnl()) + "\n\n"
                + "Order history included in the HTML email table.";
    }

    private String html(
            String title,
            String subtitle,
            String headerBackground,
            Strategy strategy,
            StrategyOrder order,
            TradeEmailNotificationContext context
    ) {
        TradeEmailNotificationContext safeContext = safe(context, order);
        return "<!doctype html><html><body style=\"margin:0;background:#f4f7fb;color:#172033;font-family:Arial,sans-serif;\">"
                + "<div style=\"max-width:760px;margin:0 auto;padding:24px;\">"
                + "<div style=\"background:" + headerBackground + ";color:#ffffff;border-radius:16px 16px 0 0;padding:22px 24px;\">"
                + "<div style=\"font-size:12px;letter-spacing:1.4px;text-transform:uppercase;color:#E5E7EB;\">NeuralArc &middot; Live</div>"
                + "<h1 style=\"margin:8px 0 4px;font-size:24px;line-height:1.25;\">" + escape(title) + "</h1>"
                + "<div style=\"font-size:14px;color:#F3F4F6;\">" + escape(subtitle) + "</div>"
                + "</div>"
                + "<div style=\"background:#ffffff;border:1px solid #E5E7EB;border-top:0;border-radius:0 0 16px 16px;padding:22px 24px;\">"
                + summaryCards(strategy, order, safeContext)
                + section("Order Details", detailsTable(orderDetails(strategy, order)))
                + section("Workspace Details", detailsTable(workspaceDetails(safeContext)))
                + section("Order History", historyTable(safeContext.orderHistory()))
                + "<p style=\"margin:18px 0 0;color:#6B7280;font-size:12px;line-height:1.5;\">"
                + "P&amp;L values are realized net P&amp;L reconstructed from persisted NeuralArc order fills. "
                + "They do not include taxes, fees, or open unrealized broker movement.</p>"
                + "</div></div></body></html>";
    }

    private String summaryCards(Strategy strategy, StrategyOrder order, TradeEmailNotificationContext context) {
        boolean sell = order != null && order.side() == StrategyOrderSide.SELL;
        return "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"width:100%;border-collapse:collapse;margin-bottom:18px;\">"
                + "<tr>"
                + summaryCard("Symbol", strategy.symbol())
                + summaryCard("Stage", stageLabel(order))
                + (sell
                    ? summaryCard("This Exit P&L", money(sellRealizedPnl(order, context)))
                    : summaryCard("Strategy Net P&L", money(context.strategyNetPnl())))
                + summaryCard("Workspace Net P&L", money(context.workspaceNetPnl()))
                + "</tr></table>";
    }

    private String summaryCard(String label, String value) {
        return "<td style=\"width:25%;padding:0 8px 8px 0;vertical-align:top;\">"
                + "<div style=\"border:1px solid #E5E7EB;border-radius:12px;padding:12px;background:#F9FAFB;\">"
                + "<div style=\"font-size:11px;color:#6B7280;text-transform:uppercase;letter-spacing:.7px;\">" + escape(label) + "</div>"
                + "<div style=\"margin-top:6px;font-size:16px;font-weight:700;color:#111827;\">" + escape(value) + "</div>"
                + "</div></td>";
    }

    private String section(String title, String content) {
        return "<h2 style=\"font-size:16px;margin:20px 0 10px;color:#111827;\">" + escape(title) + "</h2>" + content;
    }

    private String[][] orderDetails(Strategy strategy, StrategyOrder order) {
        return new String[][] {
                {"Strategy", strategy.name()},
                {"Mode", String.valueOf(strategy.mode())},
                {"Order side", String.valueOf(order.side())},
                {"Order type", String.valueOf(order.orderType())},
                {"Status", String.valueOf(order.status())},
                {"Requested quantity", String.valueOf(order.requestedQuantity())},
                {"Filled quantity", String.valueOf(order.filledQuantity())},
                {"Limit price", money(order.limitPrice())},
                {"Average fill price", money(order.filledAveragePrice())},
                {"Submitted at", instant(order.submittedAt())},
                {"Filled at", instant(order.filledAt())},
                {"Alpaca order ID", safe(order.alpacaOrderId())},
                {"Client order ID", safe(order.clientOrderId())}
        };
    }

    private String[][] workspaceDetails(TradeEmailNotificationContext context) {
        return new String[][] {
                {"Workspace", context.workspaceName()},
                {"Workspace code", context.workspaceCode()},
                {"Strategy realized net P&L", money(context.strategyNetPnl())},
                {"Workspace realized net P&L", money(context.workspaceNetPnl())}
        };
    }

    private String detailsTable(String[][] rows) {
        StringBuilder builder = new StringBuilder("<table cellpadding=\"0\" cellspacing=\"0\" style=\"width:100%;border-collapse:collapse;border:1px solid #E5E7EB;\">");
        for (String[] row : rows) {
            builder.append("<tr>")
                    .append("<th style=\"width:32%;text-align:left;padding:10px 12px;background:#F9FAFB;border-bottom:1px solid #E5E7EB;color:#374151;font-size:13px;\">")
                    .append(escape(row[0]))
                    .append("</th><td style=\"padding:10px 12px;border-bottom:1px solid #E5E7EB;color:#111827;font-size:13px;\">")
                    .append(escape(row[1]))
                    .append("</td></tr>");
        }
        return builder.append("</table>").toString();
    }

    private String historyTable(List<StrategyOrder> orders) {
        List<StrategyOrder> history = orders == null ? List.of() : orders.stream()
                .sorted(Comparator
                        .comparing(StrategyOrder::submittedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(StrategyOrder::updatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(HISTORY_LIMIT)
                .toList();
        if (history.isEmpty()) {
            return "<p style=\"color:#6B7280;font-size:13px;\">No persisted order history is available.</p>";
        }
        StringBuilder builder = new StringBuilder("<table cellpadding=\"0\" cellspacing=\"0\" style=\"width:100%;border-collapse:collapse;border:1px solid #E5E7EB;font-size:12px;\">")
                .append("<tr style=\"background:#F9FAFB;color:#374151;\">")
                .append(header("Submitted")).append(header("Stage")).append(header("Side")).append(header("Status"))
                .append(header("Req Qty")).append(header("Fill Qty")).append(header("Limit")).append(header("Avg Fill"))
                .append("</tr>");
        for (StrategyOrder order : history) {
            builder.append("<tr>")
                    .append(cell(instant(order.submittedAt())))
                    .append(cell(String.valueOf(order.stage())))
                    .append(cell(String.valueOf(order.side())))
                    .append(cell(String.valueOf(order.status())))
                    .append(cell(String.valueOf(order.requestedQuantity())))
                    .append(cell(String.valueOf(order.filledQuantity())))
                    .append(cell(money(order.limitPrice())))
                    .append(cell(money(order.filledAveragePrice())))
                    .append("</tr>");
        }
        return builder.append("</table>").toString();
    }

    private String header(String value) {
        return "<th style=\"text-align:left;padding:9px 8px;border-bottom:1px solid #E5E7EB;\">" + escape(value) + "</th>";
    }

    private String cell(String value) {
        return "<td style=\"padding:9px 8px;border-bottom:1px solid #E5E7EB;color:#111827;\">" + escape(value) + "</td>";
    }

    private TradeEmailNotificationContext safe(TradeEmailNotificationContext context, StrategyOrder order) {
        return context == null ? TradeEmailNotificationContext.singleOrder(order) : context;
    }

    private String money(BigDecimal value) {
        return Monetary.round(value).toPlainString();
    }

    private String instant(Instant value) {
        return value == null ? "-" : DATE_TIME.format(value);
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String escape(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
