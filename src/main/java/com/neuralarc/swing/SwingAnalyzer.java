package com.neuralarc.swing;

import com.neuralarc.util.SetupScore;

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
    /**
     * The pullback depth a swing entry actually wants, independent of how wide the operator's filter
     * is. Scoring used to peak at the midpoint of the configured band, so a wide filter placed the
     * "perfect" trade at a 25% collapse and left textbook 4-8% pullbacks scoring a fifth of the points.
     */
    private static final BigDecimal IDEAL_PULLBACK_LOW_PERCENT = new BigDecimal("4");
    private static final BigDecimal IDEAL_PULLBACK_HIGH_PERCENT = new BigDecimal("10");
    /** Distance from the rising 50-day MA that counts as "at support", either side of it. */
    private static final BigDecimal IDEAL_SUPPORT_PROXIMITY_PERCENT = new BigDecimal("3");
    /** Beyond this distance from the 50-day MA the entry is no longer a support test. */
    private static final BigDecimal SUPPORT_PROXIMITY_LIMIT_PERCENT = new BigDecimal("15");

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
        // Reward a healthy pullback into the swing-entry band, a fully-stacked uptrend, an entry near
        // rising support, and a favourable reward/risk profile back toward the recent high.
        int score = SetupScore.bandQuality(c.pullbackPercent(),
                        IDEAL_PULLBACK_LOW_PERCENT, IDEAL_PULLBACK_HIGH_PERCENT,
                        cfg.minimumPullbackPercent(), cfg.maximumPullbackPercent(), 30)
                + trendStrength(c)
                + supportProximityPoints(c.supportProximityPercent())
                + rewardRiskPoints(rewardRisk(c, cfg))
                + bounded(c.relativeVolume(), cfg.minimumRelativeVolume(), new BigDecimal("1.5"), 10);
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
     * Target is the recent swing high we expect price to recover toward, floored at the configured
     * target-profit percentage. Taking the high alone made shallow pullbacks plan a reward smaller
     * than their own stop - a sub-1.0 reward/risk that could never score - even though the operator
     * had asked for a 12% target.
     */
    private BigDecimal targetPrice(SwingCandidate c, SwingConfig cfg, BigDecimal entry) {
        BigDecimal percentTarget = entry.multiply(BigDecimal.ONE.add(cfg.targetProfitPercent().movePointLeft(2)))
                .setScale(2, RoundingMode.HALF_UP);
        if (c.recentHigh() == null) {
            return percentTarget;
        }
        return c.recentHigh().setScale(2, RoundingMode.HALF_UP).max(percentTarget);
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

    /**
     * Closer to the rising 50-day MA scores higher, measured either side of it: a pullback that dips
     * just under the average is a support test, not a disqualification, and the trend filter already
     * decides whether the name still counts as an uptrend.
     */
    private int supportProximityPoints(BigDecimal proximityPercent) {
        return SetupScore.proximityQuality(proximityPercent,
                IDEAL_SUPPORT_PROXIMITY_PERCENT, SUPPORT_PROXIMITY_LIMIT_PERCENT, 20);
    }

    private int rewardRiskPoints(BigDecimal rewardRisk) {
        if (rewardRisk == null) return 0;
        if (rewardRisk.compareTo(new BigDecimal("2.5")) >= 0) return 15;
        if (rewardRisk.compareTo(BigDecimal.ONE) < 0) return 0;
        BigDecimal span = new BigDecimal("1.5"); // from 1.0 up to 2.5
        return rewardRisk.subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(15))
                .divide(span, 0, RoundingMode.HALF_UP).intValue();
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
