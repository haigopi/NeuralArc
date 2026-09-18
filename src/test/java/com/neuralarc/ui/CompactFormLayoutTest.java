package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagLayout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompactFormLayoutTest {
    @Test
    void aFieldKeepsItsOwnWidthWhenTheFormIsLaidOutMuchWider() {
        // Regression guard for the Liquidate Portfolio dialog: a 5% box stretched across the whole
        // dialog, far from its label.
        JPanel form = CompactFormLayout.form();
        JLabel label = new JLabel("Profit Percent");
        JTextField field = new JTextField("5", 6);
        JLabel unit = new JLabel("%");
        CompactFormLayout.addRow(form, 0, label, field, unit);

        form.setSize(1200, 40);
        form.doLayout();

        assertEquals(field.getPreferredSize().width, field.getWidth(), "the field must not stretch");
        int gapToLabel = field.getX() - (label.getX() + label.getWidth());
        assertTrue(gapToLabel <= 12, "the field sits next to its label, gap was " + gapToLabel);
        assertTrue(unit.getX() < field.getX() + field.getWidth() + 12, "the unit sits right after the field");
    }

    @Test
    void fieldsInOneFormLineUpUnderTheWidestLabel() {
        JPanel form = CompactFormLayout.form();
        JTextField first = new JTextField(6);
        JTextField second = new JTextField(6);
        CompactFormLayout.addRow(form, 0, new JLabel("Quantity"), first, null);
        CompactFormLayout.addRow(form, 1, new JLabel("Smart Picks strategy"), second, null);

        form.setSize(800, 80);
        form.doLayout();

        assertEquals(first.getX(), second.getX());
    }

    @Test
    void longHelpTextWrapsInsteadOfWideningTheDialog() {
        String sentence = "Targets are measured on unrealized profit only — the P&L this liquidation would "
                + "actually realize. Realized profit from trades already closed is shown for reference.";
        JLabel oneLine = new JLabel(sentence);
        JLabel wrapped = new JLabel(CompactFormLayout.wrapped(sentence, 300));

        assertTrue(oneLine.getPreferredSize().width > 400, "precondition: the sentence is long");
        assertTrue(wrapped.getPreferredSize().width <= 330,
                "wrapped width was " + wrapped.getPreferredSize().width);
        assertTrue(wrapped.getPreferredSize().height > oneLine.getPreferredSize().height, "it wraps onto more lines");
    }

    @Test
    void wrappedTextEscapesMarkupCharacters() {
        String html = CompactFormLayout.wrapped("P&L <b>", 200);

        assertTrue(html.contains("P&amp;L &lt;b&gt;"), html);
        assertFalse(html.contains("<b>"), html);
    }

    @Test
    void aWidthTrackingViewNeverScrollsHorizontally() {
        JPanel view = CompactFormLayout.widthTrackingPanel(new GridBagLayout());
        view.setPreferredSize(new Dimension(900, 300));
        JScrollPane scroll = new JScrollPane(view);
        JPanel host = new JPanel(new BorderLayout());
        host.add(scroll);
        host.setSize(500, 200);
        host.doLayout();
        scroll.doLayout();
        scroll.getViewport().doLayout();

        assertEquals(scroll.getViewport().getExtentSize().width, view.getWidth(),
                "the view is sized to the viewport, not to its own preferred width");
        assertFalse(scroll.getHorizontalScrollBar().isVisible());
    }
}
