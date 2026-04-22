package com.neuralarc.rules;

import com.neuralarc.model.RuleType;
import com.neuralarc.model.StrategyConfig;
import com.neuralarc.model.StrategyState;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class RuleEvaluationService {
    public List<RuleType> evaluate(BigDecimal price, int shares, StrategyConfig config, StrategyState state) {
        List<RuleType> triggered = new ArrayList<>();

        if (shares == 0 && price.compareTo(config.baseBuyPrice()) <= 0 && !state.isTriggered(RuleType.BUY_RULE)) {
            triggered.add(RuleType.BUY_RULE);
        }

        if (shares > 0 && price.compareTo(config.stopActivationPrice()) >= 0) {
            state.setStopActivated(true);
        }

        if (shares > 0 && state.isStopActivated() && price.compareTo(config.stopActivationPrice()) < 0
                && !state.isTriggered(RuleType.STOP_LOSS_RULE)) {
            triggered.add(RuleType.STOP_LOSS_RULE);
        }

        if (shares > 0 && price.compareTo(config.sellTriggerPrice()) >= 0 && !state.isTriggered(RuleType.SELL_RULE)) {
            triggered.add(RuleType.SELL_RULE);
        }

        if (shares > 0 && price.compareTo(config.lossBuyLevel1Price()) <= 0 && !state.isTriggered(RuleType.LOSS_BUY_RULE)) {
            triggered.add(RuleType.LOSS_BUY_RULE);
        }

        if (shares > 0 && price.compareTo(config.lossBuyLevel2Price()) <= 0
                && !state.isTriggered(RuleType.LOSS_INVESTMENT_BUY_RULE)) {
            triggered.add(RuleType.LOSS_INVESTMENT_BUY_RULE);
        }

        return triggered;
    }
}
