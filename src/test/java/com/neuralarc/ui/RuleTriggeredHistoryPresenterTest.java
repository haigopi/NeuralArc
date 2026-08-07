package com.neuralarc.ui;

import com.neuralarc.model.StrategyOrder;
import com.neuralarc.model.StrategyOrderSide;
import com.neuralarc.model.StrategyOrderStatus;
import com.neuralarc.model.StrategyOrderType;
import com.neuralarc.model.StrategyStage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleTriggeredHistoryPresenterTest {
    private final RuleTriggeredHistoryPresenter presenter = new RuleTriggeredHistoryPresenter();

    @Test
    void labelShowsCurrentRuleAndPastPlacedPartialFilledAndSoldHistory() {
        Instant baseTime = Instant.parse("2026-05-18T14:30:00Z");
        StrategyOrder baseBuy = order(
                StrategyStage.BASE_BUY,
                StrategyOrderSide.BUY,
                new BigDecimal("100.00"),
                new BigDecimal("10"),
                new BigDecimal("5"),
                new BigDecimal("100.25"),
                StrategyOrderStatus.PARTIALLY_FILLED,
                baseTime
        );
        StrategyOrder sell = order(
                StrategyStage.TARGET_SELL,
                StrategyOrderSide.SELL,
                new BigDecimal("110.00"),
                new BigDecimal("5"),
                new BigDecimal("5"),
                new BigDecimal("110.50"),
                StrategyOrderStatus.FILLED,
                baseTime.plusSeconds(3600)
        );

        String label = presenter.buildLabel(
                "Rules: Sell trigger active @ $110.00 | Waiting 5m",
                List.of(baseBuy, sell),
                instant -> instant.toString()
        );

        assertTrue(label.contains("<b>Current:</b> Sell trigger active @ $110.00 | Waiting 5m"));
        // Timeline layout: each step is its own row with the timestamp in a separate aligned cell.
        assertTrue(label.contains("Base Buy placed @ $100.00/10"), label);
        assertTrue(label.contains("2026-05-18T14:30:00Z"), label);
        assertTrue(label.contains("Base Buy partially filled @ $100.25/5"), label);
        assertTrue(label.contains("Target Sell sold @ $110.50/5"), label);
        assertTrue(label.contains("<table"), label);
    }

    @Test
    void labelFallsBackToCurrentRuleWhenThereIsNoHistory() {
        String label = presenter.buildLabel("Rules: Monitoring next configured rule", List.of(), Instant::toString);

        assertTrue(label.equals("Rules: Monitoring next configured rule"));
    }

    @Test
    void labelSkipsSupersededFailedRuleWhenLaterStageFillExists() {
        Instant baseTime = Instant.parse("2026-05-18T14:30:00Z");
        StrategyOrder failedBaseBuy = order(
                StrategyStage.BASE_BUY,
                StrategyOrderSide.BUY,
                new BigDecimal("100.00"),
                new BigDecimal("10"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                StrategyOrderStatus.FAILED,
                baseTime
        );
        StrategyOrder filledBaseBuy = order(
                StrategyStage.BASE_BUY,
                StrategyOrderSide.BUY,
                new BigDecimal("99.75"),
                new BigDecimal("10"),
                new BigDecimal("10"),
                new BigDecimal("99.75"),
                StrategyOrderStatus.FILLED,
                baseTime.plusSeconds(180)
        );

        String label = presenter.buildLabel(
                "Rules: Base Buy Filled",
                List.of(failedBaseBuy, filledBaseBuy),
                Instant::toString
        );

        assertFalse(label.contains("Base Buy failed"));
        assertTrue(label.contains("Base Buy filled @ $99.75/10"));
    }

    @Test
    void labelConsolidatesRepeatedFailedRuleAttempts() {
        Instant baseTime = Instant.parse("2026-05-18T14:30:00Z");
        StrategyOrder firstFailed = order(
                StrategyStage.BASE_BUY,
                StrategyOrderSide.BUY,
                new BigDecimal("100.00"),
                new BigDecimal("10"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                StrategyOrderStatus.FAILED,
                baseTime
        );
        StrategyOrder secondFailed = order(
                StrategyStage.BASE_BUY,
                StrategyOrderSide.BUY,
                new BigDecimal("100.00"),
                new BigDecimal("10"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                StrategyOrderStatus.FAILED,
                baseTime.plusSeconds(60)
        );
        StrategyOrder thirdFailed = order(
                StrategyStage.BASE_BUY,
                StrategyOrderSide.BUY,
                new BigDecimal("100.00"),
                new BigDecimal("10"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                StrategyOrderStatus.FAILED,
                baseTime.plusSeconds(120)
        );

        String label = presenter.buildLabel(
                "Rules: Position Closed",
                List.of(firstFailed, secondFailed, thirdFailed),
                Instant::toString
        );

        assertTrue(label.contains("Base Buy failed x3 @ $100.00/10"), label);
        // The repeat window collapses into the single time cell for that consolidated row.
        assertTrue(label.contains("2026-05-18T14:30:00Z"), label);
        assertTrue(label.contains("style='color:#B71C1C; background-color:#FFF59D;'"));
        assertFalse(label.contains("Base Buy placed @ $100.00/10</td>"), label);
    }

    @Test
    void buyLimitFillKeepsBrokerAveragePricePrecisionWhenStopLossSoldAtRoundedPrice() {
        Instant baseTime = Instant.parse("2026-06-10T18:18:42Z");
        StrategyOrder stopLoss = order(
                StrategyStage.STOP_LOSS,
                StrategyOrderSide.SELL,
                new BigDecimal("9.00"),
                new BigDecimal("5.00"),
                new BigDecimal("5.00"),
                new BigDecimal("9.00"),
                StrategyOrderStatus.FILLED,
                baseTime
        );
        StrategyOrder buyLimit = order(
                StrategyStage.BUY_LIMIT_1,
                StrategyOrderSide.BUY,
                new BigDecimal("9.79"),
                new BigDecimal("1.00"),
                new BigDecimal("1.00"),
                new BigDecimal("8.998"),
                StrategyOrderStatus.FILLED,
                baseTime.plusSeconds(1)
        );

        String label = presenter.buildLabel(
                "Rules: Completed @ $9.79",
                List.of(stopLoss, buyLimit),
                Instant::toString
        );

        assertTrue(label.contains("Stop Loss sold @ $9.00/5"));
        assertTrue(label.contains("Buy Limit 1 filled @ $8.998/1"));
        assertFalse(label.contains("Buy Limit 1 filled @ $9.00/1"));
    }

    @Test
    void averagingDownShowsTheBlendedPriceThePositionIsNowAveragedInAt() {
        Instant start = Instant.parse("2026-05-18T14:30:00Z");
        StrategyOrder baseBuy = order(StrategyStage.BASE_BUY, StrategyOrderSide.BUY,
                new BigDecimal("10.00"), new BigDecimal("10"), new BigDecimal("10"),
                new BigDecimal("10.00"), StrategyOrderStatus.FILLED, start);
        StrategyOrder averageDown = order(StrategyStage.MANUAL_BUY, StrategyOrderSide.BUY,
                new BigDecimal("8.00"), new BigDecimal("10"), new BigDecimal("10"),
                new BigDecimal("8.00"), StrategyOrderStatus.FILLED, start.plusSeconds(3600));

        String label = presenter.buildLabel("Rules: Stop loss active",
                List.of(baseBuy, averageDown), Instant::toString);

        // The first entry is not an averaging step, so it shows only its own fill price.
        assertTrue(label.contains("Base Buy filled @ $10.00/10"), label);
        assertFalse(label.contains("Base Buy filled @ $10.00/10 - averaged in"), label);
        // The second buy blends 10 @ $10 with 10 @ $8 → $9.00.
        assertTrue(label.contains("Manual Buy filled @ $8.00/10 - averaged in @ $9.00"), label);
    }

    @Test
    void singleEntryPositionDoesNotClaimToBeAveraged() {
        Instant start = Instant.parse("2026-05-18T14:30:00Z");
        StrategyOrder baseBuy = order(StrategyStage.BASE_BUY, StrategyOrderSide.BUY,
                new BigDecimal("10.00"), new BigDecimal("10"), new BigDecimal("10"),
                new BigDecimal("10.00"), StrategyOrderStatus.FILLED, start);

        String label = presenter.buildLabel("Rules: Stop loss active", List.of(baseBuy), Instant::toString);

        assertFalse(label.contains("averaged in"), label);
    }

    @Test
    void reEntryAfterAFullExitStartsAFreshAverageInsteadOfBlendingAcrossTrades() {
        Instant start = Instant.parse("2026-05-18T14:30:00Z");
        StrategyOrder firstBuy = order(StrategyStage.BASE_BUY, StrategyOrderSide.BUY,
                new BigDecimal("10.00"), new BigDecimal("10"), new BigDecimal("10"),
                new BigDecimal("10.00"), StrategyOrderStatus.FILLED, start);
        StrategyOrder fullExit = order(StrategyStage.TARGET_SELL, StrategyOrderSide.SELL,
                new BigDecimal("12.00"), new BigDecimal("10"), new BigDecimal("10"),
                new BigDecimal("12.00"), StrategyOrderStatus.FILLED, start.plusSeconds(3600));
        StrategyOrder reEntry = order(StrategyStage.MANUAL_BUY, StrategyOrderSide.BUY,
                new BigDecimal("7.00"), new BigDecimal("10"), new BigDecimal("10"),
                new BigDecimal("7.00"), StrategyOrderStatus.FILLED, start.plusSeconds(7200));

        String label = presenter.buildLabel("Rules: Stop loss active",
                List.of(firstBuy, fullExit, reEntry), Instant::toString);

        // Blending the pre-exit cost into the re-entry would report a fictional basis.
        assertFalse(label.contains("averaged in @ $8.50"), label);
        assertTrue(label.contains("Manual Buy filled @ $7.00/10"), label);
    }

    private StrategyOrder order(
            StrategyStage stage,
            StrategyOrderSide side,
            BigDecimal price,
            BigDecimal requestedQuantity,
            BigDecimal filledQuantity,
            BigDecimal filledAveragePrice,
            StrategyOrderStatus status,
            Instant submittedAt
    ) {
        StrategyOrder order = new StrategyOrder(
                java.util.UUID.randomUUID().toString(),
                "strategy-1",
                stage,
                "alpaca-1",
                "client-1",
                "AAPL",
                side,
                StrategyOrderType.LIMIT,
                price,
                BigDecimal.ZERO,
                requestedQuantity,
                filledQuantity,
                filledAveragePrice,
                status,
                submittedAt,
                submittedAt.plusSeconds(60),
                status == StrategyOrderStatus.FILLED ? submittedAt.plusSeconds(120) : null,
                "{}"
        );
        return order;
    }
}
