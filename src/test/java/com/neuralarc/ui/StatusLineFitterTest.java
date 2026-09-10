package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusLineFitterTest {
    private static final String LINE =
            "Armed | Live P&L -$34.43 (-14.17%) | Target +5.00% = +$12.15 | Progress 0.00% | Need +$46.58";

    private final FontMetrics metrics = metrics();

    @Test
    void keepsTheWholeLineWhenItFits() {
        assertEquals(LINE, StatusLineFitter.fit(LINE, metrics, metrics.stringWidth(LINE)));
    }

    @Test
    void treatsUnlaidOutSlotsAsUnconstrained() {
        assertEquals(LINE, StatusLineFitter.fit(LINE, metrics, StatusLineFitter.UNCONSTRAINED));
        assertEquals(LINE, StatusLineFitter.fit(LINE, metrics, -20));
        assertEquals(LINE, StatusLineFitter.fit(LINE, null, 40));
    }

    @Test
    void yieldsTheLineEntirelyWhenTheLayoutGrantsNoRoom() {
        assertEquals("", StatusLineFitter.fit(LINE, metrics, 0));
    }

    @Test
    void dropsTrailingSegmentsAndMarksTheTrim() {
        String head = "Armed | Live P&L -$34.43 (-14.17%) | Target +5.00% = +$12.15";
        String fitted = StatusLineFitter.fit(
                LINE, metrics, metrics.stringWidth(head + " | " + StatusLineFitter.ELLIPSIS));

        assertEquals(head + " | " + StatusLineFitter.ELLIPSIS, fitted);
    }

    @Test
    void alwaysKeepsAtLeastTheLeadingSegment() {
        String fitted = StatusLineFitter.fit(LINE, metrics, metrics.stringWidth("Armed | ") + 4);

        assertTrue(fitted.startsWith("Armed"), fitted);
        assertTrue(fitted.endsWith(StatusLineFitter.ELLIPSIS), fitted);
    }

    @Test
    void trimsCharactersWhenEvenTheLeadingSegmentIsTooWide() {
        String fitted = StatusLineFitter.fit(LINE, metrics, metrics.stringWidth("Arm"));

        assertTrue(fitted.endsWith(StatusLineFitter.ELLIPSIS), fitted);
        assertTrue(metrics.stringWidth(fitted) <= metrics.stringWidth("Arm"), fitted);
    }

    @Test
    void neverExceedsTheAvailableWidthAcrossTheWholeShrinkRange() {
        for (int width = 1; width <= metrics.stringWidth(LINE); width += 3) {
            String fitted = StatusLineFitter.fit(LINE, metrics, width);
            // A bare ellipsis is the floor: below its own width there is nothing narrower to show.
            assertTrue(metrics.stringWidth(fitted) <= width || StatusLineFitter.ELLIPSIS.equals(fitted),
                    "width=" + width + " produced '" + fitted + "'");
        }
    }

    @Test
    void passesBlankTextThrough() {
        assertEquals("", StatusLineFitter.fit(null, metrics, 100));
        assertEquals("   ", StatusLineFitter.fit("   ", metrics, 1));
    }

    private static FontMetrics metrics() {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        return image.createGraphics().getFontMetrics(new Font(Font.SANS_SERIF, Font.BOLD, 11));
    }
}
