package com.neuralarc.service;

import com.neuralarc.model.MarketBar;
import com.neuralarc.model.RecommendationAction;
import com.neuralarc.model.RecommendationType;
import com.neuralarc.model.StrategyRecommendation;
import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

public class RecommendationEngine {
    private static final BigDecimal ONE_POINT_FIVE = new BigDecimal("1.5");
    private static final BigDecimal TWO = new BigDecimal("2");
    private static final BigDecimal TWO_POINT_FIVE = new BigDecimal("2.5");
    private static final BigDecimal THREE = new BigDecimal("3");
    private static final BigDecimal FIVE_PERCENT = new BigDecimal("0.05");
    private static final BigDecimal THREE_PERCENT = new BigDecimal("0.03");
    private static final BigDecimal MIN_PRICE = new BigDecimal("0.01");

    private final TechnicalIndicatorService indicators;

    public RecommendationEngine() {
        this(new TechnicalIndicatorService());
    }

    public RecommendationEngine(TechnicalIndicatorService indicators) {
        this.indicators = indicators;
    }

    public StrategyRecommendation generateShortTermRecommendation(String symbol, List<MarketBar> prices, BigDecimal currentPrice) {
        if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return StrategyRecommendation.unavailable(symbol, RecommendationType.SHORT_TERM, "Current price is missing.");
        }
        if (prices == null || prices.size() < 20) {
            return StrategyRecommendation.unavailable(symbol, RecommendationType.SHORT_TERM,
                    "At least 20 daily bars are required for the short-term recommendation.");
        }

        List<MarketBar> twoWeekBars = tail(prices, 10);
        Optional<BigDecimal> twoWeekLow = indicators.calculateLow(twoWeekBars);
        Optional<BigDecimal> twoWeekHigh = indicators.calculateHigh(twoWeekBars);
        Optional<BigDecimal> avgVolume10Day = indicators.calculateAverageVolume(prices, 10);
        Optional<BigDecimal> sma20 = indicators.calculateSMA(prices, 20);
        Optional<BigDecimal> atr14 = indicators.calculateATR(prices, 14);
        if (twoWeekLow.isEmpty() || twoWeekHigh.isEmpty() || avgVolume10Day.isEmpty() || sma20.isEmpty() || atr14.isEmpty()) {
            return StrategyRecommendation.unavailable(symbol, RecommendationType.SHORT_TERM,
                    "Short-term recommendation needs 20 bars, 14-bar ATR, and 10-day volume history.");
        }

        boolean breakdownMode = currentPrice.compareTo(twoWeekLow.get()) < 0;
        BigDecimal baseBuyPrice = breakdownMode
                ? currentPrice.subtract(twoWeekLow.get().subtract(currentPrice).abs())
                : twoWeekLow.get();
        baseBuyPrice = floorPrice(baseBuyPrice);

        BigDecimal buy1 = floorPrice(baseBuyPrice.subtract(atr14.get()));
        BigDecimal buy2 = floorPrice(baseBuyPrice.subtract(atr14.get().multiply(TWO)));
        BigDecimal stopLoss = floorPrice(buy2.subtract(atr14.get()));
        BigDecimal target1 = Monetary.round(baseBuyPrice.add(atr14.get().multiply(ONE_POINT_FIVE)));
        BigDecimal target2 = Monetary.round(baseBuyPrice.add(atr14.get().multiply(THREE)));
        BigDecimal recommendedSell = target1.max(twoWeekHigh.get());

        String trendStatus = currentPrice.compareTo(sma20.get()) > 0 ? "Bullish" : "Weak / Avoid";
        BigDecimal currentVolume = prices.getLast().volume();
        boolean strongVolume = currentVolume.compareTo(avgVolume10Day.get().multiply(ONE_POINT_FIVE)) >= 0;
        String volumeStatus = strongVolume ? "Strong" : "Normal / Weak";
        BigDecimal riskRewardRatio = riskRewardRatio(baseBuyPrice, stopLoss, recommendedSell);
        boolean nearSupport = currentPrice.compareTo(twoWeekLow.get()) >= 0
                && currentPrice.compareTo(twoWeekLow.get().multiply(BigDecimal.ONE.add(THREE_PERCENT))) <= 0;

        int confidence = 0;
        if (currentPrice.compareTo(sma20.get()) > 0) confidence += 30;
        if (nearSupport) confidence += 25;
        if (strongVolume) confidence += 20;
        if (riskRewardRatio.compareTo(TWO) >= 0) confidence += 15;
        if (!breakdownMode) confidence += 10;

        String warning = breakdownMode
                ? "Breakdown mode: price is below two-week support. Review carefully before applying."
                : "";

