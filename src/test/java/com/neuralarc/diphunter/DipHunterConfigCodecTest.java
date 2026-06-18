package com.neuralarc.diphunter;

import com.neuralarc.model.StrategyMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DipHunterConfigCodecTest {
    @Test
    void roundtripPreservesAllFields() {
        DipHunterConfig original = new DipHunterConfig(new BigDecimal("4"), new BigDecimal("12"), 750_000L,
                new BigDecimal("10"), new BigDecimal("1.5"), new BigDecimal("250"),
                DipHunterConfig.TrendFilter.ABOVE_MA_50, DipHunterConfig.BounceConfirmation.NEAR_SUPPORT,
                new BigDecimal("4"), new BigDecimal("9"), 7, DipHunterConfig.ExecutionFrequency.EVERY_15_MINUTES,
                StrategyMode.LIVE, List.of("NVDA", "TSLA"));

        DipHunterConfig restored = DipHunterConfigCodec.fromJson(DipHunterConfigCodec.toJson(original));

        assertEquals(new BigDecimal("4"), restored.minimumPullbackPercent());
        assertEquals(new BigDecimal("12"), restored.maximumPullbackPercent());
        assertEquals(750_000L, restored.minimumAverageVolume());
        assertEquals(new BigDecimal("10"), restored.minimumStockPrice());
        assertEquals(new BigDecimal("1.5"), restored.minimumRelativeVolume());
        assertEquals(new BigDecimal("250"), restored.maximumStockPrice());
        assertEquals(DipHunterConfig.TrendFilter.ABOVE_MA_50, restored.trendFilter());
        assertEquals(DipHunterConfig.BounceConfirmation.NEAR_SUPPORT, restored.bounceConfirmation());
        assertEquals(new BigDecimal("4"), restored.stopLossPercent());
        assertEquals(new BigDecimal("9"), restored.takeProfitPercent());
        assertEquals(7, restored.maxStocksToAdd());
        assertEquals(DipHunterConfig.ExecutionFrequency.EVERY_15_MINUTES, restored.executionFrequency());
        assertEquals(StrategyMode.LIVE, restored.mode());
        assertEquals(List.of("NVDA", "TSLA"), restored.candidateSymbols());
    }

    @Test
    void nullMaximumPriceRoundtrips() {
        DipHunterConfig config = DipHunterConfig.defaults(StrategyMode.PAPER);
        assertNull(config.maximumStockPrice());
        DipHunterConfig restored = DipHunterConfigCodec.fromJson(DipHunterConfigCodec.toJson(config));
        assertNull(restored.maximumStockPrice());
    }

    @Test
    void fromJsonWithNullOrBlankReturnsDefaults() {
        DipHunterConfig fromNull = DipHunterConfigCodec.fromJson(null);
        DipHunterConfig fromBlank = DipHunterConfigCodec.fromJson("  ");
        DipHunterConfig defaults = DipHunterConfig.defaults(null);
        assertEquals(defaults.minimumPullbackPercent(), fromNull.minimumPullbackPercent());
        assertEquals(defaults.minimumRelativeVolume(), fromBlank.minimumRelativeVolume());
    }

    @Test
    void fromJsonWithMissingFieldsFallsBackToDefaults() {
        DipHunterConfig restored = DipHunterConfigCodec.fromJson("{}");
        DipHunterConfig defaults = DipHunterConfig.defaults(null);
        assertEquals(defaults.maximumPullbackPercent(), restored.maximumPullbackPercent());
        assertEquals(defaults.trendFilter(), restored.trendFilter());
        assertEquals(defaults.bounceConfirmation(), restored.bounceConfirmation());
        assertTrue(restored.candidateSymbols().isEmpty());
    }

    @Test
    void fromJsonWithCorruptValuesUsesDefaults() {
        String corrupt = "{\"minimumPullbackPercent\":\"not-a-number\",\"trendFilter\":\"INVALID\"}";
        DipHunterConfig restored = DipHunterConfigCodec.fromJson(corrupt);
        DipHunterConfig defaults = DipHunterConfig.defaults(null);
        assertEquals(defaults.minimumPullbackPercent(), restored.minimumPullbackPercent());
        assertEquals(defaults.trendFilter(), restored.trendFilter());
    }
}
