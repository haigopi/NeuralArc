package com.neuralarc.ui;

import com.neuralarc.model.ApplicationMode;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlpacaCredentialChangeSupportTest {
    @Test
    void detectsChangedExistingApiKeysOnly() {
        Map<ApplicationMode, String[]> applied = new EnumMap<>(ApplicationMode.class);
        applied.put(ApplicationMode.PAPER, new String[]{"old-paper-key", "old-secret"});
        applied.put(ApplicationMode.LIVE, new String[]{"", ""});

        Map<ApplicationMode, String[]> pending = new EnumMap<>(ApplicationMode.class);
        pending.put(ApplicationMode.PAPER, new String[]{"new-paper-key", "new-secret"});
        pending.put(ApplicationMode.LIVE, new String[]{"new-live-key", "new-live-secret"});

        List<AlpacaCredentialChangeSupport.ApiKeyChange> changes =
                AlpacaCredentialChangeSupport.changedApiKeys(applied, pending);

        assertEquals(1, changes.size());
        assertEquals(ApplicationMode.PAPER, changes.getFirst().mode());
        assertEquals("old-paper-key", changes.getFirst().oldKey());
        assertEquals("new-paper-key", changes.getFirst().newKey());
    }

    @Test
    void ignoresUnchangedAndBlankApiKeys() {
        Map<ApplicationMode, String[]> applied = new EnumMap<>(ApplicationMode.class);
        applied.put(ApplicationMode.PAPER, new String[]{"same-paper-key", "old-secret"});
        applied.put(ApplicationMode.LIVE, new String[]{"old-live-key", "old-secret"});

        Map<ApplicationMode, String[]> pending = new EnumMap<>(ApplicationMode.class);
        pending.put(ApplicationMode.PAPER, new String[]{"same-paper-key", "new-secret"});
        pending.put(ApplicationMode.LIVE, new String[]{"", ""});

        List<AlpacaCredentialChangeSupport.ApiKeyChange> changes =
                AlpacaCredentialChangeSupport.changedApiKeys(applied, pending);

        assertTrue(changes.isEmpty());
    }
}
