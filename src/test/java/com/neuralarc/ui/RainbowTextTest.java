package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RainbowTextTest {

    @Test
    void wrapsEachVisibleCharWithASeamlessGradientColor() {
        String html = RainbowText.toHtml("PL");
        assertTrue(html.startsWith("<html>"));
        assertTrue(html.endsWith("</html>"));
        // Two visible chars → two font tags with colors from one end-to-end gradient.
        assertEquals(2, countOccurrences(html, "<font color='#"));
        assertTrue(html.contains(">P</font>"));
        assertTrue(html.contains(">L</font>"));
    }

    @Test
    void appliesSpectrumOnceWithoutRepeatingForLongText() {
        String html = RainbowText.toHtml("ABCDEFGHI");
        assertEquals(9, countOccurrences(html, "<font color='#"));
        assertEquals(1, countOccurrences(html, "#E63946"));
    }

    @Test
    void escapesHtmlSpecialCharactersAndKeepsSpaces() {
        String html = RainbowText.toHtml("P&L > $1");
        assertTrue(html.contains("&amp;"));
        assertTrue(html.contains("&gt;"));
        assertTrue(html.contains("&nbsp;"));
        // Raw, unescaped specials must not leak into the markup.
        assertFalse(html.contains(">&<"));
    }

    @Test
    void emptyOrNullReturnsEmpty() {
        assertEquals("", RainbowText.toHtml(""));
        assertEquals("", RainbowText.toHtml(null));
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) >= 0) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
