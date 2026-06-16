package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import javax.swing.JButton;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ButtonHoverPolicyTest {

    @Test
    void hoverEnabledByDefault() {
        assertTrue(ButtonHoverPolicy.hoverEnabled(new JButton("Capture")));
    }

    @Test
    void hoverSuppressedWhileFlashing() {
        JButton button = new JButton("Capture");
        button.putClientProperty(ButtonHoverPolicy.FLASHING_PROPERTY, Boolean.TRUE);
        assertFalse(ButtonHoverPolicy.hoverEnabled(button));
    }

    @Test
    void hoverRestoredWhenFlashingCleared() {
        JButton button = new JButton("Capture");
        button.putClientProperty(ButtonHoverPolicy.FLASHING_PROPERTY, Boolean.TRUE);
        button.putClientProperty(ButtonHoverPolicy.FLASHING_PROPERTY, Boolean.FALSE);
        assertTrue(ButtonHoverPolicy.hoverEnabled(button));
    }
}
