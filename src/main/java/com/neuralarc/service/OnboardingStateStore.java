package com.neuralarc.service;

import com.neuralarc.util.AppMetadata;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class OnboardingStateStore {
    private static final String KEY_COMPLETED = "completed";
    private final Path filePath;

    public OnboardingStateStore() {
        this(AppMetadata.appDataDirectory().resolve("onboarding.properties"));
    }

    OnboardingStateStore(Path filePath) {
        this.filePath = filePath;
    }

    public boolean isCompleted() {
        Properties properties = new Properties();
        if (!Files.exists(filePath)) {
            return false;
        }
        try (InputStream input = Files.newInputStream(filePath)) {
            properties.load(input);
            return Boolean.parseBoolean(properties.getProperty(KEY_COMPLETED, "false"));
        } catch (IOException ignored) {
            return false;
        }
    }

    public void markCompleted() {
        Properties properties = new Properties();
        properties.setProperty(KEY_COMPLETED, "true");
        try {
            Files.createDirectories(filePath.getParent());
            try (OutputStream output = Files.newOutputStream(filePath)) {
                properties.store(output, "NeuralArc onboarding state");
            }
        } catch (IOException ignored) {
            // Best-effort only. The user can still use the app if this cannot be saved.
        }
    }
}
