package com.neuralarc.api;

import com.neuralarc.model.OrderResult;
import com.neuralarc.model.Position;
import com.neuralarc.util.Monetary;
import com.neuralarc.util.AppMetadata;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AlpacaTradingApi implements TradingApi {
    private static final Logger LOGGER = Logger.getLogger(AlpacaTradingApi.class.getName());
    private static final long PRICE_CACHE_TTL_MILLIS = 5_000L;
    private static final long POSITION_CACHE_TTL_MILLIS = 5_000L;
    private final String baseUrl;
    private final String dataUrl;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final Map<String, CacheEntry<BigDecimal>> latestPriceCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<Position>> positionCache = new ConcurrentHashMap<>();
    private String apiKey;
    private String apiSecret;

    public AlpacaTradingApi() {
        this("https://paper-api.alpaca.markets/v2");
    }

    public AlpacaTradingApi(String baseUrl) {
        this.baseUrl = baseUrl;
        String configuredDataUrl = AppMetadata.alpacaDataUrl();
        this.dataUrl = configuredDataUrl.endsWith("/")
                ? configuredDataUrl.substring(0, configuredDataUrl.length() - 1)
                : configuredDataUrl;
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
            logRequest("GET", endpoint, null);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            logResponse("GET", endpoint, response.statusCode(), response.body());
            return response.statusCode() == 200;
        } catch (Exception ex) {
            logFailure("GET", endpoint, ex);
            return false;
        }
    }

    @Override
    public BigDecimal getLatestPrice(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return Monetary.zero();
        }
        String normalizedSymbol = symbol.toUpperCase();
        if (apiKey == null || apiKey.isBlank() || apiSecret == null || apiSecret.isBlank()) {
            return Monetary.zero();
        }
        CacheEntry<BigDecimal> cached = latestPriceCache.get(normalizedSymbol);
        if (cached != null && !cached.isExpired(PRICE_CACHE_TTL_MILLIS)) {
            return cached.value();
        }

        String endpoint = dataUrl + "/v2/stocks/" + URLEncoder.encode(normalizedSymbol, StandardCharsets.UTF_8) + "/trades/latest";
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(10))
                .header("APCA-API-KEY-ID", apiKey)
                .header("APCA-API-SECRET-KEY", apiSecret)
                .GET()
                .build();
        try {
            logRequest("GET", endpoint, null);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            logResponse("GET", endpoint, response.statusCode(), response.body());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Monetary.zero();
            }
            JSONObject json = new JSONObject(response.body() == null ? "{}" : response.body());
            JSONObject trade = json.optJSONObject("trade");
            if (trade == null) {
                return Monetary.zero();
            }
            BigDecimal latestPrice = parseMoney(String.valueOf(trade.opt("p")));
            latestPriceCache.put(normalizedSymbol, new CacheEntry<>(latestPrice));
            return latestPrice;
        } catch (Exception ex) {
            logFailure("GET", endpoint, ex);
            return Monetary.zero();
        }
    }

    @Override
    public OrderResult placeBuyOrder(String symbol, int qty, BigDecimal limitPrice) {
        return submitOrder(symbol, qty, limitPrice, "buy");
    }

    @Override
    public OrderResult placeSellOrder(String symbol, int qty, BigDecimal limitPrice) {
        return submitOrder(symbol, qty, limitPrice, "sell");
    }

    @Override
    public boolean cancelOpenOrdersForSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return false;
        }
        if (apiKey == null || apiKey.isBlank() || apiSecret == null || apiSecret.isBlank()) {
            return false;
        }

        String encodedSymbol = URLEncoder.encode(symbol.toUpperCase(), StandardCharsets.UTF_8);
        String ordersEndpointBase = baseUrl.endsWith("/") ? baseUrl + "orders" : baseUrl + "/orders";
        String queryEndpoint = ordersEndpointBase + "?status=open&direction=desc&limit=500&symbols=" + encodedSymbol;
        HttpRequest listRequest = HttpRequest.newBuilder(URI.create(queryEndpoint))
                .timeout(Duration.ofSeconds(15))
                .header("APCA-API-KEY-ID", apiKey)
                .header("APCA-API-SECRET-KEY", apiSecret)
                .GET()
                .build();

        try {
            logRequest("GET", queryEndpoint, null);
            HttpResponse<String> listResponse = httpClient.send(listRequest, HttpResponse.BodyHandlers.ofString());
            logResponse("GET", queryEndpoint, listResponse.statusCode(), listResponse.body());
            if (listResponse.statusCode() < 200 || listResponse.statusCode() >= 300) {
                return false;
            }

            JSONArray orders = new JSONArray(listResponse.body() == null ? "[]" : listResponse.body());
            for (int i = 0; i < orders.length(); i++) {
                JSONObject order = orders.optJSONObject(i);
                if (order == null) {
                    continue;
                }
                String orderId = order.optString("id", "").trim();
                if (orderId.isBlank()) {
                    continue;
                }

                String cancelEndpoint = ordersEndpointBase + "/" + orderId;
                HttpRequest cancelRequest = HttpRequest.newBuilder(URI.create(cancelEndpoint))
                        .timeout(Duration.ofSeconds(15))
                        .header("APCA-API-KEY-ID", apiKey)
                        .header("APCA-API-SECRET-KEY", apiSecret)
                        .DELETE()
                        .build();

                logRequest("DELETE", cancelEndpoint, null);
                HttpResponse<String> cancelResponse = httpClient.send(cancelRequest, HttpResponse.BodyHandlers.ofString());
                logResponse("DELETE", cancelEndpoint, cancelResponse.statusCode(), cancelResponse.body());
                int status = cancelResponse.statusCode();
                if (!((status >= 200 && status < 300) || status == 404)) {
                    return false;
                }
            }
            return true;
        } catch (Exception ex) {
            logFailure("DELETE", queryEndpoint, ex);
            return false;
        }
    }

    @Override
    public Position getPosition(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return new Position("UNKNOWN");
        }
        String normalizedSymbol = symbol.toUpperCase();
        if (apiKey == null || apiKey.isBlank() || apiSecret == null || apiSecret.isBlank()) {
            return new Position(normalizedSymbol);
        }
        CacheEntry<Position> cached = positionCache.get(normalizedSymbol);
        if (cached != null && !cached.isExpired(POSITION_CACHE_TTL_MILLIS)) {
            return cached.value().copy();
        }

        String endpoint = (baseUrl.endsWith("/") ? baseUrl + "positions/" : baseUrl + "/positions/")
                + URLEncoder.encode(normalizedSymbol, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(10))
                .header("APCA-API-KEY-ID", apiKey)
                .header("APCA-API-SECRET-KEY", apiSecret)
                .GET()
                .build();
        try {
            logRequest("GET", endpoint, null);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            logResponse("GET", endpoint, response.statusCode(), response.body());
            if (response.statusCode() == 404) {
                Position empty = new Position(normalizedSymbol);
                positionCache.put(normalizedSymbol, new CacheEntry<>(empty.copy()));
                return empty;
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return new Position(normalizedSymbol);
            }

            JSONObject json = new JSONObject(response.body() == null ? "{}" : response.body());
            int qty = parseQuantity(json.optString("qty", "0"));
            BigDecimal avgEntry = parseMoney(json.optString("avg_entry_price", "0"));
            BigDecimal currentPrice = parseMoney(json.optString("current_price", "0"));
            if (currentPrice.compareTo(Monetary.zero()) <= 0) {
                currentPrice = getLatestPrice(normalizedSymbol);
            }

            Position position = new Position(normalizedSymbol);
            if (qty > 0) {
                position.applyBuy(qty, avgEntry);
            }
            position.setLastPrice(currentPrice);
            positionCache.put(normalizedSymbol, new CacheEntry<>(position.copy()));
            return position;
        } catch (Exception ex) {
            logFailure("GET", endpoint, ex);
            return new Position(normalizedSymbol);
        }
    }

    private record CacheEntry<T>(T value, long loadedAtMillis) {
        private CacheEntry(T value) {
            this(value, System.currentTimeMillis());
        }

        private boolean isExpired(long ttlMillis) {
            return System.currentTimeMillis() - loadedAtMillis >= ttlMillis;
        }
    }

    private int parseQuantity(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value)) {
            return 0;
        }
        try {
            return new BigDecimal(value).intValue();
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private OrderResult submitOrder(String symbol, int qty, BigDecimal limitPrice, String side) {
        if (symbol == null || symbol.isBlank()) {
            return OrderResult.fail(symbol, qty, "Symbol is required");
        }
        if (qty <= 0) {
            return OrderResult.fail(symbol, qty, "Quantity must be greater than zero");
        }
        BigDecimal normalizedLimit = Monetary.round(limitPrice);
        if (normalizedLimit.compareTo(Monetary.zero()) <= 0) {
            return OrderResult.fail(symbol, qty, "Limit price must be greater than zero");
        }
        if (apiKey == null || apiKey.isBlank() || apiSecret == null || apiSecret.isBlank()) {
            return OrderResult.fail(symbol, qty, "Alpaca credentials are not configured");
        }

        String endpoint = baseUrl.endsWith("/") ? baseUrl + "orders" : baseUrl + "/orders";
        JSONObject payload = new JSONObject()
                .put("symbol", symbol.toUpperCase())
                .put("qty", qty)
                .put("side", side)
                .put("type", "limit")
                .put("time_in_force", "day")
                .put("limit_price", normalizedLimit.toPlainString());

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(15))
                .header("APCA-API-KEY-ID", apiKey)
                .header("APCA-API-SECRET-KEY", apiSecret)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        try {
            logRequest("POST", endpoint, payload.toString());
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            String body = response.body() == null ? "" : response.body();
            logResponse("POST", endpoint, status, body);
            if (status < 200 || status >= 300) {
                return OrderResult.fail(symbol, qty, extractErrorMessage(body, status));
            }

            JSONObject json = new JSONObject(body);
            String orderId = json.optString("id", "");
            String filledAvgPrice = json.optString("filled_avg_price", "");
            BigDecimal fillPrice = parseMoney(filledAvgPrice);
            return OrderResult.ok(orderId.isBlank() ? null : orderId, symbol, qty, fillPrice);
        } catch (Exception ex) {
            logFailure("POST", endpoint, ex);
            return OrderResult.fail(symbol, qty, "Failed to submit Alpaca " + side + " order: " + ex.getMessage());
        }
    }

    private void logRequest(String method, String endpoint, String body) {
        if (!LOGGER.isLoggable(Level.INFO)) {
            return;
        }
        String suffix = body == null || body.isBlank() ? "" : " body=" + abbreviate(body);
        LOGGER.info(() -> "Request: " + endpoint + suffix);
    }

    private void logResponse(String method, String endpoint, int statusCode, String body) {
        if (!LOGGER.isLoggable(Level.INFO)) {
            return;
        }
        LOGGER.info(() -> " -> Response: " + endpoint
                + " status=" + statusCode + " body=" + abbreviate(body));
    }

    private void logFailure(String method, String endpoint, Exception ex) {
        LOGGER.log(Level.WARNING, "Alpaca API failure: " + method + " " + endpoint, ex);
    }

    private String abbreviate(String body) {
        if (body == null || body.isBlank()) {
            return "<empty>";
        }
        String flattened = body.replaceAll("\\s+", " ").trim();
        return flattened.length() <= 300 ? flattened : flattened.substring(0, 800) + "...";
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
            return Monetary.zero();
        }
        try {
            return Monetary.round(new BigDecimal(value));
        } catch (NumberFormatException ex) {
            return Monetary.zero();
        }
    }
}
