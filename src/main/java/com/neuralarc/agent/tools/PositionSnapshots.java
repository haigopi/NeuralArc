package com.neuralarc.agent.tools;

import com.neuralarc.model.StrategyMode;

import java.math.BigDecimal;
import java.util.List;

/**
 * The open positions an agent may read, taken from the snapshots the UI already holds.
 *
 * <p>An interface rather than a repository or a broker client on purpose: the app refreshes positions
 * on its own schedule, and an agent must not be able to trigger a broker sweep by asking a question.
 * The implementation hands over the cached view, copied.
 */
public interface PositionSnapshots {
    record PositionView(
            String symbol,
            StrategyMode mode,
            String workspaceName,
            String status,
            int shares,
            BigDecimal averageCost,
            BigDecimal lastPrice,
            BigDecimal unrealizedPnl
    ) {
    }

    List<PositionView> current();
}
