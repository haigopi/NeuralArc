package com.neuralarc.api;

import com.neuralarc.model.MarketBar;
import com.neuralarc.service.ApiRequestIdStore;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HTTP implementation of {@link AlpacaMarketDataApi} that calls the Alpaca Market Data
 * REST API at https://data.alpaca.markets.
 *
 * <p>Authentication uses APCA-API-KEY-ID and APCA-API-SECRET-KEY headers — no API keys
 * are ever logged or included in analytics events.</p>
 *
 * <p>Pagination: Alpaca returns a {@code next_page_token} field when more pages exist.
 * This client follows all pages automatically until the token is absent or blank.</p>
 *
 * <p>Price field used for intraday interval: bar close price ("c" field).
 * This is the last traded price within each bar window and is the most reliable
 * single-price summary supported by the Alpaca bars endpoint.</p>
 */
public class HttpAlpacaMarketDataApi implements AlpacaMarketDataApi {

    private static final Logger LOGGER = Logger.getLogger(HttpAlpacaMarketDataApi.class.getName());
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final int PAGE_LIMIT = 10_000;
    private static final int MAX_PAGES = 200; // safety cap; 200 × 10 000 = 2 M bars max

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ApiRequestIdStore requestIdStore = new ApiRequestIdStore();
    private final String apiKey;
    private final String secretKey;
    private final String dataBaseUrl;

    /**
     * @param apiKey    Alpaca API key ID — must not be null
     * @param secretKey Alpaca API secret key — must not be null
     */
    public HttpAlpacaMarketDataApi(String apiKey, String secretKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.secretKey = secretKey == null ? "" : secretKey.trim();
        String raw = AppMetadata.alpacaDataUrl();
        this.dataBaseUrl = raw.endsWith("/") ? raw.substring(0, raw.length() - 1) : raw;
    }

    @Override
    public List<MarketBar> getDailyBars(String symbol, LocalDate startDate, LocalDate endDate)
            throws AlpacaMarketDataException {
        String symbolUpper = validateSymbol(symbol);
        String start = ISO_DATE.format(startDate);
        String end   = ISO_DATE.format(endDate);
        String urlTemplate = dataBaseUrl + "/v2/stocks/"
                + URLEncoder.encode(symbolUpper, StandardCharsets.UTF_8)
                + "/bars?timeframe=1Day&start=" + start + "&end=" + end
                + "&limit=" + PAGE_LIMIT + "&feed=iex";
        return fetchAllPages(symbolUpper, urlTemplate);
    }

    @Override
    public List<MarketBar> getIntradayBars(String symbol, LocalDate startDate, LocalDate endDate, int intervalMinutes)
            throws AlpacaMarketDataException {
        String symbolUpper = validateSymbol(symbol);
        if (intervalMinutes <= 0) {
            throw new AlpacaMarketDataException("Interval must be a positive number of minutes, got: " + intervalMinutes);
        }
        String timeframe = intervalMinutes + "Min";
        String start = ISO_DATE.format(startDate);
        String end   = ISO_DATE.format(endDate);
        String urlTemplate = dataBaseUrl + "/v2/stocks/"
                + URLEncoder.encode(symbolUpper, StandardCharsets.UTF_8)
                + "/bars?timeframe=" + timeframe + "&start=" + start + "&end=" + end
                + "&limit=" + PAGE_LIMIT + "&feed=iex";
        return fetchAllPages(symbolUpper, urlTemplate);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private List<MarketBar> fetchAllPages(String symbol, String firstUrl)
            throws AlpacaMarketDataException {
        List<MarketBar> all = new ArrayList<>();
        String nextUrl = firstUrl;
        int pageCount = 0;

        while (nextUrl != null && pageCount < MAX_PAGES) {
            pageCount++;
            final String logUrl = nextUrl;
            final int logPage = pageCount;
            LOGGER.info(() -> "Alpaca market data request #" + logPage + ": " + logUrl);
            JSONObject body = executeGet(nextUrl);

            JSONArray bars = body.optJSONArray("bars");
            if (bars != null) {
                for (int i = 0; i < bars.length(); i++) {
                    JSONObject b = bars.optJSONObject(i);
                    if (b != null) {
                        all.add(parseBar(symbol, b));
                    }
                }
            }

            String token = body.optString("next_page_token", "");
            if (token == null || token.isBlank() || "null".equalsIgnoreCase(token)) {
                break;
            }
            // Append page token to the first URL (strip existing page_token param if any)
            String base = firstUrl.contains("&page_token=")
                    ? firstUrl.substring(0, firstUrl.indexOf("&page_token="))
                    : firstUrl;
            nextUrl = base + "&page_token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
        }

        final int finalPageCount = pageCount;
        final int finalSize = all.size();
        LOGGER.info(() -> "Alpaca market data: fetched " + finalSize + " bars for " + symbol
                + " in " + finalPageCount + " page(s).");
        return all;
    }

    private JSONObject executeGet(String url) throws AlpacaMarketDataException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("APCA-API-KEY-ID", apiKey)
                .header("APCA-API-SECRET-KEY", secretKey)
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String requestId = response.headers().firstValue("X-Request-ID").orElse("");
            if (!requestId.isBlank()) {
                requestIdStore.record("marketData", "GET", url, requestId);
            }
            int status = response.statusCode();
            String bodyText = response.body() == null ? "{}" : response.body();

            if (status == 401 || status == 403) {
                throw new AlpacaMarketDataException("Alpaca API authentication failed (HTTP " + status
                        + "). Please verify your API key and secret in Settings.");
            }
            if (status == 422) {
                throw new AlpacaMarketDataException("Invalid request to Alpaca API (HTTP 422): " + bodyText);
            }
            if (status == 429) {
                throw new AlpacaMarketDataException("Alpaca API rate limit exceeded (HTTP 429). "
                        + "Try again in a few seconds or reduce the analysis interval.");
            }
            if (status < 200 || status >= 300) {
                throw new AlpacaMarketDataException("Alpaca API returned HTTP " + status + ": " + bodyText);
            }
            try {
                return new JSONObject(bodyText);
            } catch (Exception ex) {
                throw new AlpacaMarketDataException("Failed to parse Alpaca API response: " + bodyText, ex);
            }
        } catch (AlpacaMarketDataException ex) {
            throw ex;
        } catch (java.net.http.HttpTimeoutException ex) {
            throw new AlpacaMarketDataException("Request to Alpaca timed out. Check your network connection.", ex);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Alpaca market data HTTP error: " + url, ex);
            throw new AlpacaMarketDataException("Network error while contacting Alpaca: " + ex.getMessage(), ex);
        }
    }

    private MarketBar parseBar(String symbol, JSONObject b) {
        return new MarketBar(
                symbol,
                b.optString("t", ""),
                parseBd(b, "o"),
                parseBd(b, "h"),
                parseBd(b, "l"),
                parseBd(b, "c"),
                parseBd(b, "v")
        );
    }

    private BigDecimal parseBd(JSONObject obj, String key) {
        Object val = obj.opt(key);
        if (val == null) return BigDecimal.ZERO;
        try {
            return new BigDecimal(String.valueOf(val));
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private String validateSymbol(String symbol) throws AlpacaMarketDataException {
        if (symbol == null || symbol.isBlank()) {
            throw new AlpacaMarketDataException("Stock symbol must not be blank.");
        }
        return symbol.trim().toUpperCase();
    }
}

