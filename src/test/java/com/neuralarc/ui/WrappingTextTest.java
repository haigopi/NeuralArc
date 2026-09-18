package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WrappingTextTest {
    private static final String POSITION = "[MRVL]: Shares=-1 | Stock Price=238.75 | Avg Cost=201.20 | MarketValue=-238.75"
            + " | Invested=-201.20 | Realized=0.00 | Unrealized=-37.55 | Stop Loss=Disabled | Profit Target=Disabled";

    @Test
    void aLongPositionSummaryWrapsOntoMoreLinesInsteadOfBeingCutOff() {
        // Regression guard: the Position section was a one-line label that trimmed the text with "...".
        WrappingText text = new WrappingText(POSITION, 10f, Color.WHITE, 420);
        JPanel column = new JPanel(new BorderLayout());
        column.add(text);

        column.setSize(900, 200);
        int wideHeight = text.getPreferredSize().height;
        column.setSize(300, 200);
        int narrowHeight = text.getPreferredSize().height;

        assertTrue(narrowHeight > wideHeight, "narrow " + narrowHeight + " vs wide " + wideHeight);
        assertTrue(text.getPreferredSize().width <= 300, "it never asks for more width than it has");
        assertTrue(text.getText().endsWith("Profit Target=Disabled"), "all of it is kept");
    }
}
