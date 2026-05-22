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
        assertTrue(label.contains("Base Buy placed @ $100.00/10 on 2026-05-18T14:30:00Z"));
        assertTrue(label.contains("Base Buy partially filled @ $100.25/5 on 2026-05-18T14:31:00Z"));
        assertTrue(label.contains("Target Sell sold @ $110.50/5 on 2026-05-18T15:32:00Z"));
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

        assertTrue(label.contains("Base Buy failed x3 @ $100.00/10 from 2026-05-18T14:30:00Z"));
        assertFalse(label.contains("Base Buy placed @ $100.00/10 on 2026-05-18T14:31:00Z"));
        assertFalse(label.contains("Base Buy placed @ $100.00/10 on 2026-05-18T14:32:00Z"));
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
