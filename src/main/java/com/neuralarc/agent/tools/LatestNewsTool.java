package com.neuralarc.agent.tools;

import com.neuralarc.agent.AgentTool;
import com.neuralarc.agent.ToolArguments;
import com.neuralarc.agent.ToolParameters;
import com.neuralarc.model.NewsArticle;
import com.neuralarc.service.AlpacaNewsClient;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/**
 * Recent headlines for one symbol.
 *
 * <p>Headlines and sources only, never article bodies: the point is to let the model notice that a
 * move has a catalyst and say which one, not to have it read and paraphrase the press.
 */
public final class LatestNewsTool implements AgentTool {
    static final int MAX_ARTICLES = 20;
    static final int DEFAULT_ARTICLES = 5;

    private final AlpacaNewsClient newsClient;

    public LatestNewsTool(AlpacaNewsClient newsClient) {
        this.newsClient = newsClient;
    }

    @Override
    public String name() {
        return "latest_news";
    }

    @Override
    public String description() {
        return "Recent news headlines for one stock, newest first. Use it to find out whether a price move has"
                + " a known catalyst. Headlines only — cite the source when you rely on one.";
    }

    @Override
    public ToolParameters parameters() {
        return ToolParameters.builder()
                .required("symbol", ToolParameters.Type.STRING, "Stock ticker, e.g. AAPL.")
                .optional("limit", ToolParameters.Type.INTEGER,
                        "How many headlines, 1 to " + MAX_ARTICLES + " (default " + DEFAULT_ARTICLES + ").")
                .build();
    }

    @Override
    public JSONObject call(ToolArguments arguments) throws Exception {
        String symbol = arguments.symbol("symbol");
        int limit = arguments.integer("limit", 1, MAX_ARTICLES, DEFAULT_ARTICLES);
        List<NewsArticle> articles = newsClient.latestNews(symbol, limit);
        JSONArray rows = new JSONArray();
        for (NewsArticle article : articles) {
            rows.put(new JSONObject()
                    .put("headline", article.headline())
                    .put("source", article.source())
                    .put("url", article.url())
                    .put("published_at", String.valueOf(article.createdAt())));
        }
        return new JSONObject().put("symbol", symbol).put("count", rows.length()).put("articles", rows);
    }
}
