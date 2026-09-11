package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import javax.swing.JToggleButton;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Builds the chart window's content only, so no window is shown. */
class StockChartViewTest {
    @Test
    void showsTheChartAndAPlainLanguageGuideOnceDataArrives() {
        StockChartView view = new StockChartView("NIO", "ORB Engine  ·  Live");
        view.showData(data());

        assertNotNull(view.chart());
        List<String> texts = labelTexts(view);
        assertTrue(texts.stream().anyMatch(text -> text.contains("How to read this chart")));
        for (String title : List.of(StockChartReadings.TREND, StockChartReadings.RSI, StockChartReadings.VOLUME,
                StockChartReadings.MACD, StockChartReadings.ACCUMULATION, StockChartReadings.PLAN)) {
            assertTrue(texts.contains(title), "guide section " + title);
        }
        assertTrue(texts.stream().anyMatch(text -> text.contains("not a recommendation")));
    }

    @Test
    void opensOnTheLastYearWithRangesThatFitTheHistory() {
        StockChartView view = new StockChartView("NIO", "");
        view.showData(data());

        assertEquals(StockChartPanel.ONE_YEAR, view.chart().visibleBars());
        JToggleButton oneYear = button(view, "1Y");
        assertTrue(oneYear.isSelected());
        assertFalse(button(view, "3Y").isEnabled(), "only 400 trading days were loaded");
        assertTrue(button(view, "All").isEnabled());

        button(view, "6M").doClick();
        assertEquals(StockChartPanel.SIX_MONTHS, view.chart().visibleBars());
        button(view, "All").doClick();
        assertEquals(400, view.chart().visibleBars());
    }

    @Test
    void paintsEveryRangeAndTheCrosshairWithoutErrors() {
        StockChartPanel panel = new StockChartPanel(data());
        panel.setSize(900, 640);
        BufferedImage image = new BufferedImage(900, 640, BufferedImage.TYPE_INT_ARGB);
        for (int bars : new int[]{StockChartPanel.SIX_MONTHS, StockChartPanel.ONE_YEAR, 0}) {
            panel.showLast(bars);
            paint(panel, image);
        }
        panel.setHoverIndex(399);
        paint(panel, image);
    }

    @Test
    void aVeryShortHistoryStillPaints() {
        StockChartPanel panel = new StockChartPanel(StockChartData.from("NEW",
                StockChartTestBars.fromCloses(new double[]{4.0, 4.2, 4.1}), List.of()));
        panel.setSize(600, 400);
        paint(panel, new BufferedImage(600, 400, BufferedImage.TYPE_INT_ARGB));
        assertEquals(3, panel.visibleBars());
    }

    @Test
    void showsAMessageInsteadOfAChartWhenThereIsNoData() {
        StockChartView view = new StockChartView("NIO", "");
        view.showMessage("Connect to Alpaca to see this chart", "The chart is drawn from live daily prices.");

        assertNull(view.chart());
        assertTrue(labelTexts(view).contains("Connect to Alpaca to see this chart"));
    }

    @Test
    void theDaysChangeUsesThePricesOwnPrecision() {
        assertEquals("-$0.07", StockChartFormat.change(-0.0665, 3.74));
        assertEquals("+$1.25", StockChartFormat.change(1.25, 88.12));
        assertEquals("-$0.0123", StockChartFormat.change(-0.0123, 0.69), "sub-dollar stocks keep four decimals");
    }

    @Test
    void startsInALoadingState() {
        assertTrue(labelTexts(new StockChartView("NIO", "")).stream().anyMatch(text -> text.startsWith("Loading daily prices for NIO")));
    }

    private static StockChartData data() {
        return StockChartData.from("NIO", StockChartTestBars.fromCloses(StockChartTestBars.steady(9.5, 3.68, 400)), List.of(
                new StockChartLevels.Level(StockChartLevels.Kind.AVERAGE_COST, "Your avg cost", 4.71, "What you paid."),
                new StockChartLevels.Level(StockChartLevels.Kind.TARGET, "Target", 12.0, "Where it takes profit."),
                new StockChartLevels.Level(StockChartLevels.Kind.STOP_LOSS, "Stop loss", 3.40, "Where it cuts the loss.")));
    }

    private static void paint(StockChartPanel panel, BufferedImage image) {
        Graphics2D graphics = image.createGraphics();
        try {
            panel.paint(graphics);
        } finally {
            graphics.dispose();
        }
    }

    private static JToggleButton button(StockChartView view, String text) {
        return view.rangeButtons().stream().filter(button -> button.getText().equals(text)).findFirst().orElseThrow();
    }

    private static List<String> labelTexts(Container container) {
        List<String> texts = new ArrayList<>();
        for (Component child : container.getComponents()) {
            if (child instanceof JLabel label && label.getText() != null) {
                texts.add(label.getText());
            }
            if (child instanceof JTextComponent text && text.getText() != null) {
                texts.add(text.getText());
            }
            if (child instanceof Container nested) {
                texts.addAll(labelTexts(nested));
            }
        }
        return texts;
    }
}
