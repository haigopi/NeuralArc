package com.neuralarc.ui;

import com.neuralarc.analytics.LossRecoveryPlan;
import com.neuralarc.model.MarketBar;
import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import java.awt.Component;
import java.awt.Container;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LossRecoveryPanelTest {
    @Test
    void spellsOutTheAddTheNewAverageAndTheExit() {
        LossRecoveryPlan.Plan plan = plan(new BigDecimal("110"));
        LossRecoveryPanel panel = new LossRecoveryPanel("ORCL", 100, new BigDecimal("110"),
                new BigDecimal("100"), plan);

        String text = String.join(" ", texts(panel));

        assertTrue(text.contains("Buy <b>" + plan.addShares() + "</b> more shares"), text);
        assertTrue(text.contains("Average cost falls from $110.00"), text);
        assertTrue(text.contains("$99.75"), "the add is priced under the market");
        assertTrue(text.contains("Sell at <b>$105.00</b>"), text);
        assertTrue(panel.executeButton().isEnabled());
    }

    @Test
    void showsWhyTheExitPriceIsRealistic() {
        LossRecoveryPanel panel = new LossRecoveryPanel("ORCL", 100, new BigDecimal("110"),
                new BigDecimal("100"), plan(new BigDecimal("110")));

        String text = String.join(" ", texts(panel));

        assertTrue(text.contains("Why that exit price"), text);
        assertTrue(text.contains("middle of the last 21 sessions"), text);
        assertTrue(text.contains("reached it on"), text);
    }

    @Test
    void saysWhatThePlanRisksBeforeItIsRun() {
        LossRecoveryPanel panel = new LossRecoveryPanel("ORCL", 100, new BigDecimal("110"),
                new BigDecimal("100"), plan(new BigDecimal("110")));

        String text = String.join(" ", texts(panel));

        assertTrue(text.contains("Before you act"), text);
        assertTrue(text.contains("Averaging down commits"), text);
        assertTrue(text.contains("No order is sent until you submit it there"), text);
    }

    @Test
    void theButtonOpensAnOrderTicketRatherThanPlacingTheBuyOutright() {
        LossRecoveryPanel panel = new LossRecoveryPanel("ORCL", 100, new BigDecimal("110"),
                new BigDecimal("100"), plan(new BigDecimal("110")));

        String text = String.join(" ", texts(panel));

        assertEquals("Review and Execute", panel.executeButton().getText());
        assertTrue(text.contains("opens an order ticket"), text);
        assertTrue(text.contains("today's low"), text);
        assertTrue(text.contains("nothing is sold"), text);
    }

    @Test
    void anImpossiblePlanExplainsItselfAndCannotBeExecuted() {
        LossRecoveryPlan.Plan plan = LossRecoveryPlan.forPosition(100, new BigDecimal("110"),
                new BigDecimal("100"), month("98"), BigDecimal.ZERO);
        LossRecoveryPanel panel = new LossRecoveryPanel("ORCL", 100, new BigDecimal("110"),
                new BigDecimal("100"), plan);

        String text = String.join(" ", texts(panel));

        assertTrue(text.contains("No plan for this position."), text);
        assertTrue(text.contains("not traded above"), text);
        assertFalse(panel.executeButton().isEnabled());
    }

    private static LossRecoveryPlan.Plan plan(BigDecimal averageCost) {
        return LossRecoveryPlan.forPosition(100, averageCost, new BigDecimal("100"), month("105"), BigDecimal.ZERO);
    }

    private static List<MarketBar> month(String medianHigh) {
        BigDecimal median = new BigDecimal(medianHigh);
        List<MarketBar> bars = new ArrayList<>();
        for (int session = -10; session <= 10; session++) {
            BigDecimal high = median.add(BigDecimal.valueOf(session).multiply(new BigDecimal("0.10")));
            bars.add(new MarketBar("ORCL", "2026-09-" + String.format("%02d", session + 11),
                    high.subtract(new BigDecimal("1.00")), high, high.subtract(new BigDecimal("6.00")),
                    high.subtract(new BigDecimal("0.50")), new BigDecimal("1000000")));
        }
        return bars;
    }

    private static List<String> texts(Container root) {
        List<String> collected = new ArrayList<>();
        for (Component child : root.getComponents()) {
            if (child instanceof JLabel label && label.getText() != null) {
                collected.add(label.getText());
            }
            if (child instanceof Container nested) {
                collected.addAll(texts(nested));
            }
        }
        return collected;
    }
}
