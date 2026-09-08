package com.neuralarc.ui;

import com.neuralarc.model.SellSubmissionType;

import java.util.List;

/**
 * Builds the Portfolio Actions menu structure, kept out of {@link PortfolioActionsController} so the
 * controller holds the action behaviour and this file holds what the operator reads.
 */
final class PortfolioActionsMenuBuilder {
    private PortfolioActionsMenuBuilder() {
    }

    /**
     * The menu structure. Every action carries the one-line description shown under its label, and no
     * group holds more than a handful of entries so the top level stays scannable.
     */
    static List<PortfolioActionsMenu.Group> groups(PortfolioActionsController controller, boolean liveView) {
        PortfolioActionsMenu.Entry deletePaperEntries = new PortfolioActionsMenu.Entry(
                "Delete All Paper Mode Entries",
                "Permanently delete every local paper strategy, order, and event.",
                "icons/delete.svg",
                controller::handleDeleteAllPaperModeEntries);
        PortfolioActionsMenu.Entry promoteAllToLive = new PortfolioActionsMenu.Entry(
                "Promote All to Live",
                "Recreate eligible paper strategies in live mode and archive the paper copies.",
                "icons/add-stock-strategy.svg",
                controller::handlePromoteAllToLive);

        return List.of(
                new PortfolioActionsMenu.Group("Sell Positions", "icons/close.svg", List.of(
                        new PortfolioActionsMenu.Entry("Sell Profitable Positions",
                                "Limit-sell every open position currently in profit.",
                                "icons/submit.svg",
                                () -> controller.handleSellAction(PortfolioActionsSupport.Scope.PROFITABLE, SellSubmissionType.LIMIT)),
                        new PortfolioActionsMenu.Entry("Sell Losing Positions",
                                "Limit-sell every open position currently at a loss.",
                                "icons/delete.svg",
                                () -> controller.handleSellAction(PortfolioActionsSupport.Scope.LOSS_ONLY, SellSubmissionType.LIMIT)),
                        new PortfolioActionsMenu.Entry("Sell All Open Positions",
                                "Limit-sell every open position, winners and losers alike.",
                                "icons/close.svg",
                                () -> controller.handleSellAction(PortfolioActionsSupport.Scope.ALL_OPEN, SellSubmissionType.LIMIT)),
                        new PortfolioActionsMenu.Entry("Sell All Profitable Positions at Market Value",
                                "Exit profitable positions now at market price instead of waiting for a limit fill.",
                                "icons/submit.svg",
                                () -> controller.handleSellAction(PortfolioActionsSupport.Scope.PROFITABLE_MARKET, SellSubmissionType.MARKET)),
                        new PortfolioActionsMenu.Entry("Sell All Losing Positions at Market Value",
                                "Exit losing positions now at market price instead of waiting for a limit fill.",
                                "icons/delete.svg",
                                () -> controller.handleSellAction(PortfolioActionsSupport.Scope.LOSS_ONLY_MARKET, SellSubmissionType.MARKET))
                )),
                new PortfolioActionsMenu.Group("Sell Triggers", "icons/submit.svg", List.of(
                        new PortfolioActionsMenu.Entry("Position All Sell Triggers",
                                "Place the resting sell order for every open position that has a target price.",
                                "icons/submit.svg",
                                controller::handlePositionAllSellTriggers),
                        new PortfolioActionsMenu.Entry("Position All Sell Profit Threshold percentage",
                                "Turn target sell prices into trailing profit-hold exits at a percent you choose.",
                                "icons/submit.svg",
                                controller::handlePositionAllSellProfitThresholdPercentage)
                )),
                new PortfolioActionsMenu.Group("Buy More", "icons/add-stock-strategy.svg", List.of(
                        new PortfolioActionsMenu.Entry("Import Stocks",
                                "Add stocks you already hold to the grid as tracked strategies.",
                                "icons/add-stock-strategy.svg",
                                controller::handleImportStocks),
                        new PortfolioActionsMenu.Entry("Average Down Losing Positions",
                                "Submit one extra buy per losing position to lower its average cost.",
                                "icons/submit.svg",
                                controller::handleAverageLosingPositions),
                        new PortfolioActionsMenu.Entry("Reposition Expired",
                                "Reactivate expired strategies with a fresh base limit buy.",
                                "icons/submit.svg",
                                controller::handleRepositionExpired)
                )),
                new PortfolioActionsMenu.Group("Pending Base Buys", "icons/submit.svg", List.of(
                        new PortfolioActionsMenu.Entry("Place Limit Buy for All Pending Positions",
                                "Submit the base limit buy for every pending recommendation.",
                                "icons/submit.svg",
                                () -> controller.handlePlacePendingBaseBuys(PortfolioActionsSupport.BulkAction.PLACE_PENDING_BASE_BUYS)),
                        new PortfolioActionsMenu.Entry("Place Limit Buy for All Manual Buy Entries",
                                "Submit the base limit buy for stocks you added by hand.",
                                "icons/submit.svg",
                                controller::handlePlacePendingBaseBuys),
                        new PortfolioActionsMenu.Entry("Place Limit Buy for Losing Pending Positions",
                                "Submit only the amber rows, whose limit sits above the current price.",
                                "icons/submit.svg",
                                () -> controller.handlePlacePendingBaseBuys(PortfolioActionsSupport.BulkAction.PLACE_AMBER_PENDING_BASE_BUYS)),
                        new PortfolioActionsMenu.Entry("Place Limit Buy for Gaining Pending Positions",
                                "Submit only the green rows, whose limit sits below the current price.",
                                "icons/submit.svg",
                                () -> controller.handlePlacePendingBaseBuys(PortfolioActionsSupport.BulkAction.PLACE_GREEN_PENDING_BASE_BUYS)),
                        new PortfolioActionsMenu.Entry("Readjust Losing Pending Base Buy Positions",
                                "Recalculate amber rows to a lower base-buy limit so they are ready to place.",
                                "icons/submit.svg",
                                controller::handleReadjustLosingPendingBaseBuys)
                )),
                new PortfolioActionsMenu.Group("Cancel Orders", "icons/close.svg", List.of(
                        new PortfolioActionsMenu.Entry("Cancel All Pending Limit Buys",
                                "Cancel working buy orders and pause those strategies; positions stay open.",
                                "icons/close.svg",
                                controller::handleCancelAllPendingLimitBuys),
                        new PortfolioActionsMenu.Entry("Cancel All Pending Limit Sells",
                                "Cancel working sell orders; the positions stay open and keep evaluating rules.",
                                "icons/close.svg",
                                controller::handleCancelAllPendingLimitSells),
                        new PortfolioActionsMenu.Entry("Cancel all Amber Pending Buys (Losers)",
                                "Drop amber recommendations that were never submitted to the broker.",
                                "icons/delete.svg",
                                () -> controller.handleCancelColoredPendingBuys(PortfolioActionsSupport.BulkAction.CANCEL_AMBER_PENDING_BUYS)),
                        new PortfolioActionsMenu.Entry("Cancel all Green Pending Buys (Gainer)",
                                "Drop green recommendations that were never submitted to the broker.",
                                "icons/delete.svg",
                                () -> controller.handleCancelColoredPendingBuys(PortfolioActionsSupport.BulkAction.CANCEL_GREEN_PENDING_BUYS))
                )),
                new PortfolioActionsMenu.Group("Clean Up Grids", "icons/delete.svg", List.of(
                        new PortfolioActionsMenu.Entry("Remove All Closed Positions",
                                "Archive finished trades so the grids show only live work; history keeps every fill.",
                                "icons/delete.svg",
                                controller::handleRemoveClosedPositions),
                        new PortfolioActionsMenu.Entry("Remove Inactive List",
                                "Archive completed and user-canceled rows out of Current Strategies.",
                                "icons/delete.svg",
                                controller::handleRemoveInactiveList),
                        new PortfolioActionsMenu.Entry("Clean All Expired",
                                "Archive rows whose broker order expired without filling.",
                                "icons/delete.svg",
                                controller::handleCleanAllExpired),
                        new PortfolioActionsMenu.Entry("Clean All Pending Base Buys",
                                "Delete pending recommendations that never placed a broker order.",
                                "icons/delete.svg",
                                controller::handleCleanPendingBaseBuys),
                        new PortfolioActionsMenu.Entry("Clean Invalid Strategies",
                                "Delete local rows the broker has no matching order or position for.",
                                "icons/delete.svg",
                                controller::handleCleanInvalidStrategies)
                )),
                new PortfolioActionsMenu.Group("History & Local Data", "icons/delete.svg", List.of(
                        new PortfolioActionsMenu.Entry("Clean Trade History",
                                "Permanently delete archived, completed, failed, and stopped history records.",
                                "icons/delete.svg",
                                controller::handleCleanTradeHistory),
                        liveView
                                ? deletePaperEntries.disabledWith("Paper cleanup is disabled while viewing LIVE mode.")
                                : deletePaperEntries
                )),
                new PortfolioActionsMenu.Group("Lifecycle", "icons/submit.svg", List.of(
                        new PortfolioActionsMenu.Entry("Resume All",
                                "Resume paused strategies so they monitor and execute again.",
                                "icons/submit.svg",
                                controller::handleResumeAll),
                        liveView
                                ? promoteAllToLive.disabledWith("Promote All to Live is unavailable while viewing LIVE mode.")
                                : promoteAllToLive
                ))
        );
    }
}
