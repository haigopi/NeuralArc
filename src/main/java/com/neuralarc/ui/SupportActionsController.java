package com.neuralarc.ui;

import com.neuralarc.service.FeedbackEmailService;

import javax.swing.JFrame;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class SupportActionsController {
    private final JFrame parent;
    private final Supplier<String> userEmail;
    private final UserActionLogSupport actionLog;
    private final Consumer<String> logSink;

    SupportActionsController(
            JFrame parent,
            Supplier<String> userEmail,
            UserActionLogSupport actionLog,
            Consumer<String> logSink
    ) {
        this.parent = Objects.requireNonNull(parent, "parent");
        this.userEmail = Objects.requireNonNull(userEmail, "userEmail");
        this.actionLog = Objects.requireNonNull(actionLog, "actionLog");
        this.logSink = Objects.requireNonNull(logSink, "logSink");
    }

    void openRequestNewFeatureDialog() {
        String email = userEmail.get();
        RequestNewFeatureDialog dialog = new RequestNewFeatureDialog(
                parent,
                email,
                FeedbackEmailService.fromConfiguration()
        );
        if (dialog.showDialog()) {
            logSink.accept("[Request New Feature] Sent and copied to " + email);
            actionLog.completed("Request New Feature", "Request sent.");
        } else {
            actionLog.canceled("Request New Feature");
        }
    }

    void openContactUsDialog() {
        String email = userEmail.get();
        ContactUsDialog dialog = new ContactUsDialog(
                parent,
                email,
                FeedbackEmailService.fromConfiguration()
        );
        if (dialog.showDialog()) {
            logSink.accept("[Contact Us / Feedback] Sent and copied to " + email);
            actionLog.completed("Contact Us / Feedback", "Message sent.");
        } else {
            actionLog.canceled("Contact Us / Feedback");
        }
    }
}
