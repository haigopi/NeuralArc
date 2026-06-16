package com.neuralarc.orb;

import com.neuralarc.api.AlpacaMarketDataApi;
import com.neuralarc.api.AlpacaMarketDataException;
import com.neuralarc.model.MarketBar;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;

public final class OpeningRangeCaptureService {
    public static final ZoneId EASTERN = ZoneId.of("America/New_York");
    private static final LocalTime REGULAR_OPEN_ET = LocalTime.of(9, 30);
    private final AlpacaMarketDataApi marketDataApi;

    public OpeningRangeCaptureService(AlpacaMarketDataApi marketDataApi) {
        this.marketDataApi = marketDataApi;
    }

    public OpeningRangeSnapshot capture(String symbol, LocalDate sessionDate, OrbConfig config) throws AlpacaMarketDataException {
        OrbConfig safeConfig = config == null ? OrbConfig.defaults(null) : config;
        String normalized = symbol == null ? "" : symbol.trim().toUpperCase();
        if (normalized.isBlank()) {
            return rejected("", sessionDate, safeConfig, "missing symbol");
        }
        LocalDate date = sessionDate == null ? LocalDate.now(EASTERN) : sessionDate;
        Instant start = ZonedDateTime.of(date, REGULAR_OPEN_ET, EASTERN).toInstant();
        Instant end = ZonedDateTime.of(date, REGULAR_OPEN_ET.plusMinutes(safeConfig.rangeDurationMinutes()), EASTERN).toInstant();
        List<MarketBar> openingBars = marketDataApi.getIntradayBars(normalized, date, date, 1).stream()
                .filter(bar -> inRange(bar, start, end))
                .sorted(Comparator.comparing(this::timestamp))
                .toList();
        if (openingBars.isEmpty()) {
            return new OpeningRangeSnapshot(normalized, start, end, null, null, BigDecimal.ZERO, 0, false, "missing opening-range bars");
        }
        BigDecimal high = openingBars.stream().map(MarketBar::high).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal low = openingBars.stream().map(MarketBar::low).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal volume = openingBars.stream().map(MarketBar::volume).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (high.compareTo(BigDecimal.ZERO) <= 0 || low.compareTo(BigDecimal.ZERO) <= 0 || high.compareTo(low) < 0) {
            return new OpeningRangeSnapshot(normalized, start, end, high, low, volume, openingBars.size(), false, "invalid opening-range prices");
        }
        OpeningRangeSnapshot snapshot = new OpeningRangeSnapshot(normalized, start, end, high, low, volume, openingBars.size(), true, "");
        if (snapshot.rangePercent().compareTo(safeConfig.minimumRangePercent()) < 0) {
            return new OpeningRangeSnapshot(normalized, start, end, high, low, volume, openingBars.size(), false, "opening range below minimum percent");
        }
        return snapshot;
    }

    private OpeningRangeSnapshot rejected(String symbol, LocalDate date, OrbConfig config, String reason) {
        LocalDate safeDate = date == null ? LocalDate.now(EASTERN) : date;
        Instant start = ZonedDateTime.of(safeDate, REGULAR_OPEN_ET, EASTERN).toInstant();
        Instant end = ZonedDateTime.of(safeDate, REGULAR_OPEN_ET.plusMinutes(config.rangeDurationMinutes()), EASTERN).toInstant();
        return new OpeningRangeSnapshot(symbol, start, end, null, null, BigDecimal.ZERO, 0, false, reason);
    }

    private boolean inRange(MarketBar bar, Instant start, Instant end) {
        Instant ts = timestamp(bar);
        return !ts.isBefore(start) && ts.isBefore(end);
    }

    private Instant timestamp(MarketBar bar) {
        String value = bar.timestamp();
        try { return Instant.parse(value); } catch (Exception ignored) { }
        try { return ZonedDateTime.parse(value).toInstant(); } catch (Exception ignored) { }
        return LocalDateTime.parse(value).atZone(EASTERN).toInstant();
    }
}
