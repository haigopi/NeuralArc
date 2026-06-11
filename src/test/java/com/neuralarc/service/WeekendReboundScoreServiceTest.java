package com.neuralarc.service;

import com.neuralarc.model.MarketBar;
import com.neuralarc.model.TrendingStock;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeekendReboundScoreServiceTest {
    @Test
    void scoresQualifiedWeekendReboundCandidateWithReadableReason() {
        WeekendReboundScoreService service = new WeekendReboundScoreService();
        TrendingStock candidate = candidate("AAPL", "-6.50", "125.00");

        var result = service.score(candidate, qualifiedBars());

        assertTrue(result.isPresent());
        TrendingStock scored = result.get();
        assertEquals("AAPL", scored.symbol());
        assertTrue(scored.trendingScore().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(scored.reason().contains("Weekend Rebound"));
        assertTrue(scored.reason().contains("suggested entry"));
        assertTrue(scored.reason().contains("max 5% allocation"));
    }

    @Test
    void rejectsCandidatesWithInsufficientAverageVolume() {
        WeekendReboundScoreService service = new WeekendReboundScoreService();
        List<MarketBar> bars = qualifiedBars().stream()
                .map(bar -> new MarketBar(bar.symbol(), bar.timestamp(), bar.open(), bar.high(), bar.low(), bar.close(), new BigDecimal("1000000")))
                .toList();

        assertTrue(service.score(candidate("AAPL", "-6.50", "125.00"), bars).isEmpty());
    }

    @Test
    void rejectsCatastrophicDeclines() {
        WeekendReboundScoreService service = new WeekendReboundScoreService();

        assertTrue(service.score(candidate("AAPL", "-18.00", "125.00"), qualifiedBars()).isEmpty());
    }

    @Test
    void scoresMilderWatchlistDeclineWithLowerConfidenceContext() {
        WeekendReboundScoreService service = new WeekendReboundScoreService();

        var result = service.score(candidate("AAPL", "-1.50", "125.00"), qualifiedBars());

        assertTrue(result.isPresent());
        assertTrue(result.get().reason().contains("watchlist-level current selloff"));
    }

    private TrendingStock candidate(String symbol, String decline, String price) {
        return new TrendingStock(
                symbol,
                "Apple Inc.",
                new BigDecimal(price),
                new BigDecimal(decline),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "",
                BigDecimal.ZERO
        );
    }

    private List<MarketBar> qualifiedBars() {
        List<MarketBar> bars = new ArrayList<>();
        LocalDate date = LocalDate.of(2026, 1, 2);
        BigDecimal close = new BigDecimal("140.00");
        while (bars.size() < 45) {
            if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
                date = date.plusDays(1);
                continue;
            }
            BigDecimal open = close.add(new BigDecimal("1.00"));
            BigDecimal low = close.subtract(new BigDecimal("2.00"));
            BigDecimal high = close.add(new BigDecimal("2.00"));
            BigDecimal volume = new BigDecimal("2500000");
            if (bars.size() == 20) {
                low = new BigDecimal("115.00");
            }
            if (bars.size() == 44) {
                open = new BigDecimal("133.00");
                close = new BigDecimal("125.00");
                low = new BigDecimal("123.00");
                high = new BigDecimal("134.00");
                volume = new BigDecimal("5000000");
            }
            bars.add(new MarketBar("AAPL", date + "T21:00:00Z", open, high, low, close, volume));
            close = close.subtract(new BigDecimal("0.25"));
            date = date.plusDays(1);
        }
        return bars;
    }
}
