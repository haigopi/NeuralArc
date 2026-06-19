package com.neuralarc.swing;

import com.neuralarc.model.StrategyMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SwingConfigCodecTest {
    @Test
    void roundtripPreservesAllFields() {
        SwingConfig original = new SwingConfig(new BigDecimal("2"), new BigDecimal("10"), 750_000L,
                new BigDecimal("10"), new BigDecimal("1.2"), new BigDecimal("250"),
                SwingConfig.TrendFilter.STACKED_UPTREND, new BigDecimal("5"), new BigDecimal("14"), 7,
                SwingConfig.ExecutionFrequency.ONCE_PER_DAY, StrategyMode.LIVE, List.of("NVDA", "TSLA"));

        SwingConfig restored = SwingConfigCodec.fromJson(SwingConfigCodec.toJson(original));

        assertEquals(new BigDecimal("2"), restored.minimumPullbackPercent());
        assertEquals(new BigDecimal("10"), restored.maximumPullbackPercent());
        assertEquals(750_000L, restored.minimumAverageVolume());
        assertEquals(new BigDecimal("10"), restored.minimumStockPrice());
        assertEquals(new BigDecimal("1.2"), restored.minimumRelativeVolume());
        assertEquals(new BigDecimal("250"), restored.maximumStockPrice());
        assertEquals(SwingConfig.TrendFilter.STACKED_UPTREND, restored.trendFilter());
        assertEquals(new BigDecimal("5"), restored.stopLossPercent());
        assertEquals(new BigDecimal("14"), restored.targetProfitPercent());
        assertEquals(7, restored.maxStocksToAdd());
        assertEquals(SwingConfig.ExecutionFrequency.ONCE_PER_DAY, restored.executionFrequency());
        assertEquals(StrategyMode.LIVE, restored.mode());
        assertEquals(List.of("NVDA", "TSLA"), restored.candidateSymbols());
    }

    @Test
    void nullMaximumPriceRoundtrips() {
        SwingConfig config = SwingConfig.defaults(StrategyMode.PAPER);
        assertNull(config.maximumStockPrice());
        SwingConfig restored = SwingConfigCodec.fromJson(SwingConfigCodec.toJson(config));
        assertNull(restored.maximumStockPrice());
    }

    @Test
    void fromJsonWithNullOrBlankReturnsDefaults() {
        SwingConfig fromNull = SwingConfigCodec.fromJson(null);
        SwingConfig fromBlank = SwingConfigCodec.fromJson("  ");
        SwingConfig defaults = SwingConfig.defaults(null);
        assertEquals(defaults.minimumPullbackPercent(), fromNull.minimumPullbackPercent());
        assertEquals(defaults.minimumRelativeVolume(), fromBlank.minimumRelativeVolume());
    }

    @Test
    void fromJsonWithMissingFieldsFallsBackToDefaults() {
        SwingConfig restored = SwingConfigCodec.fromJson("{}");
        SwingConfig defaults = SwingConfig.defaults(null);
        assertEquals(defaults.maximumPullbackPercent(), restored.maximumPullbackPercent());
        assertEquals(defaults.trendFilter(), restored.trendFilter());
        assertEquals(defaults.targetProfitPercent(), restored.targetProfitPercent());
        assertTrue(restored.candidateSymbols().isEmpty());
    }

    @Test
    void fromJsonWithCorruptValuesUsesDefaults() {
        String corrupt = "{\"minimumPullbackPercent\":\"not-a-number\",\"trendFilter\":\"INVALID\"}";
        SwingConfig restored = SwingConfigCodec.fromJson(corrupt);
        SwingConfig defaults = SwingConfig.defaults(null);
        assertEquals(defaults.minimumPullbackPercent(), restored.minimumPullbackPercent());
        assertEquals(defaults.trendFilter(), restored.trendFilter());
    }
}
