package com.neuralarc.swing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Filters and scores Swing Vault candidates: a controlled pullback from a recent swing high in a name
 * that is still in a confirmed multi-week uptrend and sitting near rising moving-average support,
 * expecting a multi-day swing back toward that high. Pure/clock-driven for unit testing.
 */
public final class SwingAnalyzer {
    public static final int MINIMUM_RECOMMENDATION_SCORE = 60;
    /** A pullback that lands within this distance above the 50-day MA is treated as "at support". */
    private static final BigDecimal IDEAL_SUPPORT_PROXIMITY_PERCENT = new BigDecimal("6");

    private final Clock clock;
    private final Consumer<String> decisionLog;

    public SwingAnalyzer(Clock clock, Consumer<String> decisionLog) {
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.decisionLog = decisionLog == null ? ignored -> { } : decisionLog;
    }

    public List<SwingRecommendation> analyze(List<SwingCandidate> candidates, SwingConfig config) {
        SwingConfig safeConfig = config == null ? SwingConfig.defaults(null) : config;
        return candidates.stream()
                .filter(Objects::nonNull)
                .filter(candidate -> passesFilters(candidate, safeConfig))
                .map(candidate -> toRecommendation(candidate, safeConfig, score(candidate, safeConfig)))
                .filter(this::passesScoreThreshold)
                .sorted(Comparator.comparingInt(SwingRecommendation::strategyScore).reversed())
                .limit(safeConfig.maxStocksToAdd())
                .toList();
    }

    public boolean passesFilters(SwingCandidate c, SwingConfig cfg) {
        if (lt(c.pullbackPercent(), cfg.minimumPullbackPercent())) return reject(c, "pullback too shallow (not a real dip)");
        if (gt(c.pullbackPercent(), cfg.maximumPullbackPercent())) return reject(c, "pullback too deep (trend may be broken)");
        if (lt(c.currentPrice(), cfg.minimumStockPrice())) return reject(c, "price below minimum");
        if (cfg.maximumStockPrice() != null && gt(c.currentPrice(), cfg.maximumStockPrice())) return reject(c, "price above maximum");
        if (lt(c.relativeVolume(), cfg.minimumRelativeVolume())) return reject(c, "relative volume below minimum");
        if (c.averageVolume() < cfg.minimumAverageVolume()) return reject(c, "average volume below minimum");
        if (!passesTrend(c, cfg.trendFilter())) return reject(c, "not in a confirmed uptrend (trend filter failed)");
        decisionLog.accept("[Swing Vault] Accepted " + c.symbol() + " for scoring.");
        return true;
    }

    public int score(SwingCandidate c, SwingConfig cfg) {
        // Reward a healthy mid-range pullback, a fully-stacked uptrend, an entry near rising support,
        // and a favourable reward/risk profile back toward the recent high.
        BigDecimal idealPullback = cfg.minimumPullbackPercent()
                .add(cfg.maximumPullbackPercent()).divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
        int score = pullbackQuality(c.pullbackPercent(), cfg.minimumPullbackPercent(), idealPullback, cfg.maximumPullbackPercent())
                + trendStrength(c)
                + supportProximityPoints(c.supportProximityPercent())
                + rewardRiskPoints(rewardRisk(c, cfg))
                + bounded(c.relativeVolume(), cfg.minimumRelativeVolume(), new BigDecimal("2"), 10);
        return Math.min(100, score);
    }

    private boolean passesScoreThreshold(SwingRecommendation recommendation) {
        if (recommendation.strategyScore() >= MINIMUM_RECOMMENDATION_SCORE) {
            return true;
        }
        decisionLog.accept("[Swing Vault] Rejected " + recommendation.symbol() + ": score "
                + recommendation.strategyScore() + " below minimum " + MINIMUM_RECOMMENDATION_SCORE + ".");
        return false;
    }

