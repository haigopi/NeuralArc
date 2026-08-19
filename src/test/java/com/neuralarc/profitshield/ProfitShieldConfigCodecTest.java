package com.neuralarc.profitshield;

import com.neuralarc.model.StrategyMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfitShieldConfigCodecTest {

    @Test
    void roundTripsEveryField() {
        ProfitShieldConfig original = new ProfitShieldConfig(90, new BigDecimal("2.5"), new BigDecimal("15"),
                new BigDecimal("8"), 750_000L, new BigDecimal("20"), new BigDecimal("500"),
                ProfitShieldConfig.TrendFilter.ABOVE_MA_50, new BigDecimal("0.5"), new BigDecimal("2"),
                new BigDecimal("4"), 6, StrategyMode.LIVE, List.of("MSFT", "KO"));

        ProfitShieldConfig loaded = ProfitShieldConfigCodec.fromJson(ProfitShieldConfigCodec.toJson(original));

        assertEquals(90, loaded.drawdownLookbackSessions());
        assertEquals(new BigDecimal("2.5"), loaded.maximumDailyVolatilityPercent());
        assertEquals(new BigDecimal("15"), loaded.maximumDrawdownPercent());
        assertEquals(new BigDecimal("8"), loaded.maximumDistanceFromHighPercent());
        assertEquals(750_000L, loaded.minimumAverageVolume());
        assertEquals(new BigDecimal("20"), loaded.minimumStockPrice());
        assertEquals(new BigDecimal("500"), loaded.maximumStockPrice());
        assertEquals(ProfitShieldConfig.TrendFilter.ABOVE_MA_50, loaded.trendFilter());
        assertEquals(new BigDecimal("0.5"), loaded.entryDiscountPercent());
        assertEquals(new BigDecimal("2"), loaded.protectiveStopPercent());
        assertEquals(new BigDecimal("4"), loaded.targetProfitPercent());
        assertEquals(6, loaded.maxStocksToAdd());
        assertEquals(StrategyMode.LIVE, loaded.mode());
        assertEquals(List.of("MSFT", "KO"), loaded.candidateSymbols());
    }

    @Test
    void keepsAnAbsentMaximumPriceNull() {
        ProfitShieldConfig loaded = ProfitShieldConfigCodec.fromJson(
                ProfitShieldConfigCodec.toJson(ProfitShieldConfig.defaults(StrategyMode.PAPER)));

        assertNull(loaded.maximumStockPrice());
    }

    @Test
    void fallsBackToDefaultsForBlankOrMalformedJson() {
        ProfitShieldConfig defaults = ProfitShieldConfig.defaults(null);

        assertEquals(defaults, ProfitShieldConfigCodec.fromJson(null));
        assertEquals(defaults, ProfitShieldConfigCodec.fromJson("  "));
    }

    @Test
    void fallsBackToDefaultsForUnknownEnumsAndUnreadableNumbers() {
        String json = "{\"trendFilter\":\"NOT_A_FILTER\",\"mode\":\"NOT_A_MODE\","
                + "\"maximumDrawdownPercent\":\"abc\",\"candidateSymbols\":[\"  \",\"aapl\"]}";

        ProfitShieldConfig loaded = ProfitShieldConfigCodec.fromJson(json);

        assertEquals(ProfitShieldConfig.TrendFilter.ABOVE_MA_50_AND_200, loaded.trendFilter());
        assertEquals(StrategyMode.PAPER, loaded.mode());
        assertEquals(new BigDecimal("20"), loaded.maximumDrawdownPercent());
        assertEquals(List.of("aapl"), loaded.candidateSymbols(), "blank symbols are dropped");
    }

    @Test
    void normalisesInvalidValuesToDocumentedDefaults() {
        ProfitShieldConfig broken = new ProfitShieldConfig(0, BigDecimal.ZERO, new BigDecimal("-5"),
                null, -1L, BigDecimal.ZERO, null, null, new BigDecimal("-1"), BigDecimal.ZERO,
                new BigDecimal("-2"), 0, null, null);

        assertEquals(126, broken.drawdownLookbackSessions());
        assertEquals(new BigDecimal("3"), broken.maximumDailyVolatilityPercent());
        assertEquals(new BigDecimal("20"), broken.maximumDrawdownPercent());
        assertEquals(new BigDecimal("12"), broken.maximumDistanceFromHighPercent());
        assertEquals(300_000L, broken.minimumAverageVolume());
        assertEquals(new BigDecimal("5"), broken.minimumStockPrice());
        assertEquals(ProfitShieldConfig.TrendFilter.ABOVE_MA_50_AND_200, broken.trendFilter());
        assertEquals(new BigDecimal("1"), broken.entryDiscountPercent());
        assertEquals(new BigDecimal("3"), broken.protectiveStopPercent());
        assertEquals(new BigDecimal("6"), broken.targetProfitPercent());
        assertEquals(10, broken.maxStocksToAdd());
        assertEquals(StrategyMode.PAPER, broken.mode());
        assertNotNull(broken.candidateSymbols());
        assertTrue(broken.candidateSymbols().isEmpty());
    }

    @Test
    void clampsTheLookbackWindowToASensibleRange() {
        assertEquals(20, lookback(5), "too short a window cannot measure a drawdown");
        assertEquals(252, lookback(9_999), "capped at roughly one trading year");
        assertEquals(126, lookback(0), "zero falls back to the six-month default");
        assertEquals(90, lookback(90));
    }

    private static int lookback(int requested) {
        return new ProfitShieldConfig(requested, null, null, null, 0, null, null, null, null, null, null,
                0, null, null).drawdownLookbackSessions();
    }
}
