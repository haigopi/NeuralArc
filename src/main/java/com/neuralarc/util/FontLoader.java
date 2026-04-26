package com.neuralarc.util;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.io.InputStream;
import javax.swing.BorderFactory;
import javax.swing.UIManager;

/**
 * Loads and registers the bundled Inter font family from project resources,
 * then resolves the best available UI font using the fallback chain:
 *   Inter (bundled) → Segoe UI → Arial
 */
public final class FontLoader {
    public static final float DEFAULT_UI_SIZE = 14f;
    public static final float SMALL_UI_SIZE = 10f;

    private static final String[] INTER_VARIANTS = {
            "/fonts/Inter-Regular.ttf",
            "/fonts/Inter-Bold.ttf",
            "/fonts/Inter-Italic.ttf",
            "/fonts/Inter-BoldItalic.ttf"
    };

    /** Ordered fallback chain. First name found on this JVM wins. */
    private static final String[] FALLBACK_CHAIN = {"Inter", "Segoe UI", "Arial"};

    private static volatile boolean registered = false;
    private static volatile String resolvedFamily = null;

    private FontLoader() {}

    /**
     * Registers bundled Inter TTF files with the local GraphicsEnvironment.
     * Call once at startup before any Swing component is created.
     */
    public static synchronized void registerInter() {
        if (registered) return;
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        for (String path : INTER_VARIANTS) {
            try (InputStream is = FontLoader.class.getResourceAsStream(path)) {
                if (is != null) {
                    ge.registerFont(Font.createFont(Font.TRUETYPE_FONT, is));
                }
            } catch (Exception e) {
                System.err.println("FontLoader: could not load " + path + " – " + e.getMessage());
            }
        }
        registered = true;
        resolvedFamily = resolveFamily();
        System.out.println("FontLoader: using font family [" + resolvedFamily + "]");
    }

    /** Returns the resolved font family name (Inter, Segoe UI, or Arial). */
    public static String resolvedFamily() {
        if (resolvedFamily == null) registerInter();
        return resolvedFamily;
    }

    /** Returns a font using the resolved family with the given style and size (pt). */
    public static Font ui(int style, float sizePt) {
        return new Font(resolvedFamily(), style, Math.round(sizePt));
    }

    public static Font regular(float sizePt) {
        return ui(Font.PLAIN, sizePt);
    }

    public static Font bold(float sizePt) {
        return ui(Font.BOLD, sizePt);
    }

    public static Font uiDefault() {
        return regular(DEFAULT_UI_SIZE);
    }

    public static void installSwingDefaults() {
        registerInter();

        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        UIManager.put(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        UIManager.put(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

        Font plain = uiDefault();
        Font bold = FontLoader.bold(DEFAULT_UI_SIZE);
        String[] fontKeys = {
                "Button.font", "CheckBox.font", "ComboBox.font", "Label.font",
                "List.font", "Menu.font", "MenuItem.font", "OptionPane.messageFont",
                "Panel.font", "PasswordField.font", "RadioButton.font", "ScrollPane.font",
                "Spinner.font", "Table.font", "TableHeader.font", "TextArea.font",
                "TextField.font", "TextPane.font", "TitledBorder.font", "ToggleButton.font",
                "ToolBar.font", "ToolTip.font", "Tree.font", "Viewport.font"
        };
        for (String key : fontKeys) {
            UIManager.put(key, key.contains("Header") || key.contains("TitledBorder") ? bold : plain);
        }
        UIManager.put("ToolTip.font", regular(SMALL_UI_SIZE));
        UIManager.put("ToolTip.background", new java.awt.Color(32, 36, 44));
        UIManager.put("ToolTip.foreground", new java.awt.Color(245, 247, 250));
        UIManager.put("ToolTip.border", BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new java.awt.Color(88, 96, 110), 1, true),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)
        ));
    }

    // ── internals ────────────────────────────────────────────────────────────

    private static String resolveFamily() {
        java.util.Set<String> available = new java.util.HashSet<>();
        for (String f : GraphicsEnvironment.getLocalGraphicsEnvironment()
                                           .getAvailableFontFamilyNames()) {
            available.add(f.toLowerCase(java.util.Locale.ROOT));
        }
        for (String candidate : FALLBACK_CHAIN) {
            if (available.contains(candidate.toLowerCase(java.util.Locale.ROOT))) {
                return candidate;
            }
        }
        return Font.SANS_SERIF; // ultimate safety net
    }
}
