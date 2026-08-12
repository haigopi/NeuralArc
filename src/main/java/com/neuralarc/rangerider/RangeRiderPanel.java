package com.neuralarc.rangerider;

import javax.swing.*;
import java.awt.*;

public final class RangeRiderPanel extends JPanel {
    public static final String DESCRIPTION_TITLE = "Description:";
    public static final String DESCRIPTION = "Analyze the last three weeks of completed daily bars for actively traded stocks, average each session's open, high, and low, then plan a same-day income trade: buy the dip the stock typically makes below its open and sell the rally it typically makes above it, before the close.";
    public static final String EXAMPLE_TITLE = "Example:";
    public static final String EXAMPLE = "Over the last 15 sessions a stock averaged a $99.10 open, a $98.40 low, and a $101.60 high — a typical dip of 0.7% and a typical rally of 2.5%. Aiming for half of each, the strategy anchors to the latest close of $103.00 and plans a buy at $102.64 and a sell at $104.29, and reports that both were reached on the same day in 10 of those 15 sessions.";
    public static final String FIELD_GUIDANCE = "Know before you make a next move:\n"
            + "Average Low / High / Open are the means of every completed session's low, high, and open across the lookback window. Today's unfinished session is never included, so the same scan gives the same answer before the open or mid-afternoon.\n"
            + "Typical Dip % and Typical Rally % restate those averages as distances from the open. An absolute price from three weeks ago goes stale the moment a stock drifts; these percentages travel with it, so the plan is priced off the latest completed close.\n"
            + "Range Capture % is how much of that typical dip and rally the plan aims to take. Chasing the entire move only pays on unusually wide, perfectly timed days; taking half of it earns less per trade but completes far more often.\n"
            + "Range Stability % is how consistently the daily range repeats session to session — a steady range is what makes a daily plan worth placing.\n"
            + "Entry Touch Rate % is how often the stock actually traded down to the planned buy.\n"
            + "Same-Day Fill Rate % is how often the planned buy and sell were both reached in the same session. Daily bars cannot prove the low came before the high, so treat this as an optimistic upper bound, not a backtested return.";
    public static final String EMPTY_STATE_TEXT = DESCRIPTION_TITLE + "\n" + DESCRIPTION + "\n" + EXAMPLE_TITLE + "\n" + EXAMPLE + "\n" + FIELD_GUIDANCE;
    public static final String ANALYZE_BUTTON_TEXT = "Analyze Range Rider Stocks";
    private final JButton analyzeButton = new JButton(ANALYZE_BUTTON_TEXT);

    public RangeRiderPanel(Runnable onAnalyze) {
        this(onAnalyze, true);
    }

    public RangeRiderPanel(Runnable onAnalyze, boolean showButton) {
        super(new GridBagLayout());
        setOpaque(false);
        JPanel card = new JPanel();
        card.setOpaque(false);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        JLabel text = new JLabel("<html><div style='text-align:left; width:420px;'>"
                + "<div style='font-size:9px; color:#ffffff;'><b>" + DESCRIPTION_TITLE + "</b></div>"
                + "<div style='margin-top:4px; text-align:left; font-size:9px; color:#ffccff;'>" + DESCRIPTION + "</div>"
                + "<br><div style='font-size:9px; color:#ffffff;'><b>" + EXAMPLE_TITLE + "</b></div>"
                + "<div style='margin-top:4px; text-align:left; font-size:9px; color:#ffccff;'>" + EXAMPLE + "</div>"
                + "<br><div style='text-align:left; font-size:9px; color:#ffffff;'>"
                + "<b style='text-align:left; font-size:9px; color:#ffffff;'>Know before you make a next move:</b>"
                + "<ul style='margin-top:4px; padding-left:14px; font-size:9px; color:#ffccff; '>"
                + "<li><b>Average Low / High / Open:</b> the means of every completed session's low, high, and open across the lookback window. Today's unfinished session is never included.</li>"
                + "<li><b>Typical Dip % / Typical Rally %:</b> those averages restated as distances from the open, so the plan can be priced off the latest completed close instead of a stale three-week-old level.</li>"
                + "<li><b>Range Capture %:</b> how much of that typical dip and rally the plan aims to take. Chasing the whole move only pays on unusually wide, perfectly timed days; taking half of it earns less per trade but completes far more often.</li>"
                + "<li><b>Range Stability %:</b> how consistently the daily range repeats session to session — a steady range is what makes a daily plan worth placing.</li>"
                + "<li><b>Entry Touch Rate %:</b> how often the stock actually traded down to the planned buy.</li>"
                + "<li><b>Same-Day Fill Rate %:</b> how often the planned buy and sell were both reached in the same session. Daily bars cannot prove the low came before the high, so treat this as an optimistic upper bound, not a backtested return.</li>"
                + "</ul></div></div></html>");
        text.setAlignmentX(Component.CENTER_ALIGNMENT);
        analyzeButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        analyzeButton.addActionListener(e -> { if (onAnalyze != null) onAnalyze.run(); });
        card.add(text);
        if (showButton) {
            card.add(Box.createVerticalStrut(12));
            card.add(analyzeButton);
        }
        add(card, new GridBagConstraints());
    }

    public JButton analyzeButton() { return analyzeButton; }
}
