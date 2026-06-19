package com.neuralarc.vwap;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Filters and scores VWAP Desk candidates: a meaningful discount below intraday VWAP in a name that is
 * still in a broader uptrend, expecting reversion back toward VWAP. Pure/clock-driven for unit testing.
 */
public final class VwapAnalyzer {
    public static final int MINIMUM_RECOMMENDATION_SCORE = 60;
    private static final BigDecimal MAX_SPREAD_PERCENT = new BigDecimal("3.5");
    private final Clock clock;
    private final Consumer<String> decisionLog;

    public VwapAnalyzer(Clock clock, Consumer<String> decisionLog) {
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.decisionLog = decisionLog == null ? ignored -> { } : decisionLog;
    }

    public List<VwapRecommendation> analyze(List<VwapCandidate> candidates, VwapConfig config) {
        VwapConfig safeConfig = config == null ? VwapConfig.defaults(null) : config;
        return candidates.stream()
                .filter(Objects::nonNull)
                .filter(candidate -> passesFilters(candidate, safeConfig))
                .map(candidate -> toRecommendation(candidate, safeConfig, score(candidate, safeConfig)))
                .filter(this::passesScoreThreshold)
                .sorted(Comparator.comparingInt(VwapRecommendation::strategyScore).reversed())
                .limit(safeConfig.maxStocksToAdd())
                .toList();
    }

    public boolean passesFilters(VwapCandidate c, VwapConfig cfg) {
        if (lt(c.discountPercent(), cfg.minimumDiscountPercent())) return reject(c, "not far enough below VWAP");
        if (gt(c.discountPercent(), cfg.maximumDiscountPercent())) return reject(c, "too far below VWAP (possible breakdown)");
        if (lt(c.currentPrice(), cfg.minimumStockPrice())) return reject(c, "price below minimum");
        if (cfg.maximumStockPrice() != null && gt(c.currentPrice(), cfg.maximumStockPrice())) return reject(c, "price above maximum");
        if (lt(c.relativeVolume(), cfg.minimumRelativeVolume())) return reject(c, "relative volume below minimum");
        if (c.averageVolume() < cfg.minimumAverageVolume()) return reject(c, "average volume below minimum");
        if (!passesTrend(c, cfg.trendFilter())) return reject(c, "not in an uptrend (trend filter failed)");
        if (c.spreadPercent() != null && c.spreadPercent().compareTo(MAX_SPREAD_PERCENT) > 0) return reject(c, "spread too wide");
        decisionLog.accept("[VWAP Desk] Accepted " + c.symbol() + " for scoring.");
        return true;
    }

    public int score(VwapCandidate c, VwapConfig cfg) {
        // Reward an ideal mid-range discount (a tradeable stretch, not a breakdown), strong relative
        // volume, a confirmed broader uptrend, and a tight spread.
        BigDecimal idealDiscount = cfg.minimumDiscountPercent()
                .add(cfg.maximumDiscountPercent()).divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
        int score = discountQuality(c.discountPercent(), cfg.minimumDiscountPercent(), idealDiscount, cfg.maximumDiscountPercent())
                + bounded(c.relativeVolume(), cfg.minimumRelativeVolume(), new BigDecimal("4"), 25)
                + (passesTrend(c, cfg.trendFilter()) ? 20 : 0)
                + (c.aboveMa50() && c.aboveMa200() ? 10 : 0)
                + (c.spreadPercent() == null || c.spreadPercent().compareTo(BigDecimal.ONE) <= 0 ? 10 : 5);
        return Math.min(100, score);
    }

    private boolean passesScoreThreshold(VwapRecommendation recommendation) {
        if (recommendation.strategyScore() >= MINIMUM_RECOMMENDATION_SCORE) {
            return true;
        }
        decisionLog.accept("[VWAP Desk] Rejected " + recommendation.symbol() + ": score "
                + recommendation.strategyScore() + " below minimum " + MINIMUM_RECOMMENDATION_SCORE + ".");
        return false;
    }

    private VwapRecommendation toRecommendation(VwapCandidate c, VwapConfig cfg, int score) {
        BigDecimal entry = plannedEntryPrice(c);
        BigDecimal stopPrice = entry.multiply(BigDecimal.ONE.subtract(cfg.stopLossPercent().movePointLeft(2))).setScale(2, RoundingMode.HALF_UP);
        // Mean-reversion target is VWAP itself; never plan a target at or below the entry.
        BigDecimal target = c.vwap() == null ? entry : c.vwap().max(entry).setScale(2, RoundingMode.HALF_UP);
        BigDecimal reversionUpside = entry.compareTo(BigDecimal.ZERO) > 0
                ? target.subtract(entry).multiply(BigDecimal.valueOf(100)).divide(entry, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        return new VwapRecommendation(c.symbol().toUpperCase(), c.companyName(), c.currentPrice(), c.vwap(),
                c.discountPercent(), c.previousClose(), c.dayChangePercent(), c.averageVolume(), c.relativeVolume(),
                c.movingAverage50(), c.movingAverage200(), score, entry, cfg.stopLossPercent(), stopPrice, target,
                reversionUpside, VwapStatus.RECOMMENDED, cfg.mode(), Instant.now(clock));
    }

    private BigDecimal plannedEntryPrice(VwapCandidate c) {
        // Buy the discount at the current price; never plan below it.
        BigDecimal current = c.currentPrice() == null ? BigDecimal.ZERO : c.currentPrice();
        return current.setScale(2, RoundingMode.HALF_UP);
    }

    private boolean passesTrend(VwapCandidate c, VwapConfig.TrendFilter filter) {
        return switch (filter) {
            case DISABLED -> true;
            case ABOVE_MA_50 -> c.aboveMa50();
            case ABOVE_MA_200 -> c.aboveMa200();
            case ABOVE_MA_50_OR_200 -> c.aboveMa50() || c.aboveMa200();
        };
    }

    /** Triangular score: 0 at the min/max bounds, full points at the ideal mid-range discount. */
    private static int discountQuality(BigDecimal value, BigDecimal min, BigDecimal ideal, BigDecimal max) {
        int points = 35;
        if (value == null || value.compareTo(min) < 0 || value.compareTo(max) > 0) return 0;
        BigDecimal span = value.compareTo(ideal) <= 0 ? ideal.subtract(min) : max.subtract(ideal);
        if (span.compareTo(BigDecimal.ZERO) <= 0) return points;
        BigDecimal distance = value.subtract(ideal).abs();
        BigDecimal ratio = BigDecimal.ONE.subtract(distance.divide(span, 4, RoundingMode.HALF_UP));
        if (ratio.compareTo(BigDecimal.ZERO) < 0) return 0;
        return ratio.multiply(BigDecimal.valueOf(points)).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private boolean reject(VwapCandidate c, String reason) {
        decisionLog.accept("[VWAP Desk] Rejected " + c.symbol() + ": " + reason + ".");
        return false;
    }

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
