package com.neuralarc.ui;

import com.neuralarc.util.FontLoader;
import com.neuralarc.util.ThemeColors;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Shared section chrome for operator-console panels that can be folded away
 * without destroying their child component state.
 */
final class CollapsibleSectionPanel extends JPanel {
    static final String COLLAPSED_PROPERTY = "collapsed";

    private static final String EXPANDED_MARKER = "▾ ";
    private static final String COLLAPSED_MARKER = "▸ ";
    private static final int TITLE_CLICK_HEIGHT = 24;

    private final String title;
    private final TitledBorder titledBorder;
    private final JComponent content;
    private boolean collapsed;

    CollapsibleSectionPanel(String title, JComponent content) {
        super(new BorderLayout());
        this.title = title;
        this.content = content;
        this.titledBorder = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(ThemeColors.color("NeuralArc.Section.border", new Color(208, 214, 222)), 1, true),
                EXPANDED_MARKER + title,
                TitledBorder.LEADING,
                TitledBorder.TOP,
                FontLoader.ui(java.awt.Font.BOLD, 10f),
                ThemeColors.color("NeuralArc.Section.titleForeground", new Color(78, 84, 94))
        );

        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setBorder(BorderFactory.createCompoundBorder(
                new EmptyBorder(10, 0, 0, 0),
                BorderFactory.createCompoundBorder(
                        titledBorder,
                        new EmptyBorder(4, 8, 8, 8)
                )
        ));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getY() <= TITLE_CLICK_HEIGHT) {
                    setCollapsed(!collapsed);
                }
            }
        });
        add(content, BorderLayout.CENTER);
        setCollapsed(false);
    }

    void setCollapsed(boolean collapsed) {
        boolean oldValue = this.collapsed;
        this.collapsed = collapsed;
        content.setVisible(!collapsed);
        titledBorder.setTitle((collapsed ? COLLAPSED_MARKER : EXPANDED_MARKER) + title);
        firePropertyChange(COLLAPSED_PROPERTY, oldValue, collapsed);
        revalidate();
        repaint();
    }

    @Override
    public Dimension getMinimumSize() {
        Dimension preferred = getPreferredSize();
        return new Dimension(0, preferred.height);
    }
}
