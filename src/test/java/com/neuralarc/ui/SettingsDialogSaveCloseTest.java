package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsDialogSaveCloseTest {

    @Test
    void executesCloseWhenSaveSucceeds() {
        AtomicBoolean closed = new AtomicBoolean(false);

        boolean result = SettingsDialog.executeSaveAndClose(
                () -> true,
                () -> closed.set(true)
        );

        assertTrue(result);
        assertTrue(closed.get());
    }

    @Test
    void doesNotCloseWhenSaveFails() {
        AtomicBoolean closed = new AtomicBoolean(false);

        boolean result = SettingsDialog.executeSaveAndClose(
                () -> false,
                () -> closed.set(true)
        );

        assertFalse(result);
        assertFalse(closed.get());
    }
}

