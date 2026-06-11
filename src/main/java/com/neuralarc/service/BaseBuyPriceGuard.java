package com.neuralarc.service;

import com.neuralarc.api.AlpacaClient;
import com.neuralarc.model.MarketBar;
import com.neuralarc.model.StrategyConfig;
import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

final class BaseBuyPriceGuard {
    static final BigDecimal DEFAULT_WEAKNESS_REDUCTION_PERCENT =
            StrategyConfig.DEFAULT_BASE_BUY_REPOST_REDUCTION_PERCENT;

    private final BigDecimal weaknessReductionPercent;

    BaseBuyPriceGuard() {
        this(DEFAULT_WEAKNESS_REDUCTION_PERCENT);
    }

    BaseBuyPriceGuard(BigDecimal weaknessReductionPercent) {
        this.weaknessReductionPercent = normalizeReductionPercent(weaknessReductionPercent);
    }

    GuardedPrice guardedBaseBuyPrice(AlpacaClient client, String symbol, BigDecimal originalBasePrice, BigDecimal currentPrice) {
        return guardedBaseBuyPrice(client, symbol, originalBasePrice, currentPrice, weaknessReductionPercent);
    }

    GuardedPrice guardedBaseBuyPrice(
            AlpacaClient client,
            String symbol,
            BigDecimal originalBasePrice,
            BigDecimal currentPrice,
            BigDecimal reductionPercent
    ) {
        BigDecimal basePrice = Monetary.round(originalBasePrice);
        if (basePrice.compareTo(BigDecimal.ZERO) <= 0) {
            return new GuardedPrice(basePrice, "Base buy price is not positive");
        }

        BigDecimal latestPrice = positive(currentPrice) ? Monetary.round(currentPrice) : safeLatestPrice(client, symbol);
        List<MarketBar> bars = safeDailyBars(client, symbol);
        MarketBar previousDailyBar = bars.stream()
                .filter(bar -> positive(bar.close()) || positive(bar.low()))
                .max(Comparator.comparing(MarketBar::timestamp, Comparator.nullsLast(String::compareTo)))
                .orElse(null);

        BigDecimal previousClose = previousDailyBar == null ? BigDecimal.ZERO : Monetary.round(previousDailyBar.close());
        BigDecimal yesterdayLow = previousDailyBar == null ? BigDecimal.ZERO : Monetary.round(previousDailyBar.low());
        BigDecimal weakestObserved = weakestPositive(latestPrice, previousClose, yesterdayLow);
        if (!positive(weakestObserved) || weakestObserved.compareTo(basePrice) > 0) {
            return new GuardedPrice(basePrice, "Market indicators are above base buy limit; keeping original price");
        }

        BigDecimal effectiveReductionPercent = normalizeReductionPercent(reductionPercent);
        BigDecimal reducedFromOriginal = reduce(basePrice, effectiveReductionPercent);
        BigDecimal reducedFromWeakest = reduce(weakestObserved, effectiveReductionPercent);
        BigDecimal guarded = Monetary.round(reducedFromOriginal.min(reducedFromWeakest));
        return new GuardedPrice(guarded, "Market indicators weakened; reduced base buy limit from $"
                + basePrice.toPlainString() + " to $" + guarded.toPlainString());
    }

    private List<MarketBar> safeDailyBars(AlpacaClient client, String symbol) {
        if (client == null || symbol == null || symbol.isBlank()) {
            return List.of();
        }
        try {
            LocalDate end = LocalDate.now();
            return client.getDailyBars(symbol, end.minusDays(7), end);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private BigDecimal safeLatestPrice(AlpacaClient client, String symbol) {
        if (client == null || symbol == null || symbol.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return Monetary.round(client.getLatestPrice(symbol));
        } catch (Exception ex) {
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal reduce(BigDecimal value, BigDecimal reductionPercent) {
        BigDecimal factor = BigDecimal.ONE.subtract(reductionPercent.divide(new BigDecimal("100")));
        return Monetary.round(value.multiply(factor));
    }

    private BigDecimal normalizeReductionPercent(BigDecimal value) {
        BigDecimal normalized = Monetary.round(value);
        if (normalized.compareTo(BigDecimal.ZERO) <= 0) {
            return DEFAULT_WEAKNESS_REDUCTION_PERCENT;
        }
        return normalized;
    }

    private BigDecimal weakestPositive(BigDecimal... values) {
        BigDecimal weakest = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            if (positive(value) && (!positive(weakest) || value.compareTo(weakest) < 0)) {
                weakest = value;
            }
        }
        return weakest;
    }

    private boolean positive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    record GuardedPrice(BigDecimal price, String reason) {
    }
}
