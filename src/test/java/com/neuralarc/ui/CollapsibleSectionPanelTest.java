package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import java.awt.Dimension;

import javax.swing.JPanel;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CollapsibleSectionPanelTest {
    @Test
    void minimumHeightStaysBelowPreferredHeightWhenExpanded() {
        JPanel content = new JPanel();
        content.setPreferredSize(new Dimension(320, 180));
        CollapsibleSectionPanel panel = new CollapsibleSectionPanel("Logs", content);
        panel.setCollapsed(false);

        assertTrue(panel.getMinimumSize().height < panel.getPreferredSize().height);
    }
}
