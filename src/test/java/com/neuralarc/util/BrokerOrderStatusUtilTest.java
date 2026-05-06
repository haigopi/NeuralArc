package com.neuralarc.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrokerOrderStatusUtilTest {
    @Test
    void normalizeStandardizesStatusTokens() {
        assertEquals("accepted_for_bidding", BrokerOrderStatusUtil.normalize(" Accepted-For Bidding "));
    }

    @Test
    void waitingForFillRecognizesAlpacaOpenStates() {
        assertTrue(BrokerOrderStatusUtil.isWaitingForFill("new"));
        assertTrue(BrokerOrderStatusUtil.isWaitingForFill("accepted_for_bidding"));
        assertTrue(BrokerOrderStatusUtil.isWaitingForFill("partially_filled"));
        assertFalse(BrokerOrderStatusUtil.isWaitingForFill("filled"));
    }

    @Test
    void displayLabelFormatsBrokerStatus() {
        assertEquals("Pending Replace", BrokerOrderStatusUtil.displayLabel("pending_replace"));
        assertEquals("Failed", BrokerOrderStatusUtil.displayLabel("failed_transport"));
    }
}

