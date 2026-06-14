package com.neuralarc.gaprocket;

import java.math.BigDecimal;
import java.time.LocalTime;

public final class GapRocketEntryEvaluator {
    private static final BigDecimal RETEST_TOLERANCE = new BigDecimal("0.005");

    public GapRocketStatus evaluate(GapRocketStatus current, GapRocketConfig.EntryStyle style, LocalTime easternTime,
                                    LocalTime marketOpen, int openingRangeMinutes, BigDecimal openingRangeHigh,
                                    BigDecimal openingRangeLow, BigDecimal currentPrice, BigDecimal vwap,
                                    boolean volumeStrong, boolean autoTradingEnabled) {
        if (style == GapRocketConfig.EntryStyle.MANUAL_REVIEW_ONLY) return GapRocketStatus.RECOMMENDED;
        if (easternTime.isBefore(marketOpen)) return GapRocketStatus.RECOMMENDED;
        if (easternTime.isBefore(marketOpen.plusMinutes(openingRangeMinutes))) return GapRocketStatus.WATCHING_OPENING_RANGE;
        if (openingRangeHigh == null || currentPrice == null) return GapRocketStatus.WAITING_FOR_BREAKOUT;
        return switch (style) {
            case OPENING_RANGE_BREAKOUT -> currentPrice.compareTo(openingRangeHigh) > 0 && volumeStrong
                    ? (autoTradingEnabled ? GapRocketStatus.BOUGHT : GapRocketStatus.READY_TO_BUY)
                    : GapRocketStatus.WAITING_FOR_BREAKOUT;
            case BREAKOUT_RETEST -> breakoutRetestStatus(current, openingRangeHigh, currentPrice, volumeStrong);
            case PULLBACK_TO_VWAP -> vwapPullbackStatus(currentPrice, vwap, volumeStrong);
            case MANUAL_REVIEW_ONLY -> GapRocketStatus.RECOMMENDED;
        };
    }

    private GapRocketStatus breakoutRetestStatus(GapRocketStatus current, BigDecimal high, BigDecimal price, boolean volumeStrong) {
        if (current != GapRocketStatus.WAITING_FOR_PULLBACK && price.compareTo(high) > 0) {
            return GapRocketStatus.WAITING_FOR_PULLBACK;
        }
        BigDecimal upper = high.multiply(BigDecimal.ONE.add(RETEST_TOLERANCE));
        if (current == GapRocketStatus.WAITING_FOR_PULLBACK && price.compareTo(high) >= 0 && price.compareTo(upper) <= 0 && volumeStrong) {
            return GapRocketStatus.READY_TO_BUY;
        }
        return current == null ? GapRocketStatus.WAITING_FOR_BREAKOUT : current;
    }

    private GapRocketStatus vwapPullbackStatus(BigDecimal price, BigDecimal vwap, boolean volumeStrong) {
        if (vwap == null || price.compareTo(vwap) < 0) return GapRocketStatus.WAITING_FOR_PULLBACK;
        BigDecimal upper = vwap.multiply(BigDecimal.ONE.add(RETEST_TOLERANCE));
        return price.compareTo(upper) <= 0 && volumeStrong ? GapRocketStatus.READY_TO_BUY : GapRocketStatus.WAITING_FOR_PULLBACK;
    }

    public boolean canSell(int requestedQuantity, int gapRocketOwnedQuantity) {
        return requestedQuantity > 0 && requestedQuantity <= gapRocketOwnedQuantity;
    }
}
