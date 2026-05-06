package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegalDisclosureControllerTest {
    @Test
    void saveAndLoadAcceptedRoundTripsState() throws Exception {
        Path tempDir = Files.createTempDirectory("neuralarc-legal-disclosure-test");
        LegalDisclosureController controller = new LegalDisclosureController(
                tempDir.resolve("legal-disclosure.properties"),
                "Disclosure text"
        );

        assertFalse(controller.loadAccepted());

        controller.saveAccepted(true);
        assertTrue(controller.loadAccepted());

        controller.saveAccepted(false);
        assertFalse(controller.loadAccepted());
    }
}

