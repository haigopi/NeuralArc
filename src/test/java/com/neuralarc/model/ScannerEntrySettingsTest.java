package com.neuralarc.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScannerEntrySettingsTest {
    @Test
    void defaultsToTheLowerOfTheTwoWeekLowAndTheCurrentLow() {
        ScannerEntrySettings defaults = ScannerEntrySettings.defaults();

        assertEquals(ScannerEntrySettings.Mode.TWO_WEEK_LOW_OR_CURRENT, defaults.mode());
        assertEquals(10, defaults.sessionsNeeded());
    }

    @Test
    void thePercentIsKeptBetweenZeroAndOneHundred() {
        assertEquals(BigDecimal.ZERO, new ScannerEntrySettings(null, null, new BigDecimal("-3")).percentAboveLow());
        assertEquals(BigDecimal.ZERO, new ScannerEntrySettings(null, null, null).percentAboveLow());
        assertEquals(new BigDecimal("100"), new ScannerEntrySettings(null, null, new BigDecimal("250")).percentAboveLow());
    }

    @Test
    void eachModeReadsOnlyAsMuchHistoryAsItNeeds() {
        assertEquals(252, new ScannerEntrySettings(ScannerEntrySettings.Mode.AUTO_TREND, null, null).sessionsNeeded());
        assertEquals(126, new ScannerEntrySettings(ScannerEntrySettings.Mode.CUSTOM_PERCENT,
                ScannerEntrySettings.Anchor.SIX_MONTHS, BigDecimal.TEN).sessionsNeeded());
        assertEquals(5, new ScannerEntrySettings(ScannerEntrySettings.Mode.CUSTOM_PERCENT,
                ScannerEntrySettings.Anchor.LAST_WEEK, BigDecimal.TEN).sessionsNeeded());
    }

    @Test
    void storedNamesParseAndUnknownOnesFallBack() {
        assertEquals(ScannerEntrySettings.Mode.AUTO_TREND,
                ScannerEntrySettings.parseMode(" auto_trend ", ScannerEntrySettings.Mode.CUSTOM_PERCENT));
        assertEquals(ScannerEntrySettings.Mode.CUSTOM_PERCENT,
                ScannerEntrySettings.parseMode("nonsense", ScannerEntrySettings.Mode.CUSTOM_PERCENT));
        assertEquals(ScannerEntrySettings.Anchor.ONE_YEAR,
                ScannerEntrySettings.parseAnchor("ONE_YEAR", ScannerEntrySettings.Anchor.TWO_WEEKS));
        assertEquals(ScannerEntrySettings.Anchor.TWO_WEEKS, ScannerEntrySettings.parseAnchor(null, ScannerEntrySettings.Anchor.TWO_WEEKS));
    }

    @Test
    void theSummaryNamesWhatWillHappen() {
        assertEquals("5% above the 1 month low", new ScannerEntrySettings(ScannerEntrySettings.Mode.CUSTOM_PERCENT,
                ScannerEntrySettings.Anchor.ONE_MONTH, new BigDecimal("5.00")).summary());
    }
}
