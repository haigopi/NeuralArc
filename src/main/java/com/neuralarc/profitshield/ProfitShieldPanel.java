package com.neuralarc.profitshield;

import javax.swing.*;
import java.awt.*;

public final class ProfitShieldPanel extends JPanel {
    public static final String DESCRIPTION_TITLE = "Description:";
    public static final String DESCRIPTION = "Scan liquid stocks that hold their value — quiet daily ranges, shallow historical drawdowns, still trading near their own recent high, and backed by a rising long-term trend — then plan capital-preserving entries with a tight protective stop in a dedicated Profit Shield grid.";
    public static final String EXAMPLE_TITLE = "Example:";
    public static final String EXAMPLE = "After a run of winning trades you want to shelter the gains rather than chase the next move. Profit Shield reads roughly six months of daily bars, keeps only names whose worst peak-to-trough decline and daily volatility stay inside your limits, and plans a slightly discounted buy with the stop parked just under the nearest support shelf.";
    public static final String FIELD_GUIDANCE = "Know before you make a next move:\n"
            + "Profit Shield uses live Alpaca daily bars only; no hardcoded defensive tickers are used.\n"
            + "Maximum Daily Volatility caps the 14-day ATR as a percent of price — lower means quieter.\n"
            + "Maximum Drawdown rejects names that have already given back more than you are willing to risk.\n"
            + "Maximum Distance Below High keeps the book in names that are still holding up, not recovering.\n"
            + "Protective Stop is the widest stop allowed; a nearer support shelf tightens it automatically.";
    public static final String EMPTY_STATE_TEXT = DESCRIPTION_TITLE + "\n" + DESCRIPTION + "\n" + EXAMPLE_TITLE + "\n" + EXAMPLE + "\n" + FIELD_GUIDANCE;
    public static final String ANALYZE_BUTTON_TEXT = "Analyze Defensive Stocks";
    private final JButton analyzeButton = new JButton(ANALYZE_BUTTON_TEXT);

    public ProfitShieldPanel(Runnable onAnalyze, boolean showButton) {
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
                + "<li><b>Live daily bars:</b> volatility, drawdown, resilience, and the 20/50/200-day trend stack all come from Alpaca history.</li>"
                + "<li><b>Defensive scoring:</b> points come from how little a name moves against you, not from momentum.</li>"
                + "<li><b>Support-tightened stop:</b> the protective stop is parked just under the 50-day average or the recent session low, whichever is nearer.</li>"
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
