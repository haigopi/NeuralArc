package com.neuralarc.ui;

final class StrategyGridActionLayout {
    static final int ICON_BUTTON_WIDTH = 30;
    static final int BUTTON_HEIGHT = 24;
    // Promote is rendered icon-only like the other action buttons, so it stays compact
    // and the column never has to be wide enough to clip on narrow windows.
    static final int PROMOTE_BUTTON_WIDTH = ICON_BUTTON_WIDTH;
    static final int BUTTON_GAP = 6;
    private static final int COLUMN_PADDING = 16;

    private StrategyGridActionLayout() {
    }

    static int buttonCount(boolean promoteVisible) {
        return promoteVisible ? 5 : 4;
    }

    static int contentWidth(boolean promoteVisible) {
        int iconButtonsWidth = ICON_BUTTON_WIDTH * 4;
        int promoteWidth = promoteVisible ? PROMOTE_BUTTON_WIDTH : 0;
        return iconButtonsWidth + promoteWidth + BUTTON_GAP * (buttonCount(promoteVisible) - 1);
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
        if (x < cursor + ICON_BUTTON_WIDTH) {
            return Action.EDIT;
        }
        cursor += ICON_BUTTON_WIDTH + BUTTON_GAP;
        if (x < cursor) {
            return Action.NONE;
        }
        if (x < cursor + ICON_BUTTON_WIDTH) {
            return Action.TOGGLE;
        }
        cursor += ICON_BUTTON_WIDTH + BUTTON_GAP;
        if (x < cursor) {
            return Action.NONE;
        }
        if (x < cursor + ICON_BUTTON_WIDTH) {
            return Action.SELL;
        }
        cursor += ICON_BUTTON_WIDTH + BUTTON_GAP;
        if (x < cursor) {
            return Action.NONE;
        }
        if (promoteVisible) {
            if (x < cursor + PROMOTE_BUTTON_WIDTH) {
                return Action.PROMOTE;
            }
            cursor += PROMOTE_BUTTON_WIDTH + BUTTON_GAP;
            if (x < cursor) {
                return Action.NONE;
            }
        }
        if (x < cursor + ICON_BUTTON_WIDTH) {
            return Action.DELETE;
        }
        return Action.NONE;
    }

    enum Action {
        EDIT,
        TOGGLE,
        SELL,
        PROMOTE,
        DELETE,
        NONE
    }
}
