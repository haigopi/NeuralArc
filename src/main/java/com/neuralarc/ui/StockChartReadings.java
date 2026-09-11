package com.neuralarc.ui;

import com.neuralarc.analytics.SwingPoints;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The plain-language guide beside the stock chart. For each part of the chart it says what the panel
 * is and what it shows for this stock right now, in words an operator can act on without knowing the
 * jargon. It describes the chart; it does not recommend trades.
 */
final class StockChartReadings {
    static final String TREND = "Price and moving averages";
    static final String TURNING_POINTS = "Support and resistance";
    static final String PLAN = "Your strategy's plan";
    static final String RSI = "RSI (14) – how stretched the move is";
    static final String VOLUME = "Volume – how much conviction";
    static final String MACD = "MACD (12, 26, 9) – momentum";
    static final String ACCUMULATION = "Accumulation / Distribution – who is in control";

    private static final DateTimeFormatter MONTH_YEAR = DateTimeFormatter.ofPattern("MMM yyyy", Locale.US);
    private static final int LOOKBACK = 20;

    private StockChartReadings() {
    }

    enum Tone {
        POSITIVE,
        NEGATIVE,
        CAUTION,
        NEUTRAL
    }

    /** One section of the guide: its title, what it shows now, and what the panel is. */
    record Reading(String title, String now, Tone tone, String whatItIs) {
    }

    static List<Reading> describe(StockChartData data) {
        if (data == null || data.isEmpty()) {
            return List.of();
        }
        List<Reading> readings = new ArrayList<>();
        readings.add(trend(data));
        readings.add(turningPoints(data));
        if (!data.levels().isEmpty()) {
            readings.add(plan(data));
        }
        readings.add(rsi(data));
        readings.add(volume(data));
        readings.add(macd(data));
        readings.add(accumulation(data));
        return readings;
    }

    private static Reading trend(StockChartData data) {
        String what = "Each candle is one trading day. The body runs from the open to the close – green when the "
                + "stock closed higher than it opened, red when lower – and the thin wick shows the day's high and low. "
                + "The coloured lines are exponential moving averages: the average closing price over the last 9, 13, "
                + "20, 50, 100 and 200 days, weighted toward recent days. Price above the averages, with the short "
                + "averages above the long ones, is an uptrend; the reverse is a downtrend.";
        int last = data.lastIndex();
        double close = data.closes()[last];
        int defined = 0;
        int above = 0;
        int nearestAbovePeriod = -1;
        double nearestAbove = Double.MAX_VALUE;
        int nearestBelowPeriod = -1;
        double nearestBelow = -Double.MAX_VALUE;
        List<Double> stack = new ArrayList<>();
        for (int period : StockChartData.EMA_PERIODS) {
            double value = data.emas().get(period)[last];
            if (Double.isNaN(value)) {
                continue;
            }
            defined++;
            stack.add(value);
            if (close > value) {
                above++;
                if (value > nearestBelow) {
                    nearestBelow = value;
                    nearestBelowPeriod = period;
                }
            } else if (value < nearestAbove) {
                nearestAbove = value;
                nearestAbovePeriod = period;
            }
        }
        if (defined == 0) {
            return new Reading(TREND, "There is not enough history yet to draw the moving averages.", Tone.NEUTRAL, what);
        }
        boolean shortAboveLong = true;
        boolean shortBelowLong = true;
        for (int i = 1; i < stack.size(); i++) {
            shortAboveLong &= stack.get(i) < stack.get(i - 1);
            shortBelowLong &= stack.get(i) > stack.get(i - 1);
        }
        String closed = "Closed at " + StockChartFormat.money(close);
        StringBuilder now = new StringBuilder();
        Tone tone;
        if (above == defined) {
            now.append(closed).append(", above all ").append(defined).append(" moving averages")
                    .append(shortAboveLong ? ", with the short averages above the long ones – a steady uptrend."
                            : " – buyers have the upper hand.");
            tone = Tone.POSITIVE;
        } else if (above == 0) {
            now.append(closed).append(", below all ").append(defined).append(" moving averages")
                    .append(shortBelowLong ? ", with the short averages below the long ones – a steady downtrend."
                            : " – sellers have the upper hand.");
            tone = Tone.NEGATIVE;
        } else {
            now.append(closed).append(", above ").append(above).append(" of ").append(defined)
                    .append(" moving averages – the trend is mixed.");
            tone = Tone.NEUTRAL;
        }
        if (nearestAbovePeriod > 0) {
            now.append(" The nearest average above is the ").append(nearestAbovePeriod).append("-day at ")
                    .append(StockChartFormat.money(nearestAbove)).append("; rallies often stall at these lines.");
        }
        if (nearestBelowPeriod > 0) {
            now.append(" The nearest below is the ").append(nearestBelowPeriod).append("-day at ")
                    .append(StockChartFormat.money(nearestBelow)).append(", which can act as a floor.");
        }
        return new Reading(TREND, now.toString(), tone, what);
    }

