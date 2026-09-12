package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceGridAnalyticsBarTest {
    private static final Font FONT = new Font("Dialog", Font.PLAIN, 12);
    private static final PortfolioScopePresenter.Tone NEUTRAL = PortfolioScopePresenter.Tone.NEUTRAL;

    @Test
    void showsTheTabsNameAndFiguresWithTheirExplanations() {
        WorkspaceGridAnalyticsBar bar = new WorkspaceGridAnalyticsBar(FONT);

        bar.apply("Growth", "<b>Growth</b><br>Planned budget $21,493.15", figures("Growth"));

        assertEquals("Growth", bar.scopeTitle());
        assertEquals("$412.10", bar.figure("Realized").getText());
        assertEquals("$16,980.40 vs $4,512.75  (Total $21,493.15)", bar.figure("Invested vs Upcoming").getText());
        assertEquals("14 · +$612.30", bar.figure("Gaining").getText());
        assertEquals("11", bar.figure("Pending Sell").getText());
        String tooltip = bar.figure("Invested vs Upcoming").getToolTipText();
        assertTrue(tooltip.contains("<b>Growth</b>"), tooltip);
        assertEquals(tooltip, label(bar, "Invested vs Upcoming").getToolTipText(), "the caption explains its figure too");
    }

    @Test
    void theTabNameExplainsTheFiguresLeftOutOfTheRow() {
        WorkspaceGridAnalyticsBar bar = new WorkspaceGridAnalyticsBar(FONT);

        bar.apply("Growth", "<b>Growth</b><br>Planned budget $21,493.15", figures("Growth"));

        JLabel title = label(bar, "Growth");
        assertNotNull(title);
        assertTrue(title.getToolTipText().contains("Planned budget $21,493.15"), title.getToolTipText());
    }

    @Test
    void switchingTabsUpdatesTheSameFiguresInPlace() {
        WorkspaceGridAnalyticsBar bar = new WorkspaceGridAnalyticsBar(FONT);
        bar.apply("Growth", "<b>Growth</b><br>Planned budget $21,493.15", figures("Growth"));
        JLabel pendingBuy = bar.figure("Pending Buy");

        List<PortfolioScopePresenter.Figure> other = new ArrayList<>(figures("Income"));
        other.set(other.size() - 2, new PortfolioScopePresenter.Figure("Pending Buy", "2", "Income: 2", NEUTRAL));
        bar.apply("Income", null, other);

        assertEquals("Income", bar.scopeTitle());
        assertSame(pendingBuy, bar.figure("Pending Buy"));
        assertEquals("2", pendingBuy.getText());
    }

    @Test
    void differentCaptionsRebuildTheRow() {
        WorkspaceGridAnalyticsBar bar = new WorkspaceGridAnalyticsBar(FONT);
        bar.apply("Growth", "<b>Growth</b><br>Planned budget $21,493.15", figures("Growth"));
        JLabel realized = bar.figure("Realized");

        bar.apply("Growth", null, List.of(new PortfolioScopePresenter.Figure("Realized", "$1.00", "tip", NEUTRAL)));

        assertNotSame(realized, bar.figure("Realized"));
        assertEquals(null, bar.figure("Gaining"));
    }

    @Test
    void tonesColourProfitsAndLosses() {
        WorkspaceGridAnalyticsBar bar = new WorkspaceGridAnalyticsBar(FONT);

        bar.apply("Growth", "<b>Growth</b><br>Planned budget $21,493.15", figures("Growth"));

        assertEquals(WorkspaceGridAnalyticsBar.POSITIVE, bar.figure("Gaining").getForeground());
        assertEquals(WorkspaceGridAnalyticsBar.NEGATIVE, bar.figure("Losing").getForeground());
        assertEquals(WorkspaceGridAnalyticsBar.NEGATIVE, bar.figure("Total").getForeground());
        assertEquals(WorkspaceGridAnalyticsBar.POSITIVE, bar.figure("Realized").getForeground());
    }

    @Test
    void keepsOneRowWhenItFitsAndWrapsAtAFigureWhenTheGridIsNarrow() throws Exception {
        onEventThread(() -> {
            WorkspaceGridAnalyticsBar bar = new WorkspaceGridAnalyticsBar(FONT);
            bar.apply("Growth", "<b>Growth</b><br>Planned budget $21,493.15", figures("Growth"));
            int oneRowHeight = bar.getPreferredSize().height;

            bar.setSize(5000, oneRowHeight);
            bar.doLayout();
            assertEquals(1, bar.rowCount(), "a wide grid keeps every figure on one row");

            bar.setSize(600, oneRowHeight);
            bar.doLayout();
            assertTrue(bar.rowCount() > 1, "a narrow grid wraps instead of cutting figures off");
            assertTrue(bar.getPreferredSize().height > oneRowHeight, "the footer grows to show every row");
            for (Component item : bar.getComponents()) {
                assertTrue(item.getX() + item.getWidth() <= 600, "nothing is cut off at the right");
            }
        });
    }

    private static List<PortfolioScopePresenter.Figure> figures(String scope) {
        List<PortfolioScopePresenter.Figure> figures = new ArrayList<>(new WorkspaceSummaryPresenter().figures(scope,
                new com.neuralarc.analytics.WorkspaceAccounting.Snapshot(
                        new BigDecimal("412.10"), new BigDecimal("-592.58"), new BigDecimal("-180.48"),
                        new BigDecimal("88.00"), new BigDecimal("21493.15"), 33, 25, 64.0,
                        new BigDecimal("612.30"), new BigDecimal("-1204.88"))));
        figures.addAll(new PortfolioScopePresenter().present(scope, new SystemMetricsPresenter.PortfolioScopeMetrics(
                new BigDecimal("18420.55"), new BigDecimal("16980.40"), new BigDecimal("4512.75"),
                new BigDecimal("612.30"), 14, new BigDecimal("-1204.88"), 19, 6, 11)).gridFigures());
        return figures;
    }

    private static JLabel label(Container root, String text) {
        for (Component child : root.getComponents()) {
            if (child instanceof JLabel label && text.equals(label.getText())) {
                return label;
            }
            if (child instanceof Container nested) {
                JLabel found = label(nested, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void onEventThread(Runnable body) throws Exception {
        try {
            SwingUtilities.invokeAndWait(body);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Error error) {
                throw error;
            }
            throw e;
        }
    }

    @Test
    void theFooterHasAnAccessibleName() {
        assertNotNull(new WorkspaceGridAnalyticsBar(FONT).getAccessibleContext().getAccessibleName());
    }
}
