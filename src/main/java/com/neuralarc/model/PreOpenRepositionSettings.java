package com.neuralarc.model;

/**
 * The standing instruction to re-post expired day entries before the bell.
 *
 * <p>A DAY limit buy that never filled dies at the close, and the strategy sits expired until someone
 * notices. Repositioning by hand means being at the screen before the open; this does it on schedule,
 * with the entry recomputed from the session that just closed rather than re-posting a price the
 * market has already walked away from.
 *
 * <p>Off by default, and narrow on purpose: it only touches entries that expired, and by default only
 * DAY ones, because a GTC order is still working and has not missed anything.
 */
public record PreOpenRepositionSettings(boolean enabled, int minutesBeforeOpen, boolean dayOrdersOnly) {
    public static final int DEFAULT_MINUTES_BEFORE_OPEN = 15;
    /** Early enough to be queued before the bell, late enough that yesterday's close is final. */
    public static final int MIN_MINUTES_BEFORE_OPEN = 1;
    public static final int MAX_MINUTES_BEFORE_OPEN = 120;

    public PreOpenRepositionSettings {
        minutesBeforeOpen = minutesBeforeOpen <= 0
                ? DEFAULT_MINUTES_BEFORE_OPEN
                : Math.max(MIN_MINUTES_BEFORE_OPEN, Math.min(MAX_MINUTES_BEFORE_OPEN, minutesBeforeOpen));
    }

    public static PreOpenRepositionSettings defaults() {
        return new PreOpenRepositionSettings(false, DEFAULT_MINUTES_BEFORE_OPEN, true);
    }

    /** "15 minutes before the open · DAY entries only". */
    public String summary() {
        return minutesBeforeOpen + (minutesBeforeOpen == 1 ? " minute" : " minutes") + " before the open · "
                + (dayOrdersOnly ? "DAY entries only" : "every expired entry");
    }
}
