package com.neuralarc.ui;

import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * Formats the portfolio figures of one grid scope (a workspace tab, or All Stocks) for the bottom
 * portfolio bar: what is invested against what the working buy orders will still cost, how many
 * positions trend up or down, and how many positions are waiting on a buy or a sell to fill.
 * Tooltips are HTML fragments written for a reader who is not a trader; this class touches no Swing.
 */
final class PortfolioScopePresenter {
    /** The scope of the window's bottom status bar, which totals every workspace. */
    static final String ALL_WORKSPACES = "All workspaces";

    enum Tone {
        NEUTRAL,
        POSITIVE,
        NEGATIVE
    }

    /** One captioned figure in the footer under a workspace grid. */
    record Figure(String caption, String text, String tooltipHtml, Tone tone) {
    }

    /** One bar value and the plain-language explanation shown when hovering it. */
    record Item(String text, String tooltipHtml) {
    }

    record PortfolioScopeView(
            String scopeLabel,
            Item marketValue,
            Item investedVsUpcoming,
            Item gaining,
            Item losing,
            Item pendingBuy,
            Item pendingSell,
            String upcomingText
    ) {
        /** The portfolio figures shown under the workspace grid, in display order. */
        List<Figure> gridFigures() {
            return List.of(
                    new Figure("Invested vs Upcoming", investedVsUpcoming.text(), investedVsUpcoming.tooltipHtml(), Tone.NEUTRAL),
                    new Figure("Gaining", gaining.text(), gaining.tooltipHtml(), Tone.POSITIVE),
                    new Figure("Losing", losing.text(), losing.tooltipHtml(), Tone.NEGATIVE),
                    new Figure("Pending Buy", pendingBuy.text(), pendingBuy.tooltipHtml(), Tone.NEUTRAL),
                    new Figure("Pending Sell", pendingSell.text(), pendingSell.tooltipHtml(), Tone.NEUTRAL));
        }
    }

    PortfolioScopeView present(String scopeLabel, SystemMetricsPresenter.PortfolioScopeMetrics metrics) {
        String scope = scopeLabel == null || scopeLabel.isBlank() ? "All Stocks" : scopeLabel.trim();
        String scopeHtml = "<b>" + escape(scope) + "</b>";
        BigDecimal invested = nonNull(metrics.investedValue());
        BigDecimal upcoming = nonNull(metrics.upcomingBuyTotal());
        BigDecimal committed = Monetary.round(invested.add(upcoming));

        Item marketValue = new Item(
                money(metrics.marketValue()),
                scopeHtml + ": what the shares held now are worth at the latest price.");
        Item investedVsUpcoming = new Item(
                money(invested) + " vs " + money(upcoming) + "  (Total " + money(committed) + ")",
                scopeHtml
                        + "<br><b>Invested " + money(invested) + "</b>: what you actually paid for the shares you hold now."
                        + "<br><b>Upcoming " + money(upcoming) + "</b>: what the buy orders still working at the broker will cost if they fill"
                        + " (" + positions(metrics.pendingBuyPositions()) + ")."
                        + "<br><b>Total " + money(committed) + "</b>: the money committed to this scope once those orders fill.");
        Item gaining = new Item(
                trendText(metrics.gainingCount(), metrics.gainingPnl()),
                scopeHtml + ": " + positions(metrics.gainingCount()) + " in profit"
                        + (metrics.gainingCount() == 0 ? "." : ", up " + signedMoney(metrics.gainingPnl()) + " in total.")
                        + "<br>This profit is unrealized: it is locked in only when the position is closed.");
        Item losing = new Item(
                trendText(metrics.losingCount(), metrics.losingPnl()),
                scopeHtml + ": " + positions(metrics.losingCount()) + " at a loss"
                        + (metrics.losingCount() == 0 ? "." : ", down " + money(nonNull(metrics.losingPnl()).abs()) + " in total.")
                        + "<br>This loss is unrealized: it becomes final only if the position is closed at this price.");
        Item pendingBuy = new Item(
                String.valueOf(metrics.pendingBuyPositions()),
                scopeHtml + ": " + positions(metrics.pendingBuyPositions())
                        + " with a buy order working at the broker that has not filled yet"
                        + (metrics.pendingBuyPositions() == 0 ? "." : ", worth " + money(upcoming) + "."));
        Item pendingSell = new Item(
                String.valueOf(metrics.pendingSellPositions()),
                scopeHtml + ": " + positions(metrics.pendingSellPositions())
                        + " with a sell order working at the broker that has not filled yet,"
                        + " for example a target sell waiting for its price.");
        return new PortfolioScopeView(scope, marketValue, investedVsUpcoming, gaining, losing, pendingBuy, pendingSell,
                money(upcoming));
    }

    /** The scope's name in bold, escaped for a tooltip. */
    static String scopeHtml(String label) {
        String scope = label == null || label.isBlank() ? "All Stocks" : label.trim();
        return "<b>" + escape(scope) + "</b>";
    }

    static Tone toneOf(BigDecimal value) {
        int sign = Monetary.round(nonNull(value)).signum();
        return sign > 0 ? Tone.POSITIVE : sign < 0 ? Tone.NEGATIVE : Tone.NEUTRAL;
    }

    /** "$12,340.00", or "-$80.10" for a negative amount. */
    static String money(BigDecimal value) {
        BigDecimal rounded = Monetary.round(nonNull(value));
        String digits = String.format(Locale.US, "%,.2f", rounded.abs());
        return (rounded.signum() < 0 ? "-$" : "$") + digits;
    }

    private static String signedMoney(BigDecimal value) {
        BigDecimal rounded = Monetary.round(nonNull(value));
        return rounded.signum() > 0 ? "+" + money(rounded) : money(rounded);
    }

    private static String trendText(int count, BigDecimal pnl) {
        return count == 0 ? "0" : count + " · " + signedMoney(pnl);
    }

    private static String positions(int count) {
        return count == 1 ? "1 position" : count + " positions";
    }

    private static BigDecimal nonNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
