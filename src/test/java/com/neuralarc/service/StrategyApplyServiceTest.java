package com.neuralarc.service;

import com.neuralarc.model.MarketMode;
import com.neuralarc.model.RecommendationAction;
import com.neuralarc.model.RecommendationType;
import com.neuralarc.model.StrategyRecommendation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategyApplyServiceTest {
    @Test
    void applyToCurrentStrategyMapsRecommendationValues() {
        StrategyRecommendation recommendation = new StrategyRecommendation(
                "AAPL",
                RecommendationType.SHORT_TERM,
                new BigDecimal("95.00"),
                new BigDecimal("95.00"),
                new BigDecimal("100.00"),
                BigDecimal.ZERO,
                new BigDecimal("100.00"),
                BigDecimal.ZERO,
                new BigDecimal("95.00"),
                new BigDecimal("95.00"),
                MarketMode.ACCUMULATION,
                "",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("93.00"),
                new BigDecimal("91.00"),
                new BigDecimal("89.00"),
                new BigDecimal("106.00"),
                new BigDecimal("98.00"),
                new BigDecimal("101.00"),
                "Bullish",
                "Strong",
                new BigDecimal("2.75"),
                90,
                RecommendationAction.BUY,
                "",
                false
        );

        StrategyApplyService.AppliedStrategyValues values = new StrategyApplyService()
                .applyRecommendationToCurrentStrategy(recommendation);

        assertEquals(new BigDecimal("95.00"), values.buyRulePrice());
        assertEquals(new BigDecimal("93.00"), values.lossBuy1Price());
        assertEquals(new BigDecimal("91.00"), values.lossBuy2Price());
        assertEquals(new BigDecimal("89.00"), values.stopLossPrice());
        assertEquals(new BigDecimal("106.00"), values.sellRulePrice());
        assertTrue(values.enableLossBuyLevels());
    }

    @Test
    void longTermApplyUsesAdjustedBaseBuyPrice() {
        StrategyRecommendation recommendation = new StrategyRecommendation(
                "QCOM",
                RecommendationType.LONG_TERM,
                new BigDecimal("154.44"),
                new BigDecimal("172.61"),
                new BigDecimal("156.00"),
                new BigDecimal("156.00"),
                new BigDecimal("156.00"),
                new BigDecimal("0.0100"),
                new BigDecimal("154.44"),
                new BigDecimal("154.44"),
                MarketMode.ACCUMULATION,
                "Adjusted to current market price because calculated base was higher than latest confirmed price.",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("150.00"),
                new BigDecimal("144.00"),
                new BigDecimal("138.00"),
                new BigDecimal("190.00"),
                new BigDecimal("162.00"),
                new BigDecimal("168.00"),
                "Neutral",
                "Strong",
                new BigDecimal("2.50"),
                70,
                RecommendationAction.WATCH,
                "",
                false
        );

        StrategyApplyService.AppliedStrategyValues values = new StrategyApplyService()
                .applyRecommendationToCurrentStrategy(recommendation);

        assertEquals(new BigDecimal("154.44"), values.buyRulePrice());
    }
}
