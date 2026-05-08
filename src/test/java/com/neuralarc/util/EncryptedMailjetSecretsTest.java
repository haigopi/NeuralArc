package com.neuralarc.util;

import com.neuralarc.security.EncryptionUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EncryptedMailjetSecretsTest {
    private static final String PASSPHRASE_PROPERTY = "neuralarc.mailjet.passphrase";

    @AfterEach
    void clearPassphrase() {
        System.clearProperty(PASSPHRASE_PROPERTY);
    }

    @Test
    void decryptsMailjetSecretsFromEncryptedProperties() {
        String passphrase = "test-passphrase";
        EncryptionUtil encryptionUtil = new EncryptionUtil();
        Properties properties = new Properties();
        properties.setProperty("mailjet.api.key.encrypted", encryptionUtil.encrypt("api-key", passphrase));
        properties.setProperty("mailjet.api.secret.encrypted", encryptionUtil.encrypt("api-secret", passphrase));
        System.setProperty(PASSPHRASE_PROPERTY, passphrase);

        EncryptedMailjetSecrets secrets = EncryptedMailjetSecrets.from(properties);

        assertEquals("api-key", secrets.apiKey());
        assertEquals("api-secret", secrets.apiSecret());
    }

    @Test
    void returnsBlankSecretsWhenEncryptedValuesAreMissing() {
        Properties properties = new Properties();

        EncryptedMailjetSecrets secrets = EncryptedMailjetSecrets.from(properties);

        assertEquals("", secrets.apiKey());
        assertEquals("", secrets.apiSecret());
    }
}
