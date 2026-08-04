package com.neuralarc.orb;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class OrbAnalyzer {
    private final Clock clock;
    private final Consumer<String> decisionLog;

    public OrbAnalyzer(Clock clock, Consumer<String> decisionLog) {
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.decisionLog = decisionLog == null ? ignored -> { } : decisionLog;
    }

    public List<OrbRecommendation> analyze(List<OpeningRangeSnapshot> snapshots, List<OrbCandidate> candidates, OrbConfig config) {
        OrbConfig safeConfig = config == null ? OrbConfig.defaults(null) : config;
        Map<String, OrbCandidate> bySymbol = candidates == null ? Map.of() : candidates.stream()
                .collect(Collectors.toMap(OrbCandidate::symbol, c -> c, (a, b) -> a));
        return snapshots.stream()
                .filter(s -> accepted(s, safeConfig))
                .map(s -> toRecommendation(s, bySymbol.get(s.symbol()), safeConfig))
                .sorted(Comparator.comparingInt(OrbRecommendation::score).reversed())
                .limit(safeConfig.maxStocksToAdd())
                .toList();
    }

    private boolean accepted(OpeningRangeSnapshot snapshot, OrbConfig config) {
        if (snapshot == null) return false;
        if (!snapshot.complete()) return reject(snapshot, snapshot.rejectionReason().isBlank() ? "incomplete opening range" : snapshot.rejectionReason());
        if (snapshot.rangePercent().compareTo(config.minimumRangePercent()) < 0) return reject(snapshot, "opening range below minimum percent");
        LocalTime nowEt = LocalTime.ofInstant(clock.instant(), OpeningRangeCaptureService.EASTERN);
        if (nowEt.isAfter(config.latestEntryTimeEt())) return reject(snapshot, "past latest entry time " + config.latestEntryTimeEt() + " ET");
        return true;
    }

    private OrbRecommendation toRecommendation(OpeningRangeSnapshot s, OrbCandidate c, OrbConfig cfg) {
        BigDecimal breakoutEntry = s.high().multiply(BigDecimal.ONE.add(cfg.entryBufferPercent().movePointLeft(2)));
        BigDecimal discountedOpenOrCurrent = discountedLowerOpenOrCurrent(c);
        BigDecimal entry = discountedOpenOrCurrent == null
                ? breakoutEntry.setScale(2, RoundingMode.HALF_UP)
                : breakoutEntry.min(discountedOpenOrCurrent).setScale(2, RoundingMode.HALF_UP);
        BigDecimal rawStop = switch (cfg.stopMode()) {
            case RANGE_LOW, ATR_ADJUSTED -> s.low();
            case MID_RANGE -> s.high().add(s.low()).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        };
        BigDecimal stop = rawStop.setScale(2, RoundingMode.HALF_UP);
        BigDecimal target = entry.multiply(BigDecimal.ONE.add(cfg.takeProfitPercent().movePointLeft(2))).setScale(2, RoundingMode.HALF_UP);
        int score = score(s, c, cfg);
        String rationale = "ORB long breakout: range " + s.low().setScale(2, RoundingMode.HALF_UP).toPlainString()
                + "-" + s.high().setScale(2, RoundingMode.HALF_UP).toPlainString()
                + ", range%=" + s.rangePercent().setScale(2, RoundingMode.HALF_UP).toPlainString()
                + aiRationale(c);
        return new OrbRecommendation(s.symbol(), s.high().setScale(2, RoundingMode.HALF_UP), s.low().setScale(2, RoundingMode.HALF_UP),
                entry, stop, target, score, rationale, cfg.riskPercent(), s.rangePercent(), cfg.rangeDurationMinutes(),
                OrbStatus.RANGE_CAPTURED, cfg.mode(), Instant.now(clock));
    }

    private BigDecimal discountedLowerOpenOrCurrent(OrbCandidate candidate) {
        if (candidate == null) {
            return null;
        }
        BigDecimal lower = lowerPositive(candidate.regularSessionOpen(), candidate.latestPrice());
        if (lower == null) {
            return null;
        }
        return lower.multiply(new BigDecimal("0.95"));
    }

    private BigDecimal lowerPositive(BigDecimal first, BigDecimal second) {
        boolean firstValid = first != null && first.compareTo(BigDecimal.ZERO) > 0;
        boolean secondValid = second != null && second.compareTo(BigDecimal.ZERO) > 0;
        if (firstValid && secondValid) {
            return first.min(second);
        }
        if (firstValid) {
            return first;
        }
        return secondValid ? second : null;
    }

    private String aiRationale(OrbCandidate candidate) {
        if (candidate == null || candidate.aiSummary().isBlank()) {
            return "";
        }
        return ", AI context=" + candidate.aiSummary();
    }

    private int score(OpeningRangeSnapshot s, OrbCandidate c, OrbConfig cfg) {
        int rangeScore = bounded(s.rangePercent(), cfg.minimumRangePercent(), new BigDecimal("3.00"), 45);
        BigDecimal relVol = c == null ? BigDecimal.ZERO : c.relativeVolume();
        int volumeScore = bounded(relVol, cfg.minimumRelativeVolume(), new BigDecimal("5.00"), 35);
        int spreadScore = c == null || c.spreadPercent() == null ? 10 : c.spreadPercent().compareTo(new BigDecimal("0.50")) <= 0 ? 20 : 10;
        int aiScore = c == null || c.aiSummary().isBlank() ? 0 : 10;
        return Math.min(100, rangeScore + volumeScore + spreadScore + aiScore);
    }

    private boolean reject(OpeningRangeSnapshot snapshot, String reason) {
        decisionLog.accept("[ORB] Rejected " + snapshot.symbol() + ": " + reason + ".");
        return false;
    }

    private static int bounded(BigDecimal value, BigDecimal min, BigDecimal full, int points) {
        if (value == null || value.compareTo(min) < 0) return 0;
        if (value.compareTo(full) >= 0) return points;
        BigDecimal span = full.subtract(min);
        if (span.compareTo(BigDecimal.ZERO) <= 0) return points;
        return value.subtract(min).multiply(BigDecimal.valueOf(points)).divide(span, 0, RoundingMode.HALF_UP).intValue();
    }
}
