package com.neuralarc.ui;

import com.neuralarc.model.MarketBar;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Daily bars for the stock chart tests, built from a list of closing prices on consecutive weekdays. */
final class StockChartTestBars {
    private StockChartTestBars() {
    }

    static List<MarketBar> fromCloses(double[] closes) {
        List<MarketBar> bars = new ArrayList<>();
        LocalDate day = LocalDate.of(2023, 1, 2);
        for (int i = 0; i < closes.length; i++) {
            while (day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY) {
                day = day.plusDays(1);
            }
            double close = closes[i];
            double open = i == 0 ? close : closes[i - 1];
            bars.add(new MarketBar("TEST", day + "T05:00:00Z",
                    bd(open), bd(Math.max(open, close) + 0.1), bd(Math.min(open, close) - 0.1), bd(close),
                    bd(1_000_000 * (1 + (i % 5) * 0.1))));
            day = day.plusDays(1);
        }
        return bars;
    }

    static double[] steady(double from, double to, int count) {
        double[] closes = new double[count];
        for (int i = 0; i < count; i++) {
            closes[i] = from + (to - from) * i / (count - 1);
        }
        return closes;
    }

    /** Down to 5, up to 12, down to 7, then up to a last close of 8: known support and resistance. */
    static double[] swings() {
        double[] closes = new double[331];
        for (int i = 0; i < closes.length; i++) {
            if (i <= 99) {
                closes[i] = 10 - 5.0 * i / 99;
            } else if (i <= 199) {
                closes[i] = 5 + 7.0 * (i - 99) / 100;
            } else if (i <= 299) {
                closes[i] = 12 - 5.0 * (i - 199) / 100;
            } else {
                closes[i] = 7 + (i - 299) / 31.0;
            }
        }
        return closes;
    }

    private static BigDecimal bd(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }
}
