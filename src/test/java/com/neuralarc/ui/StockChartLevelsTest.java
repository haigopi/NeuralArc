package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static com.neuralarc.ui.AverageDownTestEntries.position;
import static com.neuralarc.ui.AverageDownTestEntries.withWorkingTargetSell;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StockChartLevelsTest {
    @Test
    void aHeldPositionShowsItsCostTargetAndWorkingSell() {
        ManagedStrategy entry = withWorkingTargetSell(position("NIO", 10, "4.71", "3.68"));
        entry.strategy.setTargetSellEnabled(true);
        entry.strategy.setTargetSellPrice(new BigDecimal("5.25"));

        List<StockChartLevels.Level> levels = StockChartLevels.from(entry);

        assertEquals(4.71, price(levels, StockChartLevels.Kind.AVERAGE_COST), 1e-9);
        assertEquals(5.25, price(levels, StockChartLevels.Kind.TARGET), 1e-9);
        assertEquals(12.00, price(levels, StockChartLevels.Kind.WORKING_SELL), 1e-9);
        assertFalse(has(levels, StockChartLevels.Kind.BASE_BUY), "the planned first buy is history once shares are held");
    }

    @Test
    void beforeAnythingIsHeldThePlannedBuyIsShown() {
        List<StockChartLevels.Level> levels = StockChartLevels.from(position("NIO", 0, "0", "3.68"));

        assertEquals(8.00, price(levels, StockChartLevels.Kind.BASE_BUY), 1e-9);
        assertFalse(has(levels, StockChartLevels.Kind.AVERAGE_COST));
    }

    @Test
    void levelsThatAreNotSetAreLeftOut() {
        ManagedStrategy entry = position("NIO", 10, "4.71", "3.68");
        entry.strategy.setTargetSellEnabled(false);

        List<StockChartLevels.Level> levels = StockChartLevels.from(entry);

        assertFalse(has(levels, StockChartLevels.Kind.TARGET));
        assertFalse(has(levels, StockChartLevels.Kind.STOP_LOSS), "no stop price is set");
        assertFalse(has(levels, StockChartLevels.Kind.WORKING_SELL));
    }

    @Test
    void aSellWorkingAtTheTargetIsOneLineNotTwo() {
        ManagedStrategy entry = withWorkingTargetSell(position("NIO", 10, "4.71", "3.68"));
        entry.strategy.setTargetSellEnabled(true);
        entry.strategy.setTargetSellPrice(new BigDecimal("12.00"));

        List<StockChartLevels.Level> levels = StockChartLevels.from(entry);

        assertFalse(has(levels, StockChartLevels.Kind.WORKING_SELL));
        StockChartLevels.Level target = levels.stream()
                .filter(level -> level.kind() == StockChartLevels.Kind.TARGET).findFirst().orElseThrow();
        assertTrue(target.label().contains("sell working"), target.label());
    }

    @Test
    void everyLineExplainsWhatItIs() {
        ManagedStrategy entry = withWorkingTargetSell(position("NIO", 10, "4.71", "3.68"));
        entry.strategy.setTargetSellEnabled(true);
        entry.strategy.setTargetSellPrice(new BigDecimal("5.25"));

        for (StockChartLevels.Level level : StockChartLevels.from(entry)) {
            assertFalse(level.meaning().isBlank(), level.label());
        }
    }

    private static boolean has(List<StockChartLevels.Level> levels, StockChartLevels.Kind kind) {
        return levels.stream().anyMatch(level -> level.kind() == kind);
    }

    private static double price(List<StockChartLevels.Level> levels, StockChartLevels.Kind kind) {
        return levels.stream().filter(level -> level.kind() == kind).findFirst()
                .orElseThrow(() -> new AssertionError("no " + kind)).price();
    }
}
