package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalystAnswerDialogTest {
    private static final Instant NOW = Instant.parse("2026-09-27T16:05:00Z");

    @Test
    void theAnswerOpensAtTheTopReadyToRead() {
        AnalystAnswerCache.Answer answer = new AnalystAnswerCache.Answer("What should I watch?",
                new AgentAnalystRunner.Outcome("run-1", "NVDA is holding its averages.", 4, 2, true, false),
                NOW.minusSeconds(120));

        AnalystAnswerDialog dialog = new AnalystAnswerDialog(null, answer, NOW);

        assertEquals("NVDA is holding its averages.", dialog.answerArea().getText());
        assertEquals(0, dialog.answerArea().getCaretPosition(), "a long answer opens at its first line");
        assertTrue(dialog.getWidth() >= 640 && dialog.getHeight() >= 480);
        dialog.dispose();
    }

    @Test
    void withNothingAnsweredYetItSaysSoRatherThanOpeningBlank() {
        AnalystAnswerDialog dialog = new AnalystAnswerDialog(null, null, NOW);

        assertTrue(dialog.answerArea().getText().contains("Ask the analyst something first"));
        dialog.dispose();
    }
}
