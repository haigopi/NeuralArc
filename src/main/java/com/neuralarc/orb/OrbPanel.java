package com.neuralarc.orb;

import javax.swing.*;
import java.awt.*;

public final class OrbPanel extends JPanel {
    public static final String DESCRIPTION_TITLE = "Description:";
    public static final String DESCRIPTION = "Capture the market's opening range from live Alpaca data, rank breakout candidates, and add the best ORB setups to this strategy grid for review.";
    public static final String EXAMPLE_TITLE = "Example:";
    public static final String EXAMPLE = "A candidate stock builds a 15-minute range from 9:30–9:45 AM ET, holds strong relative volume, and plans a long entry just above the range high with a stop near the range low.";
    public static final String FIELD_GUIDANCE = "Know before you make a next move:\n"
            + "Opening Range is the first 5, 15, or 30 minutes after the regular 9:30 AM ET open.\n"
            + "ORB Engine uses live Alpaca screener and intraday bar data; it does not ship demo tickers or canned prices.\n"
            + "Manual symbols override auto-discovery. Leave symbols blank to load live candidates from Alpaca movers and most-actives.\n"
            + "AI news analysis runs when an AI provider is configured and adds current catalyst context to each ORB recommendation.\n"
            + "Analyze creates strategy rows only; arming/execution continues through the normal NeuralArc strategy engine path.";
    public static final String EMPTY_STATE_TEXT = DESCRIPTION_TITLE + "\n" + DESCRIPTION + "\n" + EXAMPLE_TITLE + "\n" + EXAMPLE + "\n" + FIELD_GUIDANCE;
    public static final String ANALYZE_BUTTON_TEXT = "Analyze ORB Stocks";
    private final JButton analyzeButton = new JButton(ANALYZE_BUTTON_TEXT);

    public OrbPanel(Runnable onAnalyze) {
        super(new GridBagLayout());
        setOpaque(false);
        JPanel card = new JPanel();
        card.setOpaque(false);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        JLabel text = new JLabel("<html><div style='text-align:left; width:440px;'>"
                + "<div style='font-size:9px; color:#ffffff;'><b>" + DESCRIPTION_TITLE + "</b></div>"
                + "<div style='margin-top:4px; text-align:left; font-size:9px; color:#ffccff;'>" + DESCRIPTION + "</div>"
                + "<br><div style='font-size:9px; color:#ffffff;'><b>" + EXAMPLE_TITLE + "</b></div>"
                + "<div style='margin-top:4px; text-align:left; font-size:9px; color:#ffccff;'>" + EXAMPLE + "</div>"
                + "<br><div style='text-align:left; font-size:9px; color:#ffffff;'>"
                + "<b style='text-align:left; font-size:9px; color:#ffffff;'>Know before you make a next move:</b>"
                + "<ul style='margin-top:4px; padding-left:14px; font-size:9px; color:#ffccff;'>"
                + "<li><b>Opening Range:</b> first 5, 15, or 30 minutes after the regular 9:30 AM ET open.</li>"
                + "<li><b>Live data only:</b> candidates come from Alpaca movers / most-actives or your manual symbols.</li>"
                + "<li><b>AI catalyst context:</b> when configured, NeuralArc asks the AI provider to summarize fresh news context.</li>"
                + "<li><b>Normal execution path:</b> Analyze creates strategy rows; broker orders still go through the strategy engine.</li>"
                + "</ul></div></div></html>");
        text.setAlignmentX(Component.CENTER_ALIGNMENT);
        analyzeButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        analyzeButton.addActionListener(e -> { if (onAnalyze != null) onAnalyze.run(); });
        card.add(text);
        card.add(Box.createVerticalStrut(12));
        card.add(analyzeButton);
        add(card, new GridBagConstraints());
    }

    public JButton analyzeButton() { return analyzeButton; }
}
