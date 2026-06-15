package com.neuralarc.gaprocket;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class GapRocketAnalyzer {
    public static final int MINIMUM_RECOMMENDATION_SCORE = 70;
    private final Clock clock;
    private final Consumer<String> decisionLog;

    public GapRocketAnalyzer(Clock clock, Consumer<String> decisionLog) {
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.decisionLog = decisionLog == null ? ignored -> { } : decisionLog;
    }

    public List<GapRocketRecommendation> analyze(List<GapRocketCandidate> candidates, GapRocketConfig config) {
        GapRocketConfig safeConfig = config == null ? GapRocketConfig.defaults(null) : config;
        return candidates.stream()
                .filter(Objects::nonNull)
                .filter(candidate -> passesFilters(candidate, safeConfig))
                .map(candidate -> toRecommendation(candidate, safeConfig, score(candidate, safeConfig)))
                .filter(this::passesScoreThreshold)
                .sorted(Comparator.comparingInt(GapRocketRecommendation::strategyScore).reversed())
                .limit(safeConfig.maxStocksToAdd())
                .toList();
    }

    public boolean passesFilters(GapRocketCandidate c, GapRocketConfig cfg) {
        if (lt(c.gapPercent(), cfg.minimumPremarketGapPercent())) return reject(c, "premarket gap below minimum");
        if (c.premarketVolume() < cfg.minimumPremarketVolume()) return reject(c, "premarket volume below minimum");
        if (lt(c.currentPrice(), cfg.minimumStockPrice())) return reject(c, "price below minimum");
        if (lt(c.relativeVolume(), cfg.minimumRelativeVolume())) return reject(c, "relative volume below minimum");
        if (cfg.maximumStockPrice() != null && gt(c.currentPrice(), cfg.maximumStockPrice())) return reject(c, "price above maximum");
        if (cfg.newsCatalystRequired() && (c.catalystType() == null || c.catalystSummary() == null || c.catalystSummary().isBlank())) return reject(c, "missing catalyst");
        if (cfg.newsCatalystRequired() && !cfg.catalystTypes().contains(c.catalystType())) return reject(c, "catalyst type not selected");
        if (!passesTrend(c, cfg.marketTrendFilter())) return reject(c, "market trend filter failed");
        if (c.spreadPercent() != null && c.spreadPercent().compareTo(new BigDecimal("2.5")) > 0) return reject(c, "spread too wide");
        decisionLog.accept("[Gap Rocket] Accepted " + c.symbol() + " for scoring.");
        return true;
    }

    public int score(GapRocketCandidate c, GapRocketConfig cfg) {
        int score = bounded(c.gapPercent(), cfg.minimumPremarketGapPercent(), new BigDecimal("20"), 20)
                + bounded(BigDecimal.valueOf(c.premarketVolume()), BigDecimal.valueOf(cfg.minimumPremarketVolume()), BigDecimal.valueOf(cfg.minimumPremarketVolume() * 3), 20)
                + bounded(c.relativeVolume(), cfg.minimumRelativeVolume(), new BigDecimal("5"), 20)
                + (c.catalystType() == null ? 0 : 20)
                + (passesTrend(c, cfg.marketTrendFilter()) ? 10 : 0)
                + (c.spreadPercent() == null || c.spreadPercent().compareTo(new BigDecimal("1")) <= 0 ? 10 : 5);
        return Math.min(100, score);
    }

    private boolean passesScoreThreshold(GapRocketRecommendation recommendation) {
        if (recommendation.strategyScore() >= MINIMUM_RECOMMENDATION_SCORE) {
            return true;
        }
        decisionLog.accept("[Gap Rocket] Rejected " + recommendation.symbol() + ": score "
                + recommendation.strategyScore() + " below minimum " + MINIMUM_RECOMMENDATION_SCORE + ".");
        return false;
    }

    private GapRocketRecommendation toRecommendation(GapRocketCandidate c, GapRocketConfig cfg, int score) {
        BigDecimal plannedEntryPrice = plannedEntryPrice(c);
        BigDecimal stopPrice = plannedEntryPrice.multiply(BigDecimal.ONE.subtract(cfg.stopLossPercent().movePointLeft(2))).setScale(2, RoundingMode.HALF_UP);
        BigDecimal targetPrice = plannedEntryPrice.multiply(BigDecimal.ONE.add(cfg.takeProfitPercent().movePointLeft(2))).setScale(2, RoundingMode.HALF_UP);
        return new GapRocketRecommendation(c.symbol().toUpperCase(), c.companyName(), c.gapPercent(), c.premarketVolume(), c.relativeVolume(),
                c.currentPrice(), c.previousClose(), c.premarketHigh(), c.premarketLow(), c.catalystType(), c.catalystSummary(), score,
                cfg.entryStyle(), cfg.openingRangeDuration(), plannedEntryPrice, cfg.stopLossPercent(), stopPrice, cfg.takeProfitPercent(),
                targetPrice, GapRocketStatus.RECOMMENDED, cfg.mode(), Instant.now(clock));
    }

    private BigDecimal plannedEntryPrice(GapRocketCandidate c) {
        BigDecimal current = c.currentPrice() == null ? BigDecimal.ZERO : c.currentPrice();
        BigDecimal high = c.premarketHigh() == null ? BigDecimal.ZERO : c.premarketHigh();
        return current.max(high).setScale(2, RoundingMode.HALF_UP);
    }

    private boolean passesTrend(GapRocketCandidate c, GapRocketConfig.MarketTrendFilter filter) {
        return switch (filter) {
            case DISABLED -> true;
            case SPY_GREEN -> c.spyGreen();
            case QQQ_GREEN -> c.qqqGreen();
            case EITHER_SPY_OR_QQQ_GREEN -> c.spyGreen() || c.qqqGreen();
        };
    }

    private boolean reject(GapRocketCandidate c, String reason) { decisionLog.accept("[Gap Rocket] Rejected " + c.symbol() + ": " + reason + "."); return false; }
    private static boolean lt(BigDecimal a, BigDecimal b) { return a == null || a.compareTo(b) < 0; }
    private static boolean gt(BigDecimal a, BigDecimal b) { return a != null && a.compareTo(b) > 0; }
    private static int bounded(BigDecimal value, BigDecimal min, BigDecimal full, int points) {
        if (value == null || value.compareTo(min) < 0) return 0;
        if (value.compareTo(full) >= 0) return points;
        BigDecimal span = full.subtract(min);
        if (span.compareTo(BigDecimal.ZERO) <= 0) return points;
        return value.subtract(min).multiply(BigDecimal.valueOf(points)).divide(span, 0, RoundingMode.HALF_UP).intValue();
    }
}
