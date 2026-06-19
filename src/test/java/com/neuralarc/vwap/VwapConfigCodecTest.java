package com.neuralarc.vwap;

import com.neuralarc.model.StrategyMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VwapConfigCodecTest {
    @Test
    void roundtripPreservesAllFields() {
        VwapConfig original = new VwapConfig(new BigDecimal("2"), new BigDecimal("6"), 750_000L,
                new BigDecimal("10"), new BigDecimal("1.5"), new BigDecimal("250"),
                VwapConfig.TrendFilter.ABOVE_MA_200, new BigDecimal("3"), 7,
                VwapConfig.ExecutionFrequency.EVERY_15_MINUTES, StrategyMode.LIVE, List.of("NVDA", "TSLA"));

        VwapConfig restored = VwapConfigCodec.fromJson(VwapConfigCodec.toJson(original));

        assertEquals(new BigDecimal("2"), restored.minimumDiscountPercent());
        assertEquals(new BigDecimal("6"), restored.maximumDiscountPercent());
        assertEquals(750_000L, restored.minimumAverageVolume());
        assertEquals(new BigDecimal("10"), restored.minimumStockPrice());
        assertEquals(new BigDecimal("1.5"), restored.minimumRelativeVolume());
        assertEquals(new BigDecimal("250"), restored.maximumStockPrice());
        assertEquals(VwapConfig.TrendFilter.ABOVE_MA_200, restored.trendFilter());
        assertEquals(new BigDecimal("3"), restored.stopLossPercent());
        assertEquals(7, restored.maxStocksToAdd());
        assertEquals(VwapConfig.ExecutionFrequency.EVERY_15_MINUTES, restored.executionFrequency());
        assertEquals(StrategyMode.LIVE, restored.mode());
        assertEquals(List.of("NVDA", "TSLA"), restored.candidateSymbols());
    }

    @Test
    void nullMaximumPriceRoundtrips() {
        VwapConfig config = VwapConfig.defaults(StrategyMode.PAPER);
        assertNull(config.maximumStockPrice());
        VwapConfig restored = VwapConfigCodec.fromJson(VwapConfigCodec.toJson(config));
        assertNull(restored.maximumStockPrice());
    }

    @Test
    void fromJsonWithNullOrBlankReturnsDefaults() {
        VwapConfig fromNull = VwapConfigCodec.fromJson(null);
        VwapConfig fromBlank = VwapConfigCodec.fromJson("  ");
        VwapConfig defaults = VwapConfig.defaults(null);
        assertEquals(defaults.minimumDiscountPercent(), fromNull.minimumDiscountPercent());
        assertEquals(defaults.minimumRelativeVolume(), fromBlank.minimumRelativeVolume());
    }

    @Test
    void fromJsonWithMissingFieldsFallsBackToDefaults() {
        VwapConfig restored = VwapConfigCodec.fromJson("{}");
        VwapConfig defaults = VwapConfig.defaults(null);
        assertEquals(defaults.maximumDiscountPercent(), restored.maximumDiscountPercent());
        assertEquals(defaults.trendFilter(), restored.trendFilter());
        assertTrue(restored.candidateSymbols().isEmpty());
    }

    @Test
    void fromJsonWithCorruptValuesUsesDefaults() {
        String corrupt = "{\"minimumDiscountPercent\":\"not-a-number\",\"trendFilter\":\"INVALID\"}";
        VwapConfig restored = VwapConfigCodec.fromJson(corrupt);
        VwapConfig defaults = VwapConfig.defaults(null);
        assertEquals(defaults.minimumDiscountPercent(), restored.minimumDiscountPercent());
        assertEquals(defaults.trendFilter(), restored.trendFilter());
    }
}
