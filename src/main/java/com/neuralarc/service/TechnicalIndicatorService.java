package com.neuralarc.service;

import com.neuralarc.model.MarketBar;
import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
}
