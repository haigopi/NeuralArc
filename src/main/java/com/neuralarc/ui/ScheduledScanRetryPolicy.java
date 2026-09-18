package com.neuralarc.ui;

/** Whether a scheduled scan saw too little live data to trust its result. */
final class ScheduledScanRetryPolicy {
    private ScheduledScanRetryPolicy() {
    }

    /**
     * True when at least half the scanned symbols had no bar for today, so any ranking would rest on
     * the few liquid names that happened to print rather than on the list that was discovered.
     */
    static boolean mostlyUnmeasurable(int unmeasurable, int scanned) {
        return scanned > 0 && unmeasurable * 2 >= scanned;
    }
}
