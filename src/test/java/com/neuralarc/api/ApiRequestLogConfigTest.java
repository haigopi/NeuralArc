package com.neuralarc.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiRequestLogConfigTest {

    @AfterEach
    void reset() {
        ApiRequestLogConfig.setVerboseJsonLogging(false);
    }

    @Test
    void defaultsToOff() {
        assertFalse(ApiRequestLogConfig.isVerboseJsonLogging());
    }

    @Test
    void reflectsToggle() {
        ApiRequestLogConfig.setVerboseJsonLogging(true);
        assertTrue(ApiRequestLogConfig.isVerboseJsonLogging());
        ApiRequestLogConfig.setVerboseJsonLogging(false);
        assertFalse(ApiRequestLogConfig.isVerboseJsonLogging());
    }
}
