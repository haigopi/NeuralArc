package com.neuralarc.ui;

import com.neuralarc.util.ThemeColors;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The footer under a workspace grid: the tab's name, its P&amp;L, and its portfolio figures (what
 * is invested against what the working buys will still cost, how many positions trend up or down,
 * and how many wait on a buy or a sell) in one row. Hovering a figure or its caption explains it.
 * Only when the grid is too narrow does the row wrap, at a figure boundary, so nothing is cut off.
 */
final class WorkspaceGridAnalyticsBar extends JPanel {
    static final Color POSITIVE = ThemeColors.color("NeuralArc.pnlPositive", new Color(108, 203, 129));
    static final Color NEGATIVE = ThemeColors.color("NeuralArc.pnlNegative", new Color(240, 113, 120));
    private static final Color CAPTION_COLOR = new Color(126, 132, 146);
    private static final String SEPARATOR = "•";
    private static final int GAP = 18;
    private static final int ROW_GAP = 2;
    private static final int TOOLTIP_WIDTH = 420;
    private static final int ROLL_MILLIS = 650;
    private static final int ROLL_FRAME_MILLIS = 35;

    private final Font captionFont;
    private final Font figureFont;
    private final JLabel title = new JLabel(" ");
    private final Map<String, JLabel> figures = new LinkedHashMap<>();
    private final Map<JLabel, JLabel> captions = new HashMap<>();
    private final List<Rectangle> separators = new ArrayList<>();
    private List<String> shownCaptions = List.of();
    private Color neutralColor;
    private int rowCount = 1;
    // Rolling-number animation played when the tab changes, so the operator sees the new figures land.
    private final Map<JLabel, String> rollTargets = new HashMap<>();
    private final Timer rollTimer = new Timer(ROLL_FRAME_MILLIS, ignored -> rollFrame());
    private long rollStartedAt;
    private String lastScopeLabel;

