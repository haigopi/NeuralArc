package com.neuralarc;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatDarculaLaf;
import com.neuralarc.ui.AboutDialog;
import com.neuralarc.service.RotatingJavaLogHandler;
import com.neuralarc.ui.SplashScreenWindow;
import com.neuralarc.ui.TradingFrame;
import com.neuralarc.util.AppIconLoader;
import com.neuralarc.util.AppMetadata;
import com.neuralarc.util.FontLoader;

import javax.swing.*;
import java.awt.Desktop;
import java.awt.Image;
import java.awt.Taskbar;
import java.io.InputStream;
import java.util.logging.Logger;
import java.util.logging.LogManager;

public class NeuralArc {
    public static void main(String[] args) {
        configureLogging();
        installLookAndFeel();
        FontLoader.installSwingDefaults();
        configureTooltips();

        SwingUtilities.invokeLater(() -> {
            int splashDurationMillis = AppMetadata.splashDurationMillis();
            TradingFrame frame = new TradingFrame();
            installApplicationIcon(frame);
            installMacApplicationMenu(frame);
            frame.setVisible(true);
            frame.startBackgroundSchedulers();

            SplashScreenWindow splash = new SplashScreenWindow();
            installApplicationIcon(splash);
            splash.setLocationRelativeTo(frame);
            splash.setVisible(true);
            Timer splashTimer = new Timer(splashDurationMillis, event -> {
                splash.dispose();
                frame.toFront();
                frame.requestFocus();
                frame.promptForRequiredSettings();
            });
            splashTimer.setRepeats(false);
            splashTimer.start();
        });
    }

    private static void installLookAndFeel() {
        // Must run before any Swing component is created so every widget picks up the theme.
        FlatLaf.registerCustomDefaultsSource("themes");
        FlatDarculaLaf.setup();
    }

    private static void installMacApplicationMenu(TradingFrame frame) {
        if (!Desktop.isDesktopSupported()) {
            return;
        }
        try {
            Image appIcon = AppIconLoader.loadAppIcon();
            Desktop.getDesktop().setAboutHandler(event -> new AboutDialog(frame).setVisible(true));
        } catch (UnsupportedOperationException ignored) {
            // Non-macOS platforms or runtimes without app-menu support can safely ignore this.
        }
        installTaskbarIcon();
    }

    private static void installApplicationIcon(java.awt.Window window) {
        Image appIcon = AppIconLoader.loadAppIcon();
        if (appIcon != null) {
            window.setIconImage(appIcon);
        }
    }

    private static void installTaskbarIcon() {
        Image appIcon = AppIconLoader.loadAppIcon();
        if (appIcon == null || !Taskbar.isTaskbarSupported()) {
            return;
        }
        try {
            Taskbar taskbar = Taskbar.getTaskbar();
            if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                taskbar.setIconImage(appIcon);
            }
        } catch (UnsupportedOperationException ignored) {
            // Some platforms/JDKs do not expose taskbar icon mutation.
        }
    }

    private static void configureLogging() {
        try (InputStream is = NeuralArc.class.getResourceAsStream("/logging.properties")) {
            if (is != null) {
                LogManager.getLogManager().readConfiguration(is);
            }
        } catch (Exception ignored) {
            // Fall back to JVM defaults if config cannot be loaded.
        }
        installRotatingApplicationLogHandler();
    }

    private static void installRotatingApplicationLogHandler() {
        try {
            Logger root = Logger.getLogger("");
            root.addHandler(new RotatingJavaLogHandler(AppMetadata.appDataDirectory().resolve("logs")));
        } catch (Exception ignored) {
            // Console logging remains available if file logging cannot be installed.
        }
    }

    private static void configureTooltips() {
        ToolTipManager.sharedInstance().setInitialDelay(350);
        ToolTipManager.sharedInstance().setDismissDelay(12000);
        ToolTipManager.sharedInstance().setReshowDelay(100);
    }
}
