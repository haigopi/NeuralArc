package com.neuralarc.util;

import javax.swing.ImageIcon;
import java.awt.Image;
import java.net.URL;

public final class AppIconLoader {
    private AppIconLoader() {}

    public static Image loadAppIcon() {
        URL resource = AppIconLoader.class.getResource("/logo.png");
        if (resource == null) {
            return null;
        }
        return new ImageIcon(resource).getImage();
    }
}
