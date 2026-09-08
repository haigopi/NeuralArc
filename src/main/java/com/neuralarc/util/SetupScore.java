package com.neuralarc.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Scoring shape shared by the pullback/discount scanners.
 *
 * <p>These scanners used to award full marks at the midpoint of the operator's own min/max bounds,
 * which made the "ideal" setup a function of how wide the filter happened to be: with a 0.1%-50%
 * pullback range the perfect trade was a 25% collapse, and a textbook 5% pullback scored a fifth of
 * the available points. The ideal band belongs to the setup, not to the filter, so it is passed in
 * and merely clamped to stay inside the operator's bounds.
 */
public final class SetupScore {
    private SetupScore() {
    }

    /**
     * Full points anywhere inside the ideal band, tapering linearly to zero at the outer bounds.
     *
     * @param value     the measured pullback/discount
     * @param idealLow  start of the band that deserves full marks
     * @param idealHigh end of that band
     * @param min       operator's lower bound; the band is clamped to it
     * @param max       operator's upper bound; the band is clamped to it
     * @param points    points available
     */
    public static int bandQuality(
            BigDecimal value,
            BigDecimal idealLow,
            BigDecimal idealHigh,
            BigDecimal min,
            BigDecimal max,
            int points
    ) {
        if (value == null || min == null || max == null || value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            return 0;
        }
        BigDecimal low = clamp(idealLow, min, max);
        BigDecimal high = clamp(idealHigh, min, max);
        if (high.compareTo(low) < 0) {
            high = low;
        }
        if (value.compareTo(low) >= 0 && value.compareTo(high) <= 0) {
            return points;
        }
        if (value.compareTo(low) < 0) {
            return taper(value.subtract(min), low.subtract(min), points);
        }
        return taper(max.subtract(value), max.subtract(high), points);
    }

    /**
     * Full points when {@code distance} is inside {@code ideal}, tapering to zero at {@code limit}.
     * Distance is taken as an absolute value, so being just under a support level scores like being
     * just over it.
     */
    public static int proximityQuality(BigDecimal distance, BigDecimal ideal, BigDecimal limit, int points) {
        if (distance == null || ideal == null || limit == null || limit.compareTo(ideal) <= 0) {
            return 0;
        }
        BigDecimal absolute = distance.abs();
        if (absolute.compareTo(ideal) <= 0) {
            return points;
        }
        if (absolute.compareTo(limit) >= 0) {
            return 0;
        }
        return taper(limit.subtract(absolute), limit.subtract(ideal), points);
    }

    private static int taper(BigDecimal remaining, BigDecimal span, int points) {
        if (span.compareTo(BigDecimal.ZERO) <= 0) {
            return points;
        }
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        return remaining.multiply(BigDecimal.valueOf(points))
                .divide(span, 0, RoundingMode.HALF_UP)
                .min(BigDecimal.valueOf(points))
                .intValue();
    }

    private static BigDecimal clamp(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (value == null) {
            return min;
        }
        return value.max(min).min(max);
    }
}
