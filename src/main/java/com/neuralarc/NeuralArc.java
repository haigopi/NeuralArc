package com.neuralarc;

import com.neuralarc.ui.TradingFrame;
import com.neuralarc.util.FontLoader;

import javax.swing.*;
import java.awt.Font;

public class NeuralArc {
    public static void main(String[] args) {
        // Register bundled Inter font (fallback: Segoe UI → Arial)
        FontLoader.registerInter();

        // Enable LCD sub-pixel anti-aliasing for smooth font rendering
        System.setProperty("awt.useSystemAAFontSettings", "lcd");
        System.setProperty("swing.aatext", "true");

        // Apply resolved font globally to all Swing components via UIManager defaults
        Font plain = FontLoader.ui(Font.PLAIN, 14);
        Font bold  = FontLoader.ui(Font.BOLD,  14);
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
