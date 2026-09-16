package com.neuralarc.analytics;

import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Tax-loss harvesting over positions the app already tracks: which open losses are worth booking
 * before the year ends, how much of this year's realised gains they cancel, and what is left to
 * carry forward.
 *
 * <p>US federal rules as they apply to an ordinary brokerage account: booked losses first offset
 * realised gains of the same kind, then up to {@link #ORDINARY_INCOME_LIMIT} of ordinary income a
 * year, and anything beyond that carries forward to later years. A loss is disallowed when the same
 * security is bought back within {@link #WASH_SALE_DAYS} days either side of the sale — which this
 * app can cause by itself, since a strategy with loss-buy levels or repeat-cycle armed will
 * repurchase the symbol automatically.
 *
 * <p>Short-term losses are worth booking first: they offset short-term gains, which are taxed at
 * ordinary income rates rather than the lower long-term rate.
 *
 * <p>Pure and side-effect free. This is information drawn from the account's own numbers, not tax
 * advice; the wording it produces says so.
 */
public final class LossHarvesting {
    /** Net loss that can be written off against ordinary income in one tax year (US federal). */
    public static final BigDecimal ORDINARY_INCOME_LIMIT = new BigDecimal("3000");
    /** Days either side of the sale in which a repurchase disallows the loss. */
    public static final int WASH_SALE_DAYS = 30;
    /** A holding of at least this many days is long-term. */
    public static final int LONG_TERM_DAYS = 365;

    private LossHarvesting() {
    }

    /** One filled trade, enough to replay what each sell actually booked. */
    public record Fill(boolean buy, BigDecimal quantity, BigDecimal price, LocalDate date) {
    }

    /**
     * Profit booked by sells that filled during {@code year}. Every fill is replayed so each sell is
     * measured against the average cost at that moment; trades from earlier years still shape that
     * average, but only this year's sells count toward the total.
     */
    public static BigDecimal realizedInYear(List<Fill> fills, int year) {
        if (fills == null) {
            return Monetary.round(BigDecimal.ZERO);
        }
        List<Fill> ordered = fills.stream()
                .filter(fill -> fill != null && fill.date() != null && fill.quantity() != null
                        && fill.quantity().signum() > 0)
                .sorted(Comparator.comparing(Fill::date))
                .toList();
        BigDecimal held = BigDecimal.ZERO;
        BigDecimal averageCost = BigDecimal.ZERO;
        BigDecimal realized = BigDecimal.ZERO;
        for (Fill fill : ordered) {
            BigDecimal price = fill.price() == null ? BigDecimal.ZERO : fill.price();
            if (fill.buy()) {
                BigDecimal runningCost = averageCost.multiply(held).add(price.multiply(fill.quantity()));
                held = held.add(fill.quantity());
                if (held.signum() > 0) {
                    averageCost = runningCost.divide(held, 8, java.math.RoundingMode.HALF_UP);
                }
                continue;
            }
            BigDecimal sold = fill.quantity().min(held.max(BigDecimal.ZERO));
            if (sold.signum() <= 0) {
                continue;
            }
            if (fill.date().getYear() == year) {
                realized = realized.add(price.subtract(averageCost).multiply(sold));
            }
            held = held.subtract(sold);
            if (held.signum() == 0) {
                averageCost = BigDecimal.ZERO;
            }
        }
        return Monetary.round(realized);
    }

    /** How long the shares have been held, which decides the rate the loss offsets. */
    public enum Term {
        SHORT,
        LONG,
        UNKNOWN
    }

    /**
     * One open losing position as the tax view sees it.
     *
     * @param heldDays        days since the first buy filled, or 0 when that is unknown
     * @param repurchaseArmed whether this strategy would buy the symbol back on its own (loss-buy
     *                        levels or repeat-after-exit), which would trigger the wash-sale rule
     */
    public record Position(
            String symbol,
            String workspaceLabel,
            int shares,
            BigDecimal unrealizedPnl,
            double unrealizedPercent,
            long heldDays,
            boolean repurchaseArmed
    ) {
    }

    /** One position's booked-loss suggestion. */
    public record Lot(
            String symbol,
            String workspaceLabel,
            int shares,
            BigDecimal loss,
            double lossPercent,
            Term term,
            boolean washSaleRisk,
            String action
    ) {
    }

    public record Report(
            /** Last day a sale still books the loss in this tax year. */
            LocalDate sellBy,
            /** First day the symbol may be bought back without the wash-sale rule disallowing the loss. */
            LocalDate repurchaseAllowedFrom,
            BigDecimal realizedGainsYtd,
            BigDecimal harvestableLoss,
            BigDecimal offsetsGains,
            BigDecimal ordinaryIncomeOffset,
            BigDecimal carryForward,
            List<Lot> recommended,
            List<Lot> optional,
            String headline,
            List<String> notes
    ) {
        public Report {
            recommended = recommended == null ? List.of() : List.copyOf(recommended);
            optional = optional == null ? List.of() : List.copyOf(optional);
            notes = notes == null ? List.of() : List.copyOf(notes);
        }

        public boolean hasCandidates() {
            return !recommended.isEmpty() || !optional.isEmpty();
        }
    }

    /**
     * @param positions        every open position; those in profit are ignored
     * @param realizedGainsYtd profit already booked this tax year, negative when losses lead
     * @param today            the date the report is written for, used for the year-end deadline
     */
    public static Report analyze(List<Position> positions, BigDecimal realizedGainsYtd, LocalDate today) {
        BigDecimal gainsYtd = Monetary.round(realizedGainsYtd == null ? BigDecimal.ZERO : realizedGainsYtd);
        List<Position> losing = positions == null ? List.of() : positions.stream()
                .filter(position -> position != null && position.shares() > 0)
                .filter(position -> position.unrealizedPnl() != null && position.unrealizedPnl().signum() < 0)
                // Short-term losses first: they offset income taxed at the higher ordinary rate.
                .sorted(Comparator.comparing((Position position) -> term(position.heldDays()) == Term.SHORT ? 0 : 1)
                        .thenComparing(position -> position.unrealizedPnl().abs(), Comparator.reverseOrder()))
                .toList();

        BigDecimal harvestable = BigDecimal.ZERO;
        for (Position position : losing) {
            harvestable = harvestable.add(position.unrealizedPnl().abs());
        }
        harvestable = Monetary.round(harvestable);

        BigDecimal gains = gainsYtd.max(BigDecimal.ZERO);
        BigDecimal offsetsGains = Monetary.round(harvestable.min(gains));
        BigDecimal remaining = Monetary.round(harvestable.subtract(offsetsGains));
        BigDecimal ordinaryIncomeOffset = Monetary.round(remaining.min(ORDINARY_INCOME_LIMIT));
        BigDecimal carryForward = Monetary.round(remaining.subtract(ordinaryIncomeOffset));

        // Booking more than this year's gains plus the income allowance only builds a carry-forward,
        // so those lots are worth listing separately rather than recommending now.
        BigDecimal worthBookingNow = gains.add(ORDINARY_INCOME_LIMIT);
        List<Lot> recommended = new ArrayList<>();
        List<Lot> optional = new ArrayList<>();
        BigDecimal running = BigDecimal.ZERO;
        for (Position position : losing) {
            Lot lot = toLot(position);
            if (running.compareTo(worthBookingNow) < 0) {
                recommended.add(lot);
                running = running.add(position.unrealizedPnl().abs());
            } else {
                optional.add(lot);
            }
        }

        LocalDate sellBy = lastTradingDayOfYear(today);
        return new Report(sellBy, sellBy.plusDays(WASH_SALE_DAYS + 1L), gainsYtd, harvestable, offsetsGains,
                ordinaryIncomeOffset, carryForward, recommended, optional,
                headline(gainsYtd, harvestable, offsetsGains, ordinaryIncomeOffset, carryForward),
                notes(losing, today));
    }

    private static Lot toLot(Position position) {
        BigDecimal loss = Monetary.round(position.unrealizedPnl());
        Term term = term(position.heldDays());
        String action = "Sell " + position.shares() + (position.shares() == 1 ? " share" : " shares")
                + " to book " + money(loss.abs()) + ".";
        return new Lot(position.symbol(), position.workspaceLabel(), position.shares(), loss,
                position.unrealizedPercent(), term, position.repurchaseArmed(), action);
    }

    private static Term term(long heldDays) {
        if (heldDays <= 0) {
            return Term.UNKNOWN;
        }
        return heldDays >= LONG_TERM_DAYS ? Term.LONG : Term.SHORT;
    }

    private static String headline(BigDecimal gainsYtd, BigDecimal harvestable, BigDecimal offsetsGains,
                                   BigDecimal ordinaryIncomeOffset, BigDecimal carryForward) {
        if (harvestable.signum() <= 0) {
            return "No open position is at a loss, so there is nothing to harvest right now.";
        }
        StringBuilder headline = new StringBuilder("Booking every open loss would realise ")
                .append(money(harvestable)).append(". ");
        if (gainsYtd.signum() > 0) {
            headline.append(offsetsGains.compareTo(gainsYtd) >= 0
                            ? "That cancels all " + money(gainsYtd) + " of gains booked this year"
                            : "That cancels " + money(offsetsGains) + " of the " + money(gainsYtd) + " booked this year")
                    .append(". ");
        } else if (gainsYtd.signum() < 0) {
            headline.append("This year is already down ").append(money(gainsYtd.abs()))
                    .append(", so there are no gains left to cancel. ");
        } else {
            headline.append("No gains have been booked this year to cancel. ");
        }
        if (ordinaryIncomeOffset.signum() > 0) {
            headline.append("Up to ").append(money(ordinaryIncomeOffset)).append(" then comes off ordinary income. ");
        }
        if (carryForward.signum() > 0) {
            headline.append(money(carryForward)).append(" carries forward to later years.");
        }
        return headline.toString().trim();
    }

    private static List<String> notes(List<Position> losing, LocalDate today) {
        List<String> notes = new ArrayList<>();
        if (today != null) {
            LocalDate sellBy = lastTradingDayOfYear(today);
            long days = java.time.temporal.ChronoUnit.DAYS.between(today, sellBy);
            notes.add(days >= 0
                    ? "The sale must execute by " + date(sellBy) + " — " + days + " day(s) away — for the loss to count"
                      + " in " + sellBy.getYear() + ". A limit order left unfilled books nothing, so leave room."
                    : "This report is dated after the last trading day of the year.");
        }
        notes.add("Wash sale: buying the same security back within " + WASH_SALE_DAYS
                + " days before or after the sale disallows the loss; it moves into the cost basis of the new shares instead.");
        if (losing.stream().anyMatch(Position::repurchaseArmed)) {
            notes.add("Rows marked \"buys back automatically\" have loss-buy levels or repeat-cycle armed. Switch those "
                    + "off before booking the loss, or NeuralArc repurchases inside the window and the loss is disallowed.");
        }
        notes.add("Losses offset gains of the same kind first — short-term against short-term — so short-term losses, "
                + "which shield income taxed at ordinary rates, are listed first.");
        notes.add("Figures come from this account's own trades and are information, not tax advice. "
                + "Confirm with your CPA before selling for tax reasons; state rules and your wider portfolio may differ.");
        return notes;
    }

    /** The last weekday of the year: a trade must execute on or before it to count for that year. */
    static LocalDate lastTradingDayOfYear(LocalDate today) {
        LocalDate date = LocalDate.of(today == null ? LocalDate.now().getYear() : today.getYear(), 12, 31);
        while (date.getDayOfWeek() == java.time.DayOfWeek.SATURDAY || date.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
            date = date.minusDays(1);
        }
        return date;
    }

    /** "Thu 31 Dec 2026" — a date an operator can act on without decoding it. */
    public static String date(LocalDate date) {
        return date == null ? "-" : date.format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.US));
    }

    private static String money(BigDecimal value) {
        BigDecimal rounded = Monetary.round(value == null ? BigDecimal.ZERO : value);
        return (rounded.signum() < 0 ? "-$" : "$") + String.format(Locale.US, "%,.2f", rounded.abs());
    }
}
