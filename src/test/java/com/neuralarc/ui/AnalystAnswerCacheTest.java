package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalystAnswerCacheTest {
    private static final Instant NOON = Instant.parse("2026-09-27T16:00:00Z");

    @Test
    void thesameQuestionIsAnsweredFromMemoryInsteadOfBeingBilledAgain() {
        AnalystAnswerCache cache = new AnalystAnswerCache(Clock.fixed(NOON, ZoneOffset.UTC));
        cache.remember("What should I watch?", outcome("NVDA is holding its averages."));

        AnalystAnswerCache.Answer found = cache.find("  what should i WATCH?  ").orElseThrow();

        assertEquals("NVDA is holding its averages.", found.outcome().text(),
                "case and spacing must not make it a different question");
    }

    @Test
    void anAnswerOlderThanTheWindowIsNotRepeated() {
        MutableClock clock = new MutableClock(NOON);
        AnalystAnswerCache cache = new AnalystAnswerCache(clock);
        cache.remember("What should I watch?", outcome("NVDA is holding."));

        clock.now = NOON.plus(AnalystAnswerCache.TTL).plusSeconds(1);

        assertTrue(cache.find("What should I watch?").isEmpty(),
                "prices move; a stale answer is worse than asking again");
    }

    @Test
    void itRemembersTheLastTenQuestionsAndForgetsTheOldest() {
        AnalystAnswerCache cache = new AnalystAnswerCache(Clock.fixed(NOON, ZoneOffset.UTC));
        for (int i = 1; i <= AnalystAnswerCache.MAX_ENTRIES + 3; i++) {
            cache.remember("question " + i, outcome("answer " + i));
        }

        assertEquals(AnalystAnswerCache.MAX_ENTRIES, cache.size());
        assertTrue(cache.find("question 1").isEmpty(), "the oldest question drops out");
        assertTrue(cache.find("question 13").isPresent());
    }

    @Test
    void askingAgainMovesAQuestionBackToTheFrontOfTheQueue() {
        AnalystAnswerCache cache = new AnalystAnswerCache(Clock.fixed(NOON, ZoneOffset.UTC));
        cache.remember("first", outcome("a"));
        for (int i = 2; i <= AnalystAnswerCache.MAX_ENTRIES; i++) {
            cache.remember("q" + i, outcome("a"));
        }
        cache.remember("first", outcome("a again"));
        cache.remember("one more", outcome("a"));

        assertTrue(cache.find("first").isPresent(), "the question just asked must not be the one dropped");
        assertTrue(cache.find("q2").isEmpty());
    }

    @Test
    void theMostRecentAnswerIsWhatTheFullScreenReaderOpens() {
        AnalystAnswerCache cache = new AnalystAnswerCache(Clock.fixed(NOON, ZoneOffset.UTC));
        cache.remember("older", outcome("first answer"));
        cache.remember("newer", outcome("second answer"));

        assertEquals("second answer", cache.mostRecent().orElseThrow().outcome().text());
    }

    @Test
    void anEmptyAnswerIsNotWorthRemembering() {
        AnalystAnswerCache cache = new AnalystAnswerCache(Clock.fixed(NOON, ZoneOffset.UTC));

        cache.remember("what now?", outcome("   "));
        cache.remember("  ", outcome("something"));

        assertEquals(0, cache.size());
        assertFalse(cache.mostRecent().isPresent());
    }

    @Test
    void theAgeReadsAsSomethingAnOperatorWouldSay() {
        AnalystAnswerCache cache = new AnalystAnswerCache(Clock.fixed(NOON, ZoneOffset.UTC));
        cache.remember("q", outcome("a"));
        AnalystAnswerCache.Answer answer = cache.find("q").orElseThrow();

        assertEquals("moments ago", answer.ageText(NOON));
        assertEquals("1 minute ago", answer.ageText(NOON.plusSeconds(60)));
        assertEquals("4 minutes ago", answer.ageText(NOON.plusSeconds(260)));
    }

    private static AgentAnalystRunner.Outcome outcome(String text) {
        return new AgentAnalystRunner.Outcome("run-1", text, 3, 2, true, false);
    }

    /** A clock the test moves forward by hand. */
    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
