package com.neuralarc.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.neuralarc.model.MarketBar;
import com.neuralarc.model.TimeInForce;

public interface AlpacaClient {
    AlpacaOrderData submitLimitBuyOrder(String symbol, int quantity, BigDecimal limitPrice, String clientOrderId);

    default AlpacaOrderData submitLimitBuyOrder(
            String symbol,
            int quantity,
            BigDecimal limitPrice,
            String clientOrderId,
            TimeInForce timeInForce
    ) {
        return submitLimitBuyOrder(symbol, quantity, limitPrice, clientOrderId);
    }

    default AlpacaOrderData submitMarketBuyOrder(String symbol, int quantity, String clientOrderId) {
        throw new UnsupportedOperationException("Market buy orders are not supported by this broker client");
    }

    AlpacaOrderData submitLimitSellOrder(String symbol, int quantity, BigDecimal limitPrice, String clientOrderId);

    default AlpacaOrderData submitLimitSellOrder(
            String symbol,
            int quantity,
            BigDecimal limitPrice,
            String clientOrderId,
            TimeInForce timeInForce
    ) {
        return submitLimitSellOrder(symbol, quantity, limitPrice, clientOrderId);
    }

    AlpacaOrderData submitMarketSellOrder(String symbol, int quantity, String clientOrderId);

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

    default Optional<BigDecimal> getAvailableFunds() {
        return Optional.empty();
    }

    BigDecimal getLatestPrice(String symbol);

    default List<MarketBar> getDailyBars(String symbol, LocalDate startDate, LocalDate endDate) {
        return List.of();
    }

    /**
     * Batch-fetch today's session bar (open/high/low/close) for multiple symbols in a single API
     * call, keyed by uppercased symbol. Powers the grid's Open / Today's Low / Today's High
     * columns without one request per row. Default is empty so existing fakes stay compatible;
     * an empty/missing entry must render as "-" rather than a fabricated price.
     */
    default Map<String, MarketBar> getDailySnapshots(List<String> symbols) {
        return Map.of();
    }

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
