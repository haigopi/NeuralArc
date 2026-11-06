package com.neuralarc.analytics;

import com.neuralarc.model.MarketBar;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Predicts a buy price from how a stock has been trending over the last 2 weeks, 2 months, 6 months and
 * 1 year.
 *
 * <p>For each window, over its closing prices:
 * <ol>
 *   <li><b>Drift</b> {@code b} is the slope of a least-squares line through {@code ln(close)}, the average
 *       daily percentage move.</li>
 *   <li><b>Volatility</b> {@code σ} is the sample standard deviation of the daily log returns.</li>
 *   <li><b>Strength</b> {@code s = clamp(b·√n / σ / 2, -1, 1)}: total drift over the window measured against
 *       the noise a driftless walk would produce over it. {@code b√n/σ} of 2 is a clear trend, so it maps
 *       to 1.</li>
 * </ol>
 * The windows are blended with weights favouring the recent ones (2 weeks 35%, 2 months 30%, 6 months 20%,
 * 1 year 15%; renormalised when a window has too little history):
 * <pre>
 *   T = Σ w·s        trend, -1 (falling) to +1 (rising)
 *   μ = Σ w·b        blended daily drift
 *   σ = Σ w·σ_w      blended daily volatility
 * </pre>
 * The expected pullback over a week ({@code H = 5} sessions) is the typical low of a driftless walk,
 * {@code 0.8·σ·√H}. A rising stock is bought on shallower dips, so that depth shrinks by {@code 1 - T/2}
 * (halved at T=1, 1.5x at T=-1); a falling stock is also expected to keep sliding, which adds
 * {@code max(0, -μ)·H}:
 * <pre>
 *   depth     = 0.8·σ·√H·(1 - T/2) + max(0, -μ)·H
 *   predicted = market · e^(-depth)
 * </pre>
 * The prediction is floored at what the stock has actually traded (a limit under everything it traded
 * recently rarely fills): the 2-week low when the trend is clearly up ({@code T > 0.25}), otherwise the
 * lowest low of the longest window read. It is never above the market.
 */
public final class TrendEntryModel {
    static final int HORIZON_SESSIONS = 5;
    static final double EXPECTED_DIP_FACTOR = 0.8;
    static final double TREND_DIP_SENSITIVITY = 0.5;
    static final double CLEAR_UPTREND = 0.25;
    static final double MAX_DEPTH = 0.5;
    private static final double SIGNIFICANT_Z = 2.0;
    private static final int MIN_BARS = 5;
    private static final double MIN_COVERAGE = 0.6;

    private static final List<Window> WINDOWS = List.of(
            new Window("2 weeks", 10, 0.35),
            new Window("2 months", 42, 0.30),
            new Window("6 months", 126, 0.20),
            new Window("1 year", 252, 0.15));

    private TrendEntryModel() {
    }

    private record Window(String label, int sessions, double weight) {
    }

    /** What one window said. */
    public record WindowRead(String label, double driftPerSession, double volatility, double strength, BigDecimal low) {
    }

    public record Prediction(BigDecimal price, double trend, double expectedDipPercent,
                             BigDecimal floor, List<WindowRead> windows) {
        /** "trend +0.42 (2 weeks +0.80, 2 months +0.31), expected dip 3.1%, floor $98.20". */
        public String summary() {
            StringBuilder text = new StringBuilder(String.format(Locale.ROOT, "trend %+.2f (", trend));
            for (int index = 0; index < windows.size(); index++) {
                WindowRead read = windows.get(index);
                text.append(index == 0 ? "" : ", ")
                        .append(String.format(Locale.ROOT, "%s %+.2f", read.label(), read.strength()));
            }
            return text.append(String.format(Locale.ROOT, "), expected dip %.1f%%, floor $", expectedDipPercent))
                    .append(floor.toPlainString()).toString();
        }
    }

