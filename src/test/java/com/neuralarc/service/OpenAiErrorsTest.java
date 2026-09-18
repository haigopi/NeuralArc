package com.neuralarc.service;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiErrorsTest {
    private static final String QUOTA = "{\"error\":{\"message\":\"You exceeded your current quota, please check your plan and billing details.\","
            + "\"type\":\"insufficient_quota\",\"code\":\"insufficient_quota\"}}";
    private static final String RATE_LIMIT = "{\"error\":{\"message\":\"Rate limit reached for gpt-5 on requests per min.\","
            + "\"type\":\"requests\",\"code\":\"rate_limit_exceeded\"}}";

    @Test
    void aRateLimitIsRetriedButAnExhaustedQuotaIsNot() {
        assertTrue(OpenAiErrors.retryable(429, RATE_LIMIT));
        assertFalse(OpenAiErrors.retryable(429, QUOTA), "no amount of retrying adds credit");
        assertTrue(OpenAiErrors.retryable(503, ""));
        assertFalse(OpenAiErrors.retryable(401, "{\"error\":{\"code\":\"invalid_api_key\"}}"));
    }

    @Test
    void theFailureNamesOpenAisOwnReason() {
        String quota = OpenAiErrors.describe(429, QUOTA);
        assertTrue(quota.startsWith("OpenAI request failed with HTTP 429 (insufficient_quota: You exceeded"), quota);
        assertTrue(quota.contains("no remaining credit"), quota);

        assertTrue(OpenAiErrors.describe(429, RATE_LIMIT).contains("rate_limit_exceeded: Rate limit reached"));
        assertEquals("OpenAI request failed with HTTP 500", OpenAiErrors.describe(500, "not json"));
    }

    @Test
    void retriesWaitForTheServersRetryAfterWhenGivenAndAreCapped() {
        assertEquals(3_000L, OpenAiErrors.delayMillis(1, Optional.of("3")));
        assertEquals(2_000L, OpenAiErrors.delayMillis(1, Optional.empty()));
        assertEquals(4_000L, OpenAiErrors.delayMillis(2, Optional.empty()));
        assertEquals(20_000L, OpenAiErrors.delayMillis(1, Optional.of("600")), "never stall a scan for minutes");
    }
}
