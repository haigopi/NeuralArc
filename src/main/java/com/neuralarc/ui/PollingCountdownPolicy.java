package com.neuralarc.ui;

/**
 * Pure decision logic for re-anchoring a strategy's "next poll due" countdown.
 * Extracted so the resync-vs-completion race (see {@link TradingFrame#resetPollingCountdown})
 * can be covered by a test without needing a live Swing/TradingFrame instance.
 */
final class PollingCountdownPolicy {

    private PollingCountdownPolicy() {
    }

    record Result(long nextPollDueAtMillis, long lastAppliedPolledAtEpochMilli, long lastAppliedPollIntervalMillis) {
    }

    /**
     * @param countdownActive                whether a countdown is already running for this entry
     * @param currentNextPollDueAtMillis      the currently displayed due time, or {@code <= 0} if unset
     * @param lastAppliedPolledAtEpochMilli   the lastPolledAt (epoch millis) that produced the current due time
     * @param lastAppliedPollIntervalMillis   the poll interval that produced the current due time
     * @param lastPolledAtEpochMillis         the repository snapshot's lastPolledAt, or {@code null} if never polled
     * @param pollIntervalMillis              the strategy's currently configured poll interval
     * @param nowMillis                       current wall-clock time
     */
    static Result resolve(
            boolean countdownActive,
            long currentNextPollDueAtMillis,
            long lastAppliedPolledAtEpochMilli,
            long lastAppliedPollIntervalMillis,
            Long lastPolledAtEpochMillis,
            long pollIntervalMillis,
            long nowMillis
    ) {
        long baseTime = lastPolledAtEpochMillis != null ? lastPolledAtEpochMillis : nowMillis;
        long derivedNextDueAtMillis = baseTime + pollIntervalMillis;

        if (!countdownActive || currentNextPollDueAtMillis <= 0L) {
            return new Result(derivedNextDueAtMillis, baseTime, pollIntervalMillis);
        }

        // A repository snapshot is frequently captured right after due polls are dispatched
        // asynchronously, so lastPolledAt() is often still the PRE-poll value — re-deriving from
        // it on every re-sync would repeatedly clobber the fresh due time a just-completed poll
        // set. Only re-anchor when the snapshot shows an actually-new lastPolledAt (a poll
        // genuinely completed since we last applied one) or the configured interval itself
        // changed (compared directly, not by due-time magnitude — the completion path computes
        // the due time from "now", not from lastPolledAt, so magnitude comparisons can't tell a
        // real interval change apart from the exact stale-snapshot race this guards against).
        boolean newPollObserved = lastPolledAtEpochMillis != null && baseTime != lastAppliedPolledAtEpochMilli;
        boolean intervalChanged = pollIntervalMillis != lastAppliedPollIntervalMillis;

        if (newPollObserved || intervalChanged) {
            return new Result(derivedNextDueAtMillis, baseTime, pollIntervalMillis);
        }
        return new Result(currentNextPollDueAtMillis, lastAppliedPolledAtEpochMilli, lastAppliedPollIntervalMillis);
    }
}
