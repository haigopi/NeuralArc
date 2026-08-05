package com.neuralarc.earningshunter;

import com.neuralarc.model.NewsArticle;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record EarningsHunterCandidate(
        String symbol,
        String companyName,
        BigDecimal currentPrice,
        BigDecimal previousClose,
        BigDecimal dayChangePercent,
        long averageVolume,
        BigDecimal relativeVolume,
        List<NewsArticle> earningsArticles,
        Instant latestEarningsNewsAt
) {}
