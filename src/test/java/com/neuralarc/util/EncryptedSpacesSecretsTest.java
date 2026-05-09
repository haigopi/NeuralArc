package com.neuralarc.util;

import com.neuralarc.security.EncryptionUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EncryptedSpacesSecretsTest {
    private static final String PASSPHRASE_PROPERTY = "neuralarc.spaces.passphrase";

    @AfterEach
    void clearPassphrase() {
        System.clearProperty(PASSPHRASE_PROPERTY);
    }

    @Test
    void decryptsSpacesSecretsFromEncryptedProperties() {
        String passphrase = "test-spaces-passphrase";
        EncryptionUtil encryptionUtil = new EncryptionUtil();
        Properties properties = new Properties();
        properties.setProperty("logs.upload.spaces.accessKey.encrypted", encryptionUtil.encrypt("spaces-key", passphrase));
        properties.setProperty("logs.upload.spaces.secretKey.encrypted", encryptionUtil.encrypt("spaces-secret", passphrase));
        System.setProperty(PASSPHRASE_PROPERTY, passphrase);

        EncryptedSpacesSecrets secrets = EncryptedSpacesSecrets.from(properties);

        assertEquals("spaces-key", secrets.accessKey());
        assertEquals("spaces-secret", secrets.secretKey());
    }

    @Test
    void returnsBlankSecretsWhenEncryptedValuesAreMissing() {
        Properties properties = new Properties();

        EncryptedSpacesSecrets secrets = EncryptedSpacesSecrets.from(properties);

        assertEquals("", secrets.accessKey());
        assertEquals("", secrets.secretKey());
    }
}
