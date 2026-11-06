package com.neuralarc.service;

import com.neuralarc.analytics.EntryLevelCalculator;
import com.neuralarc.analytics.ScannerEntryPricing;
import com.neuralarc.api.AlpacaMarketDataApi;
import com.neuralarc.model.MarketBar;
import com.neuralarc.model.ScannerEntrySettings;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Applies the operator's {@link ScannerEntrySettings} to a scanner's planned entry, reading each stock's
 * daily history from the live market-data API once per scan.
 *
 * <p>When history cannot be read (no credentials, a broker error) the scanner's own entry is kept, so a
 * data outage never leaves a recommendation without a price.
 */
public final class MarketHistoryEntryPricing implements ScannerEntryPricing {
    private static final AtomicReference<ScannerEntrySettings> CURRENT =
            new AtomicReference<>(ScannerEntrySettings.defaults());
    /** Calendar days per trading session, with margin for weekends and holidays. */
    private static final double CALENDAR_DAYS_PER_SESSION = 1.5;
    private static final int HISTORY_MARGIN_DAYS = 10;

    private final AlpacaMarketDataApi marketDataApi;
    private final Supplier<ScannerEntrySettings> settings;
    private final Clock clock;
    private final Consumer<String> log;
    private final Map<String, List<MarketBar>> historyBySymbol = new ConcurrentHashMap<>();

    public MarketHistoryEntryPricing(AlpacaMarketDataApi marketDataApi, Supplier<ScannerEntrySettings> settings,
                                     Clock clock, Consumer<String> log) {
        this.marketDataApi = marketDataApi;
        this.settings = settings == null ? MarketHistoryEntryPricing::currentSettings : settings;
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
        this.log = log == null ? ignored -> { } : log;
    }

    /** The pricing a scan should use: the saved settings, read from {@code marketDataApi}. */
    public static ScannerEntryPricing forScan(AlpacaMarketDataApi marketDataApi, Consumer<String> log) {
        return new MarketHistoryEntryPricing(marketDataApi, null, null, log);
    }

    public static ScannerEntrySettings currentSettings() {
        return CURRENT.get();
    }

    /** Called when settings are loaded or saved so the next scan uses them. */
    public static void useSettings(ScannerEntrySettings settings) {
        CURRENT.set(settings == null ? ScannerEntrySettings.defaults() : settings);
    }

    @Override
    public BigDecimal entry(String symbol, BigDecimal ownEntry, BigDecimal current, BigDecimal open, BigDecimal todayLow) {
        if (ownEntry == null || ownEntry.signum() <= 0 || symbol == null || symbol.isBlank() || marketDataApi == null) {
            return ownEntry;
        }
        ScannerEntrySettings active = settings.get();
        List<MarketBar> history = history(symbol, active);
        if (history.isEmpty()) {
            return ownEntry;
        }
        BigDecimal level = EntryLevelCalculator.level(active, current, open, todayLow, history);
        if (level.signum() <= 0) {
            return ownEntry;
        }
        return level.min(ownEntry).setScale(2, RoundingMode.FLOOR);
    }

    private List<MarketBar> history(String symbol, ScannerEntrySettings active) {
        String key = symbol.trim().toUpperCase() + ":" + active.sessionsNeeded();
        return historyBySymbol.computeIfAbsent(key, ignored -> {
            try {
                LocalDate end = LocalDate.now(clock);
                int days = (int) Math.ceil(active.sessionsNeeded() * CALENDAR_DAYS_PER_SESSION) + HISTORY_MARGIN_DAYS;
                return List.copyOf(marketDataApi.getDailyBars(symbol.trim().toUpperCase(), end.minusDays(days), end));
            } catch (Exception ex) {
                log.accept("[Entry level] No price history for " + symbol + "; keeping the scanner's own entry ("
                        + ex.getMessage() + ").");
                return List.of();
            }
        });
    }
}
