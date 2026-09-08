package com.neuralarc.api;

import com.neuralarc.model.OrderResult;
import com.neuralarc.model.Position;

import java.math.BigDecimal;

public interface TradingApi {
    void authenticate(String apiKey, String apiSecret);

    boolean testConnection();

    /**
     * Same probe as {@link #testConnection()}, but reports why it failed so callers can tell rejected
     * credentials apart from a broker that was momentarily unreachable.
     */
    default ConnectionCheck checkConnection() {
        return testConnection() ? ConnectionCheck.accepted() : ConnectionCheck.unreachable("Broker connection test failed.");
    }

    BigDecimal getLatestPrice(String symbol);

    OrderResult placeBuyOrder(String symbol, int qty, BigDecimal limitPrice);

    OrderResult placeSellOrder(String symbol, int qty, BigDecimal limitPrice);

    boolean cancelOpenOrdersForSymbol(String symbol);

    Position getPosition(String symbol);
}
