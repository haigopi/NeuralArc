package com.neuralarc.ui;

import com.neuralarc.model.PauseReason;
import com.neuralarc.model.Position;
import com.neuralarc.model.ProfitHoldType;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyStatus;
import com.neuralarc.util.BrokerOrderStatusUtil;
import com.neuralarc.util.Monetary;

import java.math.BigDecimal;

public final class StrategyTablePresenter {
    public String formatLifecycleStateForDisplay(StrategyLifecycleState state) {
        if (state == null) {
            return "";
        }
        return switch (state) {
            case CREATED -> "Created";
            case VALIDATED -> "Validated";
            case BASE_BUY_PLACED -> "Limit Base Buy Placed";
            case BASE_BUY_PARTIALLY_FILLED -> "Limit Base Buy Partially Filled";
            case BASE_BUY_FILLED -> "Base Buy Filled";
            case BUY_LIMIT_1_PLACED -> "Limit Buy 1 Placed";
            case BUY_LIMIT_1_PARTIALLY_FILLED -> "Limit Buy 1 Partially Filled";
            case BUY_LIMIT_1_FILLED -> "Buy Limit 1 Filled";
            case BUY_LIMIT_2_PLACED -> "Limit Buy 2 Placed";
            case BUY_LIMIT_2_PARTIALLY_FILLED -> "Limit Buy 2 Partially Filled";
            case BUY_LIMIT_2_FILLED -> "Buy Limit 2 Filled";
            case STOP_LOSS_ACTIVE -> "Stop Loss Active";
            case PROFIT_HOLD_ACTIVE -> "Profit Hold Active";
            case SELL_PLACED -> "Limit Sell Placed";
            case SELL_PARTIALLY_FILLED -> "Limit Sell Partially Filled";
            case QUEUED_FOR_OPEN -> "Queued For Open";
            case COMPLETED -> "Completed";
            case PAUSED -> "Canceled";
            case FAILED -> "Position Closed";
            case STOPPED -> "Stopped";
        };
    }

    public String displayStatusLabel(
            Strategy strategy,
            boolean marketClosedSuppressed,
            boolean waitingForFill,
            boolean queueableSessionError
    ) {
        return displayStatusLabel(strategy, null, marketClosedSuppressed, waitingForFill, queueableSessionError);
    }

    public String displayStatusLabel(
            Strategy strategy,
            Position position,
            boolean marketClosedSuppressed,
            boolean waitingForFill,
            boolean queueableSessionError
    ) {
        return displayStatusLabel(strategy, position, marketClosedSuppressed, waitingForFill, queueableSessionError, true);
    }

    public String displayStatusLabel(
            Strategy strategy,
            Position position,
            boolean marketClosedSuppressed,
            boolean waitingForFill,
            boolean queueableSessionError,
            boolean brokerUnavailableActive
    ) {
        return displayStatusLabel(
                strategy,
                position,
                marketClosedSuppressed,
                waitingForFill,
                queueableSessionError,
                brokerUnavailableActive,
                null,
                null
        );
    }

    public String displayStatusLabel(
            Strategy strategy,
            Position position,
            boolean marketClosedSuppressed,
            boolean waitingForFill,
            boolean queueableSessionError,
            boolean brokerUnavailableActive,
            BigDecimal realizedPnl
    ) {
        return displayStatusLabel(
                strategy,
                position,
                marketClosedSuppressed,
                waitingForFill,
                queueableSessionError,
                brokerUnavailableActive,
                realizedPnl,
                null
        );
    }

