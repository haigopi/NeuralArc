package com.neuralarc.api;

import com.neuralarc.service.ApiRequestIdStore;
import com.neuralarc.util.Monetary;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HttpAlpacaClient implements AlpacaClient {
    private static final Logger LOGGER = Logger.getLogger(HttpAlpacaClient.class.getName());
    private static final String REQUEST_ID_HEADER = "X-Request-ID";

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ApiRequestIdStore requestIdStore = new ApiRequestIdStore();
    private final String apiKey;
    private final String secretKey;
    private final String tradingBaseUrl;
    private final String dataBaseUrl;
    private final boolean extendedHoursEnabled;

    public HttpAlpacaClient(String apiKey, String secretKey, String tradingBaseUrl, String dataBaseUrl) {
        this(apiKey, secretKey, tradingBaseUrl, dataBaseUrl, false);
    }

    public HttpAlpacaClient(String apiKey, String secretKey, String tradingBaseUrl, String dataBaseUrl, boolean extendedHoursEnabled) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.secretKey = secretKey == null ? "" : secretKey.trim();
        this.tradingBaseUrl = normalizeBaseUrl(tradingBaseUrl);
        this.dataBaseUrl = normalizeBaseUrl(dataBaseUrl);
        this.extendedHoursEnabled = extendedHoursEnabled;
    }

    @Override
    public AlpacaOrderData submitLimitBuyOrder(String symbol, int quantity, BigDecimal limitPrice, String clientOrderId) {
        return submitLimitOrder(symbol, quantity, limitPrice, clientOrderId, "buy");
    }

    @Override
    public AlpacaOrderData submitLimitSellOrder(String symbol, int quantity, BigDecimal limitPrice, String clientOrderId) {
        return submitLimitOrder(symbol, quantity, limitPrice, clientOrderId, "sell");
    }

    @Override
    public AlpacaOrderData submitTrailingStopSellOrder(
            String symbol,
            int quantity,
            BigDecimal trailPercent,
            BigDecimal trailPrice,
            String clientOrderId
    ) {
        JSONObject payload = new JSONObject()
                .put("symbol", symbol == null ? "" : symbol.toUpperCase())
                .put("qty", quantity)
                .put("side", "sell")
                .put("type", "trailing_stop")
                .put("time_in_force", "day")
                .put("client_order_id", clientOrderId == null ? "" : clientOrderId);
        BigDecimal normalizedTrailPercent = Monetary.round(trailPercent);
        BigDecimal normalizedTrailPrice = Monetary.round(trailPrice);
        if (normalizedTrailPercent.compareTo(BigDecimal.ZERO) > 0) {
            payload.put("trail_percent", normalizedTrailPercent.toPlainString());
        } else {
            payload.put("trail_price", normalizedTrailPrice.toPlainString());
        }
        String endpoint = tradingBaseUrl + "/v2/orders";
        HttpRequest request = baseRequest(endpoint)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();
        return executeOrderSubmission(request, payload, endpoint, symbol, clientOrderId, "sell", "trailing_stop", BigDecimal.ZERO);
    }

    @Override
    public Optional<AlpacaOrderData> getOrder(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return Optional.empty();
        }
        String endpoint = tradingBaseUrl + "/v2/orders/" + orderId;
        HttpRequest request = baseRequest(endpoint).GET().build();
        return executeJson(request).map(this::toOrderData);
    }

    @Override
    public List<AlpacaOrderData> getOpenOrders(String symbol) {
        String endpoint = buildOpenOrdersEndpoint(symbol);
        return fetchOpenOrders(endpoint);
    }

    @Override
    public List<AlpacaOrderData> getOpenOrders() {
        return fetchOpenOrders(buildOpenOrdersEndpoint(null));
    }

    @Override
    public boolean cancelOrder(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return false;
        }
        String endpoint = tradingBaseUrl + "/v2/orders/" + orderId;
        HttpRequest request = baseRequest(endpoint).DELETE().build();
        try {
            logRequest("DELETE", endpoint, null);
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            recordRequestId("cancelOrder", "DELETE", endpoint, response);
            logResponse("DELETE", endpoint, response.statusCode(), response.body());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Failed to cancel order " + orderId, ex);
            return false;
        }
    }

    @Override
    public Optional<AlpacaPositionData> getPosition(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return Optional.empty();
        }
        String endpoint = tradingBaseUrl + "/v2/positions/" + URLEncoder.encode(symbol.toUpperCase(), StandardCharsets.UTF_8);
        HttpRequest request = baseRequest(endpoint).GET().build();
        try {
            logRequest("GET", endpoint, null);
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            recordRequestId("getPosition", "GET", endpoint, response);
            logResponse("GET", endpoint, response.statusCode(), response.body());
            if (response.statusCode() == 404) {
                return Optional.empty();
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Optional.empty();
            }
            JSONObject json = new JSONObject(response.body() == null ? "{}" : response.body());
            return Optional.of(new AlpacaPositionData(
                    json.optString("symbol", ""),
                    parseMoney(json.optString("qty", "0")),
                    parseMoney(json.optString("avg_entry_price", "0")),
                    parseMoney(json.optString("current_price", "0")),
                    json.toString()
            ));
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Failed to read position", ex);
            return Optional.empty();
        }
    }

    @Override
    public List<AlpacaPositionData> getPositions() {
        String endpoint = tradingBaseUrl + "/v2/positions";
        HttpRequest request = baseRequest(endpoint).GET().build();
        Optional<String> body = executeBody(request);
        if (body.isEmpty()) {
            return List.of();
        }
        List<AlpacaPositionData> result = new ArrayList<>();
        try {
            JSONArray json = new JSONArray(body.get());
            for (int i = 0; i < json.length(); i++) {
                JSONObject item = json.optJSONObject(i);
                if (item != null) {
                    result.add(new AlpacaPositionData(
                            item.optString("symbol", ""),
                            parseMoney(item.optString("qty", "0")),
                            parseMoney(item.optString("avg_entry_price", "0")),
                            parseMoney(item.optString("current_price", "0")),
                            item.toString()
                    ));
                }
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Failed to parse positions", ex);
        }
        return result;
    }

    @Override
    public BigDecimal getLatestPrice(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return Monetary.zero();
        }
        String endpoint = dataBaseUrl + "/v2/stocks/" + URLEncoder.encode(symbol.toUpperCase(), StandardCharsets.UTF_8) + "/trades/latest";
        HttpRequest request = baseRequest(endpoint).GET().build();
        Optional<String> body = executeBody(request);
        if (body.isEmpty()) {
            return Monetary.zero();
        }
        try {
            JSONObject json = new JSONObject(body.get());
            JSONObject trade = json.optJSONObject("trade");
            if (trade == null) {
                return Monetary.zero();
            }
            Object price = trade.opt("p");
            return parseMoney(price == null ? "0" : String.valueOf(price));
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Failed to parse latest price", ex);
            return Monetary.zero();
        }
    }

    public Map<String, BigDecimal> getLatestPrices(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return Map.of();
        }
        LinkedHashSet<String> normalizedSymbols = new LinkedHashSet<>();
        for (String symbol : symbols) {
            if (symbol != null && !symbol.isBlank()) {
                normalizedSymbols.add(symbol.trim().toUpperCase());
            }
        }
        if (normalizedSymbols.isEmpty()) {
            return Map.of();
        }
        String endpoint = dataBaseUrl + "/v2/stocks/trades/latest?symbols="
                + URLEncoder.encode(String.join(",", normalizedSymbols), StandardCharsets.UTF_8);
        HttpRequest request = baseRequest(endpoint).GET().build();
        Optional<String> body = executeBody(request);
        if (body.isEmpty()) {
            return Map.of();
        }
        try {
            JSONObject json = new JSONObject(body.get());
            JSONObject trades = json.optJSONObject("trades");
            if (trades == null) {
                return Map.of();
            }
            Map<String, BigDecimal> prices = new LinkedHashMap<>();
            for (String symbol : normalizedSymbols) {
                JSONObject trade = trades.optJSONObject(symbol);
                if (trade == null) {
                    continue;
                }
                Object price = trade.opt("p");
                BigDecimal parsed = parseMoney(price == null ? "0" : String.valueOf(price));
                if (parsed.compareTo(BigDecimal.ZERO) > 0) {
                    prices.put(symbol, parsed);
                }
            }
            return prices;
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Failed to parse batch latest prices", ex);
            return Map.of();
        }
    }

    private AlpacaOrderData submitLimitOrder(String symbol, int quantity, BigDecimal limitPrice, String clientOrderId, String side) {
        JSONObject payload = new JSONObject()
                .put("symbol", symbol == null ? "" : symbol.toUpperCase())
                .put("qty", quantity)
                .put("side", side)
                .put("type", "limit")
                .put("time_in_force", "day")
                .put("limit_price", Monetary.round(limitPrice).toPlainString())
                .put("client_order_id", clientOrderId == null ? "" : clientOrderId);
        if (extendedHoursEnabled) {
            payload.put("extended_hours", true);
        }
        String endpoint = tradingBaseUrl + "/v2/orders";
        HttpRequest request = baseRequest(endpoint)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        return executeOrderSubmission(request, payload, endpoint, symbol, clientOrderId, side, "limit", Monetary.round(limitPrice));
    }

    private AlpacaOrderData executeOrderSubmission(
            HttpRequest request,
            JSONObject payload,
            String endpoint,
            String symbol,
            String clientOrderId,
            String side,
            String orderType,
            BigDecimal limitPrice
    ) {

        try {
            logRequest("POST", endpoint, payload.toString());
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            recordRequestId("submitLimitOrder", "POST", endpoint, response);
            String body = response.body() == null ? "{}" : response.body();
            logResponse("POST", endpoint, response.statusCode(), body);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                JSONObject error = parseObject(body);
                String brokerStatus = error.optString("status", "rejected");
                return new AlpacaOrderData(
                        "",
                        clientOrderId,
                        symbol,
                        side,
                        orderType,
                        Monetary.round(limitPrice),
                        Monetary.zero(),
                        Monetary.zero(),
                        brokerStatus,
                        error.toString(),
                        null
                );
            }
            return toOrderData(parseObject(body));
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Failed to submit order", ex);
            return AlpacaOrderData.transportFailure(ex.getMessage());
        }
    }

    private HttpRequest.Builder baseRequest(String endpoint) {
        return HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(15))
                .header("APCA-API-KEY-ID", apiKey)
                .header("APCA-API-SECRET-KEY", secretKey);
    }

    private Optional<String> executeBody(HttpRequest request) {
        try {
            logRequest(request.method(), request.uri().toString(), null);
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            recordRequestId("executeBody", request.method(), request.uri().toString(), response);
            logResponse(request.method(), request.uri().toString(), response.statusCode(), response.body());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Optional.empty();
            }
            return Optional.ofNullable(response.body());
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "HTTP call failed", ex);
            return Optional.empty();
        }
    }

    private String buildOpenOrdersEndpoint(String symbol) {
        String endpoint = tradingBaseUrl + "/v2/orders?status=open&direction=desc&limit=200";
        if (symbol != null && !symbol.isBlank()) {
            endpoint += "&symbols=" + URLEncoder.encode(symbol.toUpperCase(), StandardCharsets.UTF_8);
        }
        return endpoint;
    }

    private List<AlpacaOrderData> fetchOpenOrders(String endpoint) {
        HttpRequest request = baseRequest(endpoint).GET().build();
        Optional<String> body = executeBody(request);
        if (body.isEmpty()) {
            return List.of();
        }
        List<AlpacaOrderData> result = new ArrayList<>();
        try {
            JSONArray json = new JSONArray(body.get());
            for (int i = 0; i < json.length(); i++) {
                JSONObject item = json.optJSONObject(i);
                if (item != null) {
                    result.add(toOrderData(item));
                }
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Failed to parse open orders", ex);
        }
        return result;
    }

    private Optional<JSONObject> executeJson(HttpRequest request) {
        return executeBody(request).map(this::parseObject);
    }

    private void logRequest(String method, String endpoint, String body) {
        if (!LOGGER.isLoggable(Level.INFO)) {
            return;
        }
        String suffix = body == null || body.isBlank() ? "" : System.lineSeparator() + "body=" + formatBodyForLog(body);
        LOGGER.info(() -> "Request: " + endpoint + suffix);
    }

    private void logResponse(String method, String endpoint, int statusCode, String body) {
        if (!LOGGER.isLoggable(Level.INFO)) {
            return;
        }
        LOGGER.info(() -> " -> Response: " + endpoint
                + " status=" + statusCode
                + System.lineSeparator() + "body=" + formatBodyForLog(body));
    }

    private String formatBodyForLog(String body) {
        if (body == null || body.isBlank()) {
            return "<empty>";
        }
        String pretty = tryPrettyJson(body);
        int maxLength = 4000;
        return pretty.length() <= maxLength ? pretty : pretty.substring(0, maxLength) + System.lineSeparator() + "...";
    }

    private String tryPrettyJson(String body) {
        String trimmed = body == null ? "" : body.trim();
        try {
            if (trimmed.startsWith("{")) {
                return new JSONObject(trimmed).toString(2);
            }
            if (trimmed.startsWith("[")) {
                return new JSONArray(trimmed).toString(2);
            }
        } catch (Exception ignored) {
            // Fall through to plain text formatting for non-JSON or malformed content.
        }
        return trimmed.replaceAll("\\s+", " ").trim();
    }

    private void recordRequestId(String source, String method, String endpoint, HttpResponse<String> response) {
        String requestId = response.headers().firstValue(REQUEST_ID_HEADER).orElse("");
        if (!requestId.isBlank()) {
            LOGGER.info(() -> "Alpaca Request ID [" + source + "]: " + requestId + " (" + method + " " + endpoint + ")");
            requestIdStore.record(source, method, endpoint, requestId);
        }
    }

    private JSONObject parseObject(String body) {
        try {
            return new JSONObject(body == null ? "{}" : body);
        } catch (Exception ex) {
            return new JSONObject();
        }
    }

    private AlpacaOrderData toOrderData(JSONObject json) {
        String status = json.optString("status", "unknown");
        Instant submittedAt = parseInstant(json.optString("submitted_at", ""));
        if (submittedAt == null) {
            submittedAt = parseInstant(json.optString("created_at", ""));
        }
        return new AlpacaOrderData(
                json.optString("id", ""),
                json.optString("client_order_id", ""),
                json.optString("symbol", ""),
                json.optString("side", ""),
                json.optString("type", ""),
                parseMoney(json.optString("limit_price", "0")),
                parseMoney(json.optString("filled_avg_price", "0")),
                parseMoney(json.optString("filled_qty", "0")),
                status,
                json.toString(),
                submittedAt
        );
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value)) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception ex) {
            return null;
        }
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

    private String normalizeBaseUrl(String value) {
        String url = value == null ? "" : value.trim();
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }
}
