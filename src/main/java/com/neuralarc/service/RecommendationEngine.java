package com.neuralarc.service;

import com.neuralarc.model.MarketBar;
import com.neuralarc.model.MarketMode;
import com.neuralarc.model.RecommendationAction;
import com.neuralarc.model.RecommendationType;
import com.neuralarc.model.ShortTermMarketMode;
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
    private static final BigDecimal FOUR = new BigDecimal("4");
    private static final BigDecimal HALF = new BigDecimal("0.5");
    private static final BigDecimal FIVE_PERCENT = new BigDecimal("0.05");
    private static final BigDecimal ZERO_POINT_NINE_FIVE = new BigDecimal("0.95");
    private static final BigDecimal ZERO_POINT_EIGHTY_FIVE = new BigDecimal("0.85");
    private static final BigDecimal MIN_DIP_PCT = new BigDecimal("0.005");
    private static final BigDecimal MAX_DIP_PCT = new BigDecimal("0.03");
    private static final BigDecimal SHORT_TERM_VOL_WEIGHT = new BigDecimal("0.75");
    private static final BigDecimal SHORT_TERM_MIN_DIP_PCT = new BigDecimal("0.003");
    private static final BigDecimal SHORT_TERM_MAX_DIP_PCT = new BigDecimal("0.025");
    private static final BigDecimal SHORT_TERM_FALLBACK_DIP_PCT = new BigDecimal("0.0075");
    private static final BigDecimal NINETY_PERCENT = new BigDecimal("0.90");
    private static final BigDecimal ZERO_POINT_NINE_NINE_TWO_FIVE = new BigDecimal("0.9925");
    private static final BigDecimal MIN_PRICE = new BigDecimal("0.01");

    private final TechnicalIndicatorService indicators;

    public RecommendationEngine() {
        this(new TechnicalIndicatorService());
    }

    public RecommendationEngine(TechnicalIndicatorService indicators) {
        this.indicators = indicators;
    }

    public StrategyRecommendation generateShortTermRecommendation(
            String symbol,
            List<MarketBar> prices,
            BigDecimal currentPrice,
            BigDecimal lastClosePrice
    ) {
        if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return StrategyRecommendation.unavailable(symbol, RecommendationType.SHORT_TERM, "Current price is missing.");
        }
        if (prices == null || prices.size() < 10) {
            return StrategyRecommendation.unavailable(symbol, RecommendationType.SHORT_TERM,
                    "At least 10 daily bars are required for the short-term recommendation.");
        }

        List<MarketBar> twoWeekBars = tail(prices, Math.min(prices.size(), 14));
        Optional<BigDecimal> twoWeekLow = indicators.calculateLow(twoWeekBars);
        Optional<BigDecimal> twoWeekHigh = indicators.calculateHigh(twoWeekBars);
        Optional<BigDecimal> avgVolume10Day = indicators.calculateAverageVolume(prices, Math.min(10, prices.size()));
        Optional<BigDecimal> sma20 = indicators.calculateSMA(prices, Math.min(20, prices.size()));
        Optional<BigDecimal> atr14 = indicators.calculateATR(prices, Math.min(14, Math.max(1, prices.size() - 1)));
        if (twoWeekLow.isEmpty() || twoWeekHigh.isEmpty() || avgVolume10Day.isEmpty() || sma20.isEmpty() || atr14.isEmpty()) {
            return StrategyRecommendation.unavailable(symbol, RecommendationType.SHORT_TERM,
                    "Short-term recommendation needs enough bars for two-week range, ATR, SMA, and 10-day volume history.");
        }

        BigDecimal effectiveMarketPrice = validPrice(lastClosePrice) ? lastClosePrice : currentPrice;
        List<BigDecimal> gapPercentages = indicators.calculateGapPercentages(twoWeekBars);
        BigDecimal avgGapPct = average(gapPercentages);
        BigDecimal negativeGapPctAverage = indicators.calculateAverageNegativeGapPct(gapPercentages);
        BigDecimal gapVolatility = indicators.calculateGapVolatility(gapPercentages);
        BigDecimal averageIntradayDipPct = indicators.calculateAverageIntradayDipPct(twoWeekBars);

        BigDecimal expectedDipPct;
        String baseAdjustmentReason;
        if (twoWeekBars.size() < 10 || gapPercentages.isEmpty()) {
            expectedDipPct = SHORT_TERM_FALLBACK_DIP_PCT;
            baseAdjustmentReason = "Used fallback 0.75% discount because there was not enough two-week data.";
        } else {
            expectedDipPct = negativeGapPctAverage.abs()
                    .add(gapVolatility.multiply(SHORT_TERM_VOL_WEIGHT))
                    .add(averageIntradayDipPct.abs().multiply(SHORT_TERM_VOL_WEIGHT));
            expectedDipPct = indicators.clamp(expectedDipPct, SHORT_TERM_MIN_DIP_PCT, SHORT_TERM_MAX_DIP_PCT);
            baseAdjustmentReason = "Range entry: using the lower of two-week support and behavior-adjusted discounted price.";
        }

        BigDecimal behaviorAdjustedBasePrice = Monetary.round(effectiveMarketPrice.multiply(BigDecimal.ONE.subtract(expectedDipPct)));
        BigDecimal baseBuyPrice = twoWeekLow.get().min(behaviorAdjustedBasePrice);
        BigDecimal currentVolume = prices.getLast().volume();
        boolean strongVolume = currentVolume.compareTo(avgVolume10Day.get().multiply(ONE_POINT_FIVE)) >= 0;
        ShortTermMarketMode shortTermMarketMode;
        String warning = "";

        if (currentPrice.compareTo(sma20.get()) < 0 && currentPrice.compareTo(twoWeekLow.get()) < 0) {
            shortTermMarketMode = ShortTermMarketMode.WEAK_AVOID;
            baseBuyPrice = behaviorAdjustedBasePrice;
            warning = "Short-term trend is weak. Price is below SMA20 and below two-week support.";
        } else if (currentPrice.compareTo(twoWeekLow.get()) < 0) {
            shortTermMarketMode = ShortTermMarketMode.BREAKDOWN;
            baseBuyPrice = Monetary.round(currentPrice.multiply(BigDecimal.ONE.subtract(expectedDipPct)));
            warning = "Price broke below two-week support. Wait for confirmation before buying.";
        } else if (currentPrice.compareTo(twoWeekHigh.get()) > 0
                && strongVolume
                && currentPrice.compareTo(sma20.get()) > 0) {
            shortTermMarketMode = ShortTermMarketMode.SHORT_TERM_BREAKOUT;
            baseBuyPrice = currentPrice;
            baseAdjustmentReason = "Short-term breakout confirmed with volume and price above SMA20. Using current price.";
        } else if (currentPrice.compareTo(twoWeekHigh.get()) > 0
                && (!strongVolume || currentPrice.compareTo(sma20.get()) <= 0)) {
            shortTermMarketMode = ShortTermMarketMode.OVEREXTENDED;
            baseBuyPrice = behaviorAdjustedBasePrice;
            warning = "Price is above two-week high without strong breakout confirmation. Waiting for discounted entry.";
        } else if (currentPrice.compareTo(twoWeekLow.get()) >= 0 && currentPrice.compareTo(twoWeekHigh.get()) <= 0) {
            shortTermMarketMode = ShortTermMarketMode.RANGE_ENTRY;
            baseBuyPrice = twoWeekLow.get().min(behaviorAdjustedBasePrice);
            baseAdjustmentReason = "Range entry: using the lower of two-week support and behavior-adjusted discounted price.";
        } else {
            shortTermMarketMode = ShortTermMarketMode.RANGE_ENTRY;
            baseBuyPrice = twoWeekLow.get().min(behaviorAdjustedBasePrice);
            baseAdjustmentReason = "Default range entry using behavior-adjusted discounted price.";
        }

        if (shortTermMarketMode != ShortTermMarketMode.SHORT_TERM_BREAKOUT
                && baseBuyPrice.compareTo(effectiveMarketPrice) >= 0) {
            baseBuyPrice = behaviorAdjustedBasePrice;
            baseAdjustmentReason += " Final adjustment applied to avoid buying at or above latest close.";
        }
        if (baseBuyPrice.compareTo(BigDecimal.ZERO) <= 0) {
            baseBuyPrice = Monetary.round(effectiveMarketPrice.multiply(ZERO_POINT_NINE_NINE_TWO_FIVE));
            warning = appendWarning(warning, "Invalid base buy price corrected using fallback 0.75% discount.");
        }
        BigDecimal lowerClamp = Monetary.round(twoWeekLow.get().multiply(NINETY_PERCENT));
        if (baseBuyPrice.compareTo(lowerClamp) < 0) {
            baseBuyPrice = lowerClamp;
            warning = appendWarning(warning, "Base buy price was too far below two-week support and was clamped.");
        }

        baseBuyPrice = floorPrice(baseBuyPrice);
        behaviorAdjustedBasePrice = floorPrice(behaviorAdjustedBasePrice);

        BigDecimal buy1 = floorPrice(baseBuyPrice.subtract(atr14.get()));
        BigDecimal buy2 = floorPrice(baseBuyPrice.subtract(atr14.get().multiply(TWO)));
        BigDecimal stopLoss = floorPrice(buy2.subtract(atr14.get()));
        BigDecimal target1 = Monetary.round(baseBuyPrice.add(atr14.get().multiply(ONE_POINT_FIVE)));
        BigDecimal target2 = Monetary.round(baseBuyPrice.add(atr14.get().multiply(THREE)));
        BigDecimal recommendedSell = target1.max(twoWeekHigh.get());
        if (recommendedSell.compareTo(baseBuyPrice) <= 0) {
            recommendedSell = Monetary.round(baseBuyPrice.add(atr14.get().multiply(TWO)));
            warning = appendWarning(warning, "Sell target adjusted because calculated target was not above base buy price.");
        }

        BigDecimal riskRewardRatio = riskRewardRatio(baseBuyPrice, stopLoss, recommendedSell);
        String trendStatus = currentPrice.compareTo(sma20.get()) > 0 ? "Bullish" : "Weak / Avoid";
        String volumeStatus = strongVolume ? "Strong" : "Normal / Weak";

        int confidence = 0;
        if (currentPrice.compareTo(sma20.get()) > 0) confidence += 25;
        if (shortTermMarketMode == ShortTermMarketMode.RANGE_ENTRY) confidence += 20;
        if (shortTermMarketMode == ShortTermMarketMode.SHORT_TERM_BREAKOUT) confidence += 20;
        if (strongVolume) confidence += 15;
        if (riskRewardRatio.compareTo(TWO) >= 0) confidence += 15;
        if (baseBuyPrice.compareTo(effectiveMarketPrice) < 0) confidence += 10;
        if (shortTermMarketMode == ShortTermMarketMode.OVEREXTENDED) confidence -= 20;
        if (shortTermMarketMode == ShortTermMarketMode.BREAKDOWN) confidence -= 25;
        if (shortTermMarketMode == ShortTermMarketMode.WEAK_AVOID) confidence -= 35;
        confidence = Math.max(0, Math.min(100, confidence));

        RecommendationAction action;
        if (shortTermMarketMode == ShortTermMarketMode.WEAK_AVOID) {
            action = RecommendationAction.AVOID;
        } else if (shortTermMarketMode == ShortTermMarketMode.BREAKDOWN
                || shortTermMarketMode == ShortTermMarketMode.OVEREXTENDED) {
            action = RecommendationAction.WATCH;
        } else if (confidence < 50) {
            action = RecommendationAction.AVOID;
        } else if (confidence >= 75
                && (shortTermMarketMode == ShortTermMarketMode.RANGE_ENTRY
                || shortTermMarketMode == ShortTermMarketMode.SHORT_TERM_BREAKOUT)) {
            action = RecommendationAction.BUY;
        } else {
            action = RecommendationAction.WATCH;
        }

        return new StrategyRecommendation(
                symbol,
                RecommendationType.SHORT_TERM,
                baseBuyPrice,
                BigDecimal.ZERO,
                effectiveMarketPrice,
                lastClosePrice == null ? BigDecimal.ZERO : lastClosePrice,
                currentPrice,
                twoWeekLow.get(),
                twoWeekHigh.get(),
                expectedDipPct,
                behaviorAdjustedBasePrice,
                baseBuyPrice,
                MarketMode.ACCUMULATION,
                shortTermMarketMode,
                baseAdjustmentReason,
                avgGapPct,
                negativeGapPctAverage,
                gapVolatility,
                averageIntradayDipPct,
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
                action,
                warning,
                shortTermMarketMode == ShortTermMarketMode.BREAKDOWN
        );
    }

    public StrategyRecommendation generateShortTermRecommendation(String symbol, List<MarketBar> prices, BigDecimal currentPrice) {
        return generateShortTermRecommendation(symbol, prices, currentPrice, null);
    }

    public StrategyRecommendation generateLongTermRecommendation(
            String symbol,
            List<MarketBar> prices,
            BigDecimal currentPrice,
            BigDecimal lastClosePrice
    ) {
        if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return StrategyRecommendation.unavailable(symbol, RecommendationType.LONG_TERM, "Current price is missing.");
        }
        if (prices == null || prices.size() < 9) {
            return StrategyRecommendation.unavailable(symbol, RecommendationType.LONG_TERM,
                    "At least 9 daily bars are required for the long-term recommendation.");
        }

        List<MarketBar> sixMonthBars = tail(prices, Math.min(prices.size(), 126));
        List<MarketBar> oneYearBars = tail(prices, Math.min(prices.size(), 252));
        Optional<BigDecimal> sixMonthLow = indicators.calculateLow(sixMonthBars);
        Optional<BigDecimal> sixMonthHigh = indicators.calculateHigh(sixMonthBars);
        Optional<BigDecimal> oneYearHigh = indicators.calculateHigh(oneYearBars);
        Optional<BigDecimal> sma50 = indicators.calculateSMA(prices, Math.min(50, prices.size()));
        Optional<BigDecimal> sma200 = prices.size() >= 200
                ? indicators.calculateSMA(prices, 200)
                : indicators.calculateSMA(prices, Math.min(50, prices.size()));
        Optional<BigDecimal> atr14 = indicators.calculateATR(prices, Math.min(14, Math.max(1, prices.size() - 1)));
        Optional<BigDecimal> avgVolume30Day = indicators.calculateAverageVolume(prices, Math.min(30, prices.size()));
        if (sixMonthLow.isEmpty() || sixMonthHigh.isEmpty() || oneYearHigh.isEmpty()
                || sma50.isEmpty() || sma200.isEmpty() || atr14.isEmpty() || avgVolume30Day.isEmpty()) {
            return StrategyRecommendation.unavailable(symbol, RecommendationType.LONG_TERM,
                    "Long-term recommendation needs enough data for six-month range, ATR, SMA, and 30-day volume.");
        }

        BigDecimal effectiveMarketPrice = validPrice(lastClosePrice) ? lastClosePrice : currentPrice;
        BigDecimal originalCalculatedBasePrice = calculateOriginalLongTermBase(
                effectiveMarketPrice, sixMonthLow.get(), sixMonthHigh.get(), sma50.get());
        BigDecimal marketAwareBasePrice = effectiveMarketPrice.min(originalCalculatedBasePrice);

        List<MarketBar> twoWeekBars = tail(prices, Math.min(prices.size(), 14));
        List<BigDecimal> gapPercentages = indicators.calculateGapPercentages(twoWeekBars);
        BigDecimal avgGapPct = average(gapPercentages);
        BigDecimal negativeGapPctAverage = indicators.calculateAverageNegativeGapPct(gapPercentages);
        BigDecimal gapVolatility = indicators.calculateGapVolatility(gapPercentages);
        BigDecimal averageIntradayDipPct = indicators.calculateAverageIntradayDipPct(twoWeekBars);

        BigDecimal expectedDipPct;
        String baseAdjustmentReason;
        if (twoWeekBars.size() < 10 || gapPercentages.isEmpty()) {
            expectedDipPct = new BigDecimal("0.01");
            baseAdjustmentReason = "Used fallback 1% discount because there was not enough two-week historical data.";
        } else {
            expectedDipPct = negativeGapPctAverage.abs()
                    .add(gapVolatility.multiply(HALF))
                    .add(averageIntradayDipPct.abs().multiply(HALF));
            expectedDipPct = indicators.clamp(expectedDipPct, MIN_DIP_PCT, MAX_DIP_PCT);
            baseAdjustmentReason = "Base buy price discounted using recent two-week gap and intraday dip behavior.";
        }

        BigDecimal behaviorAdjustedBasePrice = Monetary.round(effectiveMarketPrice.multiply(BigDecimal.ONE.subtract(expectedDipPct)));
        BigDecimal adjustedBaseBuyPrice = marketAwareBasePrice.min(behaviorAdjustedBasePrice);

        BigDecimal currentVolume = prices.getLast().volume();
        MarketMode marketMode;
        String warningMessage = "";

        if (effectiveMarketPrice.compareTo(sixMonthHigh.get()) > 0
                && currentVolume.compareTo(avgVolume30Day.get()) >= 0
                && sma50.get().compareTo(sma200.get()) > 0) {
            marketMode = MarketMode.BREAKOUT;
            adjustedBaseBuyPrice = effectiveMarketPrice;
            baseAdjustmentReason = "Breakout mode: using latest market price because price broke above six-month high with trend and volume confirmation.";
        } else if (effectiveMarketPrice.compareTo(sixMonthHigh.get()) > 0
                && currentVolume.compareTo(avgVolume30Day.get()) < 0) {
            marketMode = MarketMode.OVEREXTENDED;
            adjustedBaseBuyPrice = behaviorAdjustedBasePrice;
            warningMessage = "Price is above six-month high without strong volume confirmation. Waiting for a discounted entry.";
        } else if (effectiveMarketPrice.compareTo(sma200.get()) < 0
                && sma50.get().compareTo(sma200.get()) < 0) {
            marketMode = MarketMode.WEAK_AVOID;
            adjustedBaseBuyPrice = behaviorAdjustedBasePrice;
            warningMessage = "Long-term trend is weak. Price is below SMA200 and SMA50 is below SMA200.";
        } else if (effectiveMarketPrice.compareTo(sma50.get()) <= 0
                || effectiveMarketPrice.compareTo(sixMonthLow.get().multiply(BigDecimal.ONE.add(FIVE_PERCENT))) <= 0) {
            marketMode = MarketMode.VALUE_OR_DISCOUNT;
            adjustedBaseBuyPrice = marketAwareBasePrice.min(behaviorAdjustedBasePrice);
            baseAdjustmentReason = "Base buy price discounted using recent two-week gap and intraday dip behavior.";
        } else if (effectiveMarketPrice.compareTo(sma50.get()) > 0
                && effectiveMarketPrice.compareTo(sixMonthLow.get().multiply(BigDecimal.ONE.add(FIVE_PERCENT))) >= 0
                && effectiveMarketPrice.compareTo(sixMonthHigh.get().multiply(ZERO_POINT_NINE_FIVE)) <= 0) {
            marketMode = MarketMode.ACCUMULATION;
            adjustedBaseBuyPrice = marketAwareBasePrice.min(behaviorAdjustedBasePrice);
            baseAdjustmentReason = "Accumulation mode: using discounted entry based on recent close-to-open and intraday dip behavior.";
        } else {
            marketMode = MarketMode.ACCUMULATION;
            adjustedBaseBuyPrice = marketAwareBasePrice.min(behaviorAdjustedBasePrice);
            baseAdjustmentReason = "Default accumulation mode with behavior-adjusted discounted entry.";
        }

        if (marketMode != MarketMode.BREAKOUT && adjustedBaseBuyPrice.compareTo(effectiveMarketPrice) >= 0) {
            adjustedBaseBuyPrice = behaviorAdjustedBasePrice;
            baseAdjustmentReason += " Final adjustment applied to avoid buying at or above latest close.";
        }
        if (adjustedBaseBuyPrice.compareTo(BigDecimal.ZERO) <= 0) {
            adjustedBaseBuyPrice = Monetary.round(effectiveMarketPrice.multiply(new BigDecimal("0.99")));
            warningMessage = appendWarning(warningMessage, "Invalid adjusted base price corrected using 1% discount.");
        }
        BigDecimal lowerClamp = Monetary.round(sixMonthLow.get().multiply(ZERO_POINT_EIGHTY_FIVE));
        if (adjustedBaseBuyPrice.compareTo(lowerClamp) < 0) {
            adjustedBaseBuyPrice = lowerClamp;
            warningMessage = appendWarning(warningMessage, "Adjusted base price was too far below six-month support and was clamped.");
        }

        adjustedBaseBuyPrice = floorPrice(adjustedBaseBuyPrice);
        behaviorAdjustedBasePrice = floorPrice(behaviorAdjustedBasePrice);
        BigDecimal buy1 = floorPrice(adjustedBaseBuyPrice.subtract(atr14.get().multiply(ONE_POINT_FIVE)));
        BigDecimal buy2 = floorPrice(adjustedBaseBuyPrice.subtract(atr14.get().multiply(THREE)));
        BigDecimal stopLoss = floorPrice(buy2.subtract(atr14.get().multiply(ONE_POINT_FIVE)));
        BigDecimal target1 = Monetary.round(adjustedBaseBuyPrice.add(atr14.get().multiply(TWO)));
        BigDecimal target2 = Monetary.round(adjustedBaseBuyPrice.add(atr14.get().multiply(FOUR)));
        BigDecimal longTermSellPrice = oneYearHigh.get().min(sixMonthHigh.get().max(target2));
        if (longTermSellPrice.compareTo(adjustedBaseBuyPrice) <= 0) {
            longTermSellPrice = Monetary.round(adjustedBaseBuyPrice.add(atr14.get().multiply(THREE)));
            warningMessage = appendWarning(warningMessage, "Sell target adjusted because calculated target was not above base buy price.");
        }

        BigDecimal riskRewardRatio = riskRewardRatio(adjustedBaseBuyPrice, stopLoss, longTermSellPrice);

        int confidence = 0;
        if (sma50.get().compareTo(sma200.get()) > 0) confidence += 25;
        if (effectiveMarketPrice.compareTo(sma50.get()) > 0) confidence += 20;
        if (marketMode == MarketMode.VALUE_OR_DISCOUNT) confidence += 20;
        if (marketMode == MarketMode.BREAKOUT) confidence += 20;
        if (riskRewardRatio.compareTo(TWO_POINT_FIVE) >= 0) confidence += 15;
        if (currentVolume.compareTo(avgVolume30Day.get()) >= 0) confidence += 10;
        if (adjustedBaseBuyPrice.compareTo(effectiveMarketPrice) < 0) confidence += 10;
        if (marketMode == MarketMode.OVEREXTENDED) confidence -= 20;
        if (marketMode == MarketMode.WEAK_AVOID) confidence -= 30;
        confidence = Math.max(0, Math.min(100, confidence));

        RecommendationAction action;
        if (marketMode == MarketMode.WEAK_AVOID) {
            action = RecommendationAction.AVOID;
        } else if (marketMode == MarketMode.OVEREXTENDED) {
            action = RecommendationAction.WATCH;
        } else if (confidence < 50) {
            action = RecommendationAction.AVOID;
        } else if (confidence >= 75 && marketMode != MarketMode.OVEREXTENDED) {
            action = RecommendationAction.BUY;
        } else {
            action = RecommendationAction.WATCH;
        }

        String trendStatus;
        if (sma50.get().compareTo(sma200.get()) > 0 && effectiveMarketPrice.compareTo(sma50.get()) > 0) {
            trendStatus = "Strong Bullish";
        } else if (effectiveMarketPrice.compareTo(sma200.get()) > 0) {
            trendStatus = "Neutral";
        } else {
            trendStatus = "Weak";
        }
        String volumeStatus = currentVolume.compareTo(avgVolume30Day.get()) >= 0 ? "Strong" : "Normal / Weak";

        return new StrategyRecommendation(
                symbol,
                RecommendationType.LONG_TERM,
                adjustedBaseBuyPrice,
                originalCalculatedBasePrice,
                effectiveMarketPrice,
                lastClosePrice == null ? BigDecimal.ZERO : lastClosePrice,
                currentPrice,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                expectedDipPct,
                behaviorAdjustedBasePrice,
                adjustedBaseBuyPrice,
                marketMode,
                ShortTermMarketMode.RANGE_ENTRY,
                baseAdjustmentReason,
                avgGapPct,
                negativeGapPctAverage,
                gapVolatility,
                averageIntradayDipPct,
                buy1,
                buy2,
                stopLoss,
                longTermSellPrice,
                target1,
                target2,
                trendStatus,
                volumeStatus,
                riskRewardRatio,
                confidence,
                action,
                warningMessage,
                false
        );
    }

    public StrategyRecommendation generateLongTermRecommendation(String symbol, List<MarketBar> prices, BigDecimal currentPrice) {
        return generateLongTermRecommendation(symbol, prices, currentPrice, null);
    }

    private BigDecimal calculateOriginalLongTermBase(
            BigDecimal effectiveMarketPrice,
            BigDecimal sixMonthLow,
            BigDecimal sixMonthHigh,
            BigDecimal sma50
    ) {
        if (effectiveMarketPrice.compareTo(sixMonthLow.multiply(BigDecimal.ONE.add(FIVE_PERCENT))) <= 0) {
            return sixMonthLow;
        }
        if (effectiveMarketPrice.compareTo(sixMonthHigh) > 0) {
            return effectiveMarketPrice;
        }
        return validPrice(sma50) ? sma50 : effectiveMarketPrice;
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

    private RecommendationAction actionForShortTerm(int confidence) {
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

    private String appendWarning(String current, String extra) {
        if (current == null || current.isBlank()) {
            return extra;
        }
        return current + " " + extra;
    }

    private boolean validPrice(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private BigDecimal average(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            sum = sum.add(value);
        }
        return sum.divide(BigDecimal.valueOf(values.size()), 4, RoundingMode.HALF_UP);
    }
}
