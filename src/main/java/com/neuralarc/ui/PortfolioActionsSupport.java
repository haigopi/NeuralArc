package com.neuralarc.ui;

import com.neuralarc.model.Position;
import com.neuralarc.model.PauseReason;
import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyStatus;
import com.neuralarc.util.BrokerOrderStatusUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

final class PortfolioActionsSupport {
    private static boolean hasCancelablePendingLimitBuy(ManagedStrategy entry) {
        if (entry == null || entry.strategy == null || entry.strategy.status() != StrategyStatus.ACTIVE) {
            return false;
        }
        StrategyLifecycleState state = entry.strategy.currentState();
        return state == StrategyLifecycleState.BASE_BUY_PLACED
                || state == StrategyLifecycleState.BASE_BUY_PARTIALLY_FILLED
                || state == StrategyLifecycleState.BUY_LIMIT_1_PLACED
                || state == StrategyLifecycleState.BUY_LIMIT_1_PARTIALLY_FILLED
                || state == StrategyLifecycleState.BUY_LIMIT_2_PLACED
                || state == StrategyLifecycleState.BUY_LIMIT_2_PARTIALLY_FILLED;
    }

    private static boolean hasCancelablePendingLimitSell(ManagedStrategy entry) {
        if (entry == null || entry.strategy == null || entry.strategy.status() != StrategyStatus.ACTIVE) {
            return false;
        }
        StrategyLifecycleState state = entry.strategy.currentState();
        return state == StrategyLifecycleState.SELL_PLACED
                || state == StrategyLifecycleState.SELL_PARTIALLY_FILLED;
    }

    private static boolean isRemovableInactive(ManagedStrategy entry) {
        if (entry == null || entry.strategy == null) {
            return false;
        }
        StrategyStatus status = entry.strategy.status();
        if (status == StrategyStatus.COMPLETED) {
            return true;
        }
        if (status == StrategyStatus.PAUSED) {
            PauseReason pauseReason = entry.strategy.pauseReason();
            return pauseReason == PauseReason.MANUAL_LIMIT_BUY_CANCELED
                    || pauseReason == PauseReason.USER_PAUSED;
        }
        return false;
    }

    private static boolean isResumeEligible(ManagedStrategy entry) {
        if (entry == null || entry.strategy == null) {
            return false;
        }
        return entry.strategy.status() == StrategyStatus.PAUSED;
    }

    private static boolean isEligibleForManualSell(ManagedStrategy entry) {
        if (entry == null || entry.strategy == null) {
            return false;
        }
        StrategyStatus status = entry.strategy.status();
        if (status == StrategyStatus.COMPLETED
                || status == StrategyStatus.FAILED
                || status == StrategyStatus.STOPPED
                || status == StrategyStatus.ARCHIVED) {
            return false;
        }
        StrategyLifecycleState state = entry.strategy.currentState();
        if (isCanceledSellState(entry)) {
            return true;
        }
        return state != StrategyLifecycleState.COMPLETED
                && state != StrategyLifecycleState.FAILED
                && state != StrategyLifecycleState.STOPPED
                && state != StrategyLifecycleState.SELL_PLACED
                && state != StrategyLifecycleState.SELL_PARTIALLY_FILLED;
    }

    private static boolean isCanceledSellState(ManagedStrategy entry) {
        if (entry == null || entry.strategy == null) {
            return false;
        }
        StrategyLifecycleState state = entry.strategy.currentState();
        if (state != StrategyLifecycleState.SELL_PLACED && state != StrategyLifecycleState.SELL_PARTIALLY_FILLED) {
            return false;
        }
        String normalized = BrokerOrderStatusUtil.normalize(entry.strategy.latestOrderStatus());
        return "canceled".equals(normalized) || "cancelled".equals(normalized);
    }

    private static boolean isExpired(ManagedStrategy entry) {
        if (entry == null || entry.strategy == null) {
            return false;
        }
        if (!"expired".equals(BrokerOrderStatusUtil.normalize(entry.strategy.latestOrderStatus()))) {
            return false;
        }
        if (entry.strategy.status() == StrategyStatus.FAILED) {
            return true;
        }
        return entry.strategy.status() == StrategyStatus.ACTIVE && isPendingOrderState(entry.strategy.currentState());
    }

