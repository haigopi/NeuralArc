package com.neuralarc.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Relative volume for scanners that run while the session is still open.
 *
 * <p>Comparing the volume traded so far today against a full 20-day average daily volume always
 * understates a stock mid-session: at noon a perfectly normal name has traded roughly half its usual
 * day, so a raw ratio reads ~0.5 and any "minimum relative volume" of 1.0 rejects the entire market.
 * Scaling the average by the fraction of the session already elapsed makes the ratio mean what the
 * operator expects - "busier than usual for this time of day" - at any point in the session.
 */
public final class IntradayVolumeSupport {
    private static final ZoneId MARKET_ZONE = ZoneId.of("America/New_York");
    private static final LocalTime SESSION_OPEN = LocalTime.of(9, 30);
    private static final LocalTime SESSION_CLOSE = LocalTime.of(16, 0);
    private static final BigDecimal SESSION_MINUTES =
            BigDecimal.valueOf(Duration.between(SESSION_OPEN, SESSION_CLOSE).toMinutes());
    /** Floor so the first minutes after the open cannot divide a small volume by ~zero. */
    private static final BigDecimal MINIMUM_FRACTION = new BigDecimal("0.10");

    private IntradayVolumeSupport() {
    }

    /**
     * Fraction of the 9:30-16:00 ET regular session already elapsed, in (0, 1]. Before the open and
     * after the close the whole session is assumed, so premarket and post-session scans compare
     * against a full day rather than inflating the ratio.
     */
    public static BigDecimal sessionFractionElapsed(Clock clock) {
        ZonedDateTime now = ZonedDateTime.now(clock == null ? Clock.systemUTC() : clock).withZoneSameInstant(MARKET_ZONE);
        LocalTime time = now.toLocalTime();
        if (!time.isAfter(SESSION_OPEN) || !time.isBefore(SESSION_CLOSE)) {
            return BigDecimal.ONE;
        }
        BigDecimal elapsed = BigDecimal.valueOf(Duration.between(SESSION_OPEN, time).toMinutes());
        BigDecimal fraction = elapsed.divide(SESSION_MINUTES, 4, RoundingMode.HALF_UP);
        return fraction.max(MINIMUM_FRACTION);
    }

    /**
     * Volume traded so far against the volume this stock would normally have traded by now.
     *
     * @param volumeSoFar        volume traded in the session up to this moment
     * @param averageDailyVolume average full-day volume over the recent lookback
     * @param sessionFraction    from {@link #sessionFractionElapsed(Clock)}
     */
    public static BigDecimal timeAdjustedRelativeVolume(
            BigDecimal volumeSoFar,
            BigDecimal averageDailyVolume,
            BigDecimal sessionFraction
    ) {
        if (volumeSoFar == null || averageDailyVolume == null
                || averageDailyVolume.compareTo(BigDecimal.ZERO) <= 0
                || volumeSoFar.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal fraction = sessionFraction == null || sessionFraction.compareTo(BigDecimal.ZERO) <= 0
                ? BigDecimal.ONE
                : sessionFraction.min(BigDecimal.ONE);
        BigDecimal expected = averageDailyVolume.multiply(fraction);
        if (expected.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return volumeSoFar.divide(expected, 2, RoundingMode.HALF_UP);
    }
}