    public String displayStatusLabel(
            Strategy strategy,
            Position position,
            boolean marketClosedSuppressed,
            boolean waitingForFill,
            boolean queueableSessionError,
            boolean brokerUnavailableActive,
            BigDecimal realizedPnl,
            PendingOrderSummary pendingManualBuy
    ) {
        if (strategy == null) {
            return "";
        }
        if ("QUEUED_FOR_OPEN".equalsIgnoreCase(strategy.latestOrderStatus())) {
            return "Queued For Open";
        }
        if (strategy.status() == StrategyStatus.ARCHIVED) {
            return "Archived";
        }
        if (strategy.status() == StrategyStatus.ACTIVE
                && strategy.pauseReason() == PauseReason.MANUAL_MARKET_CLOSED_OVERRIDE
                && marketClosedSuppressed) {
            return formatLifecycleStateForDisplay(strategy.currentState()) + " (Market Closed)";
        }
        if (strategy.status() == StrategyStatus.PAUSED
                && strategy.pauseReason() == PauseReason.AUTO_MARKET_CLOSED
                && marketClosedSuppressed) {
            return "Auto Paused (Market Closed)";
        }
        if (strategy.status() == StrategyStatus.PAUSED && strategy.pauseReason() == PauseReason.USER_PAUSED) {
            return "Canceled";
        }
        if (strategy.status() == StrategyStatus.PAUSED
                && strategy.pauseReason() == PauseReason.MANUAL_LIMIT_BUY_CANCELED) {
            return "Cancelled by user. Waiting for manual restart.";
        }
        if (strategy.status() == StrategyStatus.PAUSED && strategy.pauseReason() == PauseReason.SYSTEM_ERROR) {
            return "Canceled (System Error)";
        }
        if (strategy.status() == StrategyStatus.FAILED && queueableSessionError) {
            return "Queued For Open";
        }
        if (strategy.status() == StrategyStatus.FAILED) {
            String normalized = BrokerOrderStatusUtil.normalize(strategy.latestOrderStatus());
            if ("invalid".equals(normalized) || "invalid_local".equals(normalized)) {
                return "Invalid - not found at broker";
            }
            if ("expired".equals(normalized)) {
                return expiredStatusLabel(strategy);
            }
        }
        String completedLabel = completedBookedStatusLabel(strategy, realizedPnl);
        if (!completedLabel.isBlank()) {
            return completedLabel;
        }
        String lifecycle = appendPlacedQuantity(
                formatLifecycleStateForDisplay(strategy.currentState()),
                strategy,
                position
        );
        String normalizedStatus = BrokerOrderStatusUtil.normalize(strategy.latestOrderStatus());
        if (strategy.status() == StrategyStatus.ACTIVE
                && brokerUnavailableActive
                && isBrokerUnavailableStatus(normalizedStatus)) {
            return lifecycle + " (Retrying: Unable to reach broker)";
        }
        if (strategy.status() == StrategyStatus.ACTIVE
                && waitingForFill
                && strategy.latestOrderStatus() != null
                && !strategy.latestOrderStatus().isBlank()
                && isManualBuyLatest(strategy)
                && !isBrokerUnavailableStatus(normalizedStatus)) {
            return manualBuyPendingLabel(strategy, pendingManualBuy)
                    + " (" + BrokerOrderStatusUtil.displayLabel(strategy.latestOrderStatus()) + ")";
        }
        if (strategy.status() == StrategyStatus.ACTIVE
                && waitingForFill
                && strategy.latestOrderStatus() != null
                && !strategy.latestOrderStatus().isBlank()
                && !isBrokerUnavailableStatus(normalizedStatus)) {
            return lifecycle + " (" + BrokerOrderStatusUtil.displayLabel(strategy.latestOrderStatus()) + ")";
        }
        if (strategy.status() == StrategyStatus.ACTIVE && isWaitingForNextRule(strategy.currentState())) {
            String activeRule = resolveActiveRuleLabel(strategy, position);
            return activeRule.isBlank() ? lifecycle + " - Monitoring next configured rule" : activeRule;
        }
        return lifecycle;
    }

    private String completedBookedStatusLabel(Strategy strategy, BigDecimal realizedPnl) {
        if (strategy == null
                || strategy.status() != StrategyStatus.COMPLETED
                || strategy.currentState() != StrategyLifecycleState.COMPLETED) {
            return "";
        }
        BigDecimal booked = realizedPnl == null ? BigDecimal.ZERO : Monetary.round(realizedPnl);
        if (booked.compareTo(BigDecimal.ZERO) > 0) {
            return "Completed - Profit Booked $" + booked.toPlainString();
        }
        if (booked.compareTo(BigDecimal.ZERO) < 0) {
            return "Completed - Loss Booked $" + booked.abs().toPlainString();
        }
        return "Completed";
    }

