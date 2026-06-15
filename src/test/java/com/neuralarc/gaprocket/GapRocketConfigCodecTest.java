package com.neuralarc.gaprocket;

import com.neuralarc.model.StrategyMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GapRocketConfigCodecTest {

    @Test
    void roundTripsAllFields() {
        GapRocketConfig original = new GapRocketConfig(
                new BigDecimal("6.5"), 2_500_000L, new BigDecimal("7"), new BigDecimal("3"),
                new BigDecimal("400"), true, EnumSet.of(GapRocketConfig.CatalystType.EARNINGS, GapRocketConfig.CatalystType.FDA_BIOTECH),
                GapRocketConfig.MarketTrendFilter.SPY_GREEN, GapRocketConfig.EntryStyle.OPENING_RANGE_BREAKOUT,
                GapRocketConfig.OpeningRangeDuration.THIRTY_MINUTES, new BigDecimal("5"), new BigDecimal("10"),
                7, GapRocketConfig.ExecutionFrequency.EVERY_15_MINUTES, StrategyMode.LIVE, List.of("NVDA", "AMD"));

        GapRocketConfig restored = GapRocketConfigCodec.fromJson(GapRocketConfigCodec.toJson(original));

        assertEquals(0, original.minimumPremarketGapPercent().compareTo(restored.minimumPremarketGapPercent()));
        assertEquals(original.minimumPremarketVolume(), restored.minimumPremarketVolume());
        assertEquals(0, original.minimumStockPrice().compareTo(restored.minimumStockPrice()));
        assertEquals(0, original.minimumRelativeVolume().compareTo(restored.minimumRelativeVolume()));
        assertEquals(0, original.maximumStockPrice().compareTo(restored.maximumStockPrice()));
        assertTrue(restored.newsCatalystRequired());
        assertEquals(EnumSet.of(GapRocketConfig.CatalystType.EARNINGS, GapRocketConfig.CatalystType.FDA_BIOTECH),
                restored.catalystTypes());
        assertEquals(GapRocketConfig.MarketTrendFilter.SPY_GREEN, restored.marketTrendFilter());
        assertEquals(GapRocketConfig.EntryStyle.OPENING_RANGE_BREAKOUT, restored.entryStyle());
        assertEquals(GapRocketConfig.OpeningRangeDuration.THIRTY_MINUTES, restored.openingRangeDuration());
        assertEquals(0, original.stopLossPercent().compareTo(restored.stopLossPercent()));
        assertEquals(0, original.takeProfitPercent().compareTo(restored.takeProfitPercent()));
        assertEquals(7, restored.maxStocksToAdd());
        assertEquals(GapRocketConfig.ExecutionFrequency.EVERY_15_MINUTES, restored.executionFrequency());
        assertEquals(StrategyMode.LIVE, restored.mode());
        assertEquals(List.of("NVDA", "AMD"), restored.candidateSymbols());
    }

    @Test
    void preservesNullMaximumStockPrice() {
        GapRocketConfig original = GapRocketConfig.defaults(StrategyMode.PAPER);
        assertNull(original.maximumStockPrice());

        GapRocketConfig restored = GapRocketConfigCodec.fromJson(GapRocketConfigCodec.toJson(original));

        assertNull(restored.maximumStockPrice());
    }

    @Test
    void fromBlankOrNullReturnsDefaults() {
        assertEquals(GapRocketConfig.defaults(null).maxStocksToAdd(),
                GapRocketConfigCodec.fromJson(null).maxStocksToAdd());
        assertEquals(GapRocketConfig.defaults(null).maxStocksToAdd(),
                GapRocketConfigCodec.fromJson("  ").maxStocksToAdd());
    }

    @Test
    void toleratesUnknownEnumValuesByFallingBackToDefaults() {
        String json = "{\"marketTrendFilter\":\"BOGUS\",\"entryStyle\":\"WHATEVER\",\"mode\":\"\"}";
        GapRocketConfig restored = GapRocketConfigCodec.fromJson(json);

        assertEquals(GapRocketConfig.defaults(null).marketTrendFilter(), restored.marketTrendFilter());
        assertEquals(GapRocketConfig.defaults(null).entryStyle(), restored.entryStyle());
        assertEquals(GapRocketConfig.defaults(null).mode(), restored.mode());
    }
}
