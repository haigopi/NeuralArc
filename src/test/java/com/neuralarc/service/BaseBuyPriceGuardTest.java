package com.neuralarc.service;

import com.neuralarc.api.AlpacaClient;
import com.neuralarc.api.AlpacaOrderData;
import com.neuralarc.api.AlpacaPositionData;
import com.neuralarc.model.MarketBar;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BaseBuyPriceGuardTest {
    private final BaseBuyPriceGuard guard = new BaseBuyPriceGuard();

    @Test
    void keepsOriginalBasePriceWhenIndicatorsAreAboveLimit() {
        FakeAlpacaClient client = new FakeAlpacaClient(
                new BigDecimal("120.00"),
                List.of(bar("105.00", "110.00"))
        );

        BaseBuyPriceGuard.GuardedPrice result = guard.guardedBaseBuyPrice(
                client,
                "AAPL",
                new BigDecimal("100.00"),
                new BigDecimal("120.00")
        );

        assertEquals(new BigDecimal("100.00"), result.price());
    }

    @Test
    void reducesBasePriceWhenIndicatorsWeakenBelowLimit() {
        FakeAlpacaClient client = new FakeAlpacaClient(
                new BigDecimal("99.00"),
                List.of(bar("98.00", "101.00"))
        );

        BaseBuyPriceGuard.GuardedPrice result = guard.guardedBaseBuyPrice(
                client,
                "AAPL",
                new BigDecimal("100.00"),
                new BigDecimal("99.00")
        );

        assertEquals(new BigDecimal("96.04"), result.price());
    }

    @Test
    void reducesBasePriceWhenCurrentPriceEqualsLimit() {
        FakeAlpacaClient client = new FakeAlpacaClient(
                new BigDecimal("100.00"),
                List.of(bar("105.00", "110.00"))
        );

        BaseBuyPriceGuard.GuardedPrice result = guard.guardedBaseBuyPrice(
                client,
                "AAPL",
                new BigDecimal("100.00"),
                new BigDecimal("100.00")
        );

        assertEquals(new BigDecimal("98.00"), result.price());
    }

    @Test
    void reducesBasePriceBelowCurrentPriceWhenLimitIsAboveQuote() {
        FakeAlpacaClient client = new FakeAlpacaClient(
                new BigDecimal("95.00"),
                List.of(bar("105.00", "110.00"))
        );

        BaseBuyPriceGuard.GuardedPrice result = guard.guardedBaseBuyPrice(
                client,
                "AAPL",
                new BigDecimal("100.00"),
                new BigDecimal("95.00")
        );

        assertEquals(new BigDecimal("93.10"), result.price());
    }

    @Test
    void usesConfiguredReductionPercentWhenIndicatorsWeaken() {
        FakeAlpacaClient client = new FakeAlpacaClient(
                new BigDecimal("99.00"),
                List.of(bar("98.00", "101.00"))
        );

        BaseBuyPriceGuard.GuardedPrice result = guard.guardedBaseBuyPrice(
                client,
                "AAPL",
                new BigDecimal("100.00"),
                new BigDecimal("99.00"),
                new BigDecimal("5.00")
        );

        assertEquals(new BigDecimal("93.10"), result.price());
    }

    private static MarketBar bar(String low, String close) {
        return new MarketBar(
                "AAPL",
                "2026-06-10T20:00:00Z",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal(low),
                new BigDecimal(close),
                BigDecimal.ZERO
        );
    }

    private static final class FakeAlpacaClient implements AlpacaClient {
        private final BigDecimal latestPrice;
        private final List<MarketBar> dailyBars;

        private FakeAlpacaClient(BigDecimal latestPrice, List<MarketBar> dailyBars) {
            this.latestPrice = latestPrice;
            this.dailyBars = dailyBars;
        }

        @Override public AlpacaOrderData submitLimitBuyOrder(String symbol, int quantity, BigDecimal limitPrice, String clientOrderId) { return null; }
        @Override public AlpacaOrderData submitLimitSellOrder(String symbol, int quantity, BigDecimal limitPrice, String clientOrderId) { return null; }
        @Override public AlpacaOrderData submitMarketSellOrder(String symbol, int quantity, String clientOrderId) { return null; }
        @Override public AlpacaOrderData submitTrailingStopSellOrder(String symbol, int quantity, BigDecimal trailPercent, BigDecimal trailPrice, String clientOrderId) { return null; }
        @Override public Optional<AlpacaOrderData> getOrder(String orderId) { return Optional.empty(); }
        @Override public List<AlpacaOrderData> getOpenOrders(String symbol) { return List.of(); }
        @Override public List<AlpacaOrderData> getOpenOrders() { return List.of(); }
        @Override public boolean cancelOrder(String orderId) { return false; }
        @Override public Optional<AlpacaPositionData> getPosition(String symbol) { return Optional.empty(); }
        @Override public List<AlpacaPositionData> getPositions() { return List.of(); }
        @Override public BigDecimal getLatestPrice(String symbol) { return latestPrice; }
        @Override public List<MarketBar> getDailyBars(String symbol, LocalDate startDate, LocalDate endDate) { return dailyBars; }
    }
}
