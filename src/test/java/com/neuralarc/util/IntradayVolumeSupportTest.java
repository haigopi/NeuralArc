package com.neuralarc.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntradayVolumeSupportTest {
    private static final ZoneId ET = ZoneId.of("America/New_York");

    @Test
    void middaySessionIsAboutHalfElapsed() {
        // 12:45 ET is 195 of the session's 390 minutes.
        BigDecimal fraction = IntradayVolumeSupport.sessionFractionElapsed(
                Clock.fixed(Instant.parse("2026-09-08T16:45:00Z"), ET));

        assertEquals(new BigDecimal("0.5000"), fraction);
    }

    @Test
    void outsideTheSessionAWholeDayIsAssumed() {
        assertEquals(BigDecimal.ONE, IntradayVolumeSupport.sessionFractionElapsed(
                Clock.fixed(Instant.parse("2026-09-08T12:00:00Z"), ET)));   // 08:00 ET, premarket
        assertEquals(BigDecimal.ONE, IntradayVolumeSupport.sessionFractionElapsed(
                Clock.fixed(Instant.parse("2026-09-08T21:30:00Z"), ET)));   // 17:30 ET, after the close
    }

    @Test
    void theFirstMinutesAfterTheOpenAreFloored() {
        // 09:33 ET: without a floor a few minutes of volume would divide by ~0.008 and read as 100x.
        BigDecimal fraction = IntradayVolumeSupport.sessionFractionElapsed(
                Clock.fixed(Instant.parse("2026-09-08T13:33:00Z"), ET));

        assertEquals(new BigDecimal("0.10"), fraction);
    }

    @Test
    void anAverageStockAtMiddayReadsAsAverage() {
        // Half the session gone, half the usual daily volume traded: this stock is exactly normal, and
        // the old raw ratio called it 0.5 - below every configured minimum.
        BigDecimal relativeVolume = IntradayVolumeSupport.timeAdjustedRelativeVolume(
                new BigDecimal("500000"), new BigDecimal("1000000"), new BigDecimal("0.5"));

        assertEquals(new BigDecimal("1.00"), relativeVolume);
    }

    @Test
    void abusyStockStillReadsAboveOne() {
        BigDecimal relativeVolume = IntradayVolumeSupport.timeAdjustedRelativeVolume(
                new BigDecimal("900000"), new BigDecimal("1000000"), new BigDecimal("0.5"));

        assertTrue(relativeVolume.compareTo(BigDecimal.ONE) > 0);
        assertEquals(new BigDecimal("1.80"), relativeVolume);
    }

    @Test
    void aQuietStockStillReadsBelowOne() {
        BigDecimal relativeVolume = IntradayVolumeSupport.timeAdjustedRelativeVolume(
                new BigDecimal("200000"), new BigDecimal("1000000"), new BigDecimal("0.5"));

        assertEquals(new BigDecimal("0.40"), relativeVolume);
    }

    @Test
    void missingOrZeroInputsAreZeroRatherThanADivideByZero() {
        assertEquals(BigDecimal.ZERO, IntradayVolumeSupport.timeAdjustedRelativeVolume(
                null, new BigDecimal("1000000"), new BigDecimal("0.5")));
        assertEquals(BigDecimal.ZERO, IntradayVolumeSupport.timeAdjustedRelativeVolume(
                new BigDecimal("100"), BigDecimal.ZERO, new BigDecimal("0.5")));
        assertEquals(BigDecimal.ZERO, IntradayVolumeSupport.timeAdjustedRelativeVolume(
                BigDecimal.ZERO, new BigDecimal("1000000"), null));
    }

    @Test
    void aFractionAboveOneNeverInflatesTheRatio() {
        assertEquals(new BigDecimal("0.50"), IntradayVolumeSupport.timeAdjustedRelativeVolume(
                new BigDecimal("500000"), new BigDecimal("1000000"), new BigDecimal("3")));
    }
}