        return new StrategyRecommendation(
                symbol,
                RecommendationType.SHORT_TERM,
                baseBuyPrice,
                buy1,
                buy2,
                stopLoss,
                recommendedSell,
                target1,
                target2,
                trendStatus,
                volumeStatus,
                riskRewardRatio,
                confidence,
                actionFor(confidence),
                warning,
                breakdownMode
        );
    }

    public StrategyRecommendation generateLongTermRecommendation(String symbol, List<MarketBar> prices, BigDecimal currentPrice) {
        if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return StrategyRecommendation.unavailable(symbol, RecommendationType.LONG_TERM, "Current price is missing.");
        }
        if (prices == null || prices.size() < 200) {
            return StrategyRecommendation.unavailable(symbol, RecommendationType.LONG_TERM,
                    "At least 200 daily bars are required for the long-term recommendation.");
        }

        List<MarketBar> sixMonthBars = tail(prices, 126);
        List<MarketBar> oneYearBars = tail(prices, Math.min(prices.size(), 252));
        Optional<BigDecimal> sixMonthLow = indicators.calculateLow(sixMonthBars);
        Optional<BigDecimal> sixMonthHigh = indicators.calculateHigh(sixMonthBars);
        Optional<BigDecimal> oneYearLow = indicators.calculateLow(oneYearBars);
        Optional<BigDecimal> oneYearHigh = indicators.calculateHigh(oneYearBars);
        Optional<BigDecimal> sma50 = indicators.calculateSMA(prices, 50);
        Optional<BigDecimal> sma200 = indicators.calculateSMA(prices, 200);
        Optional<BigDecimal> atr14 = indicators.calculateATR(prices, 14);
        Optional<BigDecimal> avgVolume30Day = indicators.calculateAverageVolume(prices, 30);
        if (sixMonthLow.isEmpty() || sixMonthHigh.isEmpty() || oneYearLow.isEmpty() || oneYearHigh.isEmpty()
                || sma50.isEmpty() || sma200.isEmpty() || atr14.isEmpty() || avgVolume30Day.isEmpty()) {
            return StrategyRecommendation.unavailable(symbol, RecommendationType.LONG_TERM,
                    "Long-term recommendation needs 200 bars, 14-bar ATR, and 30-day volume history.");
        }

        boolean supportBuyMode = currentPrice.compareTo(sixMonthLow.get()) >= 0
                && currentPrice.compareTo(sixMonthLow.get().multiply(BigDecimal.ONE.add(FIVE_PERCENT))) <= 0;
        boolean breakoutMode = currentPrice.compareTo(sixMonthHigh.get()) > 0;
        BigDecimal baseBuyPrice = supportBuyMode
                ? sixMonthLow.get()
                : breakoutMode
                ? currentPrice
                : currentPrice;
        baseBuyPrice = floorPrice(baseBuyPrice);

        BigDecimal buy1 = floorPrice(baseBuyPrice.subtract(atr14.get().multiply(ONE_POINT_FIVE)));
        BigDecimal buy2 = floorPrice(baseBuyPrice.subtract(atr14.get().multiply(THREE)));
        BigDecimal stopLoss = floorPrice(buy2.subtract(atr14.get().multiply(ONE_POINT_FIVE)));
        BigDecimal target1 = Monetary.round(baseBuyPrice.add(atr14.get().multiply(TWO)));
        BigDecimal target2 = Monetary.round(baseBuyPrice.add(atr14.get().multiply(new BigDecimal("4"))));
        BigDecimal sellPrice = oneYearHigh.get().min(sixMonthHigh.get().max(target2));

        String trendStatus;
        if (sma50.get().compareTo(sma200.get()) > 0 && currentPrice.compareTo(sma50.get()) > 0) {
            trendStatus = "Strong Bullish";
        } else if (currentPrice.compareTo(sma200.get()) > 0) {
            trendStatus = "Neutral";
        } else {
            trendStatus = "Weak";
        }
        BigDecimal currentVolume = prices.getLast().volume();
        boolean strongVolume = currentVolume.compareTo(avgVolume30Day.get()) >= 0;
        String volumeStatus = strongVolume ? "Strong" : "Normal";
        BigDecimal riskRewardRatio = riskRewardRatio(baseBuyPrice, stopLoss, sellPrice);

        int confidence = 0;
        if (sma50.get().compareTo(sma200.get()) > 0) confidence += 30;
        if (currentPrice.compareTo(sma50.get()) > 0) confidence += 20;
        if (supportBuyMode || breakoutMode) confidence += 20;
        if (riskRewardRatio.compareTo(TWO_POINT_FIVE) >= 0) confidence += 15;
        if (strongVolume) confidence += 15;

        String warning = !supportBuyMode && !breakoutMode
                ? "Price is in the middle of the six-month range. WATCH is usually safer than a fresh BUY here."
                : "";

        return new StrategyRecommendation(
                symbol,
                RecommendationType.LONG_TERM,
                baseBuyPrice,
                buy1,
                buy2,
                stopLoss,
                sellPrice,
                target1,
                target2,
                trendStatus,
                volumeStatus,
                riskRewardRatio,
                confidence,
                actionFor(confidence),
                warning,
                false
        );
    }

    private List<MarketBar> tail(List<MarketBar> bars, int count) {
        int from = Math.max(0, bars.size() - count);
        return bars.subList(from, bars.size());
    }

    private BigDecimal riskRewardRatio(BigDecimal baseBuyPrice, BigDecimal stopLossPrice, BigDecimal sellPrice) {
        BigDecimal risk = baseBuyPrice.subtract(stopLossPrice);
        BigDecimal reward = sellPrice.subtract(baseBuyPrice);
        if (risk.compareTo(BigDecimal.ZERO) <= 0 || reward.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return reward.divide(risk, 2, RoundingMode.HALF_UP);
    }

    private RecommendationAction actionFor(int confidence) {
        if (confidence >= 75) {
            return RecommendationAction.BUY;
        }
        if (confidence >= 50) {
            return RecommendationAction.WATCH;
        }
        return RecommendationAction.AVOID;
    }

    private BigDecimal floorPrice(BigDecimal value) {
        return Monetary.round(value.max(MIN_PRICE));
    }
}
