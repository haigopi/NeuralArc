package com.neuralarc.ui;

import com.neuralarc.api.AlpacaMarketDataApi;
import com.neuralarc.model.AutoAnalyzeBundle;
import com.neuralarc.model.SmartPicksSimulationSelection;
import com.neuralarc.model.RecommendationType;
import com.neuralarc.model.TrendingStock;
import com.neuralarc.service.AutoAnalyzeService;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

final class SmartPicksPortfolioAutomationService {
    private final AlpacaMarketDataApi marketDataApi;
    private final Consumer<String> logger;

    SmartPicksPortfolioAutomationService(AlpacaMarketDataApi marketDataApi, Consumer<String> logger) {
        if (marketDataApi == null) {
            throw new IllegalArgumentException("marketDataApi must not be null");
        }
        this.marketDataApi = marketDataApi;
        this.logger = logger == null ? ignored -> {} : logger;
    }

    List<SmartPicksSimulationSelection> analyzeSelections(
            List<TrendingStock> stocks,
            RecommendationType recommendationType,
            int quantity
    ) {
        // One cache for the whole run: every symbol asks for the same four overlapping daily windows,
        // so without this each one costs four requests where one will do.
        AlpacaMarketDataApi cachedApi = new com.neuralarc.api.CachingMarketDataApi(marketDataApi);
        return SmartPicksParallelExecutor.mapPreservingOrder(
                        stocks,
                        "neuralarc-smart-picks-auto-analyze",
                        stock -> analyzeSelection(cachedApi, stock, recommendationType, quantity),
                        null
                ).stream()
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<SmartPicksSimulationSelection> analyzeSelection(
            AlpacaMarketDataApi api,
            TrendingStock stock,
            RecommendationType recommendationType,
            int quantity
    ) {
        try {
            AutoAnalyzeBundle bundle = new AutoAnalyzeService(api)
                    .analyzeBundle(stock.symbol(), 1, 15, stock.latestPrice());
            return Optional.of(new SmartPicksSimulationSelection(stock, bundle, recommendationType, quantity));
        } catch (Exception ex) {
            logger.accept("[Portfolio Liquidation] Auto re-entry skipped " + stock.symbol() + ": " + ex.getMessage());
            return Optional.empty();
        }
    }
}
