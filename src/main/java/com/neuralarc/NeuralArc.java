package com.neuralarc;

import com.neuralarc.ui.TradingFrame;
import com.neuralarc.util.FontLoader;

import javax.swing.*;
import java.awt.Font;
import java.awt.RenderingHints;

public class NeuralArc {
    public static void main(String[] args) {
        // Register bundled Inter font (fallback: Segoe UI → Arial)
        FontLoader.registerInter();

        // Enable anti-aliased text rendering for smooth UI typography.
        // "on" is the most cross-platform safe setting (LCD sub-pixel AA is device dependent).
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        UIManager.put(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        UIManager.put(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

        // Apply resolved font globally to all Swing components via UIManager defaults
        Font plain = FontLoader.ui(Font.PLAIN, 14f);
        Font bold  = FontLoader.ui(Font.BOLD,  14f);
        String[] fontKeys = {
            "Button.font", "CheckBox.font", "ComboBox.font", "Label.font",
            "List.font", "Menu.font", "MenuItem.font", "OptionPane.messageFont",
            "Panel.font", "PasswordField.font", "RadioButton.font", "ScrollPane.font",
            "Spinner.font", "Table.font", "TableHeader.font", "TextArea.font",
            "TextField.font", "TextPane.font", "TitledBorder.font", "ToggleButton.font",
            "ToolBar.font", "ToolTip.font", "Tree.font", "Viewport.font"
        };
        for (String key : fontKeys) {
            UIManager.put(key, key.contains("Header") || key.contains("TitledBorder")
                    ? bold : plain);
        }

        SwingUtilities.invokeLater(() -> {
            TradingFrame frame = new TradingFrame();
            frame.setVisible(true);
            frame.promptForRequiredSettings();
        });
    }
}
