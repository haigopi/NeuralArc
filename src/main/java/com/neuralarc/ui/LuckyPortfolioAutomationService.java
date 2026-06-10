package com.neuralarc.ui;

import com.neuralarc.api.AlpacaMarketDataApi;
import com.neuralarc.model.AutoAnalyzeBundle;
import com.neuralarc.model.LuckySimulationSelection;
import com.neuralarc.model.RecommendationType;
import com.neuralarc.model.TrendingStock;
import com.neuralarc.service.AutoAnalyzeService;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

final class LuckyPortfolioAutomationService {
    private final AlpacaMarketDataApi marketDataApi;
    private final Consumer<String> logger;

    LuckyPortfolioAutomationService(AlpacaMarketDataApi marketDataApi, Consumer<String> logger) {
        if (marketDataApi == null) {
            throw new IllegalArgumentException("marketDataApi must not be null");
        }
        this.marketDataApi = marketDataApi;
        this.logger = logger == null ? ignored -> {} : logger;
    }

    List<LuckySimulationSelection> analyzeSelections(
            List<TrendingStock> stocks,
            RecommendationType recommendationType,
            int quantity
    ) {
        return LuckyParallelExecutor.mapPreservingOrder(
                        stocks,
                        "neuralarc-lucky-auto-analyze",
                        stock -> analyzeSelection(stock, recommendationType, quantity),
                        null
                ).stream()
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<LuckySimulationSelection> analyzeSelection(
            TrendingStock stock,
            RecommendationType recommendationType,
            int quantity
    ) {
        try {
            AutoAnalyzeBundle bundle = new AutoAnalyzeService(marketDataApi)
                    .analyzeBundle(stock.symbol(), 1, 15, stock.latestPrice());
            return Optional.of(new LuckySimulationSelection(stock, bundle, recommendationType, quantity));
        } catch (Exception ex) {
            logger.accept("[Portfolio Liquidation] Auto re-entry skipped " + stock.symbol() + ": " + ex.getMessage());
            return Optional.empty();
        }
    }
}
