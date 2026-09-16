package com.neuralarc.ui;

import com.neuralarc.analytics.LossHarvesting;
import com.neuralarc.analytics.RiskAnalytics;
import com.neuralarc.service.ReconciliationService;
import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import java.awt.Component;
import java.awt.Container;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Risk Dashboard's Loss Harvesting section; the harvesting maths itself is covered separately. */
class RiskDashboardHarvestingSectionTest {
    @Test
    void showsWhatToBookTheWashSaleTrapAndThatItIsNotTaxAdvice() {
        LossHarvesting.Report harvesting = LossHarvesting.analyze(
                List.of(new LossHarvesting.Position("ORCL", "Growth", 200, new BigDecimal("-5000"), -12.5, 45, true)),
                new BigDecimal("2000"), LocalDate.of(2026, 11, 15));

        String text = String.join(" ", labelTexts(panel(harvesting)));

        assertTrue(text.contains("Loss Harvesting"), "the section is titled");
        assertTrue(text.contains("ORCL"), text);
        assertTrue(text.contains("Sell 200 shares to book $5,000.00"), text);
        assertTrue(text.contains("cancels all $2,000.00"), text);
        assertTrue(text.contains("buys back automatically"), "the wash-sale trap is flagged on the row");
        assertTrue(text.contains("not tax advice"), text);
    }

    @Test
    void showsTheSellByDateAndWhenTheSymbolMayBeBoughtAgain() {
        LossHarvesting.Report harvesting = LossHarvesting.analyze(
                List.of(new LossHarvesting.Position("ORCL", "Growth", 200, new BigDecimal("-5000"), -12.5, 45, true)),
                BigDecimal.ZERO, LocalDate.of(2026, 11, 15));

        String text = String.join(" ", labelTexts(panel(harvesting)));

        assertTrue(text.contains("Sell by") && text.contains("Thu 31 Dec 2026"), text);
        assertTrue(text.contains("do not buy the same symbol back before") && text.contains("Sun 31 Jan 2027"), text);
    }

    @Test
    void everyFigureIsGivenAColourRatherThanInheritingTheDarkTheme() {
        LossHarvesting.Report harvesting = LossHarvesting.analyze(
                List.of(new LossHarvesting.Position("ORCL", "Growth", 200, new BigDecimal("-5000"), -12.5, 45, false)),
                BigDecimal.ZERO, LocalDate.of(2026, 11, 15));

        String text = String.join(" ", labelTexts(panel(harvesting)));

        // The card is white while the app runs dark, so the symbol and term cells must carry their own colour.
        assertTrue(text.contains("<font color='#212b3a'><b>ORCL</b></font>"), text);
        assertTrue(text.contains("<font color='#6c7684'>short-term</font>"), text);
        assertTrue(text.contains("bgcolor='#FBF0D2'"), "the summary sits in a highlighted band");
    }

    @Test
    void withNothingLosingTheSectionSaysSoRatherThanSittingEmpty() {
        LossHarvesting.Report harvesting = LossHarvesting.analyze(List.of(), BigDecimal.ZERO, LocalDate.of(2026, 11, 15));

        String text = String.join(" ", labelTexts(panel(harvesting)));

        assertTrue(text.contains("nothing to harvest"), text);
    }

    private static RiskDashboardPanel panel(LossHarvesting.Report harvesting) {
        return new RiskDashboardPanel("Paper", RiskAnalytics.analyze(List.of()), RiskAnalytics.classify(List.of()),
                new ReconciliationService().reconcile(List.of(), List.of()), harvesting);
    }

    private static List<String> labelTexts(Container root) {
        List<String> collected = new ArrayList<>();
        for (Component child : root.getComponents()) {
            if (child instanceof JLabel label && label.getText() != null) {
                collected.add(label.getText());
            }
            if (child instanceof Container nested) {
                collected.addAll(labelTexts(nested));
            }
        }
        return collected;
    }
}
