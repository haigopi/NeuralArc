package com.neuralarc.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PositionTest {
    @Test
    void weightedAverageCostCalculationWorks() {
        Position p = new Position("NEO");
        p.applyBuy(10, new BigDecimal("8.00"));
        p.applyBuy(5, new BigDecimal("7.00"));
        assertEquals(new BigDecimal("7.67"), p.getAverageCost());
        assertEquals(15, p.getTotalShares());
    }

    @Test
    void realizedPnlIsRoundedToTwoDecimals() {
        Position p = new Position("NEO");
        p.applyBuy(3, new BigDecimal("10.015"));
        p.applySell(2, new BigDecimal("10.994"));
        assertEquals(new BigDecimal("1.94"), p.getRealizedPnl());
    }
}
