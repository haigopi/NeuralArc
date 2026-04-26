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
        configureTooltips();

        SwingUtilities.invokeLater(() -> {
            int splashDurationMillis = AppMetadata.splashDurationMillis();
            SplashScreenWindow splash = new SplashScreenWindow(splashDurationMillis);
            splash.setVisible(true);
            Timer splashTimer = new Timer(splashDurationMillis, event -> {
                splash.dispose();
                SwingUtilities.invokeLater(() -> {
                    TradingFrame frame = new TradingFrame();
                    installMacApplicationMenu(frame);
                    frame.setVisible(true);
                    frame.promptForRequiredSettings();
                });
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

    private static void configureTooltips() {
        ToolTipManager.sharedInstance().setInitialDelay(350);
        ToolTipManager.sharedInstance().setDismissDelay(12000);
        ToolTipManager.sharedInstance().setReshowDelay(100);
    }
}
