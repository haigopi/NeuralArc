package com.neuralarc.service;

import com.neuralarc.model.NewsArticle;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpAlpacaNewsClientTest {

    @Test
    void parsesNewsArrayWithSymbolsAndTimestamp() {
        String body = """
                {"news":[
                  {"headline":"Acme beats earnings","summary":"Strong quarter","source":"benzinga",
                   "url":"https://example.com/a","created_at":"2026-06-15T11:30:00Z","symbols":["acme","spy"]},
                  {"headline":"Acme signs deal","source":"benzinga","updated_at":"2026-06-14T09:00:00Z","symbols":["ACME"]}
                ]}""";

        List<NewsArticle> articles = HttpAlpacaNewsClient.parseNews(body);

        assertEquals(2, articles.size());
        assertEquals("Acme beats earnings", articles.get(0).headline());
        assertEquals(Instant.parse("2026-06-15T11:30:00Z"), articles.get(0).createdAt());
        assertEquals(List.of("ACME", "SPY"), articles.get(0).symbols());
        // Falls back to updated_at when created_at is absent.
        assertEquals(Instant.parse("2026-06-14T09:00:00Z"), articles.get(1).createdAt());
    }

    @Test
    void returnsEmptyForMissingOrBlankBody() {
        assertTrue(HttpAlpacaNewsClient.parseNews(null).isEmpty());
        assertTrue(HttpAlpacaNewsClient.parseNews("  ").isEmpty());
        assertTrue(HttpAlpacaNewsClient.parseNews("{}").isEmpty());
    }

    @Test
    void toleratesMalformedTimestampWithEpochFallback() {
        String body = "{\"news\":[{\"headline\":\"x\",\"created_at\":\"not-a-date\",\"symbols\":[]}]}";

        List<NewsArticle> articles = HttpAlpacaNewsClient.parseNews(body);

        assertEquals(1, articles.size());
        assertEquals(Instant.EPOCH, articles.get(0).createdAt());
    }

    @Test
    void missingCredentialsRaiseException() {
        AlpacaNewsClient client = new HttpAlpacaNewsClient("", "");
        org.junit.jupiter.api.Assertions.assertThrows(AlpacaNewsException.class,
                () -> client.latestNews("AAPL", 5));
    }
}