    private static Reading turningPoints(StockChartData data) {
        String what = "The labelled prices mark turning points – a high or low that stood out from the two weeks "
                + "either side of it. Buyers stepped in at old lows before, so those prices often act as support; "
                + "sellers appeared at old highs, so those often act as resistance. A level that held more than once "
                + "matters more.";
        double close = data.closes()[data.lastIndex()];
        SwingPoints.Swing support = null;
        SwingPoints.Swing resistance = null;
        for (SwingPoints.Swing swing : data.swings()) {
            if (!swing.high() && swing.price() < close && (support == null || swing.price() > support.price())) {
                support = swing;
            }
            if (swing.high() && swing.price() > close && (resistance == null || swing.price() < resistance.price())) {
                resistance = swing;
            }
        }
        String supportText = support == null
                ? "Price is below every low on this chart, so its history offers no nearby support."
                : "Nearest support below: " + StockChartFormat.money(support.price())
                        + " (" + data.dates().get(support.index()).format(MONTH_YEAR) + ").";
        String resistanceText = resistance == null
                ? " Price is above every high on this chart, so there is no nearby resistance."
                : " Nearest resistance above: " + StockChartFormat.money(resistance.price())
                        + " (" + data.dates().get(resistance.index()).format(MONTH_YEAR) + ").";
        return new Reading(TURNING_POINTS, supportText + resistanceText, Tone.NEUTRAL, what);
    }

    private static Reading plan(StockChartData data) {
        String what = "The dashed lines are this strategy's own plan: where it buys, your average cost, the stop-loss "
                + "that limits a loss, the target where it takes profit, and any order working at the broker now. "
                + "Seeing them against the price history shows whether the plan sits near real support and resistance. "
                + "A line beyond the visible range is shown as a label at the top or bottom edge.";
        double close = data.closes()[data.lastIndex()];
        StringBuilder now = new StringBuilder();
        Tone tone = Tone.NEUTRAL;
        List<String> buyLevels = new ArrayList<>();
        for (StockChartLevels.Level level : data.levels()) {
            double price = level.price();
            String money = StockChartFormat.money(price);
            switch (level.kind()) {
                case AVERAGE_COST -> {
                    double change = (close - price) / price * 100;
                    now.append("Price is ").append(StockChartFormat.percent(Math.abs(change)))
                            .append(change < 0 ? " below" : " above").append(" your average cost of ").append(money).append(". ");
                    tone = change < 0 ? Tone.NEGATIVE : Tone.POSITIVE;
                }
                case TARGET -> now.append(close < price
                        ? "The target at " + money + " is " + StockChartFormat.percent((price - close) / close * 100)
                                + " above the current price. "
                        : "Price has reached the target of " + money + ". ");
                case STOP_LOSS -> now.append(close > price
                        ? "The stop-loss at " + money + " is " + StockChartFormat.percent((close - price) / close * 100)
                                + " below the current price. "
                        : "Price is at or below the stop-loss of " + money + ". ");
                case BASE_BUY -> now.append("The strategy's first buy is planned at ").append(money).append(", ")
                        .append(StockChartFormat.percent(Math.abs(price - close) / close * 100))
                        .append(price < close ? " below" : " above").append(" the current price. ");
                case LOSS_BUY -> buyLevels.add(money);
                case WORKING_SELL -> now.append("A limit sell is working at ").append(money).append(". ");
                case WORKING_BUY -> now.append("A limit buy is working at ").append(money).append(". ");
            }
        }
        if (!buyLevels.isEmpty()) {
            now.append("Extra buys are planned at ").append(String.join(" and ", buyLevels)).append(". ");
        }
        return new Reading(PLAN, now.toString().trim(), tone, what);
    }

