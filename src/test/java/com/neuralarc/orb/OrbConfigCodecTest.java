package com.neuralarc.orb;

import com.neuralarc.model.StrategyMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrbConfigCodecTest {
    @Test
    void roundtripPreservesAllFields() {
        OrbConfig original = new OrbConfig(5, new BigDecimal("0.15"), OrbConfig.StopMode.MID_RANGE,
                new BigDecimal("2.00"), new BigDecimal("6.00"), 5, new BigDecimal("5.00"),
                new BigDecimal("200.00"), new BigDecimal("2.50"), new BigDecimal("0.50"),
                LocalTime.of(10, 30), List.of("NVDA", "TSLA"), true, false, StrategyMode.LIVE);

        OrbConfig restored = OrbConfigCodec.fromJson(OrbConfigCodec.toJson(original));

        assertEquals(5, restored.rangeDurationMinutes());
        assertEquals(new BigDecimal("0.15"), restored.entryBufferPercent());
        assertEquals(OrbConfig.StopMode.MID_RANGE, restored.stopMode());
        assertEquals(new BigDecimal("2.00"), restored.riskPercent());
        assertEquals(new BigDecimal("6.00"), restored.takeProfitPercent());
        assertEquals(5, restored.maxStocksToAdd());
        assertEquals(new BigDecimal("5.00"), restored.minimumPrice());
        assertEquals(new BigDecimal("200.00"), restored.maximumPrice());
        assertEquals(new BigDecimal("2.50"), restored.minimumRelativeVolume());
        assertEquals(new BigDecimal("0.50"), restored.minimumRangePercent());
        assertEquals(LocalTime.of(10, 30), restored.latestEntryTimeEt());
        assertEquals(List.of("NVDA", "TSLA"), restored.candidateSymbols());
        assertTrue(restored.autoDiscoverEnabled());
        assertFalse(restored.scheduleEnabled());
        assertEquals(StrategyMode.LIVE, restored.mode());
    }

    @Test
    void nullMaximumPriceRoundtrips() {
        OrbConfig config = OrbConfig.defaults(StrategyMode.PAPER);
        assertNull(config.maximumPrice());
        OrbConfig restored = OrbConfigCodec.fromJson(OrbConfigCodec.toJson(config));
        assertNull(restored.maximumPrice());
    }

    @Test
    void fromJsonWithNullOrBlankReturnsDefaults() {
        OrbConfig fromNull = OrbConfigCodec.fromJson(null);
        OrbConfig fromBlank = OrbConfigCodec.fromJson("  ");
        OrbConfig defaults = OrbConfig.defaults(null);

        assertEquals(defaults.rangeDurationMinutes(), fromNull.rangeDurationMinutes());
        assertEquals(defaults.minimumRelativeVolume(), fromBlank.minimumRelativeVolume());
    }

    @Test
    void fromJsonWithMissingFieldsFallsBackToDefaults() {
        String sparse = "{}";
        OrbConfig restored = OrbConfigCodec.fromJson(sparse);
        OrbConfig defaults = OrbConfig.defaults(null);

        assertEquals(defaults.rangeDurationMinutes(), restored.rangeDurationMinutes());
        assertEquals(defaults.stopMode(), restored.stopMode());
        assertEquals(defaults.latestEntryTimeEt(), restored.latestEntryTimeEt());
        assertEquals(defaults.minimumRangePercent(), restored.minimumRangePercent());
        assertTrue(restored.candidateSymbols().isEmpty());
    }

    @Test
    void fromJsonWithCorruptValuesUsesDefaults() {
        String corrupt = "{\"rangeDurationMinutes\":99,\"entryBufferPercent\":\"not-a-number\",\"stopMode\":\"INVALID\"}";
        OrbConfig restored = OrbConfigCodec.fromJson(corrupt);
        OrbConfig defaults = OrbConfig.defaults(null);

        assertEquals(defaults.rangeDurationMinutes(), restored.rangeDurationMinutes());
        assertEquals(defaults.entryBufferPercent(), restored.entryBufferPercent());
        assertEquals(defaults.stopMode(), restored.stopMode());
    }
}
