package com.neuralarc.analytics;

import java.math.BigDecimal;

/**
 * The scanners' shared entry rule: given the entry a scanner planned on its own, returns the entry to
 * use once the operator's entry-level setting is applied. It only ever lowers an entry, never raises it.
 */
@FunctionalInterface
public interface ScannerEntryPricing {
    /**
     * @param ownEntry the entry the scanner planned
     * @param todayLow the low so far today, or the premarket low for premarket scanners
     */
    BigDecimal entry(String symbol, BigDecimal ownEntry, BigDecimal current, BigDecimal open, BigDecimal todayLow);

    /** Leaves every scanner's own entry as planned; what scanners use until pricing is configured. */
    static ScannerEntryPricing unchanged() {
        return (symbol, ownEntry, current, open, todayLow) -> ownEntry;
    }
}
