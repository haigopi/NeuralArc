package com.neuralarc.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class UserIdentityService {
    public String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    public String generateUserId(String email) {
        String normalized = normalizeEmail(email);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public String maskEmail(String email) {
        String normalized = normalizeEmail(email);
        int at = normalized.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return normalized.charAt(0) + "***" + normalized.substring(at);
    }

    public String shortUserId(String userId) {
        return userId.length() <= 12 ? userId : userId.substring(0, 12);
    }
}
