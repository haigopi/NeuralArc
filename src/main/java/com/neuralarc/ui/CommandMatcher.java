package com.neuralarc.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Ranks Portfolio Actions against what the operator typed.
 *
 * <p>The menu is thirty-odd actions behind eight submenus — fine to browse, slow to reach when you
 * already know what you want. Typing is faster than navigating, and unlike asking the analyst it is
 * instant, free and deterministic: the same letters always select the same action, which is the
 * property that matters when the action places or cancels real orders.
 *
 * <p>Every typed word must appear somewhere, so a query can only narrow the list, never wander into
 * an unrelated action. Where a word appears decides the order: the name outranks the group, which
 * outranks the description, and a word starting a word in the name outranks one buried inside it.
 */
final class CommandMatcher {
    /** A command as the palette shows it. */
    record Command(String label, String group, String description, boolean enabled, Runnable action) {
        Command {
            label = label == null ? "" : label;
            group = group == null ? "" : group;
            description = description == null ? "" : description;
        }
    }

    private CommandMatcher() {
    }

    /** The commands matching {@code query}, best first; everything, in menu order, for a blank query. */
    static List<Command> match(List<Command> commands, String query) {
        if (commands == null || commands.isEmpty()) {
            return List.of();
        }
        String cleaned = clean(query);
        if (cleaned.isEmpty()) {
            return List.copyOf(commands);
        }
        List<String> tokens = List.of(cleaned.split("\\s+"));
        List<Scored> scored = new ArrayList<>();
        for (int index = 0; index < commands.size(); index++) {
            Command command = commands.get(index);
            int score = score(command, tokens, cleaned);
            if (score > 0) {
                scored.add(new Scored(command, score, index));
            }
        }
        return scored.stream()
                .sorted(Comparator.comparingInt(Scored::score).reversed()
                        .thenComparingInt(Scored::menuOrder))
                .map(Scored::command)
                .toList();
    }

    /** 0 when any typed word appears nowhere; higher means a closer match. */
    static int score(Command command, List<String> tokens, String wholeQuery) {
        String label = command.label().toLowerCase(Locale.ROOT);
        String group = command.group().toLowerCase(Locale.ROOT);
        String description = command.description().toLowerCase(Locale.ROOT);
        int score = 0;
        for (String token : tokens) {
            int best = 0;
            if (startsWord(label, token)) {
                best = 6;
            } else if (label.contains(token)) {
                best = 3;
            } else if (group.contains(token)) {
                best = 2;
            } else if (description.contains(token)) {
                best = 1;
            }
            if (best == 0) {
                return 0;
            }
            score += best;
        }
        if (label.startsWith(wholeQuery)) {
            score += 10;
        }
        if (label.equals(wholeQuery)) {
            score += 20;
        }
        return score;
    }

    /** The leading slash is how the operator says "command, not question"; it is not part of the query. */
    static String clean(String query) {
        String text = query == null ? "" : query.trim();
        while (text.startsWith("/")) {
            text = text.substring(1).trim();
        }
        return text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static boolean startsWord(String haystack, String token) {
        int index = haystack.indexOf(token);
        while (index >= 0) {
            if (index == 0 || !Character.isLetterOrDigit(haystack.charAt(index - 1))) {
                return true;
            }
            index = haystack.indexOf(token, index + 1);
        }
        return false;
    }

    private record Scored(Command command, int score, int menuOrder) {
    }
}
