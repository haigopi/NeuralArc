package com.neuralarc.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A short read on how the market is treating a stock right now, drawn from its daily bars.
 *
 * <p>It condenses the chart guide's own readings — trend against the moving averages, MACD momentum,
 * accumulation/distribution and today's volume — into one verdict and a few plain sentences, so the
 * summary beside the grid can never disagree with the full chart it opens. Each signal votes buyers or
 * sellers, the trend with double weight: price above or below every moving average is the broadest
 * evidence, and a single MACD wobble against it must not cancel it. The verdict is called only when
 * the votes lean clearly one way; otherwise it says undecided.
 */
final class MarketReactionSummary {
    /** Trading days in the "last week" change shown first. */
    static final int WEEK = 5;

    enum Verdict {
        BUYERS("Buyers in control"),
        SELLERS("Sellers in control"),
        UNDECIDED("Undecided");

        private final String label;

        Verdict(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    record Summary(Verdict verdict, String text) {
        static Summary empty() {
            return new Summary(Verdict.UNDECIDED, "");
        }
    }

    private MarketReactionSummary() {
    }

    static Summary summarize(StockChartData data) {
        if (data == null || data.isEmpty()) {
            return Summary.empty();
        }
        int score = 0;
        List<String> evidence = new ArrayList<>();
        for (StockChartReadings.Reading reading : StockChartReadings.describe(data)) {
            int vote = vote(reading.tone());
            switch (reading.title()) {
                case StockChartReadings.TREND -> {
                    score += 2 * vote;
                    evidence.add(vote > 0 ? "price holds above its moving averages"
                            : vote < 0 ? "price sits below its moving averages"
                            : "price is caught between its moving averages");
                }
                case StockChartReadings.MACD -> {
                    score += vote;
                    if (vote != 0) {
                        evidence.add(vote > 0 ? "momentum is improving" : "momentum is fading");
                    }
                }
                case StockChartReadings.ACCUMULATION -> {
                    score += vote;
                    evidence.add(vote > 0 ? "volume has leaned to strong closes (buying)"
                            : vote < 0 ? "volume has leaned to weak closes (selling)"
                            : "buying and selling volume are in balance");
                }
                case StockChartReadings.VOLUME -> score += vote; // Only heavy volume carries a vote.
                default -> {
                }
            }
        }
        Verdict verdict = score >= 2 ? Verdict.BUYERS : score <= -2 ? Verdict.SELLERS : Verdict.UNDECIDED;

        StringBuilder text = new StringBuilder();
        java.util.Optional<String> week = weekChange(data);
        week.ifPresent(change -> text.append(change).append(": "));
        text.append(week.isPresent() ? join(evidence) : capitalize(join(evidence))).append('.');
        todaysVolume(data).ifPresent(volume -> text.append(' ').append(volume));
        rsiExtreme(data).ifPresent(rsi -> text.append(' ').append(rsi));
        return new Summary(verdict, text.toString());
    }

    private static int vote(StockChartReadings.Tone tone) {
        return switch (tone) {
            case POSITIVE -> 1;
            case NEGATIVE -> -1;
            case CAUTION, NEUTRAL -> 0;
        };
    }

    private static java.util.Optional<String> weekChange(StockChartData data) {
        int last = data.lastIndex();
        if (last < WEEK || data.closes()[last - WEEK] <= 0) {
            return java.util.Optional.empty();
        }
        double change = (data.closes()[last] / data.closes()[last - WEEK] - 1) * 100;
        return java.util.Optional.of(String.format(Locale.US, "%s %.1f%% over the last %d sessions",
                change >= 0 ? "Up" : "Down", Math.abs(change), WEEK));
    }

    private static java.util.Optional<String> todaysVolume(StockChartData data) {
        int last = data.lastIndex();
        double average = data.volumeAverage()[last];
        if (Double.isNaN(average) || average <= 0) {
            return java.util.Optional.empty();
        }
        double ratio = data.volumes()[last] / average;
        boolean upDay = data.closes()[last] >= data.opens()[last];
        if (ratio >= 1.5) {
            return java.util.Optional.of(String.format(Locale.US,
                    "Today's %s came on heavy volume (%.1f× average), so traders are acting on it.",
                    upDay ? "rise" : "fall", ratio));
        }
        if (ratio <= 0.6) {
            return java.util.Optional.of(String.format(Locale.US,
                    "Trading today is light (%.1f× average), so today's move says little.", ratio));
        }
        return java.util.Optional.empty();
    }

    private static java.util.Optional<String> rsiExtreme(StockChartData data) {
        double value = data.rsi()[data.lastIndex()];
        if (Double.isNaN(value)) {
            return java.util.Optional.empty();
        }
        if (value >= 70) {
            return java.util.Optional.of(String.format(Locale.US,
                    "RSI %.0f is overbought – a pause or pullback often follows.", value));
        }
        if (value <= 30) {
            return java.util.Optional.of(String.format(Locale.US,
                    "RSI %.0f is oversold – a pause or bounce often follows.", value));
        }
        return java.util.Optional.empty();
    }

    private static String join(List<String> parts) {
        if (parts.size() <= 1) {
            return parts.isEmpty() ? "no clear signal yet" : parts.get(0);
        }
        return String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.get(parts.size() - 1);
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
