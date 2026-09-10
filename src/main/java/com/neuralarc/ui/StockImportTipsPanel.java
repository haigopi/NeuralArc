package com.neuralarc.ui;

import com.neuralarc.util.FontLoader;
import com.neuralarc.util.ThemeColors;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

/**
 * The guidance header of the Import Stocks dialog.
 *
 * <p>Replaces a wall of pre-formatted example text with three scannable format cards. Each card
 * names the shape, shows a two-line sample, says what the importer derives from it, and can load
 * that sample straight into the editor — so the format is learned by trying it rather than by
 * reading a specification.
 */
final class StockImportTipsPanel extends JPanel {
    static final Color BORDER = ThemeColors.color("NeuralArc.Detail.border", new Color(86, 91, 99));

    private static final Color SURFACE = ThemeColors.color("NeuralArc.Detail.background", new Color(46, 49, 60));
    private static final Color TITLE = ThemeColors.color("NeuralArc.Detail.foreground", new Color(213, 218, 226));
    private static final Color MUTED = ThemeColors.color("NeuralArc.Detail.titleForeground", new Color(157, 166, 179));
    private static final Color ACCENT = ThemeColors.color("NeuralArc.modePaper", new Color(100, 181, 246));

    private static final Font TITLE_FONT = FontLoader.ui(Font.BOLD, 15f);
    private static final Font BODY_FONT = FontLoader.ui(Font.PLAIN, 11f);
    private static final Font CARD_TITLE_FONT = FontLoader.ui(Font.BOLD, 11f);
    private static final Font LINK_FONT = FontLoader.ui(Font.BOLD, 10f);

    static final String TICKER_LIST_SAMPLE = """
            1. Palantir ~ $PLTR
            2. Nebius ~ $NBIS
            3. Oracle ~ $ORCL
            4. Rocket Lab ~ $RKLB""";

    private static final String ALERT_BLOCK_SAMPLE = """
            Symbol: $MDB
            Entry: Entered @ 451
            Stop: Below 446 (Aggressive)
            Targets: 466""";

    private static final String ANALYST_LIST_SAMPLE = """
            • $IREN $105 (+151%)
            • $NBIS $410 (+87%)
            • $HUT $273 (+238%)
            • $SOFI $30 (+59%)""";

    StockImportTipsPanel(Consumer<String> loadSample) {
        super(new BorderLayout(0, 12));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(18, 18, 14, 18));
        add(intro(), BorderLayout.NORTH);
        add(cards(loadSample), BorderLayout.CENTER);
    }

    private JComponent intro() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(leftAligned(label("Import Stocks", TITLE_FONT, TITLE)));
        panel.add(Box.createVerticalStrut(4));
        panel.add(leftAligned(label(
                "Paste a list from your alerts, research notes or a watchlist — one of the three shapes below.",
                BODY_FONT, MUTED)));
        panel.add(Box.createVerticalStrut(2));
        panel.add(leftAligned(label(
                "Every row arrives as a Manual Addition for review. Nothing is ordered until you place it yourself.",
                BODY_FONT, MUTED)));
        return panel;
    }

    private JComponent cards(Consumer<String> loadSample) {
        JPanel row = new JPanel(new GridLayout(1, 3, 10, 0));
        row.setOpaque(false);
        row.add(card("Ticker list", TICKER_LIST_SAMPLE,
                "No prices needed. Entry, stop and target are calculated from long-term market data.",
                loadSample));
        row.add(card("Alert block", ALERT_BLOCK_SAMPLE,
                "Uses the pasted entry, stop and targets as they are.",
                loadSample));
        row.add(card("Analyst targets", ANALYST_LIST_SAMPLE,
                "Entry is back-solved from each target and its quoted upside.",
                loadSample));
        return row;
    }

    private JComponent card(String title, String sample, String explanation, Consumer<String> loadSample) {
        JPanel card = new JPanel(new BorderLayout(0, 8));
        card.setBackground(SURFACE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1, true),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));

        JLabel heading = label(title, CARD_TITLE_FONT, TITLE);
        card.add(heading, BorderLayout.NORTH);

        JTextArea code = new JTextArea(sample);
        code.setEditable(false);
        code.setFocusable(false);
        code.setOpaque(false);
        code.setFont(monospaceFont());
        code.setForeground(MUTED);
        code.setBorder(BorderFactory.createEmptyBorder());
        card.add(code, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(0, 6));
        footer.setOpaque(false);
        JLabel note = label("<html><body style='width:150px'>" + explanation + "</body></html>", BODY_FONT, MUTED);
        footer.add(note, BorderLayout.NORTH);
        footer.add(sampleLink(sample, loadSample), BorderLayout.SOUTH);
        card.add(footer, BorderLayout.SOUTH);
        return card;
    }

    private JComponent sampleLink(String sample, Consumer<String> loadSample) {
        JLabel link = label("Use this example", LINK_FONT, ACCENT);
        link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        link.setHorizontalAlignment(SwingConstants.LEFT);
        link.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                loadSample.accept(sample);
            }
        });
        return link;
    }

    /** Heading that sits directly above the paste area. */
    static JComponent editorHeading() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.add(label("Paste your list", CARD_TITLE_FONT, TITLE), BorderLayout.WEST);
        panel.add(label("Duplicate tickers are collapsed automatically", BODY_FONT, MUTED), BorderLayout.EAST);
        return panel;
    }

    static Font monospaceFont() {
        return new Font(Font.MONOSPACED, Font.PLAIN, 11);
    }

    private static JLabel label(String text, Font font, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        label.setForeground(color);
        return label;
    }

    private static JComponent leftAligned(JComponent component) {
        component.setAlignmentX(LEFT_ALIGNMENT);
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
        return component;
    }
}
