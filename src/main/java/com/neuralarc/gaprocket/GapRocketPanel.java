package com.neuralarc.gaprocket;

import javax.swing.*;
import java.awt.*;

public final class GapRocketPanel extends JPanel {
    public static final String DESCRIPTION = "Scan premarket gap-up stocks, rank the strongest movers, and add the best candidates to this strategy grid for review.";
    public static final String EXAMPLE = "Example: NVDA is up 7% premarket after earnings, trades 5M shares before the open, holds 6x relative volume, and retests the opening-range high before becoming Ready to Buy.";
    public static final String EMPTY_STATE_TEXT = DESCRIPTION + "\n" + EXAMPLE;
    public static final String ANALYZE_BUTTON_TEXT = "Analyze Gap Stocks";
    private final JButton analyzeButton = new JButton(ANALYZE_BUTTON_TEXT);

    public GapRocketPanel(Runnable onAnalyze) {
        super(new GridBagLayout());
        setOpaque(false);
        JPanel card = new JPanel();
        card.setOpaque(false);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        JLabel text = new JLabel("<html><div style='text-align:center; width:420px;'>"
                + DESCRIPTION
                + "<br><br>"
                + EXAMPLE
                + "</div></html>");
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
