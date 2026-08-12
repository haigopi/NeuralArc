package com.neuralarc.rangerider;

import com.neuralarc.model.StrategyMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RangeRiderConfigCodecTest {

    @Test
    void roundTripsEveryField() {
        RangeRiderConfig original = new RangeRiderConfig(20, new BigDecimal("3"), new BigDecimal("9"),
                new BigDecimal("70"), 2_000_000L, new BigDecimal("15"), new BigDecimal("400"),
                new BigDecimal("65"), new BigDecimal("0.8"), new BigDecimal("1.5"), 7,
                RangeRiderConfig.ExecutionFrequency.EVERY_30_MINUTES, StrategyMode.LIVE, List.of("AAPL", "MSFT"));

        RangeRiderConfig restored = RangeRiderConfigCodec.fromJson(RangeRiderConfigCodec.toJson(original));

        assertEquals(20, restored.lookbackSessions());
        assertEquals(new BigDecimal("3"), restored.minimumAverageRangePercent());
        assertEquals(new BigDecimal("9"), restored.maximumAverageRangePercent());
        assertEquals(new BigDecimal("70"), restored.minimumSameDayFillRatePercent());
        assertEquals(2_000_000L, restored.minimumAverageVolume());
        assertEquals(new BigDecimal("15"), restored.minimumStockPrice());
        assertEquals(new BigDecimal("400"), restored.maximumStockPrice());
        assertEquals(new BigDecimal("65"), restored.targetCapturePercent());
        assertEquals(new BigDecimal("0.8"), restored.minimumExpectedGainPercent());
        assertEquals(new BigDecimal("1.5"), restored.stopLossPercent());
        assertEquals(7, restored.maxStocksToAdd());
        assertEquals(RangeRiderConfig.ExecutionFrequency.EVERY_30_MINUTES, restored.executionFrequency());
        assertEquals(StrategyMode.LIVE, restored.mode());
        assertEquals(List.of("AAPL", "MSFT"), restored.candidateSymbols());
    }

    @Test
    void nullMaximumPriceSurvivesTheRoundTrip() {
        RangeRiderConfig original = RangeRiderConfig.defaults(StrategyMode.PAPER);

        RangeRiderConfig restored = RangeRiderConfigCodec.fromJson(RangeRiderConfigCodec.toJson(original));

        assertNull(restored.maximumStockPrice());
        assertTrue(restored.candidateSymbols().isEmpty());
    }

    @Test
    void blankAndMalformedJsonFallBackToDefaults() {
        RangeRiderConfig defaults = RangeRiderConfig.defaults(null);

        assertEquals(defaults.lookbackSessions(), RangeRiderConfigCodec.fromJson(null).lookbackSessions());
        assertEquals(defaults.lookbackSessions(), RangeRiderConfigCodec.fromJson("  ").lookbackSessions());

        RangeRiderConfig partial = RangeRiderConfigCodec.fromJson(
                "{\"minimumAverageRangePercent\":\"not-a-number\",\"executionFrequency\":\"NOPE\"}");
        assertEquals(defaults.minimumAverageRangePercent(), partial.minimumAverageRangePercent());
        assertEquals(defaults.executionFrequency(), partial.executionFrequency());
        assertEquals(defaults.minimumSameDayFillRatePercent(), partial.minimumSameDayFillRatePercent());
    }

    @Test
    void nullConfigSerializesAsDefaults() {
        RangeRiderConfig restored = RangeRiderConfigCodec.fromJson(RangeRiderConfigCodec.toJson(null));

        assertEquals(RangeRiderConfig.DEFAULT_LOOKBACK_SESSIONS, restored.lookbackSessions());
        assertEquals(StrategyMode.PAPER, restored.mode());
    }
}
