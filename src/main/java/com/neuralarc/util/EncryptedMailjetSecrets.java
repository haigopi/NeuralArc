package com.neuralarc.util;

import com.neuralarc.security.EncryptionUtil;

import java.util.Properties;

public final class EncryptedMailjetSecrets {
    private static final String KEY_PROPERTY = "mailjet.api.key.encrypted";
    private static final String SECRET_PROPERTY = "mailjet.api.secret.encrypted";
    private static final String KEY_ENV = "MAILJET_API_KEY_ENCRYPTED";
    private static final String SECRET_ENV = "MAILJET_API_SECRET_ENCRYPTED";
    private static final String PASSPHRASE_ENV = "NEURALARC_MAILJET_PASSPHRASE";
    private static final String PASSPHRASE_PROPERTY = "neuralarc.mailjet.passphrase";

    private final String apiKey;
    private final String apiSecret;

    private EncryptedMailjetSecrets(String apiKey, String apiSecret) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.apiSecret = apiSecret == null ? "" : apiSecret.trim();
    }

    public static EncryptedMailjetSecrets from(Properties properties) {
        String passphrase = configuredPassphrase();
        return new EncryptedMailjetSecrets(
                decrypt(configuredOrEnv(properties, KEY_PROPERTY, KEY_ENV), passphrase),
                decrypt(configuredOrEnv(properties, SECRET_PROPERTY, SECRET_ENV), passphrase)
        );
    }

    public String apiKey() {
        return apiKey;
    }

    public String apiSecret() {
        return apiSecret;
    }

    public static String passphraseConfigurationHint() {
        return PASSPHRASE_ENV + " or -D" + PASSPHRASE_PROPERTY + " when overriding the bundled app decryptor";
    }

    static String configuredPassphrase() {
        String systemProperty = System.getProperty(PASSPHRASE_PROPERTY, "").trim();
        if (!systemProperty.isBlank()) {
            return systemProperty;
        }
        String environmentValue = System.getenv().getOrDefault(PASSPHRASE_ENV, "").trim();
        if (!environmentValue.isBlank()) {
            return environmentValue;
        }
        return bundledPassphrase();
    }

    private static String bundledPassphrase() {
        return "NeuralArc"
                + "-Mailjet"
                + "-Support"
                + "-2026";
    }

    private static String configuredOrEnv(Properties properties, String propertyKey, String envKey) {
        String configured = properties == null ? "" : properties.getProperty(propertyKey, "").trim();
        if (!configured.isBlank()) {
            return configured;
        }
        return System.getenv().getOrDefault(envKey, "").trim();
    }

    private static String decrypt(String encryptedValue, String passphrase) {
        if (encryptedValue == null || encryptedValue.isBlank() || passphrase == null || passphrase.isBlank()) {
            return "";
        }
        return new EncryptionUtil().decrypt(encryptedValue.trim(), passphrase);
    }
}
