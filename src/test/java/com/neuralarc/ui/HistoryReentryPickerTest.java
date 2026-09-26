package com.neuralarc.ui;

import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static com.neuralarc.ui.AverageDownTestEntries.position;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryReentryPickerTest {
    @Test
    void nothingIsTickedToStartSoPlacingIsAlwaysADeliberateChoice() {
        HistoryReentryPicker picker = picker();

        assertTrue(picker.selected().isEmpty());
        assertFalse(picker.placeButton().isEnabled());
        assertEquals("Place 0 Selected", picker.placeButton().getText());
        picker.dispose();
    }

    @Test
    void onlyTheTickedStocksArePlaced() {
        HistoryReentryPicker picker = picker();

        picker.setPicked(0, true);
        picker.setPicked(1, true);

        assertEquals(List.of("NIO", "QQQ"),
                picker.selected().stream().map(pick -> pick.source().symbol()).toList());
        assertEquals("Place 2 Selected", picker.placeButton().getText());
        assertTrue(picker.placeButton().isEnabled());
        picker.dispose();
    }

    @Test
    void pricesFillInAsTheyLoadAndACellSaysWhichStateItIsIn() {
        HistoryReentryPicker picker = picker();

        assertEquals("…", picker.valueAt(0, 3), "a price that has not loaded yet is not shown as zero");
        picker.applyLevels("NIO", new HistoryReentry.Levels(
                new BigDecimal("4.10"), new BigDecimal("4.60"), new BigDecimal("5.20")));
        picker.applyLevels("MRVL", HistoryReentry.Levels.unknown());

        assertEquals("$4.10", picker.valueAt(0, 3));
        assertEquals("$4.60", picker.valueAt(0, 4));
        assertEquals("$5.20", picker.valueAt(0, 5));
        assertEquals("—", picker.valueAt(2, 3), "a stock with no recent prices is marked, not blank");
        assertEquals("Loading prices… 1 of 3.", picker.statusText());
        picker.dispose();
    }

    @Test
    void theTickedPriceIsTheOneThatGetsPlaced() {
        HistoryReentryPicker picker = picker();
        picker.applyLevels("NIO", new HistoryReentry.Levels(
                new BigDecimal("4.10"), new BigDecimal("4.60"), new BigDecimal("5.20")));

        picker.setPicked(0, true);
        picker.setPicked(1, true);

        List<HistoryReentryPicker.Pick> picks = picker.selected();
        assertEquals(new BigDecimal("4.10"), picks.get(0).entryPrice());
        assertEquals(BigDecimal.ZERO, picks.get(1).entryPrice(), "an unloaded row is priced at placement instead");
        picker.dispose();
    }

    @Test
    void aTypedPriceIsWhatGetsPlaced() {
        HistoryReentryPicker picker = picker();
        picker.applyLevels("NIO", new HistoryReentry.Levels(
                new BigDecimal("4.10"), new BigDecimal("4.60"), new BigDecimal("5.20")));

        picker.setEntryPriceOverride("NIO", "$3.85");
        picker.setPicked(0, true);

        assertEquals("$3.85", picker.valueAt(0, 3), "the cell shows the operator's number, not the calculated one");
        assertEquals(new BigDecimal("3.85"), picker.selected().get(0).entryPrice());
        assertTrue(picker.hasOverride("NIO"));
        picker.dispose();
    }

    @Test
    void anUnreadableOrClearedPriceFallsBackToTheCalculatedOne() {
        HistoryReentryPicker picker = picker();
        picker.applyLevels("NIO", new HistoryReentry.Levels(
                new BigDecimal("4.10"), new BigDecimal("4.60"), new BigDecimal("5.20")));
        picker.setPicked(0, true);

        picker.setEntryPriceOverride("NIO", "cheap please");
        assertEquals(new BigDecimal("4.10"), picker.selected().get(0).entryPrice(),
                "an unreadable price must never become an order");

        picker.setEntryPriceOverride("NIO", "3.85");
        picker.setEntryPriceOverride("NIO", "   ");
        assertEquals(new BigDecimal("4.10"), picker.selected().get(0).entryPrice(), "clearing restores the calculation");
        picker.dispose();
    }

    @Test
    void aStockWithNoClosedResultIsNotCountedAsAWinner() {
        Map<String, BigDecimal> pnl = Map.of("NIO", BigDecimal.ZERO,
                "MRVL", new BigDecimal("-120"), "QQQ", new BigDecimal("15"));
        HistoryReentryPicker picker = new HistoryReentryPicker(null, List.of(position("NIO", 0, "0", "4").strategy,
                position("MRVL", 0, "0", "238").strategy, position("QQQ", 0, "0", "500").strategy),
                StrategyMode.LIVE, "Comeback Picks · Sep 25", null, source -> pnl.get(source.symbol()));

        assertEquals("Closed in profit (1)", picker.gainsToggle().getText());
        assertTrue(picker.lossesToggle().getText().contains("1 with no closed result"));
        assertEquals("no closed result", picker.valueAt(2, 2));
        picker.dispose();
    }

    @Test
    void winnersAndLosersAreSeparatedAndCounted() {
        HistoryReentryPicker picker = picker();

        assertEquals("Closed in profit (2)", picker.gainsToggle().getText());
        assertEquals("Closed at a loss (1)", picker.lossesToggle().getText());
        assertEquals(List.of("NIO", "QQQ", "MRVL"),
                List.of(picker.valueAt(0, 1), picker.valueAt(1, 1), picker.valueAt(2, 1)),
                "gains first, then the losses that deserve a second look");
        assertEquals("-$120.00 loss", picker.valueAt(2, 2));
        assertEquals("+$40.00 gain", picker.valueAt(0, 2));
        picker.dispose();
    }

    @Test
    void hidingAGroupKeepsItOutOfSelectAllShown() {
        HistoryReentryPicker picker = picker();

        picker.lossesToggle().doClick();

        assertEquals(2, picker.shownRowCount(), "only the two winners are listed");
        assertEquals(List.of("NIO", "QQQ"), List.of(picker.valueAt(0, 1), picker.valueAt(1, 1)));

        picker.setPicked(2, true); // the hidden loser keeps whatever tick it had
        picker.lossesToggle().doClick();
        assertEquals(3, picker.shownRowCount());
        picker.dispose();
    }

    private static HistoryReentryPicker picker() {
        Map<String, BigDecimal> pnl = Map.of(
                "NIO", new BigDecimal("40"), "MRVL", new BigDecimal("-120"), "QQQ", new BigDecimal("15"));
        return new HistoryReentryPicker(null, List.of(position("NIO", 0, "0", "4").strategy,
                position("MRVL", 0, "0", "238").strategy, position("QQQ", 0, "0", "500").strategy),
                StrategyMode.LIVE, "Comeback Picks · Sep 22", null,
                source -> pnl.get(source.symbol()));
    }
}
