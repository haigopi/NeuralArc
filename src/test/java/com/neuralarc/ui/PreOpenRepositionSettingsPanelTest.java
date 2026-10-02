package com.neuralarc.ui;

import com.neuralarc.model.PreOpenRepositionSettings;
import org.junit.jupiter.api.Test;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreOpenRepositionSettingsPanelTest {
    @Test
    void whatIsTypedIsWhatGetsSaved() {
        PreOpenRepositionSettingsPanel panel = new PreOpenRepositionSettingsPanel();

        panel.populate(new PreOpenRepositionSettings(true, 45, false));
        PreOpenRepositionSettings settings = panel.settings();

        assertTrue(settings.enabled());
        assertEquals(45, settings.minutesBeforeOpen());
        assertFalse(settings.dayOrdersOnly());
    }

    @Test
    void theFormOpensOnTheSafeDefaults() {
        PreOpenRepositionSettings settings = new PreOpenRepositionSettingsPanel().settings();

        assertFalse(settings.enabled());
        assertEquals(PreOpenRepositionSettings.DEFAULT_MINUTES_BEFORE_OPEN, settings.minutesBeforeOpen());
        assertTrue(settings.dayOrdersOnly());
    }

    @Test
    void theTimingIsGreyedOutWhileTheFeatureIsOff() {
        PreOpenRepositionSettingsPanel panel = new PreOpenRepositionSettingsPanel();

        panel.populate(PreOpenRepositionSettings.defaults());
        assertFalse(minutesField(panel).isEnabled(), "an off feature must not look configurable");

        panel.populate(new PreOpenRepositionSettings(true, 20, true));
        assertTrue(minutesField(panel).isEnabled());
    }

    @Test
    void anUnreadableLeadTimeFallsBackToTheDefaultInsteadOfFailingTheSave() {
        PreOpenRepositionSettingsPanel panel = new PreOpenRepositionSettingsPanel();
        panel.populate(new PreOpenRepositionSettings(true, 20, true));

        minutesField(panel).setText("soon");

        assertEquals(PreOpenRepositionSettings.DEFAULT_MINUTES_BEFORE_OPEN, panel.settings().minutesBeforeOpen());
    }

    private static javax.swing.JTextField minutesField(Container root) {
        return textFields(root).get(0);
    }

    private static List<javax.swing.JTextField> textFields(Container root) {
        List<javax.swing.JTextField> found = new ArrayList<>();
        for (Component child : root.getComponents()) {
            if (child instanceof javax.swing.JTextField field) {
                found.add(field);
            }
            if (child instanceof Container container) {
                found.addAll(textFields(container));
            }
        }
        return found;
    }
}
