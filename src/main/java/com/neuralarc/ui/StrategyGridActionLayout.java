package com.neuralarc.ui;

import java.util.List;

final class StrategyGridActionLayout {
    static final int ICON_BUTTON_WIDTH = 30;
    static final int BUTTON_HEIGHT = 24;
    // Promote is rendered icon-only like the other action buttons, so it stays compact
    // and the column never has to be wide enough to clip on narrow windows.
    static final int PROMOTE_BUTTON_WIDTH = ICON_BUTTON_WIDTH;
    static final int BUTTON_GAP = 6;
    // Generous padding so the centered buttons keep a clear margin (~24px) on each side and
    // the right-most button never sits flush against the table edge / vertical scrollbar,
    // which was clipping the Delete button.
    private static final int COLUMN_PADDING = 68;
    /**
     * Left to right from least to most consequential: look at the chart, edit, pause or resume,
     * sell, promote, delete. Delete stays last, furthest from a casual click.
     */
    private static final List<Action> ORDER_WITH_PROMOTE =
            List.of(Action.CHART, Action.EDIT, Action.TOGGLE, Action.SELL, Action.PROMOTE, Action.DELETE);
    private static final List<Action> ORDER_WITHOUT_PROMOTE =
            List.of(Action.CHART, Action.EDIT, Action.TOGGLE, Action.SELL, Action.DELETE);

    private StrategyGridActionLayout() {
    }

    static int buttonCount(boolean promoteVisible) {
        return order(promoteVisible).size();
    }

    static int contentWidth(boolean promoteVisible) {
        int width = 0;
        for (Action action : order(promoteVisible)) {
            width += buttonWidth(action);
        }
        return width + BUTTON_GAP * (buttonCount(promoteVisible) - 1);
    }

    static int columnWidth(boolean promoteVisible) {
        return contentWidth(promoteVisible) + COLUMN_PADDING;
    }

    static Action actionAt(int cellWidth, int xInCell, boolean promoteVisible) {
        int totalWidth = contentWidth(promoteVisible);
        int start = Math.max(0, (cellWidth - totalWidth) / 2);
        int x = xInCell - start;
        if (x < 0 || x >= totalWidth) {
            return Action.NONE;
        }
        int cursor = 0;
        for (Action action : order(promoteVisible)) {
            if (x < cursor) {
                return Action.NONE; // In the gap before this button.
            }
            int width = buttonWidth(action);
            if (x < cursor + width) {
                return action;
            }
            cursor += width + BUTTON_GAP;
        }
        return Action.NONE;
    }

    private static List<Action> order(boolean promoteVisible) {
        return promoteVisible ? ORDER_WITH_PROMOTE : ORDER_WITHOUT_PROMOTE;
    }

    private static int buttonWidth(Action action) {
        return action == Action.PROMOTE ? PROMOTE_BUTTON_WIDTH : ICON_BUTTON_WIDTH;
    }

    enum Action {
        CHART,
        EDIT,
        TOGGLE,
        SELL,
        PROMOTE,
        DELETE,
        NONE
    }
}
