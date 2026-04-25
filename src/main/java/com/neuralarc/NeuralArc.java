package com.neuralarc;

import com.neuralarc.ui.AboutDialog;
import com.neuralarc.ui.SplashScreenWindow;
import com.neuralarc.ui.TradingFrame;
import com.neuralarc.util.AppMetadata;
import com.neuralarc.util.FontLoader;

import javax.swing.*;
import java.awt.Desktop;

public class NeuralArc {
    public static void main(String[] args) {
        FontLoader.installSwingDefaults();

        SwingUtilities.invokeLater(() -> {
            SplashScreenWindow splash = new SplashScreenWindow();
            splash.setVisible(true);
            Timer splashTimer = new Timer(AppMetadata.splashDurationMillis(), event -> {
                TradingFrame frame = new TradingFrame();
                installMacApplicationMenu(frame);
                frame.setVisible(true);
                splash.dispose();
                frame.promptForRequiredSettings();
            });
            splashTimer.setRepeats(false);
            splashTimer.start();
        });
    }

    private static void installMacApplicationMenu(TradingFrame frame) {
        if (!Desktop.isDesktopSupported()) {
            return;
        }
        try {
            Desktop.getDesktop().setAboutHandler(event -> new AboutDialog(frame).setVisible(true));
        } catch (UnsupportedOperationException ignored) {
            // Non-macOS platforms or runtimes without app-menu support can safely ignore this.
        }
    }
}
