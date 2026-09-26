package com.neuralarc.ui;

/**
 * Shares the broker holds that a strategy's own orders cannot account for.
 *
 * <p>{@link BrokerPositionAllocator} deliberately parks a broker position's surplus on a visible row
 * so portfolio totals keep matching the broker. That is right for the totals and wrong for the
 * reader: the row then shows more shares than its own timeline ever bought, with nothing saying so.
 * A quantity that disagrees with the order history is worth knowing about immediately — it means
 * something bought shares this strategy has no record of.
 */
final class UntrackedShares {
    private UntrackedShares() {
    }

    /**
     * How many of {@code brokerShares} this strategy's filled orders do not explain.
     *
     * <p>Shorts are not counted: a negative broker position is handed whole to one row rather than
     * split against claims, so the difference there says nothing about missing orders.
     */
    static int count(int brokerShares, int localClaim) {
        if (brokerShares <= 0) {
            return 0;
        }
        return Math.max(0, brokerShares - Math.max(0, localClaim));
    }

    /** The note appended to a row's status line, or empty when everything is accounted for. */
    static String note(int untracked) {
        return untracked <= 0 ? "" : " | " + untracked + " share" + (untracked == 1 ? "" : "s") + " untracked";
    }

    /** What the Shares cell says on hover. Plain text; the caller styles it. */
    static String tooltip(String symbol, int brokerShares, int untracked) {
        if (untracked <= 0) {
            return "";
        }
        int accounted = Math.max(0, brokerShares - untracked);
        return "Your broker holds " + brokerShares + " " + (symbol == null ? "" : symbol)
                + " shares, but this strategy's own orders only account for " + accounted + "."
                + " The other " + untracked + " were bought by something this strategy has no order for —"
                + " check the broker's order history for this symbol before selling or averaging down.";
    }
}
