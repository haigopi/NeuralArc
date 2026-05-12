package com.neuralarc.service;

import com.neuralarc.db.AppDatabase;
import com.neuralarc.model.AiProviderType;
import com.neuralarc.model.AiRecommendationSettings;
import com.neuralarc.model.ApplicationMode;
import com.neuralarc.model.BrokerType;
import com.neuralarc.security.CredentialManager;
import com.neuralarc.util.AppMetadata;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Properties;

public class AppSettingsService {
    public static final boolean DEFAULT_AUTO_PAUSE_POLLING_WHEN_MARKET_CLOSED = true;
    public static final boolean DEFAULT_EXTENDED_HOURS_TRADING_ENABLED = false;
    public static final boolean DEFAULT_ALLOW_DUPLICATE_SYMBOL_STRATEGIES = false;
    public static final boolean DEFAULT_EMAIL_ON_BUY_EXPECTED = false;
    public static final boolean DEFAULT_EMAIL_ON_SELL_EXECUTED = false;
    public static final int DEFAULT_STRATEGY_POLLING_SECONDS = 60;
    public static final boolean DEFAULT_REPEAT_CYCLE_AFTER_PROFIT_EXIT_ENABLED = true;
    public static final boolean DEFAULT_RESUBMIT_ON_EXPIRY_ENABLED = true;

    private static final String KEY_USER_EMAIL = "userEmail";
    private static final String KEY_TELEMETRY_ENABLED = "telemetryEnabled";
    private static final String KEY_AUTO_PAUSE = "autoPausePollingWhenMarketClosed";
    private static final String KEY_EXTENDED_HOURS = "extendedHoursTradingEnabled";
    private static final String KEY_ALLOW_DUPLICATE_SYMBOLS = "allowDuplicateSymbolStrategies";
    private static final String KEY_EMAIL_ON_BUY_EXPECTED = "emailOnBuyExpected";
    private static final String KEY_EMAIL_ON_SELL_EXECUTED = "emailOnSellExecuted";
    private static final String KEY_STRATEGY_DEFAULT_POLLING_SECONDS = "strategyDefaultPollingSeconds";
    private static final String KEY_STRATEGY_DEFAULT_REPEAT_CYCLE_AFTER_PROFIT_EXIT = "strategyDefaultRepeatCycleAfterProfitExit";
    private static final String KEY_STRATEGY_DEFAULT_RESUBMIT_ON_EXPIRY = "strategyDefaultResubmitOnExpiry";
    private static final String KEY_BROKER = "broker";
    private static final String KEY_APPLICATION_MODE = "applicationMode";
    private static final String KEY_ENDPOINT = "endpoint";
    private static final String KEY_MIGRATION_V1 = "migration.legacyFilesToSqlite.v1";
    private static final String KEY_AI_PROVIDER = "ai.provider";
    private static final String KEY_AI_JETSON_HOST = "ai.jetson.host";
    private static final String KEY_AI_JETSON_PORT = "ai.jetson.port";
    private static final String KEY_AI_JETSON_PATH = "ai.jetson.path";
    private static final String KEY_AI_JETSON_CONNECT_TIMEOUT = "ai.jetson.connectTimeout";
    private static final String KEY_AI_JETSON_READ_TIMEOUT = "ai.jetson.readTimeout";
    private static final String KEY_AI_OPENAI_API_KEY = "ai.openai.apiKey";
    private static final String KEY_AI_OPENAI_MODEL = "ai.openai.model";
    private static final String KEY_AI_OPENAI_TIMEOUT = "ai.openai.timeout";

    private final AppDatabase database;
    private final Connection connection;
    private final Path legacyDataDirectory;

    public AppSettingsService() {
        this(AppDatabase.getInstance(), AppMetadata.appDataDirectory());
    }

    AppSettingsService(Path databasePath) {
        this(AppDatabase.open(databasePath), AppMetadata.appDataDirectory());
    }

    AppSettingsService(AppDatabase database) {
        this(database, AppMetadata.appDataDirectory());
    }

    AppSettingsService(Path databasePath, Path legacyDataDirectory) {
        this(AppDatabase.open(databasePath), legacyDataDirectory);
    }

    AppSettingsService(AppDatabase database, Path legacyDataDirectory) {
        this.database = database;
        this.connection = database.get();
        this.legacyDataDirectory = legacyDataDirectory;
        migrateLegacyFilesIfNeeded();
    }

