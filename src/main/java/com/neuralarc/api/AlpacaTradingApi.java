package com.neuralarc.api;

import com.neuralarc.model.OrderResult;
import com.neuralarc.model.Position;

import java.math.BigDecimal;

public class AlpacaTradingApi implements TradingApi {
    private final Position emptyPosition = new Position("UNKNOWN");

    @Override
    public void authenticate(String apiKey, String apiSecret) {
        // TODO Integrate with Alpaca REST API in a production deployment.
    }

    @Override
    public boolean testConnection() {
        return false;
    }

    @Override
    public BigDecimal getLatestPrice(String symbol) {
        return BigDecimal.ZERO;
    }

    @Override
    public OrderResult placeBuyOrder(String symbol, int qty) {
        return OrderResult.fail(symbol, qty, "Alpaca API stub not implemented yet");
    }

    @Override
    public OrderResult placeSellOrder(String symbol, int qty) {
        return OrderResult.fail(symbol, qty, "Alpaca API stub not implemented yet");
    }

    @Override
    public Position getPosition(String symbol) {
        return emptyPosition;
    }
}
