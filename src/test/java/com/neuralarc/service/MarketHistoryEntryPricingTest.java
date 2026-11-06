package com.neuralarc.service;

import com.neuralarc.api.AlpacaMarketDataApi;
import com.neuralarc.api.AlpacaMarketDataException;
import com.neuralarc.model.MarketBar;
import com.neuralarc.model.ScannerEntrySettings;
import com.neuralarc.model.ScannerEntrySettings.Anchor;
import com.neuralarc.model.ScannerEntrySettings.Mode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketHistoryEntryPricingTest {
    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-06-15T18:00:00Z"), ZoneOffset.UTC);

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    private static MarketBar bar(int day, String low) {
        return new MarketBar("T", LocalDate.of(2026, 6, 1).plusDays(day).toString(), d(low), d("120"), d(low), d(low), BigDecimal.ONE);
    }

    private static final class FakeApi implements AlpacaMarketDataApi {
        final List<String> requested = new ArrayList<>();
        final List<LocalDate> starts = new ArrayList<>();
        List<MarketBar> bars = List.of(bar(0, "95"), bar(1, "96"), bar(2, "97"));
        boolean fail;

        @Override
        public List<MarketBar> getDailyBars(String symbol, LocalDate startDate, LocalDate endDate) throws AlpacaMarketDataException {
            requested.add(symbol);
            starts.add(startDate);
            if (fail) {
                throw new AlpacaMarketDataException("boom");
            }
            return bars;
        }

        @Override
        public List<MarketBar> getIntradayBars(String symbol, LocalDate startDate, LocalDate endDate, int intervalMinutes) {
            return List.of();
        }
    }

    private static MarketHistoryEntryPricing pricing(FakeApi api, ScannerEntrySettings settings, List<String> log) {
        return new MarketHistoryEntryPricing(api, () -> settings, FIXED, log::add);
    }

    @Test
    void theDefaultLowersTheEntryToTheTwoWeekLow() {
        FakeApi api = new FakeApi();

        BigDecimal entry = pricing(api, ScannerEntrySettings.defaults(), new ArrayList<>())
                .entry("AAPL", d("100"), d("100"), d("101"), d("100"));

        assertEquals(d("95.00"), entry);
    }

    @Test
    void aScannersDeeperOwnEntryIsNeverRaised() {
        BigDecimal entry = pricing(new FakeApi(), ScannerEntrySettings.defaults(), new ArrayList<>())
                .entry("AAPL", d("90"), d("100"), d("101"), d("100"));

        assertEquals(d("90.00"), entry);
    }

    @Test
    void customAppliesThePercentageToTheChosenLow() {
        ScannerEntrySettings custom = new ScannerEntrySettings(Mode.CUSTOM_PERCENT, Anchor.TWO_WEEKS, d("10"));

        BigDecimal entry = pricing(new FakeApi(), custom, new ArrayList<>()).entry("AAPL", d("100"), d("100"), d("100"), d("100"));

        assertEquals(d("100.00"), entry, "10% above 95 is 104.50, above the market, so the market caps it");
    }

    @Test
    void aFailedHistoryReadKeepsTheScannersOwnEntryAndSaysSo() {
        FakeApi api = new FakeApi();
        api.fail = true;
        List<String> log = new ArrayList<>();

        BigDecimal entry = pricing(api, ScannerEntrySettings.defaults(), log).entry("AAPL", d("98.40"), d("100"), d("100"), d("100"));

        assertEquals(d("98.40"), entry);
        assertTrue(log.stream().anyMatch(line -> line.contains("AAPL") && line.contains("own entry")), log.toString());
    }

    @Test
    void historyIsReadOncePerSymbolPerScan() {
        FakeApi api = new FakeApi();
        MarketHistoryEntryPricing pricing = pricing(api, ScannerEntrySettings.defaults(), new ArrayList<>());

        pricing.entry("AAPL", d("100"), d("100"), d("100"), d("100"));
        pricing.entry("AAPL", d("100"), d("100"), d("100"), d("100"));
        pricing.entry("MSFT", d("100"), d("100"), d("100"), d("100"));

        assertEquals(List.of("AAPL", "MSFT"), api.requested);
    }

    @Test
    void theHistoryReadReachesFarEnoughBackForTheChosenWindow() {
        FakeApi twoWeeks = new FakeApi();
        FakeApi year = new FakeApi();

        pricing(twoWeeks, ScannerEntrySettings.defaults(), new ArrayList<>()).entry("AAPL", d("100"), d("100"), d("100"), d("100"));
        pricing(year, new ScannerEntrySettings(Mode.AUTO_TREND, Anchor.TWO_WEEKS, BigDecimal.ZERO), new ArrayList<>())
                .entry("AAPL", d("100"), d("100"), d("100"), d("100"));

        LocalDate today = LocalDate.of(2026, 6, 15);
        assertTrue(twoWeeks.starts.get(0).isBefore(today.minusDays(14)) && twoWeeks.starts.get(0).isAfter(today.minusDays(60)));
        assertTrue(year.starts.get(0).isBefore(today.minusDays(365)), year.starts.get(0).toString());
    }

    @Test
    void thePlannedEntryIsKeptWhenThereIsNoPriceOrNoApi() {
        assertEquals(d("100"), pricing(new FakeApi(), ScannerEntrySettings.defaults(), new ArrayList<>())
                .entry(" ", d("100"), d("100"), d("100"), d("100")));
        assertEquals(d("100"), new MarketHistoryEntryPricing(null, null, FIXED, null)
                .entry("AAPL", d("100"), d("100"), d("100"), d("100")));
    }

    @Test
    void savedSettingsAreWhatAScanReads() {
        ScannerEntrySettings previous = MarketHistoryEntryPricing.currentSettings();
        try {
            ScannerEntrySettings auto = new ScannerEntrySettings(Mode.AUTO_TREND, Anchor.TWO_WEEKS, BigDecimal.ZERO);
            MarketHistoryEntryPricing.useSettings(auto);
            assertEquals(auto, MarketHistoryEntryPricing.currentSettings());
            MarketHistoryEntryPricing.useSettings(null);
            assertEquals(ScannerEntrySettings.defaults(), MarketHistoryEntryPricing.currentSettings());
        } finally {
            MarketHistoryEntryPricing.useSettings(previous);
        }
    }
}
