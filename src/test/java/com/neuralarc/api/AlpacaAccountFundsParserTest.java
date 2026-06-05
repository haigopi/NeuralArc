package com.neuralarc.api;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AlpacaAccountFundsParserTest {
    @Test
    void prefersCashOverMarginBuyingPower() {
        JSONObject json = new JSONObject()
                .put("buying_power", "20000.00")
                .put("regt_buying_power", "10000.00")
                .put("cash", "5123.45");

        assertEquals(new BigDecimal("5123.45"), AlpacaAccountFundsParser.availableFunds(json));
    }

    @Test
    void fallsBackToBuyingPowerWhenCashLikeFieldsAreMissing() {
        JSONObject json = new JSONObject()
                .put("buying_power", "20000.00")
                .put("regt_buying_power", "10000.00");

        assertEquals(new BigDecimal("20000.00"), AlpacaAccountFundsParser.availableFunds(json));
    }
}