    private static Reading rsi(StockChartData data) {
        String what = "The Relative Strength Index compares the size of recent up days with recent down days over 14 "
                + "trading days, on a scale of 0 to 100. Above 70 means buying has been unusually strong (overbought); "
                + "below 30 means selling has been unusually strong (oversold). The shaded band is the normal 30–70 range.";
        int last = data.lastIndex();
        double value = data.rsi()[last];
        if (Double.isNaN(value)) {
            return new Reading(RSI, "Needs at least 15 trading days of history.", Tone.NEUTRAL, what);
        }
        String reading = "RSI is " + String.format(Locale.US, "%.1f", value);
        if (value >= 70) {
            return new Reading(RSI, reading + " – overbought. Buying has been unusually strong; a pause or pullback "
                    + "often follows, though in a strong uptrend RSI can stay high for weeks.", Tone.CAUTION, what);
        }
        if (value <= 30) {
            return new Reading(RSI, reading + " – oversold. Selling has been unusually strong; a pause or bounce "
                    + "often follows, though in a strong downtrend RSI can stay low for weeks.", Tone.CAUTION, what);
        }
        String direction = "";
        if (last >= 5 && !Double.isNaN(data.rsi()[last - 5])) {
            double change = value - data.rsi()[last - 5];
            direction = change > 3 ? " and rising" : change < -3 ? " and falling" : "";
        }
        return new Reading(RSI, reading + " – in the normal range, neither stretched up nor down" + direction + ".",
                Tone.NEUTRAL, what);
    }

    private static Reading volume(StockChartData data) {
        String what = "Each bar is the number of shares traded that day – green when the stock closed higher than it "
                + "opened, red when lower. The line is the 50-day average. A move on heavy volume carries more "
                + "conviction than one on light volume. These numbers come from Alpaca's free IEX feed, which sees only "
                + "a small share of all US trading, so they are far smaller than the market-wide volume other sites "
                + "show – compare the bars with each other, not with other sources.";
        int last = data.lastIndex();
        double average = data.volumeAverage()[last];
        double today = data.volumes()[last];
        if (Double.isNaN(average) || average <= 0) {
            return new Reading(VOLUME, "Needs 50 trading days of history for the average.", Tone.NEUTRAL, what);
        }
        double ratio = today / average;
        String ratioText = String.format(Locale.US, "%.1f×", ratio);
        boolean upDay = data.closes()[last] >= data.opens()[last];
        if (ratio >= 1.5) {
            return new Reading(VOLUME, "Today's volume is " + ratioText + " its 50-day average – heavy trading, so "
                    + "today's " + (upDay ? "rise" : "fall") + " had conviction behind it.",
                    upDay ? Tone.POSITIVE : Tone.NEGATIVE, what);
        }
        if (ratio <= 0.6) {
            return new Reading(VOLUME, "Today's volume is " + ratioText + " its 50-day average – light trading, so "
                    + "today's move carries less weight.", Tone.NEUTRAL, what);
        }
        return new Reading(VOLUME, "Today's volume is about normal (" + ratioText + " its 50-day average).",
                Tone.NEUTRAL, what);
    }

