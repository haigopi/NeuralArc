package com.neuralarc.orb;

import com.neuralarc.api.AlpacaMarketDataApi;
import com.neuralarc.model.MarketBar;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OpeningRangeCaptureServiceTest {
    @Test
    void computesHighLowVolumeFromOpeningRangeBars() throws Exception {
        FakeMarketData data = new FakeMarketData(List.of(
                bar("AAPL", "2026-06-15T13:30:00Z", "100", "101", "99", "100.50", "1000"),
                bar("AAPL", "2026-06-15T13:31:00Z", "100.5", "103", "100", "102", "1500"),
                bar("AAPL", "2026-06-15T13:45:00Z", "102", "110", "101", "105", "9999")
        ));
        OpeningRangeSnapshot snapshot = new OpeningRangeCaptureService(data)
                .capture("aapl", LocalDate.of(2026, 6, 15), OrbConfig.defaults(null));

        assertTrue(snapshot.complete());
        assertEquals("AAPL", snapshot.symbol());
        assertEquals(new BigDecimal("103"), snapshot.high());
        assertEquals(new BigDecimal("99"), snapshot.low());
        assertEquals(new BigDecimal("2500"), snapshot.volume());
        assertEquals(2, snapshot.barCount());
    }

    @Test
    void rejectsMissingBarsWithoutThrowing() throws Exception {
        OpeningRangeSnapshot snapshot = new OpeningRangeCaptureService(new FakeMarketData(List.of()))
                .capture("MSFT", LocalDate.of(2026, 6, 15), OrbConfig.defaults(null));
        assertFalse(snapshot.complete());
        assertEquals("missing opening-range bars", snapshot.rejectionReason());
    }

    private static MarketBar bar(String symbol, String ts, String o, String h, String l, String c, String v) {
        return new MarketBar(symbol, ts, new BigDecimal(o), new BigDecimal(h), new BigDecimal(l), new BigDecimal(c), new BigDecimal(v));
    }

    private record FakeMarketData(List<MarketBar> bars) implements AlpacaMarketDataApi {
        @Override public List<MarketBar> getDailyBars(String symbol, LocalDate startDate, LocalDate endDate) { return List.of(); }
        @Override public List<MarketBar> getIntradayBars(String symbol, LocalDate startDate, LocalDate endDate, int intervalMinutes) { return bars; }
    }
}
