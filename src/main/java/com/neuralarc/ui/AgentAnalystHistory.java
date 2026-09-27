package com.neuralarc.ui;

import java.time.Clock;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Bounded, most-recent-first record of questions asked of the AI Analyst and what came back.
 *
 * <p>Kept separate from {@link AgentAnalystDialog} so the ten-entry cap can be tested without
 * driving Swing or the async run itself.
 */
final class AgentAnalystHistory {
    static final int MAX_ENTRIES = 10;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    record Entry(String question, String answer, String statusSummary, String timestamp, boolean warning) {
    }

    private final LinkedList<Entry> entries = new LinkedList<>();
    private final Clock clock;

    AgentAnalystHistory() {
        this(Clock.systemDefaultZone());
    }

    AgentAnalystHistory(Clock clock) {
        this.clock = clock;
    }

    void record(String question, String answer, String statusSummary, boolean warning) {
        entries.addFirst(new Entry(question, answer, statusSummary, LocalTime.now(clock).format(TIME_FORMAT), warning));
        while (entries.size() > MAX_ENTRIES) {
            entries.removeLast();
        }
    }

    List<Entry> entries() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }

    boolean isEmpty() {
        return entries.isEmpty();
    }
}