    public AppSettings load() {
        return new AppSettings(
                readSetting(KEY_USER_EMAIL, false).trim(),
                parseBoolean(readSetting(KEY_TELEMETRY_ENABLED, false), true),
                parseBoolean(readSetting(KEY_AUTO_PAUSE, false), DEFAULT_AUTO_PAUSE_POLLING_WHEN_MARKET_CLOSED),
                parseBoolean(readSetting(KEY_EXTENDED_HOURS, false), DEFAULT_EXTENDED_HOURS_TRADING_ENABLED),
                parseBroker(readSetting(KEY_BROKER, false)),
                parseMode(readSetting(KEY_APPLICATION_MODE, false)),
                parseBoolean(readSetting(KEY_ALLOW_DUPLICATE_SYMBOLS, false), DEFAULT_ALLOW_DUPLICATE_SYMBOL_STRATEGIES),
                parseBoolean(readSetting(KEY_EMAIL_ON_BUY_EXPECTED, false), DEFAULT_EMAIL_ON_BUY_EXPECTED),
                parseBoolean(readSetting(KEY_EMAIL_ON_SELL_EXECUTED, false), DEFAULT_EMAIL_ON_SELL_EXECUTED),
                parsePositiveInt(readSetting(KEY_STRATEGY_DEFAULT_POLLING_SECONDS, false), DEFAULT_STRATEGY_POLLING_SECONDS),
                parseBoolean(readSetting(KEY_STRATEGY_DEFAULT_REPEAT_CYCLE_AFTER_PROFIT_EXIT, false), DEFAULT_REPEAT_CYCLE_AFTER_PROFIT_EXIT_ENABLED),
                parseBoolean(readSetting(KEY_STRATEGY_DEFAULT_RESUBMIT_ON_EXPIRY, false), DEFAULT_RESUBMIT_ON_EXPIRY_ENABLED)
        );
    }

    public void save(AppSettings settings) throws IOException {
        try {
            writeSetting(KEY_USER_EMAIL, settings.userEmail() == null ? "" : settings.userEmail().trim(), false);
            writeSetting(KEY_TELEMETRY_ENABLED, String.valueOf(settings.telemetryEnabled()), false);
            writeSetting(KEY_AUTO_PAUSE, String.valueOf(settings.autoPausePollingWhenMarketClosed()), false);
            writeSetting(KEY_EXTENDED_HOURS, String.valueOf(settings.extendedHoursTradingEnabled()), false);
            writeSetting(KEY_ALLOW_DUPLICATE_SYMBOLS, String.valueOf(settings.allowDuplicateSymbolStrategies()), false);
            writeSetting(KEY_EMAIL_ON_BUY_EXPECTED, String.valueOf(settings.emailOnBuyExpected()), false);
            writeSetting(KEY_EMAIL_ON_SELL_EXECUTED, String.valueOf(settings.emailOnSellExecuted()), false);
            writeSetting(KEY_STRATEGY_DEFAULT_POLLING_SECONDS, String.valueOf(Math.max(1, settings.defaultStrategyPollingSeconds())), false);
            writeSetting(KEY_STRATEGY_DEFAULT_REPEAT_CYCLE_AFTER_PROFIT_EXIT, String.valueOf(settings.defaultRepeatCycleAfterProfitExitEnabled()), false);
            writeSetting(KEY_STRATEGY_DEFAULT_RESUBMIT_ON_EXPIRY, String.valueOf(settings.defaultResubmitOnExpiryEnabled()), false);
            writeSetting(KEY_BROKER, (settings.brokerType() == null ? BrokerType.ALPACA : settings.brokerType()).name(), false);
            writeSetting(KEY_APPLICATION_MODE, (settings.applicationMode() == null ? ApplicationMode.PAPER : settings.applicationMode()).name(), false);
        } catch (SQLException ex) {
            throw new IOException("Failed to persist app settings", ex);
        }
    }

    public String loadEndpoint() {
        String value = readSetting(KEY_ENDPOINT, false);
        return value.isBlank() ? AppMetadata.analyticsEndpointDefault() : value.trim();
    }

