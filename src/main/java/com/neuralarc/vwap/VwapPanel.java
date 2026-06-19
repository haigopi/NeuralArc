package com.neuralarc.vwap;

import javax.swing.*;
import java.awt.*;

public final class VwapPanel extends JPanel {
    public static final String DESCRIPTION_TITLE = "Description:";
    public static final String DESCRIPTION = "Scan still-strong stocks trading at a discount below their intraday VWAP, rank the best mean-reversion setups on live data, and add the best candidates to this strategy grid for review.";
    public static final String EXAMPLE_TITLE = "Example:";
    public static final String EXAMPLE = "A leading stock trading above its 50- and 200-day moving averages dips 2% below its intraday VWAP on routine selling; the strategy plans a buy expecting price to revert back toward VWAP.";
    public static final String FIELD_GUIDANCE = "Know before you make a next move:\n"
            + "VWAP is the volume-weighted average price — the day's fair-value line that intraday traders anchor to.\n"
            + "Discount % measures how far below VWAP price currently sits; the strategy buys a stretch expecting reversion, with VWAP as the target.\n"
            + "Minimum / Maximum Discount % bound the stretch so tiny dips and outright breakdowns are both filtered out.\n"
            + "Trend Filter confirms the name is still strong by requiring price above its 50-day, 200-day, or either moving average.\n"
            + "Relative Volume requires today's activity to be meaningfully above normal so the reversion has participation.";
    public static final String EMPTY_STATE_TEXT = DESCRIPTION_TITLE + "\n" + DESCRIPTION + "\n" + EXAMPLE_TITLE + "\n" + EXAMPLE + "\n" + FIELD_GUIDANCE;
    public static final String ANALYZE_BUTTON_TEXT = "Analyze VWAP Stocks";
    private final JButton analyzeButton = new JButton(ANALYZE_BUTTON_TEXT);

    public VwapPanel(Runnable onAnalyze) {
        this(onAnalyze, true);
    }

    public VwapPanel(Runnable onAnalyze, boolean showButton) {
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
                + "<li><b>VWAP:</b> the volume-weighted average price — the day's fair-value line that intraday traders anchor to.</li>"
                + "<li><b>Discount %:</b> how far below VWAP price currently sits; the strategy buys a stretch expecting reversion, with VWAP as the target.</li>"
                + "<li><b>Minimum / Maximum Discount %:</b> bound the stretch so tiny dips and outright breakdowns are both filtered out.</li>"
                + "<li><b>Trend Filter:</b> confirms the name is still strong by requiring price above its 50-day, 200-day, or either moving average.</li>"
                + "<li><b>Relative Volume:</b> requires today's activity to be meaningfully above normal so the reversion has participation.</li>"
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