    private static Reading macd(StockChartData data) {
        String what = "MACD is the gap between the 12-day and 26-day moving averages. The signal line is a 9-day "
                + "average of MACD, and the bars show the difference between the two. MACD above its signal line "
                + "means momentum is improving; below means it is fading. When MACD crosses zero, the short-term "
                + "average has crossed the long-term one.";
        int last = data.lastIndex();
        double line = data.macd().line()[last];
        double signal = data.macd().signal()[last];
        if (Double.isNaN(line) || Double.isNaN(signal)) {
            return new Reading(MACD, "Needs about 35 trading days of history.", Tone.NEUTRAL, what);
        }
        boolean aboveSignal = line > signal;
        String state = aboveSignal
                ? (line > 0 ? "upward momentum is strong" : "momentum is still negative but improving")
                : (line > 0 ? "momentum is still positive but fading" : "momentum is pointing down");
        StringBuilder now = new StringBuilder("MACD is ")
                .append(aboveSignal ? "above" : "below").append(" its signal line and ")
                .append(line > 0 ? "above" : "below").append(" zero – ").append(state).append('.');
        int sameSide = 0;
        for (int i = last; i >= 0; i--) {
            double gap = data.macd().line()[i] - data.macd().signal()[i];
            if (Double.isNaN(gap) || (gap > 0) != aboveSignal) {
                break;
            }
            sameSide++;
        }
        boolean crossInView = last - sameSide >= 0 && !Double.isNaN(data.macd().signal()[last - sameSide]);
        if (crossInView) {
            String when = sameSide == 1 ? "today" : sameSide == 2 ? "1 trading day ago" : (sameSide - 1) + " trading days ago";
            now.append(" It crossed ").append(aboveSignal ? "above" : "below").append(" its signal line ").append(when).append('.');
        }
        return new Reading(MACD, now.toString(), aboveSignal ? Tone.POSITIVE : Tone.NEGATIVE, what);
    }

    private static Reading accumulation(StockChartData data) {
        String what = "A running total that adds a day's volume when the stock closes near its high and subtracts it "
                + "when it closes near its low. A rising line suggests buyers are accumulating; a falling line suggests "
                + "selling (distribution). When price falls but this line rises, buyers may be stepping in quietly. "
                + "It uses the same IEX volume as the volume panel.";
        int last = data.lastIndex();
        if (last < LOOKBACK) {
            return new Reading(ACCUMULATION, "Needs at least 21 trading days of history.", Tone.NEUTRAL, what);
        }
        double[] line = data.accumulationDistribution();
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (int i = last - LOOKBACK; i <= last; i++) {
            min = Math.min(min, line[i]);
            max = Math.max(max, line[i]);
        }
        double change = line[last] - line[last - LOOKBACK];
        boolean flat = max - min <= 0 || Math.abs(change) < (max - min) * 0.25;
        boolean priceFell = data.closes()[last] < data.closes()[last - LOOKBACK];
        if (flat) {
            return new Reading(ACCUMULATION, "Roughly flat over the last 20 trading days – buying and selling volume "
                    + "are in balance.", Tone.NEUTRAL, what);
        }
        if (change > 0 && priceFell) {
            return new Reading(ACCUMULATION, "Rising over the last 20 trading days while price fell – buyers may be "
                    + "quietly accumulating (a bullish divergence).", Tone.POSITIVE, what);
        }
        if (change < 0 && !priceFell) {
            return new Reading(ACCUMULATION, "Falling over the last 20 trading days while price rose – the rally is "
                    + "not backed by buying volume (a bearish divergence).", Tone.NEGATIVE, what);
        }
        return change > 0
                ? new Reading(ACCUMULATION, "Rising over the last 20 trading days – more volume on strong closes: "
                        + "accumulation.", Tone.POSITIVE, what)
                : new Reading(ACCUMULATION, "Falling over the last 20 trading days – more volume on weak closes: "
                        + "distribution, with no sign yet of buyers stepping in.", Tone.NEGATIVE, what);
    }
}
