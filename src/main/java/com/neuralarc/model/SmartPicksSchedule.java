package com.neuralarc.model;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A persisted autonomous Smart Picks scan for one Smart Picks workspace (High Volatility Movers,
 * Diversified Leaders or Weekend Rebound). It runs on the chosen weekdays at {@code scanTimeEt}, using
 * the workspace's Smart Picks strategy, and either places the picks into the workspace or records them
 * in its scan history. NeuralArc must be running at that time.
 */
public record SmartPicksSchedule(
        String id,
        boolean enabled,
        String workspaceId,
        String workspaceCode,
        LocalTime scanTimeEt,
        Set<DayOfWeek> days,
        boolean executeAfterScan,
        int quantity,
        RecommendationType term,
        StrategyMode mode
) {
    public SmartPicksSchedule {
        id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        workspaceId = workspaceId == null ? "" : workspaceId;
        workspaceCode = workspaceCode == null ? "" : workspaceCode.toUpperCase(Locale.ROOT);
        scanTimeEt = scanTimeEt == null ? LocalTime.of(10, 0) : scanTimeEt;
        days = days == null || days.isEmpty()
                ? EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)
                : Set.copyOf(EnumSet.copyOf(days));
        quantity = Math.max(1, quantity);
        term = term == null ? RecommendationType.SHORT_TERM : term;
        mode = mode == null ? StrategyMode.PAPER : mode;
    }

    /** "MON,WED,FRI" — the stored form of {@link #days()}. */
    public String daysCode() {
        return EnumSet.copyOf(days).stream()
                .map(day -> day.name().substring(0, 3))
                .collect(Collectors.joining(","));
    }

    /** Parses {@link #daysCode()}; unknown or empty input means every weekday. */
    public static Set<DayOfWeek> parseDays(String code) {
        EnumSet<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        if (code != null) {
            for (String token : code.split(",")) {
                String trimmed = token.trim().toUpperCase(Locale.ROOT);
                Arrays.stream(DayOfWeek.values())
                        .filter(day -> day.name().startsWith(trimmed) && !trimmed.isEmpty())
                        .findFirst()
                        .ifPresent(days::add);
            }
        }
        return days.isEmpty() ? EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY) : days;
    }

    /** "Weekdays 10:00 ET", "Fri 15:30 ET". */
    public String summary() {
        String when = days.equals(EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))
                ? "Weekdays"
                : EnumSet.copyOf(days).stream()
                        .map(day -> day.name().charAt(0) + day.name().substring(1, 3).toLowerCase(Locale.ROOT))
                        .collect(Collectors.joining(", "));
        return when + " " + scanTimeEt + " ET";
    }
}
