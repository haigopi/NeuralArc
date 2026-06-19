package com.neuralarc.swing;

import javax.swing.*;
import java.awt.*;

public final class SwingPanel extends JPanel {
    public static final String DESCRIPTION_TITLE = "Description:";
    public static final String DESCRIPTION = "Scan strong, up-trending stocks that have pulled back to a rising moving-average support zone on the daily chart, rank the best multi-day swing setups on live data, and add the best candidates to this strategy grid for review.";
    public static final String EXAMPLE_TITLE = "Example:";
    public static final String EXAMPLE = "A leader stacked above its rising 20-, 50-, and 200-day moving averages pulls back 6% from a recent high to retest the 50-day line; the strategy plans a swing entry expecting a multi-day recovery back toward that prior high.";
    public static final String FIELD_GUIDANCE = "Know before you make a next move:\n"
            + "Swing Vault holds across multiple sessions, so it works on daily bars rather than intraday ticks.\n"
            + "Pullback % measures how far price has retraced from its recent swing high; the strategy buys a controlled dip, not a breakdown.\n"
            + "Minimum / Maximum Pullback % bound the dip so shallow noise and trend-breaking drops are both filtered out.\n"
            + "Trend Filter confirms the daily uptrend is intact by requiring price above the 50-day (and optionally the 200-day) moving average, or the full stack aligned.\n"
            + "Target Price aims back toward the recent high; Stop Loss % sits below support, and Reward/Risk shows the trade-off before you commit.";
    public static final String EMPTY_STATE_TEXT = DESCRIPTION_TITLE + "\n" + DESCRIPTION + "\n" + EXAMPLE_TITLE + "\n" + EXAMPLE + "\n" + FIELD_GUIDANCE;
    public static final String ANALYZE_BUTTON_TEXT = "Analyze Swing Stocks";
    private final JButton analyzeButton = new JButton(ANALYZE_BUTTON_TEXT);

    public SwingPanel(Runnable onAnalyze) {
        this(onAnalyze, true);
    }

    public SwingPanel(Runnable onAnalyze, boolean showButton) {
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
                + "<li><b>Multi-day hold:</b> Swing Vault holds across sessions, so it works on daily bars rather than intraday ticks.</li>"
                + "<li><b>Pullback %:</b> how far price has retraced from its recent swing high; the strategy buys a controlled dip, not a breakdown.</li>"
                + "<li><b>Minimum / Maximum Pullback %:</b> bound the dip so shallow noise and trend-breaking drops are both filtered out.</li>"
                + "<li><b>Trend Filter:</b> confirms the daily uptrend is intact by requiring price above its 50-day (and optionally 200-day) moving average, or the full stack aligned.</li>"
                + "<li><b>Target &amp; Stop:</b> the target aims back toward the recent high, the stop sits below support, and Reward/Risk shows the trade-off before you commit.</li>"
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
