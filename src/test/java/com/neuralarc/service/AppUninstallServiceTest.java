package com.neuralarc.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AppUninstallServiceTest {
    private final AppUninstallService service = new AppUninstallService();

    @Test
    void macScriptRemovesApplicationAndLocalData() {
        AppUninstallService.UninstallPlan plan = new AppUninstallService.UninstallPlan(
                AppUninstallService.OperatingSystem.MACOS,
                Path.of("/Applications/NeuralArc.app"),
                Path.of("/Users/test/Library/Application Support/NeuralArc"),
                List.of(Path.of("/Users/test/Desktop/NeuralArc.app"))
        );

        String script = service.scriptPreview(plan);

        assertTrue(script.contains("/Applications/NeuralArc.app"));
        assertTrue(script.contains("Application Support/NeuralArc"));
        assertTrue(script.contains("NeuralArc.app"));
    }

    @Test
    void windowsScriptUsesRegisteredUninstallerAndRemovesLocalData() {
        AppUninstallService.UninstallPlan plan = new AppUninstallService.UninstallPlan(
                AppUninstallService.OperatingSystem.WINDOWS,
                Path.of("C:\\Users\\test\\AppData\\Local\\NeuralArc"),
                Path.of("C:\\Users\\test\\AppData\\Roaming\\NeuralArc"),
                List.of(Path.of("C:\\Users\\test\\Desktop\\NeuralArc.lnk"))
        );

        String script = service.scriptPreview(plan);

        assertTrue(script.contains("CurrentVersion\\Uninstall"));
        assertTrue(script.contains("DisplayName -eq 'NeuralArc'"));
        assertTrue(script.contains("AppData\\Roaming\\NeuralArc"));
        assertTrue(script.contains("NeuralArc.lnk"));
    }

    @Test
    void linuxScriptAttemptsDebRemovalAndRemovesUserData() {
        AppUninstallService.UninstallPlan plan = new AppUninstallService.UninstallPlan(
                AppUninstallService.OperatingSystem.LINUX,
                Path.of("/opt/neuralarc"),
                Path.of("/home/test/.neuralarc"),
                List.of(Path.of("/home/test/.local/share/applications/neuralarc.desktop"))
        );

        String script = service.scriptPreview(plan);

        assertTrue(script.contains("apt-get remove --purge -y neuralarc"));
        assertTrue(script.contains("/opt/neuralarc"));
        assertTrue(script.contains("/home/test/.neuralarc"));
        assertTrue(script.contains("neuralarc.desktop"));
    }
}
