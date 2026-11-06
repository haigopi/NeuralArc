package com.neuralarc.ui;

import com.neuralarc.model.ScannerEntrySettings;
import com.neuralarc.model.ScannerEntrySettings.Anchor;
import com.neuralarc.model.ScannerEntrySettings.Mode;
import org.junit.jupiter.api.Test;

import javax.swing.JComboBox;
import javax.swing.JTextField;
import java.awt.Component;
import java.awt.Container;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScannerEntrySettingsPanelTest {
    @Test
    void theFormOpensOnTheDefault() {
        assertEquals(ScannerEntrySettings.defaults(), new ScannerEntrySettingsPanel().settings());
    }

    @Test
    void whatIsChosenIsWhatGetsSaved() {
        ScannerEntrySettingsPanel panel = new ScannerEntrySettingsPanel();
        ScannerEntrySettings chosen = new ScannerEntrySettings(Mode.CUSTOM_PERCENT, Anchor.ONE_MONTH, new BigDecimal("12.5"));

        panel.populate(chosen);

        assertEquals(chosen, panel.settings());
    }

    @Test
    void thePeriodAndPercentAreGreyedOutUnlessTheModeIsCustom() {
        ScannerEntrySettingsPanel panel = new ScannerEntrySettingsPanel();

        panel.populate(ScannerEntrySettings.defaults());
        assertFalse(percentField(panel).isEnabled());
        assertFalse(anchorBox(panel).isEnabled());

        panel.populate(new ScannerEntrySettings(Mode.CUSTOM_PERCENT, Anchor.TWO_WEEKS, BigDecimal.ONE));
        assertTrue(percentField(panel).isEnabled());
        assertTrue(anchorBox(panel).isEnabled());

        panel.populate(new ScannerEntrySettings(Mode.AUTO_TREND, Anchor.TWO_WEEKS, BigDecimal.ONE));
        assertFalse(percentField(panel).isEnabled());
    }

    @Test
    void anUnreadablePercentFallsBackToZeroInsteadOfFailingTheSave() {
        ScannerEntrySettingsPanel panel = new ScannerEntrySettingsPanel();
        panel.populate(new ScannerEntrySettings(Mode.CUSTOM_PERCENT, Anchor.ONE_YEAR, BigDecimal.ONE));

        percentField(panel).setText("abc");

        assertEquals(BigDecimal.ZERO, panel.settings().percentAboveLow());
    }

    @Test
    void everyModeAndPeriodIsOffered() {
        ScannerEntrySettingsPanel panel = new ScannerEntrySettingsPanel();

        assertEquals(3, modeBox(panel).getItemCount());
        assertEquals(5, anchorBox(panel).getItemCount());
    }

    private static JTextField percentField(Container root) {
        return find(root, JTextField.class).get(0);
    }

    @SuppressWarnings("unchecked")
    private static JComboBox<Anchor> anchorBox(Container root) {
        return (JComboBox<Anchor>) find(root, JComboBox.class).get(1);
    }

    @SuppressWarnings("unchecked")
    private static JComboBox<Mode> modeBox(Container root) {
        return (JComboBox<Mode>) find(root, JComboBox.class).get(0);
    }

    private static <T extends Component> List<T> find(Container root, Class<T> type) {
        List<T> found = new ArrayList<>();
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) {
                found.add(type.cast(child));
            }
            if (child instanceof Container container) {
                found.addAll(find(container, type));
            }
        }
        return found;
    }
}
