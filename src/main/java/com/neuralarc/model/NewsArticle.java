package com.neuralarc.model;

import java.time.Instant;
import java.util.List;

/** A single news article returned by Alpaca's news API, used as a cheap catalyst pre-filter. */
public record NewsArticle(
        String headline,
        String summary,
        String source,
        String url,
        Instant createdAt,
        List<String> symbols
) {
    public NewsArticle {
        headline = headline == null ? "" : headline.trim();
        summary = summary == null ? "" : summary.trim();
        source = source == null ? "" : source.trim();
        url = url == null ? "" : url.trim();
        symbols = symbols == null ? List.of() : List.copyOf(symbols);
    }
}
