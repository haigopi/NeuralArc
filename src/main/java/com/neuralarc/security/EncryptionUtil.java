package com.neuralarc.security;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

public class EncryptionUtil {
    private static final int SALT_LEN = 16;
    private static final int IV_LEN = 12;

    public String encrypt(String plaintext, String password) {
        try {
            byte[] salt = random(SALT_LEN);
            byte[] iv = random(IV_LEN);
            SecretKey key = derive(password, salt);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] enc = c.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer bb = ByteBuffer.allocate(salt.length + iv.length + enc.length);
            bb.put(salt).put(iv).put(enc);
            return Base64.getEncoder().encodeToString(bb.array());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public String decrypt(String ciphertext, String password) {
        try {
            byte[] bytes = Base64.getDecoder().decode(ciphertext);
            ByteBuffer bb = ByteBuffer.wrap(bytes);
            byte[] salt = new byte[SALT_LEN];
            byte[] iv = new byte[IV_LEN];
            bb.get(salt);
            bb.get(iv);
            byte[] enc = new byte[bb.remaining()];
            bb.get(enc);
            SecretKey key = derive(password, salt);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            return new String(c.doFinal(enc), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private SecretKey derive(String password, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 65536, 256);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] key = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(key, "AES");
    }

    private byte[] random(int len) {
        byte[] arr = new byte[len];
        new SecureRandom().nextBytes(arr);
        return arr;
    }
}
