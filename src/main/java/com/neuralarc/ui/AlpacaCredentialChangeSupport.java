package com.neuralarc.ui;

import com.neuralarc.model.ApplicationMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class AlpacaCredentialChangeSupport {
    private AlpacaCredentialChangeSupport() {
    }

    static List<ApiKeyChange> changedApiKeys(
            Map<ApplicationMode, String[]> appliedCredentials,
            Map<ApplicationMode, String[]> pendingCredentials
    ) {
        List<ApiKeyChange> changes = new ArrayList<>();
        for (ApplicationMode mode : ApplicationMode.values()) {
            String oldKey = apiKey(appliedCredentials, mode);
            String newKey = apiKey(pendingCredentials, mode);
            if (!oldKey.isBlank() && !newKey.isBlank() && !oldKey.equals(newKey)) {
                changes.add(new ApiKeyChange(mode, oldKey, newKey));
            }
        }
        return changes;
    }

    private static String apiKey(Map<ApplicationMode, String[]> credentials, ApplicationMode mode) {
        if (credentials == null) {
            return "";
        }
        String[] values = credentials.get(mode);
        if (values == null || values.length == 0 || values[0] == null) {
            return "";
        }
        return values[0].trim();
    }

    record ApiKeyChange(ApplicationMode mode, String oldKey, String newKey) {
    }
}
