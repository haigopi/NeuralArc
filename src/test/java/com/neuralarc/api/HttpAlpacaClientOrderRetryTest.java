package com.neuralarc.api;

import com.neuralarc.model.TimeInForce;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLSession;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpAlpacaClientOrderRetryTest {
    private static final String ACCEPTED = "{\"id\":\"ord-1\",\"client_order_id\":\"c1\",\"symbol\":\"AAPL\",\"side\":\"buy\","
            + "\"type\":\"limit\",\"status\":\"new\",\"filled_qty\":\"0\"}";
    private static final String LIMITED = "{\"code\":42910000,\"message\":\"rate limit exceeded\"}";

    @Test
    void aRateLimitedOrderIsRetriedUntilAlpacaAcceptsIt() {
        // Six picks sent back to back: four came back "Rate limit exceeded" and their strategies failed.
        StubClient client = new StubClient(429, 429, 200);

        AlpacaOrderData order = client.submitLimitBuyOrder("AAPL", 1, new BigDecimal("180.00"), "c1", TimeInForce.DAY);

        assertEquals("ord-1", order.orderId());
        assertEquals(3, client.sends);
        assertEquals(List.of(500L, 1000L), client.sleeps, "backs off before each retry");
    }

    @Test
    void itStopsAfterTheBackoffsAttemptsInsteadOfRetryingForever() {
        StubClient client = new StubClient(429, 429, 429, 429, 429, 429);

        AlpacaOrderData order = client.submitLimitBuyOrder("AAPL", 1, new BigDecimal("180.00"), "c1", TimeInForce.DAY);

        assertEquals("", order.orderId(), "reported as not placed");
        assertEquals(RateLimitBackoff.MAX_ATTEMPTS, client.sends);
    }

    @Test
    void otherRejectionsAreNotRetried() {
        StubClient client = new StubClient(422);

        client.submitLimitBuyOrder("AAPL", 1, new BigDecimal("180.00"), "c1", TimeInForce.DAY);

        assertEquals(1, client.sends, "a validation failure answers the same every time");
    }

    private static final class StubClient extends HttpAlpacaClient {
        private final Deque<Integer> statuses = new ArrayDeque<>();
        private final List<Long> sleeps = new ArrayList<>();
        private int sends;

        private StubClient(Integer... statuses) {
            super("key", "secret", "https://paper-api.example", "https://data.example");
            this.statuses.addAll(List.of(statuses));
            orderRetrySleeper = sleeps::add;
        }

        @Override
        HttpResponse<String> sendTracked(HttpRequest request) {
            sends++;
            int status = statuses.isEmpty() ? 200 : statuses.poll();
            return new StubResponse(status, status == 200 ? ACCEPTED : LIMITED, request);
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
