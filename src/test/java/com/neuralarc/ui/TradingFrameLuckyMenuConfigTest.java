package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TradingFrameLuckyMenuConfigTest {

    @Test
    void luckyStrategiesMenuKeepsExpectedEntriesAndOrder() {
        List<String> labels = TradingFrame.luckyStrategyMenuLabels();

        assertEquals(List.of(
                "Volatile Strategy",
                "Top 20 Diversified Stocks"
        ), labels);
    }
}

