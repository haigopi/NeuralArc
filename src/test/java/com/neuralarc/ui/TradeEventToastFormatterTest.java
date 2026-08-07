package com.neuralarc.ui;

import com.neuralarc.api.AlpacaOrderData;
import com.neuralarc.api.AlpacaTradeUpdateEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeEventToastFormatterTest {
    private AlpacaTradeUpdateEvent event(String eventType, String side, String status,
                                         String filledQuantity, String filledAveragePrice) {
        AlpacaOrderData order = new AlpacaOrderData(
                "ord-1", "client-1", "AAPL", side, "limit",
                new BigDecimal("180.00"),
                filledAveragePrice == null ? BigDecimal.ZERO : new BigDecimal(filledAveragePrice),
                filledQuantity == null ? BigDecimal.ZERO : new BigDecimal(filledQuantity),
                status, "{}");
        return new AlpacaTradeUpdateEvent(eventType, order);
    }

    @Test
    void fillShowsSymbolSideQuantityAndPrice() {
        Optional<TradeEventToastFormatter.ToastMessage> message =
                TradeEventToastFormatter.format(event("fill", "buy", "filled", "10", "182.45"));

        assertTrue(message.isPresent());
        assertEquals("AAPL BUY filled 10 @ $182.45", message.get().text());
        assertEquals(TradeEventToastFormatter.Severity.SUCCESS, message.get().severity());
    }

    @Test
    void partialFillIsInformationalRatherThanSuccess() {
        Optional<TradeEventToastFormatter.ToastMessage> message =
                TradeEventToastFormatter.format(event("partial_fill", "buy", "partially_filled", "4", "182.45"));

        assertTrue(message.isPresent());
        assertTrue(message.get().text().contains("partially filled 4 @ $182.45"), message.get().text());
        assertEquals(TradeEventToastFormatter.Severity.INFO, message.get().severity());
    }

    @Test
    void rejectionAndExpiryAreWarningsBecauseTheyNeedAttention() {
        assertEquals(TradeEventToastFormatter.Severity.WARNING,
                TradeEventToastFormatter.format(event("rejected", "buy", "rejected", null, null))
                        .orElseThrow().severity());
        assertEquals(TradeEventToastFormatter.Severity.WARNING,
                TradeEventToastFormatter.format(event("expired", "buy", "expired", null, null))
                        .orElseThrow().severity());
    }

    @Test
    void routineAcknowledgementsAreNotToasted() {
        // These arrive for every working order; toasting them would bury the meaningful events.
        for (String eventType : new String[]{"new", "accepted", "pending_new", "calculated", "order_replace_rejected"}) {
            assertTrue(TradeEventToastFormatter.format(event(eventType, "buy", eventType, null, null)).isEmpty(),
                    eventType + " must not raise a toast");
        }
    }

    @Test
    void unknownOrMissingEventDataIsIgnoredRatherThanShowingAnEmptyToast() {
        assertTrue(TradeEventToastFormatter.format(null).isEmpty());
        assertTrue(TradeEventToastFormatter.format(new AlpacaTradeUpdateEvent("fill", null)).isEmpty());
    }

    @Test
    void fillWithoutPriceDataStillReportsTheEvent() {
        Optional<TradeEventToastFormatter.ToastMessage> message =
                TradeEventToastFormatter.format(event("fill", "sell", "filled", null, null));

        assertTrue(message.isPresent());
        assertEquals("AAPL SELL filled", message.get().text());
    }

    @Test
    void eventTypeCasingAndHyphensAreNormalized() {
        assertTrue(TradeEventToastFormatter.format(event("PARTIAL-FILL", "buy", "partially_filled", "2", "10.00"))
                .isPresent());
    }
}
