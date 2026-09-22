package com.neuralarc.ui;

/** Which rows Change Shares & Time In Force works on: entries that have not filled a single share. */
final class SharesAndTimeInForceTargets {
    private SharesAndTimeInForceTargets() {
    }

    static boolean isTarget(ManagedStrategy entry) {
        return isWorkingEntry(entry) || isNotPlacedEntry(entry);
    }

    /** An entry limit buy working at the broker: cancelled and placed again with the new values. */
    static boolean isWorkingEntry(ManagedStrategy entry) {
        return PortfolioActionMatchers.hasUnfilledEntryBuy(entry);
    }

    /** A recommendation whose base buy was never placed: only its stored values change. */
    static boolean isNotPlacedEntry(ManagedStrategy entry) {
        return entry != null && entry.strategy != null
                && entry.cachedPosition().getTotalShares() == 0
                && PendingBaseBuyPlacementSupport.isPendingBaseBuyPlacement(entry.strategy);
    }
}
