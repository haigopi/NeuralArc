package com.neuralarc.model;

import java.time.Instant;

public record AiSourceAnalyzed(
        String title,
        String url,
        Instant publishedAt
) {
    public AiSourceAnalyzed {
        title = title == null ? "" : title.trim();
        url = url == null ? "" : url.trim();
    }
}
