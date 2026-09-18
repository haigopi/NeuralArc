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

    @Test
    void readsEquityAndThePreviousCloseEquity() {
        java.util.Optional<AlpacaAccountEquity> equity = AlpacaAccountFundsParser.equity(
                new JSONObject("{\"equity\":\"20512.40\",\"last_equity\":\"20200.30\",\"cash\":\"512.40\"}"));

        org.junit.jupiter.api.Assertions.assertEquals(
                new AlpacaAccountEquity(new java.math.BigDecimal("20512.40"), new java.math.BigDecimal("20200.30")),
                equity.orElseThrow());
    }

    @Test
    void anAccountWithoutEquityIsNotReadAsZero() {
        org.junit.jupiter.api.Assertions.assertTrue(AlpacaAccountFundsParser.equity(new JSONObject("{}")).isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(
                AlpacaAccountFundsParser.equity(new JSONObject("{\"equity\":\"0\"}")).isEmpty());
    }

    @Test
    void aMissingPreviousCloseUsesTodaysEquityAsTheBaseline() {
        AlpacaAccountEquity equity = AlpacaAccountFundsParser.equity(
                new JSONObject("{\"equity\":\"1000\"}")).orElseThrow();

        org.junit.jupiter.api.Assertions.assertEquals(equity.equity(), equity.lastEquity());
    }
}
