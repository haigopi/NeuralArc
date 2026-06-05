package com.neuralarc.ui;

import com.neuralarc.model.MarketBar;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StockPriceTooltipSnapshotTest {
    @Test
    void fromBarsUsesFirstOpenHighLowAndLatestClose() {
        StockPriceTooltipSnapshot snapshot = StockPriceTooltipSnapshot.fromBars("AAPL", List.of(
                bar("100.00", "104.00", "99.00", "103.00"),
                bar("103.00", "106.00", "101.00", "105.50"),
                bar("105.50", "105.75", "98.50", "99.25")
        ), BigDecimal.ZERO);

        assertEquals(new BigDecimal("100.00"), snapshot.open());
        assertEquals(new BigDecimal("106.00"), snapshot.highSoFar());
        assertEquals(new BigDecimal("98.50"), snapshot.lowSoFar());
        assertEquals(new BigDecimal("99.25"), snapshot.current());
        assertTrue(snapshot.tooltipText().contains("Open: $100.00"));
        assertTrue(snapshot.tooltipText().contains("High so far: $106.00"));
        assertTrue(snapshot.tooltipText().contains("Low so far: $98.50"));
        assertTrue(snapshot.tooltipText().contains("Current: $99.25"));
    }

    @Test
    void fromBarsUsesFallbackCurrentWhenBarsAreMissing() {
        StockPriceTooltipSnapshot snapshot = StockPriceTooltipSnapshot.fromBars("AAPL", List.of(), new BigDecimal("101.25"));

        assertEquals(BigDecimal.ZERO, snapshot.open());
        assertEquals(BigDecimal.ZERO, snapshot.highSoFar());
        assertEquals(BigDecimal.ZERO, snapshot.lowSoFar());
        assertEquals(new BigDecimal("101.25"), snapshot.current());
        assertTrue(snapshot.tooltipText().contains("Open: -"));
        assertTrue(snapshot.tooltipText().contains("Current: $101.25"));
    }

    private static MarketBar bar(String open, String high, String low, String close) {
        return new MarketBar("AAPL", "", new BigDecimal(open), new BigDecimal(high), new BigDecimal(low), new BigDecimal(close), BigDecimal.ZERO);
    }
}