    private static boolean isInvalidLocalRecord(ManagedStrategy entry) {
        if (entry == null || entry.strategy == null) {
            return false;
        }
        String normalized = BrokerOrderStatusUtil.normalize(entry.strategy.latestOrderStatus());
        return entry.strategy.status() == StrategyStatus.FAILED
                && ("invalid".equals(normalized) || "invalid_local".equals(normalized));
    }

    private static boolean isPendingOrderState(StrategyLifecycleState state) {
        return state == StrategyLifecycleState.BASE_BUY_PLACED
                || state == StrategyLifecycleState.BASE_BUY_PARTIALLY_FILLED
                || state == StrategyLifecycleState.BUY_LIMIT_1_PLACED
                || state == StrategyLifecycleState.BUY_LIMIT_1_PARTIALLY_FILLED
                || state == StrategyLifecycleState.BUY_LIMIT_2_PLACED
                || state == StrategyLifecycleState.BUY_LIMIT_2_PARTIALLY_FILLED
                || state == StrategyLifecycleState.SELL_PLACED
                || state == StrategyLifecycleState.SELL_PARTIALLY_FILLED;
    }

    private static boolean isTradeHistoryRecord(ManagedStrategy entry) {
        if (entry == null || entry.strategy == null) {
            return false;
        }
        StrategyStatus status = entry.strategy.status();
        return status == StrategyStatus.ARCHIVED
                || status == StrategyStatus.COMPLETED
                || status == StrategyStatus.FAILED
                || status == StrategyStatus.STOPPED;
    }

    List<ManagedStrategy> filterTargets(List<ManagedStrategy> strategies, Scope scope) {
        List<ManagedStrategy> targets = new ArrayList<>();
        for (ManagedStrategy entry : strategies) {
            if (scope.matches(entry)) {
                targets.add(entry);
            }
        }
        return targets;
    }

    List<ManagedStrategy> filterTargets(List<ManagedStrategy> strategies, BulkAction action) {
        List<ManagedStrategy> targets = new ArrayList<>();
        for (ManagedStrategy entry : strategies) {
            if (action.matches(entry)) {
                targets.add(entry);
            }
        }
        return targets;
    }

    String buildConfirmationMessage(Scope scope, List<ManagedStrategy> targets) {
        return buildConfirmationMessage(scope.confirmHeading(targets.size()), targets, scope.confirmDetail());
    }

    String buildConfirmationMessage(BulkAction action, List<ManagedStrategy> targets) {
        return buildConfirmationMessage(action.confirmHeading(targets.size()), targets, action.confirmDetail());
    }

    private String buildConfirmationMessage(String heading, List<ManagedStrategy> targets, String detail) {
        String symbols = targets.stream()
                .limit(6)
                .map(entry -> entry.strategy.symbol())
                .collect(Collectors.joining(", "));
        String ellipsis = targets.size() > 6 ? ", ..." : "";
        return "<html><body style='width:360px'>"
                + "<b>" + heading + "</b><br><br>"
                + "Symbols: " + symbols + ellipsis + "<br><br>"
                + detail
                + "</body></html>";
    }

    String buildResultMessage(Scope scope, BatchResult result) {
        return buildResultMessage(scope.menuLabel(), "Submitted", result);
    }

    String buildResultMessage(BulkAction action, BatchResult result) {
        return buildResultMessage(action.menuLabel(), action.resultSuccessLabel(), result);
    }

    private String buildResultMessage(String label, String successLabel, BatchResult result) {
        StringBuilder sb = new StringBuilder("<html><body style='width:360px'>");
        sb.append("<b>").append(label).append("</b><br><br>");
        sb.append(successLabel).append(": ").append(result.successes().size());
        if (!result.successes().isEmpty()) {
            sb.append("<br>").append(String.join(", ", result.successes()));
        }
        if (!result.skipped().isEmpty()) {
            sb.append("<br><br><b>Skipped:</b><br>").append(String.join("<br>", result.skipped()));
        }
        if (!result.failures().isEmpty()) {
            sb.append("<br><br><b>Failed:</b><br>").append(String.join("<br>", result.failures()));
        }
        sb.append("</body></html>");
        return sb.toString();
    }

    record BatchResult(List<String> successes, List<String> failures, List<String> skipped) {
        BatchResult(List<String> successes, List<String> failures) {
            this(successes, failures, List.of());
        }

