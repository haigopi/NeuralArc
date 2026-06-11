package com.neuralarc.service;

import com.neuralarc.model.TrendingStock;
import com.neuralarc.model.TrendingStockGroups;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

public class TrendingStocksService {
    private static final Logger LOGGER = Logger.getLogger(TrendingStocksService.class.getName());
    private static final BigDecimal MIN_LISTING_PRICE = new BigDecimal("5.00");
    private static final BigDecimal WEEKEND_REBOUND_MIN_PRICE = new BigDecimal("10.00");
    private static final BigDecimal WEEKEND_REBOUND_WATCHLIST_MIN_DECLINE = new BigDecimal("-1.00");
    private static final BigDecimal WEEKEND_REBOUND_MIN_DECLINE = new BigDecimal("-3.00");
    private static final BigDecimal WEEKEND_REBOUND_MAX_DECLINE = new BigDecimal("-15.00");
    private static final BigDecimal MIN_DAILY_BARS_FOR_PROCESSING = new BigDecimal("100");

    private final AlpacaScreenerClient client;

    public TrendingStocksService(AlpacaScreenerClient client) {
        this.client = client;
    }

    public List<TrendingStock> topTrendingStocks(int limit) throws AlpacaScreenerException {
        int requested = Math.max(1, limit);
        LOGGER.info("Alpaca trending API request started.");
        JSONObject movers = client.getMarketMovers(Math.max(10, requested));
        JSONObject activeByVolume = client.getMostActives("volume", Math.max(20, requested * 4));
        JSONObject activeByTrades = client.getMostActives("trades", Math.max(20, requested * 4));
        List<TrendingStock> selected = selectTop(parseCandidates(movers, activeByVolume, activeByTrades), requested);
        LOGGER.info(() -> "Alpaca trending API request completed. selected="
                + selected.stream().map(TrendingStock::symbol).toList());
        return selected;
    }

    public TrendingStockGroups topGainersAndLosers(int perSideLimit) throws AlpacaScreenerException {
        int requested = Math.max(1, perSideLimit);
        LOGGER.info("Alpaca movers API request started.");
        JSONObject movers = client.getMarketMovers(Math.max(50, requested * 3));
        List<TrendingStock> gainers = selectMovers(movers.optJSONArray("gainers"), "top mover gainer", requested);
        List<TrendingStock> losers = selectMovers(movers.optJSONArray("losers"), "top mover loser", requested);
        LOGGER.info(() -> "Alpaca movers API request completed. gainers="
                + gainers.stream().map(TrendingStock::symbol).toList()
                + " losers=" + losers.stream().map(TrendingStock::symbol).toList());
        return new TrendingStockGroups(gainers, losers);
    }

    public List<TrendingStock> weekendReboundCandidates(int limit) throws AlpacaScreenerException {
        int requested = Math.max(1, limit);
        LOGGER.info("Alpaca Weekend Rebound candidate request started.");
        JSONObject movers = client.getMarketMovers(Math.max(100, requested * 4));
        List<TrendingStock> losers = parseMoverList(movers.optJSONArray("losers"), "weekend rebound candidate");
        List<TrendingStock> candidates = losers.stream()
                .filter(TrendingStocksService::isWeekendReboundCandidate)
                .sorted(Comparator.comparing(TrendingStocksService::weekendReboundPreScore).reversed()
                        .thenComparing(TrendingStock::symbol))
                .limit(requested)
                .toList();
        if (candidates.isEmpty()) {
            candidates = losers.stream()
                    .filter(TrendingStocksService::isWeekendReboundWatchlistCandidate)
                    .map(TrendingStocksService::asWeekendReboundWatchlistCandidate)
                    .sorted(Comparator.comparing(TrendingStocksService::weekendReboundPreScore).reversed()
                            .thenComparing(TrendingStock::symbol))
                    .limit(requested)
                    .toList();
        }
        List<TrendingStock> selected = candidates;
        LOGGER.info(() -> "Alpaca Weekend Rebound candidates selected. symbols="
                + selected.stream().map(TrendingStock::symbol).toList());
        return candidates;
    }

    static List<TrendingStock> parseCandidates(JSONObject movers, JSONObject activeByVolume, JSONObject activeByTrades) {
        Map<String, Candidate> candidates = new LinkedHashMap<>();
        parseMovers(candidates, movers == null ? new JSONObject() : movers);
        parseActives(candidates, activeByVolume == null ? new JSONObject() : activeByVolume, "volume");
        parseActives(candidates, activeByTrades == null ? new JSONObject() : activeByTrades, "trades");
        return candidates.values().stream()
                .map(Candidate::toTrendingStock)
                .sorted(Comparator.comparing(TrendingStock::trendingScore).reversed()
                        .thenComparing(TrendingStock::symbol))
                .toList();
    }

