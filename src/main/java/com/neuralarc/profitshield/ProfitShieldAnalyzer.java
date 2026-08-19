package com.neuralarc.profitshield;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Filters and ranks {@link ProfitShieldCandidate}s into defensive {@link ProfitShieldRecommendation}s.
 *
 * <p>Scoring is deliberately inverted relative to the momentum scanners: the points come from how
 * <em>little</em> a name moves against its holder. {@code protectionScore} (0–60) rewards a quiet
 * daily range, a shallow worst-case drawdown, and a high share of green sessions; the remaining 40
 * points come from an intact long-term trend, proximity to the lookback high, and liquidity.
 *
 * <p>The planned stop is the tighter of the configured protective stop and the nearest support shelf,
 * floored at half the configured stop so a shelf sitting right under price cannot produce a
 * whipsaw-width stop.
 */
public final class ProfitShieldAnalyzer {
    public static final int MINIMUM_RECOMMENDATION_SCORE = 60;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    /** Park the stop just under the shelf rather than exactly on it. */
    private static final BigDecimal SHELF_UNDERCUT = new BigDecimal("0.995");

    private final Clock clock;
    private final Consumer<String> decisionLog;

    public ProfitShieldAnalyzer(Clock clock, Consumer<String> decisionLog) {
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.decisionLog = decisionLog == null ? ignored -> { } : decisionLog;
    }

    public List<ProfitShieldRecommendation> analyze(List<ProfitShieldCandidate> candidates, ProfitShieldConfig config) {
        if (candidates == null) {
            return List.of();
        }
        ProfitShieldConfig safeConfig = config == null ? ProfitShieldConfig.defaults(null) : config;
        return candidates.stream().filter(Objects::nonNull)
                .filter(candidate -> passesFilters(candidate, safeConfig))
                .map(candidate -> toRecommendation(candidate, safeConfig))
                .filter(this::passesScoreThreshold)
                .sorted(Comparator.comparingInt(ProfitShieldRecommendation::strategyScore).reversed())
                .limit(safeConfig.maxStocksToAdd())
                .toList();
    }

    public boolean passesFilters(ProfitShieldCandidate c, ProfitShieldConfig cfg) {
        if (lt(c.currentPrice(), cfg.minimumStockPrice())) return reject(c, "price below minimum");
        if (cfg.maximumStockPrice() != null && gt(c.currentPrice(), cfg.maximumStockPrice())) return reject(c, "price above maximum");
        if (c.averageVolume() < cfg.minimumAverageVolume()) return reject(c, "average volume below minimum");
        if (gt(c.atrPercent(), cfg.maximumDailyVolatilityPercent())) return reject(c, "daily volatility above maximum");
        if (gt(c.maxDrawdownPercent(), cfg.maximumDrawdownPercent())) return reject(c, "lookback drawdown deeper than maximum");
        if (gt(c.distanceFromHighPercent(), cfg.maximumDistanceFromHighPercent())) return reject(c, "trading too far below its lookback high");
        if (!passesTrendFilter(c, cfg.trendFilter())) return reject(c, "long-term trend not intact");
        decisionLog.accept("[Profit Shield] Accepted " + c.symbol() + " for scoring.");
        return true;
    }

    private boolean passesTrendFilter(ProfitShieldCandidate c, ProfitShieldConfig.TrendFilter filter) {
        return switch (filter) {
            case DISABLED -> true;
            case ABOVE_MA_50 -> c.aboveMa50();
            case ABOVE_MA_50_AND_200 -> c.aboveMa50() && c.aboveMa200();
        };
    }

    /** Defensive quality only: quiet range, shallow drawdown, and green-session resilience (0–60). */
    public int protectionScore(ProfitShieldCandidate c, ProfitShieldConfig cfg) {
        int quiet = inverseBounded(c.atrPercent(), new BigDecimal("0.75"), cfg.maximumDailyVolatilityPercent(), 25);
        int shallow = inverseBounded(c.maxDrawdownPercent(), new BigDecimal("5"), cfg.maximumDrawdownPercent(), 20);
        int resilient = bounded(c.upSessionsPercent(), new BigDecimal("45"), new BigDecimal("60"), 15);
        return Math.min(60, quiet + shallow + resilient);
    }

    public int score(ProfitShieldCandidate c, ProfitShieldConfig cfg) {
        int trend = (c.aboveMa50() ? 8 : 0) + (c.aboveMa200() ? 10 : 0) + (c.risingTrendStack() ? 7 : 0);
        int nearHigh = inverseBounded(c.distanceFromHighPercent(), BigDecimal.ZERO, cfg.maximumDistanceFromHighPercent(), 10);
        int liquidity = c.averageVolume() >= cfg.minimumAverageVolume() * 3 ? 5
                : c.averageVolume() >= cfg.minimumAverageVolume() ? 3 : 0;
        return Math.min(100, protectionScore(c, cfg) + trend + nearHigh + liquidity);
    }

