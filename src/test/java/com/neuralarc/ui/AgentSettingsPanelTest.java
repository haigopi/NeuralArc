package com.neuralarc.ui;

import com.neuralarc.model.AgentSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentSettingsPanelTest {
    @Test
    void whatIsTypedIsWhatGetsSaved() {
        AgentSettingsPanel panel = new AgentSettingsPanel();

        panel.populate(new AgentSettings(true, "sk-ant-abc", "claude-opus-5", 5, 20));
        AgentSettings settings = panel.settings();

        assertTrue(settings.enabled());
        assertEquals("sk-ant-abc", settings.apiKey());
        assertEquals("claude-opus-5", settings.model());
        assertEquals(5, settings.maxTurns());
        assertEquals(20, settings.maxToolCalls());
    }

    @Test
    void theKeyAndLimitsAreGreyedOutUntilTheAnalystIsOn() {
        AgentSettingsPanel panel = new AgentSettingsPanel();

        panel.populate(AgentSettings.defaults());

        assertFalse(settingsFieldsEnabled(panel), "an off analyst must not look configurable");
        panel.populate(new AgentSettings(true, "k", null, 4, 4));
        assertTrue(settingsFieldsEnabled(panel));
    }

    @Test
    void anUnreadableLimitFallsBackToTheDefaultInsteadOfFailingTheSave() {
        AgentSettingsPanel panel = new AgentSettingsPanel();
        panel.populate(new AgentSettings(true, "k", "claude-opus-5", 5, 20));

        javax.swing.JTextField turns = textFields(panel).get(2);
        turns.setText("lots");

        assertEquals(AgentSettings.DEFAULT_MAX_TURNS, panel.settings().maxTurns());
    }

    private static boolean settingsFieldsEnabled(AgentSettingsPanel panel) {
        return textFields(panel).get(0).isEnabled();
    }

    /** Key, model, max turns, max tool calls — in the order the form lays them out. */
    private static java.util.List<javax.swing.JTextField> textFields(java.awt.Container root) {
        java.util.List<javax.swing.JTextField> found = new java.util.ArrayList<>();
        for (java.awt.Component child : root.getComponents()) {
            if (child instanceof javax.swing.JTextField field) {
                found.add(field);
            }
            if (child instanceof java.awt.Container container) {
                found.addAll(textFields(container));
            }
        }
        return found;
    }
}
