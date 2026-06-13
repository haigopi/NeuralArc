package com.neuralarc.util;

import com.neuralarc.model.StrategyMode;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientOrderIdTest {
    @Test
    void buildProducesStructuredId() {
        String id = ClientOrderId.build(StrategyMode.PAPER, "ORB", "NVDA", Instant.parse("2026-06-13T10:30:15Z"));
        assertTrue(id.startsWith("NA_PAPER_ORB_NVDA_20260613103015_"), id);
        String[] parts = id.split("_");
        assertEquals(6, parts.length);
        assertEquals(4, parts[5].length());
    }

    @Test
    void parseRoundTripsBuiltId() {
        String id = ClientOrderId.build(StrategyMode.LIVE, "VWAP", "TSLA", Instant.parse("2026-06-13T11:05:22Z"));
        ClientOrderId.Parsed parsed = ClientOrderId.parse(id).orElseThrow();
        assertEquals(StrategyMode.LIVE, parsed.mode());
        assertEquals("VWAP", parsed.strategyCode());
        assertEquals("TSLA", parsed.symbol());
        assertEquals("20260613110522", parsed.timestamp());
    }

    @Test
    void sanitizesSegmentsAndDefaultsMode() {
        String id = ClientOrderId.build(null, "vwap desk!", "ts-la", Instant.parse("2026-01-01T00:00:00Z"));
        ClientOrderId.Parsed parsed = ClientOrderId.parse(id).orElseThrow();
        assertEquals(StrategyMode.PAPER, parsed.mode()); // null mode -> PAPER
        assertEquals("VWAPDESK", parsed.strategyCode());
        assertEquals("TSLA", parsed.symbol());
    }

    @Test
    void unassignedStrategyUsesAllCode() {
        String id = ClientOrderId.build(StrategyMode.PAPER, null, "AAPL");
        assertTrue(id.startsWith("NA_PAPER_ALL_AAPL_"), id);
    }

    @Test
    void parseRejectsLegacyAndForeignIds() {
        assertTrue(ClientOrderId.parse("neuralarc-s1-BASE_BUY-1718").isEmpty());
        assertTrue(ClientOrderId.parse("NA_NOTAMODE_ORB_NVDA_20260613103015_A1B2").isEmpty());
        assertTrue(ClientOrderId.parse("random-string").isEmpty());
        assertTrue(ClientOrderId.parse(null).isEmpty());
    }
}
