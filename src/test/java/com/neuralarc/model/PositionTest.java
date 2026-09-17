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

    @Test
    void aShortPositionCarriesItsCostBasis() {
        // Shares owed, not owned. Without a cost basis the unrealized P&L was the whole market value.
        Position p = new Position("MRVL");
        p.applyBuy(-1, new BigDecimal("201.2005"));

        assertEquals(-1, p.getTotalShares());
        assertEquals(new BigDecimal("201.20"), p.getAverageCost());
    }

    @Test
    void aShortIsValuedTheWayTheBrokerValuesIt() {
        // MRVL: short 1 at 201.2005, marked at 229.56. Alpaca reports -229.56 / -201.20 / -28.36.
        Position p = new Position("MRVL");
        p.applyBuy(-1, new BigDecimal("201.2005"));
        p.setLastPrice(new BigDecimal("229.56"));

        assertEquals(new BigDecimal("-229.56"), p.marketValue());
        assertEquals(new BigDecimal("-201.20"), p.totalInvested());
        assertEquals(new BigDecimal("-28.36"), p.unrealizedPnl(), "a short loses as the price rises");
    }

    @Test
    void aShortThatFallsIsInProfit() {
        Position p = new Position("TTAN");
        p.applyBuy(-1, new BigDecimal("84.96"));
        p.setLastPrice(new BigDecimal("59.06"));

        assertEquals(new BigDecimal("25.90"), p.unrealizedPnl());
    }

    @Test
    void sellingNeverPushesALongPastFlatIntoAShort() {
        // A short's basis comes from the broker; inventing one by over-selling would book P&L
        // against a zero cost.
        Position p = new Position("NEO");
        p.applyBuy(2, new BigDecimal("10.00"));
        p.applySell(5, new BigDecimal("12.00"));

        assertEquals(0, p.getTotalShares());
        assertEquals(new BigDecimal("4.00"), p.getRealizedPnl(), "only the 2 shares held were sold");
    }

    @Test
    void sellingAgainstAShortBooksNothing() {
        Position p = new Position("MRVL");
        p.applyBuy(-1, new BigDecimal("201.20"));
        p.applySell(3, new BigDecimal("229.56"));

        assertEquals(-1, p.getTotalShares(), "the short is untouched");
        assertEquals(new BigDecimal("0.00"), p.getRealizedPnl());
    }
}
