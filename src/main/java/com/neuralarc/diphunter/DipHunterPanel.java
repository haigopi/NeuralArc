package com.neuralarc.diphunter;

import javax.swing.*;
import java.awt.*;

public final class DipHunterPanel extends JPanel {
    public static final String DESCRIPTION_TITLE = "Description:";
    public static final String DESCRIPTION = "Scan strong, up-trending stocks that have pulled back from a recent high, rank the best bounce setups on live data, and add the best candidates to this strategy grid for review.";
    public static final String EXAMPLE_TITLE = "Example:";
    public static final String EXAMPLE = "A leading stock trading above its 20- and 50-day moving averages dips 6% off last week's high on light selling, then reverses 1% off the intraday low on rising volume before becoming Ready to Buy.";
    public static final String FIELD_GUIDANCE = "Know before you make a next move:\n"
            + "Pullback % measures how far price has dropped from its recent (about 20-day) high; the strategy wants a healthy dip, not a crash.\n"
            + "Minimum / Maximum Pullback % bound the dip so very shallow pullbacks and deep falling knives are both filtered out.\n"
            + "Trend Filter confirms the name is still strong by requiring price above its 20-day, 50-day, or either moving average.\n"
            + "Bounce Confirmation chooses how the dip must show life: an intraday reversal off the low, holding near support, or manual review.\n"
            + "Relative Volume requires today's activity to be meaningfully above normal so the bounce has participation.";
    public static final String EMPTY_STATE_TEXT = DESCRIPTION_TITLE + "\n" + DESCRIPTION + "\n" + EXAMPLE_TITLE + "\n" + EXAMPLE + "\n" + FIELD_GUIDANCE;
    public static final String ANALYZE_BUTTON_TEXT = "Analyze Dip Stocks";
    private final JButton analyzeButton = new JButton(ANALYZE_BUTTON_TEXT);

    public DipHunterPanel(Runnable onAnalyze) {
        this(onAnalyze, true);
    }

    public DipHunterPanel(Runnable onAnalyze, boolean showButton) {
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
                + "<li><b>Pullback %:</b> how far price has dropped from its recent (~20-day) high; the strategy wants a healthy dip, not a crash.</li>"
                + "<li><b>Minimum / Maximum Pullback %:</b> bound the dip so very shallow pullbacks and deep falling knives are both filtered out.</li>"
                + "<li><b>Trend Filter:</b> confirms the name is still strong by requiring price above its 20-day, 50-day, or either moving average.</li>"
                + "<li><b>Bounce Confirmation:</b> how the dip must show life — an intraday reversal off the low, holding near support, or manual review.</li>"
                + "<li><b>Relative Volume:</b> requires today's activity to be meaningfully above normal so the bounce has participation.</li>"
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