    private ProfitShieldRecommendation toRecommendation(ProfitShieldCandidate c, ProfitShieldConfig cfg) {
        BigDecimal entry = plannedEntryPrice(c, cfg);
        BigDecimal stop = protectiveStopPrice(entry, c.supportPrice(), cfg);
        BigDecimal effectiveStopPercent = entry.subtract(stop)
                .multiply(HUNDRED).divide(entry, 2, RoundingMode.HALF_UP);
        BigDecimal target = entry.multiply(BigDecimal.ONE.add(cfg.targetProfitPercent().movePointLeft(2)))
                .setScale(2, RoundingMode.HALF_UP);
        return new ProfitShieldRecommendation(c.symbol().toUpperCase(), c.companyName(), c.currentPrice(),
                c.previousClose(), c.dayChangePercent(), c.averageVolume(), c.relativeVolume(),
                c.atrPercent(), c.maxDrawdownPercent(), c.distanceFromHighPercent(), summary(c),
                protectionScore(c, cfg), score(c, cfg), entry, effectiveStopPercent, stop,
                cfg.targetProfitPercent(), target, ProfitShieldStatus.RECOMMENDED, cfg.mode(), Instant.now(clock));
    }

    private BigDecimal plannedEntryPrice(ProfitShieldCandidate c, ProfitShieldConfig cfg) {
        BigDecimal current = c.currentPrice() == null ? BigDecimal.ZERO : c.currentPrice();
        if (current.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal entry = current.multiply(BigDecimal.ONE.subtract(cfg.entryDiscountPercent().movePointLeft(2)));
        return entry.min(current).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * The tighter of the flat protective stop and the support shelf, never closer to the entry than
     * half the configured stop and never at or above the entry.
     */
    private BigDecimal protectiveStopPrice(BigDecimal entry, BigDecimal supportPrice, ProfitShieldConfig cfg) {
        BigDecimal percentStop = entry.multiply(BigDecimal.ONE.subtract(cfg.protectiveStopPercent().movePointLeft(2)));
        BigDecimal stop = percentStop;
        if (supportPrice != null && supportPrice.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal shelfStop = supportPrice.multiply(SHELF_UNDERCUT);
            BigDecimal tightestAllowed = entry.multiply(
                    BigDecimal.ONE.subtract(cfg.protectiveStopPercent().movePointLeft(2).divide(BigDecimal.valueOf(2), 6, RoundingMode.HALF_UP)));
            stop = percentStop.max(shelfStop.min(tightestAllowed));
        }
        return stop.min(entry.multiply(SHELF_UNDERCUT)).setScale(2, RoundingMode.HALF_UP);
    }

    private boolean passesScoreThreshold(ProfitShieldRecommendation recommendation) {
        if (recommendation.strategyScore() >= MINIMUM_RECOMMENDATION_SCORE) {
            return true;
        }
        decisionLog.accept("[Profit Shield] Rejected " + recommendation.symbol() + ": score "
                + recommendation.strategyScore() + " below minimum " + MINIMUM_RECOMMENDATION_SCORE + ".");
        return false;
    }

    private String summary(ProfitShieldCandidate c) {
        return "Quiet " + c.atrPercent().toPlainString() + "% daily range, "
                + c.maxDrawdownPercent().toPlainString() + "% worst drawdown over "
                + c.sessionsAnalyzed() + " sessions, "
                + c.distanceFromHighPercent().toPlainString() + "% off its lookback high.";
    }

    private boolean reject(ProfitShieldCandidate c, String reason) {
        decisionLog.accept("[Profit Shield] Rejected " + c.symbol() + ": " + reason + ".");
        return false;
    }

    private static boolean lt(BigDecimal a, BigDecimal b) { return a == null || a.compareTo(b) < 0; }
    private static boolean gt(BigDecimal a, BigDecimal b) { return a != null && a.compareTo(b) > 0; }

    /** Linear points where a higher value is better: none at {@code min}, all at {@code full}. */
    private static int bounded(BigDecimal value, BigDecimal min, BigDecimal full, int points) {
        if (value == null || value.compareTo(min) <= 0) return 0;
        if (value.compareTo(full) >= 0) return points;
        BigDecimal span = full.subtract(min);
        if (span.compareTo(BigDecimal.ZERO) <= 0) return points;
        return value.subtract(min).multiply(BigDecimal.valueOf(points)).divide(span, 0, RoundingMode.HALF_UP).intValue();
    }

    /** Linear points where a lower value is better: all at/below {@code best}, none at/above {@code worst}. */
    private static int inverseBounded(BigDecimal value, BigDecimal best, BigDecimal worst, int points) {
        if (value == null) return 0;
        if (value.compareTo(best) <= 0) return points;
        if (value.compareTo(worst) >= 0) return 0;
        BigDecimal span = worst.subtract(best);
        if (span.compareTo(BigDecimal.ZERO) <= 0) return points;
        return worst.subtract(value).multiply(BigDecimal.valueOf(points)).divide(span, 0, RoundingMode.HALF_UP).intValue();
    }
}
