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
        assertEquals(SwingConstants.LEFT, fixture.baseBuyPendingStatus.getHorizontalAlignment());
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
        StatusBarPresenter.StatusBarViewModel vm = presenter.present(new StatusBarPresenter.StatusBarState(
                1,
                0,
                true,
                false,
                1,
                0,
                5,
                false,
                true,
                "Market: Open",
                "tooltip",
                true,
                "Market Value: $100",
                "Invested Value: $80",
                "Funds Available: $500",
                "Base Buy Pending Total: $20",
                "CPU: 10%",
                "Memory: 120 MB",
                0, 0, 0
        ));

        fixture.bars().updateCompactSummaryAndDetails(vm, "Funds Available: $500");

        assertTrue(fixture.compactStatusSummary.getText().contains("   "));
        assertFalse(fixture.compactStatusSummary.getText().contains(" | "));
        assertFalse(fixture.compactStatusSummary.getText().contains(" . "));
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
        private final JLabel baseBuyPendingStatus = new JLabel("$500");
        private final JLabel gainingPositionsStatus = new JLabel("2");
        private final JLabel losingPositionsStatus = new JLabel("1");
        private final JLabel pendingToFillStatus = new JLabel("3");
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
                    baseBuyPendingStatus,
                    gainingPositionsStatus,
                    losingPositionsStatus,
                    pendingToFillStatus,
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
