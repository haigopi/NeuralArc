package com.neuralarc.diphunter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Filters and scores Dip Hunter candidates: a pullback of the right depth in a name that is still in
 * an uptrend and showing the configured bounce confirmation. Pure/clock-driven for unit testing.
 */
public final class DipHunterAnalyzer {
    public static final int MINIMUM_RECOMMENDATION_SCORE = 60;
    private static final BigDecimal MAX_SPREAD_PERCENT = new BigDecimal("3.5");
    private final Clock clock;
    private final Consumer<String> decisionLog;

    public DipHunterAnalyzer(Clock clock, Consumer<String> decisionLog) {
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.decisionLog = decisionLog == null ? ignored -> { } : decisionLog;
    }

    public List<DipHunterRecommendation> analyze(List<DipHunterCandidate> candidates, DipHunterConfig config) {
        DipHunterConfig safeConfig = config == null ? DipHunterConfig.defaults(null) : config;
        return candidates.stream()
                .filter(Objects::nonNull)
                .filter(candidate -> passesFilters(candidate, safeConfig))
                .map(candidate -> toRecommendation(candidate, safeConfig, score(candidate, safeConfig)))
                .filter(this::passesScoreThreshold)
                .sorted(Comparator.comparingInt(DipHunterRecommendation::strategyScore).reversed())
                .limit(safeConfig.maxStocksToAdd())
                .toList();
    }

    public boolean passesFilters(DipHunterCandidate c, DipHunterConfig cfg) {
        if (lt(c.pullbackPercent(), cfg.minimumPullbackPercent())) return reject(c, "pullback below minimum");
        if (gt(c.pullbackPercent(), cfg.maximumPullbackPercent())) return reject(c, "pullback too deep (possible falling knife)");
        if (lt(c.currentPrice(), cfg.minimumStockPrice())) return reject(c, "price below minimum");
        if (cfg.maximumStockPrice() != null && gt(c.currentPrice(), cfg.maximumStockPrice())) return reject(c, "price above maximum");
        if (lt(c.relativeVolume(), cfg.minimumRelativeVolume())) return reject(c, "relative volume below minimum");
        if (c.averageVolume() < cfg.minimumAverageVolume()) return reject(c, "average volume below minimum");
        if (!passesTrend(c, cfg.trendFilter())) return reject(c, "not in an uptrend (trend filter failed)");
        if (cfg.bounceConfirmation() == DipHunterConfig.BounceConfirmation.INTRADAY_REVERSAL && !c.intradayReversal()) {
            return reject(c, "no intraday reversal yet");
        }
        if (c.spreadPercent() != null && c.spreadPercent().compareTo(MAX_SPREAD_PERCENT) > 0) return reject(c, "spread too wide");
        decisionLog.accept("[Dip Hunter] Accepted " + c.symbol() + " for scoring.");
        return true;
    }

    public int score(DipHunterCandidate c, DipHunterConfig cfg) {
        // Reward an ideal mid-range pullback (not too shallow, not too deep), strong relative volume,
        // a confirmed uptrend, an intraday reversal, and a tight spread.
        BigDecimal idealPullback = cfg.minimumPullbackPercent()
                .add(cfg.maximumPullbackPercent()).divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
        int score = pullbackQuality(c.pullbackPercent(), cfg.minimumPullbackPercent(), idealPullback, cfg.maximumPullbackPercent())
                + bounded(c.relativeVolume(), cfg.minimumRelativeVolume(), new BigDecimal("4"), 25)
                + (passesTrend(c, cfg.trendFilter()) ? 20 : 0)
                + (c.intradayReversal() ? 15 : 0)
                + (c.spreadPercent() == null || c.spreadPercent().compareTo(BigDecimal.ONE) <= 0 ? 10 : 5);
        return Math.min(100, score);
    }

    private boolean passesScoreThreshold(DipHunterRecommendation recommendation) {
        if (recommendation.strategyScore() >= MINIMUM_RECOMMENDATION_SCORE) {
            return true;
        }
        decisionLog.accept("[Dip Hunter] Rejected " + recommendation.symbol() + ": score "
                + recommendation.strategyScore() + " below minimum " + MINIMUM_RECOMMENDATION_SCORE + ".");
        return false;
    }

    private DipHunterRecommendation toRecommendation(DipHunterCandidate c, DipHunterConfig cfg, int score) {
        BigDecimal entry = plannedEntryPrice(c);
        BigDecimal stopPrice = entry.multiply(BigDecimal.ONE.subtract(cfg.stopLossPercent().movePointLeft(2))).setScale(2, RoundingMode.HALF_UP);
        BigDecimal targetPrice = entry.multiply(BigDecimal.ONE.add(cfg.takeProfitPercent().movePointLeft(2))).setScale(2, RoundingMode.HALF_UP);
        return new DipHunterRecommendation(c.symbol().toUpperCase(), c.companyName(), c.pullbackPercent(), c.dayChangePercent(),
                c.averageVolume(), c.relativeVolume(), c.currentPrice(), c.previousClose(), c.recentHigh(),
                c.movingAverage20(), c.movingAverage50(), score, cfg.bounceConfirmation(), entry,
                cfg.stopLossPercent(), stopPrice, cfg.takeProfitPercent(), targetPrice,
                DipHunterStatus.RECOMMENDED, cfg.mode(), Instant.now(clock));
    }

    private BigDecimal plannedEntryPrice(DipHunterCandidate c) {
        // Buy the bounce at the current price; never plan below it.
        BigDecimal current = c.currentPrice() == null ? BigDecimal.ZERO : c.currentPrice();
        return current.setScale(2, RoundingMode.HALF_UP);
    }

    private boolean passesTrend(DipHunterCandidate c, DipHunterConfig.TrendFilter filter) {
        return switch (filter) {
            case DISABLED -> true;
            case ABOVE_MA_20 -> c.aboveMa20();
            case ABOVE_MA_50 -> c.aboveMa50();
            case ABOVE_MA_20_OR_50 -> c.aboveMa20() || c.aboveMa50();
        };
    }

    /** Triangular score: 0 at the min/max bounds, full points at the ideal mid-range pullback. */
    private static int pullbackQuality(BigDecimal value, BigDecimal min, BigDecimal ideal, BigDecimal max) {
        int points = 30;
        if (value == null || value.compareTo(min) < 0 || value.compareTo(max) > 0) return 0;
        BigDecimal span = value.compareTo(ideal) <= 0 ? ideal.subtract(min) : max.subtract(ideal);
        if (span.compareTo(BigDecimal.ZERO) <= 0) return points;
        BigDecimal distance = value.subtract(ideal).abs();
        BigDecimal ratio = BigDecimal.ONE.subtract(distance.divide(span, 4, RoundingMode.HALF_UP));
        if (ratio.compareTo(BigDecimal.ZERO) < 0) return 0;
        return ratio.multiply(BigDecimal.valueOf(points)).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private boolean reject(DipHunterCandidate c, String reason) {
        decisionLog.accept("[Dip Hunter] Rejected " + c.symbol() + ": " + reason + ".");
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
