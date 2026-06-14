package com.neuralarc.gaprocket;

import javax.swing.*;
import java.awt.*;

public final class GapRocketPanel extends JPanel {
    public static final String EMPTY_STATE_TEXT = "Gap Rocket is ready.\nAnalyze premarket movers and add recommended stocks to this strategy.";
    public static final String ANALYZE_BUTTON_TEXT = "Analyze Gap Stocks";
    private final JButton analyzeButton = new JButton(ANALYZE_BUTTON_TEXT);

    public GapRocketPanel(Runnable onAnalyze) {
        super(new GridBagLayout());
        setOpaque(false);
        JPanel card = new JPanel();
        card.setOpaque(false);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        JLabel text = new JLabel("<html><div style='text-align:center'>Gap Rocket is ready.<br>Analyze premarket movers and add recommended stocks to this strategy.<br><br>Example: NVDA gaps +7% on news with 3x relative volume, then retests the opening-range high.</div></html>");
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
