package com.neuralarc.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface AlpacaClient {
    AlpacaOrderData submitLimitBuyOrder(String symbol, int quantity, BigDecimal limitPrice, String clientOrderId);

    AlpacaOrderData submitLimitSellOrder(String symbol, int quantity, BigDecimal limitPrice, String clientOrderId);

    Optional<AlpacaOrderData> getOrder(String orderId);

    List<AlpacaOrderData> getOpenOrders(String symbol);

    Optional<AlpacaPositionData> getPosition(String symbol);

    BigDecimal getLatestPrice(String symbol);
}

