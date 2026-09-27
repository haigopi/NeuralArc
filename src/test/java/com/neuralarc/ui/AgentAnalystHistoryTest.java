package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentAnalystHistoryTest {
    @Test
    void startsEmpty() {
        AgentAnalystHistory history = new AgentAnalystHistory();

        assertTrue(history.isEmpty());
        assertEquals(0, history.entries().size());
    }

    @Test
    void keepsOnlyTheTenMostRecentEntries() {
        AgentAnalystHistory history = new AgentAnalystHistory(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

        for (int i = 0; i < 15; i++) {
            history.record("Question " + i, "Answer " + i, "Done", false);
        }

        List<AgentAnalystHistory.Entry> entries = history.entries();
        assertEquals(AgentAnalystHistory.MAX_ENTRIES, entries.size(), "older entries beyond the cap are dropped");
        assertEquals("Question 14", entries.get(0).question(), "most recent question comes first");
        assertEquals("Question 5", entries.get(9).question(), "oldest kept entry is the 10th most recent");
    }

    @Test
    void carriesTheAnswerAndWarningFlagForEachEntry() {
        AgentAnalystHistory history = new AgentAnalystHistory(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

        history.record("What now?", "Nothing stands out.", "Done: 2 tool calls over 1 exchange.", false);
        history.record("Bad question", "The run failed: timeout", "Failed", true);

        List<AgentAnalystHistory.Entry> entries = history.entries();
        assertEquals("Bad question", entries.get(0).question());
        assertTrue(entries.get(0).warning());
        assertEquals("What now?", entries.get(1).question());
        assertTrue(!entries.get(1).warning());
    }
}
