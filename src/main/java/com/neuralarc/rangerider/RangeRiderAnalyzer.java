package com.neuralarc.rangerider;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Filters and scores Range Rider candidates, turning three weeks of averaged daily bars into a
 * same-day plan: buy the dip the stock typically makes below its open, sell the rally it typically
 * makes above it, and close the position before the session ends. Pure/clock-driven for unit testing.
 *
 * <p><strong>Why the plan is a shape, not two fixed prices.</strong> The naive reading of "average the
 * lows and highs, then buy the average low and sell the average high" produces a plan that almost
 * never completes. Two reasons: the average low and the average high are a full daily range apart, so
 * only an unusually wide <em>and</em> perfectly positioned session reaches both; and an absolute price
 * level taken from three weeks ago goes stale as soon as the stock drifts away from it. So the
 * averages are converted into percentages — the typical dip below the open and the typical rally above
 * it — and applied to the latest completed close. That is the same idea, expressed so it still works
 * on the next session.
 *
 * <p><strong>What the fill rate does and does not prove.</strong> The plan is replayed against every
 * session in the window using that session's own open as the anchor, so drift cannot flatter or
 * penalise the result. A session counts as a same-day fill when its low reached the planned dip
 * <em>and</em> its high reached the planned rally. Daily bars do not say whether the low came before
 * the high, so a session whose high printed in the morning and whose low printed in the afternoon is
 * still counted. The rate is therefore an optimistic upper bound on how often the round trip would
 * really have closed the same day — a ranking signal, not a backtested return.
 */
public final class RangeRiderAnalyzer {
    public static final int MINIMUM_RECOMMENDATION_SCORE = 60;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final Clock clock;
    private final Consumer<String> decisionLog;

    public RangeRiderAnalyzer(Clock clock, Consumer<String> decisionLog) {
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.decisionLog = decisionLog == null ? ignored -> { } : decisionLog;
    }

    public List<RangeRiderRecommendation> analyze(List<RangeRiderCandidate> candidates, RangeRiderConfig config) {
        RangeRiderConfig safeConfig = config == null ? RangeRiderConfig.defaults(null) : config;
        if (candidates == null) {
            return List.of();
        }
        return candidates.stream()
                .filter(Objects::nonNull)
                .filter(candidate -> passesFilters(candidate, safeConfig))
                .map(candidate -> toRecommendation(candidate, safeConfig))
                .filter(Objects::nonNull)
                .filter(this::passesScoreThreshold)
                .sorted(Comparator.comparingInt(RangeRiderRecommendation::strategyScore).reversed())
                .limit(safeConfig.maxStocksToAdd())
                .toList();
    }

    /** Structural and liquidity gates that do not depend on the planned prices. */
    public boolean passesFilters(RangeRiderCandidate c, RangeRiderConfig cfg) {
        if (c.sessions().isEmpty()) return reject(c, "no completed sessions to average");
        if (!valid(c.averageLow()) || c.averageHigh().compareTo(c.averageLow()) <= 0) {
            return reject(c, "average high is not above the average low");
        }
        if (lt(c.averageRangePercent(), cfg.minimumAverageRangePercent())) {
            return reject(c, "average daily range of " + c.averageRangePercent().toPlainString()
                    + "% is below the " + cfg.minimumAverageRangePercent().toPlainString()
                    + "% minimum needed to pay for a same-day round trip");
        }
        if (gt(c.averageRangePercent(), cfg.maximumAverageRangePercent())) {
            return reject(c, "average daily range of " + c.averageRangePercent().toPlainString()
                    + "% is too wide to plan against reliably");
        }
        if (lt(c.referencePrice(), cfg.minimumStockPrice())) return reject(c, "price below minimum");
        if (cfg.maximumStockPrice() != null && gt(c.referencePrice(), cfg.maximumStockPrice())) {
            return reject(c, "price above maximum");
        }
        if (c.averageVolume() < cfg.minimumAverageVolume()) return reject(c, "average volume below minimum");
        decisionLog.accept("[Range Rider] Accepted " + c.symbol() + " for scoring.");
        return true;
    }

