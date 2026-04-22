package com.neuralarc.util;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.InputStream;

/**
 * Loads and registers the bundled Inter font family from project resources,
 * then resolves the best available UI font using the fallback chain:
 *   Inter (bundled) → Segoe UI → Arial
 */
public final class FontLoader {

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
