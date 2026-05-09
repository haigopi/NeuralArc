package com.neuralarc.util;

import com.neuralarc.security.EncryptionUtil;

import java.nio.charset.StandardCharsets;

public final class SecretEncryptCli {
    private SecretEncryptCli() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 1 || (args.length == 1 && !"--mailjet".equals(args[0]) && !"--spaces".equals(args[0]))) {
            System.err.println("Usage: echo -n <plaintext> | SecretEncryptCli [--mailjet|--spaces]");
            System.exit(2);
        }
        String passphrase = args.length == 1 && "--spaces".equals(args[0])
                ? EncryptedSpacesSecrets.configuredPassphrase()
                : EncryptedMailjetSecrets.configuredPassphrase();
        String plaintext = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
        if (passphrase.isBlank() || plaintext.isBlank()) {
            System.err.println("Secret passphrase and stdin plaintext are required.");
            System.exit(2);
        }
        System.out.println(new EncryptionUtil().encrypt(plaintext, passphrase));
    }
}
