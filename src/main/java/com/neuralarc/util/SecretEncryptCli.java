package com.neuralarc.util;

import com.neuralarc.security.EncryptionUtil;

import java.nio.charset.StandardCharsets;

public final class SecretEncryptCli {
    private SecretEncryptCli() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 0) {
            System.err.println("Usage: echo -n <plaintext> | NEURALARC_MAILJET_PASSPHRASE=... SecretEncryptCli");
            System.exit(2);
        }
        String passphrase = EncryptedMailjetSecrets.configuredPassphrase();
        String plaintext = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
        if (passphrase.isBlank() || plaintext.isBlank()) {
            System.err.println("Mailjet passphrase and stdin plaintext are required.");
            System.exit(2);
        }
        System.out.println(new EncryptionUtil().encrypt(plaintext, passphrase));
    }
}
