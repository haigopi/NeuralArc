package com.neuralarc.ui;

import com.neuralarc.model.StrategyWorkspaceTemplate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradingFrameSmartPicksMenuConfigTest {

    @Test
    void theSmartPicksStrategiesAreOfferedAsWorkspacesNotOneOffMenuItems() {
        List<String> labels = TradingFrame.smartPicksMenuLabels();

        assertTrue(labels.containsAll(List.of("High Volatility Movers", "Diversified Leaders", "Weekend Rebound")), labels.toString());
        assertFalse(labels.contains("Diversified Leaders (Top 20)"), "the old one-off menu item is gone");
    }

    @Test
    void everySmartPicksKindHasAMatchingWorkspaceTemplate() {
        for (SmartPicksWorkspaceKind kind : SmartPicksWorkspaceKind.values()) {
            StrategyWorkspaceTemplate template = StrategyWorkspaceTemplate.catalog().stream()
                    .filter(candidate -> candidate.code().equals(kind.code()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no template for " + kind));
            assertEquals(kind.title(), template.name());
            assertTrue(template.implemented());
        }
    }
}
