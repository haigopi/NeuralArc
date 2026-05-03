package com.neuralarc.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OnboardingStateStoreTest {
    @Test
    void onboardingCompletionPersists() throws Exception {
        Path tempFile = Files.createTempDirectory("neuralarc-onboarding-test").resolve("onboarding.properties");
        OnboardingStateStore store = new OnboardingStateStore(tempFile);

        assertFalse(store.isCompleted());
        store.markCompleted();

        OnboardingStateStore reloaded = new OnboardingStateStore(tempFile);
        assertTrue(reloaded.isCompleted());
    }
}
