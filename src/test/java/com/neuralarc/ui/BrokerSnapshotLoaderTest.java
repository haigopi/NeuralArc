package com.neuralarc.ui;

import com.neuralarc.api.AlpacaPositionData;
import com.neuralarc.model.Position;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

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
}


