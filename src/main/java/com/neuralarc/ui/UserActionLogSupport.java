package com.neuralarc.ui;

import java.util.Objects;
import java.util.function.Consumer;

final class UserActionLogSupport {
    private final Consumer<String> logSink;

    UserActionLogSupport(Consumer<String> logSink) {
        this.logSink = Objects.requireNonNull(logSink, "logSink");
    }

    void started(String actionName) {
        log(actionName, "Started.");
    }

    void completed(String actionName) {
        log(actionName, "Completed.");
    }

    void completed(String actionName, String detail) {
        log(actionName, detail == null || detail.isBlank() ? "Completed." : "Completed. " + detail);
    }

    void canceled(String actionName) {
        log(actionName, "Canceled by user.");
    }

    void skipped(String actionName, String reason) {
        log(actionName, "Skipped. " + normalize(reason));
    }

    void failed(String actionName, String reason) {
        log(actionName, "Failed. " + normalize(reason));
    }

    private void log(String actionName, String message) {
        logSink.accept("[ACTION][" + normalizeAction(actionName) + "] " + message);
    }

    private String normalizeAction(String actionName) {
        return actionName == null || actionName.isBlank() ? "User Action" : actionName.trim();
    }

    private String normalize(String message) {
        return message == null || message.isBlank() ? "No additional details." : message.trim();
    }
}
