package com.neuralarc.db;

import com.neuralarc.model.ProfitControlMode;
import com.neuralarc.model.ProfitHoldType;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyConfig;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.ThresholdType;
import com.neuralarc.model.TimeInForce;
import com.neuralarc.model.TrailingType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqliteStrategyRepositoryTest {
    @TempDir
    Path tempDir;

    @Test
    void persistsBaseBuyRepostReductionPercent() {
        AppDatabase db = AppDatabase.open(tempDir.resolve("neuralarc.db"));
        SqliteStrategyRepository repository = new SqliteStrategyRepository(db);
        Strategy strategy = Strategy.fromConfig("s1", "Test", config(), StrategyMode.PAPER);

        repository.save(strategy);

        Strategy loaded = repository.findById("s1").orElseThrow();
        assertEquals(new BigDecimal("5.00"), loaded.baseBuyRepostReductionPercent());
        assertEquals(TimeInForce.GTC, loaded.timeInForce());
    }

    private StrategyConfig config() {
        return new StrategyConfig(
                "NEO",
                new BigDecimal("8.00"),
                10,
                true,
                new BigDecimal("7.00"),
                true,
                new BigDecimal("10.00"),
                new BigDecimal("7.40"),
                5,
                new BigDecimal("6.40"),
                5,
                true,
                false,
                BigDecimal.ZERO,
                2,
                true,
                false,
                false,
                ProfitHoldType.PERCENT_TRAILING,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                false,
                ProfitControlMode.NONE,
                ThresholdType.FIXED_AMOUNT,
                BigDecimal.ZERO,
                TrailingType.PERCENTAGE,
                BigDecimal.ZERO,
                true,
                new BigDecimal("5.00"),
                TimeInForce.GTC
        );
    }
}
