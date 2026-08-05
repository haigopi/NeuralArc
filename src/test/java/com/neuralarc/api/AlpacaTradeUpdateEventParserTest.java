package com.neuralarc.api;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlpacaTradeUpdateEventParserTest {
    @Test
    void parsesTradeUpdatesFromWrappedStreamPayload() {
        String payload = """
                {"stream":"trade_updates","data":{"event":"fill","order":{"id":"ord-1","client_order_id":"client-1","symbol":"NVDA","side":"sell","type":"limit","limit_price":"221.10","filled_qty":"1","filled_avg_price":"221.10","status":"filled"}}}
                """;

        List<AlpacaTradeUpdateEvent> events = AlpacaTradeUpdateEventParser.parseAll(payload);

        assertEquals(1, events.size());
        AlpacaOrderData order = events.getFirst().orderData();
        assertEquals("fill", events.getFirst().eventType());
        assertEquals("ord-1", order.orderId());
        assertEquals("client-1", order.clientOrderId());
        assertEquals("NVDA", order.symbol());
        assertEquals("sell", order.side());
        assertEquals("filled", order.status());
        assertEquals(new BigDecimal("221.10"), order.filledAveragePrice());
    }

    @Test
    void ignoresControlPayloadsWithoutThrowing() {
        List<AlpacaTradeUpdateEvent> events = AlpacaTradeUpdateEventParser.parseAll(
                "{\"stream\":\"listening\",\"data\":{\"streams\":[\"trade_updates\"]}}"
        );

        assertTrue(events.isEmpty());
    }
}
