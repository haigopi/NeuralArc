package com.neuralarc.db;

import com.neuralarc.model.StrategyMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteRemoteSyncSuppressionRepositoryTest {
    @TempDir
    Path tempDir;

    private SqliteRemoteSyncSuppressionRepository repository(String dbName) {
        return new SqliteRemoteSyncSuppressionRepository(AppDatabase.open(tempDir.resolve(dbName)));
    }

    @Test
    void suppressedSymbolSurvivesSoTheBrokerSyncCannotRecreateIt() {
        SqliteRemoteSyncSuppressionRepository repository = repository("suppress.db");

        repository.suppress("move", StrategyMode.PAPER);

        assertTrue(repository.suppressedSymbols(StrategyMode.PAPER).contains("MOVE"),
                "symbols must be stored normalized/uppercased");
    }

    @Test
    void suppressionIsScopedPerModeSoDeletingInPaperNeverHidesLive() {
        SqliteRemoteSyncSuppressionRepository repository = repository("modes.db");

        repository.suppress("MOVE", StrategyMode.PAPER);

        assertTrue(repository.suppressedSymbols(StrategyMode.PAPER).contains("MOVE"));
        assertFalse(repository.suppressedSymbols(StrategyMode.LIVE).contains("MOVE"),
                "a paper-mode deletion must not suppress the live-mode strategy");
    }

    @Test
    void clearingLetsTheSymbolBeTrackedAgain() {
        SqliteRemoteSyncSuppressionRepository repository = repository("clear.db");
        repository.suppress("MOVE", StrategyMode.PAPER);

        repository.clear("move", StrategyMode.PAPER);

        assertFalse(repository.suppressedSymbols(StrategyMode.PAPER).contains("MOVE"));
    }

    @Test
    void repeatedSuppressionOfTheSameSymbolIsIdempotent() {
        SqliteRemoteSyncSuppressionRepository repository = repository("idempotent.db");

        repository.suppress("MOVE", StrategyMode.PAPER);
        repository.suppress("MOVE", StrategyMode.PAPER);

        assertTrue(repository.suppressedSymbols(StrategyMode.PAPER).contains("MOVE"));
        assertTrue(repository.suppressedSymbols(StrategyMode.PAPER).size() == 1);
    }

    @Test
    void blankSymbolsAreIgnored() {
        SqliteRemoteSyncSuppressionRepository repository = repository("blank.db");

        repository.suppress("   ", StrategyMode.PAPER);
        repository.suppress(null, StrategyMode.PAPER);

        assertTrue(repository.suppressedSymbols(StrategyMode.PAPER).isEmpty());
    }
}
