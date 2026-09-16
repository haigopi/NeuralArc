package com.neuralarc.ui;

import com.neuralarc.analytics.ProtectiveStopPrice;
import com.neuralarc.api.AlpacaMarketDataApi;
import com.neuralarc.model.MarketBar;
import com.neuralarc.model.StrategyRecommendation;
import com.neuralarc.model.ProfitControlMode;
import com.neuralarc.model.ProfitHoldType;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyConfig;
import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyStatus;
import com.neuralarc.model.ThresholdType;
import com.neuralarc.model.TimeInForce;
import com.neuralarc.model.TrailingType;
import com.neuralarc.service.RecommendationEngine;
import com.neuralarc.service.StrategyRepository;
import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

final class ManualPortfolioImportService {
    private static final int LOOKBACK_DAYS = 14;
    /** Calendar days of history requested for the long-term recommendation (~1 trading year). */
    private static final int LONG_TERM_LOOKBACK_DAYS = 400;

    interface Gateway {
        StrategyRepository repository();
        String targetWorkspaceId();
        StrategyMode targetMode();
        boolean allowDuplicateSymbols();
        int defaultPollingSeconds();
        boolean defaultRepeatCycleAfterProfitExitEnabled();
        boolean defaultResubmitOnExpiryEnabled();
        AlpacaMarketDataApi marketDataApi();
        void assignWorkspace(Strategy strategy, String workspaceId);
    }

    private final Gateway gateway;
    private final RecommendationEngine recommendationEngine;

    ManualPortfolioImportService(Gateway gateway) {
        this(gateway, new RecommendationEngine());
    }

    ManualPortfolioImportService(Gateway gateway, RecommendationEngine recommendationEngine) {
        this.gateway = gateway;
        this.recommendationEngine = recommendationEngine;
    }

    ImportResult importDrafts(List<PortfolioStockImportDialog.ImportedStockDraft> drafts) {
        if (drafts == null || drafts.isEmpty()) {
            return new ImportResult(List.of(), List.of());
        }
        List<String> imported = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (PortfolioStockImportDialog.ImportedStockDraft draft : drafts) {
            if (DuplicateSymbolPolicy.wouldBeDuplicate(
                    draft.symbol(),
                    gateway.targetMode(),
                    gateway.repository().findAll(),
                    gateway.allowDuplicateSymbols(),
                    gateway.targetWorkspaceId(),
                    ""
            )) {
                skipped.add(draft.symbol() + ": duplicate symbol policy blocked this manual addition");
                continue;
            }
            Levels levels;
            try {
                levels = draft.autoPriced() ? autoLongTermLevels(draft.symbol()) : pastedLevels(draft);
            } catch (InsufficientMarketDataException ex) {
                skipped.add(draft.symbol() + ": " + ex.getMessage());
                continue;
            }
            Strategy strategy = Strategy.fromConfig(
                    UUID.randomUUID().toString(),
                    "MANUAL_ADDITION: " + draft.symbol() + " " + modeLabel(gateway.targetMode()),
                    buildConfig(draft.symbol(), levels.baseBuy(), levels.stopLoss(), levels.target()),
                    gateway.targetMode()
            );
            strategy.setStatus(StrategyStatus.CREATED);
            strategy.setCurrentState(StrategyLifecycleState.CREATED);
            strategy.setLatestOrderStatus(gateway.targetMode() == StrategyMode.LIVE ? "LIVE_PENDING" : "PAPER_PENDING");
            strategy.setLatestAlpacaOrderId("");
            strategy.setLastTriggeredRuleType(draft.autoPriced() ? "MANUAL_IMPORT_LONG_TERM" : "MANUAL_IMPORT");
            strategy.setLastEvent("Manual addition imported for pending review. "
                    + levels.origin()
                    + " baseBuy=$" + strategy.baseBuyLimitPrice().toPlainString()
                    + ", stop=$" + strategy.stopLossPrice().toPlainString()
                    + ", target=$" + strategy.targetSellPrice().toPlainString()
                    + ".");
            gateway.assignWorkspace(strategy, gateway.targetWorkspaceId());
            gateway.repository().save(strategy);
            imported.add(draft.symbol());
        }
        return new ImportResult(imported, skipped);
    }

