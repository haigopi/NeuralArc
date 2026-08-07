package com.neuralarc.ui;

import com.neuralarc.api.AlpacaOrderData;
import com.neuralarc.api.AlpacaTradeUpdateEvent;
import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;

/**
 * Turns a streaming trade update into a short operator-facing toast.
 *
 * <p>Only events that move money or need attention are surfaced. Routine acknowledgements
 * ({@code new}, {@code accepted}, {@code pending_new}) arrive constantly for every working order
 * and would bury the meaningful ones — those stay in the event log.
 */
final class TradeEventToastFormatter {
    enum Severity { SUCCESS, INFO, WARNING }

    record ToastMessage(String text, Severity severity) {
    }

    private TradeEventToastFormatter() {
    }

    static Optional<ToastMessage> format(AlpacaTradeUpdateEvent event) {
        if (event == null || event.orderData() == null) {
            return Optional.empty();
        }
        AlpacaOrderData order = event.orderData();
        String eventType = normalize(event.eventType());
        String symbol = order.symbol() == null || order.symbol().isBlank() ? "?" : order.symbol().toUpperCase(Locale.ROOT);
        String side = order.side() == null || order.side().isBlank() ? "" : order.side().toUpperCase(Locale.ROOT) + " ";

        return switch (eventType) {
            case "fill" -> Optional.of(new ToastMessage(
                    symbol + " " + side + "filled" + quantityAndPrice(order), Severity.SUCCESS));
            case "partial_fill" -> Optional.of(new ToastMessage(
                    symbol + " " + side + "partially filled" + quantityAndPrice(order), Severity.INFO));
            case "canceled" -> Optional.of(new ToastMessage(
                    symbol + " " + side + "order canceled", Severity.INFO));
            case "expired" -> Optional.of(new ToastMessage(
                    symbol + " " + side + "order expired", Severity.WARNING));
            case "rejected" -> Optional.of(new ToastMessage(
                    symbol + " " + side + "order rejected by broker", Severity.WARNING));
            default -> Optional.empty();
        };
    }

    private static String quantityAndPrice(AlpacaOrderData order) {
        String quantity = positive(order.filledQuantity())
                ? " " + order.filledQuantity().stripTrailingZeros().toPlainString()
                : "";
        String price = positive(order.filledAveragePrice())
                ? " @ $" + Monetary.round(order.filledAveragePrice()).toPlainString()
                : "";
        return quantity + price;
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }
}
