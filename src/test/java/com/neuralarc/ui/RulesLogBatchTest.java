package com.neuralarc.ui;

import com.neuralarc.service.StrategyEngine.RuleOutcome;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RulesLogBatchTest {
    private static List<RuleOutcome> noPosition() {
        return List.of(new RuleOutcome("STOP_LOSS", "SKIPPED", "No open position"),
                new RuleOutcome("BUY_LIMIT_1", "SKIPPED", "Loss buy levels disabled"));
    }

    private static List<RuleOutcome> baseNotFilled() {
        return List.of(new RuleOutcome("STOP_LOSS", "SKIPPED", "No open position"),
                new RuleOutcome("BUY_LIMIT_1", "SKIPPED", "Base buy not fully filled"));
    }

    @Test
    void strategiesWithTheSameResultShareOneLine() {
        RulesLogBatch batch = new RulesLogBatch();
        batch.add("META", noPosition());
        batch.add("CNTB", noPosition());
        batch.add("CRDO", noPosition());

        List<String> lines = batch.drain();

        assertEquals(1, lines.size());
        assertEquals("[RULES][3 strategies] analyzed=STOP_LOSS=SKIPPED (No open position) | "
                + "BUY_LIMIT_1=SKIPPED (Loss buy levels disabled) :: META, CNTB, CRDO", lines.get(0));
    }

    @Test
    void differentResultsKeepSeparateLinesMostCommonFirst() {
        RulesLogBatch batch = new RulesLogBatch();
        batch.add("MSFT", baseNotFilled());
        batch.add("META", noPosition());
        batch.add("CNTB", noPosition());

        List<String> lines = batch.drain();

        assertEquals(2, lines.size());
        assertTrue(lines.get(0).startsWith("[RULES][2 strategies]"), lines.get(0));
        assertTrue(lines.get(1).startsWith("[RULES][MSFT] analyzed="), "a lone strategy keeps the per-symbol form");
    }

    @Test
    void aLongSymbolListIsCappedWithACount() {
        RulesLogBatch batch = new RulesLogBatch();
        for (int i = 0; i < RulesLogBatch.MAX_SYMBOLS_LISTED + 5; i++) {
            batch.add("S" + i, noPosition());
        }

        String line = batch.drain().getFirst();

        assertTrue(line.endsWith(", … +5 more"), line);
        assertTrue(line.contains("[RULES][45 strategies]"));
    }

    @Test
    void drainingEmptiesTheBatchAndEmptyOutcomesAreIgnored() {
        RulesLogBatch batch = new RulesLogBatch();
        batch.add("META", List.of());
        batch.add("META", null);
        assertTrue(batch.isEmpty());

        batch.add("META", noPosition());
        batch.drain();

        assertTrue(batch.isEmpty());
        assertTrue(batch.drain().isEmpty());
    }
}
