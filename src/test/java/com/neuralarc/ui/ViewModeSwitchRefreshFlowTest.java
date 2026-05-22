package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ViewModeSwitchRefreshFlowTest {

    @Test
    void applyRunsSyncBeforeTableRefresh() {
        List<String> calls = new ArrayList<>();

        ViewModeSwitchRefreshFlow.apply(
                () -> calls.add("sync"),
                () -> calls.add("refreshTable"),
                () -> calls.add("updateSelected"),
                () -> calls.add("refreshPanels"),
                () -> calls.add("updateStatus")
        );

        assertEquals(List.of("sync", "refreshTable", "updateSelected", "refreshPanels", "updateStatus"), calls);
    }

    @Test
    void applyKeepsSameOrderAcrossRepeatedModeToggles() {
        List<String> calls = new ArrayList<>();

        // Simulates Paper -> Live -> Paper toggles invoking the same post-switch flow.
        for (int i = 0; i < 2; i++) {
            ViewModeSwitchRefreshFlow.apply(
                    () -> calls.add("sync"),
                    () -> calls.add("refreshTable"),
                    () -> calls.add("updateSelected"),
                    () -> calls.add("refreshPanels"),
                    () -> calls.add("updateStatus")
            );
        }

        assertEquals(
                List.of(
                        "sync", "refreshTable", "updateSelected", "refreshPanels", "updateStatus",
                        "sync", "refreshTable", "updateSelected", "refreshPanels", "updateStatus"
                ),
                calls
        );
    }
}

