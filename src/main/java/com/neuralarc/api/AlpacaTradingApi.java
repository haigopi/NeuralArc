package com.neuralarc.api;

import com.neuralarc.model.OrderResult;
import com.neuralarc.model.Position;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class AlpacaTradingApi implements TradingApi {
    private final Position emptyPosition = new Position("UNKNOWN");
    private final String baseUrl;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private String apiKey;
    private String apiSecret;

    public AlpacaTradingApi() {
        this("https://paper-api.alpaca.markets/v2");
    }

    public AlpacaTradingApi(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override
    public void authenticate(String apiKey, String apiSecret) {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
    }

    @Override
    public boolean testConnection() {
        if (apiKey == null || apiKey.isBlank() || apiSecret == null || apiSecret.isBlank()) {
            return false;
        }
        String endpoint = baseUrl.endsWith("/") ? baseUrl + "account" : baseUrl + "/account";
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(10))
                .header("APCA-API-KEY-ID", apiKey)
                .header("APCA-API-SECRET-KEY", apiSecret)
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public BigDecimal getLatestPrice(String symbol) {
        return BigDecimal.ZERO;
    }

    @Override
    public OrderResult placeBuyOrder(String symbol, int qty) {
        return submitOrder(symbol, qty, "buy");
    }

    @Override
    public OrderResult placeSellOrder(String symbol, int qty) {
        return submitOrder(symbol, qty, "sell");
    }

    @Override
    public Position getPosition(String symbol) {
        return emptyPosition;
    }

    private OrderResult submitOrder(String symbol, int qty, String side) {
        if (symbol == null || symbol.isBlank()) {
            return OrderResult.fail(symbol, qty, "Symbol is required");
        }
        if (qty <= 0) {
            return OrderResult.fail(symbol, qty, "Quantity must be greater than zero");
        }
        if (apiKey == null || apiKey.isBlank() || apiSecret == null || apiSecret.isBlank()) {
            return OrderResult.fail(symbol, qty, "Alpaca credentials are not configured");
        }

        String endpoint = baseUrl.endsWith("/") ? baseUrl + "orders" : baseUrl + "/orders";
        JSONObject payload = new JSONObject()
                .put("symbol", symbol.toUpperCase())
                .put("qty", qty)
                .put("side", side)
                .put("type", "market")
                .put("time_in_force", "day");

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(15))
                .header("APCA-API-KEY-ID", apiKey)
                .header("APCA-API-SECRET-KEY", apiSecret)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            String body = response.body() == null ? "" : response.body();
            if (status < 200 || status >= 300) {
                return OrderResult.fail(symbol, qty, extractErrorMessage(body, status));
            }

            JSONObject json = new JSONObject(body);
            String orderId = json.optString("id", "");
            String filledAvgPrice = json.optString("filled_avg_price", "");
            BigDecimal fillPrice = parseMoney(filledAvgPrice);
            return OrderResult.ok(orderId.isBlank() ? null : orderId, symbol, qty, fillPrice);
        } catch (Exception ex) {
            return OrderResult.fail(symbol, qty, "Failed to submit Alpaca " + side + " order: " + ex.getMessage());
        }
    }

    private String extractErrorMessage(String responseBody, int statusCode) {
        try {
            JSONObject json = new JSONObject(responseBody == null ? "" : responseBody);
            String message = json.optString("message", "");
            if (!message.isBlank()) {
                return "Alpaca order rejected (" + statusCode + "): " + message;
            }
        } catch (Exception ignored) {
            // Fall back to a generic error when the body is not JSON.
        }
        return "Alpaca order rejected (" + statusCode + ")";
    }

    private BigDecimal parseMoney(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value)) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }
}
