package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserActionLogSupportTest {
    @Test
    void writesConsistentStartedCompletedFailedMessages() {
        List<String> logs = new ArrayList<>();
        UserActionLogSupport support = new UserActionLogSupport(logs::add);

        support.started("Refresh Portfolio");
        support.completed("Refresh Portfolio", "Refreshed 2 positions.");
        support.failed("Refresh Portfolio", "Broker unavailable.");

        assertEquals("[ACTION][Refresh Portfolio] Started.", logs.get(0));
        assertEquals("[ACTION][Refresh Portfolio] Completed. Refreshed 2 positions.", logs.get(1));
        assertEquals("[ACTION][Refresh Portfolio] Failed. Broker unavailable.", logs.get(2));
    }

    @Test
    void normalizesBlankActionAndDetails() {
        List<String> logs = new ArrayList<>();
        UserActionLogSupport support = new UserActionLogSupport(logs::add);

        support.skipped(" ", " ");

        assertEquals("[ACTION][User Action] Skipped. No additional details.", logs.getFirst());
    }
}
