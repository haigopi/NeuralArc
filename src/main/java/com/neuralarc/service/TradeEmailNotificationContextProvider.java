package com.neuralarc.service;

import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyOrder;

interface TradeEmailNotificationContextProvider {
    TradeEmailNotificationContext contextFor(Strategy strategy, StrategyOrder order);

    static TradeEmailNotificationContextProvider empty() {
        return (strategy, order) -> TradeEmailNotificationContext.singleOrder(order);
    }
}
