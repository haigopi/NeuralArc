package com.neuralarc.ui;

import com.neuralarc.service.StrategyEngine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Collapses the per-strategy rule analysis lines of one poll cycle into one line per distinct
 * result. Hundreds of strategies usually reach the same verdict ("No open position", "Loss buy
 * levels disabled"), so instead of one near-identical line each the log shows the verdict once with
 * the symbols it applies to. Strategies whose outcome differs (a price comparison, say) keep their own line.
 *
 * <p>Not thread-safe: fed and drained from the Swing EDT.
 */
final class RulesLogBatch {
    /** Past this many symbols a line shows the first few and a count, so one verdict cannot run on forever. */
    static final int MAX_SYMBOLS_LISTED = 40;

    private final Map<String, List<String>> symbolsBySummary = new LinkedHashMap<>();

    void add(String symbol, List<StrategyEngine.RuleOutcome> outcomes) {
        if (outcomes == null || outcomes.isEmpty()) {
            return;
        }
        String summary = outcomes.stream()
                .map(StrategyEngine.RuleOutcome::toString)
                .collect(Collectors.joining(" | "));
        symbolsBySummary.computeIfAbsent(summary, key -> new ArrayList<>())
                .add(symbol == null || symbol.isBlank() ? "-" : symbol.trim());
    }

    boolean isEmpty() {
        return symbolsBySummary.isEmpty();
    }

    /** One log message per distinct result, most common first; empties the batch. */
    List<String> drain() {
        List<String> messages = new ArrayList<>();
        symbolsBySummary.entrySet().stream()
                .sorted((left, right) -> Integer.compare(right.getValue().size(), left.getValue().size()))
                .forEach(entry -> messages.add(format(entry.getKey(), entry.getValue())));
        symbolsBySummary.clear();
        return messages;
    }

    private static String format(String summary, List<String> symbols) {
        if (symbols.size() == 1) {
            return "[RULES][" + symbols.get(0) + "] analyzed=" + summary;
        }
        String listed = symbols.size() <= MAX_SYMBOLS_LISTED
                ? String.join(", ", symbols)
                : String.join(", ", symbols.subList(0, MAX_SYMBOLS_LISTED)) + ", … +" + (symbols.size() - MAX_SYMBOLS_LISTED) + " more";
        return "[RULES][" + symbols.size() + " strategies] analyzed=" + summary + " :: " + listed;
    }
}
