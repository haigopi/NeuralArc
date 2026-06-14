package com.neuralarc.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Color;
import javax.swing.JPanel;
import javax.swing.border.Border;
import org.junit.jupiter.api.Test;

class StrategyGridSelectionStylerTest {
    @Test
    void selectedActionsCellKeepsRendererTopPadding() {
        JPanel component = new JPanel();

        StrategyGridSelectionStyler.applySelectionBorder(
                component,
                true,
                StrategyGridLayoutPresenter.ACTIONS_COLUMN_INDEX,
                Color.BLUE
        );

        assertEquals(
                StrategyGridSelectionStyler.ACTIONS_SELECTED_TOP_PADDING,
                StrategyGridSelectionStyler.topInset(component.getBorder())
        );
    }

    @Test
    void selectedNonActionCellUsesFlushSelectionPadding() {
        JPanel component = new JPanel();

        StrategyGridSelectionStyler.applySelectionBorder(component, true, 1, Color.BLUE);

        assertEquals(
                StrategyGridSelectionStyler.DEFAULT_SELECTED_TOP_PADDING,
                StrategyGridSelectionStyler.topInset(component.getBorder())
        );
    }

    @Test
    void unselectedCellKeepsExistingBorder() {
        JPanel component = new JPanel();
        Border existing = StrategyGridSelectionStyler.defaultCellBorder();
        component.setBorder(existing);

        StrategyGridSelectionStyler.applySelectionBorder(
                component,
                false,
                StrategyGridLayoutPresenter.ACTIONS_COLUMN_INDEX,
                Color.BLUE
        );

        assertEquals(existing, component.getBorder());
    }
}
