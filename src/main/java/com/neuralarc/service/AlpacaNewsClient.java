package com.neuralarc.service;

import com.neuralarc.model.NewsArticle;

import java.util.List;

/** Boundary for Alpaca's news API, used as a cheap recency pre-filter before AI news analysis. */
public interface AlpacaNewsClient {
    /**
     * Fetch the most recent news articles tagged with the given symbol.
     *
     * @param symbol stock ticker symbol
     * @param limit  maximum number of articles to return
     * @return newest-first list of articles, never null
     * @throws AlpacaNewsException on API or network error
     */
    List<NewsArticle> latestNews(String symbol, int limit) throws AlpacaNewsException;
}
