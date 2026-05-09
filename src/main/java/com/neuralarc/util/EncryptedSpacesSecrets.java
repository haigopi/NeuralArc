package com.neuralarc.util;

import com.neuralarc.security.EncryptionUtil;

import java.util.Properties;

public final class EncryptedSpacesSecrets {
    private static final String ACCESS_KEY_PROPERTY = "logs.upload.spaces.accessKey.encrypted";
    private static final String SECRET_KEY_PROPERTY = "logs.upload.spaces.secretKey.encrypted";
    private static final String ACCESS_KEY_ENV = "NEURALARC_SPACES_ACCESS_KEY_ENCRYPTED";
    private static final String SECRET_KEY_ENV = "NEURALARC_SPACES_SECRET_KEY_ENCRYPTED";
    private static final String PASSPHRASE_ENV = "NEURALARC_SPACES_PASSPHRASE";
    private static final String PASSPHRASE_PROPERTY = "neuralarc.spaces.passphrase";

    private final String accessKey;
    private final String secretKey;

    private EncryptedSpacesSecrets(String accessKey, String secretKey) {
        this.accessKey = accessKey == null ? "" : accessKey.trim();
        this.secretKey = secretKey == null ? "" : secretKey.trim();
    }

    public static EncryptedSpacesSecrets from(Properties properties) {
        String passphrase = configuredPassphrase();
        return new EncryptedSpacesSecrets(
                decrypt(configuredOrEnv(properties, ACCESS_KEY_PROPERTY, ACCESS_KEY_ENV), passphrase),
                decrypt(configuredOrEnv(properties, SECRET_KEY_PROPERTY, SECRET_KEY_ENV), passphrase)
        );
    }

    public String accessKey() {
        return accessKey;
    }

    public String secretKey() {
        return secretKey;
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
                + "-Spaces"
                + "-LogUpload"
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
