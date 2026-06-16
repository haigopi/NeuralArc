package com.neuralarc.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategyWorkspaceTemplateTest {
    @Test
    void catalogContainsExpectedTemplates() {
        List<StrategyWorkspaceTemplate> catalog = StrategyWorkspaceTemplate.catalog();
        assertTrue(catalog.stream().anyMatch(t -> t.name().equals("Gap Rocket") && t.code().equals("GAPROCKET")));
        assertTrue(catalog.stream().anyMatch(t -> t.name().equals("ORB Engine") && t.code().equals("ORB")
                && t.description().contains("5/15/30 minute regular-session range")
                && t.description().contains("ORB Engine grid")));
        assertTrue(catalog.stream().anyMatch(t -> t.name().equals("VWAP Desk")));
        // "Momentum Lab" was folded into Gap Rocket (the dedicated high-relative-volume scanner).
        assertFalse(catalog.stream().anyMatch(t -> t.name().equals("Momentum Lab") || t.code().equals("MOMENTUM")));
        assertTrue(catalog.stream().allMatch(t -> t.description() != null && !t.description().isBlank()));
    }

    @Test
    void onlyCustomTemplateIsFlaggedCustom() {
        List<StrategyWorkspaceTemplate> catalog = StrategyWorkspaceTemplate.catalog();
        StrategyWorkspaceTemplate custom = catalog.stream().filter(StrategyWorkspaceTemplate::isCustom).findFirst().orElseThrow();
        assertTrue(custom.name().equals("Custom Strategy"));
        assertFalse(catalog.stream().filter(t -> !t.isCustom()).anyMatch(StrategyWorkspaceTemplate::isCustom));
    }
}
