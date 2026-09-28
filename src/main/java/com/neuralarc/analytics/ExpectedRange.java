package com.neuralarc.analytics;

import com.neuralarc.util.Monetary;

import java.math.BigDecimal;

/**
 * How far a stock could plausibly travel from here, at the pace it has actually been moving.
 *
 * <p>This is not a forecast and deliberately produces no target price. It is the same arithmetic the
 * options market uses for an expected move, done from daily closes: measure how much the stock has
 * varied day to day, scale that by the square root of the horizon, and state the band that variation
 * implies. About two thirds of outcomes historically land inside the one-sigma band and about 95%
 * inside two — which is a statement about spread, not direction.
 *
 * <p>Bands are lognormal ({@code price × e^±kσ√t}), so the lower edge approaches zero without ever
 * crossing it, and a rise and the matching fall are the same distance in percentage terms.
 */
public final class ExpectedRange {
    /** Sessions of history the volatility is measured over: about a trading month. */
    public static final int DEFAULT_LOOKBACK_SESSIONS = 20;
    /** Fewer returns than this and the volatility says more about the sample than the stock. */
    public static final int MINIMUM_RETURNS = 5;

    /**
     * @param dailySigma the daily standard deviation of log returns, as a fraction (0.02 = 2%)
     * @param sessions   trading days ahead the band covers
     */
    public record Projection(
            BigDecimal lastPrice,
            double dailySigma,
            int sessions,
            BigDecimal oneSigmaLow,
            BigDecimal oneSigmaHigh,
            BigDecimal twoSigmaLow,
            BigDecimal twoSigmaHigh
    ) {
        public static Projection unknown() {
            return new Projection(BigDecimal.ZERO, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO);
        }

        public boolean known() {
            return dailySigma > 0 && sessions > 0 && lastPrice.signum() > 0;
        }

        /** The horizon's own sigma, as a percentage of today's price. */
        public double horizonSigmaPercent() {
            return known() ? (Math.exp(dailySigma * Math.sqrt(sessions)) - 1) * 100 : 0;
        }

        /** A sentence that says what the band is and, as plainly, what it is not. */
        public String describe() {
            if (!known()) {
                return "Not enough recent prices to measure how far this stock usually travels.";
            }
            return String.format(java.util.Locale.US,
                    "At the pace of the last %d sessions (%.1f%% a day), about two thirds of outcomes over the next"
                            + " %d sessions fall between $%s and $%s, and about 95%% between $%s and $%s."
                            + " A range, not a target: it says nothing about which way.",
                    DEFAULT_LOOKBACK_SESSIONS, dailySigma * 100, sessions,
                    oneSigmaLow.toPlainString(), oneSigmaHigh.toPlainString(),
                    twoSigmaLow.toPlainString(), twoSigmaHigh.toPlainString());
        }
    }

    private ExpectedRange() {
    }

    /**
     * The standard deviation of the last {@code lookback} daily log returns, or 0 when there are too
     * few to mean anything.
     */
    public static double dailySigma(double[] closes, int lookback) {
        if (closes == null || closes.length < MINIMUM_RETURNS + 1) {
            return 0;
        }
        int from = Math.max(1, closes.length - Math.max(MINIMUM_RETURNS, lookback));
        double sum = 0;
        int count = 0;
        for (int i = from; i < closes.length; i++) {
            if (closes[i] > 0 && closes[i - 1] > 0) {
                sum += Math.log(closes[i] / closes[i - 1]);
                count++;
            }
        }
        if (count < MINIMUM_RETURNS) {
            return 0;
        }
        double mean = sum / count;
        double squares = 0;
        for (int i = from; i < closes.length; i++) {
            if (closes[i] > 0 && closes[i - 1] > 0) {
                double deviation = Math.log(closes[i] / closes[i - 1]) - mean;
                squares += deviation * deviation;
            }
        }
        // Sample standard deviation: one observation is spent on the mean.
        return count < 2 ? 0 : Math.sqrt(squares / (count - 1));
    }

    /** The band {@code sessions} trading days out, measured over {@link #DEFAULT_LOOKBACK_SESSIONS}. */
    public static Projection project(double[] closes, int sessions) {
        return project(closes, sessions, DEFAULT_LOOKBACK_SESSIONS);
    }

    public static Projection project(double[] closes, int sessions, int lookback) {
        if (closes == null || closes.length == 0 || sessions <= 0) {
            return Projection.unknown();
        }
        double last = closes[closes.length - 1];
        double sigma = dailySigma(closes, lookback);
        if (last <= 0 || sigma <= 0) {
            return Projection.unknown();
        }
        return new Projection(
                Monetary.round(BigDecimal.valueOf(last)),
                sigma,
                sessions,
                price(last, sigma, sessions, -1),
                price(last, sigma, sessions, 1),
                price(last, sigma, sessions, -2),
                price(last, sigma, sessions, 2));
    }

    /**
     * One edge of the cone {@code session} days out — the shape the chart draws, widening with the
     * square root of time rather than in a straight line.
     */
    public static double edge(double lastPrice, double dailySigma, int session, double sigmas) {
        if (lastPrice <= 0 || dailySigma <= 0 || session <= 0) {
            return lastPrice;
        }
        return lastPrice * Math.exp(sigmas * dailySigma * Math.sqrt(session));
    }

    private static BigDecimal price(double lastPrice, double dailySigma, int sessions, double sigmas) {
        return Monetary.round(BigDecimal.valueOf(edge(lastPrice, dailySigma, sessions, sigmas)));
    }
}
