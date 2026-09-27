package com.neuralarc.ui;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The last few analyst answers, so asking the same thing twice does not buy the same answer twice.
 *
 * <p>The analyst is asked from two places — the AI Analyst tab and the Portfolio Actions dialog — and
 * operators repeat themselves: the same "what should I watch?" typed again after switching tabs is a
 * second billed run for an answer already on screen. One cache behind both surfaces makes the repeat
 * free and instant.
 *
 * <p>It forgets quickly on purpose. An analyst answer is about prices that move, so a stale one is
 * worse than no answer: entries older than {@link #TTL} are ignored, and only the last
 * {@link #MAX_ENTRIES} questions are kept at all.
 */
final class AnalystAnswerCache {
    /** How long an answer still describes the market well enough to repeat. */
    static final Duration TTL = Duration.ofMinutes(15);
    static final int MAX_ENTRIES = 10;

    /** A remembered answer and when it was given. */
    record Answer(String question, AgentAnalystRunner.Outcome outcome, Instant answeredAt) {
        /** "4 minutes ago", for the line that tells the operator this was not re-asked. */
        String ageText(Instant now) {
            long minutes = Duration.between(answeredAt, now).toMinutes();
            if (minutes <= 0) {
                return "moments ago";
            }
            return minutes + (minutes == 1 ? " minute ago" : " minutes ago");
        }
    }

    private final Clock clock;
    private final Map<String, Answer> answers = new LinkedHashMap<>();

    AnalystAnswerCache() {
        this(Clock.systemUTC());
    }

    AnalystAnswerCache(Clock clock) {
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    /** The remembered answer for {@code question}, if it is recent enough to still be worth repeating. */
    synchronized Optional<Answer> find(String question) {
        String key = key(question);
        Answer answer = answers.get(key);
        if (answer == null) {
            return Optional.empty();
        }
        if (Duration.between(answer.answeredAt(), clock.instant()).compareTo(TTL) >= 0) {
            answers.remove(key);
            return Optional.empty();
        }
        return Optional.of(answer);
    }

    /** Remembers an answer, dropping the oldest once {@link #MAX_ENTRIES} is reached. */
    synchronized void remember(String question, AgentAnalystRunner.Outcome outcome) {
        if (question == null || question.isBlank() || outcome == null || outcome.text().isBlank()) {
            return;
        }
        String key = key(question);
        answers.remove(key);
        answers.put(key, new Answer(question.trim(), outcome, clock.instant()));
        while (answers.size() > MAX_ENTRIES) {
            answers.remove(answers.keySet().iterator().next());
        }
    }

    /** The most recent answer of all, for the full-screen reader to open with. */
    synchronized Optional<Answer> mostRecent() {
        Answer latest = null;
        for (Answer answer : answers.values()) {
            latest = answer;
        }
        return Optional.ofNullable(latest);
    }

    synchronized int size() {
        return answers.size();
    }

    synchronized void clear() {
        answers.clear();
    }

    /** Case and spacing should not decide whether a question counts as the same one. */
    private static String key(String question) {
        return question == null ? "" : question.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
