package com.neuralarc.service;

import com.neuralarc.model.AgentSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentSettingsPersistenceTest {
    @TempDir
    Path tempDir;

    @Test
    void theAnalystIsOffUntilTheOperatorTurnsItOn() {
        AgentSettings defaults = new AppSettingsService(tempDir.resolve("agent-defaults.db")).loadAgentSettings();

        assertFalse(defaults.enabled());
        assertEquals("", defaults.apiKey());
        assertFalse(defaults.ready(), "no key, no run");
        assertEquals(AgentSettings.DEFAULT_MODEL, defaults.model());
    }

    @Test
    void settingsSurviveARestartAndTheKeyIsStoredEncrypted() throws Exception {
        Path dbPath = tempDir.resolve("agent-settings.db");
        AppSettingsService service = new AppSettingsService(dbPath);

        service.saveAgentSettings(new AgentSettings(true, "sk-ant-test-key", "claude-opus-5", 6, 30));
        AgentSettings loaded = new AppSettingsService(dbPath).loadAgentSettings();

        assertTrue(loaded.enabled());
        assertEquals("sk-ant-test-key", loaded.apiKey());
        assertEquals("claude-opus-5", loaded.model());
        assertEquals(6, loaded.maxTurns());
        assertEquals(30, loaded.maxToolCalls());
        assertTrue(loaded.ready());

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
             PreparedStatement ps = connection.prepareStatement("SELECT value, encrypted FROM app_settings WHERE key = ?")) {
            ps.setString(1, "agent.anthropic.apiKey");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt("encrypted"));
                assertFalse("sk-ant-test-key".equals(rs.getString("value")), "the key must never sit in plaintext");
            }
        }
    }

    @Test
    void outlandishLimitsAreClampedRatherThanTrusted() throws Exception {
        AppSettingsService service = new AppSettingsService(tempDir.resolve("agent-limits.db"));

        service.saveAgentSettings(new AgentSettings(true, "k", "claude-opus-5", 0, 100_000));
        AgentSettings loaded = service.loadAgentSettings();

        assertEquals(AgentSettings.DEFAULT_MAX_TURNS, loaded.maxTurns(), "zero would make the analyst useless");
        assertEquals(AgentSettings.MAX_TOOL_CALLS_CEILING, loaded.maxToolCalls(), "a pasted number must not run up a bill");
    }

    @Test
    void anEnabledAnalystWithNoKeyIsNotReady() {
        assertFalse(new AgentSettings(true, "   ", null, 5, 5).ready());
    }
}
