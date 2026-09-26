package com.neuralarc.util;

import com.neuralarc.model.StrategyMode;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientOrderIdIdempotencyTest {
    private static final Instant WHEN = Instant.parse("2026-09-25T13:27:15Z");

    @Test
    void thesameOrderSentTwiceCarriesTheSameIdSoTheBrokerRefusesTheSecond() {
        String first = ClientOrderId.deterministic(StrategyMode.LIVE, "PLAY", "EDRY", "BASE_BUY", "strategy-1", 0, WHEN);
        String second = ClientOrderId.deterministic(StrategyMode.LIVE, "PLAY", "EDRY", "BASE_BUY", "strategy-1", 0,
                WHEN.plusSeconds(3600));

        assertEquals(first, second, "a second instance, or a restart, must not produce a fresh id within the day");
    }

    @Test
    void adeliberateReplacementGetsAFreshId() {
        String first = ClientOrderId.deterministic(StrategyMode.LIVE, "PLAY", "EDRY", "BASE_BUY", "strategy-1", 0, WHEN);
        String reposted = ClientOrderId.deterministic(StrategyMode.LIVE, "PLAY", "EDRY", "BASE_BUY", "strategy-1", 1, WHEN);

        assertNotEquals(first, reposted, "re-posting an expired entry is a new order, not a duplicate");
    }

    @Test
    void differentStrategiesStagesAndDaysNeverCollide() {
        String base = ClientOrderId.deterministic(StrategyMode.LIVE, "PLAY", "EDRY", "BASE_BUY", "strategy-1", 0, WHEN);

        assertNotEquals(base, ClientOrderId.deterministic(StrategyMode.LIVE, "PLAY", "EDRY", "BASE_BUY", "strategy-2", 0, WHEN));
        assertNotEquals(base, ClientOrderId.deterministic(StrategyMode.LIVE, "PLAY", "EDRY", "TARGET_SELL", "strategy-1", 0, WHEN));
        assertNotEquals(base, ClientOrderId.deterministic(StrategyMode.LIVE, "PLAY", "EDRY", "BASE_BUY", "strategy-1", 0,
                WHEN.plusSeconds(86_400)), "tomorrow's attempt is a new order");
    }

    @Test
    void theIdStillParsesAsANeuralArcOrderSoReconciliationKeepsWorking() {
        String id = ClientOrderId.deterministic(StrategyMode.PAPER, "COMEBACK", "NIO", "BASE_BUY", "s1", 2, WHEN);

        ClientOrderId.Parsed parsed = ClientOrderId.parse(id).orElseThrow();
        assertEquals(StrategyMode.PAPER, parsed.mode());
        assertEquals("COMEBACK", parsed.strategyCode());
        assertEquals("NIO", parsed.symbol());
        assertEquals("BASE_BUY", parsed.stage());
        assertTrue(id.length() <= 128, "Alpaca caps client_order_id at 128 characters");
    }
}
