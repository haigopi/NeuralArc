package com.neuralarc.api;

import com.neuralarc.model.OrderResult;
import com.neuralarc.model.Position;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MockTradingApi implements TradingApi {
    private final Map<String, Position> positions = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> lastPrice = new ConcurrentHashMap<>();

    @Override
    public void authenticate(String apiKey, String apiSecret) {
        // Mock accepts all credentials. Real integration validates with broker.
    }

    @Override
    public boolean testConnection() {
        return true;
    }

    @Override
    public BigDecimal getLatestPrice(String symbol) {
        BigDecimal current = lastPrice.getOrDefault(symbol, new BigDecimal("8.00"));
        // Simple deterministic oscillation for demo.
        BigDecimal next = current.add(new BigDecimal("0.25"));
        if (next.compareTo(new BigDecimal("10.50")) > 0) {
            next = new BigDecimal("5.75");
        }
        next = next.setScale(2, RoundingMode.HALF_UP);
        lastPrice.put(symbol, next);
        return next;
    }

    @Override
    public OrderResult placeBuyOrder(String symbol, int qty) {
        BigDecimal price = lastPrice.getOrDefault(symbol, new BigDecimal("8.00"));
        Position p = positions.computeIfAbsent(symbol, Position::new);
        p.applyBuy(qty, price);
        return OrderResult.ok(UUID.randomUUID().toString(), symbol, qty, price);
    }

    @Override
    public OrderResult placeSellOrder(String symbol, int qty) {
        BigDecimal price = lastPrice.getOrDefault(symbol, new BigDecimal("8.00"));
        Position p = positions.computeIfAbsent(symbol, Position::new);
        p.applySell(qty, price);
        return OrderResult.ok(UUID.randomUUID().toString(), symbol, qty, price);
    }

    @Override
    public boolean cancelOpenOrdersForSymbol(String symbol) {
        return true;
    }

    @Override
    public Position getPosition(String symbol) {
        return positions.computeIfAbsent(symbol, Position::new);
    }
}
