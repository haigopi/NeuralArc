package com.neuralarc.service;

import com.neuralarc.db.AppDatabase;
import com.neuralarc.db.SqliteStrategyRepository;
import com.neuralarc.model.AutoAdjustRiskConfig;
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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class AutoRiskAdjustmentServiceTest {
    private static final ZoneId ET = ZoneId.of("America/New_York");
    private static final LocalDate TRADING_DAY = LocalDate.of(2026, 6, 15);   // Monday
    private static final LocalDate WEEKEND = LocalDate.of(2026, 6, 13);       // Saturday

    @TempDir
    Path tempDir;

    private SqliteStrategyRepository repository() {
        return new SqliteStrategyRepository(AppDatabase.open(tempDir.resolve("neuralarc.db")));
    }

    private Strategy activeStrategy() {
        StrategyConfig config = new StrategyConfig(
                "TEST", new BigDecimal("400"), 10, true, new BigDecimal("365"),
                false, BigDecimal.ZERO,
                new BigDecimal("380"), 5, new BigDecimal("370"), 5, true,
                false, BigDecimal.ZERO, 10, true, false, false,
                ProfitHoldType.PERCENT_TRAILING, BigDecimal.ZERO, BigDecimal.ZERO, false,
                ProfitControlMode.NONE, ThresholdType.FIXED_AMOUNT, BigDecimal.ZERO,
                TrailingType.PERCENTAGE, BigDecimal.ZERO, false,
                StrategyConfig.DEFAULT_BASE_BUY_REPOST_REDUCTION_PERCENT, TimeInForce.DAY,
                new AutoAdjustRiskConfig(true, 3, new BigDecimal("5"), true, true, true));
        return Strategy.fromConfig("s1", "Test", config, StrategyMode.PAPER);
    }

    private static Instant et(LocalDate date, int hour, int minute) {
        return ZonedDateTime.of(date, LocalTime.of(hour, minute), ET).toInstant();
    }

    private AutoRiskAdjustmentService service(SqliteStrategyRepository repo, Function<Strategy, BigDecimal> price) {
        return new AutoRiskAdjustmentService(repo, new MarketHoursService(), Clock.systemUTC(), price, null);
    }

    @Test
    void detectsAfterRegularClose() {
        AutoRiskAdjustmentService svc = service(repository(), s -> new BigDecimal("385"));
        assertTrue(svc.isAfterRegularClose(et(TRADING_DAY, 16, 30)));
        assertFalse(svc.isAfterRegularClose(et(TRADING_DAY, 15, 0)));   // before close
        assertFalse(svc.isAfterRegularClose(et(WEEKEND, 16, 30)));      // closed day
    }

    @Test
    void adjustsAndPersistsAfterCloseOnDownwardTrend() {
        SqliteStrategyRepository repo = repository();
        repo.save(activeStrategy());
        AutoRiskAdjustmentService svc = service(repo, s -> new BigDecimal("385"));

        int adjusted = svc.runAfterCloseAdjustments(et(TRADING_DAY, 16, 30));

        assertEquals(1, adjusted);
        Strategy reloaded = repo.findById("s1").orElseThrow();
        assertEquals(new BigDecimal("346.75"), reloaded.stopLossPrice()); // 365 * 0.95
        assertEquals(1, reloaded.autoAdjustDayCount());
        assertEquals("2026-06-15", reloaded.autoAdjustLastAdjustedDate());
        assertEquals(new BigDecimal("385.00"), reloaded.autoAdjustReferencePrice());
    }

    @Test
    void doesNotRunBeforeMarketClose() {
        SqliteStrategyRepository repo = repository();
        repo.save(activeStrategy());
        AutoRiskAdjustmentService svc = service(repo, s -> new BigDecimal("385"));

        assertEquals(0, svc.runAfterCloseAdjustments(et(TRADING_DAY, 14, 0)));
        assertEquals(0, repo.findById("s1").orElseThrow().autoAdjustDayCount());
    }

    @Test
    void doesNotAdjustTwiceOnTheSameMarketDay() {
        SqliteStrategyRepository repo = repository();
        repo.save(activeStrategy());
        AutoRiskAdjustmentService svc = service(repo, s -> new BigDecimal("385"));

        assertEquals(1, svc.runAfterCloseAdjustments(et(TRADING_DAY, 16, 30)));
        assertEquals(0, svc.runAfterCloseAdjustments(et(TRADING_DAY, 16, 45))); // same day, second pass
        assertEquals(1, repo.findById("s1").orElseThrow().autoAdjustDayCount());
    }

    @Test
    void skipsStrategiesWithoutAPrice() {
        SqliteStrategyRepository repo = repository();
        repo.save(activeStrategy());
        AutoRiskAdjustmentService svc = service(repo, s -> null);

        assertEquals(0, svc.runAfterCloseAdjustments(et(TRADING_DAY, 16, 30)));
        assertEquals(0, repo.findById("s1").orElseThrow().autoAdjustDayCount());
    }

    @Test
    void persistsAutoAdjustConfigAcrossReload() {
        SqliteStrategyRepository repo = repository();
        repo.save(activeStrategy());

        repo.invalidateCache(); // force reload from SQLite
        Strategy loaded = repo.findById("s1").orElseThrow();
        assertTrue(loaded.autoAdjustRiskEnabled());
        assertEquals(3, loaded.autoAdjustMonitoringDays());
        assertEquals(new BigDecimal("5.00"), loaded.autoAdjustDailyPercent());
        assertTrue(loaded.autoAdjustOnDecrease());
        assertTrue(loaded.autoAdjustOnIncrease());
    }
}
