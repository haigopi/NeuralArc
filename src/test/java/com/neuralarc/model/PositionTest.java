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
        assertEquals(new BigDecimal("7.666667"), p.getAverageCost());
        assertEquals(15, p.getTotalShares());
    }
}