    private String expiredStatusLabel(Strategy strategy) {
        String waitingRule = expiredWaitingRuleLabel(strategy);
        if (waitingRule.isBlank()) {
            return "Expired";
        }
        return "Expired (" + waitingRule + " - waiting to fill)";
    }

    private String expiredWaitingRuleLabel(Strategy strategy) {
        StrategyLifecycleState state = strategy.currentState();
        if (isPendingOrderState(state)) {
            return formatLifecycleStateForDisplay(state);
        }
        String rule = strategy.lastTriggeredRuleType() == null
                ? ""
                : strategy.lastTriggeredRuleType().trim().toUpperCase();
        return switch (rule) {
            case "BUY_RULE", "BASE_BUY" -> formatLifecycleStateForDisplay(StrategyLifecycleState.BASE_BUY_PLACED);
            case "LOSS_BUY_RULE", "BUY_LIMIT_1" -> formatLifecycleStateForDisplay(StrategyLifecycleState.BUY_LIMIT_1_PLACED);
            case "LOSS_INVESTMENT_BUY_RULE", "BUY_LIMIT_2" -> formatLifecycleStateForDisplay(StrategyLifecycleState.BUY_LIMIT_2_PLACED);
            case "SELL_RULE", "TARGET_SELL" -> formatLifecycleStateForDisplay(StrategyLifecycleState.SELL_PLACED);
            case "STOP_LOSS_RULE", "STOP_LOSS" -> "Stop Loss Sell";
            case "PROFIT_EXIT", "ALPACA_TRAILING_STOP" -> "Profit Exit Sell";
            case "LOSS_EXIT" -> "Loss Exit Sell";
            case "MANUAL_BUY" -> "Manual Market Buy";
            case "MANUAL_EXIT", "CLOSE_POSITION" -> "Manual Exit Sell";
            default -> "";
        };
    }

    private boolean isManualBuyLatest(Strategy strategy) {
        return strategy.lastTriggeredRuleType() != null
                && "MANUAL_BUY".equalsIgnoreCase(strategy.lastTriggeredRuleType().trim());
    }

    private String manualBuyPendingLabel(Strategy strategy, PendingOrderSummary pendingManualBuy) {
        String event = strategy.lastEvent() == null ? "" : strategy.lastEvent().toLowerCase();
        String orderDetails = pendingOrderDetails(pendingManualBuy);
        if (event.contains("limit")) {
            return "Manual Limit Buy Pending Fill" + orderDetails;
        }
        if (event.contains("market")) {
            return "Manual Market Buy Pending Fill" + orderDetails;
        }
        return "Manual Buy Pending Fill" + orderDetails;
    }

    private String pendingOrderDetails(PendingOrderSummary pendingOrder) {
        if (pendingOrder == null) {
            return "";
        }
        BigDecimal price = pendingOrder.limitPrice();
        BigDecimal quantity = pendingOrder.quantity();
        String quantityText = quantity != null && quantity.compareTo(BigDecimal.ZERO) > 0
                ? "/" + quantity.stripTrailingZeros().toPlainString()
                : "";
        if (price != null && price.compareTo(BigDecimal.ZERO) > 0) {
            return " - @ $" + price.toPlainString() + quantityText;
        }
        if (!quantityText.isBlank()) {
            return " - Qty " + quantityText.substring(1);
        }
        return "";
    }

    private boolean isPendingOrderState(StrategyLifecycleState state) {
        return state == StrategyLifecycleState.BASE_BUY_PLACED
                || state == StrategyLifecycleState.BASE_BUY_PARTIALLY_FILLED
                || state == StrategyLifecycleState.BUY_LIMIT_1_PLACED
                || state == StrategyLifecycleState.BUY_LIMIT_1_PARTIALLY_FILLED
                || state == StrategyLifecycleState.BUY_LIMIT_2_PLACED
                || state == StrategyLifecycleState.BUY_LIMIT_2_PARTIALLY_FILLED             || state == StrategyLifecycleState.SELL_PLACED
                || state == StrategyLifecycleState.SELL_PARTIALLY_FILLED;
    }

