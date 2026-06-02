package com.neuralarc.ui;

import com.neuralarc.model.MarketBar;
import com.neuralarc.model.StrategyRecommendation;
import com.neuralarc.service.RecommendationEngine;
import com.neuralarc.service.TechnicalIndicatorService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LuckyTrendingStocksDialogTest {

    @Test
    void diversifiedTop20SymbolsMatchCuratedUniverse() {
        List<String> symbols = LuckyTrendingStocksDialog.diversifiedTop20Symbols();

        assertEquals(20, symbols.size());
        assertEquals(20, symbols.stream().distinct().count());
        assertTrue(symbols.contains("MSFT"));
        assertTrue(symbols.contains("AAPL"));
        assertTrue(symbols.contains("NVDA"));
        assertTrue(symbols.contains("AMZN"));
        assertTrue(symbols.contains("GOOGL"));
        assertTrue(symbols.contains("META"));
        assertTrue(symbols.contains("AVGO"));
        assertTrue(symbols.contains("ORCL"));
        assertTrue(symbols.contains("BRK.B"));
        assertTrue(symbols.contains("JPM"));
        assertTrue(symbols.contains("V"));
        assertTrue(symbols.contains("MA"));
        assertTrue(symbols.contains("JNJ"));
        assertTrue(symbols.contains("UNH"));
        assertTrue(symbols.contains("LLY"));
        assertTrue(symbols.contains("TSLA"));
        assertTrue(symbols.contains("WMT"));
        assertTrue(symbols.contains("PG"));
        assertTrue(symbols.contains("XOM"));
        assertTrue(symbols.contains("CAT"));
    }

    @Test
    void baseLimitBuyDisplayUsesClampedRecommendationValue() {
        RecommendationEngine engine = new RecommendationEngine(new TechnicalIndicatorService());
        List<MarketBar> bars = shortTermBreakoutBars(new BigDecimal("1800.00"));

        StrategyRecommendation recommendation = engine.generateShortTermRecommendation(
                "AAPL",
                bars,
                new BigDecimal("218.00"),
                new BigDecimal("218.00")
        );

        assertEquals(new BigDecimal("208.00"), recommendation.baseBuyPrice());
        assertEquals("$208.00", LuckyTrendingStocksDialog.baseLimitBuyDisplay(recommendation));
    }

    private List<MarketBar> shortTermBreakoutBars(BigDecimal lastVolume) {
        List<MarketBar> bars = new ArrayList<>();
        LocalDate start = LocalDate.of(2026, 2, 1);
        for (int i = 0; i < 20; i++) {
            BigDecimal close = i == 19 ? new BigDecimal("214.00") : new BigDecimal("210.00");
            BigDecimal open = close.subtract(BigDecimal.ONE);
            BigDecimal low = close.subtract(new BigDecimal("2.00"));
            BigDecimal high = new BigDecimal("216.00");
            BigDecimal volume = i == 19 ? lastVolume : new BigDecimal("1000.00");
            bars.add(new MarketBar("AAPL", start.plusDays(i) + "T00:00:00Z", open, high, low, close, volume));
        }
        return bars;
    }
}

