package com.neuralarc.diphunter;

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
 * Filters and scores Dip Hunter candidates: a pullback of the right depth in a name that is still in
 * an uptrend and showing the configured bounce confirmation. Pure/clock-driven for unit testing.
 */
public final class DipHunterAnalyzer {
    public static final int MINIMUM_RECOMMENDATION_SCORE = 60;
    /**
     * The dip depth worth buying, independent of how wide the operator's filter is. Scoring used to
     * peak at the midpoint of the configured band, so a 0.1-50% filter made a 25% collapse the ideal
     * dip and left a textbook 3-5% pullback scoring almost nothing.
     */
    private static final BigDecimal IDEAL_PULLBACK_LOW_PERCENT = new BigDecimal("3");
    private static final BigDecimal IDEAL_PULLBACK_HIGH_PERCENT = new BigDecimal("8");
    /**
     * A day whose whole high-to-low range exceeds this is a disorderly move, not a dip. This is the
     * day's range, not a bid/ask spread: the old 3.5% guard rejected ordinary volatile large caps
     * (INTC among them) for a "spread" they never had.
     */
    private static final BigDecimal MAX_INTRADAY_RANGE_PERCENT = new BigDecimal("12");
    /** A dip that stays inside this range is an orderly pullback rather than a slide. */
    private static final BigDecimal ORDERLY_RANGE_PERCENT = new BigDecimal("6");
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
        if (c.intradayRangePercent() != null && c.intradayRangePercent().compareTo(MAX_INTRADAY_RANGE_PERCENT) > 0) {
            return reject(c, "intraday range too wide (disorderly move, not a dip)");
        }
        decisionLog.accept("[Dip Hunter] Accepted " + c.symbol() + " for scoring.");
        return true;
    }

    public int score(DipHunterCandidate c, DipHunterConfig cfg) {
        // Reward a pullback inside the buyable-dip band, strong relative volume, a confirmed uptrend,
        // an intraday reversal, and an orderly session.
        int score = SetupScore.bandQuality(c.pullbackPercent(),
                        IDEAL_PULLBACK_LOW_PERCENT, IDEAL_PULLBACK_HIGH_PERCENT,
                        cfg.minimumPullbackPercent(), cfg.maximumPullbackPercent(), 30)
                + bounded(c.relativeVolume(), cfg.minimumRelativeVolume(), new BigDecimal("2"), 25)
                + (passesTrend(c, cfg.trendFilter()) ? 20 : 0)
                + (c.intradayReversal() ? 15 : 0)
                + orderlinessPoints(c.intradayRangePercent());
        return Math.min(100, score);
    }

    /** An orderly pullback bounces; a stock sliding all session keeps sliding. */
    private int orderlinessPoints(BigDecimal intradayRangePercent) {
        if (intradayRangePercent == null || intradayRangePercent.compareTo(ORDERLY_RANGE_PERCENT) <= 0) {
            return 10;
        }
        return 5;
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
