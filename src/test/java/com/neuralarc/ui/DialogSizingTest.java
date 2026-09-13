package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Dimension;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DialogSizingTest {
    @Test
    void growsByTheContentScrolledOutOfViewUpToTheTallestAllowed() {
        assertEquals(900, DialogSizing.tallHeight(700, 200, 1000), "shows the hidden content in full");
        assertEquals(1000, DialogSizing.tallHeight(700, 900, 1000), "stops at the tallest the screen allows");
    }

    @Test
    void neverShrinksAndLeavesContentThatAlreadyFitsAlone() {
        assertEquals(700, DialogSizing.tallHeight(700, 0, 1000));
        assertEquals(1100, DialogSizing.tallHeight(1100, 300, 1000), "a dialog already taller keeps its height");
    }

    @Test
    void aDialogWithNothingToScrollGrowsToTheTallestAllowed() {
        assertEquals(1000, DialogSizing.tallHeight(700, -1, 1000));
    }

    @Test
    void measuresHowMuchOfTheFirstScrollPaneIsOutOfView() {
        JPanel view = new JPanel();
        view.setPreferredSize(new Dimension(300, 1000));
        JScrollPane scrollPane = new JScrollPane(view);
        JPanel root = new JPanel(new BorderLayout());
        root.add(scrollPane, BorderLayout.CENTER);
        root.setSize(300, 400);
        root.doLayout();
        scrollPane.doLayout();

        assertEquals(1000 - scrollPane.getViewport().getHeight(), DialogSizing.hiddenContentHeight(root));
        assertEquals(-1, DialogSizing.hiddenContentHeight(new JPanel()), "nothing scrolls");
    }
}
