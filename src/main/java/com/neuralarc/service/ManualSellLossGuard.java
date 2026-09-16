package com.neuralarc.service;

import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;

/**
 * Refuses a hand-placed sell that would realise a loss.
 *
 * <p>Selling under the average entry turns an open loss into a booked one. A sell the operator types
 * themselves is therefore refused while the position is below cost, so a position is never closed at a
 * loss by a stray click on the wrong row.
 *
 * <p>This guards deliberate, one-off sells only. Stop-losses and the engine's own exits are what
 * protect a falling position, and they are deliberately left alone — a rule that blocked them would
 * trap the position in exactly the situation they exist for. Where the price or the average entry is
 * unknown, the sell proceeds: refusing on a missing number would block an exit for no stated reason.
 */
public final class ManualSellLossGuard {
    private ManualSellLossGuard() {
    }

    /**
     * @param averageCost what the shares being sold cost, per share
     * @param sellPrice   the price the sell would go out at
     * @return why the sell must be refused, or empty when it may proceed
     */
    public static Optional<String> refusal(String symbol, int quantity, BigDecimal averageCost, BigDecimal sellPrice) {
        if (!positive(averageCost) || !positive(sellPrice) || quantity <= 0) {
            return Optional.empty();
        }
        if (sellPrice.compareTo(averageCost) >= 0) {
            return Optional.empty();
        }
        BigDecimal loss = Monetary.round(averageCost.subtract(sellPrice).multiply(BigDecimal.valueOf(quantity)));
        return Optional.of("Selling " + name(symbol) + " now would book a loss of " + money(loss)
                + ": " + quantity + (quantity == 1 ? " share" : " shares") + " at " + money(sellPrice)
                + " against an average cost of " + money(averageCost)
                + ". Manual sells below cost are blocked; the stop loss and sell trigger still exit this position.");
    }

    private static String name(String symbol) {
        return symbol == null || symbol.isBlank() ? "this position" : symbol;
    }

    private static String money(BigDecimal value) {
        return "$" + String.format(Locale.US, "%,.2f", Monetary.round(value));
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }
}
