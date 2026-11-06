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
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void aPlannedPriceBelowTheMarketIsKeptAsIs() {
        FakeAlpacaClient client = new FakeAlpacaClient(
                new BigDecimal("186.91"),
                List.of(bar("185.00", "187.00"))
        );

        BaseBuyPriceGuard.GuardedPrice result = guard.guardedBaseBuyPrice(
                client, "NVDA", new BigDecimal("184.00"), new BigDecimal("186.91"));

        assertEquals(new BigDecimal("184.00"), result.price());
    }

    @Test
    void aPriceTheOperatorChoseBelowTheMarketIsPlacedAsTyped() {
        FakeAlpacaClient client = new FakeAlpacaClient(
                new BigDecimal("186.91"),
                List.of(bar("185.00", "187.00"))
        );

        BaseBuyPriceGuard.GuardedPrice result = guard.guardedBaseBuyPrice(
                client, "NVDA", new BigDecimal("184.00"), new BigDecimal("186.91"),
                BaseBuyPriceGuard.DEFAULT_WEAKNESS_REDUCTION_PERCENT, true);

        assertEquals(new BigDecimal("184.00"), result.price());
        assertEquals("", result.reason(), "nothing was changed, so nothing is logged as an adjustment");
    }

    @Test
    void aChosenPriceIsNotReducedEvenWhenYesterdayWasWeaker() {
        FakeAlpacaClient client = new FakeAlpacaClient(
                new BigDecimal("99.00"),
                List.of(bar("98.00", "101.00"))
        );

        BaseBuyPriceGuard.GuardedPrice result = guard.guardedBaseBuyPrice(
                client, "AAPL", new BigDecimal("90.00"), new BigDecimal("99.00"),
                BaseBuyPriceGuard.DEFAULT_WEAKNESS_REDUCTION_PERCENT, true);

        assertEquals(new BigDecimal("90.00"), result.price());
        assertEquals("", result.reason());
    }

    @Test
    void aChosenPriceAboveTheMarketIsCorrectedToTheCurrentPrice() {
        FakeAlpacaClient client = new FakeAlpacaClient(
                new BigDecimal("99.00"),
                List.of(bar("99.00", "101.00"))
        );

        BaseBuyPriceGuard.GuardedPrice result = guard.guardedBaseBuyPrice(
                client, "AAPL", new BigDecimal("105.00"), new BigDecimal("99.00"),
                BaseBuyPriceGuard.DEFAULT_WEAKNESS_REDUCTION_PERCENT, true);

        assertEquals(new BigDecimal("99.00"), result.price(), "the week's low is not under the market, so the market price is used");
        assertTrue(result.reason().startsWith("WARNING"), result.reason());
        assertTrue(result.reason().contains("$105.00") && result.reason().contains("current price"), result.reason());
    }

    @Test
    void aChosenPriceAboveTheMarketUsesTheLastWeeksLowWhenItIsLower() {
        FakeAlpacaClient client = new FakeAlpacaClient(
                new BigDecimal("99.00"),
                List.of(bar("97.50", "101.00"), bar("96.25", "98.00"))
        );

        BaseBuyPriceGuard.GuardedPrice result = guard.guardedBaseBuyPrice(
                client, "AAPL", new BigDecimal("105.00"), new BigDecimal("99.00"),
                BaseBuyPriceGuard.DEFAULT_WEAKNESS_REDUCTION_PERCENT, true);

        assertEquals(new BigDecimal("96.25"), result.price());
        assertTrue(result.reason().contains("last week's low"), result.reason());
    }

    @Test
    void aChosenPriceAboveTheMarketWithNoBarsFallsBackToTheCurrentPrice() {
        FakeAlpacaClient client = new FakeAlpacaClient(new BigDecimal("99.00"), List.of());

        BaseBuyPriceGuard.GuardedPrice result = guard.guardedBaseBuyPrice(
                client, "AAPL", new BigDecimal("105.00"), new BigDecimal("99.00"),
                BaseBuyPriceGuard.DEFAULT_WEAKNESS_REDUCTION_PERCENT, true);

        assertEquals(new BigDecimal("99.00"), result.price());
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
