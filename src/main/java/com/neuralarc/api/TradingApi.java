package com.neuralarc.api;

import com.neuralarc.model.OrderResult;
import com.neuralarc.model.Position;

import java.math.BigDecimal;

public interface TradingApi {
    void authenticate(String apiKey, String apiSecret);

    boolean testConnection();

    BigDecimal getLatestPrice(String symbol);

    OrderResult placeBuyOrder(String symbol, int qty, BigDecimal limitPrice);

    OrderResult placeSellOrder(String symbol, int qty, BigDecimal limitPrice);

    boolean cancelOpenOrdersForSymbol(String symbol);

    Position getPosition(String symbol);
}
