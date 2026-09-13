package com.neuralarc.ui;

import com.neuralarc.model.PortfolioEmailSettings;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortfolioEmailSettingsPanelTest {
    @Test
    void startsWithTheFiveDefaultTimes() {
        PortfolioEmailSettingsPanel panel = new PortfolioEmailSettingsPanel();

        assertEquals(PortfolioEmailSettings.defaults(), panel.settings());
        assertEquals(5, panel.slotCount());
    }

    @Test
    void showsAndReturnsSavedSettings() {
        PortfolioEmailSettings saved = new PortfolioEmailSettings(true, "me@example.com", List.of(
                new PortfolioEmailSettings.Slot("Lunch", LocalTime.of(12, 30), false),
                new PortfolioEmailSettings.Slot(PortfolioEmailSettings.CUSTOM_LABEL, LocalTime.of(14, 5), true)));
        PortfolioEmailSettingsPanel panel = new PortfolioEmailSettingsPanel();

        panel.populate(saved);

        assertEquals(saved, panel.settings());
    }

    @Test
    void aTimeCanBeRetimedByPickingOrTyping() {
        PortfolioEmailSettingsPanel panel = new PortfolioEmailSettingsPanel();

        panel.setSlotTimeText(0, "9:20");

        assertEquals(LocalTime.of(9, 20), panel.settings().slots().get(0).timeEt());
    }

    @Test
    void timesCanBeAddedAndTheDefaultsRestored() {
        PortfolioEmailSettingsPanel panel = new PortfolioEmailSettingsPanel();

        panel.addTime(LocalTime.of(13, 30));
        assertEquals(6, panel.slotCount());
        assertTrue(panel.settings().slots().stream().anyMatch(slot -> slot.timeEt().equals(LocalTime.of(13, 30))));

        panel.restoreDefaultTimes();
        assertEquals(PortfolioEmailSettings.defaultSlots(), panel.settings().slots());
    }

    @Test
    void switchingTheEmailsOffGreysOutTheTimes() {
        PortfolioEmailSettingsPanel panel = new PortfolioEmailSettingsPanel();

        panel.enabledCheckBox().doClick();

        assertFalse(panel.settings().enabled());
        assertFalse(panel.slotControlsEnabled());
        assertFalse(panel.recipientInput().isEnabled());
    }

    @Test
    void anInvalidAddressOrTimeIsReportedBeforeSaving() {
        PortfolioEmailSettingsPanel panel = new PortfolioEmailSettingsPanel();
        assertNull(panel.validationError());

        panel.recipientInput().setText("not-an-address");
        assertNotNull(panel.validationError());

        panel.recipientInput().setText("me@example.com");
        panel.setSlotTimeText(2, "lunchtime");
        assertTrue(panel.validationError().contains("\"lunchtime\" is not a time"), panel.validationError());
    }

    @Test
    void sendNowUsesTheSettingsOnScreen() {
        PortfolioEmailSettingsPanel panel = new PortfolioEmailSettingsPanel();
        assertFalse(panel.sendNowButton().isVisible(), "hidden until the app can send");
        List<PortfolioEmailSettings> requested = new ArrayList<>();
        panel.setSendNowHandler(requested::add);

        panel.recipientInput().setText("me@example.com");
        panel.sendNowButton().doClick();

        assertTrue(panel.sendNowButton().isVisible());
        assertEquals("me@example.com", requested.getFirst().recipient());
        assertTrue(panel.statusLabel().getText().contains("Event Log"), panel.statusLabel().getText());
    }

    @Test
    void sendNowRefusesAnInvalidAddress() {
        PortfolioEmailSettingsPanel panel = new PortfolioEmailSettingsPanel();
        List<PortfolioEmailSettings> requested = new ArrayList<>();
        panel.setSendNowHandler(requested::add);

        panel.recipientInput().setText("nope");
        panel.sendNowButton().doClick();

        assertEquals(List.of(), requested);
        assertTrue(panel.statusLabel().getText().contains("valid email address"));
    }
}