    private String resolveActiveRuleLabel(Strategy strategy, Position position) {
        if (!isProfitablePosition(position) && (position == null || position.getTotalShares() <= 0)) {
            return "";
        }
        if (strategy.currentState() == StrategyLifecycleState.PROFIT_HOLD_ACTIVE && strategy.profitHoldEnabled()) {
            return "Profit Hold active"
                    + profitHoldDescription(strategy)
                    + currentPriceDescription(position)
                    + " - monitoring trailing protection";
        }
        if (isProfitablePosition(position) && strategy.targetSellEnabled()) {
            return "Sell trigger active"
                    + priceDescription(" @ $", strategy.targetSellPrice())
                    + currentPriceDescription(position)
                    + " - monitoring for sell trigger";
        }
        if (strategy.automatedStopLossEnabled()) {
            return "Stop loss active"
                    + priceDescription(" @ $", strategy.stopLossPrice())
                    + currentPriceDescription(position)
                    + " - monitoring downside protection";
        }
        return "";
    }

    private String profitHoldDescription(Strategy strategy) {
        if (strategy.profitHoldType() == ProfitHoldType.FIXED_AMOUNT_TRAILING) {
            return amountDescription(" by $", strategy.profitHoldAmount());
        }
        return amountDescription(" by ", strategy.profitHoldPercent(), "%");
    }

    private String currentPriceDescription(Position position) {
        if (position == null || position.getLastPrice().compareTo(BigDecimal.ZERO) <= 0) {
            return "";
        }
        return " | Current $" + position.getLastPrice().toPlainString();
    }

