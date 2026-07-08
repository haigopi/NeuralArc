package com.neuralarc.ui;

import com.neuralarc.api.AlpacaPositionData;
import com.neuralarc.api.HttpAlpacaClient;
import com.neuralarc.model.ApplicationMode;
import com.neuralarc.model.Position;
import com.neuralarc.model.ProfitHoldType;
import com.neuralarc.model.StopLossType;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BrokerSnapshotLoaderTest {
    @Test
    void buildPositionSnapshotUsesRemotePositionWhenOpen() {
        Position snapshot = BrokerSnapshotLoader.buildPositionSnapshot(
                "AAPL",
                new AlpacaPositionData("AAPL", new BigDecimal("10"), new BigDecimal("100.00"), new BigDecimal("105.00"), "{}"),
                new BigDecimal("999.99")
        );

        assertEquals(10, snapshot.getTotalShares());
        assertEquals(new BigDecimal("100.00"), snapshot.getAverageCost());
        assertEquals(new BigDecimal("105.00"), snapshot.getLastPrice());
        assertEquals(new BigDecimal("50.00"), snapshot.unrealizedPnl());
    }

    @Test
    void buildPositionSnapshotFallsBackToLatestPriceWhenNoOpenPosition() {
        Position snapshot = BrokerSnapshotLoader.buildPositionSnapshot(
                "MSFT",
                new AlpacaPositionData("MSFT", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "{}"),
                new BigDecimal("123.45")
        );

        assertEquals(0, snapshot.getTotalShares());
        assertEquals(new BigDecimal("0.00"), snapshot.getAverageCost());
        assertEquals(new BigDecimal("123.45"), snapshot.getLastPrice());
        assertEquals(new BigDecimal("0.00"), snapshot.getRealizedPnl());
    }

    @Test
    void loadPositionSnapshotsUsesProvidedPositionResolver() {
        Strategy strategy = strategy("s1", "AAPL");
        FakeHttpAlpacaClient client = new FakeHttpAlpacaClient();
        AtomicInteger resolverCalls = new AtomicInteger();

        Map<String, Position> snapshots = BrokerSnapshotLoader.loadPositionSnapshots(
                List.of(strategy),
                mode -> client,
                ignored -> true,
                (mode, ignoredClient) -> {
                    assertEquals(ApplicationMode.PAPER, mode);
                    resolverCalls.incrementAndGet();
                    return List.of(new AlpacaPositionData("AAPL", new BigDecimal("7"), new BigDecimal("100.00"), new BigDecimal("104.00"), "{}"));
                }
        );

        assertEquals(1, resolverCalls.get());
        assertEquals(0, client.directPositionCalls);
        assertEquals(7, snapshots.get("s1").getTotalShares());
        assertEquals(new BigDecimal("104.00"), snapshots.get("s1").getLastPrice());
    }

    private static Strategy strategy(String id, String symbol) {
        return new Strategy(
                id,
                symbol + " Strategy",
                symbol,
                StrategyMode.PAPER,
                StrategyStatus.ACTIVE,
                StrategyLifecycleState.CREATED,
                new BigDecimal("10"),
                1,
                new BigDecimal("9"),
                1,
                new BigDecimal("8"),
                1,
                true,
                StopLossType.FIXED_PRICE,
                new BigDecimal("7"),
                new BigDecimal("1"),
                false,
                BigDecimal.ZERO,
                true,
                new BigDecimal("11"),
                new BigDecimal("100"),
                false,
                false,
                ProfitHoldType.PERCENT_TRAILING,
                new BigDecimal("1"),
                new BigDecimal("1"),
                BigDecimal.ZERO,
                false,
                10,
                new BigDecimal("1000"),
                2,
                Instant.now(),
                Instant.now()
        );
    }

    private static final class FakeHttpAlpacaClient extends HttpAlpacaClient {
        int directPositionCalls;

        FakeHttpAlpacaClient() {
            super("", "", "http://localhost", "http://localhost");
        }

        @Override
        public Map<String, BigDecimal> getLatestPrices(List<String> symbols) {
            return Map.of("AAPL", new BigDecimal("103.00"));
        }

        @Override
        public List<AlpacaPositionData> getPositions() {
            directPositionCalls++;
            return List.of();
        }
    }
}

