package com.neuralarc.api;

import com.neuralarc.model.MarketBar;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Serves repeated bar requests for the same symbol from one fetch.
 *
 * <p>Analysing a single symbol asks for daily bars four times over overlapping windows - a year, a
 * year again, the last week, and two years - so twenty symbols cost eighty daily requests where
 * twenty would do. Each of those four windows fits inside the widest one, so the first request
 * fetches the widest and the rest are answered from it.
 *
 * <p>A cached range is used only when it <em>fully covers</em> what was asked for. A partly covering
 * range is refetched rather than sliced: returning the overlap would quietly hand back a truncated
 * history that reads as a complete one, which is a worse failure than an extra request.
 *
 * <p>Intraday bars are cached by exact request only. Their windows differ by interval, and widening
 * a one-minute window to two years would fetch hundreds of thousands of bars to save a call.
 *
 * <p>Wrap a client for one bulk run and discard it. Instances are safe to share across the threads
 * that analyse symbols in parallel; entries are immutable and the maps are concurrent.
 */
public final class CachingMarketDataApi implements AlpacaMarketDataApi {
    /** Long enough for one analysis run, short enough that a later run sees a moved market. */
    static final long DEFAULT_TTL_MILLIS = 5 * 60 * 1000L;
    /** How far back a daily fetch is widened, chosen to cover the longest window analysis asks for. */
    static final int WIDENED_LOOKBACK_DAYS = 730;
    /** Analysis asks for tomorrow as the end of its snapshot window, so the cache must reach it. */
    static final int WIDENED_LOOKAHEAD_DAYS = 1;

    private final AlpacaMarketDataApi delegate;
    private final long ttlMillis;
    private final LongSupplier clock;
    private final Map<String, DailyBars> dailyBySymbol = new ConcurrentHashMap<>();
    private final Map<IntradayKey, IntradayBars> intradayByKey = new ConcurrentHashMap<>();

    public CachingMarketDataApi(AlpacaMarketDataApi delegate) {
        this(delegate, DEFAULT_TTL_MILLIS, System::currentTimeMillis);
    }

    CachingMarketDataApi(AlpacaMarketDataApi delegate, long ttlMillis, LongSupplier clock) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate must not be null");
        }
        this.delegate = delegate;
        this.ttlMillis = Math.max(0L, ttlMillis);
        this.clock = clock == null ? System::currentTimeMillis : clock;
    }

    @Override
    public List<MarketBar> getDailyBars(String symbol, LocalDate startDate, LocalDate endDate)
            throws AlpacaMarketDataException {
        if (symbol == null || symbol.isBlank() || startDate == null || endDate == null) {
            return delegate.getDailyBars(symbol, startDate, endDate);
        }
        String key = symbol.trim().toUpperCase(Locale.ROOT);
        DailyBars cached = dailyBySymbol.get(key);
        if (cached != null && !cached.expired(clock.getAsLong(), ttlMillis) && cached.covers(startDate, endDate)) {
            return slice(cached.bars(), startDate, endDate);
        }

        LocalDate widenedStart = earlier(startDate, endDate.minusDays(WIDENED_LOOKBACK_DAYS));
        LocalDate widenedEnd = later(endDate, endDate.plusDays(WIDENED_LOOKAHEAD_DAYS));
        // Only stored on success: a failed fetch must not be remembered as an empty history.
        List<MarketBar> fetched = List.copyOf(delegate.getDailyBars(symbol, widenedStart, widenedEnd));
        dailyBySymbol.put(key, new DailyBars(widenedStart, widenedEnd, fetched, clock.getAsLong()));
        return slice(fetched, startDate, endDate);
    }

    @Override
    public List<MarketBar> getIntradayBars(String symbol, LocalDate startDate, LocalDate endDate, int intervalMinutes)
            throws AlpacaMarketDataException {
        if (symbol == null || symbol.isBlank() || startDate == null || endDate == null) {
            return delegate.getIntradayBars(symbol, startDate, endDate, intervalMinutes);
        }
        IntradayKey key = new IntradayKey(symbol.trim().toUpperCase(Locale.ROOT), startDate, endDate, intervalMinutes);
        IntradayBars cached = intradayByKey.get(key);
        if (cached != null && !cached.expired(clock.getAsLong(), ttlMillis)) {
            return cached.bars();
        }
        List<MarketBar> fetched = List.copyOf(delegate.getIntradayBars(symbol, startDate, endDate, intervalMinutes));
        intradayByKey.put(key, new IntradayBars(fetched, clock.getAsLong()));
        return fetched;
    }

    /** Bars within the requested dates, in the order the broker returned them. */
    private static List<MarketBar> slice(List<MarketBar> bars, LocalDate startDate, LocalDate endDate) {
        List<MarketBar> sliced = new ArrayList<>(bars.size());
        for (MarketBar bar : bars) {
            LocalDate date = barDate(bar);
            // A bar whose timestamp cannot be read is kept. The broker returned it, and dropping data
            // silently would be worse than passing along one bar outside the window.
            if (date == null || (!date.isBefore(startDate) && !date.isAfter(endDate))) {
                sliced.add(bar);
            }
        }
        return List.copyOf(sliced);
    }

    private static LocalDate barDate(MarketBar bar) {
        if (bar == null || bar.timestamp() == null || bar.timestamp().isBlank()) {
            return null;
        }
        String timestamp = bar.timestamp().trim();
        try {
            return Instant.parse(timestamp).atZone(ZoneOffset.UTC).toLocalDate();
        } catch (Exception ignored) {
            try {
                return timestamp.length() >= 10 ? LocalDate.parse(timestamp.substring(0, 10)) : null;
            } catch (Exception alsoIgnored) {
                return null;
            }
        }
    }

    private static LocalDate earlier(LocalDate left, LocalDate right) {
        return left.isBefore(right) ? left : right;
    }

    private static LocalDate later(LocalDate left, LocalDate right) {
        return left.isAfter(right) ? left : right;
    }

    private record DailyBars(LocalDate start, LocalDate end, List<MarketBar> bars, long loadedAtMillis) {
        boolean covers(LocalDate requestedStart, LocalDate requestedEnd) {
            return !start.isAfter(requestedStart) && !end.isBefore(requestedEnd);
        }

        boolean expired(long nowMillis, long ttlMillis) {
            return nowMillis - loadedAtMillis >= ttlMillis;
        }
    }

    private record IntradayBars(List<MarketBar> bars, long loadedAtMillis) {
        boolean expired(long nowMillis, long ttlMillis) {
            return nowMillis - loadedAtMillis >= ttlMillis;
        }
    }

    private record IntradayKey(String symbol, LocalDate start, LocalDate end, int intervalMinutes) {
    }
}