        BatchResult {
            successes = successes == null ? List.of() : List.copyOf(successes);
            failures = failures == null ? List.of() : List.copyOf(failures);
            skipped = skipped == null ? List.of() : List.copyOf(skipped);
        }
    }

    enum Scope {
        PROFITABLE("Sell Profitable Positions") {
            @Override
            boolean matches(ManagedStrategy entry) {
                Position position = entry.cachedPosition();
                return isEligibleForManualSell(entry)
                        && position.getTotalShares() > 0
                        && position.unrealizedPnl().compareTo(BigDecimal.ZERO) > 0;
            }

            @Override
            String confirmHeading(int count) {
                return "Sell " + count + " profitable position(s)?";
            }

            @Override
            String emptyMessage() {
                return "There are no profitable open positions to sell.";
            }

            @Override
            String confirmDetail() {
                return "Each strategy will submit a manual limit sell using its latest broker price."
                        + "<br>Strategies configured to repeat after exit can re-initiate after the position fully closes.";
            }
        },
        ALL_OPEN("Sell All Open Positions") {
            @Override
            boolean matches(ManagedStrategy entry) {
                return isEligibleForManualSell(entry)
                        && entry.cachedPosition().getTotalShares() > 0;
            }

            @Override
            String confirmHeading(int count) {
                return "Sell all " + count + " open position(s)?";
            }

            @Override
            String emptyMessage() {
                return "There are no open positions to sell.";
            }

            @Override
            String confirmDetail() {
                return "Each strategy will submit a manual limit sell using its latest broker price."
                        + "<br>Use this when you want to flatten all currently open positions with limit orders.";
            }
        },
        LOSS_ONLY("Sell Losing Positions") {
            @Override
            boolean matches(ManagedStrategy entry) {
                Position position = entry.cachedPosition();
                return isEligibleForManualSell(entry)
                        && position.getTotalShares() > 0
                        && position.unrealizedPnl().compareTo(BigDecimal.ZERO) < 0;
            }

            @Override
            String confirmHeading(int count) {
                return "Sell " + count + " losing position(s)?";
            }

            @Override
            String emptyMessage() {
                return "There are no losing open positions to sell.";
            }
            @Override
            String confirmDetail() {
                return "Each strategy will submit a manual limit sell using its latest broker price."
                        + "<br>Use this to place limit exits only for positions currently in loss.";
            }
        },
        LOSS_ONLY_MARKET("Sell All Losing Positions at Market Value") {
            @Override
            boolean matches(ManagedStrategy entry) {
                Position position = entry.cachedPosition();
                return isEligibleForManualSell(entry)
                        && position.getTotalShares() > 0
                        && position.unrealizedPnl().compareTo(BigDecimal.ZERO) < 0;
            }

            @Override
            String confirmHeading(int count) {
                return "Sell " + count + " losing position(s) at market?";
            }

            @Override
            String emptyMessage() {
                return "There are no losing open positions to sell at market.";
            }

            @Override
            String confirmDetail() {
                return "Each strategy will submit a manual market sell for immediate market execution."
                        + "<br>Final fill prices can vary from quotes due to market movement and liquidity.";
            }
        },
        PROFITABLE_MARKET("Sell All Profitable Positions at Market Value") {
            @Override
            boolean matches(ManagedStrategy entry) {
                Position position = entry.cachedPosition();
                return isEligibleForManualSell(entry)
                        && position.getTotalShares() > 0
                        && position.unrealizedPnl().compareTo(BigDecimal.ZERO) > 0;
            }

            @Override
            String confirmHeading(int count) {
                return "Sell " + count + " profitable position(s) at market?";
            }

            @Override
            String emptyMessage() {
                return "There are no profitable open positions to sell at market.";
            }

            @Override
            String confirmDetail() {
                return "Each strategy will submit a manual market sell for immediate market execution."
                        + "<br>Final fill prices can vary from quotes due to market movement and liquidity.";
            }
        },
        POSITION_ALL_SELL_TRIGGERS("Position All Sell Triggers") {
            @Override
            boolean matches(ManagedStrategy entry) {
                Position position = entry.cachedPosition();
                return isEligibleForManualSell(entry)
                        && position.getTotalShares() > 0
                        && entry.strategy.targetSellEnabled()
                        && entry.strategy.targetSellPrice().compareTo(BigDecimal.ZERO) > 0;
            }

            @Override
            String confirmHeading(int count) {
                return "Place GTC sell triggers for " + count + " open position(s)?";
            }

            @Override
            String emptyMessage() {
                return "There are no open positions with active sell triggers.";
            }

            @Override
            String confirmDetail() {
                return "Each strategy will place a GTC manual limit sell at its configured sell trigger price."
                        + "<br>Existing open orders for each symbol are canceled before placing the new trigger order.";
            }
        };

