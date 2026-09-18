package com.neuralarc.api;

import com.neuralarc.util.Monetary;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.Optional;

final class AlpacaAccountFundsParser {
    private AlpacaAccountFundsParser() {
    }

    static BigDecimal availableFunds(JSONObject json) {
        if (json == null) {
            return Monetary.zero();
        }
        BigDecimal cashLikeFunds = firstPositiveAccountMoney(
                json,
                "cash",
                "withdrawable_cash",
                "non_marginable_buying_power"
        );
        if (cashLikeFunds.compareTo(BigDecimal.ZERO) > 0) {
            return cashLikeFunds;
        }
        return firstPositiveAccountMoney(
                json,
                "buying_power",
                "regt_buying_power",
                "daytrading_buying_power"
        );
    }

    /**
     * Equity and last-close equity from a {@code /v2/account} body. Empty when equity is missing or not
     * positive, which is what an unfunded or unreadable account returns — never a real $0 reading.
     */
    static Optional<AlpacaAccountEquity> equity(JSONObject json) {
        if (json == null) {
            return Optional.empty();
        }
        BigDecimal equity = parseMoney(String.valueOf(json.opt("equity")));
        if (equity.signum() <= 0) {
            return Optional.empty();
        }
        BigDecimal lastEquity = parseMoney(String.valueOf(json.opt("last_equity")));
        return Optional.of(new AlpacaAccountEquity(equity, lastEquity.signum() > 0 ? lastEquity : equity));
    }

    private static BigDecimal firstPositiveAccountMoney(JSONObject json, String... keys) {
        for (String key : keys) {
            Object value = json.opt(key);
            if (value == null) {
                continue;
            }
            BigDecimal parsed = parseMoney(String.valueOf(value));
            if (parsed.compareTo(BigDecimal.ZERO) > 0) {
                return parsed;
            }
        }
        return Monetary.zero();
    }

    private static BigDecimal parseMoney(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value)) {
            return Monetary.zero();
        }
        try {
            return Monetary.round(new BigDecimal(value));
        } catch (NumberFormatException ex) {
            return Monetary.zero();
        }
    }
}
