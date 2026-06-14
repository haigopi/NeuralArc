package com.neuralarc.gaprocket;

import javax.swing.*;
import java.awt.*;

public final class GapRocketPanel extends JPanel {
    public static final String DESCRIPTION_TITLE = "Description:";
    public static final String DESCRIPTION = "Scan premarket gap-up stocks, rank the strongest movers, and add the best candidates to this strategy grid for review.";
    public static final String EXAMPLE_TITLE = "Example:";
    public static final String EXAMPLE = "A candidate stock is up 7% premarket after earnings, trades 5M shares before the open, holds 6x relative volume, and retests the opening-range high before becoming Ready to Buy.";
    public static final String FIELD_GUIDANCE = "Know before you make a next move:\n"
            + "VWAP is the volume-weighted average price; a VWAP pullback looks for price to hold near that intraday fair-value line.\n"
            + "Market Trend Filter can require SPY Green, QQQ Green, or either benchmark to confirm a supportive broad-market backdrop.\n"
            + "SPY Green means the S&P 500 ETF is above its previous close; QQQ Green means the Nasdaq 100 ETF is above its previous close.\n"
            + "Entry Style controls whether the setup waits for an Opening Range Breakout or a Breakout Retest.\n"
            + "Opening Range Breakout waits for price to break above the high from the first 5, 15, or 30 minutes after 9:30 AM ET.\n"
            + "Breakout Retest waits for that breakout first, then looks for price to pull back near the breakout area before marking Ready to Buy.\n"
            + "5 minutes reacts fastest but is noisier, 15 minutes balances speed and confirmation, and 30 minutes is slower but filters more early whipsaw.";
    public static final String EMPTY_STATE_TEXT = DESCRIPTION_TITLE + "\n" + DESCRIPTION + "\n" + EXAMPLE_TITLE + "\n" + EXAMPLE + "\n" + FIELD_GUIDANCE;
    public static final String ANALYZE_BUTTON_TEXT = "Analyze Gap Stocks";
    private final JButton analyzeButton = new JButton(ANALYZE_BUTTON_TEXT);

    public GapRocketPanel(Runnable onAnalyze) {
        this(onAnalyze, true);
    }

    public GapRocketPanel(Runnable onAnalyze, boolean showButton) {
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
                + "<li><b>VWAP:</b> volume-weighted average price; a VWAP pullback looks for price to hold near that intraday fair-value line.</li>"
                + "<li><b>Market Trend Filter:</b> can require SPY Green, QQQ Green, or either benchmark to confirm a supportive broad-market backdrop.</li>"
                + "<li><b>SPY / QQQ Green:</b> SPY tracks the S&amp;P 500 and QQQ tracks the Nasdaq 100. Green means that ETF is above its previous close.</li>"
                + "<li><b>Entry Style:</b> controls whether the setup waits for an Opening Range Breakout or a Breakout Retest.</li>"
                + "<li><b>Opening Range Breakout:</b> waits for price to break above the high from the first 5, 15, or 30 minutes after 9:30 AM ET.</li>"
                + "<li><b>Breakout Retest:</b> waits for that breakout first, then looks for price to pull back near the breakout area before marking Ready to Buy.</li>"
                + "<li><b>5 / 15 / 30 minutes:</b> 5 reacts fastest but is noisier; 15 balances speed and confirmation; 30 is slower but filters more early whipsaw.</li>"
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
