package com.neuralarc.ui;

import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyMode;
import org.junit.jupiter.api.Test;

import java.util.List;

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
        picker.setPicked(2, true);

        assertEquals(List.of("NIO", "QQQ"), picker.selected().stream().map(Strategy::symbol).toList());
        assertEquals("Place 2 Selected", picker.placeButton().getText());
        assertTrue(picker.placeButton().isEnabled());
        picker.dispose();
    }

    private static HistoryReentryPicker picker() {
        return new HistoryReentryPicker(null, List.of(position("NIO", 0, "0", "4").strategy,
                position("MRVL", 0, "0", "238").strategy, position("QQQ", 0, "0", "500").strategy),
                StrategyMode.LIVE, "Comeback Picks · Sep 22");
    }
}
