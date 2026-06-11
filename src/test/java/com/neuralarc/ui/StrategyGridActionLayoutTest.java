package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StrategyGridActionLayoutTest {
    @Test
    void actionAtUsesCompactCenteredButtonZonesWithPromote() {
        int cellWidth = StrategyGridActionLayout.columnWidth(true);
        int start = (cellWidth - StrategyGridActionLayout.contentWidth(true)) / 2;

        assertEquals(StrategyGridActionLayout.Action.EDIT,
                StrategyGridActionLayout.actionAt(cellWidth, start, true));
        assertEquals(StrategyGridActionLayout.Action.NONE,
                StrategyGridActionLayout.actionAt(cellWidth, start + StrategyGridActionLayout.ICON_BUTTON_WIDTH, true));
        assertEquals(StrategyGridActionLayout.Action.TOGGLE,
                StrategyGridActionLayout.actionAt(cellWidth, start + StrategyGridActionLayout.ICON_BUTTON_WIDTH
                        + StrategyGridActionLayout.BUTTON_GAP, true));
        assertEquals(StrategyGridActionLayout.Action.SELL,
                StrategyGridActionLayout.actionAt(cellWidth, start + StrategyGridActionLayout.ICON_BUTTON_WIDTH * 2
                        + StrategyGridActionLayout.BUTTON_GAP * 2, true));
        assertEquals(StrategyGridActionLayout.Action.PROMOTE,
                StrategyGridActionLayout.actionAt(cellWidth, start + StrategyGridActionLayout.ICON_BUTTON_WIDTH * 3
                        + StrategyGridActionLayout.BUTTON_GAP * 3, true));
        assertEquals(StrategyGridActionLayout.Action.DELETE,
                StrategyGridActionLayout.actionAt(cellWidth, start + StrategyGridActionLayout.ICON_BUTTON_WIDTH * 3
                        + StrategyGridActionLayout.PROMOTE_BUTTON_WIDTH
                        + StrategyGridActionLayout.BUTTON_GAP * 4, true));
    }

    @Test
    void actionAtSkipsPromoteWhenHidden() {
        int cellWidth = StrategyGridActionLayout.columnWidth(false);
        int start = (cellWidth - StrategyGridActionLayout.contentWidth(false)) / 2;

        assertEquals(StrategyGridActionLayout.Action.DELETE,
                StrategyGridActionLayout.actionAt(cellWidth, start + StrategyGridActionLayout.ICON_BUTTON_WIDTH * 3
                        + StrategyGridActionLayout.BUTTON_GAP * 3, false));
    }
}
