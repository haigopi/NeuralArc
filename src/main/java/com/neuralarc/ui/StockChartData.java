package com.neuralarc.ui;

import com.neuralarc.analytics.ChartIndicators;
import com.neuralarc.analytics.SwingPoints;
import com.neuralarc.model.MarketBar;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything the stock chart draws, computed once off the UI thread from Alpaca's daily bars: prices,
 * the indicator series, turning points and the strategy's own levels.
 */
record StockChartData(
        String symbol,
        List<LocalDate> dates,
        double[] opens,
        double[] highs,
        double[] lows,
        double[] closes,
        double[] volumes,
        Map<Integer, double[]> emas,
        double[] rsi,
        ChartIndicators.Macd macd,
        double[] accumulationDistribution,
        double[] volumeAverage,
        List<SwingPoints.Swing> swings,
        List<StockChartLevels.Level> levels
) {
    static final int[] EMA_PERIODS = {9, 13, 20, 50, 100, 200};
    static final int RSI_PERIOD = 14;
    static final int VOLUME_AVERAGE_PERIOD = 50;
    /** Bars each side a high or low must beat to count as a turning point: about two trading weeks. */
    static final int SWING_WINDOW = 10;

    static StockChartData from(String symbol, List<MarketBar> bars, List<StockChartLevels.Level> levels) {
        List<MarketBar> usable = new ArrayList<>();
        if (bars != null) {
            for (MarketBar bar : bars) {
                if (bar != null && positive(bar.close()) && positive(bar.high()) && positive(bar.low())
                        && tradingDate(bar.timestamp()) != null) {
                    usable.add(bar);
                }
            }
        }
        usable.sort(Comparator.comparing(MarketBar::timestamp));
        int count = usable.size();
        List<LocalDate> dates = new ArrayList<>(count);
        double[] opens = new double[count];
        double[] highs = new double[count];
        double[] lows = new double[count];
        double[] closes = new double[count];
        double[] volumes = new double[count];
        for (int i = 0; i < count; i++) {
            MarketBar bar = usable.get(i);
            dates.add(tradingDate(bar.timestamp()));
            closes[i] = bar.close().doubleValue();
            opens[i] = positive(bar.open()) ? bar.open().doubleValue() : (i > 0 ? closes[i - 1] : closes[i]);
            highs[i] = Math.max(bar.high().doubleValue(), Math.max(opens[i], closes[i]));
            lows[i] = Math.min(bar.low().doubleValue(), Math.min(opens[i], closes[i]));
            volumes[i] = bar.volume() == null ? 0 : Math.max(0, bar.volume().doubleValue());
        }
        Map<Integer, double[]> emas = new LinkedHashMap<>();
        for (int period : EMA_PERIODS) {
            emas.put(period, ChartIndicators.ema(closes, period));
        }
        return new StockChartData(
                symbol,
                List.copyOf(dates),
                opens,
                highs,
                lows,
                closes,
                volumes,
                Collections.unmodifiableMap(emas),
                ChartIndicators.rsi(closes, RSI_PERIOD),
                ChartIndicators.macd(closes, 12, 26, 9),
                ChartIndicators.accumulationDistribution(highs, lows, closes, volumes),
                ChartIndicators.sma(volumes, VOLUME_AVERAGE_PERIOD),
                List.copyOf(SwingPoints.find(highs, lows, SWING_WINDOW)),
                levels == null ? List.of() : List.copyOf(levels)
        );
    }

    int size() {
        return closes.length;
    }

    boolean isEmpty() {
        return closes.length == 0;
    }

    int lastIndex() {
        return closes.length - 1;
    }

    /** Alpaca stamps a daily bar at midnight New York time, so its first ten characters are the trading date. */
    private static LocalDate tradingDate(String timestamp) {
        if (timestamp == null || timestamp.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(timestamp.substring(0, 10));
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }
}
