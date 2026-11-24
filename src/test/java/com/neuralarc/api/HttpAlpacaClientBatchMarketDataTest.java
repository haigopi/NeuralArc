package com.neuralarc.api;

import com.neuralarc.model.MarketBar;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLSession;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpAlpacaClientBatchMarketDataTest {
    @Test
    void oneInvalidSymbolDoesNotBlankTheOpenLowHighOfEveryOtherRow() {
        // Alpaca answers 400 for the whole request when any symbol is not a valid asset (825CVR012).
        StubClient client = new StubClient(Set.of("825CVR012"), 200);

        Map<String, MarketBar> bars = client.getDailySnapshots(List.of("AAPL", "825CVR012", "NBIS", "MSFT"));

        assertEquals(Set.of("AAPL", "NBIS", "MSFT"), bars.keySet());
        assertEquals(0, new BigDecimal("12.00").compareTo(bars.get("AAPL").low()));
        assertEquals(0, new BigDecimal("15.00").compareTo(bars.get("AAPL").high()));
        assertEquals(0, new BigDecimal("10.00").compareTo(bars.get("AAPL").open()));
    }

    @Test
    void oneInvalidSymbolDoesNotBlankTheLatestPricesEither() {
        StubClient client = new StubClient(Set.of("825CVR012"), 200);

        Map<String, BigDecimal> prices = client.getLatestPrices(List.of("AAPL", "825CVR012", "NBIS"));

        assertEquals(Set.of("AAPL", "NBIS"), prices.keySet());
    }

    @Test
    void aLargeSymbolListIsSentInChunks() {
        StubClient client = new StubClient(Set.of(), 200);
        List<String> symbols = IntStream.range(0, 250).mapToObj(i -> "S" + i).collect(Collectors.toList());

        Map<String, MarketBar> bars = client.getDailySnapshots(symbols);

        assertEquals(250, bars.size());
        assertEquals(3, client.requests.size(), "100 + 100 + 50");
        assertTrue(client.requests.stream().allMatch(r -> r.size() <= 100));
    }

    @Test
    void aServerFailureIsNotSplitIntoManyRequests() {
        StubClient client = new StubClient(Set.of(), 503);

        Map<String, MarketBar> bars = client.getDailySnapshots(List.of("AAPL", "NBIS", "MSFT", "TSLA"));

        assertTrue(bars.isEmpty());
        assertEquals(1, client.requests.size(), "a 5xx would fail again for every half");
    }

    @Test
    void aRateLimitIsNotSplitEither() {
        StubClient client = new StubClient(Set.of(), 429);

        assertTrue(client.getDailySnapshots(List.of("AAPL", "NBIS")).isEmpty());
        assertEquals(1, client.requests.size());
    }

    @Test
    void aLoneRejectedSymbolReturnsNothingAndStops() {
        StubClient client = new StubClient(Set.of("825CVR012"), 200);

        assertTrue(client.getDailySnapshots(List.of("825CVR012")).isEmpty());
        assertFalse(client.requests.isEmpty());
        assertEquals(1, client.requests.size());
    }

    private static final class StubClient extends HttpAlpacaClient {
        private final Set<String> invalid;
        private final int failureStatus;
        private final List<List<String>> requests = new ArrayList<>();

        private StubClient(Set<String> invalid, int status) {
            super("key", "secret", "https://paper-api.example", "https://data.example");
            this.invalid = invalid;
            this.failureStatus = status;
        }

        @Override
        HttpResponse<String> sendTracked(HttpRequest request) {
            String query = request.uri().getRawQuery();
            String csv = URLDecoder.decode(query.substring("symbols=".length()), StandardCharsets.UTF_8);
            List<String> symbols = List.of(csv.split(","));
            requests.add(symbols);
            if (failureStatus != 200) {
                return new StubResponse(failureStatus, "{}", request);
            }
            if (symbols.stream().anyMatch(invalid::contains)) {
                return new StubResponse(400, "{\"message\":\"invalid symbol\"}", request);
            }
            boolean snapshots = request.uri().getPath().endsWith("/snapshots");
            String entries = symbols.stream().map(sym -> snapshots
                    ? "\"" + sym + "\":{\"dailyBar\":{\"t\":\"2026-11-24T05:00:00Z\",\"o\":10.0,\"h\":15.0,\"l\":12.0,\"c\":13.0,\"v\":100}}"
                    : "\"" + sym + "\":{\"p\":13.5}").collect(Collectors.joining(","));
            String body = snapshots ? "{" + entries + "}" : "{\"trades\":{" + entries + "}}";
            return new StubResponse(200, body, request);
        }
    }

    private record StubResponse(int statusCode, String body, HttpRequest request) implements HttpResponse<String> {
        @Override public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (a, b) -> true); }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return request.uri(); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }
}
