package com.neuralarc.ui;

import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.Color;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * The on-screen event log, with a text filter and a clear action. The recent lines are kept in memory
 * (more than are shown) so a filter can reach back past what is on screen; only matching lines are
 * shown, and new lines appear only while they match. Clearing empties the on-screen log only — the log
 * files on disk are written separately and are never touched.
 */
final class EventLogView {
    private final JTextPane pane;
    private final int maxShown;
    private final int maxKept;
    private final BiFunction<String, Integer, Color> colorFor;
    private final Predicate<String> isFailure;
    private final Color failureBackground;
    private final Deque<String> kept = new ArrayDeque<>();
    private String filter = "";
    private int shown;

    EventLogView(JTextPane pane, int maxShown, int maxKept, BiFunction<String, Integer, Color> colorFor,
                 Predicate<String> isFailure, Color failureBackground) {
        this.pane = pane;
        this.maxShown = maxShown;
        this.maxKept = Math.max(maxShown, maxKept);
        this.colorFor = colorFor;
        this.isFailure = isFailure;
        this.failureBackground = failureBackground;
    }

    /** Adds a line; it is shown only if it matches the current filter. */
    void append(String entry) {
        kept.addLast(entry);
        while (kept.size() > maxKept) {
            kept.removeFirst();
        }
        if (matches(entry)) {
            insert(entry);
            trim();
            pane.setCaretPosition(pane.getDocument().getLength());
        }
    }

    /** Shows only the kept lines containing {@code text} (case-insensitive); blank shows everything. */
    void setFilter(String text) {
        filter = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        rerender();
    }

    String filter() {
        return filter;
    }

    /** Empties the on-screen log and its in-memory lines. */
    void clear() {
        kept.clear();
        rerender();
    }

    int shownLineCount() {
        return shown;
    }

    private boolean matches(String entry) {
        return filter.isEmpty() || entry.toLowerCase(Locale.ROOT).contains(filter);
    }

    private void rerender() {
        StyledDocument document = pane.getStyledDocument();
        try {
            document.remove(0, document.getLength());
        } catch (BadLocationException ignored) {
            // An empty or already-cleared document.
        }
        shown = 0;
        for (String entry : kept) {
            if (matches(entry)) {
                insert(entry);
            }
        }
        trim();
        pane.setCaretPosition(document.getLength());
    }

    private void insert(String entry) {
        StyledDocument document = pane.getStyledDocument();
        SimpleAttributeSet attributes = new SimpleAttributeSet();
        StyleConstants.setForeground(attributes, colorFor.apply(entry, shown));
        if (isFailure.test(entry)) {
            StyleConstants.setBackground(attributes, failureBackground);
        }
        StyleConstants.setFontFamily(attributes, pane.getFont().getFamily());
        StyleConstants.setFontSize(attributes, pane.getFont().getSize());
        try {
            document.insertString(document.getLength(), entry, attributes);
            shown++;
        } catch (BadLocationException ex) {
            throw new IllegalStateException("Failed to append log entry", ex);
        }
    }

    private void trim() {
        StyledDocument document = pane.getStyledDocument();
        javax.swing.text.Element root = document.getDefaultRootElement();
        while (root.getElementCount() > maxShown) {
            javax.swing.text.Element firstLine = root.getElement(0);
            try {
                document.remove(0, firstLine.getEndOffset());
                shown = Math.max(0, shown - 1);
            } catch (BadLocationException e) {
                break;
            }
            root = document.getDefaultRootElement();
        }
    }
}
