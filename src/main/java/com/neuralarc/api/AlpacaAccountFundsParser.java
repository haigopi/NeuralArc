package com.neuralarc.api;

import com.neuralarc.util.Monetary;
import org.json.JSONObject;

import java.math.BigDecimal;

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