    public void saveEndpoint(String endpoint) throws IOException {
        try {
            writeSetting(KEY_ENDPOINT, endpoint == null ? "" : endpoint.trim(), false);
        } catch (SQLException ex) {
            throw new IOException("Failed to persist endpoint setting", ex);
        }
    }

    public void saveCredentials(ApplicationMode mode, String apiKey, String apiSecret) {
        ApplicationMode safeMode = mode == null ? ApplicationMode.PAPER : mode;
        String keySuffix = safeMode.name().toLowerCase();
        try {
            writeSetting("credentials." + keySuffix + ".apiKey", apiKey == null ? "" : apiKey.trim(), true);
            writeSetting("credentials." + keySuffix + ".apiSecret", apiSecret == null ? "" : apiSecret, true);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to persist credentials", ex);
        }
    }

    public String[] loadCredentials(ApplicationMode mode) {
        ApplicationMode safeMode = mode == null ? ApplicationMode.PAPER : mode;
        String keySuffix = safeMode.name().toLowerCase();
        String key = readSetting("credentials." + keySuffix + ".apiKey", true);
        String secret = readSetting("credentials." + keySuffix + ".apiSecret", true);
        return new String[]{key, secret};
    }

    public AiRecommendationSettings loadAiRecommendationSettings() {
        AiRecommendationSettings defaults = AiRecommendationSettings.defaults();
        return new AiRecommendationSettings(
                parseAiProvider(readSetting(KEY_AI_PROVIDER, false), defaults.providerType()),
                fallback(readSetting(KEY_AI_JETSON_HOST, false), defaults.jetsonHost()),
                parseInt(readSetting(KEY_AI_JETSON_PORT, false), defaults.jetsonPort()),
                fallback(readSetting(KEY_AI_JETSON_PATH, false), defaults.jetsonApiPath()),
                parseDuration(readSetting(KEY_AI_JETSON_CONNECT_TIMEOUT, false), defaults.jetsonConnectionTimeout()),
                parseDuration(readSetting(KEY_AI_JETSON_READ_TIMEOUT, false), defaults.jetsonReadTimeout()),
                readSetting(KEY_AI_OPENAI_API_KEY, true),
                fallback(readSetting(KEY_AI_OPENAI_MODEL, false), defaults.openAiModel()),
                parseDuration(readSetting(KEY_AI_OPENAI_TIMEOUT, false), defaults.openAiTimeout())
        );
    }

    public void saveAiRecommendationSettings(AiRecommendationSettings settings) throws IOException {
        AiRecommendationSettings safe = settings == null ? AiRecommendationSettings.defaults() : settings;
        try {
            writeSetting(KEY_AI_PROVIDER, safe.providerType().name(), false);
            writeSetting(KEY_AI_JETSON_HOST, safe.jetsonHost(), false);
            writeSetting(KEY_AI_JETSON_PORT, String.valueOf(safe.jetsonPort()), false);
            writeSetting(KEY_AI_JETSON_PATH, safe.jetsonApiPath(), false);
            writeSetting(KEY_AI_JETSON_CONNECT_TIMEOUT, safe.jetsonConnectionTimeout().toString(), false);
            writeSetting(KEY_AI_JETSON_READ_TIMEOUT, safe.jetsonReadTimeout().toString(), false);
            writeSetting(KEY_AI_OPENAI_API_KEY, safe.openAiApiKey(), true);
            writeSetting(KEY_AI_OPENAI_MODEL, safe.openAiModel(), false);
            writeSetting(KEY_AI_OPENAI_TIMEOUT, safe.openAiTimeout().toString(), false);
        } catch (SQLException ex) {
            throw new IOException("Failed to persist AI recommendation settings", ex);
        }
    }

    private boolean parseBoolean(String value, boolean fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return Boolean.parseBoolean(value.trim());
    }

    private BrokerType parseBroker(String value) {
        try {
            return BrokerType.valueOf(value.trim());
        } catch (Exception ignored) {
            return BrokerType.ALPACA;
        }
    }

    private ApplicationMode parseMode(String value) {
        try {
            return ApplicationMode.valueOf(value.trim());
        } catch (Exception ignored) {
            return ApplicationMode.PAPER;
        }
    }

