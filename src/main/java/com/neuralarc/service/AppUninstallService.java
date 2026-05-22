package com.neuralarc.service;

import com.neuralarc.util.AppMetadata;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AppUninstallService {
    public UninstallPlan createPlan() {
        OperatingSystem os = currentOperatingSystem();
        Path appDataDirectory = AppMetadata.appDataDirectory();
        Path installDirectory = installDirectory(os);
        return new UninstallPlan(os, installDirectory, appDataDirectory, extraUserPaths(os));
    }

    public Path scheduleUninstall(UninstallPlan plan) throws IOException {
        if (plan == null || plan.os() == OperatingSystem.UNSUPPORTED) {
            throw new IOException("Unsupported operating system for automatic uninstall.");
        }
        Path script = Files.createTempFile("neuralarc-uninstall-", scriptExtension(plan.os()));
        Files.writeString(script, scriptFor(plan), StandardCharsets.UTF_8);
        script.toFile().setExecutable(true, true);
        List<String> command = commandFor(plan.os(), script);
        new ProcessBuilder(command).start();
        return script;
    }

    String scriptPreview(UninstallPlan plan) {
        return scriptFor(plan);
    }

    private List<String> commandFor(OperatingSystem os, Path script) {
        return switch (os) {
            case WINDOWS -> List.of("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", script.toString());
            case MACOS, LINUX -> List.of("/bin/sh", script.toString());
            case UNSUPPORTED -> List.of();
        };
    }

    private String scriptFor(UninstallPlan plan) {
        return switch (plan.os()) {
            case MACOS -> macScript(plan);
            case WINDOWS -> windowsScript(plan);
            case LINUX -> linuxScript(plan);
            case UNSUPPORTED -> "";
        };
    }

    private String macScript(UninstallPlan plan) {
        return "#!/bin/sh\n"
                + "sleep 2\n"
                + "rm -rf -- " + sh(plan.appDataDirectory()) + "\n"
                + deletePathLine(plan.installDirectory())
                + extraDeleteLines(plan.extraUserPaths())
                + "rm -f -- \"$0\"\n";
    }

    private String linuxScript(UninstallPlan plan) {
        return "#!/bin/sh\n"
                + "sleep 2\n"
                + "rm -rf -- " + sh(plan.appDataDirectory()) + "\n"
                + "if command -v dpkg >/dev/null 2>&1 && dpkg -s neuralarc >/dev/null 2>&1; then\n"
                + "  if command -v pkexec >/dev/null 2>&1; then\n"
                + "    pkexec env DEBIAN_FRONTEND=noninteractive apt-get remove --purge -y neuralarc >/dev/null 2>&1 || true\n"
                + "  elif command -v sudo >/dev/null 2>&1; then\n"
                + "    sudo env DEBIAN_FRONTEND=noninteractive apt-get remove --purge -y neuralarc >/dev/null 2>&1 || true\n"
                + "  fi\n"
                + "fi\n"
                + deletePathLine(plan.installDirectory())
                + "rm -rf -- /opt/neuralarc /opt/NeuralArc\n"
                + extraDeleteLines(plan.extraUserPaths())
                + "rm -f -- \"$0\"\n";
    }

    private String windowsScript(UninstallPlan plan) {
        return "$ErrorActionPreference = 'SilentlyContinue'\n"
                + "Start-Sleep -Seconds 2\n"
                + "Remove-Item -LiteralPath " + ps(plan.appDataDirectory()) + " -Recurse -Force\n"
                + "$uninstallRoots = @(\n"
                + "  'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\*',\n"
                + "  'HKLM:\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\*',\n"
                + "  'HKLM:\\Software\\WOW6432Node\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\*'\n"
                + ")\n"
                + "$entry = Get-ItemProperty $uninstallRoots | Where-Object { $_.DisplayName -eq 'NeuralArc' } | Select-Object -First 1\n"
                + "if ($entry) {\n"
                + "  $command = if ($entry.QuietUninstallString) { $entry.QuietUninstallString } else { $entry.UninstallString }\n"
                + "  if ($command) { Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', $command -Wait }\n"
                + "}\n"
                + deleteWindowsPathLine(plan.installDirectory())
                + windowsExtraDeleteLines(plan.extraUserPaths())
                + "Remove-Item -LiteralPath $PSCommandPath -Force\n";
    }

    private String deletePathLine(Path path) {
        if (path == null) {
            return "";
        }
        return "rm -rf -- " + sh(path) + "\n";
    }

    private String deleteWindowsPathLine(Path path) {
        if (path == null) {
            return "";
        }
        return "Remove-Item -LiteralPath " + ps(path) + " -Recurse -Force\n";
    }

    private String extraDeleteLines(List<Path> paths) {
        StringBuilder builder = new StringBuilder();
        for (Path path : paths) {
            builder.append("rm -rf -- ").append(sh(path)).append('\n');
        }
        return builder.toString();
    }

    private String windowsExtraDeleteLines(List<Path> paths) {
        StringBuilder builder = new StringBuilder();
        for (Path path : paths) {
            builder.append("Remove-Item -LiteralPath ").append(ps(path)).append(" -Recurse -Force\n");
        }
        return builder.toString();
    }

    private OperatingSystem currentOperatingSystem() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("mac")) {
            return OperatingSystem.MACOS;
        }
        if (osName.contains("win")) {
            return OperatingSystem.WINDOWS;
        }
        if (osName.contains("linux")) {
            return OperatingSystem.LINUX;
        }
        return OperatingSystem.UNSUPPORTED;
    }

    private Path installDirectory(OperatingSystem os) {
        Path codeLocation = codeLocation();
        if (os == OperatingSystem.MACOS) {
            Path appBundle = findParentWithSuffix(codeLocation, ".app");
            if (appBundle != null) {
                return appBundle;
            }
            Path applicationsBundle = Path.of(System.getProperty("user.home"), "Applications", "NeuralArc.app");
            if (Files.exists(applicationsBundle)) {
                return applicationsBundle;
            }
            Path globalApplicationsBundle = Path.of("/Applications/NeuralArc.app");
            return Files.exists(globalApplicationsBundle) ? globalApplicationsBundle : parentOrNull(codeLocation);
        }
        if (os == OperatingSystem.WINDOWS) {
            Path appRoot = findParentNamed(codeLocation, "NeuralArc");
            return appRoot == null ? parentOrNull(codeLocation) : appRoot;
        }
        if (os == OperatingSystem.LINUX) {
            Path optLower = Path.of("/opt/neuralarc");
            if (Files.exists(optLower)) {
                return optLower;
            }
            Path optExact = Path.of("/opt/NeuralArc");
            return Files.exists(optExact) ? optExact : parentOrNull(codeLocation);
        }
        return null;
    }

    private List<Path> extraUserPaths(OperatingSystem os) {
        List<Path> paths = new ArrayList<>();
        String home = System.getProperty("user.home", "");
        if (home.isBlank()) {
            return paths;
        }
        if (os == OperatingSystem.WINDOWS) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isBlank()) {
                paths.add(Path.of(appData, "Microsoft", "Windows", "Start Menu", "Programs", "NeuralArc"));
                paths.add(Path.of(appData, "Microsoft", "Windows", "Start Menu", "Programs", "NeuralArc.lnk"));
            }
            paths.add(Path.of(home, "Desktop", "NeuralArc.lnk"));
        } else if (os == OperatingSystem.LINUX) {
            paths.add(Path.of(home, ".local", "share", "applications", "NeuralArc.desktop"));
            paths.add(Path.of(home, ".local", "share", "applications", "neuralarc.desktop"));
            paths.add(Path.of(home, "Desktop", "NeuralArc.desktop"));
        } else if (os == OperatingSystem.MACOS) {
            paths.add(Path.of(home, "Desktop", "NeuralArc.app"));
        }
        return paths;
    }

    private Path codeLocation() {
        try {
            return Path.of(AppUninstallService.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException | RuntimeException ex) {
            return Path.of(System.getProperty("user.dir", "."));
        }
    }

    private Path findParentWithSuffix(Path start, String suffix) {
        Path current = start;
        while (current != null) {
            Path fileName = current.getFileName();
            if (fileName != null && fileName.toString().endsWith(suffix)) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    private Path findParentNamed(Path start, String name) {
        Path current = start;
        while (current != null) {
            Path fileName = current.getFileName();
            if (fileName != null && fileName.toString().equalsIgnoreCase(name)) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    private Path parentOrNull(Path path) {
        return path == null ? null : path.getParent();
    }

    private String scriptExtension(OperatingSystem os) {
        return os == OperatingSystem.WINDOWS ? ".ps1" : ".sh";
    }

    private String sh(Path path) {
        return "'" + path.toString().replace("'", "'\"'\"'") + "'";
    }

    private String ps(Path path) {
        return "'" + path.toString().replace("'", "''") + "'";
    }

    public enum OperatingSystem {
        MACOS,
        WINDOWS,
        LINUX,
        UNSUPPORTED
    }

    public record UninstallPlan(
            OperatingSystem os,
            Path installDirectory,
            Path appDataDirectory,
            List<Path> extraUserPaths
    ) {
        public UninstallPlan {
            extraUserPaths = extraUserPaths == null ? List.of() : List.copyOf(extraUserPaths);
        }

        public String summary() {
            return "OS=" + os
                    + ", installDirectory=" + (installDirectory == null ? "not detected" : installDirectory)
                    + ", appDataDirectory=" + appDataDirectory
                    + ", generatedAt=" + Instant.now();
        }
    }
}
