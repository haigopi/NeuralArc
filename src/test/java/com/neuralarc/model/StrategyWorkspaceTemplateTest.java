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
        assertTrue(catalog.stream().anyMatch(t -> t.name().equals("Swing Vault") && t.code().equals("SWING")
                && t.description().contains("pulled back")
                && t.description().contains("Swing Vault grid")));
        assertTrue(catalog.stream().anyMatch(t -> t.name().equals("Dip Hunter") && t.code().equals("DIP")));
        // "Momentum Lab" was folded into Gap Rocket (the dedicated high-relative-volume scanner).
        assertFalse(catalog.stream().anyMatch(t -> t.name().equals("Momentum Lab") || t.code().equals("MOMENTUM")));
        assertTrue(catalog.stream().allMatch(t -> t.description() != null && !t.description().isBlank()));
    }

    @Test
    void onlyImplementedStrategiesAreEnabled() {
        List<StrategyWorkspaceTemplate> catalog = StrategyWorkspaceTemplate.catalog();
        // Implemented, dedicated scanners (plus the always-usable generic workspaces).
        assertTrue(implemented(catalog, "GAPROCKET"));
        assertTrue(implemented(catalog, "ORB"));
        assertTrue(implemented(catalog, "DIP"));
        assertTrue(implemented(catalog, "VWAP"));
        assertTrue(implemented(catalog, "SWING"));
        assertTrue(implemented(catalog, "EARNINGS"));
        assertTrue(implemented(catalog, "MANUAL"));
        assertTrue(implemented(catalog, StrategyWorkspaceTemplate.CUSTOM_CODE));
        // Placeholders that are advertised but not yet implemented.
        assertFalse(implemented(catalog, "SHIELD"));
    }

    private static boolean implemented(List<StrategyWorkspaceTemplate> catalog, String code) {
        return catalog.stream().filter(t -> t.code().equals(code)).findFirst().orElseThrow().implemented();
    }

    @Test
    void onlyCustomTemplateIsFlaggedCustom() {
        List<StrategyWorkspaceTemplate> catalog = StrategyWorkspaceTemplate.catalog();
        StrategyWorkspaceTemplate custom = catalog.stream().filter(StrategyWorkspaceTemplate::isCustom).findFirst().orElseThrow();
        assertTrue(custom.name().equals("Custom Strategy"));
        assertFalse(catalog.stream().filter(t -> !t.isCustom()).anyMatch(StrategyWorkspaceTemplate::isCustom));
    }
}