    static List<TrendingStock> selectTop(List<TrendingStock> candidates, int limit) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        return candidates.stream()
                .filter(stock -> !stock.symbol().isBlank())
                .sorted(Comparator.comparing(TrendingStock::trendingScore).reversed()
                        .thenComparing(TrendingStock::symbol))
                .limit(Math.max(1, limit))
                .toList();
    }

    private static void parseMovers(Map<String, Candidate> candidates, JSONObject body) {
        parseMoverArray(candidates, body.optJSONArray("gainers"), "top mover gainer");
        parseMoverArray(candidates, body.optJSONArray("losers"), "top mover loser");
    }

    private static void parseMoverArray(Map<String, Candidate> candidates, JSONArray array, String reason) {
        if (array == null) {
            return;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String symbol = symbol(item);
            if (symbol.isBlank()) {
                continue;
            }
            Candidate candidate = candidates.computeIfAbsent(symbol, Candidate::new);
            candidate.companyName = firstText(candidate.companyName, item.optString("name", item.optString("company_name", "")));
            candidate.latestPrice = firstPositive(candidate.latestPrice, decimal(item, "price", "latest_price", "close"));
            candidate.dailyChangePercent = firstNonZero(candidate.dailyChangePercent,
                    decimal(item, "percent_change", "change_percent", "change_pct"));
            candidate.addReason(reason);
            candidate.movementScore = candidate.movementScore.max(candidate.dailyChangePercent.abs());
        }
    }

    private static List<TrendingStock> selectMovers(JSONArray array, String reason, int limit) {
        List<TrendingStock> all = parseMoverList(array, reason);
        List<TrendingStock> filtered = all.stream()
                .filter(TrendingStocksService::isProcessableMover)
                .sorted(Comparator.comparing(TrendingStocksService::techPreferenceScore).reversed()
                        .thenComparing(TrendingStock::trendingScore, Comparator.reverseOrder())
                        .thenComparing(TrendingStock::symbol))
                .limit(limit)
                .toList();
        if (!filtered.isEmpty()) {
            return filtered;
        }
        // Fallback: some Alpaca mover payloads omit trade_count/trades, so keep the listing usable.
        return all.stream()
                .filter(stock -> stock.latestPrice().compareTo(MIN_LISTING_PRICE) >= 0)
                .sorted(Comparator.comparing(TrendingStocksService::techPreferenceScore).reversed()
                        .thenComparing(TrendingStock::trendingScore, Comparator.reverseOrder())
                        .thenComparing(TrendingStock::symbol))
                .limit(limit)
                .toList();
    }

    private static boolean isProcessableMover(TrendingStock stock) {
        return stock.latestPrice().compareTo(MIN_LISTING_PRICE) >= 0
                && hasMinimumDailyBars(stock);
    }

    private static boolean hasMinimumDailyBars(TrendingStock stock) {
        return stock.tradeCount().compareTo(BigDecimal.ZERO) == 0
                || stock.tradeCount().compareTo(MIN_DAILY_BARS_FOR_PROCESSING) >= 0;
    }

    private static boolean isWeekendReboundCandidate(TrendingStock stock) {
        BigDecimal decline = stock.dailyChangePercent();
        return stock.latestPrice().compareTo(WEEKEND_REBOUND_MIN_PRICE) >= 0
                && decline.compareTo(WEEKEND_REBOUND_MIN_DECLINE) <= 0
                && decline.compareTo(WEEKEND_REBOUND_MAX_DECLINE) >= 0;
    }

    private static boolean isWeekendReboundWatchlistCandidate(TrendingStock stock) {
        BigDecimal decline = stock.dailyChangePercent();
        return stock.latestPrice().compareTo(WEEKEND_REBOUND_MIN_PRICE) >= 0
                && decline.compareTo(WEEKEND_REBOUND_WATCHLIST_MIN_DECLINE) <= 0
                && decline.compareTo(WEEKEND_REBOUND_MAX_DECLINE) >= 0;
    }

    private static TrendingStock asWeekendReboundWatchlistCandidate(TrendingStock stock) {
        return new TrendingStock(
                stock.symbol(),
                stock.companyName(),
                stock.latestPrice(),
                stock.dailyChangePercent(),
                stock.volume(),
                stock.tradeCount(),
                "weekend rebound watchlist candidate",
                stock.trendingScore()
        );
    }

    private static BigDecimal weekendReboundPreScore(TrendingStock stock) {
        BigDecimal preferredDecline = stock.dailyChangePercent().abs().min(new BigDecimal("12.00"));
        BigDecimal volumeMillions = stock.volume().compareTo(BigDecimal.ZERO) <= 0
                ? BigDecimal.ZERO
                : stock.volume().divide(new BigDecimal("1000000"), 4, RoundingMode.HALF_UP).min(new BigDecimal("20.00"));
        return preferredDecline.multiply(new BigDecimal("2.00")).add(volumeMillions);
    }

    static List<TrendingStock> parseMoverList(JSONArray array, String reason) {
        if (array == null) {
            return List.of();
        }
        List<TrendingStock> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String symbol = symbol(item);
            if (symbol.isBlank()) {
                continue;
            }
            BigDecimal price = decimal(item, "price", "latest_price", "close");
            BigDecimal change = decimal(item, "percent_change", "change_percent", "change_pct");
            BigDecimal score = change.abs().multiply(BigDecimal.valueOf(2)).add(scoreFromRank(i));
            result.add(new TrendingStock(
                    symbol,
                    item.optString("name", item.optString("company_name", "")),
                    price,
                    change,
                    decimal(item, "volume", "v"),
                    decimal(item, "trade_count", "trades"),
                    reason,
                    score
            ));
        }
        return result;
    }

    private static BigDecimal techPreferenceScore(TrendingStock stock) {
        return isLikelyTechSymbol(stock.symbol()) ? BigDecimal.ONE : BigDecimal.ZERO;
    }

    private static boolean isLikelyTechSymbol(String symbol) {
        return List.of(
                "AAPL", "MSFT", "NVDA", "AMD", "AVGO", "TSLA", "META", "GOOGL", "GOOG", "AMZN",
                "NFLX", "CRM", "ORCL", "ADBE", "INTC", "QCOM", "MU", "ARM", "SMCI", "PLTR",
                "NOW", "SNOW", "SHOP", "PANW", "CRWD", "DDOG", "NET", "MDB", "UBER", "ABNB"
        ).contains(symbol);
    }

    private static void parseActives(Map<String, Candidate> candidates, JSONObject body, String by) {
        JSONArray array = body.optJSONArray("most_actives");
        if (array == null) {
            array = body.optJSONArray("mostActives");
        }
        if (array == null) {
            return;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String symbol = symbol(item);
            if (symbol.isBlank()) {
                continue;
            }
            Candidate candidate = candidates.computeIfAbsent(symbol, Candidate::new);
            candidate.companyName = firstText(candidate.companyName, item.optString("name", item.optString("company_name", "")));
            candidate.latestPrice = firstPositive(candidate.latestPrice, decimal(item, "price", "latest_price", "close"));
            candidate.volume = firstPositive(candidate.volume, decimal(item, "volume", "v"));
            candidate.tradeCount = firstPositive(candidate.tradeCount, decimal(item, "trade_count", "trades"));
            candidate.addReason("most active by " + by);
            if ("trades".equals(by)) {
                candidate.tradeScore = scoreFromRank(i);
            } else {
                candidate.volumeScore = scoreFromRank(i);
            }
        }
    }

    private static BigDecimal scoreFromRank(int zeroBasedRank) {
        return BigDecimal.valueOf(Math.max(1, 20 - zeroBasedRank));
    }

    private static String symbol(JSONObject item) {
        return item.optString("symbol", item.optString("S", "")).trim().toUpperCase(Locale.ROOT);
    }

    private static BigDecimal decimal(JSONObject item, String... keys) {
        for (String key : keys) {
            Object value = item.opt(key);
            if (value == null) {
                continue;
            }
            try {
                return new BigDecimal(String.valueOf(value));
            } catch (NumberFormatException ignored) {
            }
        }
        return BigDecimal.ZERO;
    }

    private static String firstText(String current, String candidate) {
        return current == null || current.isBlank() ? candidate == null ? "" : candidate.trim() : current;
    }

    private static BigDecimal firstPositive(BigDecimal current, BigDecimal candidate) {
        return current.compareTo(BigDecimal.ZERO) > 0 ? current : candidate;
    }

    private static BigDecimal firstNonZero(BigDecimal current, BigDecimal candidate) {
        return current.compareTo(BigDecimal.ZERO) != 0 ? current : candidate;
    }

    private static final class Candidate {
        private final String symbol;
        private String companyName = "";
        private BigDecimal latestPrice = BigDecimal.ZERO;
        private BigDecimal dailyChangePercent = BigDecimal.ZERO;
        private BigDecimal volume = BigDecimal.ZERO;
        private BigDecimal tradeCount = BigDecimal.ZERO;
        private BigDecimal movementScore = BigDecimal.ZERO;
        private BigDecimal volumeScore = BigDecimal.ZERO;
        private BigDecimal tradeScore = BigDecimal.ZERO;
        private final List<String> reasons = new ArrayList<>();

        private Candidate(String symbol) {
            this.symbol = symbol;
        }

        private void addReason(String reason) {
            if (!reasons.contains(reason)) {
                reasons.add(reason);
            }
        }

        private TrendingStock toTrendingStock() {
            BigDecimal score = movementScore.multiply(BigDecimal.valueOf(2))
                    .add(volumeScore)
                    .add(tradeScore)
                    .setScale(4, RoundingMode.HALF_UP);
            return new TrendingStock(
                    symbol,
                    companyName,
                    latestPrice,
                    dailyChangePercent,
                    volume,
                    tradeCount,
                    String.join(", ", reasons),
                    score
            );
        }
    }
}
