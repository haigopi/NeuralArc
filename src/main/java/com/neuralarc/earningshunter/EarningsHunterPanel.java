package com.neuralarc.earningshunter;

import javax.swing.*;
import java.awt.*;

public final class EarningsHunterPanel extends JPanel {
    public static final String DESCRIPTION_TITLE = "Description:";
    public static final String DESCRIPTION = "Scan stocks with fresh earnings-related news, confirm live price/volume response, rank the best event-driven long setups, and add candidates to this strategy grid for review.";
    public static final String EXAMPLE_TITLE = "Example:";
    public static final String EXAMPLE = "A stock reports earnings or guidance news within the last week, trades above normal volume, and holds a liquid price range; Earnings Hunter plans an entry at current price with explicit stop and target levels.";
    public static final String FIELD_GUIDANCE = "Know before you make a next move:\n"
            + "Earnings Hunter depends on live Alpaca news plus live market data; no hardcoded earnings calendar is used.\n"
            + "Earnings Window controls how recent the earnings news must be.\n"
            + "Minimum News Score filters weak or stale earnings mentions.\n"
            + "Relative Volume confirms the market is reacting, not only publishing news.\n"
            + "Target and Stop are planning levels added to the strategy row; orders are not submitted until you place pending buys.";
    public static final String EMPTY_STATE_TEXT = DESCRIPTION_TITLE + "\n" + DESCRIPTION + "\n" + EXAMPLE_TITLE + "\n" + EXAMPLE + "\n" + FIELD_GUIDANCE;
    public static final String ANALYZE_BUTTON_TEXT = "Analyze Earnings Stocks";
    private final JButton analyzeButton = new JButton(ANALYZE_BUTTON_TEXT);

    public EarningsHunterPanel(Runnable onAnalyze, boolean showButton) {
        super(new GridBagLayout());
        setOpaque(false);
        JPanel card = new JPanel();
        card.setOpaque(false);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        JLabel text = new JLabel("<html><div style='text-align:left; width:430px;'>"
                + "<div style='font-size:9px; color:#ffffff;'><b>" + DESCRIPTION_TITLE + "</b></div>"
                + "<div style='margin-top:4px; font-size:9px; color:#ffccff;'>" + DESCRIPTION + "</div>"
                + "<br><div style='font-size:9px; color:#ffffff;'><b>" + EXAMPLE_TITLE + "</b></div>"
                + "<div style='margin-top:4px; font-size:9px; color:#ffccff;'>" + EXAMPLE + "</div>"
                + "<br><div style='font-size:9px; color:#ffffff;'><b>Know before you make a next move:</b>"
                + "<ul style='margin-top:4px; padding-left:14px; font-size:9px; color:#ffccff;'>"
                + "<li><b>Live catalyst:</b> uses Alpaca news tagged to each symbol and filters for earnings terms.</li>"
                + "<li><b>Market reaction:</b> requires current price, daily bars, liquidity, and relative volume.</li>"
                + "<li><b>Planning only:</b> recommendations create pending strategy rows; broker orders are placed from portfolio actions.</li>"
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
