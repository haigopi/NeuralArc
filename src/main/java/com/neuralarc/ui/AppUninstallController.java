package com.neuralarc.ui;

import com.neuralarc.service.AppUninstallService;

import javax.swing.JOptionPane;
import java.awt.Component;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;

final class AppUninstallController {
    private final Component parent;
    private final UserActionLogSupport actionLog;
    private final Consumer<String> logSink;
    private final Runnable shutdownStrategies;
    private final Runnable closeApplication;

    AppUninstallController(
            Component parent,
            UserActionLogSupport actionLog,
            Consumer<String> logSink,
            Runnable shutdownStrategies,
            Runnable closeApplication
    ) {
        this.parent = Objects.requireNonNull(parent, "parent");
        this.actionLog = Objects.requireNonNull(actionLog, "actionLog");
        this.logSink = Objects.requireNonNull(logSink, "logSink");
        this.shutdownStrategies = Objects.requireNonNull(shutdownStrategies, "shutdownStrategies");
        this.closeApplication = Objects.requireNonNull(closeApplication, "closeApplication");
    }

    void confirmAndScheduleUninstall() {
        actionLog.started("Uninstall NeuralArc");
        AppUninstallService uninstallService = new AppUninstallService();
        AppUninstallService.UninstallPlan plan = uninstallService.createPlan();
        if (plan.os() == AppUninstallService.OperatingSystem.UNSUPPORTED) {
            actionLog.failed("Uninstall NeuralArc", "Unsupported operating system.");
            JOptionPane.showMessageDialog(
                    parent,
                    "Automatic uninstall is not supported on this operating system.",
                    "Uninstall NeuralArc",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        int choice = JOptionPane.showConfirmDialog(
                parent,
                confirmationMessage(plan),
                "Uninstall NeuralArc",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (choice != JOptionPane.YES_OPTION) {
            actionLog.canceled("Uninstall NeuralArc");
            return;
        }

        try {
            Path script = uninstallService.scheduleUninstall(plan);
            logSink.accept("[UNINSTALL] Scheduled uninstall. " + plan.summary() + " script=" + script);
            actionLog.completed("Uninstall NeuralArc", "Uninstall scheduled. Closing application.");
            JOptionPane.showMessageDialog(
                    parent,
                    "NeuralArc will close now. The uninstaller will continue in the background.",
                    "Uninstall Scheduled",
                    JOptionPane.INFORMATION_MESSAGE
            );
            shutdownStrategies.run();
            closeApplication.run();
        } catch (Exception ex) {
            actionLog.failed("Uninstall NeuralArc", ex.getMessage());
            JOptionPane.showMessageDialog(
                    parent,
                    "Failed to start uninstall: " + ex.getMessage(),
                    "Uninstall Failed",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private String confirmationMessage(AppUninstallService.UninstallPlan plan) {
        return "<html><body style='width:420px'>"
                + "<b>Uninstall NeuralArc from this computer?</b><br><br>"
                + "This will close NeuralArc and remove the application files, local settings, logs, saved strategies, "
                + "trade history, and shortcuts for this user.<br><br>"
                + "<b>Detected OS:</b> " + plan.os() + "<br>"
                + "<b>Application:</b> " + escapeHtml(plan.installDirectory() == null ? "Not detected" : plan.installDirectory().toString()) + "<br>"
                + "<b>Local data:</b> " + escapeHtml(plan.appDataDirectory().toString()) + "<br><br>"
                + "This does not close or delete your Alpaca account. Broker-side orders or positions should be reviewed before uninstalling."
                + "</body></html>";
    }

    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