    private String priceDescription(String prefix, BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            return "";
        }
        return prefix + value.toPlainString();
    }

    private String amountDescription(String prefix, BigDecimal value) {
        return amountDescription(prefix, value, "");
    }

    private String amountDescription(String prefix, BigDecimal value, String suffix) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            return "";
        }
        return prefix + value.toPlainString() + suffix;
    }

    private boolean isProfitablePosition(Position position) {
        return position != null
                && position.getTotalShares() > 0
                && position.unrealizedPnl().compareTo(BigDecimal.ZERO) > 0;
    }

    private boolean isWaitingForNextRule(StrategyLifecycleState state) {
        return state == StrategyLifecycleState.BASE_BUY_FILLED
                || state == StrategyLifecycleState.BUY_LIMIT_1_FILLED
                || state == StrategyLifecycleState.BUY_LIMIT_2_FILLED
                || state == StrategyLifecycleState.STOP_LOSS_ACTIVE
                || state == StrategyLifecycleState.PROFIT_HOLD_ACTIVE;
    }

    private boolean isBrokerUnavailableStatus(String normalizedStatus) {
        return "failed_transport".equals(normalizedStatus) || "api_error".equals(normalizedStatus);
    }

    private String appendPlacedQuantity(String lifecycle, Strategy strategy, Position position) {
        int quantity = placedQuantity(strategy, position);
        if (quantity <= 0 || lifecycle == null || lifecycle.isBlank()) {
            return lifecycle;
        }
        BigDecimal price = placedPrice(strategy);
        if (price != null && price.compareTo(BigDecimal.ZERO) > 0) {
            return lifecycle + " - @ $" + price.toPlainString() + "/" + quantity;
        }
        return lifecycle + " - Qty " + quantity;
    }

    private BigDecimal placedPrice(Strategy strategy) {
        if (strategy == null || strategy.currentState() == null) {
            return BigDecimal.ZERO;
        }
        return switch (strategy.currentState()) {
            case BASE_BUY_PLACED, BASE_BUY_PARTIALLY_FILLED, QUEUED_FOR_OPEN -> strategy.baseBuyLimitPrice();
            case BUY_LIMIT_1_PLACED, BUY_LIMIT_1_PARTIALLY_FILLED -> strategy.buyLimit1Price();
            case BUY_LIMIT_2_PLACED, BUY_LIMIT_2_PARTIALLY_FILLED -> strategy.buyLimit2Price();
            case SELL_PLACED, SELL_PARTIALLY_FILLED -> strategy.targetSellPrice();
            default -> BigDecimal.ZERO;
        };
    }

    private int placedQuantity(Strategy strategy, Position position) {
        if (strategy == null || strategy.currentState() == null) {
            return 0;
        }
        return switch (strategy.currentState()) {
            case BASE_BUY_PLACED, BASE_BUY_PARTIALLY_FILLED, QUEUED_FOR_OPEN -> Math.max(0, strategy.baseBuyQuantity());
            case BUY_LIMIT_1_PLACED, BUY_LIMIT_1_PARTIALLY_FILLED -> Math.max(0, strategy.buyLimit1Quantity());
            case BUY_LIMIT_2_PLACED, BUY_LIMIT_2_PARTIALLY_FILLED -> Math.max(0, strategy.buyLimit2Quantity());
            case SELL_PLACED, SELL_PARTIALLY_FILLED -> placedSellQuantity(strategy, position);
            default -> 0;
        };
    }

    private int placedSellQuantity(Strategy strategy, Position position) {
        int positionShares = position == null ? 0 : position.getTotalShares();
        if (positionShares <= 0) {
            return 0;
        }
        BigDecimal targetQuantity = strategy.targetSellQuantity(BigDecimal.valueOf(positionShares));
        if (targetQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            return positionShares;
        }
        return Math.max(1, targetQuantity.intValue());
    }

    public Object valueAt(
            Strategy strategy,
            Position position,
            BigDecimal lastSellPrice,
            BigDecimal realizedPnl,
            int columnIndex,
            String statusLabel
    ) {
        if (columnIndex >= 2 && columnIndex <= 6) {
            return switch (columnIndex) {
                case 2 -> displayQuantity(strategy, position);
                case 3 -> position.getTotalShares() > 0 ? position.getAverageCost().toPlainString() : "-";
                case 4 -> displayPrice(position, lastSellPrice);
                case 5 -> position.getTotalShares() > 0 ? position.marketValue().toPlainString() : "-";
                case 6 -> displayPnl(position, realizedPnl);
                default -> "";
            };
        }
        return switch (columnIndex) {
            case 0 -> strategy.symbol();
            case 1 -> statusLabel;
            case 7 -> strategy.pollingIntervalSeconds();
            case 9 -> entrySource(strategy);
            case 10 -> exitSource(strategy);
            case 11 -> statusLabel;
            default -> "";
        };
    }

    // Backward-compatible overload used by older tests/call sites; broker mode is no longer a grid column.
    public Object valueAt(
            Strategy strategy,
            Position position,
            BigDecimal lastSellPrice,
            BigDecimal realizedPnl,
            int columnIndex,
            String statusLabel,
            String brokerModeLabel
    ) {
        return valueAt(strategy, position, lastSellPrice, realizedPnl, columnIndex, statusLabel);
    }


    private String entrySource(Strategy strategy) {
        if (strategy == null) {
            return "";
        }
        String name = normalizedText(strategy.name());
        String event = normalizedText(strategy.lastEvent());
        String combined = name + " " + event;
        if (isSmartPicksSource(combined)) {
            return smartPicksEntrySource(combined);
        }
        if (isBrokerSyncedSource(combined)) {
            return "Broker Synced";
        }
        if (isPromotedLiveSource(combined)) {
            return "Promoted Live";
        }
        return "Manually Added";
    }

    private String normalizedText(String value) {
        return value == null ? "" : value.toLowerCase();
    }

    private boolean isSmartPicksSource(String combined) {
        return combined.contains("smart_picks")
                || combined.contains("smart picks")
                // Legacy strategies persisted before the Smart Picks rename.
                || combined.contains("i_am_feeling_lucky")
                || combined.contains("i am feeling lucky");
    }

    private String smartPicksEntrySource(String combined) {
        if (combined.contains("gainer")) {
            return "Picks [Gainers]";
        }
        if (combined.contains("loser")) {
            return "Picks [Losers]";
        }
        if (combined.contains("reviewed")) {
            return "Picks [Reviewed]";
        }
        return "Picks";
    }

    private boolean isBrokerSyncedSource(String combined) {
        return combined.contains("remote strategy")
                || combined.contains("synced from alpaca")
                || combined.contains("remote alpaca")
                || combined.contains("remote state")
                || combined.contains("broker direct");
    }

    private boolean isPromotedLiveSource(String combined) {
        return combined.contains("promoted live")
                || combined.contains("promotion to live")
                || combined.contains("after live promotion");
    }

    private String exitSource(Strategy strategy) {
        if (strategy == null) {
            return "";
        }
        if (!isSellCompleted(strategy)) {
            return "";
        }
        String rule = strategy.lastTriggeredRuleType();
        if (rule == null || rule.isBlank()) {
            String event = strategy.lastEvent() == null ? "" : strategy.lastEvent().toLowerCase();
            if (event.contains("synced from alpaca remote state")) {
                return "Broker Direct";
            }
            return "";
        }
        if (isBuyRule(rule)) {
            return "";
        }
        if ("MANUAL_EXIT".equalsIgnoreCase(rule)) {
            return "Manual Exit";
        }
        if ("STOP_LOSS".equalsIgnoreCase(rule)) {
            return "Stop Loss";
        }
        if ("TARGET_SELL".equalsIgnoreCase(rule)) {
            return "Sell Trigger";
        }
        if ("PROFIT_EXIT".equalsIgnoreCase(rule)) {
            return "Profit Exit";
        }
        if ("REMOTE_SYNC".equalsIgnoreCase(rule)) {
            return "Broker Direct";
        }
        return "System Exit (" + rule + ")";
    }

    private boolean isSellCompleted(Strategy strategy) {
        return strategy.status() == StrategyStatus.COMPLETED
                || strategy.currentState() == StrategyLifecycleState.COMPLETED;
    }

    private boolean isBuyRule(String rule) {
        return "BASE_BUY".equalsIgnoreCase(rule)
                || "BUY_LIMIT_1".equalsIgnoreCase(rule)
                || "BUY_LIMIT_2".equalsIgnoreCase(rule);
    }

    private Object displayQuantity(Strategy strategy, Position position) {
        if (position.getTotalShares() > 0) {
            return position.getTotalShares();
        }
        if (strategy == null || strategy.status() != StrategyStatus.ACTIVE) {
            return 0;
        }
        return switch (strategy.currentState()) {
            case BASE_BUY_PLACED, BASE_BUY_PARTIALLY_FILLED, QUEUED_FOR_OPEN -> Math.max(0, strategy.baseBuyQuantity());
            case BUY_LIMIT_1_PLACED, BUY_LIMIT_1_PARTIALLY_FILLED -> Math.max(0, strategy.buyLimit1Quantity());
            case BUY_LIMIT_2_PLACED, BUY_LIMIT_2_PARTIALLY_FILLED -> Math.max(0, strategy.buyLimit2Quantity());
            default -> 0;
        };
    }

    private String displayPrice(Position position, BigDecimal lastSellPrice) {
        if (position.getLastPrice().compareTo(BigDecimal.ZERO) > 0) {
            return position.getLastPrice().toPlainString();
        }
        if (lastSellPrice != null && lastSellPrice.compareTo(BigDecimal.ZERO) > 0) {
            return lastSellPrice.toPlainString();
        }
        return "-";
    }

    private String displayPnl(Position position, BigDecimal realizedPnl) {
        if (position.getTotalShares() > 0) {
            return position.unrealizedPnl().toPlainString();
        }
        if (realizedPnl != null && realizedPnl.compareTo(BigDecimal.ZERO) != 0) {
            return realizedPnl.toPlainString();
        }
        return "-";
    }

    public record PendingOrderSummary(BigDecimal limitPrice, BigDecimal quantity) {
        public PendingOrderSummary {
            limitPrice = Monetary.round(limitPrice == null ? BigDecimal.ZERO : limitPrice);
            quantity = Monetary.round(quantity == null ? BigDecimal.ZERO : quantity);
        }
    }
}
