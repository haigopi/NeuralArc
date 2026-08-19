package com.neuralarc.profitshield;

import java.math.BigDecimal;

/**
 * One Profit Shield candidate measured from live Alpaca daily bars. Profit Shield is the defensive
 * book, so the measurements are all about how well a name <em>holds</em> value: how quiet its daily
 * range is ({@code atrPercent}), how deep its worst peak-to-trough decline was over the lookback
 * ({@code maxDrawdownPercent}), how close it still trades to that lookback high
 * ({@code distanceFromHighPercent}), and how many of those sessions closed green
 * ({@code upSessionsPercent}).
 *
 * <p>{@code supportPrice} is the nearest structural shelf below the current price (the higher of the
 * rising 50-day average and the 20-session low). The analyzer uses it to tighten the protective stop
 * so a shielded position gives back as little of an existing gain as possible.
 */
public record ProfitShieldCandidate(
        String symbol,
        String companyName,
        BigDecimal currentPrice,
        BigDecimal previousClose,
        BigDecimal dayChangePercent,
        long averageVolume,
        BigDecimal relativeVolume,
        BigDecimal ma20,
        BigDecimal ma50,
        BigDecimal ma200,
        boolean aboveMa50,
        boolean aboveMa200,
        boolean risingTrendStack,
        BigDecimal atrPercent,
        BigDecimal maxDrawdownPercent,
        BigDecimal distanceFromHighPercent,
        BigDecimal upSessionsPercent,
        BigDecimal supportPrice,
        int sessionsAnalyzed
) {}
