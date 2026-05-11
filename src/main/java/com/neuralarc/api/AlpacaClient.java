package com.neuralarc.api;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface AlpacaClient {
    AlpacaOrderData submitLimitBuyOrder(String symbol, int quantity, BigDecimal limitPrice, String clientOrderId);

    AlpacaOrderData submitLimitSellOrder(String symbol, int quantity, BigDecimal limitPrice, String clientOrderId);

    AlpacaOrderData submitTrailingStopSellOrder(
            String symbol,
            int quantity,
            BigDecimal trailPercent,
            BigDecimal trailPrice,
            String clientOrderId
    );

    Optional<AlpacaOrderData> getOrder(String orderId);

    List<AlpacaOrderData> getOpenOrders(String symbol);

    List<AlpacaOrderData> getOpenOrders();

    boolean cancelOrder(String orderId);

    Optional<AlpacaPositionData> getPosition(String symbol);

    List<AlpacaPositionData> getPositions();

    BigDecimal getLatestPrice(String symbol);

    /**
     * Batch-fetch the latest trade price for multiple symbols in a single API call.
     * The default implementation falls back to calling {@link #getLatestPrice} for each
     * symbol individually; concrete broker clients should override this with a real
     * batch endpoint for efficiency.
     */
    default Map<String, BigDecimal> getLatestPrices(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return Map.of();
        }
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (String symbol : symbols) {
            if (symbol != null && !symbol.isBlank()) {
                String upper = symbol.trim().toUpperCase();
                result.put(upper, getLatestPrice(upper));
            }
        }
        return result;
    }

    /**
     * Returns whether the symbol is eligible for Alpaca overnight (24x5) session trading.
     * Default is false so existing test fakes and alternative implementations remain compatible.
     */
    default boolean supportsOvernightSession(String symbol) {
        return false;
    }
}
