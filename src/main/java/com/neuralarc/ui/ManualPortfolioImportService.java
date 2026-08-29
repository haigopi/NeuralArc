package com.neuralarc.ui;

import com.neuralarc.api.AlpacaMarketDataApi;
import com.neuralarc.model.MarketBar;
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
import com.neuralarc.service.StrategyRepository;
import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class ManualPortfolioImportService {
    private static final int LOOKBACK_DAYS = 14;

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

    ManualPortfolioImportService(Gateway gateway) {
        this.gateway = gateway;
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
            BigDecimal twoWeekLow = loadTwoWeekLow(draft.symbol());
            BigDecimal baseBuy = selectBaseBuy(draft.recommendedEntry(), twoWeekLow);
            BigDecimal stopLoss = chooseStopLoss(draft.stopLoss(), baseBuy);
            BigDecimal target = draft.targets().stream().max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
            Strategy strategy = Strategy.fromConfig(
                    UUID.randomUUID().toString(),
                    "MANUAL_ADDITION: " + draft.symbol() + " " + modeLabel(gateway.targetMode()),
                    buildConfig(draft.symbol(), baseBuy, stopLoss, target),
                    gateway.targetMode()
            );
            strategy.setStatus(StrategyStatus.CREATED);
            strategy.setCurrentState(StrategyLifecycleState.CREATED);
            strategy.setLatestOrderStatus(gateway.targetMode() == StrategyMode.LIVE ? "LIVE_PENDING" : "PAPER_PENDING");
            strategy.setLatestAlpacaOrderId("");
            strategy.setLastTriggeredRuleType("MANUAL_IMPORT");
            strategy.setLastEvent("Manual addition imported for pending review. Recommended entry=$"
                    + draft.recommendedEntry().toPlainString()
                    + ", twoWeekLow=" + display(twoWeekLow)
                    + ", baseBuy=$" + strategy.baseBuyLimitPrice().toPlainString()
                    + ", stop=$" + strategy.stopLossPrice().toPlainString()
                    + ", target=$" + strategy.targetSellPrice().toPlainString()
                    + ".");
            gateway.assignWorkspace(strategy, gateway.targetWorkspaceId());
            gateway.repository().save(strategy);
            imported.add(draft.symbol());
        }
        return new ImportResult(imported, skipped);
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
            return safeStop;
        }
        return Monetary.round(baseBuy.multiply(new BigDecimal("0.97")));
    }

    private String modeLabel(StrategyMode mode) {
        return mode == StrategyMode.LIVE ? "Live" : "Paper";
    }

    private String display(BigDecimal value) {
        return value == null || value.signum() <= 0 ? "unavailable" : "$" + Monetary.round(value).toPlainString();
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
