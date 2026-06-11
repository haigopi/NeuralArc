package com.neuralarc.service;

import com.neuralarc.api.AlpacaMarketDataApi;
import com.neuralarc.model.MarketBar;
import com.neuralarc.model.TrendingStock;
import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class WeekendReboundScoreService {
    private static final BigDecimal MIN_AVERAGE_VOLUME = new BigDecimal("2000000");
    private static final BigDecimal MIN_RELATIVE_VOLUME = new BigDecimal("1.50");
    private static final BigDecimal PREFERRED_RSI = new BigDecimal("35.00");
    private static final BigDecimal WATCHLIST_MIN_DECLINE = new BigDecimal("-1.00");
    private static final BigDecimal PREFERRED_MIN_DECLINE = new BigDecimal("-3.00");
    private static final BigDecimal MAX_PREFERRED_DECLINE = new BigDecimal("-12.00");
    private static final BigDecimal CATASTROPHIC_DECLINE = new BigDecimal("-15.00");
    private static final ZoneId MARKET_TIME_ZONE = ZoneId.of("America/New_York");
    private final TechnicalIndicatorService indicators = new TechnicalIndicatorService();

    public List<TrendingStock> topStocks(
            TrendingStocksService trendingStocksService,
            AlpacaMarketDataApi marketDataApi,
            int limit
    ) throws AlpacaScreenerException {
        int requested = Math.max(1, limit);
        List<TrendingStock> candidates = trendingStocksService.weekendReboundCandidates(Math.max(60, requested * 4));
        LocalDate end = LocalDate.now();
        return candidates.stream()
                .map(candidate -> scoreWithMarketData(candidate, marketDataApi, end))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(TrendingStock::trendingScore).reversed()
                        .thenComparing(TrendingStock::symbol))
                .limit(requested)
                .toList();
    }

    public Optional<TrendingStock> score(TrendingStock candidate, List<MarketBar> bars) {
        if (candidate == null || bars == null || bars.size() < 35) {
            return Optional.empty();
        }
        List<MarketBar> orderedBars = bars.stream()
                .filter(bar -> bar.close().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator.comparing(this::barDate))
                .toList();
        if (orderedBars.size() < 35) {
            return Optional.empty();
        }

        MarketBar latest = orderedBars.getLast();
        BigDecimal fridayDecline = fridayDecline(candidate, orderedBars);
        if (fridayDecline.compareTo(WATCHLIST_MIN_DECLINE) > 0 || fridayDecline.compareTo(CATASTROPHIC_DECLINE) < 0) {
            return Optional.empty();
        }
        BigDecimal averageVolume30 = averageVolumeBeforeLatest(orderedBars, 30);
        if (averageVolume30.compareTo(MIN_AVERAGE_VOLUME) < 0) {
            return Optional.empty();
        }
        BigDecimal relativeVolume = latest.volume().divide(averageVolume30, 4, RoundingMode.HALF_UP);
        boolean newLow = isNewLow(orderedBars, latest);

        BigDecimal rsi = rsi14(orderedBars);
        BigDecimal sma20 = indicators.calculateSMA(orderedBars, 20).orElse(latest.close());
        BigDecimal distanceFromSma20 = latest.close().subtract(sma20)
                .divide(sma20, 6, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
        BigDecimal mondayRecovery = historicalMondayRecovery(orderedBars);
        BigDecimal score = oversoldScore(fridayDecline, rsi, distanceFromSma20).multiply(new BigDecimal("0.30"))
                .add(qualityScore(candidate, orderedBars).multiply(new BigDecimal("0.25")))
                .add(volumeCapitulationScore(relativeVolume).multiply(new BigDecimal("0.20")))
                .add(mondayRecovery.multiply(new BigDecimal("0.15")))
                .add(marketStrengthScore(orderedBars, latest, sma20, newLow).multiply(new BigDecimal("0.10")))
                .setScale(2, RoundingMode.HALF_UP);
        if (fridayDecline.compareTo(PREFERRED_MIN_DECLINE) > 0) {
            score = score.multiply(new BigDecimal("0.70")).setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal suggestedEntry = Monetary.round(latest.close().multiply(new BigDecimal("0.9975")));
        String confidence = confidence(score);
        String reason = "Weekend Rebound: Friday decline " + percent(fridayDecline)
                + ", RSI " + one(rsi)
                + ", relative volume " + one(relativeVolume) + "x"
                + ", score " + score.toPlainString()
                + ", suggested entry $" + suggestedEntry.toPlainString()
                + ", suggested size max 5% allocation"
                + ", confidence " + confidence
                + ". Qualified by " + declineQualification(fridayDecline) + ", RSI/MA oversold setup, volume capitulation, "
                + "Monday recovery history, and liquidity gate"
                + (newLow ? "; penalized because it is near the observed yearly low" : "")
                + ". Hold default: Monday close or 3% target first. "
                + "News, earnings-within-3-days, market cap, profitability, and cash-flow exclusions require a fundamentals/news feed review.";
        return Optional.of(new TrendingStock(
                candidate.symbol(),
                candidate.companyName(),
                latest.close(),
                fridayDecline.setScale(2, RoundingMode.HALF_UP),
                latest.volume(),
                candidate.tradeCount(),
                reason,
                score
        ));
    }

    private BigDecimal fridayDecline(TrendingStock candidate, List<MarketBar> bars) {
        if (candidate.dailyChangePercent() != null && candidate.dailyChangePercent().compareTo(BigDecimal.ZERO) < 0) {
            return candidate.dailyChangePercent();
        }
        if (bars.size() < 2) {
            return BigDecimal.ZERO;
        }
        MarketBar latest = bars.getLast();
        MarketBar previous = bars.get(bars.size() - 2);
        if (previous.close().compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return latest.close().subtract(previous.close())
                .divide(previous.close(), 6, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
    }

    private BigDecimal averageVolumeBeforeLatest(List<MarketBar> bars, int period) {
        int endExclusive = bars.size() - 1;
        int start = Math.max(0, endExclusive - period);
        List<MarketBar> window = bars.subList(start, endExclusive);
        if (window.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (MarketBar bar : window) {
            sum = sum.add(bar.volume());
        }
        return sum.divide(BigDecimal.valueOf(window.size()), 4, RoundingMode.HALF_UP);
    }

    private boolean isNewLow(List<MarketBar> bars, MarketBar latest) {
        BigDecimal priorLow = bars.subList(0, bars.size() - 1).stream()
                .map(MarketBar::low)
                .min(Comparator.naturalOrder())
                .orElse(latest.low());
        return latest.low().compareTo(priorLow) <= 0;
    }

    private BigDecimal rsi14(List<MarketBar> bars) {
        int period = 14;
        if (bars.size() < period + 1) {
            return new BigDecimal("50.00");
        }
        BigDecimal gains = BigDecimal.ZERO;
        BigDecimal losses = BigDecimal.ZERO;
        for (int i = bars.size() - period; i < bars.size(); i++) {
            BigDecimal change = bars.get(i).close().subtract(bars.get(i - 1).close());
            if (change.compareTo(BigDecimal.ZERO) >= 0) {
                gains = gains.add(change);
            } else {
                losses = losses.add(change.abs());
            }
        }
        if (losses.compareTo(BigDecimal.ZERO) == 0) {
            return new BigDecimal("100.00");
        }
        BigDecimal rs = gains.divide(losses, 6, RoundingMode.HALF_UP);
        return new BigDecimal("100").subtract(new BigDecimal("100")
                .divide(BigDecimal.ONE.add(rs), 4, RoundingMode.HALF_UP));
    }

    private BigDecimal historicalMondayRecovery(List<MarketBar> bars) {
        int qualified = 0;
        int recovered = 0;
        for (int i = 1; i < bars.size(); i++) {
            if (barDate(bars.get(i)).getDayOfWeek() != DayOfWeek.MONDAY) {
                continue;
            }
            MarketBar previous = bars.get(i - 1);
            if (barDate(previous).getDayOfWeek() != DayOfWeek.FRIDAY || previous.close().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal previousDecline = previous.close().subtract(previous.open())
                    .divide(previous.open(), 6, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
            if (previousDecline.compareTo(PREFERRED_MIN_DECLINE) > 0) {
                continue;
            }
            qualified++;
            if (bars.get(i).close().compareTo(previous.close()) > 0) {
                recovered++;
            }
        }
        if (qualified == 0) {
            return new BigDecimal("50.00");
        }
        return BigDecimal.valueOf(recovered)
                .multiply(new BigDecimal("100"))
                .divide(BigDecimal.valueOf(qualified), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal oversoldScore(BigDecimal decline, BigDecimal rsi, BigDecimal distanceFromSma20) {
        BigDecimal declineScore = scoreRange(decline.abs(), PREFERRED_MIN_DECLINE.abs(), MAX_PREFERRED_DECLINE.abs());
        BigDecimal rsiScore = rsi.compareTo(PREFERRED_RSI) <= 0
                ? new BigDecimal("100")
                : new BigDecimal("100").subtract(rsi.subtract(PREFERRED_RSI).multiply(new BigDecimal("4")));
        BigDecimal smaScore = distanceFromSma20.compareTo(BigDecimal.ZERO) < 0
                ? scoreRange(distanceFromSma20.abs(), BigDecimal.ONE, new BigDecimal("12"))
                : new BigDecimal("25");
        return clamp(declineScore.multiply(new BigDecimal("0.40"))
                .add(rsiScore.multiply(new BigDecimal("0.40")))
                .add(smaScore.multiply(new BigDecimal("0.20"))));
    }

    private BigDecimal qualityScore(TrendingStock stock, List<MarketBar> bars) {
        BigDecimal priceScore = scoreRange(stock.latestPrice(), new BigDecimal("10"), new BigDecimal("150"));
        BigDecimal volumeScore = scoreRange(averageVolumeBeforeLatest(bars, 30), MIN_AVERAGE_VOLUME, new BigDecimal("20000000"));
        return clamp(priceScore.multiply(new BigDecimal("0.35")).add(volumeScore.multiply(new BigDecimal("0.65"))));
    }

    private BigDecimal volumeCapitulationScore(BigDecimal relativeVolume) {
        return clamp(scoreRange(relativeVolume, MIN_RELATIVE_VOLUME, new BigDecimal("3.00")));
    }

    private BigDecimal marketStrengthScore(List<MarketBar> bars, MarketBar latest, BigDecimal sma20, boolean newLow) {
        BigDecimal low = bars.stream().map(MarketBar::low).min(Comparator.naturalOrder()).orElse(latest.low());
        BigDecimal high = bars.stream().map(MarketBar::high).max(Comparator.naturalOrder()).orElse(latest.high());
        BigDecimal rangePosition = high.compareTo(low) <= 0
                ? new BigDecimal("50")
                : latest.close().subtract(low).divide(high.subtract(low), 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
        BigDecimal smaComponent = latest.close().compareTo(sma20) >= 0 ? new BigDecimal("75") : new BigDecimal("45");
        BigDecimal score = rangePosition.multiply(new BigDecimal("0.60")).add(smaComponent.multiply(new BigDecimal("0.40")));
        if (newLow) {
            score = score.multiply(new BigDecimal("0.35"));
        }
        return clamp(score);
    }

    private BigDecimal scoreRange(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (max.compareTo(min) <= 0) {
            return BigDecimal.ZERO;
        }
        return clamp(value.subtract(min)
                .divide(max.subtract(min), 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100")));
    }

    private BigDecimal clamp(BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        if (value.compareTo(new BigDecimal("100")) > 0) {
            return new BigDecimal("100");
        }
        return value;
    }

    private LocalDate barDate(MarketBar bar) {
        String timestamp = bar.timestamp() == null ? "" : bar.timestamp();
        try {
            return Instant.parse(timestamp).atZone(MARKET_TIME_ZONE).toLocalDate();
        } catch (Exception ignored) {
            if (timestamp.length() < 10) {
                return LocalDate.MIN;
            }
            return LocalDate.parse(timestamp.substring(0, Math.min(10, timestamp.length())));
        }
    }

    private Optional<TrendingStock> scoreWithMarketData(
            TrendingStock candidate,
            AlpacaMarketDataApi marketDataApi,
            LocalDate end
    ) {
        try {
            return score(candidate, marketDataApi.getDailyBars(candidate.symbol(), end.minusYears(1), end));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private String confidence(BigDecimal score) {
        if (score.compareTo(new BigDecimal("75")) >= 0) {
            return "High";
        }
        if (score.compareTo(new BigDecimal("60")) >= 0) {
            return "Medium";
        }
        return "Low";
    }

    private String declineQualification(BigDecimal fridayDecline) {
        if (fridayDecline.compareTo(PREFERRED_MIN_DECLINE) > 0) {
            return "watchlist-level current selloff below the preferred -3% Friday threshold";
        }
        return "controlled Friday selloff";
    }

    private String percent(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private String one(BigDecimal value) {
        return value.setScale(1, RoundingMode.HALF_UP).toPlainString();
    }
}
