package com.neuralarc.api;

import com.neuralarc.model.OrderResult;
import com.neuralarc.model.Position;

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
        return OrderResult.fail(symbol, qty, "Alpaca API stub not implemented yet (endpoint: " + baseUrl + ")");
    }

    @Override
    public OrderResult placeSellOrder(String symbol, int qty) {
        return OrderResult.fail(symbol, qty, "Alpaca API stub not implemented yet (endpoint: " + baseUrl + ")");
    }

    @Override
    public Position getPosition(String symbol) {
        return emptyPosition;
    }
}
