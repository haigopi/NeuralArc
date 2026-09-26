package com.neuralarc.agent;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolArgumentsTest {
    @Test
    void symbolsAreNormalizedTheWayTheBrokerExpects() throws Exception {
        assertEquals("AAPL", new ToolArguments(new JSONObject().put("symbol", " aapl ")).symbol("symbol"));
    }

    @Test
    void aSentenceIsNotASymbol() {
        ToolArguments arguments = new ToolArguments(new JSONObject().put("symbol", "the stock Apple"));

        ToolArgumentException thrown = assertThrows(ToolArgumentException.class, () -> arguments.symbol("symbol"));
        assertTrue(thrown.getMessage().contains("ticker"), "the message has to tell the model what to send instead");
    }

    @Test
    void aMissingRequiredArgumentSaysWhichOne() {
        ToolArgumentException thrown = assertThrows(ToolArgumentException.class,
                () -> new ToolArguments(new JSONObject()).symbol("symbol"));

        assertEquals("'symbol' is required.", thrown.getMessage());
    }

    @Test
    void numbersArrivingAsTextStillCount() throws Exception {
        assertEquals(30, new ToolArguments(new JSONObject().put("days", "30")).integer("days", 1, 365, 60));
    }

    @Test
    void anOutOfRangeNumberIsRefusedRatherThanQuietlyClamped() {
        ToolArguments arguments = new ToolArguments(new JSONObject().put("days", 4000));

        ToolArgumentException thrown = assertThrows(ToolArgumentException.class,
                () -> arguments.integer("days", 1, 365, 60));
        assertEquals("'days' must be between 1 and 365, was 4000.", thrown.getMessage());
    }

    @Test
    void absentOptionalsFallBack() throws Exception {
        ToolArguments arguments = new ToolArguments(new JSONObject());

        assertEquals(60, arguments.integer("days", 1, 365, 60));
        assertEquals("ALL", arguments.choice("mode", List.of("PAPER", "LIVE", "ALL"), "ALL"));
        assertTrue(arguments.bool("extended_hours", true));
    }

    @Test
    void aChoiceOutsideTheListIsRefused() {
        ToolArguments arguments = new ToolArguments(new JSONObject().put("mode", "REAL_MONEY"));

        assertThrows(ToolArgumentException.class, () -> arguments.choice("mode", List.of("PAPER", "LIVE"), "PAPER"));
    }
}
