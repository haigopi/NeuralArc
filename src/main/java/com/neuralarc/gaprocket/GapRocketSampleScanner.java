package com.neuralarc.gaprocket;

import java.math.BigDecimal;
import java.util.List;

/**
 * Local-first fallback candidate source used until broker/news scanner wiring is available.
 * The analyzer still applies the operator's filters before anything is added to a strategy grid.
 */
public final class GapRocketSampleScanner {
    public List<GapRocketCandidate> candidates() {
        return List.of(
                candidate("NVDA", "NVIDIA Corporation", "7.2", 5_200_000L, "6.1", "125.00", "116.60", "126.10", "121.40", GapRocketConfig.CatalystType.EARNINGS, "Earnings beat with strong AI datacenter guidance.", true, true, "0.35", "123.80"),
                candidate("AMD", "Advanced Micro Devices", "6.4", 3_800_000L, "4.8", "164.20", "154.30", "165.10", "159.80", GapRocketConfig.CatalystType.ANALYST_UPGRADE, "Analyst upgrade cites accelerating AI accelerator demand.", true, true, "0.45", "162.90"),
                candidate("MRNA", "Moderna, Inc.", "8.1", 2_400_000L, "5.3", "42.70", "39.50", "43.20", "41.10", GapRocketConfig.CatalystType.FDA_BIOTECH, "Positive biotech regulatory update before the open.", true, false, "0.70", "42.10"),
                candidate("PLTR", "Palantir Technologies Inc.", "5.8", 2_100_000L, "3.9", "27.40", "25.90", "27.95", "26.80", GapRocketConfig.CatalystType.CONTRACT_PARTNERSHIP, "New large enterprise contract announced premarket.", true, true, "0.55", "27.15"),
                candidate("XYZ", "Example Low Liquidity Co.", "5.1", 150_000L, "2.2", "6.25", "5.95", "6.40", "6.05", GapRocketConfig.CatalystType.GENERAL_BREAKING_NEWS, "Minor headline with limited liquidity.", true, true, "1.20", "6.18")
        );
    }

    private GapRocketCandidate candidate(String symbol, String company, String gap, long volume, String relVolume,
                                         String current, String previousClose, String high, String low,
                                         GapRocketConfig.CatalystType catalyst, String summary, boolean spyGreen,
                                         boolean qqqGreen, String spread, String vwap) {
        return new GapRocketCandidate(symbol, company, new BigDecimal(gap), volume, new BigDecimal(relVolume),
                new BigDecimal(current), new BigDecimal(previousClose), new BigDecimal(high), new BigDecimal(low),
                catalyst, summary, spyGreen, qqqGreen, new BigDecimal(spread), true, new BigDecimal(vwap));
    }
}
