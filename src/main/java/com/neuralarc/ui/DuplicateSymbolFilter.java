package com.neuralarc.ui;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Which symbols appear on more than one row of a grid, for the All Stocks "Duplicates only" filter. */
final class DuplicateSymbolFilter {
    private DuplicateSymbolFilter() {
    }

    /** @return the upper-cased symbols that occur at least twice in {@code symbols}; blanks never count. */
    static Set<String> duplicated(Collection<String> symbols) {
        Map<String, Integer> counts = new HashMap<>();
        if (symbols != null) {
            for (String symbol : symbols) {
                if (symbol != null && !symbol.isBlank()) {
                    counts.merge(normalize(symbol), 1, Integer::sum);
                }
            }
        }
        Set<String> duplicated = new HashSet<>();
        counts.forEach((symbol, count) -> {
            if (count > 1) {
                duplicated.add(symbol);
            }
        });
        return duplicated;
    }

    static boolean isDuplicate(Set<String> duplicated, String symbol) {
        return symbol != null && duplicated.contains(normalize(symbol));
    }

    private static String normalize(String symbol) {
        return symbol.trim().toUpperCase(Locale.ROOT);
    }
}
