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
    void completedStrategyWithoutSellFillDoesNotAppearInTradeHistory() {
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

        assertTrue(rows.isEmpty());
    }

    @Test
    void activeStrategyWithOnlyFilledBuyDoesNotAppearInTradeHistory() {
        HistoryTablePresenter.HistorySource source = new HistoryTablePresenter.HistorySource(
                "AAPL",
                "Paper",
                "Base Buy Filled",
                "Base Buy Filled",
                "",
                Instant.now(),
                StrategyStatus.ACTIVE,
                List.of(filledOrder("AAPL", StrategyStage.BASE_BUY, StrategyOrderSide.BUY, "10", "100.00", "100.00"))
        );

        List<HistoryTablePresenter.HistoryRow> rows = presenter.buildRows(List.of(source), instant -> "t");

        assertTrue(rows.isEmpty());
    }

    @Test
    void activeRepeatStrategyWithFilledSellAppearsInTradeHistory() {
        HistoryTablePresenter.HistorySource source = new HistoryTablePresenter.HistorySource(
                "AAPL",
                "Paper",
                "Active",
                "Limit Base Buy Placed",
                "",
                Instant.now(),
                StrategyStatus.ACTIVE,
                List.of(
                        filledOrder("AAPL", StrategyStage.BASE_BUY, StrategyOrderSide.BUY, "10", "100.00", "100.00"),
                        filledOrder("AAPL", StrategyStage.TARGET_SELL, StrategyOrderSide.SELL, "10", "110.00", "110.00")
                )
        );

        List<HistoryTablePresenter.HistoryRow> rows = presenter.buildRows(List.of(source), instant -> "t");

        assertTrue(rows.stream().anyMatch(row -> "SELL".equals(row.side())));
    }

    @Test
    void filledSellRowsProduceSubtotalPerSymbol() {
        HistoryTablePresenter.HistorySource source = new HistoryTablePresenter.HistorySource(
                "AAPL",
                "Paper",
                "Completed",
                "Completed",
                "",
                Instant.now(),
                StrategyStatus.COMPLETED,
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
    void multipleSymbolSubtotalsProduceBottomTotalValueRow() {
        HistoryTablePresenter.HistorySource aapl = new HistoryTablePresenter.HistorySource(
                "AAPL",
                "Paper",
                "Completed",
                "Completed",
                "",
                Instant.now(),
                StrategyStatus.COMPLETED,
                List.of(
                        filledOrder("AAPL", StrategyStage.BASE_BUY, StrategyOrderSide.BUY, "10", "100.00", "100.00"),
                        filledOrder("AAPL", StrategyStage.TARGET_SELL, StrategyOrderSide.SELL, "10", "110.00", "110.00")
                )
        );
        HistoryTablePresenter.HistorySource msft = new HistoryTablePresenter.HistorySource(
                "MSFT",
                "Paper",
                "Completed",
                "Completed",
                "",
                Instant.now(),
                StrategyStatus.COMPLETED,
                List.of(
                        filledOrder("MSFT", StrategyStage.BASE_BUY, StrategyOrderSide.BUY, "5", "200.00", "200.00"),
                        filledOrder("MSFT", StrategyStage.TARGET_SELL, StrategyOrderSide.SELL, "5", "220.00", "220.00")
                )
        );

        List<HistoryTablePresenter.HistoryRow> rows = presenter.buildRows(List.of(aapl, msft), instant -> "t");

        HistoryTablePresenter.HistoryRow lastRow = rows.getLast();
        assertEquals(HistoryTablePresenter.HistoryRowStyle.SUBTOTAL, lastRow.style());
        assertEquals("Total Value", lastRow.stage());
        assertEquals("200.00 (+ve: 200.00 / -ve: 0.00)", lastRow.realizedPnl());
    }

    @Test
    void totalValueRowShowsPositiveAndNegativeBreakdownInBrackets() {
        HistoryTablePresenter.HistorySource gain = new HistoryTablePresenter.HistorySource(
                "GAIN",
                "Paper",
                "Completed",
                "Completed",
                "",
                Instant.now(),
                StrategyStatus.COMPLETED,
                List.of(
                        filledOrder("GAIN", StrategyStage.BASE_BUY, StrategyOrderSide.BUY, "10", "100.00", "100.00"),
                        filledOrder("GAIN", StrategyStage.TARGET_SELL, StrategyOrderSide.SELL, "10", "120.00", "120.00")
                )
        );
        HistoryTablePresenter.HistorySource loss = new HistoryTablePresenter.HistorySource(
                "LOSS",
                "Paper",
                "Completed",
                "Completed",
                "",
                Instant.now(),
                StrategyStatus.COMPLETED,
                List.of(
                        filledOrder("LOSS", StrategyStage.BASE_BUY, StrategyOrderSide.BUY, "10", "100.00", "100.00"),
                        filledOrder("LOSS", StrategyStage.TARGET_SELL, StrategyOrderSide.SELL, "10", "90.00", "90.00")
                )
        );

        List<HistoryTablePresenter.HistoryRow> rows = presenter.buildRows(List.of(gain, loss), instant -> "t");

        HistoryTablePresenter.HistoryRow lastRow = rows.getLast();
        assertEquals("Total Value", lastRow.stage());
        assertEquals("100.00 (+ve: 200.00 / -ve: -100.00)", lastRow.realizedPnl());
    }

    @Test
    void noTradeHistoryRowsMeansNoTotalValueRow() {
        HistoryTablePresenter.HistorySource source = new HistoryTablePresenter.HistorySource(
                "AAPL",
                "Paper",
                "Completed",
                "Completed",
                "",
                Instant.now(),
                StrategyStatus.COMPLETED,
                List.of()
        );

        List<HistoryTablePresenter.HistoryRow> rows = presenter.buildRows(List.of(source), instant -> "t");

        assertTrue(rows.stream().noneMatch(row -> "Total Value".equals(row.stage())));
    }

    @Test
    void filledOrdersUseRequestedQuantityWhenBrokerFilledQuantityIsZero() {
        HistoryTablePresenter.HistorySource source = new HistoryTablePresenter.HistorySource(
                "AAPL",
                "Paper",
                "Completed",
                "Completed",
                "",
                Instant.now(),
                StrategyStatus.COMPLETED,
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
