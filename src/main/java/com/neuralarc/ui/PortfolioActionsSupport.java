package com.neuralarc.ui;

import com.neuralarc.model.Position;
import com.neuralarc.model.PauseReason;
import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyMode;
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
                        && entry.strategy.mode() == StrategyMode.PAPER
                        && (entry.strategy.status() == StrategyStatus.ACTIVE || entry.strategy.status() == StrategyStatus.PAUSED);
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
                return "There are no active or paused paper strategies to promote.";
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
    }
}
