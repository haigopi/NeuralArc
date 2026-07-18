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

final class TradeEmailContentBuilder {
    private static final int HISTORY_LIMIT = 25;
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault());

    String buySubject(Strategy strategy) {
        return "NeuralArc live buy order placed: " + strategy.symbol();
    }

    String sellSubject(Strategy strategy) {
        return "NeuralArc live sell order executed: " + strategy.symbol();
    }

    String buyText(Strategy strategy, StrategyOrder order, TradeEmailNotificationContext context) {
        return text("Live buy order placed and waiting for fill.", strategy, order, context);
    }

    String sellText(Strategy strategy, StrategyOrder order, TradeEmailNotificationContext context) {
        return text("Live sell order executed.", strategy, order, context);
    }

    String buyHtml(Strategy strategy, StrategyOrder order, TradeEmailNotificationContext context) {
        return html("Live buy order placed", "The order is submitted and waiting for fill.", strategy, order, context);
    }

    String sellHtml(Strategy strategy, StrategyOrder order, TradeEmailNotificationContext context) {
        return html("Live sell order executed", "The order filled on the live account.", strategy, order, context);
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
            Strategy strategy,
            StrategyOrder order,
            TradeEmailNotificationContext context
    ) {
        TradeEmailNotificationContext safeContext = safe(context, order);
        return "<!doctype html><html><body style=\"margin:0;background:#f4f7fb;color:#172033;font-family:Arial,sans-serif;\">"
                + "<div style=\"max-width:760px;margin:0 auto;padding:24px;\">"
                + "<div style=\"background:#111827;color:#ffffff;border-radius:16px 16px 0 0;padding:22px 24px;\">"
                + "<div style=\"font-size:12px;letter-spacing:1.4px;text-transform:uppercase;color:#9CA3AF;\">NeuralArc Live Order</div>"
                + "<h1 style=\"margin:8px 0 4px;font-size:24px;line-height:1.25;\">" + escape(title) + "</h1>"
                + "<div style=\"font-size:14px;color:#D1D5DB;\">" + escape(subtitle) + "</div>"
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
        return "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"width:100%;border-collapse:collapse;margin-bottom:18px;\">"
                + "<tr>"
                + summaryCard("Symbol", strategy.symbol())
                + summaryCard("Stage", String.valueOf(order.stage()))
                + summaryCard("Strategy Net P&L", money(context.strategyNetPnl()))
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
