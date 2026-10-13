package com.neuralarc.model;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class StrategyOrderMetadataTest {
    private static final String STAMPED =
            "{\"id\":\"x\",\"neuralarcExitSource\":\"PORTFOLIO_CAPTURE\",\"neuralarcSubmissionType\":\"MARKET\"}";

    @Test
    void aBrokerRefreshKeepsWhatSoldThePositionAndHow() {
        StrategyOrder order = order(STAMPED);

        order.setRawResponseJson("{\"id\":\"x\",\"status\":\"filled\",\"filled_qty\":\"5\"}");

        JSONObject json = new JSONObject(order.rawResponseJson());
        assertEquals("PORTFOLIO_CAPTURE", json.getString("neuralarcExitSource"));
        assertEquals("MARKET", json.getString("neuralarcSubmissionType"));
        assertEquals("filled", json.getString("status"), "the fresh broker fields still win");
    }

    @Test
    void aFreshValueForAnAppKeyIsNotOverwrittenByTheOldOne() {
        StrategyOrder order = order(STAMPED);

        order.setRawResponseJson("{\"neuralarcExitSource\":\"MANUAL_USER\"}");

        assertEquals("MANUAL_USER", new JSONObject(order.rawResponseJson()).getString("neuralarcExitSource"));
    }

    @Test
    void ordersWithoutAppKeysAreReplacedAsBefore() {
        StrategyOrder order = order("{\"id\":\"x\"}");

        order.setRawResponseJson("{\"status\":\"filled\"}");

        assertEquals("{\"status\":\"filled\"}", order.rawResponseJson());
        assertFalse(order.rawResponseJson().contains("neuralarc"));
        order.setRawResponseJson(null);
        assertEquals("{}", order.rawResponseJson());
    }

    @Test
    void anUnparseableRefreshIsStoredAsGiven() {
        StrategyOrder order = order(STAMPED);

        order.setRawResponseJson("not json");

        assertEquals("not json", order.rawResponseJson());
    }

    private static StrategyOrder order(String rawJson) {
        return new StrategyOrder("o1", "s1", StrategyStage.MANUAL_EXIT, "a1", "c1", "AAPL",
                StrategyOrderSide.SELL, StrategyOrderType.MARKET, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, StrategyOrderStatus.SUBMITTED,
                Instant.now(), Instant.now(), null, rawJson);
    }
}