    WorkspaceGridAnalyticsBar(Font baseFont) {
        this.captionFont = baseFont.deriveFont(Font.PLAIN, 11f);
        this.figureFont = baseFont.deriveFont(Font.BOLD, 11f);
        setOpaque(false);
        setLayout(new FigureRowLayout());
        setBorder(new EmptyBorder(6, 14, 6, 14));
        getAccessibleContext().setAccessibleName("Workspace figures");
        title.setFont(figureFont);
        add(title);
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                // The row count depends on the width, so a new width can need a new height.
                if (getPreferredSize().height != getHeight()) {
                    revalidate();
                }
            }
        });
    }

    /**
     * Shows one tab's figures; the same captions update in place, so switching tabs is immediate.
     * The tab name's tooltip carries the reference figures that are not repeated in the row.
     */
    void apply(String scopeLabel, String titleTooltipHtml, List<PortfolioScopePresenter.Figure> items) {
        boolean tabChanged = lastScopeLabel != null && !lastScopeLabel.equals(scopeLabel);
        lastScopeLabel = scopeLabel;
        // Only an on-screen bar rolls; a hidden one has nobody to show it to and just takes the values.
        boolean roll = isShowing() && (tabChanged || rollTimer.isRunning());
        title.setText(scopeLabel == null || scopeLabel.isBlank() ? " " : scopeLabel);
        title.setToolTipText(titleTooltipHtml == null ? null : TooltipStyler.html(titleTooltipHtml, TOOLTIP_WIDTH));
        List<String> captionsNow = items.stream().map(PortfolioScopePresenter.Figure::caption).toList();
        if (!captionsNow.equals(shownCaptions)) {
            rebuild(captionsNow);
        }
        for (PortfolioScopePresenter.Figure item : items) {
            JLabel figure = figures.get(item.caption());
            if (roll) {
                // A refresh arriving mid-roll just retargets the reels at the newer value.
                rollTargets.put(figure, item.text());
                if (tabChanged) {
                    figure.setText(RollingDigits.frame(item.text(), 0, this::randomDigit));
                }
            } else {
                figure.setText(item.text());
            }
            figure.setForeground(switch (item.tone()) {
                case POSITIVE -> POSITIVE;
                case NEGATIVE -> NEGATIVE;
                case NEUTRAL -> neutralColor;
            });
            String tooltip = TooltipStyler.html(item.tooltipHtml(), TOOLTIP_WIDTH);
            figure.setToolTipText(tooltip);
            captions.get(figure).setToolTipText(tooltip);
        }
        if (roll && tabChanged) {
            rollStartedAt = System.currentTimeMillis();
            rollTimer.restart();
        }
        revalidate();
        repaint();
    }

    private void rollFrame() {
        double progress = (System.currentTimeMillis() - rollStartedAt) / (double) ROLL_MILLIS;
        rollTargets.forEach((figure, target) -> figure.setText(RollingDigits.frame(target, progress, this::randomDigit)));
        if (progress >= 1) {
            rollTimer.stop();
            rollTargets.clear();
        }
    }

    private int randomDigit() {
        return java.util.concurrent.ThreadLocalRandom.current().nextInt(10);
    }

    String scopeTitle() {
        return title.getText();
    }

    JLabel figure(String caption) {
        return figures.get(caption);
    }

    /** How many rows the last layout used: one unless the grid is too narrow. */
    int rowCount() {
        return rowCount;
    }

    private void rebuild(List<String> captionTexts) {
        removeAll();
        rollTimer.stop();
        rollTargets.clear();
        figures.clear();
        captions.clear();
        add(title);
        for (String captionText : captionTexts) {
            add(item(captionText));
        }
        shownCaptions = captionTexts;
    }

    private JPanel item(String captionText) {
        JLabel caption = new JLabel(captionText);
        caption.setFont(captionFont);
        caption.setForeground(CAPTION_COLOR);
        JLabel figure = new JLabel("-");
        figure.setFont(figureFont);
        if (neutralColor == null) {
            neutralColor = figure.getForeground();
        }
        figures.put(captionText, figure);
        captions.put(figure, caption);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 0));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(0, 0, 0, 4);
        panel.add(caption, gbc);
        gbc.gridx = 1;
        gbc.insets = new Insets(0, 0, 0, 0);
        panel.add(figure, gbc);
        return panel;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(captionFont);
            g.setColor(CAPTION_COLOR);
            FontMetrics metrics = g.getFontMetrics();
            int dotWidth = metrics.stringWidth(SEPARATOR);
            for (Rectangle gap : separators) {
                int x = gap.x + (gap.width - dotWidth) / 2;
                int y = gap.y + (gap.height - metrics.getHeight()) / 2 + metrics.getAscent();
                g.drawString(SEPARATOR, x, y);
            }
        } finally {
            g.dispose();
        }
    }

    /**
     * Places the title and figures left to right, moving a figure to the next row only when it no
     * longer fits, and remembers the gaps between neighbours on a row so a separator is painted there.
     */
    private final class FigureRowLayout implements LayoutManager {
        @Override
        public void addLayoutComponent(String name, Component component) {
        }

        @Override
        public void removeLayoutComponent(Component component) {
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            return arrange(parent, false);
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            return new Dimension(0, arrange(parent, false).height);
        }

        @Override
        public void layoutContainer(Container parent) {
            arrange(parent, true);
        }

        private Dimension arrange(Container parent, boolean place) {
            Insets insets = parent.getInsets();
            int available = parent.getWidth() > 0
                    ? parent.getWidth() - insets.left - insets.right
                    : Integer.MAX_VALUE;
            List<Rectangle> gaps = new ArrayList<>();
            int x = 0;
            int y = 0;
            int rowHeight = 0;
            int widest = 0;
            int rows = 1;
            boolean first = true;
            for (Component component : parent.getComponents()) {
                if (!component.isVisible()) {
                    continue;
                }
                Dimension size = component.getPreferredSize();
                int start = first ? 0 : x + GAP;
                if (!first && start + size.width > available) {
                    y += rowHeight + ROW_GAP;
                    rows++;
                    rowHeight = 0;
                    start = 0;
                } else if (!first) {
                    gaps.add(new Rectangle(insets.left + x, insets.top + y, GAP, size.height));
                }
                if (place) {
                    component.setBounds(insets.left + start, insets.top + y, size.width, size.height);
                }
                x = start + size.width;
                rowHeight = Math.max(rowHeight, size.height);
                widest = Math.max(widest, x);
                first = false;
            }
            if (place) {
                separators.clear();
                separators.addAll(gaps);
                rowCount = rows;
            }
            return new Dimension(widest + insets.left + insets.right, y + rowHeight + insets.top + insets.bottom);
        }
    }
}