        private final String menuLabel;

        Scope(String menuLabel) {
            this.menuLabel = menuLabel;
        }

        abstract boolean matches(ManagedStrategy entry);

        abstract String confirmHeading(int count);

        abstract String emptyMessage();

        abstract String confirmDetail();

        String menuLabel() {
            return menuLabel;
        }

        String dialogTitle() {
            return menuLabel;
        }

        String logPrefix() {
            return "[" + menuLabel + "]";
        }
    }

    enum BulkAction {
        RESUME_ALL("Resume All") {
            @Override
            boolean matches(ManagedStrategy entry) {
                return isResumeEligible(entry);
            }

            @Override
            String confirmHeading(int count) {
                return "Resume " + count + " paused/canceled strategy(ies)?";
            }

            @Override
            String confirmDetail() {
                return "Matching paused strategies will be resumed for monitoring/execution using their mode-specific service."
                        + "<br>For market-closed suppression, strategies are resumed with safe market-close behavior.";
            }

            @Override
            String emptyMessage() {
                return "There are no paused or canceled strategies to resume.";
            }

            @Override
            String resultSuccessLabel() {
                return "Resumed";
            }
        },
        CANCEL_PENDING_LIMIT_BUYS("Cancel All Pending Limit Buys") {
            @Override
            boolean matches(ManagedStrategy entry) {
                return hasCancelablePendingLimitBuy(entry);
            }

            @Override
            String confirmHeading(int count) {
                return "Cancel pending limit buy orders for " + count + " cancelable strategy(ies)?";
            }

            @Override
            String confirmDetail() {
                return "Only pending limit buy orders will be canceled. Open positions and sell orders are not closed."
                        + "<br>Matching strategies will be paused and can be restarted with Place Limit Buy Again.";
            }

            @Override
            String emptyMessage() {
                return "There are no strategies with cancelable pending limit buy orders.";
            }
        },
        PLACE_PENDING_BASE_BUYS("Place Pending Base Buys") {
            @Override
            boolean matches(ManagedStrategy entry) {
                return entry != null
                        && entry.strategy != null
                        && PendingBaseBuyPlacementSupport.isPendingBaseBuyPlacement(entry.strategy);
            }

            @Override
            String confirmHeading(int count) {
                return "Place base limit buy orders for " + count + " pending strategy recommendation(s)?";
            }

            @Override
            String confirmDetail() {
                return "Matching scanner recommendations will submit their pending base buy limit order."
                        + "<br>If the base buy limit is above today's low, it is lowered to 10% below today's low before submission.";
            }

            @Override
            String emptyMessage() {
                return "There are no pending scanner recommendations waiting to place base buy orders.";
            }

            @Override
            String resultSuccessLabel() {
                return "Placed";
            }
        },
        AVERAGE_LOSING_POSITIONS("Average Down Losing Positions") {
            @Override
            boolean matches(ManagedStrategy entry) {
                if (!isEligibleForManualSell(entry)) {
                    return false;
                }
                Position position = entry.cachedPosition();
                return position.getTotalShares() > 0
                        && position.unrealizedPnl().compareTo(BigDecimal.ZERO) < 0;
            }

            @Override
            String confirmHeading(int count) {
                return "Average down " + count + " losing open position(s)?";
            }

            @Override
            String confirmDetail() {
                return "Each matching losing position will submit one manual buy order using the execution and sizing plan you selected."
                        + "<br>Use this when you intentionally want to lower average cost on positions currently below entry.";
            }

            @Override
            String emptyMessage() {
                return "There are no losing open positions to average.";
            }

            @Override
            String resultSuccessLabel() {
                return "Submitted";
            }
        },
        PLACE_AMBER_PENDING_BASE_BUYS("Place Limit Buy for Losing Pending Positions") {
            @Override
            boolean matches(ManagedStrategy entry) {
                return pendingBuyDirection(entry) == OrbPendingBaseBuyRowStyler.PriceDirection.AMBER_LOSER;
            }

            @Override
            String confirmHeading(int count) {
                return "Place base limit buy orders for " + count + " losing pending position(s)?";
            }

            @Override
            String confirmDetail() {
                return "Amber pending rows have a base-buy limit above the cached current stock price."
                        + "<br>Matching pending recommendations will submit their base buy limit order.";
            }

            @Override
            String emptyMessage() {
                return "There are no losing pending positions waiting to place limit buys.";
            }

            @Override
            String resultSuccessLabel() {
                return "Placed";
            }
        },
        READJUST_AMBER_PENDING_BASE_BUYS("Readjust Losing Pending Base Buy Positions") {
            @Override
            boolean matches(ManagedStrategy entry) {
                return pendingBuyDirection(entry) == OrbPendingBaseBuyRowStyler.PriceDirection.AMBER_LOSER;
            }

            @Override
            String confirmHeading(int count) {
                return "Readjust base buy limits for " + count + " losing pending position(s)?";
            }

            @Override
            String confirmDetail() {
                return "Amber pending rows have a base-buy limit above the cached current stock price."
                        + "<br>Matching pending recommendations will be recalculated to a lower base-buy limit so they are ready for placement.";
            }

            @Override
            String emptyMessage() {
                return "There are no losing pending positions eligible for base-buy readjustment.";
            }

            @Override
            String resultSuccessLabel() {
                return "Readjusted";
            }
        },
        PLACE_GREEN_PENDING_BASE_BUYS("Place Limit Buy for Gaining Pending Positions") {
            @Override
            boolean matches(ManagedStrategy entry) {
                return pendingBuyDirection(entry) == OrbPendingBaseBuyRowStyler.PriceDirection.GREEN_GAINER;
            }

            @Override
            String confirmHeading(int count) {
                return "Place base limit buy orders for " + count + " gaining pending position(s)?";
            }

            @Override
            String confirmDetail() {
                return "Green pending rows have a base-buy limit below the cached current stock price."
                        + "<br>Matching pending recommendations will submit their base buy limit order.";
            }

            @Override
            String emptyMessage() {
                return "There are no gaining pending positions waiting to place limit buys.";
            }

            @Override
            String resultSuccessLabel() {
                return "Placed";
            }
        },
        CLEAN_PENDING_BASE_BUYS("Clean All Pending Base Buys") {
            @Override
            boolean matches(ManagedStrategy entry) {
                return entry != null
                        && entry.strategy != null
                        && PendingBaseBuyPlacementSupport.isPendingBaseBuyPlacement(entry.strategy);
            }

            @Override
            String confirmHeading(int count) {
                return "Delete " + count + " pending base-buy recommendation(s)?";
            }

            @Override
            String confirmDetail() {
                return "This permanently deletes pending scanner recommendations that have not placed a broker order yet."
                        + "<br>Submitted orders, open positions, and active strategies are not included.";
            }

            @Override
            String emptyMessage() {
                return "There are no pending base-buy recommendations to delete.";
            }

            @Override
            String resultSuccessLabel() {
                return "Deleted";
            }
        },
        CANCEL_AMBER_PENDING_BUYS("Cancel all Amber Pending Buys (Losers)") {
            @Override
            boolean matches(ManagedStrategy entry) {
                return pendingBuyDirection(entry) == OrbPendingBaseBuyRowStyler.PriceDirection.AMBER_LOSER;
            }

            @Override
            String confirmHeading(int count) {
                return "Cancel " + count + " amber pending buy recommendation(s)?";
            }

            @Override
            String confirmDetail() {
                return "Amber rows have a base-buy limit above the cached current stock price."
                        + "<br>Only unsubmitted pending recommendations are removed; broker orders and positions are not affected.";
            }

            @Override
            String emptyMessage() {
                return "There are no amber pending buy recommendations to cancel.";
            }

            @Override
            String resultSuccessLabel() {
                return "Canceled";
            }
        },
        CANCEL_GREEN_PENDING_BUYS("Cancel all Green Pending Buys (Gainer)") {
            @Override
            boolean matches(ManagedStrategy entry) {
                return pendingBuyDirection(entry) == OrbPendingBaseBuyRowStyler.PriceDirection.GREEN_GAINER;
            }

            @Override
            String confirmHeading(int count) {
                return "Cancel " + count + " green pending buy recommendation(s)?";
            }

            @Override
            String confirmDetail() {
                return "Green rows have a base-buy limit below the cached current stock price."
                        + "<br>Only unsubmitted pending recommendations are removed; broker orders and positions are not affected.";
            }

            @Override
            String emptyMessage() {
                return "There are no green pending buy recommendations to cancel.";
            }

            @Override
            String resultSuccessLabel() {
                return "Canceled";
            }
        },
        CANCEL_PENDING_LIMIT_SELLS("Cancel All Pending Limit Sells") {
            @Override
            boolean matches(ManagedStrategy entry) {
                return hasCancelablePendingLimitSell(entry);
            }

            @Override
            String confirmHeading(int count) {
                return "Cancel pending limit sell orders for " + count + " cancelable strategy(ies)?";
            }

            @Override
            String confirmDetail() {
                return "Only pending limit sell orders will be canceled. Open positions remain open."
                        + "<br>Matching strategies return to waiting-for-next-rule evaluation.";
            }

            @Override
            String emptyMessage() {
                return "There are no strategies with cancelable pending limit sell orders.";
            }
        },
        REMOVE_INACTIVE_LIST("Remove Inactive List") {
            @Override
            boolean matches(ManagedStrategy entry) {
                return isRemovableInactive(entry);
            }

            @Override
            String confirmHeading(int count) {
                return "Remove " + count + " completed/canceled strategy(ies) from Current Strategies?";
            }

            @Override
            String confirmDetail() {
                return "Matching strategies will be archived and removed from the Current Strategies tab."
                        + "<br>They remain available in repository/history records.";
            }

            @Override
            String emptyMessage() {
                return "There are no completed or canceled strategies to remove from Current Strategies.";
            }

            @Override
            String resultSuccessLabel() {
                return "Archived";
            }
        },
        CLEAN_ALL_EXPIRED("Clean All Expired") {
            @Override
            boolean matches(ManagedStrategy entry) {
                return isExpired(entry);
            }

            @Override
            String confirmHeading(int count) {
                return "Clean " + count + " expired strategy(ies) from Current Strategies?";
            }

            @Override
            String confirmDetail() {
                return "Matching expired strategies will be archived and removed from the Current Strategies tab."
                        + "<br>History records remain available.";
            }

            @Override
            String emptyMessage() {
                return "There are no expired strategies to clean from Current Strategies.";
            }

            @Override
            String resultSuccessLabel() {
                return "Archived";
            }
        },
        CLEAN_INVALID("Clean Invalid Strategies") {
            @Override
            boolean matches(ManagedStrategy entry) {
                return isInvalidLocalRecord(entry);
            }

            @Override
            String confirmHeading(int count) {
                return "Delete " + count + " invalid local strategy record(s)?";
            }

            @Override
            String confirmDetail() {
                return "Invalid records no longer match an open Alpaca order or broker position."
                        + "<br>They will be permanently deleted from local strategy history.";
            }

            @Override
            String emptyMessage() {
                return "There are no invalid local strategy records to clean.";
            }

            @Override
            String resultSuccessLabel() {
                return "Deleted";
            }
        },
        CLEAN_TRADE_HISTORY("Clean Trade History") {
            @Override
            boolean matches(ManagedStrategy entry) {
                return isTradeHistoryRecord(entry);
            }

            @Override
            String confirmHeading(int count) {
                return "Delete " + count + " trade history record(s)?";
            }

            @Override
            String confirmDetail() {
                return "Archived, completed, failed, and stopped strategy history will be permanently removed locally."
                        + "<br>Active and paused current strategies are not deleted.";
            }

            @Override
            String emptyMessage() {
                return "There are no trade history records to clean.";
            }

            @Override
            String resultSuccessLabel() {
                return "Deleted";
            }
        },
        DELETE_ALL_PAPER_MODE_ENTRIES("Delete All Paper Mode Entries") {
            @Override
            boolean matches(ManagedStrategy entry) {
                return entry != null
                        && entry.strategy != null
                        && entry.strategy.mode() == com.neuralarc.model.StrategyMode.PAPER;
            }

            @Override
            String confirmHeading(int count) {
                return "Permanently delete " + count + " PAPER mode strategy entr(ies)?";
            }

            @Override
            String confirmDetail() {
                return "This permanently removes all local PAPER strategy entries, orders, and execution events."
                        + "<br>LIVE strategies are never included."
                        + "<br>Broker paper positions are not sold by this cleanup action.";
            }

            @Override
            String emptyMessage() {
                return "There are no PAPER mode entries to delete.";
            }

            @Override
            String resultSuccessLabel() {
                return "Deleted";
            }
        },
        REPOSITION_EXPIRED("Reposition Expired") {
            @Override
            boolean matches(ManagedStrategy entry) {
                return isExpired(entry);
            }

            @Override
            String confirmHeading(int count) {
                return "Reposition " + count + " expired strategy(ies)?";
            }

            @Override
            String confirmDetail() {
                return "Matching expired strategies will be reactivated and submit a fresh base limit buy order."
                        + "<br>Strategies with open positions or open orders are skipped for safety.";
            }

            @Override
            String emptyMessage() {
                return "There are no expired strategies eligible for reposition.";
            }

            @Override
            String resultSuccessLabel() {
                return "Repositioned";
            }
        },
        PROMOTE_ALL_TO_LIVE("Promote All to Live") {
            @Override
            boolean matches(ManagedStrategy entry) {
                return entry != null
                        && entry.strategy != null
                        && StrategyPromotionEligibility.canPromoteToLive(entry.strategy);
            }

            @Override
            String confirmHeading(int count) {
                return "Promote " + count + " paper strategy(ies) to LIVE?";
            }

            @Override
            String confirmDetail() {
                return "Each eligible paper strategy will be validated with the existing live promotion rules."
                        + "<br>Successful promotions create LIVE strategies and archive the paper copies.";
            }

            @Override
            String emptyMessage() {
                return "There are no eligible paper strategies to promote.";
            }
        },
        POSITION_SELL_PROFIT_THRESHOLD_PERCENTAGE("Position all Sell Profit Threshold percentage") {
            @Override
            boolean matches(ManagedStrategy entry) {
                Position position = entry.cachedPosition();
                BigDecimal baseBuy = entry.strategy.baseBuyLimitPrice();
                BigDecimal sellTrigger = entry.strategy.targetSellPrice();
                return isEligibleForManualSell(entry)
                        && position.getTotalShares() > 0
                        && entry.strategy.targetSellEnabled()
                        && sellTrigger != null
                        && baseBuy != null
                        && sellTrigger.compareTo(BigDecimal.ZERO) > 0
                        && baseBuy.compareTo(BigDecimal.ZERO) > 0
                        && sellTrigger.compareTo(baseBuy) > 0;
            }

            @Override
            String confirmHeading(int count) {
                return "Convert sell triggers to profit threshold mode for " + count + " open position(s)?";
            }

            @Override
            String confirmDetail() {
                return "Each matching strategy keeps its sell trigger price as the profit threshold baseline."
                        + "<br>The chosen trailing pullback percent is then applied for profit-hold exits.";
            }

            @Override
            String emptyMessage() {
                return "There are no open positions with active sell triggers eligible for profit-threshold conversion.";
            }

            @Override
            String resultSuccessLabel() {
                return "Updated";
            }
        };

        private final String menuLabel;

        BulkAction(String menuLabel) {
            this.menuLabel = menuLabel;
        }

        abstract boolean matches(ManagedStrategy entry);

        abstract String confirmHeading(int count);

        abstract String confirmDetail();

        abstract String emptyMessage();

        String resultSuccessLabel() {
            return "Succeeded";
        }

        String menuLabel() {
            return menuLabel;
        }

        String dialogTitle() {
            return menuLabel;
        }

        String logPrefix() {
            return "[" + menuLabel + "]";
        }

        private static OrbPendingBaseBuyRowStyler.PriceDirection pendingBuyDirection(ManagedStrategy entry) {
            if (entry == null || entry.strategy == null
                    || !PendingBaseBuyPlacementSupport.isPendingBaseBuyPlacement(entry.strategy)) {
                return null;
            }
            return OrbPendingBaseBuyRowStyler.priceDirection(entry.strategy, entry.cachedPosition());
        }
    }
}
