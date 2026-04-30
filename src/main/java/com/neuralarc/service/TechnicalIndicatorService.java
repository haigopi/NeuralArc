package com.neuralarc.service;

import com.neuralarc.model.MarketBar;
import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TechnicalIndicatorService {
    private static final int CALC_SCALE = 8;

    public Optional<BigDecimal> calculateSMA(List<MarketBar> prices, int period) {
        if (prices == null || prices.size() < period || period <= 0) {
            return Optional.empty();
        }
        List<MarketBar> window = prices.subList(prices.size() - period, prices.size());
        BigDecimal sum = BigDecimal.ZERO;
        for (MarketBar bar : window) {
            sum = sum.add(bar.close());
        }
        return Optional.of(Monetary.round(sum.divide(BigDecimal.valueOf(period), CALC_SCALE, RoundingMode.HALF_UP)));
    }

    public Optional<BigDecimal> calculateATR(List<MarketBar> prices, int period) {
        if (prices == null || prices.size() < period + 1 || period <= 0) {
            return Optional.empty();
        }
        List<MarketBar> window = prices.subList(prices.size() - (period + 1), prices.size());
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 1; i < window.size(); i++) {
            MarketBar current = window.get(i);
            MarketBar previous = window.get(i - 1);
            BigDecimal highLow = current.high().subtract(current.low()).abs();
            BigDecimal highPrevClose = current.high().subtract(previous.close()).abs();
            BigDecimal lowPrevClose = current.low().subtract(previous.close()).abs();
            BigDecimal trueRange = highLow.max(highPrevClose).max(lowPrevClose);
            sum = sum.add(trueRange);
        }
        return Optional.of(Monetary.round(sum.divide(BigDecimal.valueOf(period), CALC_SCALE, RoundingMode.HALF_UP)));
    }

    public Optional<BigDecimal> calculateAverageVolume(List<MarketBar> prices, int period) {
        if (prices == null || prices.size() < period || period <= 0) {
            return Optional.empty();
        }
        List<MarketBar> window = prices.subList(prices.size() - period, prices.size());
        BigDecimal sum = BigDecimal.ZERO;
        for (MarketBar bar : window) {
            sum = sum.add(bar.volume());
        }
        return Optional.of(Monetary.round(sum.divide(BigDecimal.valueOf(period), CALC_SCALE, RoundingMode.HALF_UP)));
    }

    public Optional<BigDecimal> calculateHigh(List<MarketBar> prices) {
        if (prices == null || prices.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal high = prices.getFirst().high();
        for (MarketBar bar : prices) {
            if (bar.high().compareTo(high) > 0) {
                high = bar.high();
            }
        }
        return Optional.of(Monetary.round(high));
    }

    public Optional<BigDecimal> calculateLow(List<MarketBar> prices) {
        if (prices == null || prices.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal low = prices.getFirst().low();
        for (MarketBar bar : prices) {
            if (bar.low().compareTo(low) < 0) {
                low = bar.low();
            }
        }
        return Optional.of(Monetary.round(low));
    }

    public List<BigDecimal> calculateGapPercentages(List<MarketBar> candles) {
        List<BigDecimal> gaps = new ArrayList<>();
        if (candles == null || candles.size() < 2) {
            return gaps;
        }
        for (int i = 1; i < candles.size(); i++) {
            BigDecimal previousClose = candles.get(i - 1).close();
            BigDecimal currentOpen = candles.get(i).open();
            if (previousClose.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            gaps.add(currentOpen.subtract(previousClose)
                    .divide(previousClose, CALC_SCALE, RoundingMode.HALF_UP));
        }
        return gaps;
    }

    public BigDecimal calculateAverageNegativeGapPct(List<BigDecimal> gapPercentages) {
        if (gapPercentages == null || gapPercentages.isEmpty()) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (BigDecimal gap : gapPercentages) {
            if (gap.compareTo(BigDecimal.ZERO) < 0) {
                sum = sum.add(gap);
                count++;
            }
        }
        if (count == 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return sum.divide(BigDecimal.valueOf(count), CALC_SCALE, RoundingMode.HALF_UP).setScale(4, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateGapVolatility(List<BigDecimal> gapPercentages) {
        if (gapPercentages == null || gapPercentages.isEmpty()) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        BigDecimal mean = BigDecimal.ZERO;
        for (BigDecimal gap : gapPercentages) {
            mean = mean.add(gap);
        }
        mean = mean.divide(BigDecimal.valueOf(gapPercentages.size()), CALC_SCALE, RoundingMode.HALF_UP);
        BigDecimal variance = BigDecimal.ZERO;
        for (BigDecimal gap : gapPercentages) {
            BigDecimal diff = gap.subtract(mean);
            variance = variance.add(diff.multiply(diff));
        }
        variance = variance.divide(BigDecimal.valueOf(gapPercentages.size()), CALC_SCALE, RoundingMode.HALF_UP);
        double stdDev = Math.sqrt(variance.doubleValue());
        return BigDecimal.valueOf(stdDev).setScale(4, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateAverageIntradayDipPct(List<MarketBar> candles) {
        if (candles == null || candles.isEmpty()) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (MarketBar candle : candles) {
            if (candle.open().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal dip = candle.low().subtract(candle.open())
                    .divide(candle.open(), CALC_SCALE, RoundingMode.HALF_UP);
            sum = sum.add(dip);
            count++;
        }
        if (count == 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return sum.divide(BigDecimal.valueOf(count), CALC_SCALE, RoundingMode.HALF_UP).setScale(4, RoundingMode.HALF_UP);
    }

    public BigDecimal clamp(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (value == null) {
            return min;
        }
        if (value.compareTo(min) < 0) {
            return min;
        }
        if (value.compareTo(max) > 0) {
            return max;
        }
        return value;
    }
}