    private SwingRecommendation toRecommendation(SwingCandidate c, SwingConfig cfg, int score) {
        BigDecimal entry = plannedEntryPrice(c);
        BigDecimal stopPrice = entry.multiply(BigDecimal.ONE.subtract(cfg.stopLossPercent().movePointLeft(2)))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal target = targetPrice(c, cfg, entry);
        BigDecimal targetProfitPercent = entry.compareTo(BigDecimal.ZERO) > 0
                ? target.subtract(entry).multiply(BigDecimal.valueOf(100)).divide(entry, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal rewardRisk = rewardRisk(c, cfg).setScale(2, RoundingMode.HALF_UP);
        return new SwingRecommendation(c.symbol().toUpperCase(), c.companyName(), c.currentPrice(), c.recentHigh(),
                c.pullbackPercent(), c.previousClose(), c.dayChangePercent(), c.averageVolume(), c.relativeVolume(),
                c.movingAverage20(), c.movingAverage50(), c.movingAverage200(), score, entry, cfg.stopLossPercent(),
                stopPrice, targetProfitPercent, target, rewardRisk, SwingStatus.RECOMMENDED, cfg.mode(),
                Instant.now(clock));
    }

    private BigDecimal plannedEntryPrice(SwingCandidate c) {
        // Buy the pullback at the current price; never plan below it.
        BigDecimal current = c.currentPrice() == null ? BigDecimal.ZERO : c.currentPrice();
        return current.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Target is the recent swing high we expect price to recover toward; if that high is at or below the
     * entry (a fresh high), fall back to the configured target-profit percentage so the plan still aims
     * meaningfully above the entry.
     */
    private BigDecimal targetPrice(SwingCandidate c, SwingConfig cfg, BigDecimal entry) {
        BigDecimal percentTarget = entry.multiply(BigDecimal.ONE.add(cfg.targetProfitPercent().movePointLeft(2)))
                .setScale(2, RoundingMode.HALF_UP);
        if (c.recentHigh() == null || c.recentHigh().compareTo(entry) <= 0) {
            return percentTarget;
        }
        return c.recentHigh().setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal rewardRisk(SwingCandidate c, SwingConfig cfg) {
        BigDecimal entry = plannedEntryPrice(c);
        if (entry.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal stop = entry.multiply(BigDecimal.ONE.subtract(cfg.stopLossPercent().movePointLeft(2)));
        BigDecimal risk = entry.subtract(stop);
        BigDecimal reward = targetPrice(c, cfg, entry).subtract(entry);
        if (risk.compareTo(BigDecimal.ZERO) <= 0 || reward.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return reward.divide(risk, 4, RoundingMode.HALF_UP);
    }

    private boolean passesTrend(SwingCandidate c, SwingConfig.TrendFilter filter) {
        return switch (filter) {
            case DISABLED -> true;
            case ABOVE_MA_50 -> c.aboveMa50();
            case ABOVE_MA_50_AND_200 -> c.aboveMa50() && c.ma50AboveMa200();
            case STACKED_UPTREND -> c.aboveMa20() && c.aboveMa50() && c.ma50AboveMa200();
        };
    }

    private int trendStrength(SwingCandidate c) {
        // A fully-stacked daily uptrend is the strongest swing backdrop; a partial one still counts.
        if (c.aboveMa20() && c.aboveMa50() && c.ma50AboveMa200()) return 30;
        if (c.aboveMa50() && c.ma50AboveMa200()) return 22;
        if (c.aboveMa50()) return 14;
        return 0;
    }

    /** Closer to rising support (small positive distance above the 50-day MA) scores higher. */
    private int supportProximityPoints(BigDecimal proximityPercent) {
        int points = 20;
        if (proximityPercent == null || proximityPercent.compareTo(BigDecimal.ZERO) < 0) return 0;
        if (proximityPercent.compareTo(IDEAL_SUPPORT_PROXIMITY_PERCENT) >= 0) return 4;
        BigDecimal ratio = BigDecimal.ONE.subtract(
                proximityPercent.divide(IDEAL_SUPPORT_PROXIMITY_PERCENT, 4, RoundingMode.HALF_UP));
        return ratio.multiply(BigDecimal.valueOf(points)).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private int rewardRiskPoints(BigDecimal rewardRisk) {
        if (rewardRisk == null) return 0;
        if (rewardRisk.compareTo(new BigDecimal("2.5")) >= 0) return 15;
        if (rewardRisk.compareTo(BigDecimal.ONE) < 0) return 0;
        BigDecimal span = new BigDecimal("1.5"); // from 1.0 up to 2.5
        return rewardRisk.subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(15))
                .divide(span, 0, RoundingMode.HALF_UP).intValue();
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

    private boolean reject(SwingCandidate c, String reason) {
        decisionLog.accept("[Swing Vault] Rejected " + c.symbol() + ": " + reason + ".");
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
