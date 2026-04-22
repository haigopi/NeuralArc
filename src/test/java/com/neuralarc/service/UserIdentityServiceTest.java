package com.neuralarc.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserIdentityServiceTest {
    @Test
    void stableUserIdForNormalizedEmail() {
        UserIdentityService s = new UserIdentityService();
        String a = s.generateUserId(" Test@Example.Com ");
        String b = s.generateUserId("test@example.com");
        assertEquals(a, b);
        assertEquals(64, a.length());
    }
}
