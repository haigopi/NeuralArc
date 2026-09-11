package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BottomStatusBarsLayoutTest {

    @Test
    void keepsFooterOrderWithPortfolioThinBarOnTop() {
        BottomStatusBarsFixture fixture = new BottomStatusBarsFixture();
        BottomStatusBars bars = fixture.bars();

        JPanel footerBars = new JPanel(new BorderLayout());
        footerBars.add(bars.portfolioBarPanel(), BorderLayout.NORTH);
        footerBars.add(bars.mainBarPanel(), BorderLayout.SOUTH);

        BorderLayout layout = (BorderLayout) footerBars.getLayout();
        assertSame(bars.portfolioBarPanel(), layout.getLayoutComponent(BorderLayout.NORTH));
        assertSame(bars.mainBarPanel(), layout.getLayoutComponent(BorderLayout.SOUTH));
    }

    @Test
    void forcesLeftAlignmentForBottomBarStatusLabels() {
        BottomStatusBarsFixture fixture = new BottomStatusBarsFixture();

        assertEquals(SwingConstants.LEFT, fixture.statusBar.getHorizontalAlignment());
        assertEquals(SwingConstants.LEFT, fixture.marketStatus.getHorizontalAlignment());
        assertEquals(SwingConstants.LEFT, fixture.streamStatus.getHorizontalAlignment());
        assertEquals(SwingConstants.LEFT, fixture.pollingSummary.getHorizontalAlignment());
        assertEquals(SwingConstants.LEFT, fixture.cpuUsageStatus.getHorizontalAlignment());
        assertEquals(SwingConstants.LEFT, fixture.memoryUsageStatus.getHorizontalAlignment());
        assertEquals(SwingConstants.LEFT, fixture.statusStrategyCount.getHorizontalAlignment());
        assertEquals(SwingConstants.LEFT, fixture.availableFundsStatus.getHorizontalAlignment());
        assertEquals(SwingConstants.LEFT, fixture.marketValueStatus.getHorizontalAlignment());
        assertEquals(SwingConstants.LEFT, fixture.investedValueStatus.getHorizontalAlignment());
        assertEquals(SwingConstants.LEFT, fixture.pendingBuyStatus.getHorizontalAlignment());
        assertEquals(SwingConstants.LEFT, fixture.compactStatusSummary.getHorizontalAlignment());
    }

    @Test
    void removesSeparatorLabelsAcrossStatusItems() {
        BottomStatusBarsFixture fixture = new BottomStatusBarsFixture();
        BottomStatusBars bars = fixture.bars();

        long dots = countLabelsWithText(bars.mainBarPanel(), ".")
                + countLabelsWithText(bars.portfolioBarPanel(), ".");
        long pipes = countLabelsWithText(bars.mainBarPanel(), "|")
                + countLabelsWithText(bars.portfolioBarPanel(), "|");

        assertEquals(0L, dots, "Dot separators should not appear in redesigned status sections.");
        assertEquals(0L, pipes, "Pipe separators should not appear in redesigned status sections.");
        assertTrue(countLabelsWithText(bars.mainBarPanel(), "Broker") > 0);
        assertTrue(countLabelsWithText(bars.portfolioBarPanel(), "Funds") > 0);
    }

    @Test
    void placesWifiIndicatorOnUpperPortfolioBarRightSide() {
        BottomStatusBarsFixture fixture = new BottomStatusBarsFixture();
        BottomStatusBars bars = fixture.bars();

        assertTrue(containsAccessibleComponent(bars.portfolioBarPanel(), "Internet connection status"));
        assertFalse(containsAccessibleComponent(bars.mainBarPanel(), "Internet connection status"));
    }

    @Test
    void compactSummaryUsesSpacingWithoutOldSeparators() {
        BottomStatusBarsFixture fixture = new BottomStatusBarsFixture();
        StatusBarPresenter presenter = new StatusBarPresenter();
        StatusBarPresenter.StatusBarViewModel vm = presenter.present(state(metrics()));

        fixture.bars().updateCompactSummaryAndDetails(vm, "Funds Available: $500");

        assertTrue(fixture.compactStatusSummary.getText().contains("   "));
        assertFalse(fixture.compactStatusSummary.getText().contains(" | "));
        assertFalse(fixture.compactStatusSummary.getText().contains(" . "));
        assertTrue(fixture.compactStatusSummary.getText().endsWith("Upcoming $20.00"),
                fixture.compactStatusSummary.getText());
    }

    @Test
    void portfolioBarShowsTheSelectedGridsFiguresWithTheirExplanations() {
        BottomStatusBarsFixture fixture = new BottomStatusBarsFixture();
        BottomStatusBars bars = fixture.bars();
        StatusBarPresenter.StatusBarViewModel vm = new StatusBarPresenter().present(state(metrics()));

        bars.applyPortfolioScope(vm.portfolioScope());

        for (String caption : new String[]{"Invested vs Upcoming", "Gaining", "Losing", "Pending Buy", "Pending Sell"}) {
            assertEquals(1L, countLabelsWithText(bars.portfolioBarPanel(), caption), caption);
        }
        assertEquals("$80.00 vs $20.00  (Total $100.00)", fixture.investedValueStatus.getText());
        assertEquals("2 · +$15.00", fixture.gainingPositionsStatus.getText());
        assertEquals("1 · -$5.00", fixture.losingPositionsStatus.getText());
        assertEquals("1", fixture.pendingBuyStatus.getText());
        assertEquals("3", fixture.pendingSellStatus.getText());
        assertTrue(fixture.investedValueStatus.getToolTipText().contains("what you actually paid"));
        assertEquals(fixture.pendingSellStatus.getToolTipText(),
                captionOf(bars.portfolioBarPanel(), "Pending Sell").getToolTipText(),
                "hovering the caption explains the figure too");
    }

    @Test
    void portfolioFiguresWrapOntoASecondRowWhenTheWindowIsTooNarrow() throws Exception {
        // On the event thread, like the app: resizing also re-arranges the bar from a resize listener.
        onEventThread(() -> {
            BottomStatusBarsFixture fixture = new BottomStatusBarsFixture();
            BottomStatusBars bars = fixture.bars();
            JPanel portfolio = bars.portfolioBarPanel();
            Container rows = (Container) ((BorderLayout) portfolio.getLayout()).getLayoutComponent(BorderLayout.WEST);

            portfolio.setSize(5000, 40);
            bars.updateLayoutMode();
            assertEquals(1, rows.getComponentCount(), "a wide window keeps one row");

            portfolio.setSize(300, 40);
            bars.updateLayoutMode();
            assertEquals(2, rows.getComponentCount(), "a narrow window moves the position counts to a second row");
            assertEquals(1L, countLabelsWithText((Container) rows.getComponent(0), "Invested vs Upcoming"));
            assertEquals(0L, countLabelsWithText((Container) rows.getComponent(0), "Gaining"));
            assertEquals(1L, countLabelsWithText((Container) rows.getComponent(1), "Pending Sell"));

            portfolio.setSize(5000, 40);
            bars.updateLayoutMode();
            assertEquals(1, rows.getComponentCount(), "widening the window joins the rows again");
        });
    }

    private static void onEventThread(Runnable body) throws Exception {
        try {
            javax.swing.SwingUtilities.invokeAndWait(body);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof Error error) {
                throw error;
            }
            throw e;
        }
    }

    @Test
    void detailsPopupNamesTheGridItTotals() {
        BottomStatusBarsFixture fixture = new BottomStatusBarsFixture();
        StatusBarPresenter.StatusBarViewModel vm = new StatusBarPresenter().present(state(metrics()));

        fixture.bars().updateCompactSummaryAndDetails(vm, "Funds Available: $500");

        String details = fixture.compactStatusSummary.getToolTipText();
        assertTrue(details.contains("Totals for</b>: Growth"), details);
        assertTrue(details.contains("Pending Sell</b>: 3"), details);
    }

    private static SystemMetricsPresenter.PortfolioScopeMetrics metrics() {
        return new SystemMetricsPresenter.PortfolioScopeMetrics(
                new java.math.BigDecimal("100.00"), new java.math.BigDecimal("80.00"), new java.math.BigDecimal("20.00"),
                new java.math.BigDecimal("15.00"), 2, new java.math.BigDecimal("-5.00"), 1, 1, 3);
    }

    private static StatusBarPresenter.StatusBarState state(SystemMetricsPresenter.PortfolioScopeMetrics metrics) {
        return new StatusBarPresenter.StatusBarState(1, 0, true, false, 1, 0, 5, false, true,
                "Market: Open", "tooltip", true, "Funds Available: $500", "CPU: 10%", "Memory: 120 MB",
                "Growth", metrics);
    }

    private JLabel captionOf(Container root, String text) {
        for (Component child : root.getComponents()) {
            if (child instanceof JLabel label && text.equals(label.getText())) {
                return label;
            }
            if (child instanceof Container nested) {
                JLabel found = captionOf(nested, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private long countLabelsWithText(Container root, String text) {
        long count = 0;
        for (Component child : root.getComponents()) {
            if (child instanceof JLabel label && text.equals(label.getText())) {
                count++;
            }
            if (child instanceof Container nested) {
                count += countLabelsWithText(nested, text);
            }
        }
        return count;
    }

    private boolean containsAccessibleComponent(Container root, String accessibleName) {
        for (Component child : root.getComponents()) {
            if (child.getAccessibleContext() != null
                    && accessibleName.equals(child.getAccessibleContext().getAccessibleName())) {
                return true;
            }
            if (child instanceof Container nested && containsAccessibleComponent(nested, accessibleName)) {
                return true;
            }
        }
        return false;
    }

    private static final class BottomStatusBarsFixture {
        private final JLabel statusBar = new JLabel("Connected");
        private final JLabel marketStatus = new JLabel("Open");
        private final JLabel streamStatus = new JLabel("connected");
        private final JLabel pollingSummary = new JLabel("Running");
        private final JLabel cpuUsageStatus = new JLabel("12%");
        private final JLabel memoryUsageStatus = new JLabel("256 MB");
        private final JLabel statusStrategyCount = new JLabel("Strategies 4");
        private final JLabel availableFundsStatus = new JLabel("$1000");
        private final JLabel marketValueStatus = new JLabel("$1200");
        private final JLabel investedValueStatus = new JLabel("$900");
        private final JLabel pendingBuyStatus = new JLabel("$500");
        private final JLabel gainingPositionsStatus = new JLabel("2");
        private final JLabel losingPositionsStatus = new JLabel("1");
        private final JLabel pendingSellStatus = new JLabel("3");
        private final JLabel compactStatusSummary = new JLabel("Broker Connected");
        private final JButton statusDetailsButton = new JButton("Details");
        private final BottomStatusBars bars;

        private BottomStatusBarsFixture() {
            JPanel statusRight = new JPanel(new BorderLayout());
            statusRight.add(new JLabel("App"), BorderLayout.WEST);
            bars = new BottomStatusBars(
                    new Font("Dialog", Font.PLAIN, 12),
                    new Color(180, 160, 110),
                    new Color(35, 35, 45),
                    statusBar,
                    marketStatus,
                    streamStatus,
                    pollingSummary,
                    cpuUsageStatus,
                    memoryUsageStatus,
                    statusStrategyCount,
                    availableFundsStatus,
                    marketValueStatus,
                    investedValueStatus,
                    pendingBuyStatus,
                    gainingPositionsStatus,
                    losingPositionsStatus,
                    pendingSellStatus,
                    compactStatusSummary,
                    statusDetailsButton,
                    statusRight,
                    new StatusBarPresenter(),
                    () -> false,
                    () -> {
                    }
            );
        }

        private BottomStatusBars bars() {
            return bars;
        }
    }
}
