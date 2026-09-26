package com.neuralarc.api;

import com.neuralarc.util.Monetary;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlpacaOrderDataDuplicateTest {
    @Test
    void theBrokersDuplicateRefusalIsRecognised() {
        assertTrue(order("422", "{\"code\":42210000,\"message\":\"client_order_id must be unique\"}")
                .duplicateClientOrderId());
        assertTrue(order("rejected", "{\"message\":\"client_order_id already exists\"}").duplicateClientOrderId());
    }

    @Test
    void ordinaryRejectionsAreNotMistakenForDuplicates() {
        assertFalse(order("rejected", "{\"message\":\"insufficient buying power\"}").duplicateClientOrderId());
        assertFalse(order("accepted", "{}").duplicateClientOrderId());
        assertFalse(AlpacaOrderData.transportFailure("timeout").duplicateClientOrderId());
    }

    private static AlpacaOrderData order(String status, String rawJson) {
        return new AlpacaOrderData("", "NA_LIVE_PLAY_EDRY_BASE_BUY_20260925_AB12", "EDRY", "buy", "limit",
                Monetary.zero(), Monetary.zero(), Monetary.zero(), status, rawJson);
    }
}