    /**
     * How far below a session's open to place the buy, as a fraction. This is the typical dip pulled in
     * by the entry buffer, so the limit sits slightly above the typical low and fills before it.
     */
    public BigDecimal entryFraction(RangeRiderCandidate c, RangeRiderConfig cfg) {
        return fraction(c.averageDipPercent().subtract(cfg.entryBufferPercent()));
    }

    /** How far above a session's open to place the sell, as a fraction — the mirror of the entry. */
    public BigDecimal exitFraction(RangeRiderCandidate c, RangeRiderConfig cfg) {
        return fraction(c.averageRallyPercent().subtract(cfg.exitBufferPercent()));
    }

    /** The planned buy for the next session, anchored to the last completed close. */
    public BigDecimal plannedEntryPrice(RangeRiderCandidate c, RangeRiderConfig cfg) {
        return c.referencePrice().multiply(BigDecimal.ONE.subtract(entryFraction(c, cfg)))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** The planned same-day sell, anchored to the same reference price. */
    public BigDecimal plannedTargetPrice(RangeRiderCandidate c, RangeRiderConfig cfg) {
        return c.referencePrice().multiply(BigDecimal.ONE.add(exitFraction(c, cfg)))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** Share of lookback sessions that traded down to the planned dip below their own open. */
    public BigDecimal entryTouchRatePercent(RangeRiderCandidate c, RangeRiderConfig cfg) {
        BigDecimal entry = entryFraction(c, cfg);
        return ratePercent(c.sessions().stream()
                .filter(session -> reachedEntry(session, entry))
                .count(), c.sessions().size());
    }

    /** Share of lookback sessions that reached both the planned dip and the planned rally. */
    public BigDecimal sameDayFillRatePercent(RangeRiderCandidate c, RangeRiderConfig cfg) {
        BigDecimal entry = entryFraction(c, cfg);
        BigDecimal exit = exitFraction(c, cfg);
        return ratePercent(c.sessions().stream()
                .filter(session -> reachedEntry(session, entry))
                .filter(session -> reachedTarget(session, exit))
                .count(), c.sessions().size());
    }

    private static boolean reachedEntry(RangeRiderSession session, BigDecimal entryFraction) {
        if (session.open().compareTo(BigDecimal.ZERO) <= 0) return false;
        return session.low().compareTo(session.open().multiply(BigDecimal.ONE.subtract(entryFraction))) <= 0;
    }

    private static boolean reachedTarget(RangeRiderSession session, BigDecimal exitFraction) {
        if (session.open().compareTo(BigDecimal.ZERO) <= 0) return false;
        return session.high().compareTo(session.open().multiply(BigDecimal.ONE.add(exitFraction))) >= 0;
    }

    /**
     * Ranks a plan 0–100. The dominant term is how often the round trip would have completed in one
     * session; the rest rewards a range that is both worth trading and repeatable, plus real liquidity.
     * Nothing here reads today's price — the plan is built entirely from completed sessions.
     */
    public int score(RangeRiderCandidate c, RangeRiderConfig cfg, BigDecimal fillRate) {
        BigDecimal idealRange = cfg.minimumAverageRangePercent()
                .add(cfg.maximumAverageRangePercent()).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
        int score = bounded(fillRate, cfg.minimumSameDayFillRatePercent(), HUNDRED, 45)
                + rangeQuality(c.averageRangePercent(), cfg.minimumAverageRangePercent(), idealRange, cfg.maximumAverageRangePercent())
                + bounded(c.rangeStabilityPercent(), BigDecimal.ZERO, BigDecimal.valueOf(80), 20)
                + bounded(BigDecimal.valueOf(c.averageVolume()), BigDecimal.valueOf(cfg.minimumAverageVolume()),
                        BigDecimal.valueOf(cfg.minimumAverageVolume()).multiply(BigDecimal.valueOf(4)), 15);
        return Math.min(100, score);
    }

    private RangeRiderRecommendation toRecommendation(RangeRiderCandidate c, RangeRiderConfig cfg) {
        BigDecimal entry = plannedEntryPrice(c, cfg);
        BigDecimal target = plannedTargetPrice(c, cfg);
        if (entry.compareTo(BigDecimal.ZERO) <= 0 || target.compareTo(entry) <= 0) {
            reject(c, "buffers leave no workable spread between the planned buy and sell");
            return null;
        }
        BigDecimal fillRate = sameDayFillRatePercent(c, cfg);
        if (fillRate.compareTo(cfg.minimumSameDayFillRatePercent()) < 0) {
            reject(c, "planned buy and sell both completed on only " + fillRate.toPlainString()
                    + "% of the last " + c.sessionsAnalyzed() + " sessions, below the "
                    + cfg.minimumSameDayFillRatePercent().toPlainString() + "% minimum");
            return null;
        }
        BigDecimal expectedGainPercent = target.subtract(entry)
                .multiply(HUNDRED).divide(entry, 2, RoundingMode.HALF_UP);
        BigDecimal stopLossPrice = entry
                .multiply(BigDecimal.ONE.subtract(cfg.stopLossPercent().movePointLeft(2)))
                .setScale(2, RoundingMode.HALF_UP);
        return new RangeRiderRecommendation(
                c.symbol().toUpperCase(), c.companyName(), c.referencePrice(),
                c.averageOpen(), c.averageHigh(), c.averageLow(),
                c.averageRangePercent(), scale2(c.averageDipPercent()), scale2(c.averageRallyPercent()),
                c.rangeStabilityPercent(),
                entryTouchRatePercent(c, cfg), fillRate, c.sessionsAnalyzed(),
                c.previousClose(), c.dayChangePercent(), c.averageVolume(), c.relativeVolume(),
                score(c, cfg, fillRate), entry, target, expectedGainPercent,
                cfg.stopLossPercent(), stopLossPrice,
                RangeRiderStatus.RECOMMENDED, cfg.mode(), Instant.now(clock));
    }

    private boolean passesScoreThreshold(RangeRiderRecommendation recommendation) {
        if (recommendation.strategyScore() >= MINIMUM_RECOMMENDATION_SCORE) {
            return true;
        }
        decisionLog.accept("[Range Rider] Rejected " + recommendation.symbol() + ": score "
                + recommendation.strategyScore() + " below minimum " + MINIMUM_RECOMMENDATION_SCORE + ".");
        return false;
    }

    /** Triangular score: 0 at the min/max bounds, full points at the ideal mid-range daily range. */
    private static int rangeQuality(BigDecimal value, BigDecimal min, BigDecimal ideal, BigDecimal max) {
        int points = 20;
        if (value == null || value.compareTo(min) < 0 || value.compareTo(max) > 0) return 0;
        BigDecimal span = value.compareTo(ideal) <= 0 ? ideal.subtract(min) : max.subtract(ideal);
        if (span.compareTo(BigDecimal.ZERO) <= 0) return points;
        BigDecimal distance = value.subtract(ideal).abs();
        BigDecimal ratio = BigDecimal.ONE.subtract(distance.divide(span, 4, RoundingMode.HALF_UP));
        if (ratio.compareTo(BigDecimal.ZERO) < 0) return 0;
        return ratio.multiply(BigDecimal.valueOf(points)).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    /** A percentage as a fraction, floored at zero so an over-wide buffer cannot invert the plan. */
    private static BigDecimal fraction(BigDecimal percent) {
        BigDecimal value = percent == null ? BigDecimal.ZERO : percent;
        return value.compareTo(BigDecimal.ZERO) <= 0 ? BigDecimal.ZERO : value.movePointLeft(2);
    }

    private static BigDecimal scale2(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal ratePercent(long matches, int total) {
        if (total <= 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(matches).multiply(HUNDRED)
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    private boolean reject(RangeRiderCandidate c, String reason) {
        decisionLog.accept("[Range Rider] Rejected " + c.symbol() + ": " + reason + ".");
        return false;
    }

    private static boolean valid(BigDecimal value) { return value != null && value.compareTo(BigDecimal.ZERO) > 0; }
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
