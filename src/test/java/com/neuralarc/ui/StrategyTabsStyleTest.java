package com.neuralarc.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatDarkLaf;
import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Dimension;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategyTabsStyleTest {
    /** The tab style TradingFrame applies must be accepted by FlatLaf and make tabs narrower than the default. */
    @Test
    void tightTabStyleIsValidAndNarrowsTheTabs() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            FlatDarkLaf.setup();
            JTabbedPane plain = new JTabbedPane();
            JTabbedPane tight = new JTabbedPane();
            tight.putClientProperty(FlatClientProperties.STYLE, "tabInsets: 2,6,2,6; textIconGap: 4; minimumTabWidth: 0");
            for (JTabbedPane tabs : new JTabbedPane[] {plain, tight}) {
                tabs.addTab("All Stocks - PAPER (24)", new JLabel());
                tabs.addTab("Growth (3)", new JLabel());
                tabs.setSize(new Dimension(800, 300));
                tabs.doLayout();
            }
            assertTrue(tight.getBoundsAt(0).width < plain.getBoundsAt(0).width,
                    tight.getBoundsAt(0).width + " vs " + plain.getBoundsAt(0).width);
        });
    }
}
