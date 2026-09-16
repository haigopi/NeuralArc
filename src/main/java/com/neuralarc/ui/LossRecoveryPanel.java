package com.neuralarc.ui;

import com.neuralarc.analytics.LossRecoveryPlan;
import com.neuralarc.util.FontLoader;
import com.neuralarc.util.Monetary;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.Component;
import java.awt.Font;
import java.math.BigDecimal;
import java.util.Locale;

/**
 * The body of the Minimize Loss Impact window: what the position looks like now, the plan drawn from
 * the last month of daily bars, the evidence behind its exit price, and what the plan risks. A panel
 * rather than a dialog so the wording and the button state can be tested without a screen.
 */
final class LossRecoveryPanel extends JPanel {
    private final JButton executeButton = new JButton("Review and Execute");
    private final LossRecoveryPlan.Plan plan;
    private final BigDecimal averageCostBefore;

    LossRecoveryPanel(String symbol, int shares, BigDecimal averageCost, BigDecimal currentPrice,
                      LossRecoveryPlan.Plan plan) {
        this.plan = plan;
        this.averageCostBefore = averageCost;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(16, 18, 14, 18));

        add(title(symbol + " — minimize loss impact"));
        add(Box.createVerticalStrut(4));
        add(muted("Held " + shares + (shares == 1 ? " share" : " shares") + " at " + money(averageCost)
                + ", market " + money(currentPrice) + ". Open loss " + money(plan.lossNow()) + "."));
        add(Box.createVerticalStrut(12));

        if (!plan.feasible()) {
            add(body("<b>No plan for this position.</b><br>" + escape(plan.reason())));
            executeButton.setEnabled(false);
            add(Box.createVerticalGlue());
            return;
        }

        add(body(planHtml()));
        add(Box.createVerticalStrut(10));
        add(body(evidenceHtml()));
        if (!plan.warnings().isEmpty()) {
            add(Box.createVerticalStrut(10));
            StringBuilder warnings = new StringBuilder("<b>Before you act</b><ul style='margin:4px 0 0 14px;'>");
            for (String warning : plan.warnings()) {
                warnings.append("<li>").append(escape(warning)).append("</li>");
            }
            add(body(warnings.append("</ul>").toString()));
        }
        add(Box.createVerticalStrut(10));
        add(muted("Review and Execute opens an order ticket with the buy above already filled in, priced at "
                + "today's low, where the quantity, limit price and time in force can still be changed. No order "
                + "is sent until you submit it there, and nothing is sold."));
        add(Box.createVerticalGlue());
    }

    JButton executeButton() {
        return executeButton;
    }

    LossRecoveryPlan.Plan plan() {
        return plan;
    }

    private String planHtml() {
        StringBuilder html = new StringBuilder("<b>The plan</b><br>");
        if (plan.addShares() > 0) {
            html.append("Buy <b>").append(plan.addShares()).append("</b> more ")
                    .append(plan.addShares() == 1 ? "share" : "shares").append(" at <b>")
                    .append(escape(money(plan.addLimitPrice()))).append("</b> or better — ")
                    .append(escape(money(plan.addCost()))).append(" of new money.<br>")
                    .append("Average cost falls from ").append(escape(money(averageCostBefore)))
                    .append(" to <b>").append(escape(money(plan.newAverageCost()))).append("</b>.<br>");
        } else {
            html.append("No extra shares are needed.<br>");
        }
        html.append("Sell at <b>").append(escape(money(plan.exitPrice()))).append("</b>, which turns today's ")
                .append(escape(money(plan.lossNow()))).append(" into <b>")
                .append(escape(signedMoney(plan.resultAtExit()))).append("</b>");
        html.append(plan.partialRecovery()
                ? " — smaller than the loss now, though not back to even."
                : " — the loss is cleared.");
        return html.toString();
    }

    private String evidenceHtml() {
        return "<b>Why that exit price</b><br>"
                + escape(money(plan.exitPrice())) + " is the middle of the last " + plan.sessionsAnalyzed()
                + " sessions' highs: the stock reached it on " + plan.sessionsAtOrAboveExit() + " of them. "
                + "It traded between " + escape(money(plan.monthLow())) + " and " + escape(money(plan.monthHigh()))
                + " over that month, so the plan asks for an ordinary move rather than a full recovery.";
    }

    private JLabel title(String text) {
        JLabel label = new JLabel(text);
        label.setFont(FontLoader.ui(Font.BOLD, 15f));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private JLabel body(String html) {
        JLabel label = new JLabel("<html><div style='width:520px;'>" + html + "</div></html>");
        label.setFont(FontLoader.ui(Font.PLAIN, 12f));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private JLabel muted(String text) {
        JLabel label = new JLabel("<html><div style='width:520px;'>" + escape(text) + "</div></html>");
        label.putClientProperty("neuralarc.mutedDescription", Boolean.TRUE);
        label.setFont(FontLoader.ui(Font.PLAIN, 11f));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private static String money(BigDecimal value) {
        BigDecimal rounded = Monetary.round(value == null ? BigDecimal.ZERO : value);
        return (rounded.signum() < 0 ? "-$" : "$") + String.format(Locale.US, "%,.2f", rounded.abs());
    }

    private static String signedMoney(BigDecimal value) {
        BigDecimal rounded = Monetary.round(value == null ? BigDecimal.ZERO : value);
        return rounded.signum() > 0 ? "+" + money(rounded) : money(rounded);
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