    private AiProviderType parseAiProvider(String value, AiProviderType fallback) {
        try {
            return AiProviderType.valueOf(value.trim());
        } catch (Exception ignored) {
            return fallback == null ? AiProviderType.JETSON_LOCAL : fallback;
        }
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private int parsePositiveInt(String value, int fallback) {
        return Math.max(1, parseInt(value, fallback));
    }

    private Duration parseDuration(String value, Duration fallback) {
        try {
            return Duration.parse(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String readSetting(String key, boolean encrypted) {
        String sql = "SELECT value, encrypted FROM app_settings WHERE key = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return "";
                }
                String raw = rs.getString("value");
                boolean rowEncrypted = rs.getInt("encrypted") == 1;
                if (encrypted || rowEncrypted) {
                    return database.decrypt(raw);
                }
                return raw == null ? "" : raw;
            }
        } catch (SQLException ex) {
            return "";
        }
    }

    private void writeSetting(String key, String value, boolean encrypted) throws SQLException {
        String sql = """
                INSERT INTO app_settings (key, value, encrypted)
                VALUES (?, ?, ?)
                ON CONFLICT(key) DO UPDATE SET value = excluded.value, encrypted = excluded.encrypted
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, encrypted ? database.encrypt(value) : value);
            ps.setInt(3, encrypted ? 1 : 0);
            ps.executeUpdate();
        }
    }

    private void migrateLegacyFilesIfNeeded() {
        if ("1".equals(readSetting(KEY_MIGRATION_V1, false))) {
            return;
        }
        Path settingsFile = legacyDataDirectory.resolve("settings.properties");
        Path paperCredentialsFile = legacyDataDirectory.resolve("credentials-paper.properties");
        Path liveCredentialsFile = legacyDataDirectory.resolve("credentials-live.properties");
        Path legacyCredentialsFile = legacyDataDirectory.resolve("credentials.properties");

        try {
            migrateLegacySettings(settingsFile);
            migrateLegacyCredentials(paperCredentialsFile, ApplicationMode.PAPER);
            migrateLegacyCredentials(liveCredentialsFile, ApplicationMode.LIVE);
            // Legacy single-file credentials map to PAPER mode.
            migrateLegacyCredentials(legacyCredentialsFile, ApplicationMode.PAPER);
            writeSetting(KEY_MIGRATION_V1, "1", false);
            cleanupLegacyFiles(settingsFile, paperCredentialsFile, liveCredentialsFile, legacyCredentialsFile);
        } catch (Exception ignored) {
            // Keep startup resilient; migration can retry on the next load.
        }
    }

    private void cleanupLegacyFiles(Path settingsFile, Path paperCredentialsFile, Path liveCredentialsFile, Path legacyCredentialsFile) {
        try {
            Files.deleteIfExists(settingsFile);
            Files.deleteIfExists(paperCredentialsFile);
            Files.deleteIfExists(liveCredentialsFile);
            Files.deleteIfExists(legacyCredentialsFile);
        } catch (IOException ignored) {
            // Non-fatal cleanup: migration already succeeded and is now DB-backed.
        }
    }

    private void migrateLegacySettings(Path settingsFile) throws IOException, SQLException {
        if (!Files.exists(settingsFile)) {
            return;
        }
        Properties properties = new Properties();
        try (var input = Files.newInputStream(settingsFile)) {
            properties.load(input);
        }
        writeSettingIfMissing(KEY_USER_EMAIL, properties.getProperty(KEY_USER_EMAIL, "").trim(), false);
        writeSettingIfMissing(KEY_TELEMETRY_ENABLED, properties.getProperty(KEY_TELEMETRY_ENABLED, "true").trim(), false);
        writeSettingIfMissing(KEY_AUTO_PAUSE, properties.getProperty(KEY_AUTO_PAUSE,
                String.valueOf(DEFAULT_AUTO_PAUSE_POLLING_WHEN_MARKET_CLOSED)).trim(), false);
        writeSettingIfMissing(KEY_EXTENDED_HOURS, properties.getProperty(KEY_EXTENDED_HOURS,
                String.valueOf(DEFAULT_EXTENDED_HOURS_TRADING_ENABLED)).trim(), false);
        writeSettingIfMissing(KEY_BROKER, properties.getProperty(KEY_BROKER, BrokerType.ALPACA.name()).trim(), false);
        writeSettingIfMissing(KEY_APPLICATION_MODE, properties.getProperty(KEY_APPLICATION_MODE, ApplicationMode.PAPER.name()).trim(), false);
        writeSettingIfMissing(KEY_ENDPOINT, properties.getProperty(KEY_ENDPOINT, AppMetadata.analyticsEndpointDefault()).trim(), false);
    }

    private void migrateLegacyCredentials(Path credentialsFile, ApplicationMode mode) {
        if (!Files.exists(credentialsFile)) {
            return;
        }
        String email = readSetting(KEY_USER_EMAIL, false).trim();
        if (email.isBlank()) {
            return;
        }
        UserIdentityService identityService = new UserIdentityService();
        String passphrase = identityService.generateUserId(email).substring(0, 16);
        CredentialManager manager = new CredentialManager();
        manager.load(credentialsFile, passphrase).ifPresent(creds -> {
            try {
                String suffix = (mode == null ? ApplicationMode.PAPER : mode).name().toLowerCase();
                String keyName = "credentials." + suffix + ".apiKey";
                String secretName = "credentials." + suffix + ".apiSecret";
                String key = creds[0] == null ? "" : creds[0].trim();
                String secret = creds[1] == null ? "" : creds[1];
                if (!key.isBlank() && !secret.isBlank()) {
                    writeSettingIfMissing(keyName, key, true);
                    writeSettingIfMissing(secretName, secret, true);
                }
            } catch (SQLException ignored) {
                // Ignore one-off migration errors to keep startup resilient.
            }
        });
    }

    private void writeSettingIfMissing(String key, String value, boolean encrypted) throws SQLException {
        if (settingExists(key)) {
            return;
        }
        writeSetting(key, value, encrypted);
    }

    private boolean settingExists(String key) throws SQLException {
        String sql = "SELECT 1 FROM app_settings WHERE key = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public record AppSettings(
            String userEmail,
            boolean telemetryEnabled,
            boolean autoPausePollingWhenMarketClosed,
            boolean extendedHoursTradingEnabled,
            BrokerType brokerType,
            ApplicationMode applicationMode,
            boolean allowDuplicateSymbolStrategies,
            boolean emailOnBuyExpected,
            boolean emailOnSellExecuted,
            int defaultStrategyPollingSeconds,
            boolean defaultRepeatCycleAfterProfitExitEnabled,
            boolean defaultResubmitOnExpiryEnabled
    ) {
        public AppSettings {
            defaultStrategyPollingSeconds = Math.max(1, defaultStrategyPollingSeconds);
        }

        public AppSettings(
                String userEmail,
                boolean telemetryEnabled,
                boolean autoPausePollingWhenMarketClosed,
                boolean extendedHoursTradingEnabled,
                BrokerType brokerType,
                ApplicationMode applicationMode,
                boolean allowDuplicateSymbolStrategies,
                boolean emailOnBuyExpected,
                boolean emailOnSellExecuted
        ) {
            this(
                    userEmail,
                    telemetryEnabled,
                    autoPausePollingWhenMarketClosed,
                    extendedHoursTradingEnabled,
                    brokerType,
                    applicationMode,
                    allowDuplicateSymbolStrategies,
                    emailOnBuyExpected,
                    emailOnSellExecuted,
                    DEFAULT_STRATEGY_POLLING_SECONDS,
                    DEFAULT_REPEAT_CYCLE_AFTER_PROFIT_EXIT_ENABLED,
                    DEFAULT_RESUBMIT_ON_EXPIRY_ENABLED
            );
        }

        public AppSettings(
                String userEmail,
                boolean telemetryEnabled,
                boolean autoPausePollingWhenMarketClosed,
                boolean extendedHoursTradingEnabled,
                BrokerType brokerType,
                ApplicationMode applicationMode,
                boolean allowDuplicateSymbolStrategies
        ) {
            this(
                    userEmail,
                    telemetryEnabled,
                    autoPausePollingWhenMarketClosed,
                    extendedHoursTradingEnabled,
                    brokerType,
                    applicationMode,
                    allowDuplicateSymbolStrategies,
                    DEFAULT_EMAIL_ON_BUY_EXPECTED,
                    DEFAULT_EMAIL_ON_SELL_EXECUTED,
                    DEFAULT_STRATEGY_POLLING_SECONDS,
                    DEFAULT_REPEAT_CYCLE_AFTER_PROFIT_EXIT_ENABLED,
                    DEFAULT_RESUBMIT_ON_EXPIRY_ENABLED
            );
        }
    }
}
