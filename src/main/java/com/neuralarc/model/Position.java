package com.neuralarc.model;

import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class Position {
    private final String symbol;
    private int totalShares;
    private BigDecimal averageCost = Monetary.zero();
    private BigDecimal realizedPnl = Monetary.zero();
    private BigDecimal lastPrice = Monetary.zero();

    public Position(String symbol) {
        this.symbol = symbol;
    }

    public synchronized void applyBuy(int qty, BigDecimal price) {
        BigDecimal normalizedPrice = Monetary.round(price);
        BigDecimal oldCost = averageCost.multiply(BigDecimal.valueOf(totalShares));
        BigDecimal newCost = normalizedPrice.multiply(BigDecimal.valueOf(qty));
        totalShares += qty;
        if (totalShares > 0) {
            averageCost = Monetary.round(oldCost.add(newCost).divide(BigDecimal.valueOf(totalShares), 6, RoundingMode.HALF_UP));
        }
        lastPrice = normalizedPrice;
    }

    public synchronized void applySell(int qty, BigDecimal price) {
        BigDecimal normalizedPrice = Monetary.round(price);
        if (qty > totalShares) {
            qty = totalShares;
        }
        BigDecimal pnlPerShare = normalizedPrice.subtract(averageCost);
        realizedPnl = Monetary.round(realizedPnl.add(pnlPerShare.multiply(BigDecimal.valueOf(qty))));
        totalShares -= qty;
        lastPrice = normalizedPrice;
        if (totalShares == 0) {
            averageCost = Monetary.zero();
        }
    }

    public synchronized BigDecimal marketValue() {
        return Monetary.round(lastPrice.multiply(BigDecimal.valueOf(totalShares)));
    }

    public synchronized BigDecimal unrealizedPnl() {
        return Monetary.round(lastPrice.subtract(averageCost).multiply(BigDecimal.valueOf(totalShares)));
    }

    public synchronized BigDecimal totalInvested() {
        return Monetary.round(averageCost.multiply(BigDecimal.valueOf(totalShares)));
    }

    public synchronized int getTotalShares() { return totalShares; }
    public synchronized BigDecimal getAverageCost() { return Monetary.round(averageCost); }
    public synchronized BigDecimal getRealizedPnl() { return Monetary.round(realizedPnl); }
    public synchronized BigDecimal getLastPrice() { return Monetary.round(lastPrice); }
    public String getSymbol() { return symbol; }

    public synchronized void setLastPrice(BigDecimal price) {
        this.lastPrice = Monetary.round(price);
    }
}
