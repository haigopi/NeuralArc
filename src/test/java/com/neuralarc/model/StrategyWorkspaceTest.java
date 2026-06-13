package com.neuralarc.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategyWorkspaceTest {
    @Test
    void normalizesCodeToShortUppercaseAlphanumeric() {
        assertEquals("ORBENGIN", StrategyWorkspace.normalizeCode("ORB Engine")); // 8-char cap
        assertEquals("VWAP", StrategyWorkspace.normalizeCode("vwap"));
        assertEquals("STRAT", StrategyWorkspace.normalizeCode("   !!!  "));
        assertEquals("STRAT", StrategyWorkspace.normalizeCode(null));
    }

    @Test
    void constructorNormalizesCodeAndTrimsName() {
        StrategyWorkspace w = new StrategyWorkspace("w1", "  ORB Engine  ", "orb", StrategyMode.PAPER, false, null, null);
        assertEquals("ORB Engine", w.name());
        assertEquals("ORB", w.code());
        assertEquals(StrategyMode.PAPER, w.mode());
        assertFalse(w.archived());
    }

    @Test
    void rejectsBlankIdAndNullMode() {
        assertThrows(IllegalArgumentException.class,
                () -> new StrategyWorkspace(" ", "ORB", "ORB", StrategyMode.PAPER, false, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new StrategyWorkspace("w1", "ORB", "ORB", null, false, null, null));
    }

    @Test
    void withHelpersProduceUpdatedCopies() {
        StrategyWorkspace original = new StrategyWorkspace("w1", "Old", "OLD", StrategyMode.LIVE, false, null, null);
        StrategyWorkspace renamed = original.withName("New");
        StrategyWorkspace archived = original.withArchived(true);

        assertEquals("New", renamed.name());
        assertEquals("w1", renamed.id());
        assertEquals("OLD", renamed.code());
        assertEquals(StrategyMode.LIVE, renamed.mode());
        assertTrue(archived.archived());
        // original is unchanged
        assertEquals("Old", original.name());
        assertFalse(original.archived());
    }
}
