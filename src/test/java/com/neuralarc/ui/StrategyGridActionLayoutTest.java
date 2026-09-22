package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StrategyGridActionLayoutTest {
    private static final int BUTTON = StrategyGridActionLayout.ICON_BUTTON_WIDTH;
    private static final int GAP = StrategyGridActionLayout.BUTTON_GAP;

    @Test
    void chartComesFirstThenTheActionsInOrderOfConsequence() {
        int cellWidth = StrategyGridActionLayout.columnWidth(true);
        int start = (cellWidth - StrategyGridActionLayout.contentWidth(true)) / 2;

        assertEquals(StrategyGridActionLayout.Action.NONE, StrategyGridActionLayout.actionAt(cellWidth, start - 1, true));
        assertEquals(StrategyGridActionLayout.Action.CHART, StrategyGridActionLayout.actionAt(cellWidth, start, true));
        assertEquals(StrategyGridActionLayout.Action.NONE, StrategyGridActionLayout.actionAt(cellWidth, start + BUTTON, true),
                "the gap between buttons does nothing");
        assertEquals(StrategyGridActionLayout.Action.ANALYZE, StrategyGridActionLayout.actionAt(cellWidth, start + (BUTTON + GAP), true));
        assertEquals(StrategyGridActionLayout.Action.EDIT, StrategyGridActionLayout.actionAt(cellWidth, start + 2 * (BUTTON + GAP), true));
        assertEquals(StrategyGridActionLayout.Action.TOGGLE, StrategyGridActionLayout.actionAt(cellWidth, start + 3 * (BUTTON + GAP), true));
        assertEquals(StrategyGridActionLayout.Action.SELL, StrategyGridActionLayout.actionAt(cellWidth, start + 4 * (BUTTON + GAP), true));
        assertEquals(StrategyGridActionLayout.Action.PROMOTE, StrategyGridActionLayout.actionAt(cellWidth, start + 5 * (BUTTON + GAP), true));
        assertEquals(StrategyGridActionLayout.Action.DELETE, StrategyGridActionLayout.actionAt(cellWidth,
                start + 5 * (BUTTON + GAP) + StrategyGridActionLayout.PROMOTE_BUTTON_WIDTH + GAP, true));
    }

    @Test
    void actionAtSkipsPromoteWhenHidden() {
        int cellWidth = StrategyGridActionLayout.columnWidth(false);
        int start = (cellWidth - StrategyGridActionLayout.contentWidth(false)) / 2;

        assertEquals(StrategyGridActionLayout.Action.DELETE,
                StrategyGridActionLayout.actionAt(cellWidth, start + 5 * (BUTTON + GAP), false));
    }

    @Test
    void theColumnMakesRoomForTheChartButton() {
        assertEquals(7, StrategyGridActionLayout.buttonCount(true));
        assertEquals(6, StrategyGridActionLayout.buttonCount(false));
        assertEquals(6 * BUTTON + 5 * GAP, StrategyGridActionLayout.contentWidth(false));
    }
}
