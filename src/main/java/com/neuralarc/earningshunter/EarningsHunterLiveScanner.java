package com.neuralarc.earningshunter;

import com.neuralarc.api.AlpacaMarketDataApi;
import com.neuralarc.api.AlpacaMarketDataException;
import com.neuralarc.model.MarketBar;
import com.neuralarc.model.NewsArticle;
import com.neuralarc.service.AlpacaNewsClient;
import com.neuralarc.service.AlpacaNewsException;
import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public final class EarningsHunterLiveScanner {
    private static final int LOOKBACK_DAYS = 80;
    private static final int NEWS_LIMIT = 10;
    private static final int MINIMUM_HISTORY = 20;
    private static final List<String> EARNINGS_TERMS = List.of(
            "earnings", "eps", "revenue", "quarter", "quarterly", "guidance", "results", "beat", "miss");

    private final AlpacaMarketDataApi marketDataApi;
    private final AlpacaNewsClient newsClient;
    private final Clock clock;
    private final Consumer<String> log;

    public EarningsHunterLiveScanner(AlpacaMarketDataApi marketDataApi, AlpacaNewsClient newsClient,
                                     Clock clock, Consumer<String> log) {
        this.marketDataApi = Objects.requireNonNull(marketDataApi, "marketDataApi");
        this.newsClient = Objects.requireNonNull(newsClient, "newsClient");
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
        this.log = log == null ? ignored -> { } : log;
    }

    public List<EarningsHunterCandidate> candidates(List<String> symbols, EarningsHunterConfig config) {
        if (symbols == null || symbols.isEmpty()) {
            return List.of();
        }
        EarningsHunterConfig safeConfig = config == null ? EarningsHunterConfig.defaults(null) : config;
        LocalDate today = LocalDate.now(clock);
        List<EarningsHunterCandidate> candidates = new ArrayList<>();
        for (String symbol : symbols.stream().map(EarningsHunterLiveScanner::normalizeSymbol)
                .filter(normalized -> !normalized.isBlank()).distinct().toList()) {
            try {
                buildCandidate(symbol, today, safeConfig).ifPresent(candidates::add);
            } catch (AlpacaMarketDataException | AlpacaNewsException ex) {
                log.accept("[Earnings Hunter] Skipped " + symbol + ": " + ex.getMessage());
            }
        }
        return candidates;
    }

    private Optional<EarningsHunterCandidate> buildCandidate(String symbol, LocalDate today, EarningsHunterConfig config)
            throws AlpacaMarketDataException, AlpacaNewsException {
        List<NewsArticle> earningsArticles = recentEarningsNews(symbol, config.earningsWindowDays());
        if (earningsArticles.isEmpty()) {
            log.accept("[Earnings Hunter] Skipped " + symbol + ": no recent earnings catalyst found in Alpaca news.");
            return Optional.empty();
        }
        List<MarketBar> bars = marketDataApi.getDailyBars(symbol, today.minusDays(LOOKBACK_DAYS), today);
        if (bars == null || bars.size() < MINIMUM_HISTORY + 1) {
            log.accept("[Earnings Hunter] Skipped " + symbol + ": Alpaca did not return enough daily history.");
            return Optional.empty();
        }
        MarketBar latest = bars.get(bars.size() - 1);
        List<MarketBar> history = bars.subList(0, bars.size() - 1);
        MarketBar previous = history.get(history.size() - 1);
        BigDecimal current = valid(latest.close()) ? latest.close() : latest.open();
        if (!valid(current) || !valid(previous.close())) {
            log.accept("[Earnings Hunter] Skipped " + symbol + ": missing live price data.");
            return Optional.empty();
        }
        BigDecimal averageVolume = averageVolume(history, 20);
        BigDecimal relativeVolume = valid(averageVolume) && valid(latest.volume())
                ? latest.volume().divide(averageVolume, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal dayChange = current.subtract(previous.close()).multiply(BigDecimal.valueOf(100))
                .divide(previous.close(), 2, RoundingMode.HALF_UP);
        Instant latestNewsAt = earningsArticles.stream().map(NewsArticle::createdAt)
                .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
        return Optional.of(new EarningsHunterCandidate(symbol, symbol, Monetary.round(current),
                Monetary.round(previous.close()), dayChange, averageVolume.longValue(), relativeVolume,
                earningsArticles, latestNewsAt));
    }

    private List<NewsArticle> recentEarningsNews(String symbol, int windowDays) throws AlpacaNewsException {
        Instant cutoff = Instant.now(clock).minus(Duration.ofDays(Math.max(1, windowDays)));
        return newsClient.latestNews(symbol, NEWS_LIMIT).stream()
                .filter(article -> article.createdAt() == null || !article.createdAt().isBefore(cutoff))
                .filter(EarningsHunterLiveScanner::containsEarningsTerm)
                .toList();
    }

    static boolean containsEarningsTerm(NewsArticle article) {
        if (article == null) {
            return false;
        }
        String text = (article.headline() + " " + article.summary()).toLowerCase(Locale.ROOT);
        return EARNINGS_TERMS.stream().anyMatch(text::contains);
    }

    private BigDecimal averageVolume(List<MarketBar> history, int period) {
        int count = Math.min(period, history.size());
        List<MarketBar> window = history.subList(history.size() - count, history.size());
        BigDecimal sum = window.stream().map(MarketBar::volume).reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    private boolean valid(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    public static List<String> parseSymbols(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.split("[,\\s]+"))
                .map(EarningsHunterLiveScanner::normalizeSymbol)
                .filter(symbol -> !symbol.isBlank())
                .distinct()
                .toList();
    }

    private static String normalizeSymbol(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    }
}
