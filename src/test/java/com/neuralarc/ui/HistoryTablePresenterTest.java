package com.neuralarc.ui;

import com.neuralarc.model.StrategyOrder;
import com.neuralarc.model.StrategyOrderSide;
import com.neuralarc.model.StrategyOrderStatus;
import com.neuralarc.model.StrategyOrderType;
import com.neuralarc.model.StrategyStage;
import com.neuralarc.model.StrategyStatus;
import com.neuralarc.service.StrategyService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void sellStageLabelsDifferentiatePortfolioCaptureAutonomousAndManualOrigins() {
        String captureJson = "{\"" + StrategyService.EXIT_SOURCE_JSON_KEY + "\":\""
                + StrategyService.SellExecutionSource.PORTFOLIO_CAPTURE.name() + "\"}";
        String portfolioActionJson = "{\"" + StrategyService.EXIT_SOURCE_JSON_KEY + "\":\""
                + StrategyService.SellExecutionSource.PORTFOLIO_ACTION.name() + "\"}";
        HistoryTablePresenter.HistorySource source = new HistoryTablePresenter.HistorySource(
                "AAPL",
                "Paper",
                "Completed",
                "Completed",
                "",
                Instant.now(),
                StrategyStatus.COMPLETED,
                List.of(
                        filledOrderAt("AAPL", StrategyStage.BASE_BUY, StrategyOrderSide.BUY,
                                "60", "100.00", "100.00", "2026-05-06T10:00:00Z"),
                        filledOrderAt("AAPL", StrategyStage.TARGET_SELL, StrategyOrderSide.SELL,
                                "10", "110.00", "110.00", "2026-05-06T11:00:00Z"),
                        filledOrderAt("AAPL", StrategyStage.STOP_LOSS, StrategyOrderSide.SELL,
                                "10", "95.00", "95.00", "2026-05-06T12:00:00Z"),
                        filledOrderAt("AAPL", StrategyStage.PROFIT_EXIT, StrategyOrderSide.SELL,
                                "10", "115.00", "115.00", "2026-05-06T13:00:00Z"),
                        filledOrderAt("AAPL", StrategyStage.MANUAL_EXIT, StrategyOrderSide.SELL,
                                "10", "105.00", "105.00", "2026-05-06T14:00:00Z"),
                        filledOrderAt("AAPL", StrategyStage.MANUAL_EXIT, StrategyOrderSide.SELL,
                                "10", "106.00", "106.00", "2026-05-06T15:00:00Z", captureJson),
                        filledOrderAt("AAPL", StrategyStage.MANUAL_EXIT, StrategyOrderSide.SELL,
                                "10", "107.00", "107.00", "2026-05-06T16:00:00Z", portfolioActionJson)
                )
        );

        List<String> sellStages = presenter.buildRows(List.of(source), instant -> "t").stream()
                .filter(row -> "SELL".equals(row.side()))
                .map(HistoryTablePresenter.HistoryRow::stage)
                .toList();

        assertTrue(sellStages.contains("Autonomous Strategy - Target Sell"));
        assertTrue(sellStages.contains("Autonomous Strategy - Stop Loss"));
        assertTrue(sellStages.contains("Autonomous Strategy - Profit Exit"));
        assertTrue(sellStages.contains("Manual - User Sell"));
        assertTrue(sellStages.contains("Portfolio Capture - Market Sell"));
        assertTrue(sellStages.contains("Manual - Portfolio Action"));
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
    void sellRowsShowAverageBuyPriceUsedForRealizedPnl() {
        HistoryTablePresenter.HistorySource source = new HistoryTablePresenter.HistorySource(
                "AAPL",
                "Paper",
                "Completed",
                "Completed",
                "",
                Instant.now(),
                StrategyStatus.COMPLETED,
                List.of(
                        filledOrder("AAPL", StrategyStage.BASE_BUY, StrategyOrderSide.BUY, "2", "100.00", "100.00"),
                        filledOrder("AAPL", StrategyStage.BASE_BUY, StrategyOrderSide.BUY, "2", "120.00", "120.00"),
                        filledOrder("AAPL", StrategyStage.TARGET_SELL, StrategyOrderSide.SELL, "4", "130.00", "130.00")
                )
        );

        List<HistoryTablePresenter.HistoryRow> rows = presenter.buildRows(List.of(source), instant -> "t");

        HistoryTablePresenter.HistoryRow sellRow = rows.stream()
                .filter(row -> "SELL".equals(row.side()))
                .findFirst()
                .orElseThrow();
        assertEquals("110.00", sellRow.buyPrice());
        assertEquals("130.00", sellRow.fillPrice());
        assertEquals("80.00", sellRow.realizedPnl());
    }

    @Test
    void profitableSellFilterHidesLossSellsWithinSameGroup() {
        HistoryTablePresenter.HistorySource source = new HistoryTablePresenter.HistorySource(
                "AAPL",
                "Paper",
                "Completed",
                "Completed",
                "",
                Instant.now(),
                StrategyStatus.COMPLETED,
                List.of(
                        filledOrderAt("AAPL", StrategyStage.BASE_BUY, StrategyOrderSide.BUY, "10", "100.00", "100.00", "2026-05-06T10:00:00Z"),
                        filledOrderAt("AAPL", StrategyStage.TARGET_SELL, StrategyOrderSide.SELL, "5", "110.00", "110.00", "2026-05-06T11:00:00Z"),
                        filledOrderAt("AAPL", StrategyStage.LOSS_EXIT, StrategyOrderSide.SELL, "5", "90.00", "90.00", "2026-05-06T12:00:00Z")
                )
        );

        List<HistoryTablePresenter.HistoryRow> rows = presenter.buildRows(
                List.of(source),
                instant -> "t",
                TradeHistoryGroupBy.SYMBOL,
                TradeHistorySellFilter.PROFITABLE_SELLS
        );

        assertTrue(rows.stream().anyMatch(row -> row.style() == HistoryTablePresenter.HistoryRowStyle.SELL_GAIN));
        assertFalse(rows.stream().anyMatch(row -> row.style() == HistoryTablePresenter.HistoryRowStyle.SELL_LOSS));
        HistoryTablePresenter.HistoryRow subtotal = rows.stream()
                .filter(row -> row.style() == HistoryTablePresenter.HistoryRowStyle.SUBTOTAL && "Subtotal".equals(row.stage()))
                .findFirst()
                .orElseThrow();
        assertEquals("50.00", subtotal.realizedPnl());
    }

    @Test
    void lossSellFilterHidesProfitableSellsWithinSameGroup() {
        HistoryTablePresenter.HistorySource source = new HistoryTablePresenter.HistorySource(
                "AAPL",
                "Paper",
                "Completed",
                "Completed",
                "",
                Instant.now(),
                StrategyStatus.COMPLETED,
                List.of(
                        filledOrderAt("AAPL", StrategyStage.BASE_BUY, StrategyOrderSide.BUY, "10", "100.00", "100.00", "2026-05-06T10:00:00Z"),
                        filledOrderAt("AAPL", StrategyStage.TARGET_SELL, StrategyOrderSide.SELL, "5", "110.00", "110.00", "2026-05-06T11:00:00Z"),
                        filledOrderAt("AAPL", StrategyStage.LOSS_EXIT, StrategyOrderSide.SELL, "5", "90.00", "90.00", "2026-05-06T12:00:00Z")
                )
        );

        List<HistoryTablePresenter.HistoryRow> rows = presenter.buildRows(
                List.of(source),
                instant -> "t",
                TradeHistoryGroupBy.SYMBOL,
                TradeHistorySellFilter.LOSS_SELLS
        );

        assertFalse(rows.stream().anyMatch(row -> row.style() == HistoryTablePresenter.HistoryRowStyle.SELL_GAIN));
        assertTrue(rows.stream().anyMatch(row -> row.style() == HistoryTablePresenter.HistoryRowStyle.SELL_LOSS));
        HistoryTablePresenter.HistoryRow subtotal = rows.stream()
                .filter(row -> row.style() == HistoryTablePresenter.HistoryRowStyle.SUBTOTAL && "Subtotal".equals(row.stage()))
                .findFirst()
                .orElseThrow();
        assertEquals("-50.00", subtotal.realizedPnl());
    }

    @Test
    void canGroupTradeHistoryRowsByDate() {
        HistoryTablePresenter.HistorySource aapl = new HistoryTablePresenter.HistorySource(
                "AAPL",
                "Paper",
                "Completed",
                "Completed",
                "",
                Instant.now(),
                StrategyStatus.COMPLETED,
                List.of(
                        filledOrderAt("AAPL", StrategyStage.BASE_BUY, StrategyOrderSide.BUY, "1", "100.00", "100.00", "2026-05-06T10:00:00Z"),
                        filledOrderAt("AAPL", StrategyStage.TARGET_SELL, StrategyOrderSide.SELL, "1", "110.00", "110.00", "2026-05-06T12:00:00Z")
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
                        filledOrderAt("MSFT", StrategyStage.BASE_BUY, StrategyOrderSide.BUY, "1", "200.00", "200.00", "2026-05-07T10:00:00Z"),
                        filledOrderAt("MSFT", StrategyStage.TARGET_SELL, StrategyOrderSide.SELL, "1", "190.00", "190.00", "2026-05-07T12:00:00Z")
                )
        );

        List<HistoryTablePresenter.HistoryRow> rows = presenter.buildRows(
                List.of(aapl, msft),
                instant -> "t",
                TradeHistoryGroupBy.DATE
        );

        assertTrue(rows.stream().anyMatch(row -> "2026-05-06".equals(row.groupKey())));
        assertTrue(rows.stream().anyMatch(row -> "2026-05-07".equals(row.groupKey())));
    }

    @Test
    void dateGroupingOrdersRowsByDateAndTimeWithoutSymbolGroupingFirst() {
        HistoryTablePresenter.HistorySource laterSymbolAlphabeticallyFirst = new HistoryTablePresenter.HistorySource(
                "AAA",
                "Paper",
                "Completed",
                "Completed",
                "",
                Instant.now(),
                StrategyStatus.COMPLETED,
                List.of(
                        filledOrderAt("AAA", StrategyStage.BASE_BUY, StrategyOrderSide.BUY, "1", "100.00", "100.00", "2026-05-06T10:00:00Z"),
                        filledOrderAt("AAA", StrategyStage.TARGET_SELL, StrategyOrderSide.SELL, "1", "110.00", "110.00", "2026-05-06T14:00:00Z")
                )
        );
        HistoryTablePresenter.HistorySource earlierSymbolAlphabeticallyLast = new HistoryTablePresenter.HistorySource(
                "ZZZ",
                "Paper",
                "Completed",
                "Completed",
                "",
                Instant.now(),
                StrategyStatus.COMPLETED,
                List.of(
                        filledOrderAt("ZZZ", StrategyStage.BASE_BUY, StrategyOrderSide.BUY, "1", "200.00", "200.00", "2026-05-06T10:00:00Z"),
                        filledOrderAt("ZZZ", StrategyStage.TARGET_SELL, StrategyOrderSide.SELL, "1", "210.00", "210.00", "2026-05-06T12:00:00Z")
                )
        );

        List<HistoryTablePresenter.HistoryRow> sellRows = presenter.buildRows(
                        List.of(earlierSymbolAlphabeticallyLast, laterSymbolAlphabeticallyFirst),
                        instant -> "t",
                        TradeHistoryGroupBy.DATE
                ).stream()
                .filter(row -> "SELL".equals(row.side()))
                .toList();

        assertEquals("AAA", sellRows.getFirst().symbol());
        assertEquals("ZZZ", sellRows.get(1).symbol());
        assertEquals("2026-05-06", sellRows.getFirst().groupKey());
        assertEquals("2026-05-06", sellRows.get(1).groupKey());
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

    private static StrategyOrder filledOrderAt(
            String symbol,
            StrategyStage stage,
            StrategyOrderSide side,
            String quantity,
            String limitPrice,
            String fillPrice,
            String timestamp
    ) {
        return filledOrderAt(symbol, stage, side, quantity, limitPrice, fillPrice, timestamp, "{}");
    }

    private static StrategyOrder filledOrderAt(
            String symbol,
            StrategyStage stage,
            StrategyOrderSide side,
            String quantity,
            String limitPrice,
            String fillPrice,
            String timestamp,
            String rawJson
    ) {
        Instant at = Instant.parse(timestamp);
        return new StrategyOrder(
                UUID.randomUUID().toString(),
                "strategy-1",
                stage,
                "ord-" + stage.name() + "-" + timestamp,
                "client-" + stage.name() + "-" + timestamp,
                symbol,
                side,
                StrategyOrderType.LIMIT,
                new BigDecimal(limitPrice),
                BigDecimal.ZERO,
                new BigDecimal(quantity),
                new BigDecimal(quantity),
                new BigDecimal(fillPrice),
                StrategyOrderStatus.FILLED,
                at,
                at,
                at,
                rawJson
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
