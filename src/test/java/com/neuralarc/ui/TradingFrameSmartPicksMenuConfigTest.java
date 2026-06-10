package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TradingFrameSmartPicksMenuConfigTest {

    @Test
    void smartPicksMenuKeepsExpectedEntriesAndOrder() {
        List<String> labels = TradingFrame.smartPicksMenuLabels();

        assertEquals(List.of(
                "High Volatility Movers",
                "Diversified Leaders (Top 20)"
        ), labels);
    }
}

