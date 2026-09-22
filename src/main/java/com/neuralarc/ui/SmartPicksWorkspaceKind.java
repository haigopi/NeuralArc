package com.neuralarc.ui;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The three Smart Picks strategies that have their own workspace: which Smart Picks universe the
 * workspace reviews and runs, and when its autonomous scan defaults to — fitted to the strategy.
 */
enum SmartPicksWorkspaceKind {
    MOVERS("MOVERS", "High Volatility Movers",
            SmartPicksTrendingStocksDialog.StrategyUniverse.VOLATILE, PortfolioCaptureSmartPicksStrategy.VOLATILE,
            EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), LocalTime.of(10, 0),
            "every weekday at 10:00 ET, after the opening swings settle"),
    LEADERS("LEADERS", "Diversified Leaders",
            SmartPicksTrendingStocksDialog.StrategyUniverse.DIVERSIFIED_TOP_20, PortfolioCaptureSmartPicksStrategy.DIVERSIFIED_TOP_20,
            EnumSet.of(DayOfWeek.MONDAY), LocalTime.of(10, 0),
            "Mondays at 10:00 ET — a stable large-cap list, so once a week"),
    REBOUND("REBOUND", "Weekend Rebound",
            SmartPicksTrendingStocksDialog.StrategyUniverse.WEEKEND_REBOUND, PortfolioCaptureSmartPicksStrategy.WEEKEND_REBOUND,
            EnumSet.of(DayOfWeek.FRIDAY), LocalTime.of(15, 30),
            "Fridays at 3:30 PM ET, to buy Friday's decliners before the close for the Monday bounce");

    private final String code;
    private final String title;
    private final SmartPicksTrendingStocksDialog.StrategyUniverse universe;
    private final PortfolioCaptureSmartPicksStrategy automationStrategy;
    private final Set<DayOfWeek> defaultDays;
    private final LocalTime defaultScanTimeEt;
    private final String defaultScheduleText;

    SmartPicksWorkspaceKind(String code, String title, SmartPicksTrendingStocksDialog.StrategyUniverse universe,
                            PortfolioCaptureSmartPicksStrategy automationStrategy, Set<DayOfWeek> defaultDays,
                            LocalTime defaultScanTimeEt, String defaultScheduleText) {
        this.code = code;
        this.title = title;
        this.universe = universe;
        this.automationStrategy = automationStrategy;
        this.defaultDays = defaultDays;
        this.defaultScanTimeEt = defaultScanTimeEt;
        this.defaultScheduleText = defaultScheduleText;
    }

    String code() { return code; }
    String title() { return title; }
    SmartPicksTrendingStocksDialog.StrategyUniverse universe() { return universe; }
    PortfolioCaptureSmartPicksStrategy automationStrategy() { return automationStrategy; }
    Set<DayOfWeek> defaultDays() { return EnumSet.copyOf(defaultDays); }
    LocalTime defaultScanTimeEt() { return defaultScanTimeEt; }
    String defaultScheduleText() { return defaultScheduleText; }

    /** One line on what the strategy is for, shown atop the Smart Picks dialog and the workspace. */
    String purpose() {
        return switch (this) {
            case MOVERS -> "For short-term trades on today's biggest price swings: higher reward, higher risk.";
            case LEADERS -> "For steadier core positions in 20 sector-leading large caps, spread across the market.";
            case REBOUND -> "For buying Friday's controlled selloffs to catch the typical Monday bounce.";
        };
    }

    /** The kind that reviews this Smart Picks universe. */
    static SmartPicksWorkspaceKind forUniverse(SmartPicksTrendingStocksDialog.StrategyUniverse universe) {
        return Arrays.stream(values()).filter(kind -> kind.universe == universe).findFirst().orElse(MOVERS);
    }

    /** The kind for a workspace code, if the workspace is a Smart Picks one. */
    static Optional<SmartPicksWorkspaceKind> fromCode(String workspaceCode) {
        if (workspaceCode == null) {
            return Optional.empty();
        }
        String normalized = workspaceCode.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(kind -> kind.code.equals(normalized)).findFirst();
    }

    /** The kind that runs this Smart Picks strategy. */
    static SmartPicksWorkspaceKind forStrategy(PortfolioCaptureSmartPicksStrategy strategy) {
        return Arrays.stream(values()).filter(kind -> kind.automationStrategy == strategy).findFirst().orElse(MOVERS);
    }
}
