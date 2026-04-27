package com.neuralarc.util;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import java.awt.Color;
import java.net.URL;

public final class SvgIconLoader {
    private SvgIconLoader() {}

    public static Icon load(String resourcePath, int size) {
        return load(resourcePath, size, Color.WHITE);
    }

    public static Icon load(String resourcePath, int size, Color color) {
        String normalized = resourcePath.startsWith("/") ? resourcePath : "/" + resourcePath;
        URL resource = SvgIconLoader.class.getResource(normalized);
        if (resource == null) {
            return new ImageIcon();
        }
        FlatSVGIcon icon = new FlatSVGIcon(resource).derive(size, size);
        Color targetColor = colorOrDefault(color);
        icon.setColorFilter(new FlatSVGIcon.ColorFilter(sourceColor ->
                new Color(
                        targetColor.getRed(),
                        targetColor.getGreen(),
                        targetColor.getBlue(),
                        sourceColor == null ? 255 : sourceColor.getAlpha()
                )
        ));
        return icon;
    }

    private static Color colorOrDefault(Color color) {
        return color != null ? color : Color.WHITE;
    }
}
