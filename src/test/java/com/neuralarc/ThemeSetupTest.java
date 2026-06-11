package com.neuralarc;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatLaf;
import org.junit.jupiter.api.Test;

import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the themes/FlatLaf.properties custom defaults: a syntax error there
 * would otherwise only surface as broken styling at application startup.
 */
class ThemeSetupTest {

    @Test
    void customThemeDefaultsLoadAndParse() {
        FlatLaf.registerCustomDefaultsSource("themes");
        assertTrue(FlatDarculaLaf.setup(), "FlatDarculaLaf should install");

        // Semantic color keys resolve.
        assertNotNull(UIManager.getColor("NeuralArc.Section.titleForeground"));
        assertNotNull(UIManager.getColor("NeuralArc.Section.border"));
        assertNotNull(UIManager.getColor("NeuralArc.Input.border"));
        assertNotNull(UIManager.getColor("NeuralArc.Button.background"));
        assertNotNull(UIManager.getColor("NeuralArc.Table.rowBackgroundEven"));
        assertNotNull(UIManager.getColor("NeuralArc.Log.background"));

        // Section titles use the readable accent, not white.
        assertEquals(new Color(0x8A, 0xB4, 0xF8), UIManager.getColor("TitledBorder.titleColor"));

        // Relative font specs parsed: tabs and buttons are smaller than default.
        Font defaultFont = UIManager.getFont("defaultFont");
        Font tabFont = UIManager.getFont("TabbedPane.font");
        Font buttonFont = UIManager.getFont("Button.font");
        assertNotNull(defaultFont);
        assertNotNull(tabFont);
        assertNotNull(buttonFont);
        assertTrue(tabFont.getSize() < defaultFont.getSize(),
                "tab font (" + tabFont.getSize() + ") should be smaller than default (" + defaultFont.getSize() + ")");
        assertTrue(buttonFont.getSize() < defaultFont.getSize(),
                "button font (" + buttonFont.getSize() + ") should be smaller than default (" + defaultFont.getSize() + ")");
    }
}
