package com.neuralarc.rangerider;

import javax.swing.*;
import java.awt.*;

public final class RangeRiderPanel extends JPanel {
    public static final String DESCRIPTION_TITLE = "Description:";
    public static final String DESCRIPTION = "Analyze the last three weeks of daily bars for actively traded stocks, average each session's open, high, and low, then plan a same-day income trade: buy near the average daily low and sell near the average daily high before the close.";
    public static final String EXAMPLE_TITLE = "Example:";
    public static final String EXAMPLE = "Over the last 15 sessions a stock averaged a $98.40 low, a $99.10 open, and a $101.60 high. The strategy plans a buy at $98.65 and a sell at $101.35, and reports that both prices were reached on the same day in 12 of those 15 sessions.";
    public static final String FIELD_GUIDANCE = "Know before you make a next move:\n"
            + "Average Low / High / Open are the means of every session's low, high, and open across the lookback window — the planned buy and sell are derived from them.\n"
            + "Average Daily Range % is how far the stock travels between its low and high on a typical day; too small and the round trip cannot pay, too wide and the day is news-driven rather than repeatable.\n"
            + "Range Stability % is how consistently that range repeats session to session — a steady range is what makes a daily plan worth placing.\n"
            + "Entry Touch Rate % is how often the stock actually traded down to the planned buy price.\n"
            + "Same-Day Fill Rate % is how often the planned buy and the planned sell were both reached in the same session. Daily bars cannot prove the low came before the high, so treat this as an optimistic upper bound, not a backtested return.";
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
                + "<li><b>Average Low / High / Open:</b> the means of every session's low, high, and open across the lookback window — the planned buy and sell are derived from them.</li>"
                + "<li><b>Average Daily Range %:</b> how far the stock travels between its low and high on a typical day; too small and the round trip cannot pay, too wide and the day is news-driven rather than repeatable.</li>"
                + "<li><b>Range Stability %:</b> how consistently that range repeats session to session — a steady range is what makes a daily plan worth placing.</li>"
                + "<li><b>Entry Touch Rate %:</b> how often the stock actually traded down to the planned buy price.</li>"
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