    /**
     * @param history completed and current daily bars, oldest first
     * @param market  the price the entry must not exceed
     * @param todayLow the low so far today, or zero
     * @return the prediction, or empty when there is no usable history
     */
    public static Optional<Prediction> predict(List<MarketBar> history, BigDecimal market, BigDecimal todayLow) {
        if (history == null || market == null || market.signum() <= 0) {
            return Optional.empty();
        }
        List<WindowRead> reads = new ArrayList<>();
        double weightSum = 0;
        double trend = 0;
        double drift = 0;
        double volatility = 0;
        BigDecimal low2Weeks = BigDecimal.ZERO;
        BigDecimal lowLongest = BigDecimal.ZERO;
        for (Window window : WINDOWS) {
            List<MarketBar> bars = lastBars(history, window.sessions());
            if (bars.size() < Math.max(MIN_BARS, (int) Math.ceil(window.sessions() * MIN_COVERAGE))) {
                continue;
            }
            Slice slice = read(bars);
            BigDecimal low = withToday(lowest(bars), todayLow);
            reads.add(new WindowRead(window.label(), slice.drift(), slice.volatility(), slice.strength(), low));
            weightSum += window.weight();
            trend += window.weight() * slice.strength();
            drift += window.weight() * slice.drift();
            volatility += window.weight() * slice.volatility();
            if (window.sessions() == WINDOWS.get(0).sessions()) {
                low2Weeks = low;
            }
            lowLongest = low;
        }
        if (reads.isEmpty() || weightSum <= 0) {
            return Optional.empty();
        }
        trend /= weightSum;
        drift /= weightSum;
        volatility /= weightSum;

        double dip = EXPECTED_DIP_FACTOR * volatility * Math.sqrt(HORIZON_SESSIONS) * (1 - TREND_DIP_SENSITIVITY * trend);
        double depth = Math.min(MAX_DEPTH, Math.max(0, dip) + Math.max(0, -drift) * HORIZON_SESSIONS);
        BigDecimal predicted = market.multiply(BigDecimal.valueOf(Math.exp(-depth)));
        BigDecimal floor = trend > CLEAR_UPTREND && low2Weeks.signum() > 0 ? low2Weeks : lowLongest;
        BigDecimal price = floor.signum() > 0 ? predicted.max(floor) : predicted;
        price = price.min(market).setScale(2, RoundingMode.FLOOR);
        double dipPercent = market.signum() > 0
                ? market.subtract(price).multiply(BigDecimal.valueOf(100)).divide(market, 4, RoundingMode.HALF_UP).doubleValue()
                : 0;
        return Optional.of(new Prediction(price, trend, dipPercent, floor.setScale(2, RoundingMode.FLOOR), List.copyOf(reads)));
    }

    private record Slice(double drift, double volatility, double strength) {
    }

    private static Slice read(List<MarketBar> bars) {
        int n = bars.size();
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            y[i] = Math.log(bars.get(i).close().doubleValue());
        }
        double meanX = (n - 1) / 2.0;
        double meanY = 0;
        for (double value : y) {
            meanY += value;
        }
        meanY /= n;
        double covariance = 0;
        double varianceX = 0;
        for (int i = 0; i < n; i++) {
            covariance += (i - meanX) * (y[i] - meanY);
            varianceX += (i - meanX) * (i - meanX);
        }
        double slope = varianceX == 0 ? 0 : covariance / varianceX;

        double meanReturn = (y[n - 1] - y[0]) / (n - 1);
        double squares = 0;
        for (int i = 1; i < n; i++) {
            double deviation = (y[i] - y[i - 1]) - meanReturn;
            squares += deviation * deviation;
        }
        double volatility = n > 2 ? Math.sqrt(squares / (n - 2)) : 0;
        double strength = volatility == 0
                ? 0
                : Math.max(-1, Math.min(1, slope * Math.sqrt(n) / volatility / SIGNIFICANT_Z));
        return new Slice(slope, volatility, strength);
    }

    /** The last {@code sessions} bars that carry a usable close and low, oldest first. */
    private static List<MarketBar> lastBars(List<MarketBar> history, int sessions) {
        List<MarketBar> usable = new ArrayList<>();
        for (MarketBar bar : history) {
            if (bar != null && bar.close() != null && bar.close().signum() > 0) {
                usable.add(bar);
            }
        }
        return usable.subList(Math.max(0, usable.size() - sessions), usable.size());
    }

    private static BigDecimal lowest(List<MarketBar> bars) {
        BigDecimal lowest = BigDecimal.ZERO;
        for (MarketBar bar : bars) {
            BigDecimal low = bar.low() != null && bar.low().signum() > 0 ? bar.low() : bar.close();
            if (lowest.signum() == 0 || low.compareTo(lowest) < 0) {
                lowest = low;
            }
        }
        return lowest;
    }

    private static BigDecimal withToday(BigDecimal low, BigDecimal todayLow) {
        return todayLow != null && todayLow.signum() > 0 && (low.signum() == 0 || todayLow.compareTo(low) < 0) ? todayLow : low;
    }
}
