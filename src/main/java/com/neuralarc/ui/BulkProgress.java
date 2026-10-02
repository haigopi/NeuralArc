package com.neuralarc.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * What a bulk run has got through so far, and what to say about it.
 *
 * <p>A run over fifty stocks is fifty broker round trips. Without a count in front of them an operator
 * cannot tell a slow run from a wedged one, and the honest answer — which stock, how many are left —
 * is cheap to keep. This holds that answer; {@link BulkProgressDialog} only paints it.
 *
 * <p>Deliberately free of Swing so the counting and the wording can be tested without a screen.
 */
final class BulkProgress {
    /** How one item ended. Skipped is not a failure: it means there was nothing to do for that stock. */
    enum Outcome {
        DONE, SKIPPED, FAILED
    }

    record Line(String text, Outcome outcome) {
    }

    private final String title;
    private final int total;
    private final List<Line> lines = new ArrayList<>();
    private int done;
    private int skipped;
    private int failed;

    BulkProgress(String title, int total) {
        this.title = title == null || title.isBlank() ? "Working" : title;
        this.total = Math.max(0, total);
    }

    String title() {
        return title;
    }

    int total() {
        return total;
    }

    int completed() {
        return done + skipped + failed;
    }

    void record(String text, Outcome outcome) {
        Outcome safe = outcome == null ? Outcome.DONE : outcome;
        lines.add(new Line(text == null ? "" : text, safe));
        switch (safe) {
            case DONE -> done++;
            case SKIPPED -> skipped++;
            case FAILED -> failed++;
        }
    }

    /** Replaces the running tally with the authoritative one the action finished with. */
    void settle(List<String> successes, List<String> skippedItems, List<String> failures) {
        done = size(successes);
        skipped = size(skippedItems);
        failed = size(failures);
        if (lines.size() < done + skipped + failed) {
            // A run that never reported item by item still deserves a readable list at the end.
            lines.clear();
            add(successes, Outcome.DONE);
            add(skippedItems, Outcome.SKIPPED);
            add(failures, Outcome.FAILED);
        }
    }

    List<Line> lines() {
        return List.copyOf(lines);
    }

    List<Line> failures() {
        return lines.stream().filter(line -> line.outcome() == Outcome.FAILED).toList();
    }

    /** 0–100, and 100 for an empty run so the bar is never left looking stuck at nothing. */
    int percent() {
        if (total <= 0) {
            return 100;
        }
        return Math.min(100, (int) Math.round(completed() * 100.0 / total));
    }

    boolean finished() {
        return completed() >= total;
    }

    /** Nothing failed, which is what lets the window close itself. */
    boolean clean() {
        return failed == 0;
    }

    /** "12 of 57 · AAPL" — the line that proves the run is moving. */
    String headline(String current) {
        String position = completed() + " of " + total;
        return current == null || current.isBlank() ? position : position + " · " + current;
    }

    /** "49 done · 5 skipped · 3 failed", leaving out whatever did not happen. */
    String summary() {
        StringBuilder text = new StringBuilder(done + " done");
        if (skipped > 0) {
            text.append(" · ").append(skipped).append(" skipped");
        }
        if (failed > 0) {
            text.append(" · ").append(failed).append(" failed");
        }
        return text.toString();
    }

    /**
     * Which outcome an app-written result line describes. The bulk loops all write
     * "SYMBOL: placed…", "SYMBOL: skipped…" or "SYMBOL: failed…", so the line itself says how it ended.
     */
    static Outcome classify(String line) {
        String text = line == null ? "" : line.toLowerCase(java.util.Locale.ROOT);
        if (text.contains(": failed") || text.startsWith("failed")) {
            return Outcome.FAILED;
        }
        return text.contains(": skipped") ? Outcome.SKIPPED : Outcome.DONE;
    }

    /** Splits already-finished result lines into the three buckets {@link #settle} expects. */
    static List<String> only(List<String> lines, Outcome outcome) {
        return lines == null ? List.of() : lines.stream().filter(line -> classify(line) == outcome).toList();
    }

    private void add(List<String> items, Outcome outcome) {
        if (items != null) {
            items.forEach(item -> lines.add(new Line(item == null ? "" : item, outcome)));
        }
    }

    private static int size(List<String> items) {
        return items == null ? 0 : items.size();
    }
}
