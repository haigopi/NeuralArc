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
        assertEquals(SwingConstants.LEFT, fixture.compactStatusSummary.getHorizontalAlignment());
    }

    @Test
    void usesDotSeparatorsAcrossGroupedStatusItems() {
        BottomStatusBarsFixture fixture = new BottomStatusBarsFixture();
        BottomStatusBars bars = fixture.bars();

        long dots = countLabelsWithText(bars.mainBarPanel(), ".")
                + countLabelsWithText(bars.portfolioBarPanel(), ".");
        long pipes = countLabelsWithText(bars.mainBarPanel(), "|")
                + countLabelsWithText(bars.portfolioBarPanel(), "|");

        assertTrue(dots >= 3, "Expected dot separators in grouped sections of both bars.");
        assertEquals(0L, pipes, "Pipe separators should no longer appear in grouped status sections.");
    }

    @Test
    void compactSummaryUsesDotSeparator() {
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
                "CPU: 10%",
                "Memory: 120 MB"
        ));

        fixture.bars().updateCompactSummaryAndDetails(vm, "Funds Available: $500");

        assertTrue(fixture.compactStatusSummary.getText().contains(" . "));
        assertFalse(fixture.compactStatusSummary.getText().contains(" | "));
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

    private static final class BottomStatusBarsFixture {
        private final JLabel statusBar = new JLabel("Broker: Connected");
        private final JLabel marketStatus = new JLabel("Market: Open");
        private final JLabel streamStatus = new JLabel("Trade Stream: connected");
        private final JLabel pollingSummary = new JLabel("Strategy Polling: Running");
        private final JLabel cpuUsageStatus = new JLabel("CPU: 12%");
        private final JLabel memoryUsageStatus = new JLabel("Memory: 256 MB");
        private final JLabel statusStrategyCount = new JLabel("Records: 4");
        private final JLabel availableFundsStatus = new JLabel("Funds Available: $1000");
        private final JLabel marketValueStatus = new JLabel("Market Value: $1200");
        private final JLabel investedValueStatus = new JLabel("Invested Value: $900");
        private final JLabel compactStatusSummary = new JLabel("Broker: Connected");
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
                    compactStatusSummary,
                    statusDetailsButton,
                    statusRight,
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

