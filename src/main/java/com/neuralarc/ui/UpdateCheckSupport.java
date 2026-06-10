package com.neuralarc.ui;

import com.neuralarc.service.GitHubReleaseUpdateService;
import com.neuralarc.util.AppMetadata;
import com.neuralarc.util.FontLoader;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.net.URI;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

final class UpdateCheckSupport {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy")
            .withZone(ZoneId.systemDefault());

    private UpdateCheckSupport() {
    }

    static void checkForUpdates(Component parent, AbstractButton triggerButton) {
        checkForUpdates(parent, triggerButton, null);
    }

    static void checkForUpdates(Component parent, AbstractButton triggerButton, UserActionLogSupport actionLog) {
        if (!AppMetadata.updateCheckEnabled()) {
            logSkipped(actionLog, "Update checks are disabled in app.properties.");
            JOptionPane.showMessageDialog(parent,
                    "Update checks are disabled in app.properties.",
                    "Updates Disabled",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        GitHubReleaseUpdateService service = new GitHubReleaseUpdateService(AppMetadata.githubLatestReleaseUrl());
        if (!service.isConfigured()) {
            logFailed(actionLog, service.missingConfigMessage());
            JOptionPane.showMessageDialog(parent,
                    service.missingConfigMessage(),
                    "Update URL Missing",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        String originalText = triggerButton == null ? "" : triggerButton.getText();
        if (triggerButton != null) {
            triggerButton.setEnabled(false);
            triggerButton.setText("Checking...");
        }

        SwingWorker<GitHubReleaseUpdateService.UpdateCheckResult, Void> worker = new SwingWorker<>() {
            @Override
            protected GitHubReleaseUpdateService.UpdateCheckResult doInBackground() throws Exception {
                return service.checkForUpdates(AppMetadata.version());
            }

            @Override
            protected void done() {
                if (triggerButton != null) {
                    triggerButton.setEnabled(true);
                    triggerButton.setText(originalText);
                }
                try {
                    GitHubReleaseUpdateService.UpdateCheckResult result = get();
                    if (actionLog != null) {
                        actionLog.completed(
                                "Check Updates",
                                result.updateAvailable()
                                        ? "Update available: " + safe(result.latestVersion())
                                        : "Application is up to date."
                        );
                    }
                    showResultDialog(parent, result);
                } catch (Exception ex) {
                    logFailed(actionLog, ex.getMessage());
                    JOptionPane.showMessageDialog(parent,
                            "Unable to check for updates.\n" + ex.getMessage(),
                            "Update Check Failed",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private static void logSkipped(UserActionLogSupport actionLog, String reason) {
        if (actionLog != null) {
            actionLog.skipped("Check Updates", reason);
        }
    }

    private static void logFailed(UserActionLogSupport actionLog, String reason) {
        if (actionLog != null) {
            actionLog.failed("Check Updates", reason);
        }
    }

    private static void showResultDialog(Component parent, GitHubReleaseUpdateService.UpdateCheckResult result) {
        if (!result.updateAvailable()) {
            JOptionPane.showMessageDialog(parent,
                    "<html><b>You're up to date.</b><br><br>Current version: "
                            + AppMetadata.version()
                            + "</html>",
                    "No Update Available",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBorder(new EmptyBorder(4, 4, 4, 4));

        String published = result.publishedAt() == null ? "" : DATE_FORMAT.format(result.publishedAt());
        JLabel header = new JLabel("<html><b>Update Available</b><br>"
                + "Current version: " + safe(result.currentVersion()) + "<br>"
                + "Latest version: " + safe(result.latestVersion())
                + (published.isBlank() ? "" : "<br>Published: " + published)
                + "<br><br>" + updateDataSafetyHtml(result)
                + "</html>");
        header.setFont(FontLoader.ui(java.awt.Font.PLAIN, 12f));
        panel.add(header, BorderLayout.NORTH);

        JTextArea notes = new JTextArea(result.releaseNotes().isBlank() ? "No release notes provided." : result.releaseNotes());
        notes.setEditable(false);
        notes.setLineWrap(true);
        notes.setWrapStyleWord(true);
        notes.setCaretPosition(0);
        notes.setBorder(new EmptyBorder(8, 8, 8, 8));
        JScrollPane notesScroll = new JScrollPane(notes);
        notesScroll.setPreferredSize(new Dimension(520, 220));
        panel.add(notesScroll, BorderLayout.CENTER);

        Object[] options = result.downloadUrl().isBlank()
                ? new Object[]{"View Release", "Close"}
                : new Object[]{"Download Update", "View Release", "Close"};
        int selection = JOptionPane.showOptionDialog(parent,
                panel,
                result.releaseName().isBlank() ? "Update Available" : result.releaseName(),
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.INFORMATION_MESSAGE,
                null,
                options,
                options[0]);

        if (selection == 0) {
            if (result.downloadUrl().isBlank()) {
                openUrl(parent, result.releasePageUrl());
            } else if (confirmDownloadWithDataWarning(parent, result)) {
                openUrl(parent, result.downloadUrl());
            }
        } else if (!result.downloadUrl().isBlank() && selection == 1) {
            openUrl(parent, result.releasePageUrl());
        }
    }

    private static String updateDataSafetyHtml(GitHubReleaseUpdateService.UpdateCheckResult result) {
        if (result.dataCompatibilityWarning()) {
            return "<b style='color:#B00020'>Data compatibility warning:</b> "
                    + "This update is marked as potentially incompatible with local application data. "
                    + "Local strategy/history data may be lost in rare situations. "
                    + "NeuralArc will still pull and sync broker-side positions/orders where available after installation.";
        }
        return "<b>Data safety:</b> Normal updates keep local NeuralArc data in place. "
                + "Do not uninstall/reset application data during the update unless the release notes explicitly require it.";
    }

    private static boolean confirmDownloadWithDataWarning(Component parent, GitHubReleaseUpdateService.UpdateCheckResult result) {
        if (!result.dataCompatibilityWarning()) {
            return true;
        }
        int choice = JOptionPane.showConfirmDialog(parent,
                "<html><body style='width:440px'>"
                        + "<b>This update may not be compatible with existing local data.</b><br><br>"
                        + "There may be local data loss with this update in rare situations. "
                        + "After installation, NeuralArc will pull and sync broker-side data where available, "
                        + "but local-only strategy history/settings may not be fully recoverable.<br><br>"
                        + "Continue to download this update?"
                        + "</body></html>",
                "Data Compatibility Warning",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        return choice == JOptionPane.YES_OPTION;
    }

    private static void openUrl(Component parent, String url) {
        if (url == null || url.isBlank()) {
            JOptionPane.showMessageDialog(parent,
                    "No download or release URL is available for this update.",
                    "Missing Download URL",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                throw new IllegalStateException("Desktop browsing is not supported on this system.");
            }
            Desktop.getDesktop().browse(URI.create(url));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parent,
                    "Unable to open the download page automatically.\n" + url,
                    "Open Download Manually",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
