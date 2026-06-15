package com.neuralarc.service;

import com.neuralarc.model.NewsArticle;
import com.neuralarc.util.AppMetadata;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Live Alpaca news client ({@code /v1beta1/news}). Mirrors the auth/error handling of the screener client. */
public class HttpAlpacaNewsClient implements AlpacaNewsClient {
    private static final Logger LOGGER = Logger.getLogger(HttpAlpacaNewsClient.class.getName());

    private final HttpClient httpClient;
    private final String apiKey;
    private final String secretKey;
    private final String dataBaseUrl;

    public HttpAlpacaNewsClient(String apiKey, String secretKey) {
        this(apiKey, secretKey, AppMetadata.alpacaDataUrl(), HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build());
    }

    HttpAlpacaNewsClient(String apiKey, String secretKey, String dataBaseUrl, HttpClient httpClient) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.secretKey = secretKey == null ? "" : secretKey.trim();
        this.dataBaseUrl = dataBaseUrl == null || dataBaseUrl.isBlank()
                ? "https://data.alpaca.markets"
                : trimSlash(dataBaseUrl);
        this.httpClient = httpClient;
    }

    @Override
    public List<NewsArticle> latestNews(String symbol, int limit) throws AlpacaNewsException {
        if (apiKey.isBlank() || secretKey.isBlank()) {
            throw new AlpacaNewsException("Alpaca credentials are missing.");
        }
        String safeSymbol = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
        if (safeSymbol.isBlank()) {
            return List.of();
        }
        String endpoint = dataBaseUrl + "/v1beta1/news?symbols=" + URLEncoder.encode(safeSymbol, StandardCharsets.UTF_8)
                + "&limit=" + Math.max(1, Math.min(50, limit)) + "&sort=desc";
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(30))
                .header("APCA-API-KEY-ID", apiKey)
                .header("APCA-API-SECRET-KEY", secretKey)
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            String body = response.body() == null ? "{}" : response.body();
            if (status == 401 || status == 403) {
                throw new AlpacaNewsException("Alpaca API authentication failed. Verify Settings.");
            }
            if (status == 429) {
                throw new AlpacaNewsException("Alpaca news rate limit exceeded. Try again shortly.");
            }
            if (status < 200 || status >= 300) {
                throw new AlpacaNewsException("Alpaca news returned HTTP " + status + ".");
            }
            return parseNews(body);
        } catch (AlpacaNewsException ex) {
            throw ex;
        } catch (java.net.http.HttpTimeoutException ex) {
            throw new AlpacaNewsException("Alpaca news request timed out.", ex);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Alpaca news request failed", ex);
            throw new AlpacaNewsException("Network error while contacting Alpaca news: " + ex.getMessage(), ex);
        }
    }

    /** Parse the {@code news} array from an Alpaca news response body. Package-visible for testing. */
    static List<NewsArticle> parseNews(String body) {
        List<NewsArticle> articles = new ArrayList<>();
        if (body == null || body.isBlank()) {
            return articles;
        }
        JSONArray array = new JSONObject(body).optJSONArray("news");
        if (array == null) {
            return articles;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            List<String> symbols = new ArrayList<>();
            JSONArray symbolArray = item.optJSONArray("symbols");
            if (symbolArray != null) {
                for (int j = 0; j < symbolArray.length(); j++) {
                    String symbol = symbolArray.optString(j, "").trim().toUpperCase(Locale.ROOT);
                    if (!symbol.isBlank()) {
                        symbols.add(symbol);
                    }
                }
            }
            articles.add(new NewsArticle(
                    item.optString("headline", ""),
                    item.optString("summary", ""),
                    item.optString("source", ""),
                    item.optString("url", ""),
                    parseInstant(item.optString("created_at", item.optString("updated_at", ""))),
                    symbols));
        }
        return articles;
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return Instant.EPOCH;
        }
        try {
            return Instant.parse(value);
        } catch (Exception ex) {
            return Instant.EPOCH;
        }
    }

    private static String trimSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
