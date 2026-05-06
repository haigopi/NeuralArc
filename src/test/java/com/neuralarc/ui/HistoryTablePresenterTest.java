package com.neuralarc.ui;

import com.neuralarc.model.StrategyOrder;
import com.neuralarc.model.StrategyOrderSide;
import com.neuralarc.model.StrategyOrderStatus;
import com.neuralarc.model.StrategyOrderType;
import com.neuralarc.model.StrategyStage;
import com.neuralarc.model.StrategyStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryTablePresenterTest {
    private final HistoryTablePresenter presenter = new HistoryTablePresenter();

    @Test
    void completedStrategyWithoutFilledOrdersAddsFallbackHistoryRow() {
        HistoryTablePresenter.HistorySource source = new HistoryTablePresenter.HistorySource(
                "AAPL",
                "Paper",
                "Completed",
                "Completed",
                "",
                Instant.parse("2026-05-06T10:15:30Z"),
                StrategyStatus.COMPLETED,
                List.of()
        );

        List<HistoryTablePresenter.HistoryRow> rows = presenter.buildRows(List.of(source), instant -> "formatted");

        assertEquals(1, rows.size());
        assertEquals("Completed", rows.getFirst().stage());
        assertEquals("formatted", rows.getFirst().whenDisplay());
    }

    @Test
    void filledSellRowsProduceSubtotalPerSymbol() {
        HistoryTablePresenter.HistorySource source = new HistoryTablePresenter.HistorySource(
                "AAPL",
                "Paper",
                "Active",
                "Active",
                "",
                Instant.now(),
                StrategyStatus.ACTIVE,
                List.of(
                        filledOrder("AAPL", StrategyStage.BASE_BUY, StrategyOrderSide.BUY, "10", "100.00", "100.00"),
                        filledOrder("AAPL", StrategyStage.TARGET_SELL, StrategyOrderSide.SELL, "4", "110.00", "110.00"),
                        filledOrder("AAPL", StrategyStage.PROFIT_EXIT, StrategyOrderSide.SELL, "6", "120.00", "120.00")
                )
        );

        List<HistoryTablePresenter.HistoryRow> rows = presenter.buildRows(List.of(source), instant -> "t");

        assertTrue(rows.stream().anyMatch(row -> row.style() == HistoryTablePresenter.HistoryRowStyle.SUBTOTAL));
        HistoryTablePresenter.HistoryRow subtotal = rows.stream()
                .filter(row -> row.style() == HistoryTablePresenter.HistoryRowStyle.SUBTOTAL)
                .findFirst()
                .orElseThrow();
        assertEquals("160.00", subtotal.realizedPnl());
    }

    @Test
    void filledOrdersUseRequestedQuantityWhenBrokerFilledQuantityIsZero() {
        HistoryTablePresenter.HistorySource source = new HistoryTablePresenter.HistorySource(
                "AAPL",
                "Paper",
                "Active",
                "Active",
                "",
                Instant.now(),
                StrategyStatus.ACTIVE,
                List.of(
                        filledOrderWithReportedQuantity("AAPL", StrategyStage.BASE_BUY, StrategyOrderSide.BUY,
                                "10", "100.00", "0", "100.00"),
                        filledOrderWithReportedQuantity("AAPL", StrategyStage.TARGET_SELL, StrategyOrderSide.SELL,
                                "10", "110.00", "0", "110.00")
                )
        );

        List<HistoryTablePresenter.HistoryRow> rows = presenter.buildRows(List.of(source), instant -> "t");

        HistoryTablePresenter.HistoryRow sellRow = rows.stream()
                .filter(row -> "SELL".equals(row.side()))
                .findFirst()
                .orElseThrow();
        assertEquals("100.00", sellRow.realizedPnl());
    }

    private static StrategyOrder filledOrder(
            String symbol,
            StrategyStage stage,
            StrategyOrderSide side,
            String quantity,
            String limitPrice,
            String fillPrice
    ) {
        Instant now = Instant.parse("2026-05-06T10:00:00Z");
        return new StrategyOrder(
                UUID.randomUUID().toString(),
                "strategy-1",
                stage,
                "ord-" + stage.name(),
                "client-" + stage.name(),
                symbol,
                side,
                StrategyOrderType.LIMIT,
                new BigDecimal(limitPrice),
                BigDecimal.ZERO,
                new BigDecimal(quantity),
                new BigDecimal(quantity),
                new BigDecimal(fillPrice),
                StrategyOrderStatus.FILLED,
                now,
                now,
                now,
                "{}"
        );
    }

    private static StrategyOrder filledOrderWithReportedQuantity(
            String symbol,
            StrategyStage stage,
            StrategyOrderSide side,
            String requestedQuantity,
            String limitPrice,
            String filledQuantity,
            String fillPrice
    ) {
        Instant now = Instant.parse("2026-05-06T10:00:00Z");
        return new StrategyOrder(
                UUID.randomUUID().toString(),
                "strategy-1",
                stage,
                "ord-" + stage.name() + "-reported",
                "client-" + stage.name() + "-reported",
                symbol,
                side,
                StrategyOrderType.LIMIT,
                new BigDecimal(limitPrice),
                BigDecimal.ZERO,
                new BigDecimal(requestedQuantity),
                new BigDecimal(filledQuantity),
                new BigDecimal(fillPrice),
                StrategyOrderStatus.FILLED,
                now,
                now,
                now,
                "{}"
        );
    }
}
