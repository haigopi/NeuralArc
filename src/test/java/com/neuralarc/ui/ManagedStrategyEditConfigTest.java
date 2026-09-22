package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.neuralarc.ui.AverageDownTestEntries.position;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ManagedStrategyEditConfigTest {
    @Test
    void editingAHeldPositionShowsWhatTheSharesActuallyCost() {
        // TSLA: the entry rule said $389.72, but after later buys the 5 shares averaged $368.11.
        ManagedStrategy tsla = position("TSLA", 5, "368.11", "376.21");
        tsla.strategy.setBaseBuyLimitPrice(new BigDecimal("389.72"));

        assertEquals(new BigDecimal("368.11"), tsla.toEditConfig().baseBuyPrice());
        assertEquals(new BigDecimal("389.72"), tsla.toConfig().baseBuyPrice(), "the stored rule itself is unchanged");
    }

    @Test
    void beforeAnythingFillsTheEntryRulePriceIsShown() {
        ManagedStrategy pending = position("NIO", 0, "0", "3.68");
        pending.strategy.setBaseBuyLimitPrice(new BigDecimal("3.50"));

        assertEquals(new BigDecimal("3.50"), pending.toEditConfig().baseBuyPrice());
    }
}
