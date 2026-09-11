package com.neuralarc.analytics;

import java.util.Arrays;

/**
 * Indicator series for price charts: moving averages, RSI, MACD and accumulation/distribution.
 *
 * <p>Display-only analytics, computed in double precision from daily bars. They never feed an order,
 * so the {@code BigDecimal} rule for monetary values does not apply. Positions before an indicator has
 * enough history are {@code NaN}, which the chart simply does not draw.
 */
public final class ChartIndicators {
    private ChartIndicators() {
    }

    /** MACD line, its signal line, and the histogram between them. */
    public record Macd(double[] line, double[] signal, double[] histogram) {
    }

    /** Simple moving average over {@code period} values. */
    public static double[] sma(double[] values, int period) {
        double[] out = undefined(values.length);
        if (period <= 0) {
            return out;
        }
        double sum = 0;
        for (int i = 0; i < values.length; i++) {
            sum += values[i];
            if (i >= period) {
                sum -= values[i - period];
            }
            if (i >= period - 1) {
                out[i] = sum / period;
            }
        }
        return out;
    }

    /**
     * Exponential moving average. Seeded with the simple average of the first {@code period} values
     * after any leading gaps, then each value moves toward the new price by {@code 2 / (period + 1)}.
     */
    public static double[] ema(double[] values, int period) {
        double[] out = undefined(values.length);
        int start = firstDefined(values);
        if (period <= 0 || start < 0 || values.length - start < period) {
            return out;
        }
        double sum = 0;
        for (int i = start; i < start + period; i++) {
            sum += values[i];
        }
        double previous = sum / period;
        out[start + period - 1] = previous;
        double weight = 2.0 / (period + 1);
        for (int i = start + period; i < values.length; i++) {
            previous += weight * (values[i] - previous);
            out[i] = previous;
        }
        return out;
    }

    /** Wilder's Relative Strength Index, 0 to 100. */
    public static double[] rsi(double[] closes, int period) {
        double[] out = undefined(closes.length);
        if (period <= 0 || closes.length <= period) {
            return out;
        }
        double gain = 0;
        double loss = 0;
        for (int i = 1; i <= period; i++) {
            double change = closes[i] - closes[i - 1];
            if (change > 0) {
                gain += change;
            } else {
                loss -= change;
            }
        }
        gain /= period;
        loss /= period;
        out[period] = rsiValue(gain, loss);
        for (int i = period + 1; i < closes.length; i++) {
            double change = closes[i] - closes[i - 1];
            gain = (gain * (period - 1) + Math.max(change, 0)) / period;
            loss = (loss * (period - 1) + Math.max(-change, 0)) / period;
            out[i] = rsiValue(gain, loss);
        }
        return out;
    }

    /** MACD: the fast EMA minus the slow EMA, its signal EMA, and the histogram between them. */
    public static Macd macd(double[] closes, int fast, int slow, int signalPeriod) {
        double[] fastEma = ema(closes, fast);
        double[] slowEma = ema(closes, slow);
        double[] line = undefined(closes.length);
        for (int i = 0; i < closes.length; i++) {
            if (!Double.isNaN(fastEma[i]) && !Double.isNaN(slowEma[i])) {
                line[i] = fastEma[i] - slowEma[i];
            }
        }
        double[] signal = ema(line, signalPeriod);
        double[] histogram = undefined(closes.length);
        for (int i = 0; i < closes.length; i++) {
            if (!Double.isNaN(line[i]) && !Double.isNaN(signal[i])) {
                histogram[i] = line[i] - signal[i];
            }
        }
        return new Macd(line, signal, histogram);
    }

    /**
     * Chaikin accumulation/distribution line: a running total that adds a day's volume when the close
     * sits at the high, subtracts it when the close sits at the low, and weights the days in between.
     */
    public static double[] accumulationDistribution(double[] highs, double[] lows, double[] closes, double[] volumes) {
        double[] out = new double[closes.length];
        double total = 0;
        for (int i = 0; i < closes.length; i++) {
            double range = highs[i] - lows[i];
            double multiplier = range <= 0 ? 0 : ((closes[i] - lows[i]) - (highs[i] - closes[i])) / range;
            total += multiplier * volumes[i];
            out[i] = total;
        }
        return out;
    }

    private static double rsiValue(double averageGain, double averageLoss) {
        if (averageLoss == 0) {
            return averageGain == 0 ? 50 : 100;
        }
        return 100 - 100 / (1 + averageGain / averageLoss);
    }

    private static double[] undefined(int length) {
        double[] out = new double[length];
        Arrays.fill(out, Double.NaN);
        return out;
    }

    private static int firstDefined(double[] values) {
        for (int i = 0; i < values.length; i++) {
            if (!Double.isNaN(values[i])) {
                return i;
            }
        }
        return -1;
    }
}
