package com.neuralarc.ui;

import java.awt.FontMetrics;
import java.util.List;

/**
 * Fits a {@code " | "}-separated status line into the width the layout actually granted it.
 *
 * <p>Header status lines grow and shrink with live figures and with the automation counters appended
 * to them, so on a narrow window they used to overflow their slot and disappear entirely rather than
 * degrade. Segments are therefore dropped from the right — they are ordered least-important-last —
 * and a horizontal ellipsis marks the trim so the operator can tell the line is abbreviated. Callers
 * keep the untrimmed line for the tooltip.
 */
final class StatusLineFitter {
    static final String ELLIPSIS = "…";
    private static final String SEPARATOR = " | ";

    private StatusLineFitter() {
    }

    /** Passed as {@code availableWidth} when the slot has not been laid out yet, so nothing is trimmed. */
    static final int UNCONSTRAINED = -1;

    /**
     * @param availableWidth pixels the line may occupy. {@link #UNCONSTRAINED} (or any negative value)
     *                       leaves the text untouched; zero means the layout granted no room at all,
     *                       for which the honest result is an empty line rather than an overflowing
     *                       one — callers keep the full line on the tooltip either way.
     */
    static String fit(String text, FontMetrics metrics, int availableWidth) {
        if (text == null || text.isBlank() || metrics == null || availableWidth < 0) {
            return text == null ? "" : text;
        }
        if (availableWidth == 0) {
            return "";
        }
        if (metrics.stringWidth(text) <= availableWidth) {
            return text;
        }
        List<String> segments = List.of(text.split(java.util.regex.Pattern.quote(SEPARATOR), -1));
        for (int keep = segments.size() - 1; keep >= 1; keep--) {
            String candidate = String.join(SEPARATOR, segments.subList(0, keep)) + SEPARATOR + ELLIPSIS;
            if (metrics.stringWidth(candidate) <= availableWidth) {
                return candidate;
            }
        }
        return trimCharacters(segments.getFirst(), metrics, availableWidth);
    }

    private static String trimCharacters(String segment, FontMetrics metrics, int availableWidth) {
        String candidate = segment;
        while (!candidate.isEmpty() && metrics.stringWidth(candidate + ELLIPSIS) > availableWidth) {
            candidate = candidate.substring(0, candidate.length() - 1);
        }
        return candidate.isEmpty() ? ELLIPSIS : candidate + ELLIPSIS;
    }
}
