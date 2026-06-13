package com.neuralarc.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategyWorkspaceTemplateTest {
    @Test
    void catalogContainsExpectedTemplates() {
        List<StrategyWorkspaceTemplate> catalog = StrategyWorkspaceTemplate.catalog();
        assertTrue(catalog.stream().anyMatch(t -> t.name().equals("ORB Engine") && t.code().equals("ORB")));
        assertTrue(catalog.stream().anyMatch(t -> t.name().equals("VWAP Desk")));
        assertTrue(catalog.stream().anyMatch(t -> t.name().equals("Momentum Lab")));
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
