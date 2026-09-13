package com.neuralarc.ui;

import com.neuralarc.model.PortfolioEmailSettings;
import com.neuralarc.service.AppSettingsService;
import org.junit.jupiter.api.Test;

import javax.swing.JTextField;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommunicationSettingsPanelTest {
    @Test
    void theOneAddressIsTheUserEmailAndFollowsItAsItIsTyped() {
        CommunicationSettingsPanel panel = new CommunicationSettingsPanel();
        JTextField userEmail = new JTextField();
        panel.followRecipient(userEmail);
        assertEquals("No email address yet", panel.recipientText());

        userEmail.setText("me@example.com");

        assertEquals("me@example.com", panel.recipientText());
    }

    @Test
    void tradeAlertsStartAtTheirDefaults() {
        CommunicationSettingsPanel panel = new CommunicationSettingsPanel();

        assertEquals(AppSettingsService.DEFAULT_EMAIL_ON_BUY_EXPECTED, panel.buyExpectedCheckBox().isSelected());
        assertEquals(AppSettingsService.DEFAULT_EMAIL_ON_SELL_EXECUTED, panel.sellExecutedCheckBox().isSelected());
    }

    @Test
    void snapshotsStartWithTheFiveDefaultTimes() {
        CommunicationSettingsPanel panel = new CommunicationSettingsPanel();

        assertEquals(PortfolioEmailSettings.defaults(), panel.portfolioEmailSettings());
        assertEquals(5, panel.slotCount());
    }

    @Test
    void showsAndReturnsSavedSnapshotSettings() {
        PortfolioEmailSettings saved = new PortfolioEmailSettings(true, List.of(
                new PortfolioEmailSettings.Slot("Lunch", LocalTime.of(12, 30), false),
                new PortfolioEmailSettings.Slot(PortfolioEmailSettings.CUSTOM_LABEL, LocalTime.of(14, 5), true)));
        CommunicationSettingsPanel panel = new CommunicationSettingsPanel();

        panel.populatePortfolioEmail(saved);

        assertEquals(saved, panel.portfolioEmailSettings());
    }

    @Test
    void aTimeCanBeRetimedAddedAndTheDefaultsRestored() {
        CommunicationSettingsPanel panel = new CommunicationSettingsPanel();

        panel.setSlotTimeText(0, "9:20");
        assertEquals(LocalTime.of(9, 20), panel.portfolioEmailSettings().slots().get(0).timeEt());

        panel.addTime(LocalTime.of(13, 30));
        assertEquals(6, panel.slotCount());

        panel.restoreDefaultTimes();
        assertEquals(PortfolioEmailSettings.defaultSlots(), panel.portfolioEmailSettings().slots());
    }

    @Test
    void switchingSnapshotsOffGreysOutTheirTimesButNotTheTradeAlerts() {
        CommunicationSettingsPanel panel = new CommunicationSettingsPanel();

        panel.snapshotsCheckBox().doClick();

        assertFalse(panel.portfolioEmailSettings().enabled());
        assertFalse(panel.slotControlsEnabled());
        assertTrue(panel.buyExpectedCheckBox().isEnabled());
    }

    @Test
    void anInvalidTimeIsReportedBeforeSaving() {
        CommunicationSettingsPanel panel = new CommunicationSettingsPanel();
        assertNull(panel.validationError());

        panel.setSlotTimeText(2, "lunchtime");

        assertTrue(panel.validationError().contains("\"lunchtime\" is not a time"), panel.validationError());
    }

    @Test
    void sendNowUsesTheUserEmailOnScreen() {
        CommunicationSettingsPanel panel = new CommunicationSettingsPanel();
        JTextField userEmail = new JTextField("me@example.com");
        panel.followRecipient(userEmail);
        assertFalse(panel.sendNowButton().isVisible(), "hidden until the app can send");
        List<String> requested = new ArrayList<>();
        panel.setSendNowHandler(requested::add);

        panel.sendNowButton().doClick();

        assertEquals(List.of("me@example.com"), requested);
        assertTrue(panel.statusLabel().getText().contains("Event Log"), panel.statusLabel().getText());
    }

    @Test
    void sendNowAsksForTheUserEmailWhenThereIsNone() {
        CommunicationSettingsPanel panel = new CommunicationSettingsPanel();
        panel.followRecipient(new JTextField());
        List<String> requested = new ArrayList<>();
        panel.setSendNowHandler(requested::add);

        panel.sendNowButton().doClick();

        assertEquals(List.of(), requested);
        assertTrue(panel.statusLabel().getText().contains("Add your User Email above first"));
    }
}