    /** Levels taken from the pasted alert, refined by the recent two-week low. */
    private Levels pastedLevels(PortfolioStockImportDialog.ImportedStockDraft draft) {
        BigDecimal twoWeekLow = loadTwoWeekLow(draft.symbol());
        BigDecimal baseBuy = selectBaseBuy(draft.recommendedEntry(), twoWeekLow);
        return new Levels(
                baseBuy,
                chooseStopLoss(draft.stopLoss(), baseBuy),
                draft.targets().stream().max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO),
                "Recommended entry=$" + draft.recommendedEntry().toPlainString()
                        + ", twoWeekLow=" + display(twoWeekLow) + ",");
    }

    /**
     * Levels for a ticker pasted without prices: the app's own long-term recommendation, computed
     * from a year of daily bars.
     *
     * <p>Nothing is invented locally — if the market data is missing or too short for the engine, the
     * symbol is skipped with the reason rather than imported against a guessed price.
     */
    private Levels autoLongTermLevels(String symbol) {
        AlpacaMarketDataApi marketDataApi = gateway.marketDataApi();
        if (marketDataApi == null) {
            throw new InsufficientMarketDataException(
                    "market data is unavailable, so long-term levels could not be calculated");
        }
        List<MarketBar> bars;
        try {
            LocalDate end = LocalDate.now();
            bars = marketDataApi.getDailyBars(symbol, end.minusDays(LONG_TERM_LOOKBACK_DAYS), end);
        } catch (Exception ex) {
            throw new InsufficientMarketDataException("market data request failed (" + ex.getMessage() + ")");
        }
        if (bars == null || bars.isEmpty()) {
            throw new InsufficientMarketDataException("no daily bars were returned for this symbol");
        }
        BigDecimal currentPrice = Monetary.round(bars.getLast().close());
        if (currentPrice.signum() <= 0) {
            throw new InsufficientMarketDataException("the latest daily close is missing");
        }
        StrategyRecommendation recommendation =
                recommendationEngine.generateLongTermRecommendation(symbol, bars, currentPrice, currentPrice);
        BigDecimal baseBuy = recommendation.adjustedBaseBuyPrice();
        BigDecimal stopLoss = recommendation.stopLossPrice();
        BigDecimal target = recommendation.sellPrice();
        if (baseBuy.signum() <= 0 || target.compareTo(baseBuy) <= 0) {
            throw new InsufficientMarketDataException(
                    "not enough history for a long-term recommendation ("
                            + bars.size() + " daily bar(s) available)");
        }
        return new Levels(
                baseBuy,
                chooseStopLoss(stopLoss, baseBuy),
                target,
                "Long-term levels auto-calculated from " + bars.size() + " daily bars"
                        + " (last close=$" + currentPrice.toPlainString()
                        + ", trend=" + recommendation.trendStatus()
                        + ", confidence=" + recommendation.confidenceScore() + "%).");
    }

    private StrategyConfig buildConfig(String symbol, BigDecimal baseBuy, BigDecimal stopLoss, BigDecimal target) {
        return new StrategyConfig(
                symbol,
                baseBuy,
                1,
                true,
                stopLoss,
                true,
                target,
                BigDecimal.ZERO,
                0,
                BigDecimal.ZERO,
                0,
                false,
                false,
                BigDecimal.ZERO,
                Math.max(1, gateway.defaultPollingSeconds()),
                gateway.targetMode() == StrategyMode.PAPER,
                false,
                false,
                ProfitHoldType.PERCENT_TRAILING,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                gateway.defaultRepeatCycleAfterProfitExitEnabled(),
                ProfitControlMode.SELL_TRIGGER,
                ThresholdType.FIXED_AMOUNT,
                BigDecimal.ZERO,
                TrailingType.PERCENTAGE,
                BigDecimal.ZERO,
                gateway.defaultResubmitOnExpiryEnabled(),
                StrategyConfig.DEFAULT_BASE_BUY_REPOST_REDUCTION_PERCENT,
                TimeInForce.DAY
        );
    }

    private BigDecimal loadTwoWeekLow(String symbol) {
        AlpacaMarketDataApi marketDataApi = gateway.marketDataApi();
        if (marketDataApi == null) {
            return BigDecimal.ZERO;
        }
        try {
            LocalDate end = LocalDate.now();
            LocalDate start = end.minusDays(LOOKBACK_DAYS);
            return marketDataApi.getDailyBars(symbol, start, end).stream()
                    .map(MarketBar::low)
                    .filter(low -> low != null && low.signum() > 0)
                    .min(BigDecimal::compareTo)
                    .map(Monetary::round)
                    .orElse(BigDecimal.ZERO);
        } catch (Exception ex) {
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal selectBaseBuy(BigDecimal recommendedEntry, BigDecimal twoWeekLow) {
        if (recommendedEntry == null || recommendedEntry.signum() <= 0) {
            return Monetary.round(twoWeekLow);
        }
        if (twoWeekLow == null || twoWeekLow.signum() <= 0) {
            return Monetary.round(recommendedEntry);
        }
        return Monetary.round(recommendedEntry.min(twoWeekLow));
    }

    private BigDecimal chooseStopLoss(BigDecimal recommendedStop, BigDecimal baseBuy) {
        BigDecimal safeStop = Monetary.round(recommendedStop);
        if (safeStop.signum() > 0 && safeStop.compareTo(baseBuy) < 0) {
            return ProtectiveStopPrice.belowEntry(baseBuy, safeStop);
        }
        return ProtectiveStopPrice.belowEntry(baseBuy, baseBuy.multiply(new BigDecimal("0.97")));
    }

    private String modeLabel(StrategyMode mode) {
        return mode == StrategyMode.LIVE ? "Live" : "Paper";
    }

    private String display(BigDecimal value) {
        return value == null || value.signum() <= 0 ? "unavailable" : "$" + Monetary.round(value).toPlainString();
    }

    /** The three prices a manual addition needs, plus how they were arrived at (for the event log). */
    private record Levels(BigDecimal baseBuy, BigDecimal stopLoss, BigDecimal target, String origin) {
    }

    /** Raised when live market data cannot support a calculated import; the symbol is then skipped. */
    private static final class InsufficientMarketDataException extends RuntimeException {
        private InsufficientMarketDataException(String message) {
            super(message);
        }
    }

    record ImportResult(List<String> importedSymbols, List<String> skippedReasons) {
        ImportResult {
            importedSymbols = importedSymbols == null ? List.of() : List.copyOf(importedSymbols);
            skippedReasons = skippedReasons == null ? List.of() : List.copyOf(skippedReasons);
        }

        String summary(String modeLabel) {
            StringBuilder sb = new StringBuilder("<html><body style='width:360px'><b>Import Stocks</b><br><br>");
            sb.append("Imported manual pending-review strategies: ").append(importedSymbols.size());
            if (!importedSymbols.isEmpty()) {
                sb.append("<br>").append(String.join(", ", importedSymbols));
            }
            sb.append("<br><br>These rows stay as Manual Additions in ").append(modeLabel)
                    .append(" mode until you use the manual placement action.");
            if (!skippedReasons.isEmpty()) {
                sb.append("<br><br><b>Skipped:</b><br>").append(String.join("<br>", skippedReasons));
            }
            sb.append("</body></html>");
            return sb.toString();
        }
    }
}
