package com.neuralarc.rules;

import com.neuralarc.model.RuleType;
import com.neuralarc.model.StrategyConfig;
import com.neuralarc.model.StrategyState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RuleEvaluationServiceTest {
    private final StrategyConfig config = new StrategyConfig("NEO", new BigDecimal("8.00"), 10,
            new BigDecimal("9.00"), new BigDecimal("10.00"), new BigDecimal("7.00"), 5,
            new BigDecimal("6.00"), 5, 2, true, true);

    private final StrategyConfig configWithoutProfitHold = new StrategyConfig("NEO", new BigDecimal("8.00"), 10,
            new BigDecimal("9.00"), new BigDecimal("10.00"), new BigDecimal("7.00"), 5,
            new BigDecimal("6.00"), 5, 2, true, false);

    @Test
    void buyRuleTriggersAtOrBelowBase() {
        RuleEvaluationService service = new RuleEvaluationService();
        StrategyState state = new StrategyState();
        List<RuleType> rules = service.evaluate(new BigDecimal("8.00"), 0, config, state);
        assertTrue(rules.contains(RuleType.BUY_RULE));
    }

    @Test
    void duplicateRulePreventedByState() {
        RuleEvaluationService service = new RuleEvaluationService();
        StrategyState state = new StrategyState();
        assertTrue(state.markTriggered(RuleType.BUY_RULE));
        List<RuleType> rules = service.evaluate(new BigDecimal("8.00"), 0, config, state);
        assertFalse(rules.contains(RuleType.BUY_RULE));
    }

    @Test
    void sellRuleTriggersAtSellTriggerWhenWithinProfitWindow() {
        RuleEvaluationService service = new RuleEvaluationService();
        StrategyState state = new StrategyState();
        List<RuleType> rules = service.evaluate(new BigDecimal("10.00"), 10, config, state);
        assertTrue(rules.contains(RuleType.SELL_RULE));
    }

    @Test
    void sellRuleDoesNotTriggerWhenPriceIsTenPercentAboveSellTrigger() {
        RuleEvaluationService service = new RuleEvaluationService();
        StrategyState state = new StrategyState();
        List<RuleType> rules = service.evaluate(new BigDecimal("11.00"), 10, config, state);
        assertFalse(rules.contains(RuleType.SELL_RULE));
    }

    @Test
    void sellRuleTriggersWhenPriceIsTenPercentAboveSellTriggerIfProfitHoldDisabled() {
        RuleEvaluationService service = new RuleEvaluationService();
        StrategyState state = new StrategyState();
        List<RuleType> rules = service.evaluate(new BigDecimal("11.00"), 10, configWithoutProfitHold, state);
        assertTrue(rules.contains(RuleType.SELL_RULE));
    }
}
