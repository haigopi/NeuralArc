package com.neuralarc.service;

import com.neuralarc.util.AppMetadata;
import org.json.JSONObject;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HttpAlpacaScreenerClient implements AlpacaScreenerClient {
    private static final Logger LOGGER = Logger.getLogger(HttpAlpacaScreenerClient.class.getName());

    private final HttpClient httpClient;
    private final String apiKey;
    private final String secretKey;
    private final String dataBaseUrl;

    public HttpAlpacaScreenerClient(String apiKey, String secretKey) {
        this(apiKey, secretKey, AppMetadata.alpacaDataUrl(), HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build());
    }

    HttpAlpacaScreenerClient(String apiKey, String secretKey, String dataBaseUrl, HttpClient httpClient) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.secretKey = secretKey == null ? "" : secretKey.trim();
        this.dataBaseUrl = dataBaseUrl == null || dataBaseUrl.isBlank()
                ? "https://data.alpaca.markets"
                : trimSlash(dataBaseUrl);
        this.httpClient = httpClient;
    }

    @Override
    public JSONObject getMarketMovers(int top) throws AlpacaScreenerException {
        String endpoint = dataBaseUrl + "/v1beta1/screener/stocks/movers?top=" + Math.max(1, Math.min(50, top));
        return execute(endpoint);
    }

    @Override
    public JSONObject getMostActives(String by, int top) throws AlpacaScreenerException {
        String ranking = "trades".equalsIgnoreCase(by) ? "trades" : "volume";
        String endpoint = dataBaseUrl + "/v1beta1/screener/stocks/most-actives?by="
                + URLEncoder.encode(ranking, StandardCharsets.UTF_8)
                + "&top=" + Math.max(1, Math.min(100, top));
        return execute(endpoint);
    }

    private JSONObject execute(String endpoint) throws AlpacaScreenerException {
        if (apiKey.isBlank() || secretKey.isBlank()) {
            throw new AlpacaScreenerException("Alpaca credentials are missing.");
        }
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
                throw new AlpacaScreenerException("Alpaca API authentication failed. Verify Settings.");
            }
            if (status == 429) {
                throw new AlpacaScreenerException("Alpaca screener rate limit exceeded. Try again shortly.");
            }
            if (status < 200 || status >= 300) {
                throw new AlpacaScreenerException("Alpaca screener returned HTTP " + status + ".");
            }
            return new JSONObject(body);
        } catch (AlpacaScreenerException ex) {
            throw ex;
        } catch (java.net.http.HttpTimeoutException ex) {
            throw new AlpacaScreenerException("Alpaca screener request timed out.", ex);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Alpaca screener request failed", ex);
            throw new AlpacaScreenerException("Network error while contacting Alpaca screener: " + ex.getMessage(), ex);
        }
    }

    private static String trimSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
