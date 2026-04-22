package com.neuralarc.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class Position {
    private final String symbol;
    private int totalShares;
    private BigDecimal averageCost = BigDecimal.ZERO;
    private BigDecimal realizedPnl = BigDecimal.ZERO;
    private BigDecimal lastPrice = BigDecimal.ZERO;

    public Position(String symbol) {
        this.symbol = symbol;
    }

    public synchronized void applyBuy(int qty, BigDecimal price) {
        BigDecimal oldCost = averageCost.multiply(BigDecimal.valueOf(totalShares));
        BigDecimal newCost = price.multiply(BigDecimal.valueOf(qty));
        totalShares += qty;
        if (totalShares > 0) {
            averageCost = oldCost.add(newCost).divide(BigDecimal.valueOf(totalShares), 6, RoundingMode.HALF_UP);
        }
        lastPrice = price;
    }

    public synchronized void applySell(int qty, BigDecimal price) {
        if (qty > totalShares) {
            qty = totalShares;
        }
        BigDecimal pnlPerShare = price.subtract(averageCost);
        realizedPnl = realizedPnl.add(pnlPerShare.multiply(BigDecimal.valueOf(qty)));
        totalShares -= qty;
        lastPrice = price;
        if (totalShares == 0) {
            averageCost = BigDecimal.ZERO;
        }
    }

    public synchronized BigDecimal marketValue() {
        return lastPrice.multiply(BigDecimal.valueOf(totalShares));
    }

    public synchronized BigDecimal unrealizedPnl() {
        return lastPrice.subtract(averageCost).multiply(BigDecimal.valueOf(totalShares));
    }

    public synchronized BigDecimal totalInvested() {
        return averageCost.multiply(BigDecimal.valueOf(totalShares));
    }

    public synchronized int getTotalShares() { return totalShares; }
    public synchronized BigDecimal getAverageCost() { return averageCost; }
    public synchronized BigDecimal getRealizedPnl() { return realizedPnl; }
    public synchronized BigDecimal getLastPrice() { return lastPrice; }
    public String getSymbol() { return symbol; }

    public synchronized void setLastPrice(BigDecimal price) { this.lastPrice = price; }
}
