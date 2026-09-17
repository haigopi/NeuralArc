package com.neuralarc.api;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlpacaPositionDataTest {
    @Test
    void aLongPositionExistsAndIsNotShort() {
        AlpacaPositionData position = position("NVDA", "10");

        assertTrue(position.exists());
        assertTrue(position.hasExposure());
        assertFalse(position.isShort());
    }

    @Test
    void aShortIsExposureButDeliberatelyDoesNotExist() {
        // exists() drives the long-only engine. Reporting a short as a position there would have it
        // sell more of something already sold short; read as flat it buys instead, which covers.
        AlpacaPositionData position = position("MRVL", "-1");

        assertFalse(position.exists(), "the engine must not treat a short as a sellable position");
        assertTrue(position.hasExposure(), "but the account really is exposed");
        assertTrue(position.isShort());
    }

    @Test
    void aFlatSymbolIsNeitherHeldNorExposed() {
        AlpacaPositionData position = position("AAPL", "0");

        assertFalse(position.exists());
        assertFalse(position.hasExposure());
        assertFalse(position.isShort());
    }

    @Test
    void aBlankSymbolIsNeverExposure() {
        assertFalse(position("", "10").hasExposure());
        assertFalse(position("", "-10").isShort());
        assertFalse(position(null, "-10").hasExposure());
    }

    private static AlpacaPositionData position(String symbol, String quantity) {
        return new AlpacaPositionData(symbol, new BigDecimal(quantity), new BigDecimal("201.20"),
                new BigDecimal("229.56"), "{}");
    }
}
