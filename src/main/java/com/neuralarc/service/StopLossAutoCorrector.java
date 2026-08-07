package com.neuralarc.service;

import com.neuralarc.model.StopLossType;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyEventType;

import java.math.BigDecimal;

/**
 * Applies and announces the repair for a stop loss that could never have been valid downside
 * protection. Kept out of {@link StrategyEngine} so the engine stays within its size budget and
 * the side effects (persist, audit transition, operator notification) live in one place.
 */
final class StopLossAutoCorrector {
    /** Surfaces automatic safety corrections to the UI so the operator is never silently overridden. */
    public interface AutoCorrectionListener {
        AutoCorrectionListener NOOP = (strategyId, symbol, message) -> { };

        void onAutoCorrection(String strategyId, String symbol, String message);
    }

    private final StrategyRepository strategyRepository;
    private final StrategyStateMachine stateMachine;
    private AutoCorrectionListener listener = AutoCorrectionListener.NOOP;

    StopLossAutoCorrector(StrategyRepository strategyRepository, StrategyStateMachine stateMachine) {
        this.strategyRepository = strategyRepository;
        this.stateMachine = stateMachine;
    }

    void setListener(AutoCorrectionListener listener) {
        this.listener = listener == null ? AutoCorrectionListener.NOOP : listener;
    }

    /**
     * Moves the stop to a real downside level and reports it. Selling on the misconfigured stop
     * would have liquidated the position at an unintended price.
     *
     * @return audit detail describing the correction, for the rule log.
     */
    String correct(Strategy strategy, BigDecimal previousStop, BigDecimal latestPrice) {
        BigDecimal correctedStop = StopLossSanityGuard.correctedStopPrice(latestPrice);
        strategy.setStopLossType(StopLossType.FIXED_PRICE);
        strategy.setStopLossPrice(correctedStop);
        strategyRepository.save(strategy);

        String detail = "stop $" + previousStop.toPlainString()
                + " was at/above both current $" + latestPrice.toPlainString()
                + " and average cost; auto-corrected to $" + correctedStop.toPlainString()
                + " (10% below current)";
        stateMachine.transition(strategy, strategy.currentState(), StrategyEventType.ORDER_STATUS_UPDATED,
                "Stop loss auto-corrected: " + detail, "{}");
        listener.onAutoCorrection(strategy.id(), strategy.symbol(),
                "Stop loss for " + strategy.symbol() + " was $" + previousStop.toPlainString()
                        + ", at or above the current price of $" + latestPrice.toPlainString()
                        + ". Auto-corrected to $" + correctedStop.toPlainString()
                        + " (10% below current) to restore downside protection.");
        return detail;
    }
}
