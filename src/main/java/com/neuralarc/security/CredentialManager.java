package com.neuralarc.security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

public class CredentialManager {
    private final EncryptionUtil encryptionUtil = new EncryptionUtil();

    public void save(String apiKey, char[] secret, Path file, String passphrase) {
        Properties p = new Properties();
        p.setProperty("apiKey", encryptionUtil.encrypt(apiKey, passphrase));
        p.setProperty("apiSecret", encryptionUtil.encrypt(new String(secret), passphrase));
        try {
            Files.createDirectories(file.getParent());
            try (var out = Files.newOutputStream(file)) {
                p.store(out, "Encrypted credentials");
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } finally {
            java.util.Arrays.fill(secret, '\0');
        }
    }

    public Optional<String[]> load(Path file, String passphrase) {
        if (!Files.exists(file)) return Optional.empty();
        Properties p = new Properties();
        try (var in = Files.newInputStream(file)) {
            p.load(in);
            String key = encryptionUtil.decrypt(p.getProperty("apiKey"), passphrase);
            String secret = encryptionUtil.decrypt(p.getProperty("apiSecret"), passphrase);
            return Optional.of(new String[]{key, secret});
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}
