package com.neuralarc.ui.chart;

import com.neuralarc.model.PortfolioValueSample;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PortfolioValueChartTest {
    @Test
    void theHeadlineStatesTheValueAndASignedChangeSinceTheFirstReading() {
        PortfolioValueChart chart = new PortfolioValueChart(ZoneOffset.UTC);
        chart.setSamples(List.of(sample(0, "20000"), sample(5, "20312.10")), "Live · all workspaces");

        assertEquals("$20,312.10  +$312.10 (+1.56%) today", chart.headline());
    }

    @Test
    void aFallIsSpelledWithAMinusSignNotOnlyColoured() {
        assertEquals("-$150.00 (-1.50%)", PortfolioValueChart.change(new BigDecimal("10000"), new BigDecimal("9850")));
    }

    @Test
    void hoverPicksTheNearestMinute() {
        PortfolioValueChart chart = new PortfolioValueChart(ZoneOffset.UTC);
        chart.setSize(458, 180); // plot runs from x=58 to x=450
        chart.setSamples(List.of(sample(0, "1"), sample(10, "2"), sample(20, "3")), "");

        assertEquals(0, chart.indexAt(40));
        assertEquals(1, chart.indexAt(255));
        assertEquals(2, chart.indexAt(600));
    }

    @Test
    void axisLabelsStayShort() {
        assertEquals("$20.5K", PortfolioValueChart.compactMoney(20_512));
        assertEquals("$1.25M", PortfolioValueChart.compactMoney(1_250_000));
        assertEquals("$950", PortfolioValueChart.compactMoney(950));
    }

    @Test
    void paintsEmptySingleAndHoveredStatesWithoutFailing() {
        PortfolioValueChart chart = new PortfolioValueChart(ZoneOffset.UTC);
        chart.setSize(420, 180);
        BufferedImage image = new BufferedImage(420, 180, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            chart.paint(g);
            chart.setSamples(List.of(sample(0, "1000")), "Paper");
            chart.paint(g);
            chart.setSamples(List.of(sample(0, "1000"), sample(1, "1000")), "Paper"); // flat line
            chart.paint(g);
        } finally {
            g.dispose();
        }
    }

    private static PortfolioValueSample sample(int minute, String value) {
        return new PortfolioValueSample(Instant.parse("2026-09-18T14:30:00Z").plusSeconds(minute * 60L),
                new BigDecimal(value), new BigDecimal("19000"));
    }
}
