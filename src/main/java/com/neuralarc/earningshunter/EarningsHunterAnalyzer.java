package com.neuralarc.earningshunter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class EarningsHunterAnalyzer {
    public static final int MINIMUM_RECOMMENDATION_SCORE = 55;
    private final Clock clock;
    private final Consumer<String> decisionLog;

    public EarningsHunterAnalyzer(Clock clock, Consumer<String> decisionLog) {
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.decisionLog = decisionLog == null ? ignored -> { } : decisionLog;
    }

    public List<EarningsHunterRecommendation> analyze(List<EarningsHunterCandidate> candidates, EarningsHunterConfig config) {
        EarningsHunterConfig safeConfig = config == null ? EarningsHunterConfig.defaults(null) : config;
        return candidates.stream().filter(Objects::nonNull)
                .filter(candidate -> passesFilters(candidate, safeConfig))
                .map(candidate -> toRecommendation(candidate, safeConfig, score(candidate, safeConfig)))
                .filter(this::passesScoreThreshold)
                .sorted(Comparator.comparingInt(EarningsHunterRecommendation::strategyScore).reversed())
                .limit(safeConfig.maxStocksToAdd())
                .toList();
    }

    public boolean passesFilters(EarningsHunterCandidate c, EarningsHunterConfig cfg) {
        if (lt(c.currentPrice(), cfg.minimumStockPrice())) return reject(c, "price below minimum");
        if (cfg.maximumStockPrice() != null && gt(c.currentPrice(), cfg.maximumStockPrice())) return reject(c, "price above maximum");
        if (c.averageVolume() < cfg.minimumAverageVolume()) return reject(c, "average volume below minimum");
        if (lt(c.relativeVolume(), cfg.minimumRelativeVolume())) return reject(c, "relative volume below minimum");
        int catalystScore = catalystScore(c);
        if (BigDecimal.valueOf(catalystScore).compareTo(cfg.minimumNewsScore()) < 0) return reject(c, "earnings catalyst score below minimum");
        decisionLog.accept("[Earnings Hunter] Accepted " + c.symbol() + " for scoring.");
        return true;
    }

    public int score(EarningsHunterCandidate c, EarningsHunterConfig cfg) {
        int score = catalystScore(c)
                + bounded(c.relativeVolume(), cfg.minimumRelativeVolume(), new BigDecimal("2"), 20)
                + bounded(c.dayChangePercent().abs(), BigDecimal.ZERO, new BigDecimal("8"), 15)
                + volumePoints(c.averageVolume(), cfg.minimumAverageVolume());
        return Math.min(100, score);
    }

    private EarningsHunterRecommendation toRecommendation(EarningsHunterCandidate c, EarningsHunterConfig cfg, int score) {
        BigDecimal entry = c.currentPrice().setScale(2, RoundingMode.HALF_UP);
        BigDecimal stop = entry.multiply(BigDecimal.ONE.subtract(cfg.stopLossPercent().movePointLeft(2)))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal target = entry.multiply(BigDecimal.ONE.add(cfg.targetProfitPercent().movePointLeft(2)))
                .setScale(2, RoundingMode.HALF_UP);
        return new EarningsHunterRecommendation(c.symbol().toUpperCase(), c.companyName(), c.currentPrice(),
                c.previousClose(), c.dayChangePercent(), c.averageVolume(), c.relativeVolume(), summary(c),
                catalystScore(c), score, entry, cfg.stopLossPercent(), stop, cfg.targetProfitPercent(), target,
                EarningsHunterStatus.RECOMMENDED, cfg.mode(), Instant.now(clock));
    }

    private boolean passesScoreThreshold(EarningsHunterRecommendation recommendation) {
        if (recommendation.strategyScore() >= MINIMUM_RECOMMENDATION_SCORE) {
            return true;
        }
        decisionLog.accept("[Earnings Hunter] Rejected " + recommendation.symbol() + ": score "
                + recommendation.strategyScore() + " below minimum " + MINIMUM_RECOMMENDATION_SCORE + ".");
        return false;
    }

    private int catalystScore(EarningsHunterCandidate c) {
        int count = c.earningsArticles() == null ? 0 : c.earningsArticles().size();
        int score = Math.min(60, 35 + (count * 8));
        if (c.dayChangePercent() != null && c.dayChangePercent().abs().compareTo(new BigDecimal("3")) >= 0) {
            score += 10;
        }
        return Math.min(70, score);
    }

    private String summary(EarningsHunterCandidate c) {
        if (c.earningsArticles() == null || c.earningsArticles().isEmpty()) {
            return "Recent earnings catalyst found.";
        }
        String headline = c.earningsArticles().getFirst().headline();
        return headline.isBlank() ? "Recent earnings catalyst found." : headline;
    }

    private boolean reject(EarningsHunterCandidate c, String reason) {
        decisionLog.accept("[Earnings Hunter] Rejected " + c.symbol() + ": " + reason + ".");
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

    private int volumePoints(long averageVolume, long minimumAverageVolume) {
        if (averageVolume < minimumAverageVolume) return 0;
        if (averageVolume >= minimumAverageVolume * 5) return 15;
        return 8;
    }
}
