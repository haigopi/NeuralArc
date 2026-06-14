package com.neuralarc.util;

import com.neuralarc.model.StrategyMode;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientOrderIdTest {
    @Test
    void buildProducesStructuredIdWithStage() {
        String id = ClientOrderId.build(StrategyMode.PAPER, "ORB", "NVDA", "BASE_BUY", Instant.parse("2026-06-13T10:30:15Z"));
        assertTrue(id.startsWith("NA_PAPER_ORB_NVDA_BASE_BUY_20260613103015_"), id);
        assertEquals(4, id.substring(id.lastIndexOf('_') + 1).length());
    }

    @Test
    void parseRoundTripsMultiWordStage() {
        String id = ClientOrderId.build(StrategyMode.LIVE, "VWAP", "TSLA", "TARGET_SELL", Instant.parse("2026-06-13T11:05:22Z"));
        ClientOrderId.Parsed parsed = ClientOrderId.parse(id).orElseThrow();
        assertEquals(StrategyMode.LIVE, parsed.mode());
        assertEquals("VWAP", parsed.strategyCode());
        assertEquals("TSLA", parsed.symbol());
        assertEquals("TARGET_SELL", parsed.stage());
        assertEquals("20260613110522", parsed.timestamp());
    }

    @Test
    void sanitizesSegmentsAndDefaultsMode() {
        String id = ClientOrderId.build(null, "vwap desk!", "ts-la", "manual_buy!", Instant.parse("2026-01-01T00:00:00Z"));
        ClientOrderId.Parsed parsed = ClientOrderId.parse(id).orElseThrow();
        assertEquals(StrategyMode.PAPER, parsed.mode()); // null mode -> PAPER
        assertEquals("VWAPDESK", parsed.strategyCode());
        assertEquals("TSLA", parsed.symbol());
        assertEquals("MANUAL_BUY", parsed.stage()); // special chars stripped, underscore kept
    }

    @Test
    void unassignedStrategyUsesAllCode() {
        String id = ClientOrderId.build(StrategyMode.PAPER, null, "AAPL", "BASE_BUY");
        assertTrue(id.startsWith("NA_PAPER_ALL_AAPL_BASE_BUY_"), id);
    }

    @Test
    void gapRocketOrderIdIncludesStrategyAndMode() {
        String id = ClientOrderId.build(StrategyMode.PAPER, "GAPROCKET", "NVDA", "MANUAL_BUY", Instant.parse("2026-06-13T09:45:15Z"));
        assertTrue(id.startsWith("NA_PAPER_GAPROCKET_NVDA_MANUAL_BUY_20260613094515_"), id);
    }

    @Test
    void parseRejectsLegacyAndForeignIds() {
        assertTrue(ClientOrderId.parse("neuralarc-s1-BASE_BUY-1718").isEmpty());
        assertTrue(ClientOrderId.parse("NA_NOTAMODE_ORB_NVDA_BASE_BUY_20260613103015_A1B2").isEmpty());
        assertTrue(ClientOrderId.parse("NA_PAPER_ORB").isEmpty()); // too short
        assertTrue(ClientOrderId.parse(null).isEmpty());
    }
}
