package com.neuralarc.model;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * When NeuralArc emails the portfolio snapshot. Times are US Eastern clock times on trading days.
 * The defaults sit around the regular session: 5 minutes before the 9:30 open, 10 minutes after it,
 * lunch, 5 minutes before the 4:00 close and 10 minutes after it. Snapshots, like every NeuralArc
 * email, go to the user's email from Settings.
 */
public record PortfolioEmailSettings(boolean enabled, List<Slot> slots) {
    public static final boolean DEFAULT_ENABLED = true;
    public static final String CUSTOM_LABEL = "Custom";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    /** One send time: its name (used in the email subject), its Eastern time, and whether it is on. */
    public record Slot(String label, LocalTime timeEt, boolean enabled) {
        public Slot {
            // Labels are stored with '@' and ';' as separators, so neither may appear in one.
            label = label == null || label.isBlank() ? CUSTOM_LABEL : label.replace('@', ' ').replace(';', ' ').trim();
            timeEt = (timeEt == null ? LocalTime.NOON : timeEt).withSecond(0).withNano(0);
        }
    }

    public PortfolioEmailSettings {
        slots = slots == null ? List.of() : slots.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Slot::timeEt))
                .toList();
    }

    public static PortfolioEmailSettings defaults() {
        return new PortfolioEmailSettings(DEFAULT_ENABLED, defaultSlots());
    }

    public static List<Slot> defaultSlots() {
        return List.of(
                new Slot("Before the open", LocalTime.of(9, 25), true),
                new Slot("After the open", LocalTime.of(9, 40), true),
                new Slot("Lunch", LocalTime.of(12, 0), true),
                new Slot("Before the close", LocalTime.of(15, 55), true),
                new Slot("After the close", LocalTime.of(16, 10), true));
    }

    public static boolean isDefaultLabel(String label) {
        return defaultSlots().stream().anyMatch(slot -> slot.label().equals(label));
    }

    /** The times that will actually send: none while the snapshots are switched off. */
    public List<Slot> activeSlots() {
        return enabled ? slots.stream().filter(Slot::enabled).toList() : List.of();
    }

    /** "on at 09:25, 09:40 ET on trading days", or "off". */
    public String describe() {
        List<Slot> active = activeSlots();
        if (active.isEmpty()) {
            return "off";
        }
        return "on at " + active.stream().map(slot -> slot.timeEt().format(TIME)).collect(Collectors.joining(", "))
                + " ET on trading days";
    }

    /** "Before the open@09:25@1;Lunch@12:00@0" for storage. */
    public String encodeSlots() {
        return slots.stream()
                .map(slot -> slot.label() + "@" + slot.timeEt().format(TIME) + "@" + (slot.enabled() ? "1" : "0"))
                .collect(Collectors.joining(";"));
    }

    /** Reads {@link #encodeSlots()} output; malformed entries are dropped, and nothing usable means the defaults. */
    public static List<Slot> decodeSlots(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return defaultSlots();
        }
        List<Slot> slots = new ArrayList<>();
        for (String entry : encoded.split(";")) {
            String[] parts = entry.split("@");
            if (parts.length != 3) {
                continue;
            }
            try {
                slots.add(new Slot(parts[0], LocalTime.parse(parts[1].trim(), TIME), "1".equals(parts[2].trim())));
            } catch (DateTimeParseException ignored) {
                // Skip an entry that no longer parses rather than losing every time.
            }
        }
        return slots.isEmpty() ? defaultSlots() : slots;
    }
}
