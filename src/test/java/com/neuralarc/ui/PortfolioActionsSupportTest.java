package com.neuralarc.ui;

import com.neuralarc.model.ProfitHoldType;
import com.neuralarc.model.PauseReason;
import com.neuralarc.model.Position;
import com.neuralarc.model.StopLossType;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortfolioActionsSupportTest {

    private final PortfolioActionsSupport support = new PortfolioActionsSupport();

    @Test
    void filtersProfitableTargetsFromCachedPositions() {
        ManagedStrategy profitable = managed("AAPL", StrategyStatus.ACTIVE, 10, new BigDecimal("100"), new BigDecimal("110"));
        ManagedStrategy losing = managed("MSFT", StrategyStatus.ACTIVE, 10, new BigDecimal("100"), new BigDecimal("90"));
        ManagedStrategy flat = managed("TSLA", StrategyStatus.ACTIVE, 0, BigDecimal.ZERO, BigDecimal.ZERO);

        List<ManagedStrategy> targets = support.filterTargets(
                List.of(profitable, losing, flat),
                PortfolioActionsSupport.Scope.PROFITABLE
        );

        assertEquals(1, targets.size());
        assertEquals("AAPL", targets.getFirst().strategy.symbol());
    }

    @Test
    void profitableIncludesCanceledSellPlacedOpenPositions() {
        ManagedStrategy canceledSellPlaced = managed(
                "AAPL",
                StrategyStatus.ACTIVE,
                StrategyLifecycleState.SELL_PLACED,
                10,
                new BigDecimal("100"),
                new BigDecimal("110")
        );
        canceledSellPlaced.strategy.setLatestOrderStatus("cancelled");

        List<ManagedStrategy> targets = support.filterTargets(
                List.of(canceledSellPlaced),
                PortfolioActionsSupport.Scope.PROFITABLE
        );

        assertEquals(List.of("AAPL"), targets.stream().map(entry -> entry.strategy.symbol()).toList());
    }

    @Test
    void lossOnlyMarketTargetsOnlyLosingOpenPositions() {
        ManagedStrategy losing = managed("AAPL", StrategyStatus.ACTIVE, 10, new BigDecimal("100"), new BigDecimal("90"));
        ManagedStrategy profitable = managed("MSFT", StrategyStatus.ACTIVE, 10, new BigDecimal("100"), new BigDecimal("110"));

        List<ManagedStrategy> targets = support.filterTargets(
                List.of(losing, profitable),
                PortfolioActionsSupport.Scope.LOSS_ONLY_MARKET
        );

        assertEquals(1, targets.size());
        assertEquals("AAPL", targets.getFirst().strategy.symbol());
    }

    @Test
    void lossOnlyMarketIncludesCanceledSellPlacedOpenPositions() {
        ManagedStrategy canceledSellPlaced = managed(
                "AAPL",
                StrategyStatus.ACTIVE,
                StrategyLifecycleState.SELL_PLACED,
                10,
                new BigDecimal("100"),
                new BigDecimal("90")
        );
        canceledSellPlaced.strategy.setLatestOrderStatus("cancelled");

        List<ManagedStrategy> targets = support.filterTargets(
                List.of(canceledSellPlaced),
                PortfolioActionsSupport.Scope.LOSS_ONLY_MARKET
        );

        assertEquals(List.of("AAPL"), targets.stream().map(entry -> entry.strategy.symbol()).toList());
    }

    @Test
    void lossOnlyIncludesCanceledSellPartiallyFilledOpenPositions() {
        ManagedStrategy canceledSellPartial = managed(
                "AAPL",
                StrategyStatus.ACTIVE,
                StrategyLifecycleState.SELL_PARTIALLY_FILLED,
                10,
                new BigDecimal("100"),
                new BigDecimal("90")
        );
        canceledSellPartial.strategy.setLatestOrderStatus("canceled");

        List<ManagedStrategy> targets = support.filterTargets(
                List.of(canceledSellPartial),
                PortfolioActionsSupport.Scope.LOSS_ONLY
        );

        assertEquals(List.of("AAPL"), targets.stream().map(entry -> entry.strategy.symbol()).toList());
    }

    @Test
    void profitableExcludesSellPlacedWhenNotCanceled() {
        ManagedStrategy activePendingSell = managed(
                "AAPL",
                StrategyStatus.ACTIVE,
                StrategyLifecycleState.SELL_PLACED,
                10,
                new BigDecimal("100"),
                new BigDecimal("110")
        );
        activePendingSell.strategy.setLatestOrderStatus("new");

        List<ManagedStrategy> targets = support.filterTargets(
                List.of(activePendingSell),
                PortfolioActionsSupport.Scope.PROFITABLE
        );

        assertTrue(targets.isEmpty());
    }

    @Test
    void allOpenExcludesCompletedStrategiesEvenWhenCachedPositionShowsShares() {
        ManagedStrategy completed = managed(
                "AAPL",
                StrategyStatus.COMPLETED,
                StrategyLifecycleState.COMPLETED,
                10,
                new BigDecimal("100"),
                new BigDecimal("110")
        );

        List<ManagedStrategy> targets = support.filterTargets(
                List.of(completed),
                PortfolioActionsSupport.Scope.ALL_OPEN
        );

        assertTrue(targets.isEmpty());
    }

    @Test
    void allOpenExcludesStrategiesAlreadySubmittedForSell() {
        ManagedStrategy sellPlaced = managed(
                "AAPL",
                StrategyStatus.ACTIVE,
                StrategyLifecycleState.SELL_PLACED,
                10,
                new BigDecimal("100"),
                new BigDecimal("110")
        );

        List<ManagedStrategy> targets = support.filterTargets(
                List.of(sellPlaced),
                PortfolioActionsSupport.Scope.ALL_OPEN
        );

        assertTrue(targets.isEmpty());
    }

    @Test
    void allOpenIncludesSellPlacedWhenLatestStatusIsCanceled() {
        ManagedStrategy canceledSellPlaced = managed(
                "AAPL",
                StrategyStatus.ACTIVE,
                StrategyLifecycleState.SELL_PLACED,
                10,
                new BigDecimal("100"),
                new BigDecimal("110")
        );
        canceledSellPlaced.strategy.setLatestOrderStatus("canceled");

        List<ManagedStrategy> targets = support.filterTargets(
                List.of(canceledSellPlaced),
                PortfolioActionsSupport.Scope.ALL_OPEN
        );

        assertEquals(List.of("AAPL"), targets.stream().map(entry -> entry.strategy.symbol()).toList());
    }

    @Test
    void confirmationMessageTruncatesSymbolsAfterSixEntries() {
        List<ManagedStrategy> targets = List.of(
                managed("AAPL", StrategyStatus.ACTIVE, 1, new BigDecimal("100"), new BigDecimal("101")),
                managed("MSFT", StrategyStatus.ACTIVE, 1, new BigDecimal("100"), new BigDecimal("101")),
                managed("TSLA", StrategyStatus.ACTIVE, 1, new BigDecimal("100"), new BigDecimal("101")),
                managed("NVDA", StrategyStatus.ACTIVE, 1, new BigDecimal("100"), new BigDecimal("101")),
                managed("AMD", StrategyStatus.ACTIVE, 1, new BigDecimal("100"), new BigDecimal("101")),
                managed("META", StrategyStatus.ACTIVE, 1, new BigDecimal("100"), new BigDecimal("101")),
                managed("ORCL", StrategyStatus.ACTIVE, 1, new BigDecimal("100"), new BigDecimal("101"))
        );

        String message = support.buildConfirmationMessage(PortfolioActionsSupport.Scope.ALL_OPEN, targets);

        assertTrue(message.contains("AAPL, MSFT, TSLA, NVDA, AMD, META"));
        assertTrue(message.contains(", ..."));
        assertTrue(!message.contains("ORCL"));
    }

    @Test
    void resultMessageIncludesFailuresSectionWhenAnyFail() {
        String message = support.buildResultMessage(
                PortfolioActionsSupport.Scope.LOSS_ONLY,
                new PortfolioActionsSupport.BatchResult(
                        List.of("AAPL", "MSFT"),
                        List.of("TSLA: no open quantity")
                )
        );

        assertTrue(message.contains("Submitted: 2"));
        assertTrue(message.contains("<b>Failed:</b>"));
        assertTrue(message.contains("TSLA: no open quantity"));
    }

    @Test
    void cancelPendingLimitBuysTargetsOnlyCancelablePendingBuyStrategies() {
        ManagedStrategy cancelableBaseBuy = managed(
                "AAPL",
                StrategyStatus.ACTIVE,
                StrategyLifecycleState.BASE_BUY_PLACED,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
        ManagedStrategy cancelableLimit1 = managed(
                "MSFT",
                StrategyStatus.ACTIVE,
                StrategyLifecycleState.BUY_LIMIT_1_PARTIALLY_FILLED,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
        ManagedStrategy notCancelableSellPlaced = managed(
                "TSLA",
                StrategyStatus.ACTIVE,
                StrategyLifecycleState.SELL_PLACED,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
        ManagedStrategy paused = managed(
                "NVDA",
                StrategyStatus.PAUSED,
                StrategyLifecycleState.BASE_BUY_PLACED,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );

        List<ManagedStrategy> targets = support.filterTargets(
                List.of(cancelableBaseBuy, cancelableLimit1, notCancelableSellPlaced, paused),
                PortfolioActionsSupport.BulkAction.CANCEL_PENDING_LIMIT_BUYS
        );

        assertEquals(2, targets.size());
        assertEquals(List.of("AAPL", "MSFT"), targets.stream().map(entry -> entry.strategy.symbol()).toList());
    }

    @Test
    void placePendingBaseBuysTargetsScannerRecommendationsOnly() {
        ManagedStrategy gapRocket = managed("AAPL", StrategyStatus.ACTIVE, 0, BigDecimal.ZERO, BigDecimal.ZERO);
        gapRocket.strategy.setLatestOrderStatus("GAP_ROCKET_RECOMMENDED");
        ManagedStrategy orbArmed = managed("MSFT", StrategyStatus.ACTIVE, 0, BigDecimal.ZERO, BigDecimal.ZERO);
        orbArmed.strategy.setLatestOrderStatus("ORB_ARMED");
        ManagedStrategy activeOrder = managed(
                "TSLA",
                StrategyStatus.ACTIVE,
                StrategyLifecycleState.BASE_BUY_PLACED,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
        activeOrder.strategy.setLatestOrderStatus("new");

        List<ManagedStrategy> targets = support.filterTargets(
                List.of(gapRocket, orbArmed, activeOrder),
                PortfolioActionsSupport.BulkAction.PLACE_PENDING_BASE_BUYS
        );

        assertEquals(List.of("AAPL", "MSFT"), targets.stream().map(entry -> entry.strategy.symbol()).toList());
    }

    @Test
    void cleanPendingBaseBuysTargetsScannerRecommendationsOnly() {
        ManagedStrategy dipHunter = managed("AAPL", StrategyStatus.ACTIVE, 0, BigDecimal.ZERO, BigDecimal.ZERO);
        dipHunter.strategy.setLatestOrderStatus("DIP_HUNTER_RECOMMENDED");
        ManagedStrategy swing = managed("MSFT", StrategyStatus.ACTIVE, 0, BigDecimal.ZERO, BigDecimal.ZERO);
        swing.strategy.setLatestOrderStatus("SWING_RECOMMENDED");
        ManagedStrategy submittedOrder = managed(
                "TSLA",
                StrategyStatus.ACTIVE,
                StrategyLifecycleState.BASE_BUY_PLACED,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
        submittedOrder.strategy.setLatestOrderStatus("new");

        List<ManagedStrategy> targets = support.filterTargets(
                List.of(dipHunter, swing, submittedOrder),
                PortfolioActionsSupport.BulkAction.CLEAN_PENDING_BASE_BUYS
        );

        assertEquals(List.of("AAPL", "MSFT"), targets.stream().map(entry -> entry.strategy.symbol()).toList());
    }

    @Test
    void cleanTradeHistoryTargetsOnlyInactiveHistoryRecords() {
        ManagedStrategy archived = managed("AAPL", StrategyStatus.ARCHIVED, StrategyLifecycleState.STOPPED, 0, BigDecimal.ZERO, BigDecimal.ZERO);
        ManagedStrategy completed = managed("MSFT", StrategyStatus.COMPLETED, StrategyLifecycleState.COMPLETED, 0, BigDecimal.ZERO, BigDecimal.ZERO);
        ManagedStrategy failed = managed("TSLA", StrategyStatus.FAILED, StrategyLifecycleState.FAILED, 0, BigDecimal.ZERO, BigDecimal.ZERO);
        ManagedStrategy stopped = managed("NVDA", StrategyStatus.STOPPED, StrategyLifecycleState.STOPPED, 0, BigDecimal.ZERO, BigDecimal.ZERO);
        ManagedStrategy active = managed("AMD", StrategyStatus.ACTIVE, StrategyLifecycleState.BASE_BUY_PLACED, 0, BigDecimal.ZERO, BigDecimal.ZERO);
        ManagedStrategy paused = managed("META", StrategyStatus.PAUSED, StrategyLifecycleState.PAUSED, 0, BigDecimal.ZERO, BigDecimal.ZERO);

        List<ManagedStrategy> targets = support.filterTargets(
                List.of(archived, completed, failed, stopped, active, paused),
                PortfolioActionsSupport.BulkAction.CLEAN_TRADE_HISTORY
        );

        assertEquals(List.of("AAPL", "MSFT", "TSLA", "NVDA"),
                targets.stream().map(entry -> entry.strategy.symbol()).toList());
    }

    @Test
    void cleanTradeHistoryResultLabelIsDeleted() {
        String message = support.buildResultMessage(
                PortfolioActionsSupport.BulkAction.CLEAN_TRADE_HISTORY,
                new PortfolioActionsSupport.BatchResult(List.of("AAPL", "MSFT"), List.of())
        );

        assertTrue(message.contains("Deleted: 2"));
    }

    @Test
    void deleteAllPaperModeEntriesTargetsOnlyPaperStrategiesAcrossStatuses() {
        ManagedStrategy activePaper = managed("AAPL", StrategyStatus.ACTIVE, 0, BigDecimal.ZERO, BigDecimal.ZERO);
        ManagedStrategy archivedPaper = managed("MSFT", StrategyStatus.ARCHIVED, StrategyLifecycleState.STOPPED, 0, BigDecimal.ZERO, BigDecimal.ZERO);
        ManagedStrategy live = managed("TSLA", StrategyStatus.ACTIVE, 0, BigDecimal.ZERO, BigDecimal.ZERO);
        live.strategy.setMode(StrategyMode.LIVE);

        List<ManagedStrategy> targets = support.filterTargets(
                List.of(activePaper, archivedPaper, live),
                PortfolioActionsSupport.BulkAction.DELETE_ALL_PAPER_MODE_ENTRIES
        );

        assertEquals(List.of("AAPL", "MSFT"), targets.stream().map(entry -> entry.strategy.symbol()).toList());
    }

    @Test
    void deleteAllPaperModeEntriesResultLabelIsDeleted() {
        String message = support.buildResultMessage(
                PortfolioActionsSupport.BulkAction.DELETE_ALL_PAPER_MODE_ENTRIES,
                new PortfolioActionsSupport.BatchResult(List.of("AAPL", "MSFT"), List.of())
        );

        assertTrue(message.contains("Deleted: 2"));
    }

    @Test
    void cleanInvalidTargetsOnlyInvalidFailedRecords() {
        ManagedStrategy invalid = managed("AAPL", StrategyStatus.FAILED, StrategyLifecycleState.FAILED, 0, BigDecimal.ZERO, BigDecimal.ZERO);
        invalid.strategy.setLatestOrderStatus("invalid");
        ManagedStrategy expired = managed("MSFT", StrategyStatus.FAILED, StrategyLifecycleState.FAILED, 0, BigDecimal.ZERO, BigDecimal.ZERO);
        expired.strategy.setLatestOrderStatus("expired");
        ManagedStrategy active = managed("TSLA", StrategyStatus.ACTIVE, StrategyLifecycleState.BASE_BUY_PLACED, 0, BigDecimal.ZERO, BigDecimal.ZERO);

        List<ManagedStrategy> targets = support.filterTargets(
                List.of(invalid, expired, active),
                PortfolioActionsSupport.BulkAction.CLEAN_INVALID
        );

        assertEquals(List.of("AAPL"), targets.stream().map(entry -> entry.strategy.symbol()).toList());
    }

    @Test
    void cleanInvalidResultLabelIsDeleted() {
        String message = support.buildResultMessage(
                PortfolioActionsSupport.BulkAction.CLEAN_INVALID,
                new PortfolioActionsSupport.BatchResult(List.of("AAPL"), List.of())
        );

        assertTrue(message.contains("Deleted: 1"));
    }

    @Test
    void cancelPendingLimitSellsTargetsOnlyCancelablePendingSellStrategies() {
        ManagedStrategy cancelableSellPlaced = managed(
                "AAPL",
                StrategyStatus.ACTIVE,
                StrategyLifecycleState.SELL_PLACED,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
        ManagedStrategy cancelableSellPartial = managed(
                "MSFT",
                StrategyStatus.ACTIVE,
                StrategyLifecycleState.SELL_PARTIALLY_FILLED,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
        ManagedStrategy notCancelableBuyPlaced = managed(
                "TSLA",
                StrategyStatus.ACTIVE,
                StrategyLifecycleState.BASE_BUY_PLACED,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
        ManagedStrategy paused = managed(
                "NVDA",
                StrategyStatus.PAUSED,
                StrategyLifecycleState.SELL_PLACED,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );

        List<ManagedStrategy> targets = support.filterTargets(
                List.of(cancelableSellPlaced, cancelableSellPartial, notCancelableBuyPlaced, paused),
                PortfolioActionsSupport.BulkAction.CANCEL_PENDING_LIMIT_SELLS
        );

        assertEquals(2, targets.size());
        assertEquals(List.of("AAPL", "MSFT"), targets.stream().map(entry -> entry.strategy.symbol()).toList());
    }

    @Test
    void promoteAllTargetsActivePausedAndExpiredPaperStrategiesOnly() {
        ManagedStrategy activePaper = managed("AAPL", StrategyStatus.ACTIVE, 0, BigDecimal.ZERO, BigDecimal.ZERO);
        ManagedStrategy pausedPaper = managed("MSFT", StrategyStatus.PAUSED, 0, BigDecimal.ZERO, BigDecimal.ZERO);
        ManagedStrategy expiredPaper = managed("NVDA", StrategyStatus.FAILED, 0, BigDecimal.ZERO, BigDecimal.ZERO);
        expiredPaper.strategy.setLatestOrderStatus("expired");
        ManagedStrategy failedPaper = managed("AMD", StrategyStatus.FAILED, 0, BigDecimal.ZERO, BigDecimal.ZERO);
        failedPaper.strategy.setLatestOrderStatus("api_error");
        ManagedStrategy live = managed("TSLA", StrategyStatus.ACTIVE, 0, BigDecimal.ZERO, BigDecimal.ZERO);
        live.strategy.setMode(StrategyMode.LIVE);

        List<ManagedStrategy> targets = support.filterTargets(
                List.of(activePaper, pausedPaper, expiredPaper, failedPaper, live),
                PortfolioActionsSupport.BulkAction.PROMOTE_ALL_TO_LIVE
        );

        assertEquals(3, targets.size());
        assertEquals(List.of("AAPL", "MSFT", "NVDA"), targets.stream().map(entry -> entry.strategy.symbol()).toList());
    }

    @Test
    void removeInactiveListTargetsCompletedAndCanceledStrategies() {
        ManagedStrategy completed = managed("AAPL", StrategyStatus.COMPLETED, 0, BigDecimal.ZERO, BigDecimal.ZERO);
        ManagedStrategy canceledByUser = managed("MSFT", StrategyStatus.PAUSED, 0, BigDecimal.ZERO, BigDecimal.ZERO);
        canceledByUser.strategy.setPauseReason(PauseReason.USER_PAUSED);
        ManagedStrategy manualCanceled = managed("NVDA", StrategyStatus.PAUSED, 0, BigDecimal.ZERO, BigDecimal.ZERO);
        manualCanceled.strategy.setPauseReason(PauseReason.MANUAL_LIMIT_BUY_CANCELED);
        ManagedStrategy active = managed("TSLA", StrategyStatus.ACTIVE, 0, BigDecimal.ZERO, BigDecimal.ZERO);

        List<ManagedStrategy> targets = support.filterTargets(
                List.of(completed, canceledByUser, manualCanceled, active),
                PortfolioActionsSupport.BulkAction.REMOVE_INACTIVE_LIST
        );

        assertEquals(List.of("AAPL", "MSFT", "NVDA"), targets.stream().map(entry -> entry.strategy.symbol()).toList());
    }

    @Test
    void resumeAllTargetsPausedAndManualCanceledStrategies() {
        ManagedStrategy userPaused = managed("AAPL", StrategyStatus.PAUSED, 0, BigDecimal.ZERO, BigDecimal.ZERO);
        userPaused.strategy.setPauseReason(PauseReason.USER_PAUSED);
        ManagedStrategy manualCanceled = managed("MSFT", StrategyStatus.PAUSED, 0, BigDecimal.ZERO, BigDecimal.ZERO);
        manualCanceled.strategy.setPauseReason(PauseReason.MANUAL_LIMIT_BUY_CANCELED);
        ManagedStrategy autoPaused = managed("NVDA", StrategyStatus.PAUSED, 0, BigDecimal.ZERO, BigDecimal.ZERO);
        autoPaused.strategy.setPauseReason(PauseReason.AUTO_MARKET_CLOSED);
        ManagedStrategy active = managed("TSLA", StrategyStatus.ACTIVE, 0, BigDecimal.ZERO, BigDecimal.ZERO);

        List<ManagedStrategy> targets = support.filterTargets(
                List.of(userPaused, manualCanceled, autoPaused, active),
                PortfolioActionsSupport.BulkAction.RESUME_ALL
        );

        assertEquals(List.of("AAPL", "MSFT", "NVDA"), targets.stream().map(entry -> entry.strategy.symbol()).toList());
    }

    @Test
    void bulkActionConfirmationUsesSpecificDescription() {
        String message = support.buildConfirmationMessage(
                PortfolioActionsSupport.BulkAction.CANCEL_PENDING_LIMIT_BUYS,
                List.of(managed("AAPL", StrategyStatus.ACTIVE, 0, BigDecimal.ZERO, BigDecimal.ZERO))
        );

        assertTrue(message.contains("Cancel pending limit buy orders"));
        assertTrue(message.contains("Open positions and sell orders are not closed"));
    }

    @Test
    void marketLosingConfirmationIncludesMarketExecutionWarning() {
        String message = support.buildConfirmationMessage(
                PortfolioActionsSupport.Scope.LOSS_ONLY_MARKET,
                List.of(managed("AAPL", StrategyStatus.ACTIVE, 1, new BigDecimal("100"), new BigDecimal("95")))
        );

        assertTrue(message.contains("manual market sell"));
        assertTrue(message.contains("can vary"));
    }

    @Test
    void cancelPendingLimitSellsConfirmationUsesSpecificDescription() {
        String message = support.buildConfirmationMessage(
                PortfolioActionsSupport.BulkAction.CANCEL_PENDING_LIMIT_SELLS,
                List.of(managed("AAPL", StrategyStatus.ACTIVE, StrategyLifecycleState.SELL_PLACED, 0, BigDecimal.ZERO, BigDecimal.ZERO))
        );

        assertTrue(message.contains("Cancel pending limit sell orders"));
        assertTrue(message.contains("Open positions remain open"));
    }

    @Test
    void bulkActionResultMessageUsesSucceededLabel() {
        String message = support.buildResultMessage(
                PortfolioActionsSupport.BulkAction.PROMOTE_ALL_TO_LIVE,
                new PortfolioActionsSupport.BatchResult(List.of("AAPL"), List.of())
        );

        assertTrue(message.contains("Succeeded: 1"));
    }

    @Test
    void removeInactiveListResultMessageUsesArchivedLabel() {
        String message = support.buildResultMessage(
                PortfolioActionsSupport.BulkAction.REMOVE_INACTIVE_LIST,
                new PortfolioActionsSupport.BatchResult(List.of("AAPL", "MSFT"), List.of())
        );

        assertTrue(message.contains("Archived: 2"));
    }

    @Test
    void resumeAllResultMessageUsesResumedLabel() {
        String message = support.buildResultMessage(
                PortfolioActionsSupport.BulkAction.RESUME_ALL,
                new PortfolioActionsSupport.BatchResult(List.of("AAPL", "MSFT"), List.of())
        );

        assertTrue(message.contains("Resumed: 2"));
    }

    @Test
    void cleanAllExpiredTargetsFailedAndBrokerExpiredPendingStrategies() {
        ManagedStrategy expired = managed("AAPL", StrategyStatus.FAILED, 0, BigDecimal.ZERO, BigDecimal.ZERO);
        expired.strategy.setCurrentState(StrategyLifecycleState.FAILED);
        expired.strategy.setLatestOrderStatus("expired");
        ManagedStrategy activeExpiredBaseBuy = managed(
                "NVDA",
                StrategyStatus.ACTIVE,
                StrategyLifecycleState.BASE_BUY_PLACED,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
        activeExpiredBaseBuy.strategy.setLatestOrderStatus("expired");
        ManagedStrategy failedRejected = managed("MSFT", StrategyStatus.FAILED, 0, BigDecimal.ZERO, BigDecimal.ZERO);
        failedRejected.strategy.setCurrentState(StrategyLifecycleState.FAILED);
        failedRejected.strategy.setLatestOrderStatus("rejected");
        ManagedStrategy active = managed("TSLA", StrategyStatus.ACTIVE, 0, BigDecimal.ZERO, BigDecimal.ZERO);

        List<ManagedStrategy> targets = support.filterTargets(
                List.of(expired, activeExpiredBaseBuy, failedRejected, active),
                PortfolioActionsSupport.BulkAction.CLEAN_ALL_EXPIRED
        );

        assertEquals(List.of("AAPL", "NVDA"), targets.stream().map(entry -> entry.strategy.symbol()).toList());
    }

    @Test
    void repositionExpiredUsesRepositionedResultLabel() {
        String message = support.buildResultMessage(
                PortfolioActionsSupport.BulkAction.REPOSITION_EXPIRED,
                new PortfolioActionsSupport.BatchResult(List.of("AAPL"), List.of())
        );

        assertTrue(message.contains("Repositioned: 1"));
    }

    @Test
    void placePendingBaseBuysResultLabelIsPlaced() {
        String message = support.buildResultMessage(
                PortfolioActionsSupport.BulkAction.PLACE_PENDING_BASE_BUYS,
                new PortfolioActionsSupport.BatchResult(List.of("AAPL"), List.of())
        );

        assertTrue(message.contains("Placed: 1"));
    }

    @Test
    void cleanPendingBaseBuysResultLabelIsDeleted() {
        String message = support.buildResultMessage(
                PortfolioActionsSupport.BulkAction.CLEAN_PENDING_BASE_BUYS,
                new PortfolioActionsSupport.BatchResult(List.of("AAPL"), List.of())
        );

        assertTrue(message.contains("Deleted: 1"));
    }

    @Test
    void resultMessageIncludesSkippedSectionWhenPresent() {
        String message = support.buildResultMessage(
                PortfolioActionsSupport.BulkAction.CANCEL_PENDING_LIMIT_BUYS,
                new PortfolioActionsSupport.BatchResult(
                        List.of("AAPL (1)"),
                        List.of(),
                        List.of("MSFT: no pending limit buy orders were cancelable")
                )
        );

        assertTrue(message.contains("<b>Skipped:</b>"));
        assertTrue(message.contains("no pending limit buy orders were cancelable"));
    }

    private static ManagedStrategy managed(String symbol, StrategyStatus status, int shares, BigDecimal avgCost, BigDecimal lastPrice) {
        return managed(symbol, status, StrategyLifecycleState.CREATED, shares, avgCost, lastPrice);
    }

    private static ManagedStrategy managed(
            String symbol,
            StrategyStatus status,
            StrategyLifecycleState lifecycleState,
            int shares,
            BigDecimal avgCost,
            BigDecimal lastPrice
    ) {
        ManagedStrategy managed = new ManagedStrategy(baseStrategy(symbol, status, lifecycleState));
        Position position = new Position(symbol);
        if (shares > 0) {
            position.applyBuy(shares, avgCost);
            position.setLastPrice(lastPrice);
        }
        managed.setCachedPosition(position);
        return managed;
    }

    private static Strategy baseStrategy(String symbol, StrategyStatus status) {
        return baseStrategy(symbol, status, StrategyLifecycleState.CREATED);
    }

    private static Strategy baseStrategy(String symbol, StrategyStatus status, StrategyLifecycleState lifecycleState) {
        return new Strategy(
                UUID.randomUUID().toString(),
                symbol + " Strategy",
                symbol,
                StrategyMode.PAPER,
                status,
                lifecycleState,
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
                10,
                Instant.now(),
                Instant.now()
        );
    }
}
