package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuplicateSymbolFilterTest {
    @Test
    void symbolsOnMoreThanOneRowAreDuplicatesRegardlessOfCase() {
        Set<String> duplicated = DuplicateSymbolFilter.duplicated(List.of("TQQQ", "aapl", "tqqq", "NVDA", "AAPL ", "AAPL"));

        assertEquals(Set.of("TQQQ", "AAPL"), duplicated);
        assertTrue(DuplicateSymbolFilter.isDuplicate(duplicated, "tqqq"));
        assertFalse(DuplicateSymbolFilter.isDuplicate(duplicated, "NVDA"));
    }

    @Test
    void blanksAndNullsNeverCountAsDuplicates() {
        assertTrue(DuplicateSymbolFilter.duplicated(Arrays.asList("", " ", null, null)).isEmpty());
        assertTrue(DuplicateSymbolFilter.duplicated(null).isEmpty());
        assertFalse(DuplicateSymbolFilter.isDuplicate(Set.of("A"), null));
    }
}
