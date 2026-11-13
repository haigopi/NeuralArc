package com.neuralarc.service;

import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyOrder;
import com.neuralarc.model.StrategyOrderSide;
import com.neuralarc.model.StrategyOrderStatus;
import com.neuralarc.model.StrategyOrderType;
import com.neuralarc.model.StrategyStage;
import com.neuralarc.model.StrategyStatus;
import com.neuralarc.model.StopLossType;
import com.neuralarc.model.ProfitHoldType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LossBuyLevelRearmTest {

    private static Strategy strategy(StrategyLifecycleState state) {
        Strategy strategy = new Strategy(
                UUID.randomUUID().toString(), "s", "GOOG", StrategyMode.PAPER, StrategyStatus.ACTIVE, state,
                new BigDecimal("300.00"), 10,
                new BigDecimal("280.00"), 8,
                new BigDecimal("260.00"), 10,
                false, StopLossType.FIXED_PRICE, BigDecimal.ZERO, BigDecimal.ZERO,
                false, BigDecimal.ZERO,
                false, BigDecimal.ZERO, BigDecimal.ZERO, false,
                false, ProfitHoldType.PERCENT_TRAILING, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO,
                false, 25, new BigDecimal("300.00"), 2, Instant.now(), Instant.now());
        strategy.setLossBuyLevelsEnabled(true);
        return strategy;
    }

    private static StrategyOrder filled(Strategy strategy, StrategyStage stage, int qty, String price) {
        return new StrategyOrder(UUID.randomUUID().toString(), strategy.id(), stage, "o-" + stage, "c-" + stage,
                "GOOG", StrategyOrderSide.BUY, StrategyOrderType.LIMIT, new BigDecimal(price), BigDecimal.ZERO,
                BigDecimal.valueOf(qty), BigDecimal.valueOf(qty), new BigDecimal(price), StrategyOrderStatus.FILLED,
                Instant.now(), Instant.now(), null, "{}");
    }

    private static List<StrategyOrder> allFilled(Strategy strategy) {
        return List.of(filled(strategy, StrategyStage.BASE_BUY, 10, "300.00"),
                filled(strategy, StrategyStage.BUY_LIMIT_1, 8, "280.00"),
                filled(strategy, StrategyStage.BUY_LIMIT_2, 10, "260.00"));
    }

    @Test
    void unchangedLevelsKeepTheFilledState() {
        Strategy strategy = strategy(StrategyLifecycleState.BUY_LIMIT_2_FILLED);
        assertEquals(Optional.empty(), LossBuyLevelRearm.stateAfterEdit(strategy, allFilled(strategy)));
    }

    @Test
    void editingBothFilledLevelsWaitsOnTheFirstLevelAgain() {
        Strategy strategy = strategy(StrategyLifecycleState.BUY_LIMIT_2_FILLED);
        List<StrategyOrder> orders = allFilled(strategy);
        strategy.setBuyLimit1Price(new BigDecimal("269.00"));
        strategy.setBuyLimit2Price(new BigDecimal("249.00"));

        assertEquals(Optional.of(StrategyLifecycleState.BASE_BUY_FILLED),
                LossBuyLevelRearm.stateAfterEdit(strategy, orders));
    }

    @Test
    void editingOnlyTheSecondFilledLevelWaitsOnTheSecondLevel() {
        Strategy strategy = strategy(StrategyLifecycleState.BUY_LIMIT_2_FILLED);
        List<StrategyOrder> orders = allFilled(strategy);
        strategy.setBuyLimit2Price(new BigDecimal("249.00"));

        assertEquals(Optional.of(StrategyLifecycleState.BUY_LIMIT_1_FILLED),
                LossBuyLevelRearm.stateAfterEdit(strategy, orders));
    }

    @Test
    void aChangedQuantityRearmsALevelToo() {
        Strategy strategy = strategy(StrategyLifecycleState.BUY_LIMIT_2_FILLED);
        List<StrategyOrder> orders = allFilled(strategy);
        strategy.setBuyLimit2Quantity(12);

        assertEquals(Optional.of(StrategyLifecycleState.BUY_LIMIT_1_FILLED),
                LossBuyLevelRearm.stateAfterEdit(strategy, orders));
    }

    @Test
    void disabledLevelsOrOtherStatesAreLeftAlone() {
        Strategy disabled = strategy(StrategyLifecycleState.BUY_LIMIT_2_FILLED);
        List<StrategyOrder> orders = allFilled(disabled);
        disabled.setBuyLimit1Price(new BigDecimal("269.00"));
        disabled.setLossBuyLevelsEnabled(false);
        assertEquals(Optional.empty(), LossBuyLevelRearm.stateAfterEdit(disabled, orders));

        Strategy selling = strategy(StrategyLifecycleState.SELL_PLACED);
        selling.setBuyLimit1Price(new BigDecimal("269.00"));
        assertEquals(Optional.empty(), LossBuyLevelRearm.stateAfterEdit(selling, allFilled(selling)));
    }

    @Test
    void aFilledLevelOnlyCountsAtItsCurrentValues() {
        Strategy strategy = strategy(StrategyLifecycleState.BUY_LIMIT_2_FILLED);
        List<StrategyOrder> orders = allFilled(strategy);
        assertTrue(StrategyStageSupport.isLossLevelFilled(strategy, orders, StrategyStage.BUY_LIMIT_1));
        strategy.setBuyLimit1Price(new BigDecimal("269.00"));
        assertFalse(StrategyStageSupport.isLossLevelFilled(strategy, orders, StrategyStage.BUY_LIMIT_1));
        assertTrue(StrategyStageSupport.isLossLevelFilled(strategy, orders, StrategyStage.BUY_LIMIT_2));
    }
}
