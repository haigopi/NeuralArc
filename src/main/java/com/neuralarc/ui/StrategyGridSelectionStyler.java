package com.neuralarc.ui;

import java.awt.Color;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;

final class StrategyGridSelectionStyler {
    static final int DEFAULT_SELECTED_TOP_PADDING = 0;
    static final int ACTIONS_SELECTED_TOP_PADDING = 5;

    private StrategyGridSelectionStyler() {
    }

    static void applySelectionBorder(JComponent component, boolean rowSelected, int column, Color selectionBorderColor) {
        if (!rowSelected) {
            return;
        }
        if (column == 0) {
            component.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 3, 0, 0, selectionBorderColor),
                    BorderFactory.createEmptyBorder(0, 7, 0, 10)
            ));
            return;
        }
        if (column == StrategyGridLayoutPresenter.ACTIONS_COLUMN_INDEX) {
            component.setBorder(actionCellBorder());
            return;
        }
        component.setBorder(defaultCellBorder());
    }

    static Border actionCellBorder() {
        return BorderFactory.createEmptyBorder(ACTIONS_SELECTED_TOP_PADDING, 0, 0, 0);
    }

    static Border defaultCellBorder() {
        return BorderFactory.createEmptyBorder(DEFAULT_SELECTED_TOP_PADDING, 10, 0, 10);
    }

    static int topInset(Border border) {
        if (border instanceof EmptyBorder emptyBorder) {
            return emptyBorder.getBorderInsets().top;
        }
        return border.getBorderInsets(null).top;
    }
}
